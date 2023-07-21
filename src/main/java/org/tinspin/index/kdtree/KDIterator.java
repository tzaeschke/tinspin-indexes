package org.tinspin.index.kdtree;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import static org.tinspin.index.Index.*;

/**
 * Resetable query iterator.
 *
 * @param <T> Value type
 */
public class KDIterator<T> implements PointIterator<T> {

	private static class IteratorPos<T> {
		private Node<T> node;
		private int depth;
		private boolean doLeft;
		private boolean doKey;
		private boolean doRight;

		void set(Node<T> node, double[] min, double[] max, int depth, int dims) {
			this.node = node;
			this.depth = depth;
			double[] key = node.getKey();
			int pos = depth % dims;
			doLeft = min[pos] <= key[pos];
			doRight = max[pos] >= key[pos];
			doKey = true;
		}
	}
	
	private class IteratorStack {
		private final ArrayList<IteratorPos<T>> stack;
		private int size = 0;
		
		IteratorStack() {
			stack = new ArrayList<>();
		}

		boolean isEmpty() {
			return size == 0;
		}

		void prepareAndPush(Node<T> node, double[] min, double[] max, int depth, int dims) {
			if (size == stack.size()) {
				stack.add(new IteratorPos<>());
			}
			IteratorPos<T> ni = stack.get(size++);
			ni.set(node, min, max, depth, dims);
		}

		IteratorPos<T> peek() {
			return stack.get(size-1);
		}

		void pop() {
			--size;
		}

		public void clear() {
			size = 0;
		}
	}

	private final KDTree<T> tree;
	private final IteratorStack stack;
	private Node<T> next = null;
	private double[] min;
	private double[] max;
	
	KDIterator(KDTree<T> tree, double[] min, double[] max) {
		this.stack = new IteratorStack();
		this.tree = tree;
		reset(min, max);
	}
	
	private void findNext() {
		while(!stack.isEmpty()) {
			KDIterator.IteratorPos<T> itPos = stack.peek();
			Node<T> node = itPos.node;
			if (itPos.doLeft && node.getLo() != null) {
				itPos.doLeft = false;
				stack.prepareAndPush(node.getLo(), min, max, itPos.depth + 1, tree.getDims());
				continue;
			}
			if (itPos.doKey) {
				itPos.doKey = false;
				if (KDTree.isEnclosed(node.getKey(), min, max)) {
					next = node;
					return;
				}
			}
			if (itPos.doRight && node.getHi() != null) {
				itPos.doRight = false;
				stack.prepareAndPush(node.getHi(), min, max, itPos.depth + 1, tree.getDims());
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
	 *
	 * @param min lower left corner of query
	 * @param max upper right corner of query
	 * @return this.
	 */
	@Override
	public PointIterator<T> reset(double[] min, double[] max) {
		stack.clear();
		this.min = min;
		this.max = max;
		next = null;
		if (tree.getRoot() != null) {
			stack.prepareAndPush(tree.getRoot(), min, max, 0, tree.getDims());
			findNext();
		}
		return this;
	}
}