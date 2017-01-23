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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.tinspin.index.quadtree.QuadTreeKD.QStats;

/**
 * Node class for the quadtree.
 * 
 * @author ztilmann
 *
 * @param <T>
 */
public class QRNode<T> {

	private static final int OVERLAP_WITH_CENTER = -1;
	private double[] center;
	private double radius;
	//null indicates that we have sub-nopde i.o. values
	private ArrayList<QREntry<T>> values;
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
	QRNode<T> tryPut(QREntry<T> e, int maxNodeSize, boolean enforceLeaf) {
		if (QuadTreeKD.DEBUG && !e.enclosedBy(center, radius)) {
			throw new IllegalStateException("e=" + e + 
					" center/radius=" + Arrays.toString(center) + "/" + radius);
		}
		
		//traverse subs?
		int pos = calcSubPositionR(e.lower(), e.upper());
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
				e.isExact(values.get(0)) || subs != null) {
			values.add(e);
			return null;
		}
		
		//split
		ArrayList<QREntry<T>> vals = values;
		values = null;
		subs = new QRNode[1 << center.length];
		for (int i = 0; i < vals.size(); i++) {
			QREntry<T> e2 = vals.get(i); 
			int pos2 = calcSubPositionR(e2.lower(), e2.upper());
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
				sub = (QRNode<T>) sub.tryPut(e2, maxNodeSize, false);
			}
		}
		if (pos == OVERLAP_WITH_CENTER) {
			if (values == null) {
				values = new ArrayList<>();
			}
			values.add(e);
			return null;
		}
		return getOrCreateSubR(pos);
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
	 * @param p point
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

	QREntry<T> remove(QRNode<T> parent, double[] keyL, double[] keyU, int maxNodeSize) {
		if (subs != null) {
			int pos = calcSubPositionR(keyL, keyU);
			if (pos != OVERLAP_WITH_CENTER) {
				QRNode<T> sub = subs[pos];
				if (sub != null) {
					return sub.remove(this, keyL, keyU, maxNodeSize);
				}
				return null;
			}
		}
		
		//now check local data
		if (values == null) {
			return null;
		}
		for (int i = 0; i < values.size(); i++) {
			QREntry<T> e = values.get(i);
			if (QUtil.isRectEqual(e, keyL, keyU)) {
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
	QREntry<T> update(QRNode<T> parent, double[] keyOldL, double[] keyOldU, 
			double[] keyNewL, double[] keyNewU, int maxNodeSize,
			boolean[] requiresReinsert, int currentDepth, int maxDepth) {
		if (subs != null) {
			int pos = calcSubPositionR(keyOldL, keyOldU);
			if (pos != OVERLAP_WITH_CENTER) {
				QRNode<T> sub = subs[pos];
				if (sub == null) {
					return null;
				}
				QREntry<T> ret = sub.update(this, keyOldL, keyOldU, keyNewL, keyNewU, 
						maxNodeSize, requiresReinsert, currentDepth+1, maxDepth);
				//Divide by EPS to ensure that we do not reinsert to low
				if (ret != null && requiresReinsert[0] && 
						QUtil.isRectEnclosed(ret.lower(), ret.upper(), 
								center, radius/QUtil.EPS_MUL)) {
					requiresReinsert[0] = false;
					Object r = this;
					while (r instanceof QRNode) {
						r = ((QRNode<T>)r).tryPut(ret, maxNodeSize, currentDepth++ > maxDepth);
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
			QREntry<T> e = values.get(i);
			if (QUtil.isRectEqual(e, keyOldL, keyOldU)) {
				values.remove(i);
				e.setKey(keyNewL, keyNewU);
				//Divide by EPS to ensure that we do not reinsert to low
				if (QUtil.isRectEnclosed(keyNewL, keyNewU, center, radius/QUtil.EPS_MUL)) {
					requiresReinsert[0] = false;
					int pos = calcSubPositionR(keyNewL, keyNewU);
					if (pos == OVERLAP_WITH_CENTER) {
						//reinsert locally;
						values.add(e);
					} else {
						Object r = this;
						while (r instanceof QRNode) {
							r = ((QRNode<T>)r).tryPut(e, maxNodeSize, currentDepth++ > maxDepth);
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

	QREntry<T> getExact(double[] keyL, double[] keyU) {
		if (subs != null) {
			int pos = calcSubPositionR(keyL, keyU);
			if (pos != OVERLAP_WITH_CENTER) {
				QRNode<T> sub = subs[pos];
				if (sub != null) {
					return sub.getExact(keyL, keyU);
				}
				return null;
			}
		}
		
		if (values == null) {
			return null;
		}
		
		for (int i = 0; i < values.size(); i++) {
			QREntry<T> e = values.get(i);
			if (QUtil.isRectEqual(e, keyL, keyU)) {
				return e;
			}
		}
		return null;
	}

	ArrayList<QREntry<T>> getEntries() {
		return values;
	}

	Iterator<?> getChildIterator() {
		if (subs == null) {
			return values.iterator();
		}
		return new ArrayIterator<>(subs, values != null ? values.toArray() : null);
	}

	private static class ArrayIterator<E> implements Iterator<E> {

		private E[] data;
		private E[] data2;
		private int pos;
		
		ArrayIterator(E[] data1, E[] data2) {
			this.data = data1;
			this.data2 = data2;
			this.pos = 0;
			findNext();
		}
		
		private void findNext() {
			while (pos < data.length && data[pos] == null) {
				pos++;
			}
			if (pos >= data.length && data2 != null) {
				data = data2;
				data2 = null;
				pos = 0;
				findNext();
			}
		}
		
		@Override
		public boolean hasNext() {
			return pos < data.length;
		}

		@Override
		public E next() {
			E ret = data[pos++];
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
			if (!QUtil.isRectEnclosed(center, radius, parent.center, parent.radius*QUtil.EPS_MUL)) {
				//TODO?
				//throw new IllegalStateException();
			}
		}
		if (values != null) {
			for (int i = 0; i < values.size(); i++) {
				QREntry<T> e = values.get(i);
				if (!QUtil.isRectEnclosed(e.lower(), e.upper(), center, radius*QUtil.EPS_MUL)) {
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

	QRNode<T>[] getChildNodes() {
		return subs;
	}
}
