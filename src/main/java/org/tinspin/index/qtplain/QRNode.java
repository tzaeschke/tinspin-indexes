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
public class QRNode<T> {

	private final double[] center;
	private final double radius;
	//null indicates that we have sub-nopde i.o. values
	private ArrayList<BoxEntry<T>> values;
	private ArrayList<QRNode<T>> subs;
	
	QRNode(double[] center, double radius) {
		this.center = center;
		this.radius = radius;
		this.values = new ArrayList<>(); 
	}

	QRNode(double[] center, double radius, QRNode<T> subNode) {
		this.center = center;
		this.radius = radius;
		this.values = null;
		this.subs = new ArrayList<>();
		subs.add(subNode);
	}

	@SuppressWarnings("unused")
	QRNode<T> tryPut(BoxEntry<T> e, int maxNodeSize, boolean enforceLeaf) {
		if (QuadTreeKD0.DEBUG && !QUtil.fitsIntoNode(e.min(), e.max(), center, radius)) {
			throw new IllegalStateException("e=" + e + 
					" center/radius=" + Arrays.toString(center) + "/" + radius);
		}
		
		//traverse subs?
		QRNode<T> sub1 = findSubNode(e.min(), e.max());
		if (subs != null && sub1 != this) {
			if (sub1 == null) {
				sub1 = createSubForEntry(e);
			}
			return sub1;
		}
		
		//add if:
		//a) we have space
		//b) we have maxDepth
		//c) elements are equal (work only for n=1, avoids splitting
		//   in cases where splitting won't help. For n>1 the
		//   local limit is (temporarily) violated.
		//d) We already have subs, which means that the local entries all 
		//   overlap with the centerpoint
		if (values == null) {
			values = new ArrayList<>();
		}
		if (values.size() < maxNodeSize || enforceLeaf || 
				QUtil.isRectEqual(e,values.get(0)) || subs != null) {
			values.add(e);
			return null;
		}
		
		//split
		ArrayList<BoxEntry<T>> vals = values;
		vals.add(e);
		values = null;
		subs = new ArrayList<>();
		for (int i = 0; i < vals.size(); i++) {
			BoxEntry<T> e2 = vals.get(i);
			QRNode<T> sub = findSubNode(e2.min(), e2.max());
			if (sub == this) {
				if (values == null) {
					values = new ArrayList<>();
				}
				values.add(e2);
				continue;
			}
			if (sub == null) {
				sub = createSubForEntry(e2);
			}
			while (sub != null) {
				//This may recurse if all entries fall 
				//into the same subnode
				sub = sub.tryPut(e2, maxNodeSize, false);
			}
		}
		return null;
	}

	private QRNode<T> createSubForEntry(BoxEntry<T> e) {
		double[] centerSub = new double[center.length];
		double[] pMin = e.min();
		//This ensures that the subsnodes completely cover the area of
		//the parent node.
		double radiusSub = radius/2.0;
		for (int d = 0; d < center.length; d++) {
			if (pMin[d] >= center[d]) {
				centerSub[d] = center[d]+radiusSub;
			} else {
				centerSub[d] = center[d]-radiusSub; 
			}
		}
		QRNode<T> n = new QRNode<>(centerSub, radiusSub);		
		subs.add(n);
		return n;
	}
	
	/**
	 * The subnode position has reverse ordering of the point's
	 * dimension ordering. Dimension 0 of a point is the highest
	 * ordered bit in the position.
	 * @param pMin box min
	 * @param pMax box max
	 * @return subnode position
	 */
	private QRNode<T> findSubNode(double[] pMin, double[] pMax) {
		if (subs != null) {
			for (int i = 0; i < subs.size(); i++) {
				QRNode<T> n = subs.get(i);
				if (QUtil.fitsIntoNode(pMin, pMax, n.center, n.radius)) {
					return n;
				}
			}
		}
		
		//Okay, we have to check whether the entry should at all go into a subnode.
		//It it crosses at least one border between quadrants, it belongs into
		//the local node. Pairwise check:
		for (int i = 0; i < center.length; i++) {
			if (pMin[i] < center[i] && pMax[i] >= center[i]) {
				//found crossing border
				return this;
			}
		}
		
		return null;
	}

	BoxEntry<T> remove(QRNode<T> parent, double[] keyL, double[] keyU, int maxNodeSize, Predicate<BoxEntry<T>> condition) {
		if (subs != null) {
			QRNode<T> sub = findSubNode(keyL, keyU);
			if (sub != this) {
				if (sub != null) {
					return sub.remove(this, keyL, keyU, maxNodeSize, condition);
				}
				return null;
			}
		}
		
		//now check local data
		if (values == null) {
			return null;
		}
		for (int i = 0; i < values.size(); i++) {
			BoxEntry<T> e = values.get(i);
			if (QUtil.isRectEqual(e, keyL, keyU) && condition.test(e)) {
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

	BoxEntry<T> update(QRNode<T> parent, double[] keyOldL, double[] keyOldU,
			double[] keyNewL, double[] keyNewU, int maxNodeSize,
			boolean[] requiresReinsert, int currentDepth, int maxDepth, Predicate<T> pred) {
		if (subs != null) {
			QRNode<T> sub = findSubNode(keyOldL, keyOldU);
			if (sub != this) {
				if (sub == null) {
					return null;
				}
				BoxEntry<T> ret = sub.update(this, keyOldL, keyOldU, keyNewL, keyNewU,
						maxNodeSize, requiresReinsert, currentDepth+1, maxDepth, pred);
				//Divide by EPS to ensure that we do not reinsert to low
				if (ret != null && requiresReinsert[0] && 
						QUtil.fitsIntoNode(ret.min(), ret.max(),
								center, radius)) {
					requiresReinsert[0] = false;
					QRNode<T> r = this;
					while (r != null) {
						r = r.tryPut(ret, maxNodeSize, currentDepth++ > maxDepth);
					}
				}
				return ret;
			}
		}
		
		//now check local data
		if (values == null) {
			return null;
		}
		for (int i = 0; i < values.size(); i++) {
			BoxEntry<T> e = values.get(i);
			if (QUtil.isRectEqual(e, keyOldL, keyOldU) && pred.test(e.value())) {
				values.remove(i);
				e.set(keyNewL, keyNewU);
				//Divide by EPS to ensure that we do not reinsert to low
				if (QUtil.fitsIntoNode(keyNewL, keyNewU, center, radius)) {
					requiresReinsert[0] = false;
					QRNode<T> sub = findSubNode(keyNewL, keyNewU);
					if (sub == this) {
						// reinsert locally
						values.add(e);
					} else {
						//we try to use subnode directly, if there is one.
						QRNode<T> r;
						if (sub == null) {
							//create node locally for insert
							r = this;
						} else {
							r = sub;
							currentDepth++;
						}
						while (r != null) {
							r = r.tryPut(e, maxNodeSize, currentDepth++ > maxDepth);
						}
					}
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
		//check
		int nTotal = 0;
		if (values != null) {
			nTotal += values.size();
		}
		for (int i = 0; i < subs.size(); i++) {
			QRNode<T> sub = subs.get(i);
			if (sub.subs != null) {
				//can't merge directory nodes.
				return; 
			}
			if (sub.values != null) {
				nTotal += sub.values.size();
			}					
			if (nTotal > maxNodeSize) {
				//too many children
				return;
			}
		}
		
		//okay, let's merge
		if (values == null) {
			values = new ArrayList<>();
		}
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

	BoxEntry<T> getExact(double[] keyL, double[] keyU, Predicate<BoxEntry<T>> condition) {
		if (subs != null) {
			QRNode<T> sub = findSubNode(keyL, keyU);
			if (sub != this) {
				if (sub != null) {
					return sub.getExact(keyL, keyU, condition);
				}
				return null;
			}
		}
		
		if (values == null) {
			return null;
		}
		
		for (int i = 0; i < values.size(); i++) {
			BoxEntry<T> e = values.get(i);
			if (QUtil.isRectEqual(e, keyL, keyU) && condition.test(e)) {
				return e;
			}
		}
		return null;
	}

	ArrayList<BoxEntry<T>> getEntries() {
		return values;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	Iterator<?> getChildIterator() {
		if (subs == null) {
			return values.iterator();
		}
		return new ArrayIterator(subs, values != null ? values : null);
	}

	private static class ArrayIterator<E> implements Iterator<E> {

		private Iterator<E> data;
		private ArrayList<E> data2;
		
		ArrayIterator(ArrayList<E> data1, ArrayList<E> data2) {
			this.data = data1.iterator();
			this.data2 = data2;
		}
		
		private void findNext() {
			if (!data.hasNext() && data2 != null) {
				data = data2.iterator();
				data2 = null;
			}
		}
		
		@Override
		public boolean hasNext() {
			return data.hasNext();
		}

		@Override
		public E next() {
			E ret = data.next();
			findNext();
			return ret;
		}
		
	}
	
	@Override
	public String toString() {
		return "center/radius=" + Arrays.toString(center) + "/" + radius + 
				" " + System.identityHashCode(this);
	}

	void checkNode(QStats s, QRNode<T> parent, int depth) {
		if (depth > s.maxDepth) {
			s.maxDepth = depth;
		}
		s.nNodes++;
		
		if (parent != null) {
			if (!QUtil.isNodeEnclosed(center, radius, parent.center, parent.radius*QUtil.EPS_MUL)) {
				//TODO?
				//throw new IllegalStateException();
			}
		}
		if (values != null) {
			for (int i = 0; i < values.size(); i++) {
				BoxEntry<T> e = values.get(i);
				if (!QUtil.fitsIntoNode(e.min(), e.max(), center, radius*QUtil.EPS_MUL)) {
					throw new IllegalStateException();
				}
				//TODO check that they overlap with the centerpoint or that subs==null
			}
		} 
		if (subs != null) {
			for (int i = 0; i < subs.size(); i++) {
				QRNode<T> n = subs.get(i);
				//TODO check pos
				if (n != null) {
					n.checkNode(s, this, depth+1);
				}
			}
		}
	}

	boolean hasValues() {
		return values != null;
	}

	boolean hasChildNodes() {
		return subs != null;
	}

	ArrayList<QRNode<T>> getChildNodes() {
		return subs;
	}
}
