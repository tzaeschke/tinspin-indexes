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
package org.tinspin.index.qtplain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Predicate;

import org.tinspin.index.qtplain.QuadTreeKD0.QStats;

import static org.tinspin.index.Index.*;

/**
 * Node class for the quadtree.
 * 
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class QNode<T> {

	private double[] center;
	private double radius;
	//null indicates that we have sub-nopde i.o. values
	private ArrayList<PointEntry<T>> values;
	private ArrayList<QNode<T>> subs;
	
	QNode(double[] center, double radius) {
		this.center = center;
		this.radius = radius;
		this.values = new ArrayList<>(2); 
	}

	QNode(double[] center, double radius, QNode<T> subNode) {
		this.center = center;
		this.radius = radius;
		this.values = null;
		this.subs = new ArrayList<>();
		subs.add(subNode);
	}

	@SuppressWarnings("unused")
	QNode<T> tryPut(PointEntry<T> e, int maxNodeSize, boolean enforceLeaf) {
		if (QuadTreeKD0.DEBUG && !QUtil.fitsIntoNode(e.point(), center, radius)) {
			throw new IllegalStateException("e=" + Arrays.toString(e.point()) + 
					" center/radius=" + Arrays.toString(center) + "/" + radius);
		}
		
		//traverse subs?
		if (values == null) {
			return getOrCreateSub(e);
		}
		
		//add if:
		//a) we have space
		//b) we have maxDepth
		//c) elements are equal (work only for n=1, avoids splitting
		//   in cases where splitting won't help. For n>1 the
		//   local limit is (temporarily) violated.
		if (values.size() < maxNodeSize || enforceLeaf || 
				QUtil.isPointEqual(e.point(), values.get(0).point())) {
			values.add(e);
			return null;
		}
		
		//split
		ArrayList<PointEntry<T>> vals = values;
		values = null;
		subs = new ArrayList<>();
		for (int i = 0; i < vals.size(); i++) {
			PointEntry<T> e2 = vals.get(i);
			QNode<T> sub = getOrCreateSub(e2);
			while (sub != null) {
				//This may recurse if all entries fall 
				//into the same subnode
				sub = sub.tryPut(e2, maxNodeSize, false);
			}
		}
		return getOrCreateSub(e);
	}

	private QNode<T> getOrCreateSub(PointEntry<T> e) {
		QNode<T> n = findSubNode(e.point());
		if (n == null) {
			n = createSubForEntry(e.point());
			subs.add(n);
		}
		return n;
	}
	
	private QNode<T> createSubForEntry(double[] p) {
		double[] centerSub = new double[center.length];
		//This ensures that the subsnodes completely cover the area of
		//the parent node.
		double radiusSub = radius/2.0;
		for (int d = 0; d < center.length; d++) {
			if (p[d] >= center[d]) {
				centerSub[d] = center[d]+radiusSub;
			} else {
				centerSub[d] = center[d]-radiusSub; 
			}
		}
		return new QNode<>(centerSub, radiusSub);		
	}
	
	/**
	 * The subnode position has reverse ordering of the point's
	 * dimension ordering. Dimension 0 of a point is the highest
	 * ordered bit in the position.
	 * @param p point
	 * @return subnode position
	 */
	private QNode<T> findSubNode(double[] p) {
		for (int i = 0; i < subs.size(); i++) {
			QNode<T> n = subs.get(i);
			if (QUtil.fitsIntoNode(p, n.center, n.radius)) {
				return n;
			}
		}
		return null;
	}

	PointEntry<T> remove(QNode<T> parent, double[] key, int maxNodeSize, Predicate<PointEntry<T>> pred) {
		if (values == null) {
			QNode<T> sub = findSubNode(key);
			if (sub != null) {
				return sub.remove(this, key, maxNodeSize, pred);
			}
			return null;
		}
		
		for (int i = 0; i < values.size(); i++) {
			PointEntry<T> e = values.get(i);
			if (QUtil.isPointEqual(e.point(), key) && pred.test(e)) {
				values.remove(i);
				// TODO provide threshold for re-insert
				// i.e. do not always merge.
				if (parent != null) {
					parent.checkAndMergeLeafNodes(maxNodeSize);
				}
				return e;
			}
		}
		return null;
	}

	PointEntry<T> update(QNode<T> parent, double[] keyOld, double[] keyNew, int maxNodeSize,
			boolean[] requiresReinsert, int currentDepth, int maxDepth, Predicate<PointEntry<T>> pred) {
		if (values == null) {
			QNode<T> sub = findSubNode(keyOld);
			if (sub == null) {
				return null;
			}
			PointEntry<T> ret = sub.update(this, keyOld, keyNew, maxNodeSize, requiresReinsert,
					currentDepth+1, maxDepth, pred);
			if (ret != null && requiresReinsert[0] && 
					QUtil.fitsIntoNode(ret.point(), center, radius)) {
				requiresReinsert[0] = false;
				QNode<T> r = this;
				while (r != null) {
					r = r.tryPut(ret, maxNodeSize, currentDepth++ > maxDepth);
				}
			}
			return ret;
		}
		
		for (int i = 0; i < values.size(); i++) {
			PointEntry<T> e = values.get(i);
			if (QUtil.isPointEqual(e.point(), keyOld) && pred.test(e)) {
				values.remove(i);
				e.setPoint(keyNew);
				if (QUtil.fitsIntoNode(keyNew, center, radius)) {
					// reinsert locally
					values.add(e);
					requiresReinsert[0] = false;
				} else {
					requiresReinsert[0] = true;
					// TODO provide threshold for re-insert
					// i.e. do not always merge.
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
		//check
		int nTotal = 0;
		for (int i = 0; i < subs.size(); i++) {
			if (subs.get(i).values == null) {
				//can't merge directory nodes.
				return; 
			}
			nTotal += subs.get(i).values.size();
			if (nTotal > maxNodeSize) {
				//too many children
				return;
			}
		}
		
		//okay, let's merge
		values = new ArrayList<>(nTotal);
		for (int i = 0; i < subs.size(); i++) {
			values.addAll(subs.get(i).values);
		}
		subs = null;
	}

	double[] getCenter() {
		return center;
	}

	double getRadius() {
		return radius;
	}

	PointEntry<T> getExact(double[] key, Predicate<PointEntry<T>> pred) {
		if (values == null) {
			QNode<T> sub = findSubNode(key);
			if (sub != null) {
				return sub.getExact(key, pred);
			}
			return null;
		}

		for (int i = 0; i < values.size(); i++) {
			PointEntry<T> e = values.get(i);
			if (QUtil.isPointEqual(e.point(), key) && pred.test(e)) {
				return e;
			}
		}
		return null;
	}

	ArrayList<PointEntry<T>> getEntries() {
		return values;
	}

	Iterator<?> getChildIterator() {
		if (values != null) {
			return values.iterator();
		}
		return subs.iterator();
	}

	@Override
	public String toString() {
		return "center/radius=" + Arrays.toString(center) + "/" + radius + 
				" " + System.identityHashCode(this);
	}

	void checkNode(QStats s, QNode<T> parent, int depth) {
		if (depth > s.maxDepth) {
			s.maxDepth = depth;
		}
		s.nNodes++;
		
		if (parent != null) {
			if (!QUtil.isNodeEnclosed(center, radius,
					parent.center, parent.radius*QUtil.EPS_MUL)) {
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
			for (int i = 0; i < values.size(); i++) {
				PointEntry<T> e = values.get(i);
				if (!QUtil.fitsIntoNode(e.point(), center, radius*QUtil.EPS_MUL)) {
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
			if (subs != null) {
				throw new IllegalStateException();
			}
		} else {
			for (int i = 0; i < subs.size(); i++) {
				QNode<T> n = subs.get(i);
				n.checkNode(s, this, depth+1);
			}
		}
	}

	boolean isLeaf() {
		return values != null;
	}

	ArrayList<QNode<T>> getChildNodes() {
		return subs;
	}


	void adjustRadius(double radius) {
		if (!isLeaf()) {
			throw new IllegalStateException();
		}
		this.radius = radius;
	}
}
