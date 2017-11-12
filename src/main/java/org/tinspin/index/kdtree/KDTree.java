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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import org.tinspin.index.PointEntry;
import org.tinspin.index.PointEntryDist;
import org.tinspin.index.PointIndex;
import org.tinspin.index.QueryIterator;
import org.tinspin.index.QueryIteratorKNN;

import ch.ethz.globis.phtree.util.Tools;

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
 * @param <T>
 */
public class KDTree<T> implements PointIndex<T> {

	private static final String NL = System.lineSeparator();

	public static final boolean DEBUG = false;
	
	private final int dims;
	private int size = 0; 
	private int modCount = 0;
	
	private Node<T> root;
	
	public static void main(String ... args) {
//		double[][] point_list = {{2,3}, {5,4}, {9,6}, {4,7}, {8,1}, {7,2}};
		double[][] point_list = new double[100][2];
		Random R = new Random(0);
		for (double[] p : point_list) {
			Arrays.setAll(p, (i) -> { return (double)R.nextInt(100);} );
		}
		KDTree<double[]> tree = create(2);
		for (double[] data : point_list) {
			tree.insert(data, data);
		}
	    System.out.println(tree.toStringTree());
		for (double[] key : point_list) {
			if (!tree.containsExact(key)) {
				throw new IllegalStateException("" + Arrays.toString(key));
			}
		}
		for (double[] key : point_list) {
			System.out.println(Arrays.toString(tree.queryExact(key)));
		}
	    System.out.println(tree.toStringTree());
	    
		for (double[] key : point_list) {
			System.out.println(tree.toStringTree());
			System.out.println("Removing: " + Arrays.toString(key));
			if (key[0] == 52) {
				System.out.println("Hello"); //TODO
			}
			double[] answer = tree.remove(key); 
			if (answer != key) {
				throw new IllegalStateException("Expected " + Arrays.toString(key) + " but got " + Arrays.toString(answer));
			}
		}
	}
	
	private KDTree(int dims) {
		if (DEBUG) {
			System.err.println("Warning: DEBUG enabled");
		}
		this.dims = dims;
	}

	public static <T> KDTree<T> create(int dims) {
		return new KDTree<>(dims);
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
		modCount++;
		if (root == null) {
			root = new Node<>(key, value);
			return;
		}
		Node<T> n = root;
		int depth = 0;
		do {
			n = n.getClosestNodeOrAddPoint(key, value, depth, dims);
			depth++;
		} while (n != null);
	}
	
	/**
	 * Check whether a given key exists.
	 * @param key the key to check
	 * @return true iff the key exists
	 */
	public boolean containsExact(double[] key) {
		return findNodeExcat(key) != null;
	}
	
	/**
	 * Get the value associates with the key.
	 * @param key the key to look up
	 * @return the value for the key or 'null' if the key was not found
	 */
	@Override
	public T queryExact(double[] key) {
		Node<T> e = findNodeExcat(key);
		return e == null ? null : e.getValue();
	}
	
	private Node<T> findNodeExcat(double[] key) {
		if (root == null) {
			return null;
		}
		Node<T> n = root;
		int depth = 0;
		do {
			double[] nodeKey = n.getKey();
			int pos = depth % dims;
			double nodeX = nodeKey[pos];
			double keyX = key[pos];
			if (keyX == nodeX && Arrays.equals(key, nodeKey)) {
				return n;
			}
			n = (keyX >= nodeX) ? n.getRight() : n.getLeft();
			depth++;
		} while (n != null);
		return n;
	}
	
	/**
	 * Remove a key.
	 * @param key key to remove
	 * @return the value associated with the key or 'null' if the key was not found
	 */
	@Override
	public T remove(double[] key) {
		if (root == null) {
			return null;
		}
		
		//find
		Node<T> n = root;
		Node<T> parent = null;
		Node<T> eToRemove = null;
		int depth = 0;
		int pos;
		do {
			double[] nodeKey = n.getKey();
			pos = depth % dims;
			double nodeX = nodeKey[pos];
			double keyX = key[pos];
			if (keyX == nodeX && Arrays.equals(key, nodeKey)) {
				eToRemove = n;
				break;
			}
			parent = n;
			n = (keyX >= nodeX) ? n.getRight() : n.getLeft();
			if (n == null) {
				return null;
			}
			depth++;
		} while (true); 

		//remove
		modCount++;
		T value = eToRemove.getValue();
		if (eToRemove == root && size == 1) {
			root = null;
			size = 0;
		}
		
		//find replacement
		RemoveResult<T> removeResult = new RemoveResult<>();
		removeResult.nodeParent = parent; //in case we skip the loop??? TODO we can't skip the loop...
		while (eToRemove != null && !eToRemove.isLeaf()) {
			//recurse
			removeResult.node = null; 
			pos = depth % dims;
			//randomize search direction (modCount)
			if (((modCount & 0x1) == 0 || eToRemove.getRight() == null) && eToRemove.getLeft() != null) {
				//get replacement from left
				removeResult.best = Double.NEGATIVE_INFINITY;
				removeMaxLeaf(eToRemove.getLeft(), eToRemove, pos, depth+1, removeResult);
			} else if (eToRemove.getRight() != null) {
				//get replacement from right
				removeResult.best = Double.POSITIVE_INFINITY;
				removeMinLeaf(eToRemove.getRight(), eToRemove, pos, depth+1, removeResult);
			}
			eToRemove.setKeyValue(removeResult.node.getKey(), removeResult.node.getValue());
			eToRemove = removeResult.node;
			depth = removeResult.depth;
			if (eToRemove != null && eToRemove.getKey()[0] == 19) {
				System.out.println("Helloe2: ");
			}
		} 
		//leaf node
		parent = removeResult.nodeParent; 
		if (parent != null) {
			if (parent.getLeft() == eToRemove) {
				parent.setLeft(null);
			} else if (parent.getRight() == eToRemove) {
				parent.setRight(null);
			} else { 
				throw new IllegalStateException();
			}
		}
		size--;
		return value;
	}

	private static class RemoveResult<T> {
		Node<T> node = null;
		Node<T> nodeParent = null;
		double best;
		int depth;
		
	}
	
	private void removeMinLeaf(Node<T> node, Node<T> parent, int pos, int depth, RemoveResult<T> result) {
		//Split in 'interesting' dimension
		if (pos == depth % dims) {
			//We strictly look for leaf nodes with left==null
			if (node.getLeft() != null) {
				removeMinLeaf(node.getLeft(), node, pos, depth + 1, result);
			} else if (node.getKey()[pos] <= result.best) {
				result.node = node;
				result.nodeParent = parent;
				result.best = node.getKey()[pos];
				result.depth = depth;
			}
		} else {
			//split in any other dimension.
			//First, check local key. 
			if (node.getKey()[pos] <= result.best) {
				result.node = node;
				result.nodeParent = parent;
				result.best = node.getKey()[pos];
				result.depth = depth;
			}
			if (node.getLeft() != null) {
				removeMinLeaf(node.getLeft(), node, pos, depth + 1, result);
			}
			if (node.getRight() != null) {
				 removeMinLeaf(node.getRight(), node, pos, depth + 1, result);
			}
			//TODO we should create a list of 10 (or so) 'best' nodes, this would avoid traversing
			//the whole subtree again to get the 2nd smallest node ...
		}
	}
	
	private void removeMaxLeaf(Node<T> node, Node<T> parent, int pos, int depth, RemoveResult<T> result) {
		//Split in 'interesting' dimension
		if (pos == depth % dims) {
			//We strictly look for leaf nodes with left==null
			if (node.getRight() != null) {
				removeMaxLeaf(node.getRight(), node, pos, depth + 1, result);
			} else if (node.getKey()[pos] >= result.best) {
				result.node = node;
				result.nodeParent = parent;
				result.best = node.getKey()[pos];
				result.depth = depth;
			}
		} else {
			//split in any other dimension.
			//First, check local key. 
			if (node.getKey()[pos] >= result.best) {
				result.node = node;
				result.nodeParent = parent;
				result.best = node.getKey()[pos];
				result.depth = depth;
			}
			if (node.getLeft() != null) {
				removeMaxLeaf(node.getLeft(), node, pos, depth + 1, result);
			}
			if (node.getRight() != null) {
				 removeMaxLeaf(node.getRight(), node, pos, depth + 1, result);
			}
			//TODO we should create a list of 10 (or so) 'best' nodes, this would avoid traversing
			//the whole subtree again to get the 2nd smallest node ...
		}
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
		T value = remove(oldKey);
		insert(newKey, value);
		return value;
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
	public QIterator<T> query(double[] min, double[] max) {
		return new QIterator<>(this, min, max);
	}

	private static boolean isEnclosed(double[] point, double[] min, double[] max) {
		for (int i = 0; i < point.length; i++) {
			if (point[i] < min[i] || point[i] > max[i]) {
				return false;
			}
		}
		return true;
	}

	private static double distance(double[] p1, double[] p2) {
		double dist = 0;
		for (int i = 0; i < p1.length; i++) {
			double d = p1[i]-p2[i];
			dist += d * d;
		}
		return Math.sqrt(dist);
	}
	
	private static class IteratorPos<T> {
		private Node<T> node;
		private int depth;
		private boolean doLeft, doKey, doRight;
		IteratorPos(Node<T> node) {
			this.node = node;
		}
		void initTodo(double[] min, double[] max, int depth, int dims) {
			this.depth = depth;
			double[] key = node.getKey();
			int pos = depth % dims;
			doLeft = min[pos] < key[pos];
			doRight = max[pos] > key[pos];
			doKey = doLeft || doRight || key[pos] == min[pos] || key[pos] == max[pos];
		}
	}
	
	/**
	 * Resetable query iterator.
	 *
	 * @param <T>
	 */
	public static class QIterator<T> implements QueryIterator<PointEntry<T>> {

		private final KDTree<T> tree;
		private ArrayDeque<IteratorPos<T>> stack;
		private Node<T> next = null;
		private double[] min;
		private double[] max;
		
		QIterator(KDTree<T> tree, double[] min, double[] max) {
			this.stack = new ArrayDeque<>();
			this.tree = tree;
			reset(min, max);
		}
		
		@SuppressWarnings("unchecked")
		private void findNext() {
			while(!stack.isEmpty()) {
				IteratorPos<T> itPos = stack.peek();
				Node<T> node = itPos.node;
				if (itPos.doLeft && node.getLeft() != null) {
					itPos.doLeft = false;
					stack.push(new IteratorPos<>(node.getLeft()));
					stack.peek().initTodo(min, max, itPos.depth + 1, tree.getDims());
					continue;
				}
				if (itPos.doKey) {
					itPos.doKey = false;
					if (isEnclosed(node.getKey(), min, max)) {
						next = node;
						return;
					}
				}
				if (itPos.doRight && node.getRight() != null) {
					itPos.doRight = false;
					stack.push(new IteratorPos<>(node.getRight()));
					stack.peek().initTodo(min, max, itPos.depth + 1, tree.getDims());
					continue;
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
		public Node<T> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			Node<T> ret = next;
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
				//TODO use better stack and reuse stack-entries!
				stack.push(new IteratorPos<>(tree.root));
				stack.peek().initTodo(min, max, 0, tree.getDims());
				findNext();
			}
		}
	}
	
	public List<KDEntryDist<T>> knnQuery(double[] center, int k) {
		if (root == null) {
    		return Collections.emptyList();
		}
        Comparator<Node<T>> comp =  
        		(Node<T> point1, Node<T> point2) -> {
        			double deltaDist = 
        					distance(center, point1.point()) - 
        					distance(center, point2.point());
        			return deltaDist < 0 ? -1 : (deltaDist > 0 ? 1 : 0);
        		};
        double distEstimate = distanceEstimate(root, center, k, comp);
    	ArrayList<KDEntryDist<T>> candidates = new ArrayList<>();
    	while (candidates.size() < k) {
    		candidates.clear();
    		rangeSearchKNN(root, center, candidates, k, distEstimate);
    		distEstimate *= 2;
    	}
    	return candidates;
    }

    private double rangeSearchKNN(Node<T> node, double[] center, 
    		ArrayList<KDEntryDist<T>> candidates, int k, double maxRange) {
		if (node.getLeft() == null && node.getRight() == null) {
			double dist = distance(center, node.point());
			if (dist < maxRange) {
				candidates.add(new KDEntryDist<>(node, dist));
			}
    		maxRange = adjustRegionKNN(candidates, k, maxRange);
    	} else {
    		ArrayList<Node<T>> nodes = node.getChildNodes(); 
    		for (int i = 0; i < nodes.size(); i++) {
    			Node<T> sub = nodes.get(i);
    			if (sub != null && 
    					QUtil.distToRectNode(center, sub.getCenter(), sub.getRadius()) < maxRange) {
    				maxRange = rangeSearchKNN(sub, center, candidates, k, maxRange);
    				//we set maxRange simply to the latest returned value.
    			}
    		}
    	}
    	return maxRange;
    }

    private double adjustRegionKNN(ArrayList<KDEntryDist<T>> candidates, int k, double maxRange) {
        if (candidates.size() < k) {
        	//wait for more candidates
        	return maxRange;
        }

        //use stored distances instead of recalcualting them
        candidates.sort(KDEntryDist.COMP);
        while (candidates.size() > k) {
        	candidates.remove(candidates.size()-1);
        }
        
        double range = candidates.get(candidates.size()-1).dist();
        return range;
	}
	
    private class KDQueryIteratorKNN implements QueryIteratorKNN<PointEntryDist<T>> {

    	private Iterator<PointEntryDist<T>> it;
    	
		public KDQueryIteratorKNN(double[] center, int k) {
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
			toStringTree(sb, root, 0);
		}
		return sb.toString();
	}
	
	@SuppressWarnings("unchecked")
	private void toStringTree(StringBuilder sb, Node<T> node, int depth) {
		String prefix = "";
		for (int i = 0; i < depth; i++) {
			prefix += ".";
		}
		//sb.append(prefix + " d=" + depth + NL);
		prefix += " ";
		if (node.getLeft() != null) {
			toStringTree(sb, node.getLeft(), depth+1);
		}
		sb.append(prefix + Arrays.toString(node.point()));
		sb.append(" v=" + node.value());
		sb.append(" l/r=");
		sb.append(node.getLeft() == null ? null : Arrays.toString(node.getLeft().point()));
		sb.append("/");
		sb.append(node.getRight() == null ? null : Arrays.toString(node.getRight().point()));
		sb.append(NL);
		if (node.getRight() != null) {
			toStringTree(sb, node.getRight(), depth+1);
		}
	}
	
	@Override
	public String toString() {
		return "KDTree;size=" + size + 
				";DEBUG=" + DEBUG + 
				";center=" + (root==null ? "null" : Arrays.toString(root.getKey()));
	}
	
	@Override
	public KDStats getStats() {
		KDStats s = new KDStats();
		if (root != null) {
			root.checkNode(s, 0);
		}
		return s;
	}
	
	/**
	 * Statistics container class.
	 */
	public static class KDStats {
		int nNodes;
		int maxDepth;
		public int getNodeCount() {
			return nNodes;
		}
		public int getMaxDepth() {
			return maxDepth;
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
	public KDQueryIteratorKNN queryKNN(double[] center, int k) {
		return new KDQueryIteratorKNN(center, k);
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
