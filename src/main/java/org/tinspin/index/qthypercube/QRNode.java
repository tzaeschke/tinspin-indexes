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
import java.util.Arrays;
import java.util.function.Predicate;

import org.tinspin.index.qthypercube.QuadTreeKD.QStats;

import static org.tinspin.index.Index.*;

/**
 * Node class for the quadtree.
 * 
 * @author ztilmann
 *
 * @param <T> Value type.
 */
public class QRNode<T> {

	private static final int OVERLAP_WITH_CENTER = -1;
	private final double[] center;
	private final double radius;
	//null indicates that we have sub-nopde i.o. values
	private ArrayList<BoxEntry<T>> values;
	private QRNode<T>[] subs;
	
	QRNode(double[] center, double radius) {
		this.center = center;
		this.radius = radius;
		this.values = new ArrayList<>(); 
	}

	@SuppressWarnings("unchecked")
	QRNode(double[] center, double radius, QRNode<T> subNode, int subNodePos) {
		this.center = center;
		this.radius = radius;
		this.values = null;
		this.subs = new QRNode[1 << center.length];
		subs[subNodePos] = subNode;
	}

	@SuppressWarnings({ "unchecked", "unused" })
	QRNode<T> tryPut(BoxEntry<T> e, int maxNodeSize, boolean enforceLeaf) {
		if (QuadTreeKD.DEBUG && !QUtil.fitsIntoNode(e.min(), e.max(), center, radius)) {
			throw new IllegalStateException("e=" + e + 
					" center/radius=" + Arrays.toString(center) + "/" + radius);
		}
		
		//traverse subs?
		int pos = calcSubPositionR(e.min(), e.max());
		if (subs != null && pos != OVERLAP_WITH_CENTER) {
			return getOrCreateSubR(pos);
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
				QUtil.isRectEqual(e, values.get(0)) || subs != null) {
			values.add(e);
			return null;
		}
		
		//split
		ArrayList<BoxEntry<T>> vals = values;
		vals.add(e);
		values = null;
		subs = new QRNode[1 << center.length];
		for (int i = 0; i < vals.size(); i++) {
			BoxEntry<T> e2 = vals.get(i);
			int pos2 = calcSubPositionR(e2.min(), e2.max());
			if (pos2 == OVERLAP_WITH_CENTER) {
				if (values == null) {
					values = new ArrayList<>();
				}
				values.add(e2);
				continue;
			}
			QRNode<T> sub = getOrCreateSubR(pos2);
			while (sub != null) {
				//This may recurse if all entries fall 
				//into the same subnode
				sub = sub.tryPut(e2, maxNodeSize, false);
			}
		}
		return null;
	}

	private QRNode<T> getOrCreateSubR(int pos) {
		QRNode<T> n = subs[pos];
		if (n == null) {
			n = createSubForEntry(pos);
			subs[pos] = n;
		}
		return n;
	}
	
	private QRNode<T> createSubForEntry(int subNodePos) {
		double[] centerSub = new double[center.length];
		int mask = 1<<center.length;
		//This ensures that the subsnodes completely cover the area of
		//the parent node.
		double radiusSub = radius/2.0;
		for (int d = 0; d < center.length; d++) {
			mask >>= 1;
			if ((subNodePos & mask) > 0) {
				centerSub[d] = center[d]+radiusSub;
			} else {
				centerSub[d] = center[d]-radiusSub; 
			}
		}
		return new QRNode<>(centerSub, radiusSub);		
	}
	
	/**
	 * The subnode position has reverse ordering of the point's
	 * dimension ordering. Dimension 0 of a point is the highest
	 * ordered bit in the position.
	 * @param pMin point
	 * @param pMax point
	 * @return subnode position
	 */
	private int calcSubPositionR(double[] pMin, double[] pMax) {
		int subNodePos = 0;
		for (int d = 0; d < center.length; d++) {
			subNodePos <<= 1;
			if (pMin[d] >= center[d]) {
				subNodePos |= 1;
			} else if (pMax[d] >= center[d]) {
				//overlap with center point
				return OVERLAP_WITH_CENTER;
			}
		}
		return subNodePos;
	}

	BoxEntry<T> remove(QRNode<T> parent, double[] keyL, double[] keyU, int maxNodeSize, Predicate<BoxEntry<T>> pred) {
		if (subs != null) {
			int pos = calcSubPositionR(keyL, keyU);
			if (pos != OVERLAP_WITH_CENTER) {
				QRNode<T> sub = subs[pos];
				if (sub != null) {
					return sub.remove(this, keyL, keyU, maxNodeSize, pred);
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
			if (QUtil.isRectEqual(e, keyL, keyU) && pred.test(e)) {
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

	@SuppressWarnings("unchecked")
	BoxEntry<T> update(QRNode<T> parent, double[] keyOldL, double[] keyOldU,
			double[] keyNewL, double[] keyNewU, int maxNodeSize,
			boolean[] requiresReinsert, int currentDepth, int maxDepth, Predicate<T> pred) {
		if (subs != null) {
			int pos = calcSubPositionR(keyOldL, keyOldU);
			if (pos != OVERLAP_WITH_CENTER) {
				QRNode<T> sub = subs[pos];
				if (sub == null) {
					return null;
				}
				BoxEntry<T> ret = sub.update(this, keyOldL, keyOldU, keyNewL, keyNewU,
						maxNodeSize, requiresReinsert, currentDepth+1, maxDepth, pred);
				if (ret != null && requiresReinsert[0] && 
						QUtil.fitsIntoNode(ret.min(), ret.max(), center, radius)) {
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
					int pos = calcSubPositionR(keyNewL, keyNewU);
					if (pos == OVERLAP_WITH_CENTER) {
						// reinsert locally
						values.add(e);
					} else {
						QRNode<T> r;
						//we try to use subnode directly, if there is one.
						if (subs == null || subs[pos] == null) {
							//create node locally for insert
							r = this;
						} else {
							r = subs[pos];
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
		for (int i = 0; i < subs.length; i++) {
			if (subs[i] != null) {
				if (subs[i].subs != null) {
					//can't merge directory nodes.
					return; 
				}
				if (subs[i].values != null) {
					nTotal += subs[i].values.size();
				}					
				if (nTotal > maxNodeSize) {
					//too many children
					return;
				}
			}
		}
		
		//okay, let's merge
		if (values == null) {
			values = new ArrayList<>();
		}
		for (int i = 0; i < subs.length; i++) {
			if (subs[i] != null) {
				values.addAll(subs[i].values);
			}
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
			int pos = calcSubPositionR(keyL, keyU);
			if (pos != OVERLAP_WITH_CENTER) {
				QRNode<T> sub = subs[pos];
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
			for (int i = 0; i < subs.length; i++) {
				QRNode<T> n = subs[i];
				//TODO check pos
				if (n != null) {
					n.checkNode(s, this, depth+1);
				}
			}
		}
	}

	public boolean hasValues() {
		return values != null;
	}

	public boolean hasChildNodes() {
		return subs != null;
	}

	QRNode<T>[] getChildNodes() {
		return subs;
	}
}
