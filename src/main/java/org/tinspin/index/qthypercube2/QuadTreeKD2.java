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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.tinspin.index.PointEntry;
import org.tinspin.index.PointEntryDist;
import org.tinspin.index.PointIndex;
import org.tinspin.index.QueryIterator;
import org.tinspin.index.QueryIteratorKNN;

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
 * create a subnode if a quadrant has to mhold more than one data entry.
 * 
 * 
 * @author ztilmann
 *
 * @param <T>
 */
public class QuadTreeKD2<T> implements PointIndex<T> {

	/** Enable basic HCI navigation with Algo #1 isInI(), for example for window queries. */
	public static boolean ENABLE_HCI_1 = true;
	/** Enable basic HCI navigation with Algo #2 inc(), for example for window queries. */
	public static boolean ENABLE_HCI_2 = true;
	
	private static final int MAX_DEPTH = 50;
	
	private static final String NL = System.lineSeparator();

	public static final boolean DEBUG = false;
	private static final int DEFAULT_MAX_NODE_SIZE = 10;
	
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

	public static <T> QuadTreeKD2<T> create(int dims) {
		return new QuadTreeKD2<>(dims, DEFAULT_MAX_NODE_SIZE);
	}
	
	public static <T> QuadTreeKD2<T> create(int dims, int maxNodeSize) {
		return new QuadTreeKD2<>(dims, maxNodeSize);
	}
	
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
	@SuppressWarnings("unchecked")
	public void insert(double[] key, T value) {
		size++;
		QEntry<T> e = new QEntry<>(key, value);
		if (root == null) {
			initializeRoot(key);
		}
		ensureCoverage(e);
		Object r = root;
		int depth = 0;
		while (r instanceof QNode) {
			r = ((QNode<T>)r).tryPut(e, maxNodeSize, depth++>MAX_DEPTH);
		}
	}
	
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
		QEntry<T> e = root.remove(null, key, maxNodeSize);
		if (e == null) {
			if (DEBUG) {
				System.err.println("Failed remove 2: " + Arrays.toString(key));
			}
			return null;
		}
		size--;
		return e.value();
	}

	/**
	 * Reinsert the key.
	 * @param oldKey old key
	 * @param newKey new key
	 * @return the value associated with the key or 'null' if the key was not found.
	 */
	@Override
	@SuppressWarnings("unchecked")
	public T update(double[] oldKey, double[] newKey) {
		if (root == null) {
			return null;
		}
		boolean[] requiresReinsert = new boolean[]{false};
		QEntry<T> e = root.update(null, oldKey, newKey, maxNodeSize, requiresReinsert, 
				0, MAX_DEPTH);
		if (e == null) {
			//not found
			if (DEBUG) {
				System.err.println("Failed reinsert 1: " + Arrays.toString(newKey));
			}
			return null;
		}
		if (requiresReinsert[0]) {
			if (DEBUG) {
				System.err.println("Failed reinsert 2: " + Arrays.toString(newKey));
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
			if (QuadTreeKD2.DEBUG && !QUtil.isRectEnclosed(center, radius, center2, radius2)) {
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
	 * Query the tree, returning all points in the axis-aligned rectangle between 'min' and 'max'.
	 * @param min lower left corner of query
	 * @param max upper right corner of query
	 * @return all entries in the rectangle
	 */
	@Override
	public QueryIterator<PointEntry<T>> query(double[] min, double[] max) {
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

	public List<QEntryDist<T>> knnQuery(double[] center, int k) {
		if (root == null) {
    		return Collections.emptyList();
		}
    	ArrayList<QEntryDist<T>> candidates = new ArrayList<>();
    	rangeSearchKNN(root, center, candidates, k, Double.MAX_VALUE);
    	return candidates;
    }

    @SuppressWarnings("unchecked")
	private double rangeSearchKNN(QNode<T> node, double[] center, 
    		ArrayList<QEntryDist<T>> candidates, int k, double maxRange) {
    	int posHC = node.calcSubPosition(center);
    	Object[] entries = node.getEntries();
    	Object alreadyVisited = null;
    	if (!node.isLeaf()) {
    		//Search best node first 
	    	Object ePos = entries[posHC];
	    	if (ePos instanceof QNode) {
				maxRange = rangeSearchKNN((QNode<T>)ePos, center, candidates, k, maxRange);
				alreadyVisited = ePos;
	    	}
    	}
    	//TODO first sort entries by distance!
    	//TODO reuse buffer!
    	ArrayList<KnnTemp> buffer = new ArrayList<>();
    	for (int i = 0; i < entries.length; i++) {
    		Object e = entries[i];
    		if (e instanceof QNode && e != alreadyVisited) {
    			QNode<T> n = (QNode<T>) e;
    			double dist = QUtil.distToRectNode(center, n.getCenter(), n.getRadius());
    			addToBuffer(n, dist, maxRange, buffer);
    		} else if (e instanceof QEntry) {
    			QEntry<T> p = (QEntry<T>) e;
   				double dist = QUtil.distance(center, p.point());
    			addToBuffer(p, dist, maxRange, buffer);
    		}
    	}
    	
    	buffer.sort(KnnTempComp);
    	for (int i = 0; i < buffer.size(); i++) {
    		KnnTemp t = buffer.get(i);
    		if (t.dist > maxRange) {
    			//check again, because maxDist may change during this loop
    			continue;
    		}
    		Object o = t.o;
    		if (o instanceof QNode && o != alreadyVisited) {
    			maxRange = rangeSearchKNN((QNode<T>)o, center, candidates, k, maxRange);
    		} else if (o instanceof QEntry) {
    			QEntry<T> p = (QEntry<T>) o;
   				candidates.add(new QEntryDist<>(p, t.dist));
   	    		maxRange = adjustRegionKNN(candidates, k, maxRange);
    		}
    	}
    	return maxRange;
    }

    private void addToBuffer(Object o, double dist, double maxDist, ArrayList<KnnTemp> buffer) {
		if (dist < maxDist) {
			buffer.add(new KnnTemp(o, dist));
		}
	}

	private double adjustRegionKNN(ArrayList<QEntryDist<T>> candidates, int k, double maxRange) {
        if (candidates.size() < k) {
        	//wait for more candidates
        	return maxRange;
        }

        //use stored distances instead of recalculating them
        candidates.sort(QEntryDist.COMP);
        while (candidates.size() > k) {
        	candidates.remove(candidates.size()-1);
        }
        
        double range = candidates.get(candidates.size()-1).dist();
        return range;
	}
	
    private static class KnnTemp {
    	Object o;
    	double dist;
    	public KnnTemp(Object o, double d) {
    		this.o = o;
    		this.dist = d;
		}
    }

    private static Comparator<KnnTemp> KnnTempComp =  
    		(KnnTemp point1, KnnTemp point2) -> {
    			double deltaDist = point1.dist - point2.dist;
    			return deltaDist < 0 ? -1 : (deltaDist > 0 ? 1 : 0);
    		};

    private class QQueryIteratorKNN implements QueryIteratorKNN<PointEntryDist<T>> {

    	private Iterator<PointEntryDist<T>> it;
    	
		public QQueryIteratorKNN(double[] center, int k) {
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
	
	@SuppressWarnings("unchecked")
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
		for (int i = 0; i < node.getEntries().length; i++) {
			Object o = node.getEntries()[i];
			if (o instanceof QNode) {
				QNode<T> sub = (QNode<T>) o;
				toStringTree(sb, sub, depth+1, i);
			} else if (o != null) {
				QEntry<T> e = (QEntry<T>) o;
				sb.append(prefix + Arrays.toString(e.point()));
				sb.append(" v=" + e.value() + NL);
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
	public static class QStats {
		int nNodes;
		int nNodesInner;
		int nNodesLeaf;
		int maxDepth;
		int nEntries;
		final int[] histoValues = new int[100];
		final int[] histoSubs;
		final int dims;
		public QStats(int dims) {
			this.dims = dims;
			this.histoSubs = new int[1 + (1 << dims)];
		}
		public int getNodeCount() {
			return nNodes;
		}
		public int getEntryCount() {
			return nEntries;
		}
		public int getMaxDepth() {
			return maxDepth;
		}
		@Override
		public String toString() {
			return "nNodes/inner/leaf=" + nNodes + "/" + nNodesInner + "/" + nNodesLeaf + ";\n"
					+ "histoVal:" + Arrays.toString(histoValues) + "\n"
					+ "histoSub:" + Arrays.toString(histoSubs);
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
		return new QQueryIteratorKNN(center, k);
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
