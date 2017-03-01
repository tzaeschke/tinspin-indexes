/*
 * Copyright 2017 Christophe Schmaltz
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinspin.index.rtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

import org.tinspin.index.RectangleEntryDist;

class RTreeMixedQuery<T> implements Iterator<RectangleEntryDist<T>> {
	
	private static class RTreeNodeWrapper<T> implements RectangleEntryDist<T>, Comparable<RTreeNodeWrapper<T>> {

		Entry<T> node;
		double distance;

		RTreeNodeWrapper(Entry<T> node, double distance) {
			this.node = node;
			this.distance = distance;
		}

		@Override
		public double[] lower() {
			return node.min;
		}

		@Override
		public double[] upper() {
			return node.max;
		}

		@Override
		public T value() {
			return node.value();
		}

		@Override
		public double dist() {
			return distance;
		}

		@Override
		public String toString() {
			return "RTreeNodeWrapper [lower()=" + Arrays.toString(lower()) + ", upper()=" + Arrays.toString(upper())
					+ ", value()=" + value() + ", dist()=" + dist() + "]";
		}


		@Override
		public int hashCode() {
			return node.hashCode();
		}
		
		/*
		 * Two nodes are equal if they represent the same node.
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			RTreeNodeWrapper<?> other = (RTreeNodeWrapper<?>) obj;
			if (node == null) {
				if (other.node != null)
					return false;
			} else if (!node.equals(other.node))
				return false;
			return true;
		}

		/*
		 * For the PriorityQueue
		 */
		@Override
		public int compareTo(RTreeNodeWrapper<T> o) {
			return Double.compare(distance, o.dist());
		}

	}
	
	/*
	 * Subclass which holds a reference to the parent node for optimized support
	 *  of the iterator.remove() Method.
	 */
	private static class RTreeEntryWrapper<T> extends RTreeNodeWrapper<T> {
		/*
		 * For fast Iterator.remove() support
		 */
		int idx;
		RTreeNodeLeaf<T> parent;

		RTreeEntryWrapper(Entry<T> node, double distance, int idx, RTreeNodeLeaf<T> parent) {
			super(node, distance);
			this.idx = idx;
			this.parent = parent;
		}
	}

	private final RTree<T> tree;
	private final double[] center;
	private final DistanceFunction dist;
	private final DistanceFunction closestDist;
	private final PriorityQueue<RTreeNodeWrapper<T>> queue = new PriorityQueue<>();
	private final Filter filter;
	private RTreeEntryWrapper<T> next;
	private RTreeEntryWrapper<T> current;
	
	private double distanceOfLastReturnedNode = Double.NEGATIVE_INFINITY;
	private List<RTreeNodeWrapper<T>> nodesAlreadyReturnedWithSameDist = new ArrayList<>();
	
	/*
	 * Statistics for evaluation
	 */
	private int remove_pointerLoss;
	private int remove_hit;

	public RTreeMixedQuery(RTree<T> tree, double[] center, Filter filter, DistanceFunction dist, DistanceFunction closestDist) {
		this.tree = tree;
		this.center = center;
		this.closestDist = closestDist;
		this.filter = filter;
		this.dist = dist;

		init();
	}

	private void init() {
		insert(tree.getRoot());
	}

	private RTreeNodeWrapper<T> insert(RTreeNode<T> node) {
		if (!filter.intersects(node.min, node.max)) {
			return null;
		}
		RTreeNodeWrapper<T> wrapped = new RTreeNodeWrapper<>(node, closestDist.dist(center, node.min, node.max));
		queue.add(wrapped);
		return wrapped;
	}

	private RTreeEntryWrapper<T> findNext() {
		RTreeEntryWrapper<T> nextElement = null;
		while (nextElement == null && !queue.isEmpty()) {
			RTreeNodeWrapper<T> top = queue.poll();
			Entry<T> ent = top.node;
			if (ent instanceof RTreeNodeDir) {
				processNode((RTreeNodeDir<T>) ent);
			} else if (ent instanceof RTreeNodeLeaf) {
				processNode((RTreeNodeLeaf<T>) ent);
			} else {
				assert top.value() != null;
				nextElement = (RTreeEntryWrapper<T>) top;
			}

			if (nextElement != null) {
				/*
				 * Filter out duplicates (due to remove() calls)
				 */
				if (nextElement.distance > distanceOfLastReturnedNode) {
					distanceOfLastReturnedNode = nextElement.distance;
					nodesAlreadyReturnedWithSameDist.clear();
				} else if (nextElement.distance < distanceOfLastReturnedNode) {
					// loop
					nextElement = null;
					continue;
				} else if (nodesAlreadyReturnedWithSameDist.contains(nextElement)) {
					// loop
					nextElement = null;
					continue;
				}
			}
		}
		if (nextElement != null) {
			nodesAlreadyReturnedWithSameDist.add(nextElement);
		}
		return nextElement;
	}
	
	private void processNode(RTreeNodeDir<T> node) {
		ArrayList<RTreeNode<T>> children = node.getChildren();
		assert node.value() == null;
		assert children.size() > 0;
		for (int i = 0; i < children.size(); i++) {
			insert(children.get(i));
		}
	}

	private boolean processNode(RTreeNodeLeaf<T> node) {
		ArrayList<Entry<T>> entries = node.getEntries();
		assert node.value() == null;
		for (int i = 0; i < entries.size(); i++) {
			Entry<T> ent = entries.get(i);
			assert !(ent instanceof RTreeNode);
			insert(ent, node, i);
		}
		return !entries.isEmpty();
	}

	private void insert(Entry<T> ent, RTreeNodeLeaf<T> parent, int idx) {
		if (!filter.matches(ent)) {
			return;
		}
		assert isTreeNode(parent);
		assert !(ent instanceof RTreeNode);
		double distance = dist.dist(center, ent.min, ent.max);

		if (distance < distanceOfLastReturnedNode) {
			return;
		}

		RTreeEntryWrapper<T> wrapped = new RTreeEntryWrapper<>(ent, distance, idx, parent);
		if (distance == distanceOfLastReturnedNode) {
			if (nodesAlreadyReturnedWithSameDist.contains(wrapped)) {
				return;
			}
		}
		queue.add(wrapped);
	}

	@Override
	public boolean hasNext() {
		if (next == null) {
			next = findNext();
		}
		return next != null;
	}

	@Override
	public RectangleEntryDist<T> next() {
		if (!hasNext()) {
			throw new IllegalStateException();
		}

		current = next;
		next = null;
		return current;
	}

	@Override
	public String toString() {
		return "RTreeMixedQuery [queueSize=" + queueSize() + ", rm.loss=" + remove_pointerLoss + ", rm.hit="
				+ remove_hit + ", center=" + Arrays.toString(center) + ", dist=" + dist + "]";
	}
	
	int queueSize() {
		return queue.size();
	}
	
	public void remove(RTreeEntryWrapper<T> e) {
		Entry<T> toDelete = e.node;
		int pos = e.idx;
		RTreeNodeLeaf<T> parent = e.parent;
		if (parent.getEntries().size() <= pos || toDelete != parent.getEntries().get(pos)) {
			pos = parent.getEntries().indexOf(toDelete);
		}
		if (pos == -1 || parent.getParent() == null) {
			assert remove_pointerLoss++ > 0 || true : "Counting enabled by assert";
			// lost pointer, need to look it up from the beginning
			if (tree.remove(e.lower(), e.upper()) == null) {
				throw new IllegalStateException("Node not found");
			}
		} else {
			assert remove_hit++ > 0 || true : "Counting enabled by assert";
			assert isTreeNode(parent);
			tree.deleteFromNode(parent, pos);
		}
	}
	
	private boolean isTreeNode(RTreeNode<T> parent) {
		if (parent == tree.getRoot()) {
			return true;
		}
		if (!parent.getParent().getChildren().contains(parent)) {
			return false;
		}
		return isTreeNode(parent.getParent());
	}

	@Override
	public void remove() {
		if (current == null) {
			throw new IllegalStateException();
		}
		remove(current);
		
		checkQueueAfterRemove();
	}

	/**
	 * <pre>
	 * Restructuring the tree while iterating may change the objects in the queue (inner-nodes).
	 * 
	 * Node which are actual entries cannot change their shape and don't
	 * have to be updated in the queue.
	 * 
	 * But entries which were already returned may be moved into one of
	 * the nodes in the queue. This can only be one of the reshaped ones
	 * (which are tracked in {@code toReinsert}).
	 * 
	 * But an entry which is already unpacked in the queue may also be
	 * moved into a RTreeNode which still is in the queue (because it's
	 * distance is bigger than the current distance) without changing
	 * the treenode's shape. We cannot detect this except by filtering
	 * out duplicates, or by extending the RTreeNodes to be informed of
	 * changes.
	 * </pre>
	 * 
	 * <pre>
	 * TODO: the iterator could register itself as listener for node modification at the tree.
	 *  - When a node changes dimension, it notifies the tree which forwards to the listeners.
	 *  - It the distance was reduced, we add the node a second time in the queue.
	 *  - We hold a set of nodes which are duplicated in the queue to be able to skip them when they appear for the second time.
	 *  </pre> 
	 */
	void checkQueueAfterRemove() {
		ArrayList<RTreeNodeWrapper<T>> toReinsert = new ArrayList<>();
		for (Iterator<RTreeNodeWrapper<T>> iterator = queue.iterator(); iterator.hasNext();) {
			RTreeNodeWrapper<T> e = iterator.next();
			Entry<T> node = e.node;
			if (node instanceof RTreeNode) {
				double actualDist = closestDist.dist(center, node.min, node.max);
				if (e.dist() > actualDist) {
					/* Distance is now shorter due to restructuring of the tree. 
					 * We have to process this to keep correct ordering.
					 */
					
					// TODO: does this happens at most one time?
					// if so, we don't need a list but we can exit right here
					iterator.remove();
					toReinsert.add(new RTreeNodeWrapper<>(node, actualDist));
				}
			}
		}
		if (!toReinsert.isEmpty()) {
			queue.addAll(toReinsert);
		}
	}
	
}
