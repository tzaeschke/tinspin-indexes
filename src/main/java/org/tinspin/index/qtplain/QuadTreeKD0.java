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
package org.tinspin.index.qtplain;

import java.util.*;
import java.util.function.Predicate;

import org.tinspin.index.*;
import org.tinspin.index.util.StringBuilderLn;

/**
 * A simple MX-quadtree implementation with configurable maximum depth, maximum nodes size, and
 * (if desired) automatic guessing of root rectangle. 
 * <p>
 * This version of the quadtree stores for each node only the center point and the
 * distance (radius) to the edges.
 * This reduces space requirements but increases problems with numerical precision.
 * Overall it is more space efficient and slightly faster. 
 * 
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class QuadTreeKD0<T> implements PointMap<T>, PointMultimap<T> {

	private static final int MAX_DEPTH = 50;
	private static final int DEFAULT_MAX_NODE_SIZE = 10;
	public static final boolean DEBUG = false;
	private final int dims;
	private final int maxNodeSize;
	private QNode<T> root = null;
	private int size = 0; 
	
	private QuadTreeKD0(int dims, int maxNodeSize) {
		if (DEBUG) {
			System.err.println("Warning: DEBUG enabled");
		}
		this.dims = dims;
		this.maxNodeSize = maxNodeSize;
	}

	public static <T> QuadTreeKD0<T> create(int dims) {
		return new QuadTreeKD0<>(dims, DEFAULT_MAX_NODE_SIZE);
	}
	
	public static <T> QuadTreeKD0<T> create(int dims, int maxNodeSize) {
		return new QuadTreeKD0<>(dims, maxNodeSize);
	}
	
	public static <T> QuadTreeKD0<T> create(int dims, int maxNodeSize, 
			double[] center, double radius) {
		QuadTreeKD0<T> t = new QuadTreeKD0<>(dims, maxNodeSize);
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
			initializeRoot(key);
		}
		ensureCoverage(e);
		QNode<T> r = root;
		int depth = 0;
		while (r != null) {
			r = r.tryPut(e, maxNodeSize, depth++ > MAX_DEPTH);
		}
	}

	// TODO doing the same as in QTZ/QTZ2 breaks the tests...
	private void initializeRoot(double[] key) {
		double lo = Double.MAX_VALUE;
		double hi = -Double.MAX_VALUE;
		for (int d = 0; d < dims; d++) {
			lo = Math.min(lo, key[d]);
			hi = Math.max(hi, key[d]);
		}
		if (lo == 0 && hi == 0) {
			hi = 1.0;
		}
		double maxDistOrigin = Math.abs(hi) > Math.abs(lo) ? hi : lo;
		maxDistOrigin = Math.abs(maxDistOrigin);
		//no we use (0,0)/(+-maxDistOrigin*2,+-maxDistOrigin*2) as root.

		//HACK: To avoid precision problems, we ensure that at least the initial
		//point is not exactly on the border of the quadrants:
		maxDistOrigin *= QUtil.EPS_MUL*QUtil.EPS_MUL;
		double[] center = new double[dims];
		for (int d = 0; d < dims; d++) {
			center[d] = key[d] > 0 ? maxDistOrigin : -maxDistOrigin;
		}
		root = new QNode<>(center, maxDistOrigin);
	}

	/**
	 * Check whether a given key exists.
	 * @param key the key to check
	 * @return true iff the key exists
	 */
	@Deprecated
	public boolean containsExact(double[] key) {
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
	private void ensureCoverage(PointEntry<T> e) {
		double[] p = e.point();
		while(!QUtil.fitsIntoNode(e.point(), root.getCenter(), root.getRadius())) {
			double[] center = root.getCenter();
			double radius = root.getRadius();
			double[] center2 = new double[center.length];
			double radius2 = radius*2;
			for (int d = 0; d < center.length; d++) {
				if (p[d] < center[d]-radius) {
					center2[d] = center[d]-radius;
					//root will end up in upper quadrant in this 
					//dimension
				} else {
					//extend upwards, even if extension unnecessary for this dimension.
					center2[d] = center[d]+radius; 
				}
			}
			if (QuadTreeKD0.DEBUG && !QUtil.isNodeEnclosed(center, radius, center2, radius2)) {
				throw new IllegalStateException("e=" + Arrays.toString(e.point()) + 
						" center/radius=" + Arrays.toString(center2) + "/" + radius);
			}
			root = new QNode<>(center2, radius2, root);
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
	 * @see PointMultimap#query(double[])
	 */
	public PointIterator<T> query(double[] point) {
		return query(point, point);
	}

	/**
	 * Query the tree, returning all points in the axis-aligned rectangle between 'min' and 'max'.
	 * @param min lower left corner of query
	 * @param max upper right corner of query
	 * @return all entries in the rectangle
	 */
	@Override
	public QIterator<T> query(double[] min, double[] max) {
		return new QIterator<>(this, min, max);
	}

	/**
	 * Resettable query iterator.
	 *
	 * @param <T> Value type
	 */
	public static class QIterator<T> implements PointIterator<T> {

		private final QuadTreeKD0<T> tree;
		private final ArrayDeque<Iterator<?>> stack;
		private PointEntry<T> next = null;
		private double[] min;
		private double[] max;
		
		QIterator(QuadTreeKD0<T> tree, double[] min, double[] max) {
			this.stack = new ArrayDeque<>();
			this.tree = tree;
			reset(min, max);
		}
		
		@SuppressWarnings("unchecked")
		private void findNext() {
			while(!stack.isEmpty()) {
				Iterator<?> it = stack.peek();
				while (it.hasNext()) {
					Object o = it.next();
					if (o instanceof QNode) {
						QNode<T> node = (QNode<T>)o;
						if (QUtil.overlap(min, max, node.getCenter(), node.getRadius())) {
							it = node.getChildIterator();
							stack.push(it);
						}
						continue;
					}
					PointEntry<T> e = (PointEntry<T>) o;
					if (QUtil.isPointEnclosed(e.point(), min, max)) {
						next = e;
						return;
					}
				}
				stack.pop();
			}
			next = null;
		}
		
		@Override
		public boolean hasNext() {
			return next != null;
		}

		@Override
		public PointEntry<T> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			PointEntry<T> ret = next;
			findNext();
			return ret;
		}

		/**
		 * Reset the iterator. This iterator can be reused in order to reduce load on the
		 * garbage collector.
		 * @param min lower left corner of query
		 * @param max upper right corner of query
		 * @return this.
		 */
		@Override
		public PointIterator<T> reset(double[] min, double[] max) {
			stack.clear();
			this.min = min;
			this.max = max;
			next = null;
			if (tree.root != null) {
				stack.push(tree.root.getChildIterator());
				findNext();
			}
			return this;
		}
	}

	@Override
	public PointEntryKnn<T> query1nn(double[] center) {
		return new QIteratorKnn<>(this.root, 1, center, PointDistance.L2, (e, d) -> true).next();
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
		return new QIteratorKnn<>(this.root, k, center, dist, (e, d) -> true);
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
	private void toStringTree(StringBuilderLn sb, QNode<T> node, int depth, int posInParent) {
		Iterator<?> it = node.getChildIterator();
		String prefix = ".".repeat(depth);
		sb.append(prefix + posInParent + " d=" + depth);
		sb.append(" " + Arrays.toString(node.getCenter()));
		sb.appendLn("/" + node.getRadius());
		prefix += " ";
		int pos = 0;
		while (it.hasNext()) {
			Object o = it.next();
			if (o instanceof QNode) {
				QNode<T> sub = (QNode<T>) o;
				toStringTree(sb, sub, depth+1, pos);
			} else if (o instanceof PointEntry) {
				PointEntry<T> e = (PointEntry<T>) o;
				sb.append(prefix + Arrays.toString(e.point()));
				sb.appendLn(" v=" + e.value());
			}
			pos++;
		}
	}
	
	@Override
	public String toString() {
		return "QuadTreeKD0;maxNodeSize=" + maxNodeSize + 
				";maxDepth=" + MAX_DEPTH + 
				";DEBUG=" + DEBUG + 
				";center/radius=" + (root==null ? "null" : 
					(Arrays.toString(root.getCenter()) + "/" +
				root.getRadius()));
	}
	
	@Override
	public QStats getStats() {
		QStats s = new QStats();
		if (root != null) {
			root.checkNode(s, null, 0);
		}
		return s;
	}
	
	/**
	 * Statistics container class.
	 */
	public static class QStats extends Stats {
		protected QStats() {
			super(0, 0, 0);
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
}
