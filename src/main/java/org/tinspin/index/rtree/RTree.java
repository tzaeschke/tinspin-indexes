/*
 * Copyright 2016 Tilmann Zaeschke
 * Modification Copyright 2017 Christophe Schmaltz
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
package org.tinspin.index.rtree;

import java.util.*;
import java.util.function.Predicate;

import org.tinspin.index.*;
import org.tinspin.index.util.MutableRef;
import org.tinspin.index.util.StringBuilderLn;


/**
 * R*Tree implementation based on the paper from
 * Beckmann, N.; Kriegel, H. P.; Schneider, R.; Seeger, B. (1990). 
 * "The R*-tree: an efficient and robust access method for points and rectangles".
 * <p>
 * Revised R*Tree (for DBMS, see conclusion?) -- RR*Tree
 * "A Revised R*-tree in Comparison with Related Index Structures"
 * Norbert Beckmann; Bernhard Seeger
 *
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class RTree<T> implements BoxMap<T>, BoxMultimap<T> {

	static final int NODE_MAX_DIR = 10;//56; //PAPER: M=56 for 1KB pages
	static final int NODE_MAX_DATA = 10;//50; //PAPER: M=50 for 1KB pages
	//PAPER: m = 20% of M
	static final int NODE_MIN_DIR = 2;//11;  //2 <= min <= max/2
	static final int NODE_MIN_DATA = 2;//10;  //2 <= min <= max/2
	public static final boolean DEBUG = false;
	private final int dims;
	private int size = 0;
	//number of levels
	private int depth;
	private RTreeNode<T> root;
	private int nNodes = 0;
	private long nDist1NN = 0;
	private long nDistKNN = 0;
	
	static RTreeLogic logic = new RStarTreeLogic();
	
	/**
	 * Create an RTree. By default, it is an R*tree.
	 * @param dims dimensionality
	 */
	protected RTree(int dims) {
		this.dims = dims;
		if (DEBUG) {
			System.err.println("WARNING: Using DEBUG mode.");
		}
		init();
	} 
	
	public static <T> RTree<T> createRStar(int dims) {
		return new RTree<>(dims);
	}
	
	private void init() {
		this.root = new RTreeNodeLeaf<>(dims);
		this.nNodes = 1;
		this.depth = 1;
		this.size = 0;
	}

	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#getDims()
	 */
	@Override
	public int getDims() {
		return dims;
	}
	
	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#size()
	 */
	@Override
	public int size() {
		return size;
	}
	
	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#clear()
	 */
	@Override
	public void clear() {
		init();
	}
	
	public void insert(double[] point, T value) {
		insert(new Entry<>(point, point, value));
	}

	/**
	 * Insert a rectangle.
	 * @param keyMin min
	 * @param keyMax max
	 * @param value value
	 */
	@Override
	public void insert(double[] keyMin, double[] keyMax, T value) {
		insert(new Entry<>(keyMin, keyMax, value));
	}
	
	/**
	 * Insert an entry.
	 * @param e the entry
	 */
	public void insert(Entry<T> e) {
		size++;
		insertAtDepth(e, 0);
	}
	
	private void insertAtDepth(Entry<T> e, int desiredInsertionLevel) {
		boolean[] blockedLevels = new boolean[depth];
		insert(e, blockedLevels, desiredInsertionLevel);
	}
	
	/**
	 * @param e entry
	 * @param desiredInsertionLevel Entries have to be inserted at the desired level.
	 * The level is usually '0' (for data points) but can be higher for
	 * reinsertion of subtrees.
	 */
	private void insert(Entry<T> e, boolean[] blockedLevels, int desiredInsertionLevel) {
		//I1
		RTreeNode<T> node = logic.chooseSubTree(root, e, desiredInsertionLevel, depth);
		//I2
		if (logic.hasSpace(node)) {
			node.addEntry(e);
			node.extendParentMBB();
		} else {
			RTreeNode<T> newNode = overflowTreatment(
					node, e, blockedLevels, desiredInsertionLevel);
			//I3 propagate overflow up the tree.
			if (newNode != null) {
				nNodes++;
				if (desiredInsertionLevel+1 < depth) {
					insert(newNode, blockedLevels, desiredInsertionLevel+1);
				} else {
					RTreeNodeDir<T> newRoot = new RTreeNodeDir<>(dims);
					nNodes++; //for the new root
					newRoot.addEntry(newNode);
					newRoot.addEntry(root);
					root = newRoot;
					depth++;
				}
			}
		}
		//I4 adjust governing rectangles
		// -> done inside split/reinsert
	}
	
	private RTreeNode<T> overflowTreatment(RTreeNode<T> node,
			Entry<T> e, boolean[] blockedLevels, int desiredInsertionLevel) {
		//OT1
		if (node != root && !blockedLevels[desiredInsertionLevel]) {
			blockedLevels[desiredInsertionLevel] = true;
			Entry<T>[] toReinsert = logic.reInsert(node, e);
			for (int i = 0; i < toReinsert.length; i++) {
				insert(toReinsert[i], blockedLevels, desiredInsertionLevel);
			}
			return null;
		} else {
			return logic.split(node, e);
		}
	}

	public void load(Entry<T>[] entries) {
		STRLoader<T> bulkLoader = new STRLoader<>();
		bulkLoader.load(entries);
		size = bulkLoader.getSize();
		nNodes = bulkLoader.getNNodes();
		root = bulkLoader.getRoot();
		depth = bulkLoader.getDepth();
	}

	public Object remove(double[] point) {
		//TODO speed up
		return remove(point, point);
	}

	/**
	 * Remove an entry.
	 * @param min min
	 * @param max max
	 * @return the value of the entry or null if the entry was not found
	 */
	@Override
	public T remove(double[] min, double[] max) {
		MutableRef<T> ref = new MutableRef<>();
		Predicate<Entry<T>> pred = e -> {
			ref.set(e.checkExactMatch(min, max) ? e.value() : null);
			return ref.get() != null;
		};
		findNodes(min, max, root, node -> deleteFromNode(node, pred));
		return ref.get();
	}

	/**
	 * Remove an entry.
	 *
	 * @param min min
	 * @param max max
	 * @return the value of the entry or null if the entry was not found
	 */
	@Override
	public boolean remove(double[] min, double[] max, T value) {
		return removeIf(min, max, e -> Objects.equals(value, e.value()));
	}

	/**
	 * Remove the first entry matching the geometry and condition.
	 * @param min min
	 * @param max max
	 * @param condition Condition for deletion.
	 * @return `true` iff an entry was found and removed
	 */
	@Override
	public boolean removeIf(double[] min, double[] max, Predicate<BoxEntry<T>> condition) {
		Predicate<Entry<T>> pred = e -> e.checkExactMatch(min, max) && condition.test(e);
		return findNodes(min, max, root, node -> deleteFromNode(node, pred));
	}

	/**
	 * Update the position of an entry.
	 * @param lo1 old min
	 * @param up1 old max
	 * @param lo2 new min
	 * @param up2 new max
	 * @return the value, or null if the entries was not found
	 */
	@Override
	public T update(double[] lo1, double[] up1, double[] lo2, double[] up2) {
		T val = remove(lo1, up1);
		if (val != null) {
			insert(lo2, up2, val);
		}
		return val;
	}

	/**
	 * Update the position of an entry.
	 *
	 * @param lo1   old min
	 * @param up1   old max
	 * @param lo2   new min
	 * @param up2   new max
	 * @param value only entry with the given value is updated
	 * @return `true` iff the entry was found and relocated
	 */
	@Override
	public boolean update(double[] lo1, double[] up1, double[] lo2, double[] up2, T value) {
		// TODO improve, try to move within node
		if (remove(lo1, up1, value)) {
			insert(lo2, up2, value);
			return true;
		}
		return false;
	}

	@Override
	public boolean contains(double[] min, double[] max) {
		return findNodeEntry(min, max, (entry, node, posInNode) -> true) != null;
	}

	@Override
	public boolean contains(double[] min, double[] max, T value) {
		return findNodeEntry(min, max, (entry, node, posInNode) -> Objects.equals(value, entry.value())) != null;
	}

	private interface Matcher<T> {
		boolean test(Entry<T> entry, RTreeNode<T> node, int posInNode);
	}

	private interface CheckNodeAbort<T> {
		boolean checkAbort(RTreeNodeLeaf<T> node);
	}

	// TODO either remove findNodeEntry() or remove findNodes()
	//     --> test performance!
	private T findNodeEntry(double[] min, double[] max, Matcher<T> matcher) {
		int[] positions = new int[depth];
		int level = depth-1;
		RTreeNode<T> node = root;
		outer:
		while (level < depth) {
			int pos = positions[level];
			if (node instanceof RTreeNodeDir) {
				ArrayList<RTreeNode<T>> children = ((RTreeNodeDir<T>)node).getChildren();
				for (int i = pos; i < children.size(); i++) {
					RTreeNode<T> sub = children.get(i);
					if (sub.checkInclusion(min, max)) {
						positions[level] = i+1;
						level--;
						node = sub;
						positions[level] = 0;
						continue outer;
					}
				}
			} else {
				ArrayList<Entry<T>> children = node.getEntries();
				for (int i = 0; i < children.size(); i++) {
					Entry<T> e = children.get(i);
					if (e.checkExactMatch(min, max) && matcher.test(e, node, i)) {
						return e.value();
					}
				}
			}
			node = node.getParent();
			level++;
		}
		return null;
	}

	void deleteFromNode(RTreeNode<T> node, int pos) {
		size--;
		//this also adjusts parent MBBs
		//Question: Should we adjust parent MBBs later if we have to remove the sub-node?
		//-> But later adjustment would skew with reinsertion, because the
		//node may look bigger than it actually is.
		//TODO no need to remove the entry (update the MBBs if we gonna remove the node...
		//TODO check inside 'removeEntry????'
		node.removeEntry(pos);
		int level = 0;
		while (node != root && node.isUnderfull()) {
			ArrayList<Entry<T>> entries = node.getEntries();
			RTreeNodeDir<T> parent = node.getParent();
			parent.removeChildByIdentity(node);
			node = parent;
			nNodes--;
			for (int i = 0; i < entries.size(); i++) {
				insertAtDepth(entries.get(i), level);
			}
			level++;
		}
		if (root.getEntries().size() == 1 && root instanceof RTreeNodeDir) {
			depth--;
			nNodes--;
			root = (RTreeNode<T>) root.getEntries().get(0);
			root.setParent(null);
		}
	}

	private boolean findNodes(double[] min, double[] max, RTreeNode<T> node, CheckNodeAbort<T> checkAbort) {
		if (node instanceof RTreeNodeDir) {
			ArrayList<RTreeNode<T>> children = ((RTreeNodeDir<T>)node).getChildren();
			for (int i = 0; i < children.size(); i++) {
				RTreeNode<T> sub = children.get(i);
				if (sub.checkInclusion(min, max) && findNodes(min, max, sub, checkAbort)) {
					return true;
				}
			}
		} else {
			return checkAbort.checkAbort((RTreeNodeLeaf<T>) node);
		}
		return false;
	}

	boolean deleteFromNode(RTreeNode<T> node, Predicate<Entry<T>> pred) {
		//this also adjusts parent MBBs
		//Question: Should we adjust parent MBBs later if we have to remove the sub-node?
		//-> But later adjustment would skew with reinsertion, because the
		//node may look bigger than it actually is.
		//TODO no need to remove the entry (update the MBBs if we gonna remove the node...
		//TODO check inside 'removeEntry????'
		boolean found = false;
		for (int i = 0; i < node.getEntries().size(); ++i) {
			if (pred.test(node.getEntries().get(i))) {
				node.removeEntry(i);
				size--;
				found = true;
				break;
			}
		}

		int level = 0;
		while (node != root && node.isUnderfull()) {
			ArrayList<Entry<T>> entries = node.getEntries();
			RTreeNodeDir<T> parent = node.getParent();
			parent.removeChildByIdentity(node);
			node = parent;
			nNodes--;
			for (int i = 0; i < entries.size(); i++) {
				insertAtDepth(entries.get(i), level);
			}
			level++;
		}
		if (root.getEntries().size() == 1 && root instanceof RTreeNodeDir) {
			depth--;
			nNodes--;
			root = (RTreeNode<T>) root.getEntries().get(0);
			root.setParent(null);
		}
		return found;
	}

	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#queryEntry(double[], double[])
	 */
	@Override
	public T queryExact(double[] min, double[] max) {
		return findNodeEntry(min, max, (entry, node, posInNode) -> true);
	}
	
	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#iterator()
	 */
	@Override
	public RTreeIterator<T> iterator() {
		double[] min = new double[dims];
		double[] max = new double[dims];
		Arrays.fill(min, Double.NEGATIVE_INFINITY);
		Arrays.fill(max, Double.POSITIVE_INFINITY);
		return new RTreeIterator<>(this, min, max);
	}

	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#queryOverlap(double[], double[])
	 */
	@Override
	public RTreeIterator<T> queryExactBox(double[] min, double[] max) {
		return RTreeIterator.createExactMatch(this, min, max);
	}

	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#queryOverlap(double[], double[])
	 */
	@Override
	public RTreeIterator<T> queryIntersect(double[] min, double[] max) {
		return new RTreeIterator<>(this, min, max);
	}
	
	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#query1N
	 */
	@Override
	public BoxEntryKnn<T> query1nn(double[] center) {
		return new RTreeQuery1nn<>(this).reset(center, BoxDistance.EDGE);
	}
	
	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#queryKNN(double[], int, org.tinspin.index.rtree.RectangleDistanceFunction)
	 */
	@Override
	public RTreeQueryKnn<T> queryKnn(double[] center, int k) {
		return queryKnn(center, k, BoxDistance.EDGE);
	}

	@Override
	public RTreeQueryKnn<T> queryKnn(double[] center, int k, BoxDistance dist) {
		return new RTreeQueryKnn<>(this, k, center, dist, (e, d) -> true);
	}

	public Iterable<BoxEntryKnn<T>> queryRangedNearestNeighbor(
			double[] center, BoxDistance dist,
			BoxDistance closestDist, double[] minBound, double[] maxBound) {
		return queryRangedNearestNeighbor(center, dist, closestDist, 
				new Filter.RectangleIntersectFilter(minBound, maxBound));
	}
	
	/**
	 * This method returns an Iterable which returns the nodes by a combined range and
	 * nearest number search.
	 * The Iterator supports the {@code Iterator.remove()} method. 
	 * 
	 * @param center       Target position passed as parameter to the distance functions. 
	 *                     Can be {@code null} if your distance function supports 
	 *                     it (like {@code RectangleDistanceFunction.RectangleDist}).
	 * @param dist         Distance function used to compare entries 
	 * 				       (example: {@code RectangleDistanceFunction.EDGE} or {@code RectangleDistanceFunction.CENTER})
	 * @param closestDist  Distance of the best point in a given rectangle  
	 *                     (example: {@code RectangleDistanceFunction.EDGE} but *not* {@code RectangleDistanceFunction.CENTER})
	 * @param filter       Filter to limit the results for range queries 
	 *                     (example: {@code new Filter.RectangleIntersectFilter(min, max)})
	 * @return             An Iterable which lazily calculates the nearest neighbors. 
	 */
	public Iterable<BoxEntryKnn<T>> queryRangedNearestNeighbor(double[] center, BoxDistance dist,
															   BoxDistance closestDist, Filter filter) {
		RTree<T> self = this;
		return () -> new RTreeMixedQuery<>(self, center, filter, dist, closestDist);
	}
	
	@Override
	public String toStringTree() {
		StringBuilderLn sb = new StringBuilderLn();
		toStringTree(sb, root, depth-1);
		return sb.toString();
	}
	
	private void toStringTree(StringBuilderLn sb, RTreeNode<T> node, int level) {
		String pre = " ".repeat(depth-level);
		sb.appendLn(pre + "L=" + level + " " + node.toString() +
				";P=" + System.identityHashCode(node.getParent()));
		
		ArrayList<Entry<T>> entries = node.getEntries();
		for (int i = 0; i < entries.size(); i++) {
			Entry<T> e = entries.get(i);
			if (e instanceof RTreeNode) {
				toStringTree(sb, (RTreeNode<T>) e, level-1);
			} else {
				sb.append(pre).append("e:").append(e.toString()).appendLn();
			}
		}
	}

	public static class RTreeStats extends Stats {
		RTreeStats(RTree<?> tree) {
			super(tree.nDist1NN + tree.nDistKNN, tree.nDist1NN, tree.nDistKNN);
			minLevel = 0;
			maxLevel = tree.depth;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#getStats()
	 */
	@Override
	public RTreeStats getStats() {
		RTreeStats stats = new RTreeStats(this);
		stats.dims = dims;
		stats.maxDepth = depth;
		getStats(stats, root, depth-1);
		if (stats.nEntries != size) {
			throw new IllegalStateException();
		}
		if (stats.nNodes != nNodes) {
			throw new IllegalStateException("Node count/nNodes " + stats.nNodes + "/" + nNodes);
		}
		return stats;
	}
	
	@SuppressWarnings("unused")
	private void getStats(RTreeStats stats, RTreeNode<T> node, int level) {
		if (level < 0) {
			throw new IllegalStateException();
		}
		stats.nNodes ++;
		
		if (node instanceof RTreeNodeLeaf && level != 0) {
			throw new IllegalStateException();
		}
		
		ArrayList<Entry<T>> entries = node.getEntries();
		for (int i = 0; i < entries.size(); i++) {
			Entry<T> e = entries.get(i);
			if (!node.checkInclusion(e.min(), e.max())) {
				throw new IllegalStateException();
			}
			if (e instanceof RTreeNode) {
				getStats(stats, (RTreeNode<T>) e, level-1);
			} else {
				stats.nEntries++;
			}
		}

		if (node instanceof RTreeNodeLeaf && node != root && entries.size() < NODE_MIN_DATA) {
			throw new IllegalStateException();
		}
		if (node instanceof RTreeNodeLeaf && entries.size() > NODE_MAX_DATA) {
			throw new IllegalStateException();
		}
		if (node instanceof RTreeNodeDir && node != root && entries.size() < NODE_MIN_DIR) {
			throw new IllegalStateException();
		}
		if (node instanceof RTreeNodeDir && entries.size() > NODE_MAX_DIR) {
			throw new IllegalStateException();
		}

		// For debugging:
		//		if (node instanceof RTreeNodeLeaf) {
		//			for (int i = 0; i < entries.size(); i++) {
		//				Entry<T> e = entries.get(i);
		//				for (int j = i+1; j < entries.size(); j++) {
		//					if (Entry.checkOverlap(e.min(), e.max(), entries.get(j))) {
		//						System.out.println("Overlap 1: " + e);
		//						System.out.println("Overlap 2: " + entries.get(j));
		//						System.out.println("Overlap 1 parent : " +
		//								((RTreeNode<T>)e).getParent());
		//						System.out.println("Overlap 2 parent : " +
		//								((RTreeNode<T>)entries.get(j)).getParent());
		//						throw new IllegalStateException();
		//					}
		//				}
		//			}
		//		}
	}

	@Override
	public int getDepth() {
		return depth;
	}
	
	/* (non-Javadoc)
	 * @see org.tinspin.index.rtree.Index#getNodeCount()
	 */
	@Override
	public int getNodeCount() {
		return nNodes;
	}
	
	protected RTreeNode<T> getRoot() {
		return root;
	}
	
	@Override
	public String toString() {
		return "RTreeZ;" + logic.getClass().getSimpleName() +
				";size=" + size + ";nNodes=" + nNodes +
				";DEBUG=" + DEBUG +
				";dir_m/M=" + NODE_MIN_DIR + "/" + NODE_MAX_DIR +
				";data_m/M=" + NODE_MIN_DATA + "/" + NODE_MAX_DATA;
	}

	void incNDist1NN() {
		nDist1NN++;		
	}

	void incNDistKNN() {
		nDistKNN++;
	}
}
