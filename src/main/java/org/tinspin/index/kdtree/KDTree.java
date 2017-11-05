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

import org.tinspin.index.PointEntry;
import org.tinspin.index.PointEntryDist;
import org.tinspin.index.PointIndex;
import org.tinspin.index.QueryIterator;
import org.tinspin.index.QueryIteratorKNN;

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
	
	
	private Node<T> root;
	
	public static void main(String ... args) {
		double[][] point_list = {{2,3}, {5,4}, {9,6}, {4,7}, {8,1}, {7,2}};
		KDTree<double[]> tree = create(2);
		for (double[] data : point_list) {
			tree.insert(data, data);
		}
		for (double[] key : point_list) {
			if (!tree.containsExact(key)) {
				throw new IllegalStateException("" + key);
			}
		}
		for (double[] key : point_list) {
			System.out.println(Arrays.toString(tree.queryExact(key)));
		}
	    System.out.println(tree.toStringTree());
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
			if (keyX != nodeX) {
				n = (keyX > nodeX) ? n.getRight() : n.getLeft();
			} else {
				return Arrays.equals(key, nodeKey) ? n : null;
			}
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
			if (keyX != nodeX) {
				parent = n;
				n = (keyX > nodeX) ? n.getRight() : n.getLeft();
			} else {
				eToRemove = Arrays.equals(key, nodeKey) ? n : null;
				break;
			}
			depth++;
		} while (true); 

		//remove
		if (eToRemove == null) {
			return null;
		}
		T value = eToRemove.getValue();
		if (eToRemove == root && size == 1) {
			root = null;
			size = 0;
		}
		
		//find replacement
		//TODO randomize better
		if (size % 2 == 0 && eToRemove.getLeft() != null) {
			//get replacement from left
			//TODO
		} else if (eToRemove.getRight() != null) {
			//get replacement from right
			//minRef[0] = minNode; minRef[1] = minRef's parent
			Node<T>[] minRef = new Node[2];
			do {
				//recurse
				removeMinLeaf(eToRemove.getRight(), eToRemove, pos, depth, new double[] {Double.MAX_VALUE}, minRef);
				eToRemove.setKeyValue(minRef[0].getKey(), minRef[0].getValue());
				eToRemove = minRef[0];
			} while (eToRemove != null || eToRemove != null);
			//leaf node
			parent = minRef[1]; 
			if (parent.getLeft() == eToRemove) {
				parent.setLeft(null);
			} else {
				parent.setRight(null);
			}
		} else {
			//leaf node
			if (parent.getLeft() == eToRemove) {
				parent.setLeft(null);
			} else {
				parent.setRight(null);
			}
		}
		size--;
		return value;
	}

	private void removeMinLeaf(Node<T> node, Node<T> parent, int pos, int depth, double[] currentMin, Node<T>[] minRef) {
		//Split in 'interesting' dimension
		if (pos == depth % dims) {
			//We strictly look for leaf nodes with left==null
			if (node.getLeft() != null) {
				removeMinLeaf(node.getLeft(), node, pos, depth + 1, currentMin, minRef);
			} else if (node.getKey()[pos] < currentMin[0]) {
				minRef[0] = node;
				minRef[1] = parent;
				currentMin[0] = node.getKey()[pos];
			}
		} else {
			//split in any other dimension
			if (node.getLeft() != null) {
				removeMinLeaf(node.getLeft(), node, pos, depth + 1, currentMin, minRef);
			}
			if (node.getRight() != null) {
				 removeMinLeaf(node.getRight(), node, pos, depth + 1, currentMin, minRef);
			}
			if (node.getKey()[pos] < currentMin[0]) {
				minRef[0] = node;
				minRef[1] = parent;
				currentMin[0] = node.getKey()[pos];
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

    @SuppressWarnings("unchecked")
	private double distanceEstimate(Node<T> node, double[] point, int k,
    		Comparator<Node<T>> comp) {
    	if (node.isLeaf()) {
    		//This is a leaf that would contain the point.
    		int n = node.getEntries().size();
    		Node<T>[] data = node.getEntries().toArray(new Node[n]);
    		Arrays.sort(data, comp);
    		int pos = n < k ? n : k;
    		double dist = distance(point, data[pos-1].point());
    		if (n < k) {
    			//scale search dist with dimensions.
    			dist = dist * Math.pow(k/(double)n, 1/(double)dims);
    		}
    		if (dist <= 0.0) {
    			return node.getRadius();
    		}
    		return dist;
    	} else {
    		ArrayList<Node<T>> nodes = node.getChildNodes(); 
    		for (int i = 0; i < nodes.size(); i++) {
    			Node<T> sub = nodes.get(i);
    			if (QUtil.isPointEnclosed(point, sub.getCenter(), sub.getRadius())) {
    				return distanceEstimate(sub, point, k, comp);
    			}
    		}
    		//okay, this directory node contains the point, but none of the leaves does.
    		//We just return the size of this node, because all it's leaf nodes should
    		//contain more than enough candidate in proximity of 'point'.
    		return node.getRadius() * Math.sqrt(point.length);
    	}
    }
    
    private double rangeSearchKNN(Node<T> node, double[] center, 
    		ArrayList<KDEntryDist<T>> candidates, int k, double maxRange) {
		if (node.isLeaf()) {
    		ArrayList<QEntry<T>> points = node.getEntries();
    		for (int i = 0; i < points.size(); i++) {
    			QEntry<T> p = points.get(i);
   				double dist = QUtil.distance(center, p.point());
   				if (dist < maxRange) {
    				candidates.add(new KDEntryDist<>(p, dist));
  				}
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
		sb.append(prefix + " d=" + depth + NL);
		prefix += " ";
		if (node.getLeft() != null) {
			toStringTree(sb, node.getLeft(), depth+1);
		}
		sb.append(prefix + Arrays.toString(node.point()));
		sb.append(" v=" + node.value() + NL);
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
}
