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
package org.zoodb.index.rtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * 1-NN search with EDGE distance and presorting of entries.
 * 
 * @author Tilmann Zäschke
 *
 * @param <T>
 */
public class RTreeQuery1NN<T> {
	
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

		IterPos<T> prepareAndPush(RTreeNode<T> node) {
			IterPos<T> ni = stack[size++];
			if (ni == null)  {
				ni = new IterPos<>();
				stack[size-1] = ni;
			}
			
			ni.init(node);
			if (ni.node instanceof RTreeNodeDir) {
				ni.subNodes = sortEntries(((RTreeNodeDir<T>)ni.node).getEntries());
			}
			return ni;
		}

		IterPos<T> peek() {
			return stack[size-1];
		}

		IterPos<T> pop() {
			return stack[--size];
		}
	}

	private static class DEComparator implements Comparator<DistEntry<?>> {
		@Override
		public int compare(DistEntry<?> o1, DistEntry<?> o2) {
			double d = o1.dist() - o2.dist();
			return d < 0 ? -1 : d > 0 ? 1 : 0;
		}
	}

	private final DEComparator COMP = new DEComparator();
	private final RTree<T> tree;
	private double[] center;
	private IteratorStack stack;
	private DistanceFunction dist;
	
	private static class IterPos<T> {
		DistEntry<RTreeNode<T>>[] subNodes;
		RTreeNode<T> node;
		int pos;
		
		void init(RTreeNode<T> node) {
			this.node = node;
			this.pos = 0;
		}
	}
	
	public RTreeQuery1NN(RTree<T> tree) {
		this.stack = new IteratorStack(tree.getDepth());
		this.tree = tree;
	}

	public DistEntry<T> reset(double[] center, DistanceFunction dist) {
		if (stack.stack.length < tree.getDepth()) {
			this.stack = new IteratorStack(tree.getDepth());
		} else {
			this.stack.size = 0;
		}
		if (dist != null) {
			this.dist = dist;
		}
		//set default if none is given
		if (this.dist == null) {
			this.dist = DistanceFunction.EDGE;
		}
		if (!(this.dist instanceof DistanceFunction.EdgeDistance)) {
			System.err.println("This distance iterator only works for EDGE distance");
		}
		this.center = center;
		if (tree.size() == 0) {
			return null;
		}
		
		this.stack.prepareAndPush(tree.getRoot());
		return findCandidate();
	}
	
	private DistEntry<T> findCandidate() {
		DistEntry<T> candidate  = new DistEntry<>(null, null, null, Double.POSITIVE_INFINITY);
		double currentDist = Double.MAX_VALUE;
		nextSub:
		while (!stack.isEmpty()) {
			IterPos<T> ip = stack.peek();
			if (ip.node instanceof RTreeNodeDir) {
				while (ip.pos < ip.subNodes.length && 
						ip.subNodes[ip.pos].dist() < currentDist) {
					stack.prepareAndPush(ip.subNodes[ip.pos].value());
					ip.pos++;
					continue nextSub;
				}
			} else {
				ArrayList<Entry<T>> entries = ip.node.getEntries(); 
				while (ip.pos < entries.size()) {
					Entry<T> e = entries.get(ip.pos);
					ip.pos++;
					//this works only for EDGE distance !!!
					double d = dist.dist(center, e.min, e.max);
					if (candidate.dist() > d) {
						candidate.set(e, d);
						currentDist = d; 
					}
				}
			}
			stack.pop();
		}
		return candidate;
	}
	
	@SuppressWarnings("unchecked")
	private DistEntry<RTreeNode<T>>[] sortEntries(
			ArrayList<Entry<T>> entries) {
		DistEntry<RTreeNode<T>>[] ret = new DistEntry[entries.size()];
		for (int i = 0; i < entries.size(); i++) {
			Entry<T> e = entries.get(i);
			double d = dist.dist(center, e.min, e.max);
			ret[i] = new DistEntry<RTreeNode<T>>(
					e.lower(), e.upper(), (RTreeNode<T>) e, d);
		}
		Arrays.sort(ret, COMP);
		return ret;
	}
	
}