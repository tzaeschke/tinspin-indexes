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

import org.tinspin.index.BoxDistance;
import org.tinspin.index.BoxEntryDist;

class RTreeMixedQuery2<T> implements Iterator<BoxEntryDist<T>> {
	
	private static class RTreeNodeWrapper<T> implements BoxEntryDist<T>, Comparable<RTreeNodeWrapper<T>> {

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
			return "RTreeNodeWrapper [lower()=" + Arrays.toString(lower()) + 
					", upper()=" + Arrays.toString(upper())
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
	

	private final RTree<T> tree;
	private final double[] center;
	private final BoxDistance dist;
	private final BoxDistance closestDist;
	private final PriorityQueue<RTreeNodeWrapper<T>> queue = new PriorityQueue<>();
	private final Filter filter;
	private RTreeNodeWrapper<T> next;
	private RTreeNodeWrapper<T> current;
	
	private double distanceOfLastReturnedNode = Double.NEGATIVE_INFINITY;
	private List<RTreeNodeWrapper<T>> nodesAlreadyReturnedWithSameDist = new ArrayList<>();
	
	/*
	 * Statistics for evaluation
	 */
	private int remove_pointerLoss;
	private int remove_hit;

	public RTreeMixedQuery2(RTree<T> tree, double[] center, Filter filter,
							BoxDistance dist, BoxDistance closestDist) {
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

	private void insert(RTreeNode<T> node) {
		if (filter.intersects(node.min, node.max)) {
			RTreeNodeWrapper<T> wrapped = new RTreeNodeWrapper<>(node, closestDist.dist(center, node.min, node.max));
			queue.add(wrapped);
		}
	}

	private RTreeNodeWrapper<T> findNext() {
		RTreeNodeWrapper<T> nextElement = null;
		while (nextElement == null && !queue.isEmpty()) {
			RTreeNodeWrapper<T> top = queue.poll();
			Entry<T> ent = top.node;
			if (ent instanceof RTreeNodeDir) {
				processNode((RTreeNodeDir<T>) ent);
			} else if (ent instanceof RTreeNodeLeaf) {
				processNode((RTreeNodeLeaf<T>) ent);
			} else {
				nextElement = (RTreeNodeWrapper<T>) top;
			}

			if (nextElement != null) {
				/*
				 * Filter out duplicates (due to remove() calls)
				 */
				//TODO what is this good for??
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
			//TODO what is this good for??
			nodesAlreadyReturnedWithSameDist.add(nextElement);
		}
		return nextElement;
	}
	
	private void processNode(RTreeNodeDir<T> node) {
		ArrayList<RTreeNode<T>> children = node.getChildren();
		for (int i = 0; i < children.size(); i++) {
			insert(children.get(i));
		}
	}

	private void processNode(RTreeNodeLeaf<T> node) {
		ArrayList<Entry<T>> entries = node.getEntries();
		for (int i = 0; i < entries.size(); i++) {
			insert(entries.get(i));
		}
	}

	private void insert(Entry<T> ent) {
		if (!filter.matches(ent)) {
			return;
		}
		double distance = dist.dist(center, ent.min, ent.max);

		if (distance < distanceOfLastReturnedNode) {
			return;
		}

		RTreeNodeWrapper<T> wrapped = new RTreeNodeWrapper<>(ent, distance);
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
	public BoxEntryDist<T> next() {
		if (!hasNext()) {
			throw new IllegalStateException();
		}

		current = next;
		next = null;
		return current;
	}

	@Override
	public String toString() {
		return "RTreeMixedQuery [queueSize=" + queueSize() 
				+ ", rm.loss=" + remove_pointerLoss + ", rm.hit="
				+ remove_hit + ", center=" + Arrays.toString(center) + ", dist=" + dist + "]";
	}
	
	int queueSize() {
		return queue.size();
	}
	

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
	
}
