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
import java.util.Iterator;

/**
 * kNN search with EDGE distance and presorting of entries.
 * 
 * @author Tilmann Zäschke
 *
 * @param <T>
 */
public class RTreeIteratorKnn<T> implements Iterator<DistEntry<T>> {
	
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
	private int k;
	private IteratorStack stack;
	private final ArrayList<DistEntry<T>> candidates;
	private Iterator<DistEntry<T>> iter;
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
	
	public RTreeIteratorKnn(RTree<T> tree, double[] center, int k, 
			DistanceFunction dist) {
		this.stack = new IteratorStack(tree.getDepth());
		this.tree = tree;
		this.candidates = new ArrayList<>();
		reset(center, k, dist == null ? DistanceFunction.EDGE : dist);
	}

	public void reset(double[] center, int k, DistanceFunction dist) {
		if (stack.stack.length < tree.getDepth()) {
			this.stack = new IteratorStack(tree.getDepth());
		} else {
			this.stack.size = 0;
		}
		if (dist != null) {
			this.dist = dist;
		}
		if (!(this.dist instanceof DistanceFunction.EdgeDistance)) {
			System.err.println("This distance iterator only works for EDGE distance");
		}
		this.k = k;
		this.center = center;
		this.candidates.clear();
		if (k <= 0 || tree.size() == 0) {
			iter = candidates.iterator();
			return;
		}
		
		//estimate distance
		double distEst = estimateDistance();
		do {
			this.candidates.clear();
			this.stack.size = 0;
			this.stack.prepareAndPush(tree.getRoot());
			findCandidates(distEst);
			distEst *= 2;
			//repeat if we didn't get enough candidates, or if we got
			//candidates that are from outside the search circle (they got into the result set by
			//being inn the searched nodes).
		} while (candidates.size() < k || candidates.get(k-1).dist() > distEst/2);
	}
	
	private double estimateDistance() {
		RTreeNode<T> node = tree.getRoot();
		boolean done = false;
		outer: 
		while (node instanceof RTreeNodeDir && !done) {
			ArrayList<RTreeNode<T>> children = ((RTreeNodeDir<T>)node).getChildren();
			for (int i = 0; i < children.size(); i++) {
				RTreeNode<T> sub = children.get(i);
				if (sub.checkInclusion(center, center)) {
					node = sub;
					continue outer;
				}
			}
			//so this is a node that includes the point but none of the subnodes includes it.
			//We just use this, then.
			break;
		}
		//At this point we have the lowest node that includes 'center'.
		double dist = 0;
		for (int i = 0; i < center.length; i++) {
			if (Math.abs(node.upper()[i]-center[i]) > dist) {
				dist = Math.abs(node.upper()[i]-center[i]);
			}
			if (Math.abs(node.lower()[i]-center[i]) > dist) {
				dist = Math.abs(node.lower()[i]-center[i]);
			}
		}
		
		//use diagonal to ensure that all the node's points are within 'd'
		dist = dist*Math.sqrt(center.length);

		//double distance if local node is a leaf and does not contain k elements
		if (node.getEntries().size() < k && node instanceof RTreeNodeLeaf) {
			dist *= 2;
		}
		
		return dist;
	}

	private void findCandidates(double currentDist) {
		nextSub:
		while (!stack.isEmpty()) {
			IterPos<T> ip = stack.peek();
			if (ip.node instanceof RTreeNodeDir) {
				while (ip.pos < ip.subNodes.length && 
						ip.subNodes[ip.pos].dist() <= currentDist) {
					stack.prepareAndPush(ip.subNodes[ip.pos].value());
					ip.pos++;
					continue nextSub;
				}
			} else {
				ArrayList<Entry<T>> entries = ip.node.getEntries(); 
				while (ip.pos < entries.size()) {
					Entry<T> e = entries.get(ip.pos);
					ip.pos++;
					//if (Entry.checkOverlap(min, max, e)) {
					//TODO this works only for EDGE distance
					if (dist.dist(center, e.min, e.max) <= currentDist) {
						currentDist = checkCandidate(e, currentDist);
					}
				}
			}
			stack.pop();
		}
		iter = candidates.iterator();
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

	private double checkCandidate(Entry<T> e, double distEst) {
		double d = dist.dist(center, e.min, e.max);
		if (candidates.size() < k) {
			candidates.add(new DistEntry<T>(e.lower(), e.upper(), e.value(), d));
			candidates.sort(COMP);
		} else if (candidates.get(k-1).dist() > d) {
			candidates.remove(k-1);
			candidates.add(new DistEntry<T>(e.lower(), e.upper(), e.value(), d));
			candidates.sort(COMP);
			distEst = candidates.get(k-1).dist(); 
		}
		return distEst;
	}

	@Override
	public boolean hasNext() {
		return iter.hasNext();
	}

	@Override
	public DistEntry<T> next() {
		return iter.next();
	}
	
}