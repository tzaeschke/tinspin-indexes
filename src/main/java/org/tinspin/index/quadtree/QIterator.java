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

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.tinspin.index.PointEntry;
import org.tinspin.index.QueryIterator;

/**
 * Resettable query iterator.
 *
 * @param <T>
 */
public class QIterator<T> implements QueryIterator<PointEntry<T>> {

	private final QuadTreeKD<T> tree;
	private ArrayDeque<Iterator<?>> stack;
	private QEntry<T> next = null;
	private double[] min;
	private double[] max;
	
	QIterator(QuadTreeKD<T> tree, double[] min, double[] max) {
		this.stack = new ArrayDeque<>();
		this.tree = tree;
		reset(min, max);
	}
	
	@SuppressWarnings("unchecked")
	private void findNext() {
		while(!stack.isEmpty()) {
			Iterator<?> it = stack.peek();
			while (it.hasNext()) {
				Object o = it.next();
				if (o instanceof QNode) {
					QNode<T> node = (QNode<T>)o;
					if (QUtil.overlap(min, max, node.getCenter(), node.getRadius())) {
						it = node.getChildIterator();
						stack.push(it);
					}
					continue;
				}
				QEntry<T> e = (QEntry<T>) o;
				if (e.enclosedBy(min, max)) {
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
			stack.push(tree.getRoot().getChildIterator());
			findNext();
		}
	}
}