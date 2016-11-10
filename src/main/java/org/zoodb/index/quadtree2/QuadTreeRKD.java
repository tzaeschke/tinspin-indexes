/*
 * Copyright 2016 Tilmann Zaeschke
 * 
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
package org.zoodb.index.quadtree2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.zoodb.index.RectangleEntry;
import org.zoodb.index.RectangleEntryDist;
import org.zoodb.index.RectangleIndex;
import org.zoodb.index.quadtree2.QuadTreeKD.QStats;

/**
 * A simple MX-quadtree implementation with configurable maximum depth, maximum nodes size, and
 * (if desired) automatic guessing of root rectangle. 
 * 
 * @author ztilmann
 *
 * @param <T>
 */
public class QuadTreeRKD<T> implements RectangleIndex<T> {

	private static final int MAX_DEPTH = 50;
	
	private static final String NL = System.lineSeparator();

	public static final boolean DEBUG = false;
	private static final int DEFAULT_MAX_NODE_SIZE = 10;
	
	private final int dims;
	private final int maxNodeSize;
	private QRNode<T> root = null;
	private int size = 0; 
	
	private QuadTreeRKD(int dims, int maxNodeSize) {
//		if (dims > 6) {
//			throw new UnsupportedOperationException();
//		}
		if (DEBUG) {
			System.err.println("Warning: DEBUG enabled");
		}
		this.dims = dims;
		this.maxNodeSize = maxNodeSize;
	}

	public static <T> QuadTreeRKD<T> create(int dims) {
		return new QuadTreeRKD<>(dims, DEFAULT_MAX_NODE_SIZE);
	}
	
	public static <T> QuadTreeRKD<T> create(int dims, int maxNodeSize) {
		return new QuadTreeRKD<>(dims, maxNodeSize);
	}
	
	public static <T> QuadTreeRKD<T> create(int dims, int maxNodeSize, 
			double[] min, double[] max) {
		QuadTreeRKD<T> t = new QuadTreeRKD<>(dims, maxNodeSize);
		t.root = new QRNode<>( 
				Arrays.copyOf(min, min.length), 
				Arrays.copyOf(max, max.length));
		return t;
	}
	
	/**
	 * Insert a key-value pair.
	 * @param keyL the lower part of the key
	 * @param keyU the upper part of the key
	 * @param value the value
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void insert(double[] keyL, double[] keyU, T value) {
		size++;
		QREntry<T> e = new QREntry<>(keyL, keyU, value);
		if (root == null) {
			initializeRoot(keyL, keyU);
		}
		ensureCoverage(e);
		Object r = root;
		int depth = 0;
		while (r instanceof QRNode) {
			r = ((QRNode<T>)r).tryPut(e, maxNodeSize, depth++>MAX_DEPTH);
		}
	}
	
	private void initializeRoot(double[] keyL, double[] keyU) {
		double lo = Double.MAX_VALUE;
		double hi = -Double.MAX_VALUE;
		for (int d = 0; d < dims; d++) {
			lo = lo > keyL[d] ? keyL[d] : lo;
			hi = hi < keyU[d] ? keyU[d] : hi;
		}
		if (lo == 0 && hi == 0) {
			hi = 1.0; 
		}
		double maxDistOrigin = Math.abs(hi) > Math.abs(lo) ? hi : lo;
		maxDistOrigin = Math.abs(maxDistOrigin);
		//no we use (0,0)/(+-maxDistOrigin*2,+-maxDistOrigin*2) as root.
		double[] min = new double[dims];
		double[] max = new double[dims];
		for (int d = 0; d < dims; d++) {
			min[d] = keyL[d] > 0 ? 0 : -(maxDistOrigin*2);
			max[d] = keyU[d] < 0 ? 0 : (maxDistOrigin*2);
		}			
		root = new QRNode<>(min, max);
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
		return root.getExact(keyL, keyU) != null;
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
		QREntry<T> e = root.getExact(keyL, keyU);
		return e == null ? null : e.value();
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
			System.err.println("Failed remove 1: " + Arrays.toString(keyL)); //TODO
			return null;
		}
		QREntry<T> e = root.remove(null, keyL, keyU, maxNodeSize);
		if (e == null) {
			System.err.println("Failed remove 2: " + Arrays.toString(keyL)); //TODO
			return null;
		}
		size--;
		return e.value();
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
	@SuppressWarnings("unchecked")
	public T update(double[] oldKeyL, double[] oldKeyU, double[] newKeyL, double[] newKeyU) {
		if (root == null) {
			return null;
		}
		boolean[] requiresReinsert = new boolean[]{false};
		QREntry<T> e = root.update(null, oldKeyL, oldKeyU, newKeyL, newKeyU, 
				maxNodeSize, requiresReinsert, 0, MAX_DEPTH);
		if (e == null) {
			//not found
			System.err.println("Failed reinsert 1: " + Arrays.toString(newKeyL)); //TODO
			return null;
		}
		if (requiresReinsert[0]) {
			System.err.println("Failed reinsert 2: " + Arrays.toString(newKeyL)); //TODO
			//does not fit in root node...
			ensureCoverage(e);
			Object r = root;
			int depth = 0;
			while (r instanceof QNode) {
				r = ((QRNode<T>)r).tryPut(e, maxNodeSize, depth++>MAX_DEPTH);
			}
		}
		return e.value();
	}
	
	/**
	 * Ensure that the tree covers the entry.
	 * @param e Entry to cover.
	 */
	private void ensureCoverage(QREntry<T> e) {
		double[] pMin = e.lower();
		//double[] pMax = e.getPointU();
		while(!e.enclosedByXX(root.getMin(), root.getMax())) {
			double len = root.getSideLength();
			double[] min = root.getMin();
			double[] max = root.getMax();
			double[] min2 = new double[min.length];
			double[] max2 = new double[max.length];
			int subNodePos = 0;
			for (int d = 0; d < min.length; d++) {
				subNodePos <<= 1;
				if (pMin[d] < min[d]) {
					min2[d] = min[d]-len;
					max2[d] = max[d];
					//root will end up in upper quadrant in this 
					//dimension
					subNodePos |= 1;
				} else {
					//extend upwards, even if extension unnecessary for this dimension.
					min2[d] = min[d];
					max2[d] = max[d]+len; 
				}
			}
			if (QuadTreeRKD.DEBUG && !QUtil.isRectEnclosed(min, max, min2, max2)) {
				throw new IllegalStateException("e=" + Arrays.toString(e.lower()) + 
						"/" + Arrays.toString(e.upper()) + 
						" min/max=" + Arrays.toString(min) + "/" + Arrays.toString(max));
			}
			root = new QRNode<>(min2, max2, root, subNodePos);
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
	public QIterator<T> queryIntersect(double[] min, double[] max) {
		return new QIterator<>(this, min, max);
	}

	/**
	 * Resetable query iterator.
	 *
	 * @param <T>
	 */
	public static class QIterator<T> implements Iterator<QREntry<T>> {

		private final QuadTreeRKD<T> tree;
		private ArrayDeque<Iterator<?>> stack;
		private QREntry<T> next = null;
		private double[] min;
		private double[] max;
		
		QIterator(QuadTreeRKD<T> tree, double[] min, double[] max) {
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
						if (QUtil.overlap(min, max, node.getMin(), node.getMax())) {
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
	
	public List<QREntryDist<T>> knnQuery(double[] center, int k) {
		if (root == null) {
    		return Collections.emptyList();
		}
        Comparator<QREntry<T>> comp =  
        		(QREntry<T> e1, QREntry<T> e2) -> {
        			double deltaDist = 
        					QUtil.distanceToRect(center, e1) - 
        					QUtil.distanceToRect(center, e2);
        			return deltaDist < 0 ? -1 : (deltaDist > 0 ? 1 : 0);
        		};
        double distEstimate = distanceEstimate(root, center, k, comp);
    	ArrayList<QREntryDist<T>> candidates = new ArrayList<>();
    	while (candidates.size() < k) {
    		candidates.clear();
    		knnSearchNew(root, center, k, distEstimate, candidates);
    		distEstimate *= 2;
    	}
    	return candidates;
    }

    @SuppressWarnings("unchecked")
	private double distanceEstimate(QRNode<T> node, double[] point, int k,
    		Comparator<QREntry<T>> comp) {
    	//We ignore local values if there are child nodes
    	if (node.getChildNodes() != null) {
    		QRNode<T>[] nodes = node.getChildNodes(); 
    		for (int i = 0; i < nodes.length; i++) {
    			if (nodes[i] != null && 
    					QUtil.isPointEnclosed(point, nodes[i].getMin(), nodes[i].getMax())) {
    				return distanceEstimate(nodes[i], point, k, comp);
    			}
    		}
    		//okay, this directory node contains the point, but none of the leaves does.
    		//We just return the size of this node, because all it's leaf nodes should
    		//contain more than enough candidate in proximity of 'point'.
    		double distMin = QUtil.distance(point, node.getMin()); 
    		double distMax = QUtil.distance(point, node.getMax());
    		//Return distance to farthest corner as approximation
    		return distMin < distMax ? distMax : distMin;
    	}

    	//This is a leaf that would contain a good candidate.
    	int n = node.getEntries().size();
    	QREntry<T>[] data = node.getEntries().toArray(new QREntry[n]);
    	Arrays.sort(data, comp);
    	int pos = n < k ? n : k;
    	double dist = QUtil.distanceToRect(point, data[pos-1]);
    	if (n < k) {
    		//scale search dist with dimensions.
    		dist = dist * Math.pow(k/(double)n, 1/(double)dims);
    	}
		if (dist <= 0.0) {
			return node.getSideLength();
		}
		return dist;
    }
    
    private void knnSearchNew(QRNode<T> start, double[] center, int k, double range,
    		ArrayList<QREntryDist<T>> candidates) {
        double[] mbrPointsMin = new double[dims];
        double[] mbrPointsMax = new double[dims];
        for (int i = 0; i < dims; i++) {
            mbrPointsMin[i] = center[i] - range;
            mbrPointsMax[i] = center[i] + range;
        }
        rangeSearchKNN(start, center, mbrPointsMin, mbrPointsMax, candidates, k, range);
    }

    private double rangeSearchKNN(QRNode<T> node, double[] center, double[] min, double[] max, 
    		ArrayList<QREntryDist<T>> candidates, int k, double maxRange) {
		ArrayList<QREntry<T>> points = node.getEntries();
    	if (points != null) {
    		for (int i = 0; i < points.size(); i++) {
    			QREntry<T> p = points.get(i);
    			double dist = QUtil.distanceToRect(center, p);
    			if (dist < maxRange) {
    				candidates.add(new QREntryDist<>(p, dist));
    			}
    		}
    		maxRange = adjustRegionKNN(min, max, center, candidates, k, maxRange);
    	} 
    	
   		QRNode<T>[] nodes = node.getChildNodes();
   		if (nodes != null) {
    		for (int i = 0; i < nodes.length; i++) {
    			QRNode<T> sub = nodes[i];
    			if (sub != null && QUtil.overlap(min, max, sub.getMin(), sub.getMax())) {
    				maxRange = rangeSearchKNN(sub, center, min, max, candidates, k, maxRange);
    			}
    		}
    	}
    	return maxRange;
    }

    private double adjustRegionKNN(double[] min, double[] max, double[] center, 
    		ArrayList<QREntryDist<T>> candidates, int k, double maxRange) {
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
        for (int i = 0; i < dims; i++) {
            min[i] = center[i] - range;
            max[i] = center[i] + range;
        }
        return range;
	}
	
    /**
	 * Returns a printable list of the tree.
	 * @return the tree as String
	 */
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
		sb.append(" " + Arrays.toString(node.getMin()));
		sb.append("/" + Arrays.toString(node.getMax())+ NL);
		prefix += " ";
		int pos = 0;
		while (it.hasNext()) {
			Object o = it.next();
			if (o instanceof QRNode) {
				QRNode<T> sub = (QRNode<T>) o;
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
		return "QuadTreeRKD;maxNodeSize=" + maxNodeSize + 
				";maxDepth=" + MAX_DEPTH + 
				";DEBUG=" + DEBUG + 
				";min/max=" + (root==null ? "null" : 
					(Arrays.toString(root.getMin()) + "/" + 
				Arrays.toString(root.getMax())));
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
		return -1;
	}

	@Override
	public Iterator<? extends RectangleEntry<T>> iterator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public Iterator<? extends RectangleEntryDist<T>> queryKNN(double[] center, int k) {
		return knnQuery(center, k).iterator();
	}
}
