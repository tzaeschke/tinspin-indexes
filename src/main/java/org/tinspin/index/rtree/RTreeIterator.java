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
package org.tinspin.index.rtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import static org.tinspin.index.Index.*;

public class RTreeIterator<T> implements BoxIterator<T> {
	
	private class IteratorStack {
		private final IterPos<T>[] stack;
		private int size = 0;
		
		@SuppressWarnings("unchecked")
		IteratorStack(int depth) {
			stack = new IterPos[depth];
		}

		boolean isEmpty() {
			return size == 0;
		}

		void prepareAndPush(RTreeNode<T> node) {
			IterPos<T> ni = stack[size++];
			if (ni == null)  {
				ni = new IterPos<>();
				stack[size-1] = ni;
			}
			
			ni.init(node);
		}

		IterPos<T> peek() {
			return stack[size-1];
		}

		void pop() {
			--size;
		}
	}

	private final RTree<T> tree;
	private double[] min;
	private double[] max;
	private IteratorStack stack;
	private boolean hasNext = true;
	private RTreeEntry<T> next;
	private final Predicate<RTreeEntry<T>> filter;
	
	private static class IterPos<T> {
		private RTreeNode<T> node;
		private int pos;
		
		public void init(RTreeNode<T> node) {
			this.node = node;
			this.pos = 0;
		}
	}

	public RTreeIterator(RTree<T> tree, double[] min, double[] max) {
		this.stack = new IteratorStack(tree.getDepth());
		this.tree = tree;
		// Default: intersection query
		this.filter = (RTreeEntry<T> entry)-> RTreeEntry.checkOverlap(this.min, this.max, entry);
		reset(min, max);
	}

	public static <T> RTreeIterator<T> createExactMatch(RTree<T> tree, double[] min, double[] max) {
		return new RTreeIterator<>(tree, min, max, e -> e instanceof RTreeNode ? RTreeEntry.checkOverlap(min, max, e) :
				Arrays.equals(min, e.min()) && Arrays.equals(max, e.max()));
	}

	private RTreeIterator(RTree<T> tree, double[] min, double[] max, Predicate<RTreeEntry<T>> filter) {
		this.stack = new IteratorStack(tree.getDepth());
		this.tree = tree;
		this.filter = filter;
		reset(min, max);
	}

	@Override
	public BoxIterator<T> reset(double[] min, double[] max) {
		if (stack.stack.length < tree.getDepth()) {
			this.stack = new IteratorStack(tree.getDepth());
		} else {
			this.stack.size = 0;
		}
		this.min = min;
		this.max = max;
		this.hasNext = true;
		
		if (!RTreeEntry.checkOverlap(min, max, tree.getRoot())) {
			hasNext = false;
			return null;
		}
		this.stack.prepareAndPush(tree.getRoot());
		findNext();
		return this;
	}
	
	private void findNext() {
		nextSub:
		while (!stack.isEmpty()) {
			IterPos<T> ip = stack.peek();
			ArrayList<RTreeEntry<T>> entries = ip.node.getEntries();
			while (ip.pos < entries.size()) {
				RTreeEntry<T> e = entries.get(ip.pos);
				ip.pos++;
				if (filter.test(e)) {
					if (e instanceof RTreeNode) {
						stack.prepareAndPush((RTreeNode<T>) e);
						continue nextSub;
					} else {
						next = e;
						return;
					}
				}
			}
			stack.pop();
		}
		hasNext = false;
	}
	
	@Override
	public boolean hasNext() {
		return hasNext;
	}

	@Override
	public RTreeEntry<T> next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		RTreeEntry<T> ret = next;
		findNext();
		return ret;
	}
	
}