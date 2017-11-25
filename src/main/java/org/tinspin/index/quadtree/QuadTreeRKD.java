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
package org.tinspin.index.quadtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.tinspin.index.QueryIterator;
import org.tinspin.index.QueryIteratorKNN;
import org.tinspin.index.RectangleEntry;
import org.tinspin.index.RectangleEntryDist;
import org.tinspin.index.RectangleIndex;
import org.tinspin.index.quadtree.QuadTreeKD.QStats;

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
			if (DEBUG) {
				System.err.println("Failed remove 1: " + 
						Arrays.toString(keyL) + Arrays.toString(keyU));
			}
			return null;
		}
		QREntry<T> e = root.remove(null, keyL, keyU, maxNodeSize);
		if (e == null) {
			if (DEBUG) {
				System.err.println("Failed remove 2: " + 
						Arrays.toString(keyL) + Arrays.toString(keyU));
			}
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
			if (DEBUG) {
				System.err.println("Failed reinsert 1: " + 
						Arrays.toString(oldKeyL) + Arrays.toString(oldKeyU));
			}
			return null;
		}
		if (requiresReinsert[0]) {
			if (DEBUG) {
				System.err.println("Failed reinsert 2: " + 
						Arrays.toString(oldKeyL) + Arrays.toString(oldKeyU));
			}
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
	@SuppressWarnings("unused")
	private void ensureCoverage(QREntry<T> e) {
		double[] pLow = e.lower();
		//double[] pUpp = e.upper();
		while (!e.enclosedBy(root.getCenter(), root.getRadius())) {
			double[] center = root.getCenter();
			double radius = root.getRadius();
			double[] center2 = new double[center.length];
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
			if (QuadTreeRKD.DEBUG && !QUtil.isRectEnclosed(center, radius, center2, radius2)) {
				throw new IllegalStateException("e=" + Arrays.toString(e.lower()) + 
						"/" + Arrays.toString(e.upper()) + 
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
    		rangeSearchKNN(root, center, candidates, k, distEstimate);
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
    					QUtil.isPointEnclosed(point, nodes[i].getCenter(), nodes[i].getRadius())) {
    				return distanceEstimate(nodes[i], point, k, comp);
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
    		ArrayList<QREntryDist<T>> candidates, int k, double maxRange) {
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
    	
   		QRNode<T>[] nodes = node.getChildNodes();
   		if (nodes != null) {
    		for (int i = 0; i < nodes.length; i++) {
    			QRNode<T> sub = nodes[i];
    			if (sub != null && 
    					QUtil.distToRectNode(center, sub.getCenter(), sub.getRadius()) < maxRange) {
    				maxRange = rangeSearchKNN(sub, center, candidates, k, maxRange);
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
	
    private class QRQueryIteratorKNN implements QueryIteratorKNN<RectangleEntryDist<T>> {

    	private Iterator<RectangleEntryDist<T>> it;
    	
		public QRQueryIteratorKNN(double[] center, int k) {
			reset(center, k);
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public RectangleEntryDist<T> next() {
			return it.next();
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public void reset(double[] center, int k) {
			it = ((List)knnQuery(center, k)).iterator();
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
	
	private void toStringTree(StringBuilder sb, QRNode<T> node, 
			int depth, int posInParent) {
		String prefix = "";
		for (int i = 0; i < depth; i++) {
			prefix += ".";
		}
		sb.append(prefix + posInParent + " d=" + depth);
		sb.append(" " + Arrays.toString(node.getCenter()));
		sb.append("/" + node.getRadius() + NL);
		prefix += " ";
		int pos = 0;
		if (node.getChildNodes() != null) {
			for (int i = 0; i < node.getChildNodes().length; i++) {
				QRNode<T> sub = node.getChildNodes()[i];
				if (sub != null) {
					toStringTree(sb, sub, depth+1, pos);
				}
			}
		}
		if (node.getEntries() != null) {
			for (int i = 0; i < node.getEntries().size(); i++) {
				QREntry<T> e = node.getEntries().get(i);
				sb.append(prefix + Arrays.toString(e.lower()) + Arrays.toString(e.upper()));
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
	public QueryIterator<RectangleEntry<T>> iterator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public QRQueryIteratorKNN queryKNN(double[] center, int k) {
		return new QRQueryIteratorKNN(center, k);
	}

	@Override
	public int getDepth() {
		return getStats().getMaxDepth();
	}
	
	protected QRNode<T> getRoot() {
		return root;
	}
}
