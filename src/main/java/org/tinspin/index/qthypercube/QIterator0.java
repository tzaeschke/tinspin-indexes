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
package org.tinspin.index.qthypercube;

import java.util.ArrayList;
import java.util.NoSuchElementException;

import static org.tinspin.index.Index.*;

/**
 * Resettable query iterator.
 *
 * @param <T> Value type
 */
public class QIterator0<T> implements PointIterator<T> {

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

		void pop() {
			--size;
		}

		public void clear() {
			size = 0;
		}
	}

	private final QuadTreeKD<T> tree;
	private final IteratorStack stack;
	private PointEntry<T> next = null;
	private double[] min;
	private double[] max;
	
	private static class StackEntry<T> {
		int pos;
		QNode<T>[] subs;
		ArrayList<PointEntry<T>> vals;
		int len;
		
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
					PointEntry<T> e = se.vals.get(pos);
					if (QUtil.isPointEnclosed(e.point(), min, max)) {
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
			stack.prepareAndPush(tree.getRoot());
			findNext();
		}
		return this;
	}
}