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
public class QIterator3<T> implements QueryIterator<PointEntry<T>> {

	private class IteratorStack {
		private final ArrayList<StackEntry<T>> stack;
		private int size = 0;
		
		IteratorStack() {
			stack = new ArrayList<>();
		}

		boolean isEmpty() {
			return size == 0;
		}

		StackEntry<T> prepareAndPush(QNode<T> node, double[] min, double[] max) {
			if (size == stack.size()) {
				stack.add(new StackEntry<>());
			}
			StackEntry<T> ni = stack.get(size++);
			
			ni.set(node, min, max);
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
		long m0;
		long m1;
		QNode<T>[] subs;
		ArrayList<QEntry<T>> vals;
		public int len;
		
		void set(QNode<T> node, double[] min, double[] max) {
			this.pos = 0;
			this.vals = node.getEntries();
			this.subs = node.getChildNodes();

			if (this.vals != null) {
				len = this.vals.size();
			} else {
				len = this.subs.length;
				m1 = ~((-1L) << min.length);
				double[] center = node.getCenter();
				for (int d = 0; d < center.length; d++) {
					m0 <<= 1;
					m1 <<= 1;
					if (min[d] >= center[d]) {
						m0 |= 1;
					} else if (max[d] < center[d]) {
						m1 |= 0;
					}
				}
				pos = (int) m0;
			}
		}
		
		public boolean isLeaf() {
			return vals != null;
		}
		
		boolean checkHcPos(long pos) {
			return ((pos | m0) & m1) == pos;
		}

		long inc(long v) {
			//first, fill all 'invalid' bits with '1' (bits that can have only one value).
			long r = v | (~m1);
			//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
			r++;
			//remove invalid bits.
			return (r & m1) | m0;

			//return -1 if we exceed 'max' and cause an overflow or return the original value. The
			//latter can happen if there is only one possible value (all filter bits are set).
			//The <= is also owed to the bug tested in testBugDecrease()
			//return (r <= v) ? -1 : r;
		}
	}
	
	
	QIterator3(QuadTreeKD<T> tree, double[] min, double[] max) {
		this.stack = new IteratorStack();
		this.tree = tree;
		reset(min, max);
	}
	
	private void findNext() {
		
	}
	
	
	private void findNext0() {
		while(!stack.isEmpty()) {
			StackEntry<T> se = stack.peek();
			if (se.isLeaf()) {
				for ( ; se.pos < se.vals.size(); se.pos++) {
					QEntry<T> e = se.vals.get(se.pos);
					if (e.enclosedBy(min, max)) {
						next = e;
						return;
					}
				}
			} else {
				for ( ; se.pos < se.vals.size(); se.pos++) {
					QNode<T> node = se.subs[se.pos];
					if (QUtil.overlap(min, max, node.getCenter(), node.getRadius())) {
						stack.prepareAndPush(node, min, max);
					}
				}
			}
			stack.pop();
		}
		next = null;
	}
	
	private void findNext1() {
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
					if (se.checkHcPos(pos)) {
						QNode<T> node = se.subs[pos];
						se = stack.prepareAndPush(node, min, max);
					}
				}
			}
			stack.pop();
		}
		next = null;
	}
	
	private void findNext2() {
		while(!stack.isEmpty()) {
			StackEntry<T> se = stack.peek();
			while (se.pos < se.len) {
				int pos = se.pos;
				se.inc(pos);
				if (se.isLeaf()) {
					QEntry<T> e = se.vals.get(pos);
					if (e.enclosedBy(min, max)) {
						next = e;
						return;
					}
				} else {
//					if (se.checkHcPos(pos)) {
//						QNode<T> node = se.subs[pos];
//						se = stack.prepareAndPush(node, min, max);
//					}
					QNode<T> node = se.subs[pos];
					se = stack.prepareAndPush(node, min, max);
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
			stack.prepareAndPush(tree.getRoot(), min, max);
			findNext();
		}
	}
}