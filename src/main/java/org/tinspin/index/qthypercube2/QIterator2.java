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
package org.tinspin.index.qthypercube2;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import static org.tinspin.index.Index.*;

/**
 * Resettable query iterator.
 *
 * @param <T> Value type
 */
public class QIterator2<T> implements PointIterator<T> {

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

		void pop() {
			--size;
		}

		public void clear() {
			size = 0;
		}
	}

	private final QuadTreeKD2<T> tree;
	private final IteratorStack stack;
	private PointEntry<T> next = null;
	private double[] min;
	private double[] max;
	
	private static class StackEntry<T> {
		long pos;
		long m0;
		long m1;
		Object[] entries;
		boolean isLeaf;
		int len;
		
		void set(QNode<T> node, double[] min, double[] max) {
			this.entries = node.getEntries();
			this.isLeaf = node.isLeaf();

			if (isLeaf) {
				len = node.getValueCount();
				pos = 0;
			} else {
				len = this.entries.length;
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
				pos = m0;
			}
		}
		
		boolean isLeaf() {
			return isLeaf;
		}
		
		//boolean checkHcPos(long pos) {
		//	return ((pos | m0) & m1) == pos;
		//}

		void inc() {
			//first, fill all 'invalid' bits with '1' (bits that can have only one value).
			long r = pos | (~m1);
			//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
			r++;
			//remove invalid bits.
			pos = (r & m1) | m0;

			//return -1 if we exceed 'max' and cause an overflow or return the original value. The
			//latter can happen if there is only one possible value (all filter bits are set).
			//The <= is also owed to the bug tested in testBugDecrease()
			//return (r <= v) ? -1 : r;
		}
	}
	
	
	QIterator2(QuadTreeKD2<T> tree, double[] min, double[] max) {
		this.stack = new IteratorStack();
		this.tree = tree;
		reset(min, max);
	}
	
	@SuppressWarnings("unchecked")
	private void findNext() {
		while(!stack.isEmpty()) {
			StackEntry<T> se = stack.peek();
			while (se.pos < se.len) {
				if (se.isLeaf()) {
					PointEntry<T> e = (PointEntry<T>) se.entries[(int) se.pos++];
					if (QUtil.isPointEnclosed(e.point(), min, max)) {
						next = e;
						return;
					}
				} else {
					int pos = (int) se.pos;
					se.inc();
					//abort in next round if no increment is detected
					if (se.pos <= pos) {
						se.pos = Long.MAX_VALUE;
					}
					Object e = se.entries[pos];
					if (e != null) {
						if (e instanceof QNode) {
							QNode<T> node = (QNode<T>) e;
							se = stack.prepareAndPush(node, min, max);
						} else {
							PointEntry<T> qe = (PointEntry<T>) e;
							if (QUtil.isPointEnclosed(qe.point(), min, max)) {
								next = qe;
								return;
							}
						}
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
	public PointEntry<T> next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		PointEntry<T> ret = next;
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
			stack.prepareAndPush(tree.getRoot(), min, max);
			findNext();
		}
		return this;
	}
}