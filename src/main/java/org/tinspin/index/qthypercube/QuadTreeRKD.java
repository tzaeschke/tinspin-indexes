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
package org.tinspin.index.qthypercube;

import java.util.*;
import java.util.function.Predicate;

import org.tinspin.index.*;
import org.tinspin.index.qthypercube.QuadTreeKD.QStats;
import org.tinspin.index.util.BoxIteratorWrapper;
import org.tinspin.index.util.StringBuilderLn;

/**
 * A simple MX-quadtree implementation with configurable maximum depth, maximum nodes size, and
 * (if desired) automatic guessing of root rectangle. 
 * 
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class QuadTreeRKD<T> implements BoxMap<T>, BoxMultimap<T> {

	private static final int MAX_DEPTH = 50;
	public static final boolean DEBUG = false;
	private static final int DEFAULT_MAX_NODE_SIZE = 10;
	private final int dims;
	private final int maxNodeSize;
	private QRNode<T> root = null;
	private int size = 0; 
	
	private QuadTreeRKD(int dims, int maxNodeSize) {
		if (DEBUG) {
			System.err.println("Warning: DEBUG enabled");
		}
		this.dims = dims;
		this.maxNodeSize = maxNodeSize;
	}

	public static <T> QuadTreeRKD<T> create(int dims) {
		return new QuadTreeRKD<>(dims, DEFAULT_MAX_NODE_SIZE);
	}
	
	@Deprecated
	public static <T> QuadTreeRKD<T> create(int dims, int maxNodeSize) {
		return new QuadTreeRKD<>(dims, maxNodeSize);
	}

	/**
	 *
	 * @param dims Number of dimensions per coordinate, usually 2 or 3
	 * @param maxNodeSize Maximum node capacity before a split occurs
	 * @param min Estimated global minimum
	 * @param max Estimated global minimum
	 * @return New quadtree
	 * @param <T> Value type
	 */
	public static <T> QuadTreeRKD<T> create(int dims, int maxNodeSize,
			double[] min, double[] max) {
		double radius = 0;
		double[] center = new double[dims];
		for (int i = 0; i < dims; i++) {
			center[i] = (max[i]+min[i])/2.0;
			if (max[i]-min[i]>radius) {
				radius = max[i]-min[i];
			}
		}
		return create(dims, maxNodeSize, center, radius);
	}
	
	public static <T> QuadTreeRKD<T> create(int dims, int maxNodeSize, 
			double[] center, double radius) {
		QuadTreeRKD<T> t = new QuadTreeRKD<>(dims, maxNodeSize);
		if (radius <= 0) {
			throw new IllegalArgumentException("Radius must be > 0 but was " + radius);
		}
		t.root = new QRNode<>(Arrays.copyOf(center, center.length), radius);
		return t;
	}
	
	/**
	 * Insert a key-value pair.
	 * @param keyL the lower part of the key
	 * @param keyU the upper part of the key
	 * @param value the value
	 */
	@Override
	public void insert(double[] keyL, double[] keyU, T value) {
		size++;
		BoxEntry<T> e = new BoxEntry<>(keyL, keyU, value);
		if (root == null) {
			initializeRoot(keyL, keyU);
		}
		ensureCoverage(e);
		QRNode<T> r = root;
		int depth = 0;
		while (r != null) {
			r = r.tryPut(e, maxNodeSize, depth++>MAX_DEPTH);
		}
	}

	private void initializeRoot(double[] keyL, double[] keyU) {
		double[] center = new double[dims];
		double radius = 0;
		for (int d = 0; d < dims; d++) {
			center[d] = (keyU[d] + keyL[d]) / 2.0;
			if (keyU[d]-keyL[d] > radius) {
				radius = keyU[d] -keyL[d];
			}
		}			
		radius *= 5; //for good measure
		root = new QRNode<>(center, radius);
	}
	
	/**
	 * Check whether a given key exists.
	 * @param keyL the lower key to check
	 * @param keyU the upper key to check
	 * @return true iff the key exists
	 */
	public boolean containsExact(double[] keyL, double[] keyU) {
		if (root == null) {
			return false;
		}
		return root.getExact(keyL, keyU, e -> true) != null;
	}
	
	/**
	 * Get the value associates with the key.
	 * @param keyL the lower key to look up
	 * @param keyU the upper key to look up
	 * @return the value for the key or 'null' if the key was not found
	 */
	@Override
	public T queryExact(double[] keyL, double[] keyU) {
		if (root == null) {
			return null;
		}
		BoxEntry<T> e = root.getExact(keyL, keyU, x -> true);
		return e == null ? null : e.value();
	}

	@Override
	public boolean contains(double[] lower, double[] upper, T value) {
		if (root == null) {
			return false;
		}
		return root.getExact(lower, upper, x -> Objects.equals(value, x.value())) != null;
	}

	@Override
	public BoxIterator<T> queryRectangle(double[] lower, double[] upper) {
		if (root == null) {
			return null;
		}

		return new BoxIteratorWrapper<>(lower, upper, (low, upp2) -> {
			ArrayList<BoxEntry<T>> results = new ArrayList<>();
			// Hack: we use the 'condition' to collect results, however, in order to continue search, we return `false`.
			if (root != null) {
				root.getExact(lower, upper, x -> !results.add(x));
			}
			return results.iterator();
		});
	}


	/**
	 * Remove a key.
	 * @param keyL key to remove
	 * @param keyU key to remove
	 * @return the value associated with the key or 'null' if the key was not found
	 */
	@Override
	public T remove(double[] keyL, double[] keyU) {
		if (root == null) {
			return null;
		}
		BoxEntry<T> e = root.remove(null, keyL, keyU, maxNodeSize, x -> true);
		if (e == null) {
			return null;
		}
		size--;
		return e.value();
	}

	@Override
	public boolean remove(double[] lower, double[] upper, T value) {
		return removeIf(lower, upper, e -> Objects.equals(value, e.value()));
	}

	@Override
	public boolean removeIf(double[] lower, double[] upper, Predicate<BoxEntry<T>> condition) {
		if (root == null) {
			return false;
		}
		BoxEntry<T> e = root.remove(null, lower, upper, maxNodeSize, condition);
		if (e == null) {
			return false;
		}
		size--;
		return true;
	}

	@Override
	public boolean update(double[] oldKeyL, double[] oldKeyU, double[] newKeyL, double[] newKeyU, T value) {
		if (root == null) {
			return false;
		}
		boolean[] requiresReinsert = new boolean[]{false};
		BoxEntry<T> e = root.update(null, oldKeyL, oldKeyU, newKeyL, newKeyU,
				maxNodeSize, requiresReinsert, 0, MAX_DEPTH, t -> Objects.equals(value, t));
		if (e == null) {
			return false;
		}
		if (requiresReinsert[0]) {
			//does not fit in root node...
			ensureCoverage(e);
			QRNode<T> r = root;
			int depth = 0;
			while (r != null) {
				r = r.tryPut(e, maxNodeSize, depth++>MAX_DEPTH);
			}
		}
		return true;
	}

	/**
	 * Reinsert the key.
	 * @param oldKeyL old key
	 * @param oldKeyU old key
	 * @param newKeyL new key
	 * @param newKeyU new key
	 * @return the value associated with the key or 'null' if the key was not found.
	 */
	@Override
	public T update(double[] oldKeyL, double[] oldKeyU, double[] newKeyL, double[] newKeyU) {
		if (root == null) {
			return null;
		}
		boolean[] requiresReinsert = new boolean[]{false};
		BoxEntry<T> e = root.update(null, oldKeyL, oldKeyU, newKeyL, newKeyU,
				maxNodeSize, requiresReinsert, 0, MAX_DEPTH, t -> true);
		if (e == null) {
			//not found
			return null;
		}
		if (requiresReinsert[0]) {
			//does not fit in root node...
			ensureCoverage(e);
			QRNode<T> r = root;
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
	private void ensureCoverage(BoxEntry<T> e) {
		double[] pLow = e.min();
		while (!QUtil.fitsIntoNode(e.min(), e.max(), root.getCenter(), root.getRadius())) {
			double[] center = root.getCenter();
			double radius = root.getRadius();
			double[] center2 = new double[center.length];
			radius = radius == 0.0 ? 1 : radius;
			double radius2 = radius*2;
			int subNodePos = 0;
			for (int d = 0; d < center.length; d++) {
				subNodePos <<= 1;
				if (pLow[d] < center[d]-radius) {
					center2[d] = center[d]-radius;
					//root will end up in upper quadrant in this 
					//dimension
					subNodePos |= 1;
				} else {
					//extend upwards, even if extension unnecessary for this dimension.
					center2[d] = center[d]+radius; 
				}
			}
			if (QuadTreeRKD.DEBUG && !QUtil.isNodeEnclosed(center, radius, center2, radius2)) {
				throw new IllegalStateException("e=" + Arrays.toString(e.min()) +
						"/" + Arrays.toString(e.max()) +
						" center/radius=" + Arrays.toString(center) + "/" + radius);
			}
			root = new QRNode<>(center2, radius2, root, subNodePos);
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
	 * Query the tree, returning all points in the axis-aligned rectangle between 'min' and 'max'.
	 * @param min lower left corner of query
	 * @param max upper right corner of query
	 * @return all entries in the rectangle
	 */
	@Override
	public QRIterator<T> queryIntersect(double[] min, double[] max) {
		return new QRIterator<>(this, min, max);
	}

	@Override
	public BoxEntryKnn<T> query1nn(double[] center) {
		return queryKnn(center, 1).next();
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
	
	private void toStringTree(StringBuilderLn sb, QRNode<T> node, int depth, int posInParent) {
		String prefix = ".".repeat(depth);
		sb.append(prefix + posInParent + " d=" + depth);
		sb.append(" " + Arrays.toString(node.getCenter()));
		sb.appendLn("/" + node.getRadius());
		prefix += " ";
		if (node.getChildNodes() != null) {
			for (int i = 0; i < node.getChildNodes().length; i++) {
				QRNode<T> sub = node.getChildNodes()[i];
				if (sub != null) {
					toStringTree(sb, sub, depth+1, i);
				}
			}
		}
		if (node.getEntries() != null) {
			for (int i = 0; i < node.getEntries().size(); i++) {
				BoxEntry<T> e = node.getEntries().get(i);
				sb.append(prefix + Arrays.toString(e.min()) + Arrays.toString(e.max()));
				sb.appendLn(" v=" + e.value());
			}
		}
	}
	
	@Override
	public String toString() {
		return "QuadTreeRKD;maxNodeSize=" + maxNodeSize + 
				";maxDepth=" + MAX_DEPTH + 
				";DEBUG=" + DEBUG + 
				";center/radius=" + (root==null ? "null" : 
					(Arrays.toString(root.getCenter()) + "/" +
				root.getRadius()));
	}
	
	@Override
	public QStats getStats() {
		QStats s = new QStats(dims);
		if (root != null) {
			root.checkNode(s, null, 0);
		}
		return s;
	}
	
	@Override
	public int getDims() {
		return dims;
	}

	@Override
	public int getNodeCount() {
		return getStats().getNodeCount();
	}

	@Override
	public BoxIterator<T> iterator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public BoxIteratorKnn<T> queryKnn(double[] center, int k) {
		return queryKnn(center, k, BoxDistance.EDGE);
	}

	@Override
	public BoxIteratorKnn<T> queryKnn(double[] center, int k, BoxDistance distFn) {
		return new QRIteratorKnn<>(root, k, center, distFn, (t, d) -> true);
	}

	@Override
	public int getDepth() {
		return getStats().getMaxDepth();
	}
	
	protected QRNode<T> getRoot() {
		return root;
	}
}
