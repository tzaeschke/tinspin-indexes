/*
 * Copyright 2016-2024 Tilmann Zaeschke
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
package org.tinspin.index.balltree;

import org.tinspin.index.PointDistance;
import org.tinspin.index.PointMap;
import org.tinspin.index.PointMultimap;
import org.tinspin.index.Stats;
import org.tinspin.index.util.MathTools;
import org.tinspin.index.util.StringBuilderLn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * This is a BallTree.
 * <p>
 * Every ball node contains either two smaller balls or up to 10 entries.
 * <p>
 * Insertion follows the "Improved Insertion" from "Five balltree construction algorithms" by S.M. Omohundro, 1989
 *
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class BallTree<T> implements PointMap<T>, PointMultimap<T> {

	private static final int MAX_DEPTH = 50;
	public static final boolean DEBUG = false;
	private static final int DEFAULT_MAX_NODE_SIZE = 10;
	private static final double INITIAL_RADIUS = Double.MAX_VALUE;
	
	private final int dims;
	private final int maxNodeSize;
	private BTNode<T> root = null;
	private int size = 0; 
	
	private BallTree(int dims, int maxNodeSize) {
		if (DEBUG) {
			System.err.println("Warning: DEBUG enabled");
		}
		this.dims = dims;
		this.maxNodeSize = maxNodeSize;
	}

	public static <T> BallTree<T> create(int dims) {
		return new BallTree<>(dims, DEFAULT_MAX_NODE_SIZE);
	}
	
	public static <T> BallTree<T> create(int dims, int maxNodeSize) {
		return new BallTree<>(dims, maxNodeSize);
	}

	/**
	 * Note: This will align center and radius to a power of two before creating a tree.
	 * @param center center of initial root node
	 * @param radius radius of initial root node
	 * @param maxNodeSize maximum entries per node, default is 10
	 * @param align Whether center and radius should be aligned to powers of two. Aligning considerably
	 *              reduces risk of precision problems. Recommended: "true".
	 * @return New quadtree
	 * @param <T> Value type
	 */
	public static <T> BallTree<T> create(double[] center, double radius, boolean align, int maxNodeSize) {
		BallTree<T> t = new BallTree<>(center.length, maxNodeSize);
		if (radius <= 0) {
			throw new IllegalArgumentException("Radius must be > 0 but was " + radius);
		}
		if (align) {
			center = MathTools.floorPowerOfTwoCopy(center);
			radius = MathTools.ceilPowerOfTwo(radius);
		}
		t.root = new BTNode<>(Arrays.copyOf(center, center.length), radius, null, new ArrayList<>());
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
			root = new BTNode<>(key.clone(), INITIAL_RADIUS, null, new ArrayList<>());
		}
		BTNode<T> r = root;
		int depth = 0;
		while (r != null) {
			r = r.tryPut(e, maxNodeSize, depth++ > MAX_DEPTH);
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
		return root.getExact(key, e -> true) != null;
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
			BTNode<T> r = root;
			int depth = 0;
			while (r != null) {
				r = r.tryPut(e, maxNodeSize, depth++>MAX_DEPTH);
			}
		}
		return e.value();
	}

//	/**
//	 * Ensure that the tree covers the entry.
//	 * @param e Entry to cover.
//	 */
//	@SuppressWarnings("unused")
//	// TODO remove !?!?
//	private void ensureCoverage(PointEntry<T> e) {
//		double[] p = e.point();
//		while(!BTUtil.fitsIntoNode(e.point(), root.getCenter(), root.getRadius())) {
//			double[] center = root.getCenter();
//			double radius = root.getRadius();
//			double[] center2 = new double[center.length];
//			double radius2 = radius*2;
//			int subNodePos = 0;
//			for (int d = 0; d < center.length; d++) {
//				subNodePos <<= 1;
//				if (p[d] < center[d]-radius) {
//					center2[d] = center[d]-radius;
//					//root will end up in upper quadrant in this
//					//dimension
//					subNodePos |= 1;
//				} else {
//					//extend upwards, even if extension unnecessary for this dimension.
//					center2[d] = center[d]+radius;
//				}
//			}
//			if (BallTree.DEBUG && !BTUtil.isNodeEnclosed(center, radius, center2, radius2)) {
//				throw new IllegalStateException("e=" + Arrays.toString(e.point()) +
//						" center/radius=" + Arrays.toString(center2) +
//						"/"+ radius);
//			}
//			root = new BTNode<>(center2, radius2, root, subNodePos);
//		}
//	}
	
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
		//This does not use min/max but is really very basic.
		return new BTIterator<>(this, min, max);
		//return new QIterator<>(this, min, max);
	}

	@Override
	public PointEntryKnn<T> query1nn(double[] center) {
		return PointMap.super.query1nn(center);
	}

	/**
	 *
	 * @param center center point
	 * @param k      number of neighbors
	 * @param dist   the point distance function to be used
	 * @return Iterator over query result
	 * @see PointMultimap#queryKnn(double[], int, PointDistance)
	 */
	@Override
	public PointIteratorKnn<T> queryKnn(double[] center, int k, PointDistance dist) {
		return new BTIteratorKnn<>(root, k, center, dist, (e, d) -> true);
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
	
	private void toStringTree(StringBuilderLn sb, BTNode<T> node, int depth, int posInParent) {
		String prefix = ".".repeat(depth);
		sb.append(prefix + posInParent + " d=" + depth);
		sb.append(" " + Arrays.toString(node.getCenter()));
		sb.appendLn("/" + node.getRadius());
		prefix += " ";
		if (node.getLeftChild() != null) {
			toStringTree(sb, node.getLeftChild(), depth+1, 0);
		}
		if (node.getRightChild() != null) {
			toStringTree(sb, node.getRightChild(), depth+1, 1);
		}
		if (node.getEntries() != null) {
			for (int i = 0; i < node.getEntries().size(); i++) {
				PointEntry<T> e = node.getEntries().get(i);
				sb.append(prefix + Arrays.toString(e.point()));
				sb.appendLn(" v=" + e.value());
			}
		}
	}
	
	@Override
	public String toString() {
		return "BallTree;maxNodeSize=" + maxNodeSize +
				";maxDepth=" + MAX_DEPTH + 
				";DEBUG=" + DEBUG + 
				";center/radius=" + (root==null ? "null" : 
					(Arrays.toString(root.getCenter()) + "/" +
				root.getRadius()));
	}
	
	@Override
	public BTStats getStats() {
		BTStats s = new BTStats(dims);
		if (root != null) {
			root.checkNode(s, null, 0);
		}
		return s;
	}
	
	/**
	 * Statistics container class.
	 */
	public static class BTStats extends Stats {
		final int[] histoValues = new int[1000];
		final int[] histoSubs;

		public BTStats(int dims) {
			super(0, 0, 0);
			this.dims = dims;
			this.histoSubs = new int[1 + (1 << dims)];
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
	
	protected BTNode<T> getRoot() {
		return root;
	}
}
