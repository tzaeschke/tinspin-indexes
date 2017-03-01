package org.tinspin.index.rtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Supplier;

import org.tinspin.index.RectangleEntryDist;

/*
 * TODO: couldn't we take a distance-function which takes an Entry?
 *      Maybe our values are potatoes and not squares, and the distance calculation is a bit more complex.
 *      
 * TODO: performance, avoid iterators for arraylist
 * 
 * TODO: look at org.tinspin.index.rtree.RTree.findNodeEntry(double[], double[], boolean)
 *      -> correct logic becomes clear there
 */
class RTreeMixedQuery<T> implements Iterator<RectangleEntryDist<T>> {
	
	private static class RTreeNodeWrapper<T> implements RectangleEntryDist<T> {

		Entry<T> node;
		private double distance;

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

	}
	
	private static class RTreeEntryWrapper<T> extends RTreeNodeWrapper<T> {

		int idx;
		RTreeNodeLeaf<T> parent;

		RTreeEntryWrapper(Entry<T> node, double distance, int idx, RTreeNodeLeaf<T> parent) {
			super(node, distance);
			this.idx = idx;
			this.parent = parent;
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
	private RTreeNodeWrapper<T> current;
	
	private double check_lastDist = Double.NEGATIVE_INFINITY;
	private List<RTreeNodeWrapper<T>> check_alreadyReturnedWithSameDist = new ArrayList<>();
	/*
	 * This is only for the purpose of an optional validation
	 */
	private Set<RTreeNodeWrapper<T>> check_resizedInnerNodes;
	{
		testCode(() -> {
			check_resizedInnerNodes = new HashSet<>();
		});
	}

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

	private RTreeNodeWrapper<T> insert(RTreeNode<T> node) {
		if (!filter.intersects(node.min, node.max)) {
			return null;
		}
		RTreeNodeWrapper<T> wrapped = wrap(node);
		queue.add(wrapped);
		return wrapped;
	}

	private RTreeNodeWrapper<T> wrap(RTreeNode<T> node) {
		return new RTreeNodeWrapper<>(node, dist(node));
	}

	private double dist(RTreeNode<T> node) {
		return closestDist.dist(center, node.min, node.max);
	}

	private RTreeNodeWrapper doNext() {
		RTreeNodeWrapper next = null;
		while (next == null && !queue.isEmpty()) {
			RectangleEntryDist<?> top = queue.poll(); // TODO: make a queue of
														// RTreeNodeWrapper
			RTreeNodeWrapper nodeWrapper = (RTreeNodeWrapper) top;
			Entry ent = nodeWrapper.node;
			if (ent instanceof RTreeNodeDir) {
				processNode((RTreeNodeDir) ent);
			} else if (ent instanceof RTreeNodeLeaf) {
				processNode((RTreeNodeLeaf) ent);
			} else if (ent instanceof Entry) {
				next = nodeWrapper;
			} else {
				throw new IllegalStateException();
			}

			if (next != null) {
				/*
				 * Filter out duplicates (due to removals)
				 */
				if (next.distance > check_lastDist) {
					check_lastDist = next.distance;
					check_alreadyReturnedWithSameDist.clear();
				} else if (next.distance < check_lastDist) {
					RTreeNodeWrapper anext = next;
					assert softAssert(() -> check_resizedInnerNodesContains(((RTreeEntryWrapper) anext).parent));
					next = null;
					continue;
				} else if (wasAlreadyReturned(next.node, ((RTreeEntryWrapper) next).parent)) {
					// loop
					next = null;
					continue;
				}
			}
		}
		if (next != null) {
			check_alreadyReturnedWithSameDist.add(next);
		}
		return next;
	}
	
	private void processNode(RTreeNodeDir node) {
		ArrayList<Entry<T>> entries = node.getEntries();
		assert node.value() == null;
		assert entries.size() > 0;
		for (int i = 0; i < entries.size(); i++) {
			RTreeNode<T> ent = (RTreeNode<T>) entries.get(i);
			RTreeNodeWrapper<T> inserted = insert(ent);
			
			testCode(() -> {
				if (inserted != null && check_resizedInnerNodesContains(node)) {
					if (dist(node) <= check_lastDist) {
						check_resizedInnerNodes.add(inserted);
					}
				}
			});
		}
		testCode(() -> {
			check_resizedInnerNodes.remove(node);
		});
	}

	private boolean check_resizedInnerNodesContains(RTreeNode node) {
		for (RTreeNodeWrapper<T> e : check_resizedInnerNodes) {
			if (e.node == node) {
				return true;
			}
		}
		return false;
	}

	private boolean processNode(RTreeNodeLeaf node) {
		ArrayList<Entry<T>> entries = node.getEntries();
		assert node.value() == null;
		for (int i = 0; i < entries.size(); i++) {
			Entry<T> ent = entries.get(i);
			assert !(ent instanceof RTreeNode);
			insert(ent, node, i);
		}
		return !entries.isEmpty();
	}

	private void insert(Entry ent, RTreeNodeLeaf parent, int idx) {
		if (!filter.matches(ent.min, ent.max)) {
			return;
		}
		assert isTreeNode(parent);
		double distance = dist(ent);

		if (distance == check_lastDist) {
			if (wasAlreadyReturned(ent, parent)) {
				return;
			}
		} else if (distance < check_lastDist) {
			System.err.println("Found duplicate.");
			assert softAssert(()->check_resizedInnerNodesContains(parent));
			return;
		}

		queue.add(new RTreeEntryWrapper<>(ent, distance, idx, parent));
	}

	private boolean wasAlreadyReturned(Entry ent, RTreeNodeLeaf parent) {
		for (int i = 0; i < check_alreadyReturnedWithSameDist.size(); i++) {
			if (ent == check_alreadyReturnedWithSameDist.get(i).node) {
				// found duplicate
				System.err.println("Found duplicate!");
				assert softAssert(()->check_resizedInnerNodesContains(parent));
				return true;
			}
		}
		return false;
	}

	private double dist(Entry ent) {
		assert !(ent instanceof RTreeNode);
		return dist.dist(center, ent.min, ent.max);
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

		current = next;
		next = null;
		return current;
	}

	@Override
	public String toString() {
		return "RTreeMixedQuery [queueSize=" + queueSize() + ", center=" + Arrays.toString(center) + ", dist=" + dist + "]";
	}
	
	int queueSize() {
		return queue.size();
	}
	
	public void remove(RTreeEntryWrapper e) {
		Entry toDelete = e.node;
		int pos = e.idx;
		RTreeNodeLeaf parent = e.parent;
		if (parent.getEntries().size() <= pos || toDelete != parent.getEntries().get(pos)) {
			pos = parent.getEntries().indexOf(toDelete);
		}
		if (pos == -1 || parent.getParent() == null) {
			// lost pointer, need to look it up from the beginning
			if (tree.remove(e.lower(), e.upper()) == null) {
				throw new IllegalStateException("Node not found");
			}
		} else {
			assert isTreeNode(parent);
			tree.deleteFromNode(parent, pos);
		}
	}
	
	private boolean isTreeNode(RTreeNode parent) {
		if (parent == tree.getRoot()) {
			return true;
		}
		if (!parent.getParent().getChildren().contains(parent)) {
			return false;
		}
		return isTreeNode(parent.getParent());
	}

	/*
	 * Can we actually support this?
	 * Restructuring the tree while iterating may change the objects in the queue.
	 * -----
	 *  The iterator therefore doesn't support any change while iterating :(
	 * ----
	 * Concrete problem: removing an element may restructure the tree in a
	 * way where an element B goes from Node X to Node Y in a way that alters the bound of Y
	 * such that it's distance is reduced.
	 * If Y is already in the queue, we actually need reordering of the queue...
	 */
	@Override
	public void remove() {
		if (current == null) {
			throw new IllegalStateException();
		}
		remove((RTreeEntryWrapper) current);
		
		checkQueueAfterRemove();
	}

	/**
	 * <pre>
	 * TODO: the iterator could register itself as listener for node modification at the tree.
	 *  - When a node changes dimension, it notifies the tree which forwards to the liseners.
	 *  - It the distance was reduced, we add the node a second time in the queue.
	 *  - We hold a set of nodes which are duplicated in the queue to be able to skip them when they appear for the second time.
	 *  </pre> 
	 */
	void checkQueueAfterRemove() {
		PriorityQueue<RTreeNodeWrapper> priorityQueue = (PriorityQueue<RTreeNodeWrapper>) (PriorityQueue) queue;
		ArrayList<RectangleEntryDist<?>> toReinsert = new ArrayList<>();
		for (Iterator<RTreeNodeWrapper> iterator = priorityQueue.iterator(); iterator.hasNext();) {
			RTreeNodeWrapper<?> e = iterator.next();
			Entry<?> node = e.node;
			double actualDist;
			if (node instanceof RTreeNode) {
				actualDist = closestDist.dist(center, node.min, node.max);
				if (e.dist() > actualDist) {
					/* Distance is now shorter. We have to process this to keep correct ordering.
					 */
					
					// TODO: is it true that this happens at most one time?
					// if so, we don't need a list but we can exit right here
					iterator.remove();
					toReinsert.add(wrap((RTreeNode<T>) node));
				}
				testCode(()-> {
					if (e.dist() < actualDist) {
						System.err.println("Distance increased - ignore");
					}
				});
			}
			/**
			 * Node which are actual entries cannot change their shape and don't
			 * have to be updated in the queue.
			 * 
			 * But entries which were already returned may be moved into one of
			 * the nodes in the queue. This can only be one of the reshaped ones
			 * (and therefore in {@code toReinsert}).
			 * 
			 * But an entry which is already unpacked in the queue may also be
			 * moved into a RTreeNode which still is in the queue (because it's
			 * distance is bigger than the current distance) without changing
			 * the treenode's shape. We cannot detect this except by filtering
			 * out duplicates, or by extending the RTreeNodes to be informed of
			 * changes.
			 */
		}
		if (!toReinsert.isEmpty()) {
			System.err.println("toReinsert.size()" + toReinsert.size());
			queue.addAll(toReinsert);
			testCode(() -> {
				for (int i = 0; i < toReinsert.size(); i++) {
					RectangleEntryDist<?> node = toReinsert.get(i);
					if (node.dist() <= current.distance) {
						System.err.println("TODO: special treatment " + node.dist());
						System.err.println("TODO: special treatment " + ((RTreeNodeWrapper) node).node.getClass());
						check_resizedInnerNodes.add(((RTreeNodeWrapper) node));
					}
				}
			});
		}
	}
	
	/*
	 * Don't forget to test if the code still work with assert disabled :)
	 */
	private void testCode(Runnable run) {
		// Asserts are only executed in debug mode
		assert ((Supplier<Boolean>) () -> {
			run.run();
			return true;
		}).get();
	}
	
	private boolean softAssert(Supplier<Boolean> a) {
		return true;
	}

}
