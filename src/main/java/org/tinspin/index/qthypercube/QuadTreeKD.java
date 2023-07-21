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
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class QuadTreeKD<T> implements PointMap<T>, PointMultimap<T> {

	/** Enable basic HCI navigation with Algo #1 isInI(), for example for window queries. */
	public static boolean ENABLE_HCI_1 = true;
	/** Enable basic HCI navigation with Algo #2 inc(), for example for window queries. */
	public static boolean ENABLE_HCI_2 = true;
	
	private static final int MAX_DEPTH = 50;
	
	private static final String NL = System.lineSeparator();

	public static final boolean DEBUG = false;
	private static final int DEFAULT_MAX_NODE_SIZE = 10;
	private static final double INITIAL_RADIUS = Double.MAX_VALUE;
	
	private final int dims;
	private final int maxNodeSize;
	private QNode<T> root = null;
	private int size = 0; 
	
	private QuadTreeKD(int dims, int maxNodeSize) {
		if (DEBUG) {
			System.err.println("Warning: DEBUG enabled");
		}
		this.dims = dims;
		this.maxNodeSize = maxNodeSize;
	}

	public static <T> QuadTreeKD<T> create(int dims) {
		return new QuadTreeKD<>(dims, DEFAULT_MAX_NODE_SIZE);
	}
	
	public static <T> QuadTreeKD<T> create(int dims, int maxNodeSize) {
		return new QuadTreeKD<>(dims, maxNodeSize);
	}
	
	public static <T> QuadTreeKD<T> create(int dims, int maxNodeSize, 
			double[] center, double radius) {
		QuadTreeKD<T> t = new QuadTreeKD<>(dims, maxNodeSize);
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
			// We calculate a better radius when adding a second point.
			root = new QNode<>(key.clone(), INITIAL_RADIUS);
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
		if (!root.isLeaf() || root.getEntries().isEmpty()) {
			return;
		}
		if (root.getRadius() == INITIAL_RADIUS) {
			double dist = QUtil.distance(key, root.getCenter());
			if (dist > 0) {
				root.adjustRadius(2 * dist);
			} else if (root.getEntries().size() >= maxNodeSize - 1) {
				// we just set an arbitrary radius here
				root.adjustRadius(1000);
			}
		}
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
		QEntry<T> e = root.getExact(key, entry -> true);
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
	 * @param condition A predicate that must evaluate to 'true' for an entry to be updated.
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
	private void ensureCoverage(QEntry<T> e) {
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
			if (QuadTreeKD.DEBUG && !QUtil.isNodeEnclosed(center, radius, center2, radius2)) {
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
	public PointEntryDist<T> query1nn(double[] center) {
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
		return new QIteratorKnn<>(root, k, center, dist, e -> true);
	}

	@Deprecated
	public List<QEntryDist<T>> knnQuery(double[] center, int k) {
		return knnQuery(center, k, PointDistance.L2);
	}

	@Deprecated
	public List<QEntryDist<T>> knnQuery(double[] center, int k, PointDistance distFn) {
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
    		Comparator<QEntry<T>> comp, PointDistance distFn) {
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
    		QNode<T>[] nodes = node.getChildNodes(); 
    		for (int i = 0; i < nodes.length; i++) {
    			if (nodes[i] != null && QUtil.fitsIntoNode(point, nodes[i].getCenter(), nodes[i].getRadius())) {
    				return distanceEstimate(nodes[i], point, k, comp, distFn);
    			}
    		}
    		//okay, this directory node contains the point, but none of the leaves does.
    		//We just return the size of this node, because all it's leaf nodes should
    		//contain more than enough candidate in proximity of 'point'.
    		return node.getRadius() * Math.sqrt(point.length);
    	}
    }
    
    private double rangeSearchKNN(QNode<T> node, double[] center, 
    		ArrayList<QEntryDist<T>> candidates, int k, double maxRange, PointDistance distFn) {
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
    		QNode<T>[] nodes = node.getChildNodes(); 
    		for (int i = 0; i < nodes.length; i++) {
    			QNode<T> sub = nodes[i];
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
	
	private void toStringTree(StringBuilder sb, QNode<T> node, 
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
				QNode<T> sub = node.getChildNodes()[i];
				if (sub != null) {
					toStringTree(sb, sub, depth+1, pos);
				}
			}
		}
		if (node.getEntries() != null) {
			for (int i = 0; i < node.getEntries().size(); i++) {
				QEntry<T> e = node.getEntries().get(i);
				sb.append(prefix + Arrays.toString(e.point()));
				sb.append(" v=" + e.value() + NL);
			}
			pos++;
		}
	}
	
	@Override
	public String toString() {
		return "QuadTreeKD;maxNodeSize=" + maxNodeSize + 
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
		final int[] histoValues = new int[100];
		final int[] histoSubs;

		public QStats(int dims) {
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
	
	protected QNode<T> getRoot() {
		return root;
	}
}
