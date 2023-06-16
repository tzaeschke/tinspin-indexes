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
import org.tinspin.index.qthypercube.QuadTreeKD;

/**
 * A simple MX-quadtree implementation with configurable maximum depth, maximum nodes size, and
 * (if desired) automatic guessing of root rectangle. 
 * 
 * This version of the quadtree stores for each node only the center point and the
 * distance (radius) to the edges.
 * This reduces space requirements but increases problems with numerical precision.
 * Overall it is more space efficient and slightly faster. 
 * 
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class QuadTreeKD0<T> implements PointIndex<T>, PointIndexMM<T> {

	private static final int MAX_DEPTH = 50;
	
	private static final String NL = System.lineSeparator();

	public static final boolean DEBUG = false;
	private static final int DEFAULT_MAX_NODE_SIZE = 10;
	private static final double INITIAL_RADIUS = Double.MAX_VALUE;

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
		QEntry<T> e = new QEntry<>(key, value);
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
			lo = lo > key[d] ? key[d] : lo;
			hi = hi < key[d] ? key[d] : hi;
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
//			max[d] = key[d] < 0 ? 0 : (maxDistOrigin*2);
		}
		root = new QNode<>(center, maxDistOrigin);
	}

	/**
	 * Check whether a given key exists.
	 * @param key the key to check
	 * @return true iff the key exists
	 */
	public boolean containsExact(double[] key) {
		if (root == null) {
			return false;
		}
		return root.getExact(key) != null;
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
		QEntry<T> e = root.getExact(key);
		return e == null ? null : e.value();
	}
	
	/**
	 * Remove a key.
	 * @param key key to remove
	 * @return the value associated with the key or 'null' if the key was not found
	 */
	@Override
	public T remove(double[] key) {
		if (root == null) {
			if (DEBUG) {
				System.err.println("Failed remove 1: " + Arrays.toString(key));
			}
			return null;
		}
		QEntry<T> e = root.remove(null, key, maxNodeSize, x -> true);
		if (e == null) {
			if (DEBUG) {
				System.err.println("Failed remove 2: " + Arrays.toString(key));
			}
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
		QEntry<T> e = root.remove(null, key, maxNodeSize, condition);
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
	 * @return the value associated with the key or 'null' if the key was not found.
	 */
	public T updateIf(double[] oldKey, double[] newKey, Predicate<PointEntry<T>> condition) {
		if (root == null) {
			return null;
		}
		boolean[] requiresReinsert = new boolean[]{false};
		QEntry<T> e = root.update(null, oldKey, newKey, maxNodeSize, requiresReinsert,
				0, MAX_DEPTH, condition);
		if (e == null) {
			//not found
			if (DEBUG) {
				System.err.println("Failed reinsert 1: " + Arrays.toString(oldKey) + "/" +
						Arrays.toString(newKey));
			}
			return null;
		}
		if (requiresReinsert[0]) {
			if (DEBUG) {
				System.err.println("Failed reinsert 2: " + Arrays.toString(oldKey) + "/" +
						Arrays.toString(newKey));
			}
			//does not fit in root node...
			ensureCoverage(e);
			Object r = root;
			int depth = 0;
			while (r instanceof QNode) {
				r = ((QNode<T>)r).tryPut(e, maxNodeSize, depth++>MAX_DEPTH);
			}
		}
		return e.value();
	}

	/**
	 * Ensure that the tree covers the entry.
	 * @param e Entry to cover.
	 */
	@SuppressWarnings("unused")
	private void ensureCoverage(QEntry<T> e) {
		double[] p = e.point();
		while(!e.enclosedBy(root.getCenter(), root.getRadius())) {
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
			if (QuadTreeKD0.DEBUG && !QUtil.isRectEnclosed(center, radius, center2, radius2)) {
				throw new IllegalStateException("e=" + Arrays.toString(e.point()) + 
						" center/radius=" + Arrays.toString(center2) + 
						"/"+ radius);
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
	 * @see PointIndexMM#query(double[])
	 */
	public QueryIterator<PointEntry<T>> query(double[] point) {
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
	public static class QIterator<T> implements QueryIterator<PointEntry<T>> {

		private final QuadTreeKD0<T> tree;
		private ArrayDeque<Iterator<?>> stack;
		private QEntry<T> next = null;
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
					QEntry<T> e = (QEntry<T>) o;
					if (e.enclosedBy(min, max)) {
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
		public QEntry<T> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			QEntry<T> ret = next;
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

	@Override
	public PointEntryDist<T> query1NN(double[] center) {
		return PointIndex.super.query1NN(center);
	}

	/**
	 *
	 * @param center center point
	 * @param k      number of neighbors
	 * @param dist   the point distance function to be used
	 * @return Iterator over query result
	 * @see PointIndexMM#queryKNN(double[], int, PointDistanceFunction)
	 */
	@Override
	public QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k, PointDistanceFunction dist) {
		return new QQueryIteratorKNN(center, k, dist);
	}

	public List<QEntryDist<T>> knnQuery(double[] center, int k) {
		return knnQuery(center, k, PointDistanceFunction.L2);
	}

	public List<QEntryDist<T>> knnQuery(double[] center, int k, PointDistanceFunction distFn) {
		if (root == null) {
    		return Collections.emptyList();
		}
        Comparator<QEntry<T>> comp =  
        		(QEntry<T> point1, QEntry<T> point2) -> {
        			double deltaDist = 
        					QUtil.distance(center, point1.point()) - 
        					QUtil.distance(center, point2.point());
        			return deltaDist < 0 ? -1 : (deltaDist > 0 ? 1 : 0);
        		};
        double distEstimate = distanceEstimate(root, center, k, comp, distFn);
    	ArrayList<QEntryDist<T>> candidates = new ArrayList<>();
    	while (candidates.size() < k) {
    		candidates.clear();
    		rangeSearchKNN(root, center, candidates, k, distEstimate, distFn);
    		distEstimate *= 2;
    	}
    	return candidates;
    }

    @SuppressWarnings("unchecked")
	private double distanceEstimate(QNode<T> node, double[] point, int k,
    		Comparator<QEntry<T>> comp, PointDistanceFunction distFn) {
    	if (node.isLeaf()) {
    		//This is a leaf that would contain the point.
    		int n = node.getEntries().size();
    		QEntry<T>[] data = node.getEntries().toArray(new QEntry[n]);
    		Arrays.sort(data, comp);
    		int pos = n < k ? n : k;
    		double dist = distFn.dist(point, data[pos-1].point());
    		if (n < k) {
    			//scale search dist with dimensions.
    			dist = dist * Math.pow(k/(double)n, 1/(double)dims);
    		}
    		if (dist <= 0.0) {
    			return node.getRadius();
    		}
    		return dist;
    	} else {
    		ArrayList<QNode<T>> nodes = node.getChildNodes(); 
    		for (int i = 0; i < nodes.size(); i++) {
    			QNode<T> sub = nodes.get(i);
    			if (QUtil.isPointEnclosed(point, sub.getCenter(), sub.getRadius())) {
    				return distanceEstimate(sub, point, k, comp, distFn);
    			}
    		}
    		//okay, this directory node contains the point, but none of the leaves does.
    		//We just return the size of this node, because all it's leaf nodes should
    		//contain more than enough candidate in proximity of 'point'.
    		return node.getRadius() * Math.sqrt(point.length);
    	}
    }
    
    private double rangeSearchKNN(QNode<T> node, double[] center, 
    		ArrayList<QEntryDist<T>> candidates, int k, double maxRange, PointDistanceFunction distFn) {
		if (node.isLeaf()) {
    		ArrayList<QEntry<T>> points = node.getEntries();
    		for (int i = 0; i < points.size(); i++) {
    			QEntry<T> p = points.get(i);
   				double dist = distFn.dist(center, p.point());
   				if (dist < maxRange) {
    				candidates.add(new QEntryDist<>(p, dist));
  				}
    		}
    		maxRange = adjustRegionKNN(candidates, k, maxRange);
    	} else {
    		ArrayList<QNode<T>> nodes = node.getChildNodes(); 
    		for (int i = 0; i < nodes.size(); i++) {
    			QNode<T> sub = nodes.get(i);
    			if (sub != null && 
    					QUtil.distToRectNode(center, sub.getCenter(), sub.getRadius(), distFn) < maxRange) {
    				maxRange = rangeSearchKNN(sub, center, candidates, k, maxRange, distFn);
    				//we set maxRange simply to the latest returned value.
    			}
    		}
    	}
    	return maxRange;
    }

    private double adjustRegionKNN(ArrayList<QEntryDist<T>> candidates, int k, double maxRange) {
        if (candidates.size() < k) {
        	//wait for more candidates
        	return maxRange;
        }

        //use stored distances instead of recalcualting them
        candidates.sort(QEntryDist.COMP);
        while (candidates.size() > k) {
        	candidates.remove(candidates.size()-1);
        }
        
        double range = candidates.get(candidates.size()-1).dist();
        return range;
	}
	
    private class QQueryIteratorKNN implements QueryIteratorKNN<PointEntryDist<T>> {

		private final PointDistanceFunction distFn;
    	private Iterator<PointEntryDist<T>> it;
    	
		public QQueryIteratorKNN(double[] center, int k, PointDistanceFunction distFn) {
			this.distFn = distFn;
			reset(center, k);
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public PointEntryDist<T> next() {
			return it.next();
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public QQueryIteratorKNN reset(double[] center, int k) {
			it = ((List)knnQuery(center, k, distFn)).iterator();
			return this;
		}
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
	private void toStringTree(StringBuilder sb, QNode<T> node, 
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
			if (o instanceof QNode) {
				QNode<T> sub = (QNode<T>) o;
				toStringTree(sb, sub, depth+1, pos);
			} else if (o instanceof QEntry) {
				QEntry<T> e = (QEntry<T>) o;
				sb.append(prefix + Arrays.toString(e.point()));
				sb.append(" v=" + e.value() + NL);
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
	public QueryIterator<PointEntry<T>> iterator() {
		if (root == null) {
			return query(new double[dims], new double[dims]);
		}
		//return query(root.);
		//TODO
		throw new UnsupportedOperationException();
	}

	@Override
	public QQueryIteratorKNN queryKNN(double[] center, int k) {
		return new QQueryIteratorKNN(center, k, PointDistanceFunction.L2);
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
