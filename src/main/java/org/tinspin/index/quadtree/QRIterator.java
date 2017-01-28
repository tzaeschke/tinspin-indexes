/*
 * Copyright 2016 Tilmann Zaeschke
 * 
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
package org.tinspin.index.quadtree;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import org.tinspin.index.QueryIterator;
import org.tinspin.index.RectangleEntry;

/**
 * Resetable query iterator.
 *
 * @param <T>
 */
public class QRIterator<T> implements QueryIterator<RectangleEntry<T>> {

	private final QuadTreeRKD<T> tree;
	private IteratorStack stack;
	private QREntry<T> next = null;
	private double[] min;
	private double[] max;
	
	QRIterator(QuadTreeRKD<T> tree, double[] min, double[] max) {
		this.stack = new IteratorStack();
		this.tree = tree;
		reset(min, max);
	}
	
	private void findNext() {
		while(!stack.isEmpty()) {
			StackEntry<T> se = stack.peek();
			while (se.posSub < se.lenSub) {
				int pos = (int) se.posSub;
				se.inc();
				//abort in next round if no increment is detected
				if (se.posSub <= pos) {
					se.posSub = Long.MAX_VALUE;
				}
				QRNode<T> node = se.subs[pos];
				if (node != null) {
					se = stack.prepareAndPush(node, min, max);
				}
			}
			while (se.posE < se.lenE) {
				QREntry<T> e = se.vals.get((int) se.posE++);
				if (QUtil.overlap(min, max, e.lower(), e.upper())) {
					next = e;
					return;
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
	public QREntry<T> next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		QREntry<T> ret = next;
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
	
	private class IteratorStack {
		private final ArrayList<StackEntry<T>> stack;
		private int size = 0;
		
		IteratorStack() {
			stack = new ArrayList<>();
		}

		boolean isEmpty() {
			return size == 0;
		}

		StackEntry<T> prepareAndPush(QRNode<T> node, double[] min, double[] max) {
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

	private static class StackEntry<T> {
		int posE;
		long posSub;
		long m0;
		long m1;
		QRNode<T>[] subs;
		ArrayList<QREntry<T>> vals;
		int lenE;
		int lenSub;
		
		void set(QRNode<T> node, double[] min, double[] max) {
			this.vals = node.getEntries();
			this.subs = node.getChildNodes();

			lenE = this.vals != null ? this.vals.size() : 0;
			posE = 0;
			if (subs != null) {
				lenSub = this.subs.length;
				m0 = 0;
				m1 = 0;
				double[] center = node.getCenter();
				for (int d = 0; d < center.length; d++) {
					m0 <<= 1;
					m1 <<= 1;
					if (max[d] >= center[d]) {
						m1 |= 1;
						if (min[d] >= center[d]) {
							m0 |= 1;
						}
					}
				}
				posSub = m0;
			} else {
				lenSub = 0;
				posSub = 0;
			}
		}
		
		boolean checkHcPos(long pos) {
			return ((pos | m0) & m1) == pos;
		}

		void inc() {
			//first, fill all 'invalid' bits with '1' (bits that can have only one value).
			long r = posSub | (~m1);
			//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
			r++;
			//remove invalid bits.
			posSub = (r & m1) | m0;

			//return -1 if we exceed 'max' and cause an overflow or return the original value. The
			//latter can happen if there is only one possible value (all filter bits are set).
			//The <= is also owed to the bug tested in testBugDecrease()
			//return (r <= v) ? -1 : r;
		}
	}

}