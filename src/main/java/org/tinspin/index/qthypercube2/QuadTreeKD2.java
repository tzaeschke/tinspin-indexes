/*
 * Copyright 2016-2017 Tilmann Zaeschke
 * 
 * This file is part of TinSpin.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinspin.index.qthypercube2;

import java.util.*;
import java.util.function.Predicate;

import org.tinspin.index.*;
import org.tinspin.index.util.MathTools;
import org.tinspin.index.util.StringBuilderLn;

/**
 * This is a MX-quadtree implementation with configurable maximum depth, maximum nodes size, and
 * (if desired) automatic guessing of root rectangle. 
 * 
 * For navigation during insert/delete/update/queries, it uses 
 * hypercube navigation as described by 
 * T. Zaeschke and M. Norrie, "Efficient Z-Ordered Traversal of Hypercube Indexes, 
 * BTW proceedings, 2017.
 * 
 * This version of the quadtree stores for each node only the center point and the
 * distance (radius) to the edges.
 * This reduces space requirements but increases problems with numerical precision.
 * Overall it is more space efficient and slightly faster. 
 * 
 * This version 2 of the QuadtreeHC reduces memory consumption allowing directory nodes to contain 
 * data entries.
 * With version 1, there were many nodes with just one data entry because the parent directory 
 * node could not hold date entries. 
 * With version two a directory node (which contains a hypercube array) will only
 * create a subnode if a quadrant has to hold more than one data entry.
 * 
 * 
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class QuadTreeKD2<T> implements PointMap<T>, PointMultimap<T> {

	/** Enable basic HCI navigation with Algo #1 isInI(), for example for window queries. */
	public static boolean ENABLE_HCI_1 = true;
	/** Enable basic HCI navigation with Algo #2 inc(), for example for window queries. */
	public static boolean ENABLE_HCI_2 = true;
	private static final int MAX_DEPTH = 50;
	public static final boolean DEBUG = false;
	// This is the MINIMUM MAX_NODE_SIZE. MAX__NODE_SIZE is adjust upwards automatically
	// with increasing dimensionality
	private static final int DEFAULT_MAX_NODE_SIZE = 10;
	private static final double INITIAL_RADIUS = Double.MAX_VALUE;
	private final int dims;
	private final int maxNodeSize;
	private QNode<T> root = null;
	private int size = 0;


	private QuadTreeKD2(int dims, int maxNodeSize) {
		if (DEBUG) {
			System.err.println("Warning: DEBUG enabled");
		}
		this.dims = dims;
		this.maxNodeSize = maxNodeSize;
	}

	/**
	 * @param dims dimensions, usually 2 or 3
	 * @return New quadtree
	 * @param <T> Value type
	 */
	public static <T> QuadTreeKD2<T> create(int dims) {
		int maxNodeSize = DEFAULT_MAX_NODE_SIZE;
		if (2 * dims > DEFAULT_MAX_NODE_SIZE) {
			maxNodeSize = 2*dims;
		}
		return new QuadTreeKD2<>(dims, maxNodeSize);
	}

	/**
	 * @param dims dimensions, usually 2 or 3
	 * @param maxNodeSize maximum entries per node, default is 10
	 * @return New quadtree
	 * @param <T> Value type
	 */
	public static <T> QuadTreeKD2<T> create(int dims, int maxNodeSize) {
		return new QuadTreeKD2<>(dims, maxNodeSize);
	}

	/**
	 * Note: This will align center and radius to a power of two before creating a tree.
	 * @param center center of initial root node
	 * @param radius radius of initial root node
	 * @param align Whether center and radius should be aligned to powers of two. Aligning considerably
	 *              reduces risk of precision problems. Recommended: "true".
	 * @param maxNodeSize maximum entries per node, default is 10
	 * @return New quadtree
	 * @param <T> Value type
	 */
	public static <T> QuadTreeKD2<T> create(double[] center, double radius, boolean align, int maxNodeSize) {
		QuadTreeKD2<T> t = new QuadTreeKD2<>(center.length, maxNodeSize);
		if (radius <= 0) {
			throw new IllegalArgumentException("Radius must be > 0 but was " + radius);
		}
		if (align) {
			center = MathTools.floorPowerOfTwoCopy(center);
			radius = MathTools.ceilPowerOfTwo(radius);
		}
		t.root = new QNode<>(Arrays.copyOf(center, center.length), radius);
		return t;
	}

	/**
	 * WARNING: Unaligned center and radius can cause precision problems.
	 * @param dims dimensions, usually 2 or 3
	 * @param maxNodeSize maximum entries per node, default is 10
	 * @param center center of initial root node
	 * @param radius radius of initial root node
	 * @return New quadtree
	 * @param <T> Value type
	 * @deprecated Please use {@link #create(double[], double, boolean, int)}
	 */
	@Deprecated
	public static <T> QuadTreeKD2<T> create(int dims, int maxNodeSize, 
			double[] center, double radius) {
		QuadTreeKD2<T> t = new QuadTreeKD2<>(dims, maxNodeSize);
		if (radius <= 0) {
			throw new IllegalArgumentException("Radius must be > 0 but was " + radius);
		}
		t.root = new QNode<>(Arrays.copyOf(center, center.length), radius);
		return t;
	}
	
	/**
	 * Insert a key-value pair.
	 * @param key the key
	 * @param value the value
	 */
	@Override
	public void insert(double[] key, T value) {
		size++;
		PointEntry<T> e = new PointEntry<>(key, value);
		if (root == null) {
			// We calculate a better radius when adding a second point.
			// We align the center to a power of two. That reduces precision problems when
			// creating subnode centers.
			root = new QNode<>(MathTools.floorPowerOfTwoCopy(key), INITIAL_RADIUS);
		}
		if (root.getRadius() == INITIAL_RADIUS) {
			adjustRootSize(key);
		}
		ensureCoverage(e);
		QNode<T> r = root;
		int depth = 0;
		while (r != null) {
			r = r.tryPut(e, maxNodeSize, depth++ > MAX_DEPTH);
		}
	}

	private void adjustRootSize(double[] key) {
		// Idea: we calculate the root size only when adding a point that is distinct from the root's center
		if (!root.isLeaf() || root.getValueCount() == 0) {
			return;
		}
		if (root.getRadius() == INITIAL_RADIUS) {
			// Root size has not been initialized yet.
			// We start by getting the maximum horizontal distance between the node center and any point in the node
			double dMax = MathTools.maxDelta(key, root.getCenter());
			for (int i = 0; i < root.getValueCount(); i++) {
				dMax = Math.max(dMax, MathTools.maxDelta(root.getValues()[i].point(), root.getCenter()));
			}
			// We calculate the minimum required radius that is also a power of two.
			// This radius can be divided by 2 many times without precision problems.
			double radius = MathTools.ceilPowerOfTwo(dMax + QUtil.EPS_MUL);
			if (radius > 0) {
				root.adjustRadius(radius);
			} else if (root.getValueCount() >= maxNodeSize - 1) {
				// all entries have (approximately?) the same coordinates. We just set an arbitrary radius here.
				root.adjustRadius(1000);
			}
		}
	}

	/**
	 * Check whether a given key exists.
	 * @param key the key to check
	 * @return true iff the key exists
	 */
	public boolean contains(double[] key) {
		if (root == null) {
			return false;
		}
		return root.getExact(key, entry -> true) != null;
	}
	
	/**
	 * Get the value associates with the key.
	 * @param key the key to look up
	 * @return the value for the key or 'null' if the key was not found
	 */
	@Override
	public T queryExact(double[] key) {
		if (root == null) {
			return null;
		}
		PointEntry<T> e = root.getExact(key, entry -> true);
		return e == null ? null : e.value();
	}

	@Override
	public boolean contains(double[] key, T value) {
		if (root == null) {
			return false;
		}
		return root.getExact(key, e -> Objects.equals(value, e.value())) != null;
	}

	/**
	 * Remove a key.
	 * @param key key to remove
	 * @return the value associated with the key or 'null' if the key was not found
	 */
	@Override
	public T remove(double[] key) {
		if (root == null) {
			return null;
		}
		PointEntry<T> e = root.remove(null, key, maxNodeSize, x -> true);
		if (e == null) {
			return null;
		}
		size--;
		return e.value();
	}

	@Override
	public boolean remove(double[] key, T value) {
		return removeIf(key, e -> Objects.equals(e.value(), value));
	}

	@Override
	public boolean removeIf(double[] key, Predicate<PointEntry<T>> condition) {
		if (root == null) {
			return false;
		}
		PointEntry<T> e = root.remove(null, key, maxNodeSize, condition);
		if (e == null) {
			return false;
		}
		size--;
		return true;
	}

	/**
	 * Reinsert the key.
	 * @param oldKey old key
	 * @param newKey new key
	 * @return the value associated with the key or 'null' if the key was not found.
	 */
	@Override
	public T update(double[] oldKey, double[] newKey) {
		return updateIf(oldKey, newKey, e -> true);
	}

	/**
	 * Reinsert the key.
	 * @param oldKey old key
	 * @param newKey new key
	 * @param value the value of the entry that should be updated.
	 * @return the value associated with the key or 'null' if the key was not found.
	 */
	@Override
	public boolean update(double[] oldKey, double[] newKey, T value) {
		return updateIf(oldKey, newKey, e -> Objects.equals(e.value(), value)) != null;
	}

	/**
	 * Reinsert the key.
	 * @param oldKey old key
	 * @param newKey new key
	 * @param condition A predicate that must evaluate to 'true' for an entry to be updated.
	 * @return the value associated with the key or 'null' if the key was not found.
	 */
	public T updateIf(double[] oldKey, double[] newKey, Predicate<PointEntry<T>> condition) {
		if (root == null) {
			return null;
		}
		boolean[] requiresReinsert = new boolean[]{false};
		PointEntry<T> e = root.update(null, oldKey, newKey, maxNodeSize, requiresReinsert,
				0, MAX_DEPTH, condition);
		if (e == null) {
			//not found
			return null;
		}
		if (requiresReinsert[0]) {
			//does not fit in root node...
			ensureCoverage(e);
			QNode<T> r = root;
			int depth = 0;
			while (r != null) {
				r = r.tryPut(e, maxNodeSize, depth++>MAX_DEPTH);
			}
		}
		return e.value();
	}

	/**
	 * Ensure that the tree covers the entry.
	 * @param e Entry to cover.
	 */
	@SuppressWarnings("unused")
	private void ensureCoverage(PointEntry<T> e) {
		double[] p = e.point();
		while(!QUtil.fitsIntoNode(e.point(), root.getCenter(), root.getRadius())) {
			double[] center = root.getCenter();
			double radius = root.getRadius();
			double[] center2 = new double[center.length];
			double radius2 = radius*2;
			int subNodePos = 0;
			for (int d = 0; d < center.length; d++) {
				subNodePos <<= 1;
				if (p[d] < center[d]-radius) {
					center2[d] = center[d]-radius;
					//root will end up in upper quadrant in this 
					//dimension
					subNodePos |= 1;
				} else {
					//extend upwards, even if extension unnecessary for this dimension.
					center2[d] = center[d]+radius; 
				}
			}
			if (QuadTreeKD2.DEBUG && !QUtil.isNodeEnclosed(center, radius, center2, radius2)) {
				throw new IllegalStateException("e=" + Arrays.toString(e.point()) + 
						" center/radius=" + Arrays.toString(center2) + 
						"/"+ radius);
			}
			root = new QNode<>(center2, radius2, root, subNodePos);
		}
	}
	
	/**
	 * Get the number of key-value pairs in the tree.
	 * @return the size
	 */
	@Override
	public int size() {
		return size;
	}

	/**
	 * Removes all elements from the tree.
	 */
	@Override
	public void clear() {
		size = 0;
		root = null;
	}

	/**
	 * @param point the point
	 * @return an iterator over all entries at the given coordinate.
	 * @see PointMultimap#queryExactPoint(double[])
	 */
	public PointIterator<T> queryExactPoint(double[] point) {
		return query(point, point);
	}

	/**
	 * Query the tree, returning all points in the axis-aligned rectangle between 'min' and 'max'.
	 * @param min lower left corner of query
	 * @param max upper right corner of query
	 * @return all entries in the rectangle
	 */
	@Override
	public PointIterator<T> query(double[] min, double[] max) {
		if (ENABLE_HCI_2) {
			return new QIterator2<>(this, min, max);
		} else if (ENABLE_HCI_1) {
			return new QIterator1<>(this, min, max);
		} //else if (ENABLE_HCI_0) {
		//This does not use min/max but is really very basic. 
		return new QIterator0<>(this, min, max);
		//}
		//return new QIterator<>(this, min, max);
	}

	@Override
	public PointEntryKnn<T> query1nn(double[] center) {
		return PointMap.super.query1nn(center);
	}

	/**
	 *
	 * @param center   center point
	 * @param k        number of neighbors
	 * @param distFn   the point distance function to be used
	 * @return Iterator over query result
	 * @see PointMultimap#queryKnn(double[], int, PointDistance)
	 */
	@Override
	public PointIteratorKnn<T> queryKnn(double[] center, int k, PointDistance distFn) {
		return new QIteratorKnn<>(root, k, center, distFn, (e, d) -> true);
	}

    /**
	 * Returns a printable list of the tree.
	 * @return the tree as String
	 */
	@Override
	public String toStringTree() {
		StringBuilderLn sb = new StringBuilderLn();
		if (root == null) {
			sb.append("empty tree");
		} else {
			toStringTree(sb, root, 0, 0);
		}
		return sb.toString();
	}
	
	@SuppressWarnings("unchecked")
	private void toStringTree(StringBuilderLn sb, QNode<T> node,
							  int depth, int posInParent) {
		String prefix = ".".repeat(depth);
		sb.append(prefix + posInParent + " d=" + depth);
		sb.append(" nV=" + node.getValueCount());
		sb.append(" " + Arrays.toString(node.getCenter()));
		sb.appendLn("/" + node.getRadius());
		prefix += " ";
		for (int i = 0; i < node.getEntries().length; i++) {
			Object o = node.getEntries()[i];
			if (o instanceof QNode) {
				QNode<T> sub = (QNode<T>) o;
				toStringTree(sb, sub, depth+1, i);
			} else if (o != null) {
				PointEntry<T> e = (PointEntry<T>) o;
				sb.append(prefix).append(Arrays.toString(e.point()));
				sb.append(" v=").append(e.value()).appendLn();
			}
		}
	}
	
	@Override
	public String toString() {
		return "QuadTreeKD2;maxNodeSize=" + maxNodeSize + 
				";maxDepth=" + MAX_DEPTH + 
				";DEBUG=" + DEBUG + 
				";center/radius=" + (root==null ? "null" : 
					(Arrays.toString(root.getCenter()) + "/" +
				root.getRadius())) + 
				";HCI-1/2=" + ENABLE_HCI_1 + "/" + ENABLE_HCI_2;
	}
	
	@Override
	public QStats getStats() {
		QStats s = new QStats(dims);
		if (root != null) {
			root.checkNode(s, null, 0);
		}
		return s;
	}
	
	/**
	 * Statistics container class.
	 */
	public static class QStats extends Stats {
		final int[] histoValues = new int[1000];
		final int[] histoSubs;
		static final int HISTO_MAX = (1 << 10) + 1;
		public QStats(int dims) {
			super(0, 0, 0);
			this.dims = dims;
			int histoSize = 1 + (1 << dims);
			this.histoSubs = new int[Math.min(histoSize, HISTO_MAX)];
		}
		public void histo(int pos) {
			if (pos < histoSubs.length) {
				histoSubs[pos]++;
			} else {
				histoSubs[histoSubs.length-1]++;
			}
		}
		@Override
		public String toString() {
			return super.toString() + ";\n"
					+ "histoVal:" + Arrays.toString(histoValues) + "\n"
					+ "histoSub:" + Arrays.toString(histoSubs);
		}
	}

	@Override
	public int getDims() {
		return dims;
	}

	@Override
	public PointIterator<T> iterator() {
		if (root == null) {
			return query(new double[dims], new double[dims]);
		}
		//return query(root.);
		//TODO
		throw new UnsupportedOperationException();
	}

	@Override
	public PointIteratorKnn<T> queryKnn(double[] center, int k) {
		return queryKnn(center, k, PointDistance.L2);
	}

	@Override
	public int getNodeCount() {
		return getStats().getNodeCount();
	}

	@Override
	public int getDepth() {
		return getStats().getMaxDepth();
	}
	
	protected QNode<T> getRoot() {
		return root;
	}
}
