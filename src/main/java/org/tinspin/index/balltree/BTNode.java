/*
 * Copyright 2016-2024 Tilmann Zaeschke
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
package org.tinspin.index.balltree;

import org.tinspin.index.PointDistance;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Predicate;

import static org.tinspin.index.Index.PointEntry;

/**
 * Node class for the quadtree.
 * 
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class BTNode<T> {

	private static final PointDistance DIST = PointDistance.L2;
	private BTNode<T> parent;
	private final double[] center;
	private double radius;
	// null indicates that we have sub-node i.o. values
	private ArrayList<PointEntry<T>> values;
	private BTNode<T> left;
	private BTNode<T> right;

	BTNode(double[] center, double radius, BTNode<T> parent, ArrayList<PointEntry<T>> values) {
		this.center = center;
		this.radius = radius;
		this.parent = parent;
		this.values = values;
	}

	BTNode(double[] center, double radius, BTNode<T> parent, BTNode<T> left, BTNode<T> right) {
		this.center = center;
		this.radius = radius;
		this.parent = parent;
		this.values = null;
		this.left = left;
		this.right = right;
	}

	@SuppressWarnings({"unused" })
	BTNode<T> tryPut(PointEntry<T> e, int maxNodeSize, boolean enforceLeaf) {
		if (BallTree.DEBUG && !BTUtil.fitsIntoNode(e.point(), center, radius)) {
			throw new IllegalStateException("e=" + Arrays.toString(e.point()) + 
					" center/radius=" + Arrays.toString(center) + "/" + radius);
		}
		
		//traverse subs?
		if (values == null) {
			return findBestChildForInsert(e);
		}
		
		//add if:
		//a) we have space
		//b) we have maxDepth
		//c) elements are equal (work only for n=1, avoids splitting
		//   in cases where splitting won't help. For n>1 the
		//   local limit is (temporarily) violated.
		if (values.size() < maxNodeSize || enforceLeaf || 
				BTUtil.isPointEqual(e.point(), values.get(0).point())) {
			values.add(e);
			return null;
		}
		
		//split
		ArrayList<PointEntry<T>> vals = values;
		values = null;
		PointEntry<T> start = vals.get(0);
		int dims = start.point().length;
		double[][] ordered = BTUtil.orderCoordinates(vals);
		int splitDim = -1;
		double maxRange = -1;
		for (int d = 0; d < dims; d++) {
			double[] rangeArray = ordered[d];
			double range = rangeArray[rangeArray.length - 1] - rangeArray[0];
			if (range > maxRange) {
				maxRange = range;
				splitDim = d;
			}
		}
		double splitValue = ordered[splitDim][vals.size() / 2];

		ArrayList<PointEntry<T>> leftPoints = new ArrayList<>();
		ArrayList<PointEntry<T>> rightPoints = new ArrayList<>();
		for (int i = 0; i < vals.size(); i++) {
			PointEntry<T> pe = vals.get(i);
			if (pe.point()[splitDim] >= splitValue) {
				rightPoints.add(pe);
			} else {
				leftPoints.add(pe);
			}
		}

		double[] centerLeft = new double[dims];
		double[] centerRight = new double[dims];
		double radiusLeft = BTUtil.calcBoundingSphere(leftPoints, centerLeft);
		double radiusRight = BTUtil.calcBoundingSphere(rightPoints, centerRight);

		this.left = new BTNode<>(centerLeft, radiusLeft, this, leftPoints);
		this.right = new BTNode<>(centerRight, radiusRight, this, rightPoints);

		return findBestChildForInsert(e);
	}


	private BTNode<T> findBestChildForInsert(PointEntry<T> e) {
		if (isLeaf()) {
			throw new IllegalStateException();
		}
		double radiusLeft = left.radius;
		double radiusRight = right.radius;
		int dims = center.length;

		double distLeft = DIST.dist(e.point(), this.left.center);
		double distRight = DIST.dist(e.point(), this.right.center);

		double resizeLeft = distLeft > radiusLeft ? distLeft - radiusLeft : 0;
		double resizeRight = distRight > radiusRight ? distRight - radiusRight : 0;

		double resizeVolLeft = resizeLeft > 0 ? Math.pow(distLeft, dims) - Math.pow(radiusLeft, dims) : 0;
		double resizeVolRight = resizeRight > 0 ? Math.pow(distRight, dims) - Math.pow(radiusRight, dims) : 0;

		if (resizeVolLeft == 0 && resizeVolRight == 0) {
			// We just pick the one with the smaller radius -> more compact...  ?
			return  this.left.radius < this.right.radius ? this.left : this.right;
		}

		// adjust parent radius (and position???)
		if (parent != null) {
			parent.adjustRadius();
		}
		return resizeVolLeft > resizeVolRight ? this.right : this.left;
	}

	PointEntry<T> remove(BTNode<T> parent, double[] key, int maxNodeSize, Predicate<PointEntry<T>> pred) {
		if (!isLeaf()) {
			if (DIST.dist(left.center, key) <= left.radius) {
				PointEntry<T> ret = left.remove(this, key, maxNodeSize, pred);
				if (ret != null) {
					return ret;
				}
			}
			if (DIST.dist(right.center, key) <= right.radius) {
				return right.remove(this, key, maxNodeSize, pred);
			}
			return null;
		}
		
		for (int i = 0; i < values.size(); i++) {
			PointEntry<T> e = values.get(i);
			if (BTUtil.isPointEqual(e.point(), key) && pred.test(e)) {
				values.remove(i);
				//TODO provide threshold for re-insert
				//i.e. do not always merge.
				if (parent != null) {
					parent.checkAndMergeLeafNodes(maxNodeSize);
				}
				return e;
			}
		}
		return null;
	}

	PointEntry<T> update(BTNode<T> parent, double[] keyOld, double[] keyNew, int maxNodeSize,
						 boolean[] requiresReinsert, int currentDepth, int maxDepth, Predicate<PointEntry<T>> pred) {
		if (!isLeaf()) {
			PointEntry<T> ret = null;
			if (DIST.dist(left.center, keyOld) <= left.radius) {
				ret = left.update(this, keyOld, keyNew, maxNodeSize, requiresReinsert, currentDepth + 1, maxDepth, pred);
				if (ret == null) {
					return ret;
				}
			}
			if (DIST.dist(right.center, keyOld) <= right.radius) {
				ret = right.update(this, keyOld, keyNew, maxNodeSize, requiresReinsert, currentDepth + 1, maxDepth, pred);
				if (ret == null) {
					return ret;
				}
			}
			if (ret != null && requiresReinsert[0] &&
					BTUtil.fitsIntoNode(ret.point(), center, radius/ BTUtil.EPS_MUL)) {
				requiresReinsert[0] = false;
				BTNode<T> r = this;
				while (r != null) {
					r = r.tryPut(ret, maxNodeSize, currentDepth++ > maxDepth);
				}
			}
			return ret;
		}
		
		for (int i = 0; i < values.size(); i++) {
			PointEntry<T> e = values.get(i);
			if (BTUtil.isPointEqual(e.point(), keyOld) && pred.test(e)) {
				values.remove(i);
				e.setPoint(keyNew);
				if (BTUtil.fitsIntoNode(keyNew, center, radius/ BTUtil.EPS_MUL)) {
					// reinsert locally;
					values.add(e);
					requiresReinsert[0] = false;
				} else {
					requiresReinsert[0] = true;
					//TODO provide threshold for re-insert
					//i.e. do not always merge.
					if (parent != null) {
						parent.checkAndMergeLeafNodes(maxNodeSize);
					}
				}
				return e;
			}
		}
		requiresReinsert[0] = false;
		return null;
	}

	private void checkAndMergeLeafNodes(int maxNodeSize) {
		if (isLeaf()) {
			throw new IllegalStateException();
		}
		if (!left.isLeaf() && !right.isLeaf()) {
			// This should only be called by a leaf node->parent
			throw new IllegalStateException();
		}

		// Several possibilities:
		// - Parent has two leaves -> merging is straight forward
		// - Parent has one leaf only.
		//   -> Current approach: do not merge, unless leaf is empty.
		//   TODO improve
		//   -> rebalance? Reinsert remaining entries somewhere else?

		// Case: One inner node + one leaf (empty)
		if (!right.isLeaf() && left.isLeaf() && left.values.isEmpty()) {
			left = right.left;
			right = right.right;
			adjustRadius();
			return;
		}
		if (!left.isLeaf() && right.isLeaf() && right.values.isEmpty()) {
			right = left.right;
			left = left.left;
			adjustRadius();
			return;
		}

		if (left.isLeaf() != right.isLeaf()) {
			// TODO may later merge and reinsert entries somewhere else? -> rebalance()?
			return;
		}

		int threshold = maxNodeSize / 5;
		if (left.values.size() + right.values.size() + threshold > maxNodeSize) {
			// Too many entries to merge
			return;
		}

		//okay, let's merge
		values = left.values;
		values.addAll(right.values);

		left = null;
		right = null;
	}

	double[] getCenter() {
		return center;
	}

	double getRadius() {
		return radius;
	}

	PointEntry<T> getExact(double[] key, Predicate<PointEntry<T>> pred) {
		if (!isLeaf()) {
			if (DIST.dist(left.center, key) <= left.radius) {
				PointEntry<T> ret = left.getExact(key, pred);
				if (ret != null) {
					return ret;
				}
			}
			if (DIST.dist(right.center, key) <= right.radius) {
				return right.getExact(key, pred);
			}
			return null;
		}

		for (int i = 0; i < values.size(); i++) {
			PointEntry<T> e = values.get(i);
			if (BTUtil.isPointEqual(e.point(), key) && pred.test(e)) {
				return e;
			}
		}
		return null;
	}

	ArrayList<PointEntry<T>> getEntries() {
		return values;
	}

	
	@Override
	public String toString() {
		return "center/radius=" + Arrays.toString(center) + "/" + radius + 
				" " + System.identityHashCode(this);
	}

	void checkNode(BallTree.BTStats s, BTNode<T> parent, int depth) {
		if (depth > s.maxDepth) {
			s.maxDepth = depth;
		}
		s.nNodes++;
		
		if (parent != null) {
			if (!BTUtil.isNodeEnclosed(center, radius, parent.center, parent.radius* BTUtil.EPS_MUL)) {
				for (int d = 0; d < center.length; d++) {
//					if ((centerOuter[d]+radiusOuter) / (centerEnclosed[d]+radiusEnclosed) < 0.9999999 || 
//							(centerOuter[d]-radiusOuter) / (centerEnclosed[d]-radiusEnclosed) > 1.0000001) {
//						return false;
//					}
					System.out.println("Outer: " + parent.radius + " " + 
						Arrays.toString(parent.center));
					System.out.println("Child: " + radius + " " + Arrays.toString(center));
					System.out.println((parent.center[d]+parent.radius) + " vs " + (center[d]+radius)); 
					System.out.println("r=" + (parent.center[d]+parent.radius) / (center[d]+radius)); 
					System.out.println((parent.center[d]-parent.radius) + " vs " + (center[d]-radius));
					System.out.println("r=" + (parent.center[d]-parent.radius) / (center[d]-radius));
				}
				throw new IllegalStateException();
			}
		}
		if (values != null) {
			s.nLeaf++;
			s.nEntries += values.size();
			s.histoValues[values.size()]++;
			s.maxValuesInNode = Math.max(s.maxValuesInNode, values.size());
			for (int i = 0; i < values.size(); i++) {
				PointEntry<T> e = values.get(i);
				if (!BTUtil.fitsIntoNode(e.point(), center, radius* BTUtil.EPS_MUL)) {
					System.out.println("Node: " + radius + " " + Arrays.toString(center));
					System.out.println("Child: " + Arrays.toString(e.point()));
					for (int d = 0; d < center.length; d++) {
//						if ((centerOuter[d]+radiusOuter) / (centerEnclosed[d]+radiusEnclosed) < 0.9999999 || 
//								(centerOuter[d]-radiusOuter) / (centerEnclosed[d]-radiusEnclosed) > 1.0000001) {
//							return false;
//						}
						System.out.println("min/max for " + d);
						System.out.println("min: " + (center[d]-radius) + " vs " + (e.point()[d]));
						System.out.println("r=" + (center[d]-radius) / (e.point()[d]));
						System.out.println("max: " + (center[d]+radius) + " vs " + (e.point()[d])); 
						System.out.println("r=" + (center[d]+radius) / (e.point()[d])); 
					}
					throw new IllegalStateException();
				}
			}
			if (left != null || right != null) {
				throw new IllegalStateException();
			}
		} else {
			s.nInner++;
			left.checkNode(s, this, depth + 1);
			right.checkNode(s, this, depth + 1);
		}
	}

	boolean isLeaf() {
		return values != null;
	}

	BTNode<T> getLeftChild() {
		return left;
	}

	BTNode<T> getRightChild() {
		return right;
	}

	// TODO add radii as argument
	void adjustRadius() {
		if (isLeaf()) {
			throw new IllegalStateException();
		}
		// TODO adjust center???
		// adjust radius
		double rLeft = DIST.dist(center, left.getCenter()) + left.radius;
		double rRight = DIST.dist(center, right.getCenter()) + right.radius;
		double rNeeded = Math.max(rLeft, rRight);
		if (rNeeded <= radius) {
			// Nothing to do.
			return;
		}
		boolean adJustRadiusOnly = true;
		if (adJustRadiusOnly) {
			this.radius = rNeeded;
			if (parent != null) {
				parent.adjustRadius();
			}
			return;
		}
		return;

//		// adjust radius and position
//		double deltaRadius = (rNeeded-radius) / 2.;


	}
}
