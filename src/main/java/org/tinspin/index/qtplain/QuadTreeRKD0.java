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
import org.tinspin.index.qtplain.QuadTreeKD0.QStats;
import org.tinspin.index.util.QueryIteratorWrapper;

/**
 * A simple MX-quadtree implementation with configurable maximum depth, maximum nodes size, and
 * (if desired) automatic guessing of root rectangle. 
 * 
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class QuadTreeRKD0<T> implements RectangleIndex<T>, RectangleIndexMM<T> {

	private static final int MAX_DEPTH = 50;
	
	private static final String NL = System.lineSeparator();

	public static final boolean DEBUG = false;
	private static final int DEFAULT_MAX_NODE_SIZE = 10;
	
	private final int dims;
	private final int maxNodeSize;
	private QRNode<T> root = null;
	private int size = 0; 
	
	private QuadTreeRKD0(int dims, int maxNodeSize) {
		if (DEBUG) {
			System.err.println("Warning: DEBUG enabled");
		}
		this.dims = dims;
		this.maxNodeSize = maxNodeSize;
	}

	public static <T> QuadTreeRKD0<T> create(int dims) {
		return new QuadTreeRKD0<>(dims, DEFAULT_MAX_NODE_SIZE);
	}
	
	@Deprecated
	public static <T> QuadTreeRKD0<T> create(int dims, int maxNodeSize) {
		return new QuadTreeRKD0<>(dims, maxNodeSize);
	}
	
	public static <T> QuadTreeRKD0<T> create(int dims, int maxNodeSize, 
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
	
	public static <T> QuadTreeRKD0<T> create(int dims, int maxNodeSize, 
			double[] center, double radius) {
		QuadTreeRKD0<T> t = new QuadTreeRKD0<>(dims, maxNodeSize);
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
		QREntry<T> e = new QREntry<>(keyL, keyU, value);
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
		radius = radius == 0.0 ? 1 : radius;
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
		QREntry<T> e = root.getExact(keyL, keyU, x -> true);
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
	public QueryIterator<RectangleEntry<T>> queryRectangle(double[] lower, double[] upper) {
		if (root == null) {
			return null;
		}

		return new QueryIteratorWrapper<>(lower, upper, (low, upp2) -> {
			ArrayList<RectangleEntry<T>> results = new ArrayList<>();
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
		QREntry<T> e = root.remove(null, keyL, keyU, maxNodeSize, x -> true);
		if (e == null) {
			return null;
		}
		size--;
		return e.value();
	}

	@Override
	public boolean remove(double[] lower, double[] upper, T value) {
		return removeIf(lower, upper, e -> Objects.equals(e.value(), value));
	}

	@Override
	public boolean removeIf(double[] lower, double[] upper, Predicate<RectangleEntry<T>> condition) {
		if (root == null) {
			return false;
		}
		QREntry<T> e = root.remove(null, lower, upper, maxNodeSize, condition);
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
		QREntry<T> e = root.update(null, oldKeyL, oldKeyU, newKeyL, newKeyU,
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
		QREntry<T> e = root.update(null, oldKeyL, oldKeyU, newKeyL, newKeyU, 
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
	private void ensureCoverage(QREntry<T> e) {
		double[] pLow = e.lower();
		//double[] pUpp = e.upper();
		while (!QUtil.fitsIntoNode(e.lower(), e.upper(), root.getCenter(), root.getRadius())) {
			double[] center = root.getCenter();
			double radius = root.getRadius();
			double[] center2 = new double[center.length];
			double radius2 = radius*2;
			for (int d = 0; d < center.length; d++) {
				if (pLow[d] < center[d]-radius) {
					center2[d] = center[d]-radius;
					//root will end up in upper quadrant in this 
					//dimension
				} else {
					//extend upwards, even if extension unnecessary for this dimension.
					center2[d] = center[d]+radius; 
				}
			}
			if (QuadTreeRKD0.DEBUG && !QUtil.isNodeEnclosed(center, radius, center2, radius2)) {
				throw new IllegalStateException("e=" + Arrays.toString(e.lower()) + 
						"/" + Arrays.toString(e.upper()) + 
						" center/radius=" + Arrays.toString(center) + "/" + radius);
			}
			root = new QRNode<>(center2, radius2, root);
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
	public RectangleEntryDist<T> query1NN(double[] center) {
		return queryKNN(center, 1).next();
	}

	/**
	 * Resetable query iterator.
	 *
	 * @param <T> Value type
	 */
	public static class QRIterator<T> implements QueryIterator<RectangleEntry<T>> {

		private final QuadTreeRKD0<T> tree;
		private ArrayDeque<Iterator<?>> stack;
		private QREntry<T> next = null;
		private double[] min;
		private double[] max;
		
		QRIterator(QuadTreeRKD0<T> tree, double[] min, double[] max) {
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
					if (o instanceof QRNode) {
						QRNode<T> node = (QRNode<T>)o;
						if (QUtil.overlap(min, max, node.getCenter(), node.getRadius())) {
							it = node.getChildIterator();
							stack.push(it);
						}
						continue;
					}
					QREntry<T> e = (QREntry<T>) o;
					if (QUtil.overlap(min, max, e.lower(), e.upper())) {
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
		public QREntry<T> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			QREntry<T> ret = next;
			findNext();
			return ret;
		}

		/**
		 * Reset the iterator. This iterator can be reused in order to reduce load on the
		 * garbage collector.
		 * @param min lower left corner of query
		 * @param max upper right corner of query
		 */
		@Override
		public void reset(double[] min, double[] max) {
			stack.clear();
			this.min = min;
			this.max = max;
			next = null;
			if (tree.root != null) {
				stack.push(tree.root.getChildIterator());
				findNext();
			}
		}
	}

	@Deprecated
	public List<QREntryDist<T>> knnQuery(double[] center, int k) {
		if (root == null) {
    		return Collections.emptyList();
		}
        Comparator<QREntry<T>> comp =  
        		(QREntry<T> e1, QREntry<T> e2) -> {
        			double deltaDist = 
        					QUtil.distToRectEdge(center, e1) - 
        					QUtil.distToRectEdge(center, e2);
        			return deltaDist < 0 ? -1 : (deltaDist > 0 ? 1 : 0);
        		};
        double distEstimate = distanceEstimate(root, center, k, comp);
        ArrayList<QREntryDist<T>> candidates = new ArrayList<>();
    	while (candidates.size() < k) {
    		candidates.clear();
			// TODO use other distance function !
    		rangeSearchKNN(root, center, candidates, k, distEstimate, PointDistanceFunction.L2);
    		distEstimate *= 2;
    	}
    	return candidates;
    }

    @SuppressWarnings("unchecked")
	private double distanceEstimate(QRNode<T> node, double[] point, int k,
    		Comparator<QREntry<T>> comp) {
    	//We ignore local values if there are child nodes
    	if (node.getChildNodes() != null) {
    		ArrayList<QRNode<T>> nodes = node.getChildNodes(); 
    		for (int i = 0; i < nodes.size(); i++) {
    			QRNode<T> sub = nodes.get(i);
    			if (QUtil.fitsIntoNode(point, sub.getCenter(), sub.getRadius())) {
    				return distanceEstimate(sub, point, k, comp);
    			}
    		}
    		//okay, this directory node contains the point, but none of the leaves does.
    		//We just return the size of this node, because all it's leaf nodes should
    		//contain more than enough candidate in proximity of 'point'.
    		return node.getRadius() * Math.sqrt(point.length); //TODO scale???
    	}

    	//TODO do we need this stuff?? Simplify???
    	//This is a leaf that would contain a good candidate.
    	int n = node.getEntries().size();
    	QREntry<T>[] data = node.getEntries().toArray(new QREntry[n]);
    	Arrays.sort(data, comp);
    	int pos = n < k ? n : k;
    	double dist = QUtil.distToRectEdge(point, data[pos-1]);
    	if (n < k) {
    		//scale search dist with dimensions.
    		dist = dist * Math.pow(k/(double)n, 1/(double)dims);
    	}
		if (dist <= 0.0) {
			return node.getRadius() * 2;
		}
		return dist;
    }
    
    private double rangeSearchKNN(QRNode<T> node, double[] center,
								  ArrayList<QREntryDist<T>> candidates, int k, double maxRange,
								  PointDistanceFunction distFn) {
		ArrayList<QREntry<T>> points = node.getEntries();
    	if (points != null) {
    		for (int i = 0; i < points.size(); i++) {
    			QREntry<T> p = points.get(i);
    			double dist = QUtil.distToRectEdge(center, p);
    			if (dist < maxRange) {
    				candidates.add(new QREntryDist<>(p, dist));
    			}
    		}
    		maxRange = adjustRegionKNN(candidates, k, maxRange);
    	} 
    	
   		ArrayList<QRNode<T>> nodes = node.getChildNodes();
   		if (nodes != null) {
    		for (int i = 0; i < nodes.size(); i++) {
    			QRNode<T> sub = nodes.get(i);
    			if (QUtil.distToRectNode(center, sub.getCenter(), sub.getRadius(), distFn) < maxRange) {
    				maxRange = rangeSearchKNN(sub, center, candidates, k, maxRange, distFn);
    			}
    		}
    	}
    	return maxRange;
    }

    private double adjustRegionKNN(ArrayList<QREntryDist<T>> candidates, int k, double maxRange) {
        if (candidates.size() < k) {
        	//wait for more candidates
        	return maxRange;
        }

        //use stored distances instead of recalculating them
        candidates.sort(QREntryDist.COMP);
        while (candidates.size() > k) {
        	candidates.remove(candidates.size()-1);
        }
        
        double range = candidates.get(candidates.size()-1).dist();
        return range;
	}
	
    /**
	 * Returns a printable list of the tree.
	 * @return the tree as String
	 */
    @Override
	public String toStringTree() {
		StringBuilder sb = new StringBuilder();
		if (root == null) {
			sb.append("empty tree");
		} else {
			toStringTree(sb, root, 0, 0);
		}
		return sb.toString();
	}
	
	@SuppressWarnings("unchecked")
	private void toStringTree(StringBuilder sb, QRNode<T> node, 
			int depth, int posInParent) {
		Iterator<?> it = node.getChildIterator();
		String prefix = "";
		for (int i = 0; i < depth; i++) {
			prefix += ".";
		}
		sb.append(prefix + posInParent + " d=" + depth);
		sb.append(" " + Arrays.toString(node.getCenter()));
		sb.append("/" + node.getRadius() + NL);
		prefix += " ";
		int pos = 0;
		while (it.hasNext()) {
			Object o = it.next();
			if (o instanceof QRNode) {
				QRNode<T> sub = (QRNode<T>) o;
				toStringTree(sb, sub, depth+1, pos);
			} else {
				QREntry<T> e = (QREntry<T>) o;
				sb.append(prefix + Arrays.toString(e.lower()) + Arrays.toString(e.upper()));
				sb.append(" v=" + e.value() + NL);
			}
			pos++;
		}
	}
	
	@Override
	public String toString() {
		return "QuadTreeRKD0;maxNodeSize=" + maxNodeSize + 
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
	
	@Override
	public int getDims() {
		return dims;
	}

	@Override
	public int getNodeCount() {
		return getStats().getNodeCount();
	}

	@Override
	public QueryIterator<RectangleEntry<T>> iterator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public QRIteratorKnn<T> queryKNN(double[] center, int k) {
		return new QRIteratorKnn<>(root, k, center, RectangleDistanceFunction.EDGE, e -> true);
	}

	@Override
	public QueryIteratorKNN<RectangleEntryDist<T>> queryKNN(double[] center, int k, RectangleDistanceFunction distFn) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getDepth() {
		return getStats().getMaxDepth();
	}
}
