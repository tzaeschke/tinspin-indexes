package org.tinspin.index.rtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import org.tinspin.index.RectangleEntryDist;

class RTreeMixedQuery<T> implements Iterator<RectangleEntryDist<T>> {
	
	private static class RTreeNodeWrapper<T> implements RectangleEntryDist<T> {

		Entry<T> node;
		private double distance;

		private RTreeNodeWrapper(Entry<T> node, double distance) {
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

	}

	private static class RectangleEntryDistComparator implements Comparator<RectangleEntryDist<?>> {

		@Override
		public int compare(RectangleEntryDist<?> o1, RectangleEntryDist<?> o2) {
			return Double.compare(o1.dist(), o2.dist());
		}

	}

	private final RTree<T> tree;
	private final double[] center;
	private final DistanceFunction dist;
	private final DistanceFunction closestDist;
	private final PriorityQueue<RectangleEntryDist<?>> queue = new PriorityQueue<>(new RectangleEntryDistComparator());
	private final Filter filter;
	private RTreeNodeWrapper<T> next;

	public RTreeMixedQuery(RTree<T> tree, double[] center, Filter filter, DistanceFunction dist, DistanceFunction closestDist) {
		this.tree = tree;
		this.center = center;
		this.closestDist = closestDist;
		this.filter = filter;
		this.dist = dist;

		init();
	}

	private void init() {
		RTreeNode<T> root = tree.getRoot();
		insert(root);
	}

	private void insert(RTreeNode<T> node) {
		if (!filter.intersects(node.min, node.max)) {
			return;
		}
		queue.add(wrap(node));
	}

	private RectangleEntryDist<?> wrap(RTreeNode<T> node) {
		return new RTreeNodeWrapper<>(node, closestDist.dist(center, node.min, node.max));
	}

	private RTreeNodeWrapper doNext() {
		while (!queue.isEmpty()) {
			RectangleEntryDist<?> top = queue.poll(); // TODO: make a queue of RTreeNodeWrapper
			RTreeNodeWrapper nodeWrapper = (RTreeNodeWrapper) top;
			Entry ent = nodeWrapper.node;
			if (ent instanceof RTreeNodeDir) {
				processNode((RTreeNodeDir) ent);
			} else if (ent instanceof RTreeNodeLeaf) {
				processNode((RTreeNodeLeaf) ent);
			} else if (ent instanceof Entry) {
				return nodeWrapper;
			} else {
				throw new IllegalStateException();
			}
		}
		return null;
	}
	
	private void processNode(RTreeNodeDir node) {
		ArrayList<Entry<T>> entries = node.getEntries();
		assert node.value() == null;
		assert entries.size() > 0;
		for (int i = 0; i < entries.size(); i++) {
			insert((RTreeNode<T>) entries.get(i));
		}
	}

	private boolean processNode(RTreeNodeLeaf node) {
		ArrayList<Entry<T>> entries = node.getEntries();
		assert node.value() == null;
		for (int i = 0; i < entries.size(); i++) {
			Entry<T> ent = entries.get(i);
			assert !(ent instanceof RTreeNode);
			insert(ent);
		}
		return !entries.isEmpty();
	}

	private void insert(Entry ent) {
		if (!filter.matches(ent.min, ent.max)) {
			return;
		}
		queue.add(new RTreeNodeWrapper<>(ent, dist.dist(center, ent.min, ent.max)));
	}

	@Override
	public boolean hasNext() {
		if (next == null) {
			next = doNext();
		}
		return next != null;
	}

	@Override
	public RectangleEntryDist<T> next() {
		if (!hasNext()) {
			throw new IllegalStateException();
		}
		RTreeNodeWrapper<T> ne = next;
		next = null;
		return ne;
	}

	@Override
	public String toString() {
		return "RTreeMixedQuery [queueSize=" + queueSize() + ", center=" + Arrays.toString(center) + ", dist=" + dist + "]";
	}
	
	int queueSize() {
		return queue.size();
	}
	
	@Override
	public void remove() {
		// TODO Auto-generated method stub
		Iterator.super.remove();
	}

}
