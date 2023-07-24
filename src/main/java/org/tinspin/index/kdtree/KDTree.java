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
package org.tinspin.index.kdtree;

import java.util.*;
import java.util.function.Predicate;

import org.tinspin.index.*;
import org.tinspin.index.util.MutableRef;
import org.tinspin.index.util.StringBuilderLn;

/**
 * A simple KD-Tree implementation. 
 * 
 * @author T. Zäschke
 *
 * @param <T> Value type
 */
public class KDTree<T> implements PointMap<T>, PointMultimap<T> {

	public static final boolean DEBUG = false;

	private final int dims;
	/** Defensive keys copying. If `false`, the kd-tree will store the passed in
	 * double[] keys internally (this reduces required memory).
	 * If `true`, the keys are copied in order to avoid accidental modification. */
	private final boolean defensiveKeyCopy;
	private int size = 0;
	private int modCount = 0;
	private long nDist1NN = 0;
	private long nDistKNN = 0;
	//During insertion, the tree maintains an invariant that if two points have the
	//same value in any dimension, then one key is never in the 'lower' branch of the other.
	//This allows very efficient look-up because we have to follow only a single path.
	//Unfortunately, removing keys (and moving up keys in the tree) may break this invariant.
	//
	//The cost of breaking the invariant is that there may be two branches that contain the same
	//key. This makes searches more expensive simply because the code gets more complex.
	//
	//One solution to maintain the invariant is to repair the invariant by moving all points 
	//with the same value into the 'upper' part of the point that was moved up. 
	//Identifying these points is relatively cheap, we can do this while searching for 
	//the min/max during removal. However, _moving_ these point may be very expensive.
	//
	//Our (pragmatic) solution is to identify when the invariant gets broken.
	//When it is not broken, we use the simple search. If it gets broken, we use the slower search.
	//This is especially useful in scenarios where 'remove()' is not required or where
	//points have never the same values (such as for physical measurements or other experimental results).
	private boolean invariantBroken = false;

	private Node<T> root;


	private KDTree(int dims, boolean defensiveKeyCopy) {
		if (DEBUG) {
			System.err.println("Warning: DEBUG enabled");
		}
		this.dims = dims;
		this.defensiveKeyCopy = defensiveKeyCopy;
	}

	public static <T> KDTree<T> create(int dims) {
		return new KDTree<>(dims, true);
	}

	public static <T> KDTree<T> create(IndexConfig config) {
		return new KDTree<>(config.getDimensions(), config.getDefensiveKeyCopy());
	}

	/**
	 * Insert a key-value pair.
	 *
	 * @param key   the key
	 * @param value the value
	 */
	@Override
	public void insert(double[] key, T value) {
		size++;
		modCount++;
		if (root == null) {
			root = new Node<>(key, value, 0, defensiveKeyCopy);
			return;
		}
		Node<T> n = root;
		while ((n = n.getClosestNodeOrAddPoint(key, value, dims, defensiveKeyCopy)) != null) ;
	}

	/**
	 * Check whether a given key exists.
	 *
	 * @param key the key to check
	 * @return true iff the key exists
	 */
	public boolean containsExact(double[] key) {
		return findNodeExact(key, new RemoveResult<>(), e -> true) != null;
	}

	/**
	 * Lookup an entry, using exact match.
	 *
	 * @param point the point
	 * @return an iterator over all entries at the given point
	 */
	@Override
	public KDIterator<T> query(double[] point) {
		return query(point, point);
	}

	/**
	 * Get the value associates with the key.
	 *
	 * @param key the key to look up
	 * @return the value for the key or 'null' if the key was not found
	 */
	@Override
	public T queryExact(double[] key) {
		Node<T> e = findNodeExact(key, new RemoveResult<>(), entry -> true);
		return e == null ? null : e.value();
	}

	private Node<T> findNodeExact(double[] key, RemoveResult<T> resultDepth, Predicate<PointEntry<T>> filter) {
		if (root == null) {
			return null;
		}
		return invariantBroken
				? findNodeExactSlow(key, root, null, resultDepth, filter)
				: findNodeExactFast(key, null, resultDepth, filter);
	}

	private Node<T> findNodeExactFast(double[] key, Node<T> parent, RemoveResult<T> resultDepth, Predicate<PointEntry<T>> filter) {
		Node<T> n = root;
		do {
			double[] nodeKey = n.point();
			double nodeX = nodeKey[n.getDim()];
			double keyX = key[n.getDim()];
			if (keyX == nodeX && Arrays.equals(key, nodeKey) && filter.test(n)) {
				resultDepth.pos = n.getDim();
				resultDepth.nodeParent = parent;
				return n;
			}
			parent = n;
			n = (keyX >= nodeX) ? n.getHi() : n.getLo();
		} while (n != null);
		return n;
	}

	private Node<T> findNodeExactSlow(double[] key, Node<T> n, Node<T> parent, RemoveResult<T> resultDepth, Predicate<PointEntry<T>> filter) {
		do {
			double[] nodeKey = n.point();
			double nodeX = nodeKey[n.getDim()];
			double keyX = key[n.getDim()];
			if (keyX == nodeX) {
				if (Arrays.equals(key, nodeKey) && filter.test(n)) {
					resultDepth.pos = n.getDim();
					resultDepth.nodeParent = parent;
					return n;
				}
				//Broken invariant? We need to check the 'lower' part as well...
				if (n.getLo() != null) {
					Node<T> n2 = findNodeExactSlow(key, n.getLo(), n, resultDepth, filter);
					if (n2 != null) {
						return n2;
					}
				}
			}
			parent = n;
			n = (keyX >= nodeX) ? n.getHi() : n.getLo();
		} while (n != null);
		return n;
	}

	/**
	 * Remove all entries at the given point.
	 *
	 * @param key the point
	 * @return `true` iff an entry was found and removed
	 */
	@Override
	public boolean remove(double[] key, T value) {
		return removeIf(key, e -> Objects.equals(e.value(), value));
	}

	/**
	 * Remove a key.
	 * @param key key to remove
	 * @return the value associated with the key or 'null' if the key was not found
	 */
	@Override
	public T remove(double[] key) {
		MutableRef<T> ref = new MutableRef<>();
		removeIf(key, e -> {
			ref.set(e.value());
			return true;
		});
		return ref.get();
	}

	@Override
	public boolean removeIf(double[] key, Predicate<PointEntry<T>> pred) {
		if (root == null) {
			return false;
		}

		invariantBroken = true;

		//find
		RemoveResult<T> removeResult = new RemoveResult<>();
		Node<T> eToRemove = findNodeExact(key, removeResult, pred);
		if (eToRemove == null) {
			return false;
		}

		//remove
		modCount++;
		T value = eToRemove.value();
		if (eToRemove == root && size == 1) {
			root = null;
			size = 0;
			invariantBroken = false;
			return true;
		}
		
		// find replacement
		while (eToRemove != null && !eToRemove.isLeaf()) {
			//recurse
			int pos = removeResult.pos;
			removeResult.node = null; 
			//randomize search direction (modCount)
//			if (((modCount & 0x1) == 0 || eToRemove.getHi() == null) && eToRemove.getLo() != null) {
//				//get replacement from left
//				removeResult.best = Double.NEGATIVE_INFINITY;
//				removeMaxLeaf(eToRemove.getLo(), eToRemove, pos, removeResult);
//			} else if (eToRemove.getHi() != null) {
//				//get replacement from right
//				removeResult.best = Double.POSITIVE_INFINITY;
//				removeMinLeaf(eToRemove.getHi(), eToRemove, pos, removeResult);
//			}
			if (eToRemove.getHi() != null) {
				//get replacement from right
				//This is preferable, because it cannot break the invariant
				removeResult.best = Double.POSITIVE_INFINITY;
				removeMinLeaf(eToRemove.getHi(), eToRemove, pos, removeResult);
			} else if (eToRemove.getLo() != null) {
				//get replacement from left
				removeResult.best = Double.NEGATIVE_INFINITY;
				removeMaxLeaf(eToRemove.getLo(), eToRemove, pos, removeResult);
			}
			eToRemove.set(removeResult.node.point(), removeResult.node.value());
			eToRemove = removeResult.node;
		} 
		//leaf node
		Node<T> parent = removeResult.nodeParent; 
		if (parent != null) {
			if (parent.getLo() == eToRemove) {
				parent.setLeft(null);
			} else if (parent.getHi() == eToRemove) {
				parent.setRight(null);
			} else { 
				throw new IllegalStateException();
			}
		}
		size--;
		return true;
	}

	private static class RemoveResult<T> {
		Node<T> node = null;
		Node<T> nodeParent = null;
		double best;
		int pos;
	}
	
	private void removeMinLeaf(Node<T> node, Node<T> parent, int pos, RemoveResult<T> result) {
		//Split in 'interesting' dimension
		if (pos == node.getDim()) {
			//We strictly look for leaf nodes with left==null
			// -> left!=null means the left child is at least as small as the current node
			if (node.getLo() != null) {
				removeMinLeaf(node.getLo(), node, pos, result);
			} else if (node.point()[pos] <= result.best) {
				result.node = node;
				result.nodeParent = parent;
				result.best = node.point()[pos];
				result.pos = node.getDim();
			}
		} else {
			//split in any other dimension.
			//First, check local key. 
			double localX = node.point()[pos];
			if (localX <= result.best) {
				result.node = node;
				result.nodeParent = parent;
				result.best = localX;
				result.pos = node.getDim();
			}
			if (node.getLo() != null) {
				removeMinLeaf(node.getLo(), node, pos, result);
			}
			if (node.getHi() != null) {
				removeMinLeaf(node.getHi(), node, pos, result);
			}
		}
	}
	
	private void removeMaxLeaf(Node<T> node, Node<T> parent, int pos, RemoveResult<T> result) {
		//Split in 'interesting' dimension
		if (pos == node.getDim()) {
			//We strictly look for leaf nodes with left==null
			if (node.getHi() != null) {
				removeMaxLeaf(node.getHi(), node, pos, result);
			} else if (node.point()[pos] >= result.best) {
				result.node = node;
				result.nodeParent = parent;
				result.best = node.point()[pos];
				result.pos = node.getDim();
			}
		} else {
			//split in any other dimension.
			//First, check local key. 
			double localX = node.point()[pos];
			if (localX >= result.best) {
				result.node = node;
				result.nodeParent = parent;
				result.best = localX;
				result.pos = node.getDim();
			}
			if (node.getLo() != null) {
				removeMaxLeaf(node.getLo(), node, pos, result);
			}
			if (node.getHi() != null) {
				 removeMaxLeaf(node.getHi(), node, pos, result);
			}
		}
	}
	
	/**
	 * Reinsert the key.
	 * @param oldKey old key
	 * @param newKey new key
	 * @return the value associated with the key or 'null' if the key was not found.
	 */
	@Override
	public T update(double[] oldKey, double[] newKey) {
		if (root == null) {
			return null;
		}
		T value = remove(oldKey);
		insert(newKey, value);
		return value;
	}

	/**
	 * Reinsert the key.
	 *
	 * @param oldKey old key
	 * @param newKey new key
	 * @param value  the value of the entry that should be updated
	 * @return `true` iff the entry was found and updated
	 */
	@Override
	public boolean update(double[] oldKey, double[] newKey, T value) {
		if (root == null) {
			return false;
		}
		if (remove(oldKey, value)) {
			insert(newKey, value);
			return true;
		}
		return false;
	}

	@Override
	public boolean contains(double[] key, T value) {
		return findNodeExact(key, new RemoveResult<>(), e -> Objects.equals(value, e.value())) != null;
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
		invariantBroken = false;
		modCount++;
	}

	/**
	 * Query the tree, returning all points in the axis-aligned rectangle between 'min' and 'max'.
	 * @param min lower left corner of query
	 * @param max upper right corner of query
	 * @return all entries in the rectangle
	 */
	@Override
	public KDIterator<T> query(double[] min, double[] max) {
		return new KDIterator<>(this, min, max);
	}

	static boolean isEnclosed(double[] point, double[] min, double[] max) {
		for (int i = 0; i < point.length; i++) {
			if (point[i] < min[i] || point[i] > max[i]) {
				return false;
			}
		}
		return true;
	}

	private List<PointEntryKnn<T>> knnQuery(double[] center, int k, PointDistance distFn) {
		if (root == null) {
			return Collections.emptyList();
		}
		ArrayList<PointEntryKnn<T>> candidates = new ArrayList<>(k);
		rangeSearchKNN(root, center, candidates, k, Double.POSITIVE_INFINITY, distFn);
		return candidates;
	}

	private double rangeSearchKNN(Node<T> node, double[] center,
                                  ArrayList<PointEntryKnn<T>> candidates, int k, double maxRange, PointDistance distFn) {
    	int pos = node.getDim();
    	if (node.getLo() != null && (center[pos] < node.point()[pos] || node.getHi() == null)) {
        	//go down
    		maxRange = rangeSearchKNN(node.getLo(), center, candidates, k, maxRange, distFn);
        	//refine result
    		if (center[pos] + maxRange >= node.point()[pos]) {
    			maxRange = addCandidate(node, center, candidates, k, maxRange, distFn);
        		if (node.getHi() != null) {
        			maxRange = rangeSearchKNN(node.getHi(), center, candidates, k, maxRange, distFn);
        		}
    		}
    	} else if (node.getHi() != null) {
        	//go down
    		maxRange = rangeSearchKNN(node.getHi(), center, candidates, k, maxRange, distFn);
        	//refine result
    		if (center[pos] <= node.point()[pos] + maxRange) {
    			maxRange = addCandidate(node, center, candidates, k, maxRange, distFn);
        		if (node.getLo() != null) {
        			maxRange = rangeSearchKNN(node.getLo(), center, candidates, k, maxRange, distFn);
        		}
    		}
    	} else {
    		//leaf -> first (probably best) match!
    		maxRange = addCandidate(node, center, candidates, k, maxRange, distFn);
    	}
    	return maxRange;
    }


    private static final Comparator<PointEntryKnn<?>> compKnn =
    		(PointEntryKnn<?> point1, PointEntryKnn<?> point2) -> {
    			double deltaDist = point1.dist() - point2.dist();
    			return deltaDist < 0 ? -1 : (deltaDist > 0 ? 1 : 0);
    		};

    
    private double addCandidate(Node<T> node, double[] center,
                                ArrayList<PointEntryKnn<T>> candidates, int k, double maxRange, PointDistance distFn) {
    	nDistKNN++;
    	//add ?
    	double dist = distFn.dist(center, node.point());
    	if (dist > maxRange) {
    		//don't add if too far away
    		return maxRange;
    	}
    	if (dist == maxRange && candidates.size() >= k) {
    		//don't add if we already have enough equally good results.
    		return maxRange;
    	}
		PointEntryKnn<T> cand;
    	if (candidates.size() >= k) {
    		cand = candidates.remove(k - 1);
    		cand.set(node, dist);
    	} else {
    		cand = new PointEntryKnn<>(node, dist);
    	}
    	int insertionPos = Collections.binarySearch(candidates, cand, compKnn);
    	insertionPos = insertionPos >= 0 ? insertionPos : -(insertionPos+1);
    	candidates.add(insertionPos, cand);
    	return candidates.size() < k ? maxRange : candidates.get(candidates.size() - 1).dist();
    }


    private static class KDQueryIteratorKnn<T> implements PointIteratorKnn<T> {

    	private Iterator<PointEntryKnn<T>> it;
    	private final KDTree<T> tree;
		private final PointDistance distFn;

		public KDQueryIteratorKnn(KDTree<T> tree, double[] center, int k, PointDistance distFn) {
			this.tree = tree;
			this.distFn = distFn;
			reset(center, k);
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public PointEntryKnn<T> next() {
			return it.next();
		}

		@Override
		public KDQueryIteratorKnn<T> reset(double[] center, int k) {
			it = tree.knnQuery(center, k, distFn).iterator();
			return this;
		}
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
			toStringTree(sb, root, 0);
		}
		return sb.toString();
	}
	
	private void toStringTree(StringBuilderLn sb, Node<T> node, int depth) {
		if (node.getLo() != null) {
			toStringTree(sb, node.getLo(), depth+1);
		}
		for (int i = 0; i < depth; i++) {
			sb.append(".");
		}
		sb.append(" ");
		sb.append(Arrays.toString(node.point()));
		sb.append(" v=").append(node.value());
		sb.append(" l/r=");
		sb.append(node.getLo() == null ? null : Arrays.toString(node.getLo().point()));
		sb.append("/");
		sb.append(node.getHi() == null ? null : Arrays.toString(node.getHi().point()));
		sb.appendLn();
		if (node.getHi() != null) {
			toStringTree(sb, node.getHi(), depth+1);
		}
	}
	
	@Override
	public String toString() {
		return "KDTree;size=" + size + 
				";DEBUG=" + DEBUG + 
				";center=" + (root==null ? "null" : Arrays.toString(root.point()));
	}
	
	@Override
	public KDStats getStats() {
		KDStats s = new KDStats(this);
		if (root != null) {
			root.checkNode(s, 0);
		}
		return s;
	}
	
	/**
	 * Statistics container class.
	 */
	public static class KDStats extends Stats {
		public KDStats(KDTree<?> tree) {
			super(tree.nDist1NN + tree.nDistKNN, tree.nDist1NN, tree.nDistKNN);
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
	public PointEntryKnn<T> query1nn(double[] center) {
		return queryKnn(center, 1).next();
	}

	@Override
	public PointIteratorKnn<T> queryKnn(double[] center, int k) {
		if (size < 1_000_000) {
			return new KDQueryIteratorKnn<>(this, center, k, PointDistance.L2);
		}
		return new KDIteratorKnn<>(root, k, center, PointDistance.L2, e -> true);
	}

	@Override
	public PointIteratorKnn<T> queryKnn(double[] center, int k, PointDistance distFn) {
		// For small trees, the old iterator is about 3x fatser.
		// For 1M it is about even, for 10M the new HS-iterator is about 2x faster.
		if (size < 1_000_000) {
			return new KDQueryIteratorKnn<>(this, center, k, distFn);
		}
		return new KDIteratorKnn<>(root, k, center, distFn, e -> true);
	}

	@Override
	public int getNodeCount() {
		return getStats().getNodeCount();
	}

	@Override
	public int getDepth() {
		return getStats().getMaxDepth();
	}
	
	Node<T> getRoot() {
		return root;
	}
}
