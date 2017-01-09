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
		IteratorStack(int depth, int nSubNodes) {
			stack = new IterPos[depth];
			for (int i = 0; i < stack.length; i++) {
				stack[i] = new IterPos<>(nSubNodes);
			}
		}

		boolean isEmpty() {
			return size == 0;
		}

		IterPos<T> prepareAndPush(RTreeNode<T> node, double minDist) {
			IterPos<T> ni = stack[size++];
			ni.init(node);
			if (ni.node instanceof RTreeNodeDir) {
				sortEntries(ni, minDist);
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
		final DistEntry<RTreeNode<T>>[] subNodes;
		RTreeNode<T> node;
		int pos;
		int maxPos;
		
		@SuppressWarnings("unchecked")
		public IterPos(int nSubNodes) {
			subNodes = new DistEntry[nSubNodes];
			for (int i = 0; i < nSubNodes; i++) {
				subNodes[i] = new DistEntry<>(null,  null,  null, Double.POSITIVE_INFINITY);
			}
		}
		
		void init(RTreeNode<T> node) {
			this.node = node;
			this.pos = 0;
		}
	}
	
	public RTreeQuery1NN(RTree<T> tree) {
		this.stack = new IteratorStack(tree.getDepth(), RTree.NODE_MAX_DIR);
		this.tree = tree;
	}

	public DistEntry<T> reset(double[] center, DistanceFunction dist) {
		if (stack.stack.length < tree.getDepth()) {
			this.stack = new IteratorStack(tree.getDepth(), RTree.NODE_MAX_DIR);
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
		
		this.stack.prepareAndPush(tree.getRoot(), Double.POSITIVE_INFINITY);
		return findCandidate();
	}
	
	private DistEntry<T> findCandidate() {
		DistEntry<T> candidate  = new DistEntry<>(null, null, null, Double.POSITIVE_INFINITY);
		double currentDist = Double.MAX_VALUE;
		nextSub:
		while (!stack.isEmpty()) {
			IterPos<T> ip = stack.peek();
			if (ip.node instanceof RTreeNodeDir) {
				while (ip.pos < ip.maxPos && 
						ip.subNodes[ip.pos].dist() < currentDist) {
					stack.prepareAndPush(ip.subNodes[ip.pos].value(), currentDist);
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
	
//	protected void sortEntries(IterPos<T> iPos, double minDist) {
//		ArrayList<RTreeNode<T>> subNodes = ((RTreeNodeDir<T>)iPos.node).getChildren();
//		DistEntry<RTreeNode<T>>[] ret = iPos.subNodes;
//		int pos = 0;
//		for (int i = 0; i < subNodes.size(); i++) {
//			RTreeNode<T> e = subNodes.get(i);
//			double d = dist.dist(center, e.min, e.max);
//			if (d < minDist) {
//				ret[pos++].set(e.lower(), e.upper(), e, d);
//			}
//		}
//		for (int i = pos; i < iPos.maxPos; i++ ) {
//			ret[i].set(null, null, null, Double.POSITIVE_INFINITY);
//		}
//		iPos.maxPos = pos;
//		Arrays.sort(ret, 0, pos, COMP);
//	}
	
	/**
	 * 
	 * @param iPos
	 * @param minDist
	 */
	protected void sortEntries(IterPos<T> iPos, double minDist) {
		ArrayList<RTreeNode<T>> subNodes = ((RTreeNodeDir<T>)iPos.node).getChildren();
		DistEntry<RTreeNode<T>>[] ret = iPos.subNodes;
		int pos = 0;
		for (int i = 0; i < subNodes.size(); i++) {
			RTreeNode<T> e = subNodes.get(i);
			double d = dist.dist(center, e.min, e.max);
			//Strategy #1/#3
			if (d < minDist) {
				double minMaxDist = minMaxDist(e);
				ret[pos++].set(e.lower(), e.upper(), e, d, minMaxDist);
				//Strategy #2
				minDist = Math.min(minMaxDist, minDist);
			}
		}
		
		Arrays.sort(ret, 0, pos, COMP);

		//prune with minMaxDist/dist again
		for (int i = 0; i < pos; i++) {
			if (ret[i].dist() > minDist) {
				//discard the rest
				pos = i;
				break;
			}
		}
		
		iPos.maxPos = pos;
	}

	/**
	 * MinMax dist calculation, see
	 * "Nearest Neighbor Query" by
	 * N. Roussopoulos, and S. Kelley, and F. Vincent.
	 * 
	 * @param e
	 * @return min max dist
	 */
	private double minMaxDist(RTreeNode<T> e) {
    	double minimum = Double.POSITIVE_INFINITY;
    	double S = 0;

        double[] min = e.lower();
        double[] max = e.upper();
        
        for (int i = 0; i < center.length; i++) {
            double rMi = (center[i] >= (min[i]+max[i])/2) ? min[i] : max[i];
            double d = center[i] - rMi;
            S += d * d;
        }

        for (int k = 0; k < center.length; k++) {
        	double minMaxAvg = (min[k]+max[k]) / 2;
        	double rmk;
        	double rMi;
        	if (center[k] >= minMaxAvg) {
        		rMi = min[k];
        		rmk = max[k];
        	} else {
        		rMi = max[k];
        		rmk = min[k];
        	}

        	double sum = center[k] - rmk;
        	double sum2 = center[k] - rMi;
        	double x = S + sum*sum - sum2*sum2;
        	minimum = Math.min(minimum, x);
        }

        return Math.sqrt(minimum);
	}
}