/*
 * Copyright 2016 Tilmann Zäschke
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

public class RStarTreeLogic implements RTreeLogic {

	private final SortByAxisAsc SORT_BY_AXIS_ASC = new SortByAxisAsc();
	private final SortByAxisDes SORT_BY_AXIS_DES = new SortByAxisDes();
	
	/**
	 * Choose subtree as described in the paper.
	 */
	@Override
	public <T> RTreeNode<T> chooseSubTree(RTreeNode<T> root, 
			Entry<T> e, int desiredInsertionLevel, int nLevels) {
		//CS1
		RTreeNode<T> node = root;
		int level = nLevels-1;
		
		//CS2
		while (level != desiredInsertionLevel) {
			RTreeNodeDir<T> dir = (RTreeNodeDir<T>) node;
			if (dir.containsLeafNodes()) {
				//determine minimum overlap cost
				node = chooseNodeWithNearlyMinimumOverlapCost(dir, e);
			} else {
				//determine minimum area cost
				node = chooseNodeWithLeastAreaEnlargement(dir, e);
			}
			level--;
		}
		//this is a leaf node
		return node;
	}

	private static class NDPair<T> implements Comparable<NDPair<T>>{
		RTreeNode<T> node;
		double d;
		public NDPair(double d, RTreeNode<T> node) {
			this.d = d;
			this.node = node;
		}
		@Override
		public int compareTo(NDPair<T> o) {
			return d < o.d ? -1 : d > o.d ? 1 : 0;
		}
		@Override
		public boolean equals(Object obj) {
			return this == obj;
		}
	}
	
	private static class EDPair<T> implements Comparable<EDPair<T>>{
		Entry<T> entry;
		double d;
		public EDPair(double d, Entry<T> entry) {
			this.d = d;
			this.entry = entry;
		}
		@Override
		public int compareTo(EDPair<T> o) {
			return d < o.d ? -1 : d > o.d ? 1 : 0;
		}
	}
	
	@SuppressWarnings("unchecked")
	private <T> RTreeNode<T> chooseNodeWithNearlyMinimumOverlapCost(
			RTreeNodeDir<T> dir, Entry<T> e) {
		final int P = Math.min(32, dir.getChildren().size());
		
		//rank by area enlargement
		ArrayList<RTreeNode<T>> children = dir.getChildren();
		//TODO This doesn't always allow for ties...
		NDPair<T>[] areas = new NDPair[P];
		for (int i = 0; i < children.size(); i++) {
			RTreeNode<T> child = children.get(i);
			double area = calcAreaEnlargementSize(child, e);
			if (i < P) {
				areas[i] = new NDPair<>(area, child);
				if (i == P-1) {
					Arrays.sort(areas);
				}
			} else if (areas[P-1].d > area) {
				areas[P-1].d = area;
				areas[P-1].node = child;
			}
		}
		int dims = dir.min.length;
		Entry<T> enlarged = new Entry<T>(new double[dims], new double[dims], null);
		double bestOverLap = Double.MAX_VALUE;
		RTreeNode<T> bestNode = null;
		for (int i = 0; i < P; i++) {
			enlarged.setToCover(e, areas[i].node);
			double o = calcOverlapSize(enlarged, areas[i].node, children);
			if (o < bestOverLap) {
				bestOverLap = o;
				bestNode = areas[i].node; 
			} else if (o == bestOverLap) {
				//ties are resolved by choosing the node with the smallest area
				double aBest = bestNode.calcArea();
				double aNew = areas[i].node.calcArea();
				if (aNew < aBest) {
					bestOverLap = o;
					bestNode = areas[i].node; 
				}
			}
		}
		return bestNode;
	}

	private <T> double calcAreaEnlargementSize(RTreeNode<T> node, Entry<T> e) {
		return node.calcAreaEnlarged(e) - node.calcArea();
	}

	private <T> double calcOverlapSize(Entry<T> node, Entry<T> toSkip, ArrayList<RTreeNode<T>> children) {
		double o = 0;
		for (int i = 0; i < children.size(); i++) {
			if (children.get(i) != toSkip) {
				o += node.calcOverlap(children.get(i));
			}
		}
		return o;
	}

	private <T> RTreeNode<T> chooseNodeWithLeastAreaEnlargement(RTreeNodeDir<T> dir, Entry<T> e) {
		ArrayList<RTreeNode<T>> children = dir.getChildren();
		double bestAreaEnl = Double.MAX_VALUE;
		RTreeNode<T> bestNode = null;
		for (int i = 0; i < children.size(); i++) {
			RTreeNode<T> child = children.get(i);
			double areaEnl = calcAreaEnlargementSize(child, e);
			if (areaEnl < bestAreaEnl) {
				bestAreaEnl = areaEnl;
				bestNode = child; 
			} if (areaEnl == bestAreaEnl) {
				//ties are resolved by choosing the node with the smallest area
				double aBest = bestNode.calcArea();
				double aNew = child.calcArea();
				if (aNew < aBest) {
					bestAreaEnl = areaEnl;
					bestNode = child; 
				}
			}
		}
		return bestNode;
	}

	/**
	 * Choose subtree as described in the paper.
	 * 
	 * According to paper the function
	 * a) returns (param) a sorted array of BB sorted for splitting
	 * b) returns (ret) the split dimension
	 * c) 
	 */
	@SuppressWarnings("unchecked")
	@Override
	public <T> RTreeNode<T> split(RTreeNode<T> node, Entry<T> e) {
		//S1 determine axis
		final int M = getM(node);
		Entry<T>[] children = node.getEntries().toArray(new Entry[M+1]);
		children[M] = e;
		
		int splitAxis = chooseSplitAxis(children);

		//S2 choose split index 
		//S3 split
		return chooseSplitIndex(node, children, splitAxis);
	}
	
	private <T> int chooseSplitAxis(Entry<T>[] children) {
		int dims = children[0].min.length;
		double[] bufMin = new double[dims];
		double[] bufMax = new double[dims];

		final int M = children.length;
		final int m = (int) (0.40 * M);
		final int kMax = M-2*m+1;
		int kMin = m;
		int kEnd = m + kMax;

		if (RTree.DEBUG && m > M) {
			throw new IllegalStateException();
		}
		
		//CSA1 
		double bestMargin = Double.MAX_VALUE;
		int bestDim = -1;
		for (int d = 0; d < dims; d++) {
			//total margin S for all combinations of this split axis
			double totalMargin = 0;
			for (int sortOrder = 0; sortOrder < 2; sortOrder++) {
				if (sortOrder == 0) {
					SORT_BY_AXIS_ASC.setAxis(d);
					Arrays.sort(children, SORT_BY_AXIS_ASC);
				} else {
					SORT_BY_AXIS_DES.setAxis(d);
					Arrays.sort(children, SORT_BY_AXIS_DES);
				}

				for (int k = kMin+1; k <= kEnd; k++) {
					totalMargin += calcMargin(children, 0, k, bufMin, bufMax);
					totalMargin += calcMargin(children, k, children.length, bufMin, bufMax);
				}
			}
			//CSA2
			if (totalMargin < bestMargin) {
				bestMargin = totalMargin;
				bestDim = d;
			}
		}
		return bestDim;
	} 

	private <T> double calcMargin(Entry<T>[] entries, int start, int end, 
			double[] min, double[] max) {
		Entry.calcBoundingBox(entries, start, end, min, max);
		return Entry.calcMargin(min, max);
	}

	/**
	 * 
	 * @param children After calling this methods, the children will be in the best sorting order.
	 * @param splitAxis
	 * @return the split index
	 */
	private <T> RTreeNode<T> chooseSplitIndex(
			RTreeNode<T> nodeToSplit, Entry<T>[] children, int splitAxis) {
		int dims = children[0].min.length;
		double[] bufMin1 = new double[dims];
		double[] bufMax1 = new double[dims];
		double[] bufMin2 = new double[dims];
		double[] bufMax2 = new double[dims];

		final int M = children.length-1;
		final int m = (int) (0.40 * M);
		final int kMax = (M-2*m+1);
		int kMin = m;
		int kEnd = m + kMax;

		int bestSortOrder = -1;
		double bestDeadSpace = Double.MAX_VALUE;
		double bestOverlap = Double.MAX_VALUE;
		int bestIndex = -1;
		SORT_BY_AXIS_ASC.setAxis(splitAxis);
		SORT_BY_AXIS_DES.setAxis(splitAxis);
		//CSI1
		for (int sortOrder = 0; sortOrder < 2; sortOrder++) {
			if (sortOrder == 0) {
				Arrays.sort(children, SORT_BY_AXIS_ASC);
			} else {
				Arrays.sort(children, SORT_BY_AXIS_DES);
			}
			for (int k = kMin+1; k <= kEnd; k++) {
				//TODO This is done as in the reference implementation.
				//     To improve performance, it may be worth to calculate deadSpace separately,
				//     it is only needed in case of a draw.
				double ds1 = Entry.calcDeadspace(children, 0, k, bufMin1, bufMax1);
				double ds2 = Entry.calcDeadspace(children, k, children.length, bufMin2, bufMax2);
				double overlap = Entry.calcOverlap(bufMin1, bufMax1, bufMin2, bufMax2);
				if (overlap < bestOverlap || (overlap == bestOverlap && ds1+ds2 < bestDeadSpace)) {
					bestOverlap = overlap;
					bestDeadSpace = ds1 + ds2;
					bestSortOrder = sortOrder;
					bestIndex = k;
				}
			}
		}
		//ensure that children are in correct order
		if (bestSortOrder == 0) {
			Arrays.sort(children, SORT_BY_AXIS_ASC);
		}
		
		//split
		RTreeNode<T> newNode;
		if (nodeToSplit instanceof RTreeNodeDir) {
			newNode = new RTreeNodeDir<>(dims);
		} else {
			newNode = new RTreeNodeLeaf<>(dims);
		}
		
		nodeToSplit.clear();
		for (int i = 0; i < bestIndex; i++) {
			nodeToSplit.addEntry(children[i]);
		}
		//shrink parents
		nodeToSplit.recalcParentMBB();
	
		for (int i = bestIndex; i < children.length; i++) {
			newNode.addEntry(children[i]);
		}
		
		return newNode;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> Entry<T>[] reInsert(RTreeNode<T> node, Entry<T> e) {
		//RI1 calculate center distances from node center
		final int M = getM(node);
		EDPair<T>[] children = new EDPair[M+1];
		ArrayList<Entry<T>> currentChildren =  node.getEntries();
		for (int i = 0; i < M; i++) {
			Entry<T> c = currentChildren.get(i);
			double cd = Entry.calcCenterDistance(node, c);
			children[i] = new EDPair<>(cd, c);
		}
		double cd = Entry.calcCenterDistance(node, e);
		children[M] = new EDPair<>(cd, e);
		
		//RI2 sort according to distance
		Arrays.sort(children);
		
		//RI3 remove first P entries and adjust BB
		int p = (int) (0.30 * (M+1));
		int nToKeep = (M+1)-p;

		node.clear();
		for (int i = 0; i < nToKeep; i++) {
			node.addEntry(children[i].entry);
		}
		//shrink parents
		node.recalcParentMBB();
		
		//RI4 reinsert entries
		//use 'close reinsert', starting with closest values, as suggested in paper
		Entry<T>[] toReinsert = new Entry[p];
		for (int i = 0; i < p; i++) {
			toReinsert[i] = children[i+nToKeep].entry;
		}
		return toReinsert; 
	}

	private class SortByAxisAsc implements Comparator<Entry<?>> {
		int axis = -1;
		public void setAxis(int axis) {
			this.axis = axis;
		}
		@Override
		public int compare(Entry<?> o1, Entry<?> o2) {
			double dMin = o1.min[axis] - o2.min[axis];
			if (dMin < 0) {
				return -1; 
			} else if (dMin > 0) {
				return 1;
			}
			double dMax = o1.max[axis] - o2.max[axis];
			return dMax < 0 ? -1 : dMax > 0 ? 1 : 0; 
		}
	}
	
	private class SortByAxisDes implements Comparator<Entry<?>> {
		int axis = -1;
		public void setAxis(int axis) {
			this.axis = axis;
		}
		@Override
		public int compare(Entry<?> o2, Entry<?> o1) {
			double dMin = o1.min[axis] - o2.min[axis];
			if (dMin < 0) {
				return -1; 
			} else if (dMin > 0) {
				return 1;
			}
			double dMax = o1.max[axis] - o2.max[axis];
			return dMax < 0 ? -1 : dMax > 0 ? 1 : 0; 
		}
	}
	

	@Override
	public <T> boolean hasSpace(RTreeNode<T> node) {
		return node.hasSpace();
	}

	private boolean isLeaf(RTreeNode<?> node) {
		return node instanceof RTreeNodeLeaf;
	}
	
	private int getM(RTreeNode<?> node) {
		return isLeaf(node) ? RTree.NODE_MAX_DATA : RTree.NODE_MAX_DIR;
	}

}
