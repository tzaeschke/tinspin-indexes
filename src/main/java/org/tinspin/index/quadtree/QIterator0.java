package org.tinspin.index.quadtree;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import org.tinspin.index.PointEntry;
import org.tinspin.index.QueryIterator;

/**
 * Resettable query iterator.
 *
 * @param <T>
 */
public class QIterator0<T> implements QueryIterator<PointEntry<T>> {

	private class IteratorStack {
		private final ArrayList<StackEntry<T>> stack;
		private int size = 0;
		
		IteratorStack() {
			stack = new ArrayList<>();
		}

		boolean isEmpty() {
			return size == 0;
		}

		StackEntry<T> prepareAndPush(QNode<T> node) {
			if (size == stack.size()) {
				stack.add(new StackEntry<>());
			}
			StackEntry<T> ni = stack.get(size++);
			
			ni.set(node);
			return ni;
		}

		StackEntry<T> peek() {
			return stack.get(size-1);
		}

		StackEntry<T> pop() {
			return stack.get(--size);
		}

		public void clear() {
			size = 0;
		}
	}

	private final QuadTreeKD<T> tree;
	private IteratorStack stack;
	private QEntry<T> next = null;
	private double[] min;
	private double[] max;
	
	private static class StackEntry<T> {
		int pos;
		QNode<T>[] subs;
		ArrayList<QEntry<T>> vals;
		public int len;
		
		void set(QNode<T> node) {
			this.pos = 0;
			this.vals = node.getEntries();
			this.subs = node.getChildNodes();
			if (this.vals != null) {
				len = this.vals.size();
			} else {
				len = this.subs.length;
			}
		}
		
		public boolean isLeaf() {
			return vals != null;
		}
	}
	
	
	QIterator0(QuadTreeKD<T> tree, double[] min, double[] max) {
		this.stack = new IteratorStack();
		this.tree = tree;
		reset(min, max);
	}
	
	private void findNext() {
		while(!stack.isEmpty()) {
			StackEntry<T> se = stack.peek();
			while (se.pos < se.len) {
				int pos = se.pos++;
				if (se.isLeaf()) {
					QEntry<T> e = se.vals.get(pos);
					if (e.enclosedBy(min, max)) {
						next = e;
						return;
					}
				} else {
					QNode<T> node = se.subs[pos];
					if (node != null && 
							QUtil.overlap(min, max, node.getCenter(), node.getRadius())) {
						se = stack.prepareAndPush(node);
					}
				}
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
	public QEntry<T> next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		QEntry<T> ret = next;
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
		if (tree.getRoot() != null) {
			stack.prepareAndPush(tree.getRoot());
			findNext();
		}
	}
}