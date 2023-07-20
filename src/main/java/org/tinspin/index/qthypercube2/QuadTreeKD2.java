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
 * create a subnode if a quadrant has to mhold more than one data entry.
 * 
 * 
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class QuadTreeKD2<T> implements PointIndex<T>, PointIndexMM<T> {

	/** Enable basic HCI navigation with Algo #1 isInI(), for example for window queries. */
	public static boolean ENABLE_HCI_1 = true;
	/** Enable basic HCI navigation with Algo #2 inc(), for example for window queries. */
	public static boolean ENABLE_HCI_2 = true;
	
	private static final int MAX_DEPTH = 50;
	
	private static final String NL = System.lineSeparator();

	public static final boolean DEBUG = false;
	//This is the MINIMUM MAX_NODE_SIZE. MAX__NODE_SIZE is adjust upwards automatically
	//with increasing dimensionality
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

	public static <T> QuadTreeKD2<T> create(int dims) {
		int maxNodeSize = DEFAULT_MAX_NODE_SIZE;
		if (2 * dims > DEFAULT_MAX_NODE_SIZE) {
			maxNodeSize = 2*dims;
		}
		return new QuadTreeKD2<>(dims, maxNodeSize);
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
		if (!root.isLeaf() || root.getValueCount() == 0) {
			return;
		}
		if (root.getRadius() == INITIAL_RADIUS) {
			double dist = PointDistanceFunction.L2.dist(key, root.getCenter());
			if (dist > 0) {
				root.adjustRadius(2 * dist);
			} else if (root.getValueCount() >= maxNodeSize - 1) {
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
		// TODO
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

	@Override
	public PointEntryDist<T> query1NN(double[] center) {
		return PointIndex.super.query1NN(center);
	}

	/**
	 *
	 * @param center   center point
	 * @param k        number of neighbors
	 * @param distFn   the point distance function to be used
	 * @return Iterator over query result
	 * @see PointIndexMM#queryKNN(double[], int, PointDistanceFunction)
	 */
	@Override
	public QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k, PointDistanceFunction distFn) {
		return new QIteratorKnn<>(root, k, center, distFn, e -> true);
	}

	@Deprecated
	public List<QEntryDist<T>> knnQuery(double[] center, int k) {
		if (root == null) {
			return Collections.emptyList();
		}
		ArrayList<QEntryDist<T>> candidates = new ArrayList<>();
		rangeSearchKNN(root, center, candidates, k, Double.MAX_VALUE, PointDistanceFunction.L2);
		return candidates;
	}

	@Deprecated
	public List<QEntryDist<T>> knnQuery(double[] center, int k, PointDistanceFunction distFn) {
		if (root == null) {
			return Collections.emptyList();
		}
		ArrayList<QEntryDist<T>> candidates = new ArrayList<>();
		rangeSearchKNN(root, center, candidates, k, Double.MAX_VALUE, distFn);
		return candidates;
	}

	@SuppressWarnings("unchecked")
	private double rangeSearchKNN(QNode<T> node, double[] center, 
    		ArrayList<QEntryDist<T>> candidates, int k, double maxRange, PointDistanceFunction distFn) {
    	int posHC = node.calcSubPosition(center);
    	Object[] entries = node.getEntries();
    	Object alreadyVisited = null;
    	if (!node.isLeaf()) {
    		// Search the best node first
	    	Object ePos = entries[posHC];
	    	if (ePos instanceof QNode) {
				maxRange = rangeSearchKNN((QNode<T>)ePos, center, candidates, k, maxRange, distFn);
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
    			double dist = QUtil.distToRectNode(center, n.getCenter(), n.getRadius(), distFn);
    			addToBuffer(n, dist, maxRange, buffer);
    		} else if (e instanceof QEntry) {
    			QEntry<T> p = (QEntry<T>) e;
   				double dist = distFn.dist(center, p.point());
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
    			maxRange = rangeSearchKNN((QNode<T>)o, center, candidates, k, maxRange, distFn);
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
		final int[] histoValues = new int[100];
		final int[] histoSubs;
		static final int HISTO_MAX = (1 << 10) + 1;
		final int dims;
		public QStats(int dims) {
			super(0, 0, 0);
			this.dims = dims;
			int histoSize = 1 + (1 << dims);
			this.histoSubs = new int[histoSize > HISTO_MAX ? HISTO_MAX : histoSize];
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
	public QueryIterator<PointEntry<T>> iterator() {
		if (root == null) {
			return query(new double[dims], new double[dims]);
		}
		//return query(root.);
		//TODO
		throw new UnsupportedOperationException();
	}

	@Override
	public QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k) {
		return queryKNN(center, k, PointDistanceFunction.L2);
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
