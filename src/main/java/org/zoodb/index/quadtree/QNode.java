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
package org.zoodb.index.quadtree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import org.zoodb.index.quadtree.QuadTreeKD.QStats;

/**
 * Node class for the quadtree.
 * 
 * @author ztilmann
 *
 * @param <T>
 */
public class QNode<T> {

	private double[] min;
	private double[] max;
	//null indicates that we have sub-nopde i.o. values
	private ArrayList<QEntry<T>> values;
	private QNode<T>[] subs;
	
	QNode(double[] min, double[] max) {
		this.min = min;
		this.max = max;
		this.values = new ArrayList<>(2); 
	}

	@SuppressWarnings("unchecked")
	QNode(double[] min, double[] max, QNode<T> subNode, int subNodePos) {
		this.min = min;
		this.max = max;
		this.values = null;
		this.subs = new QNode[1 << min.length];
		subs[subNodePos] = subNode;
	}

	@SuppressWarnings("unchecked")
	QNode<T> tryPut(QEntry<T> e, int maxNodeSize, boolean enforceLeaf) {
		if (QuadTreeKD.DEBUG && !e.enclosedBy(min, max)) {
			throw new IllegalStateException("e=" + Arrays.toString(e.getPoint()) + 
					" min/max=" + Arrays.toString(min) + Arrays.toString(max));
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
				e.isExact(values.get(0))) {
			values.add(e);
			return null;
		}
		
		//split
		ArrayList<QEntry<T>> vals = values;
		values = null;
		subs = new QNode[1 << min.length];
		for (int i = 0; i < vals.size(); i++) {
			QEntry<T> e2 = vals.get(i); 
			QNode<T> sub = getOrCreateSub(e2);
			while (sub != null) {
				//This may recurse if all entries fall 
				//into the same subnode
				sub = (QNode<T>) sub.tryPut(e2, maxNodeSize, false);
			}
		}
		return getOrCreateSub(e);
	}

	private QNode<T> getOrCreateSub(QEntry<T> e) {
		int pos = calcSubPosition(e.getPoint());
		QNode<T> n = subs[pos];
		if (n == null) {
			n = createSubForEntry(pos);
			subs[pos] = n;
		}
		return n;
	}
	
	private QNode<T> createSubForEntry(int subNodePos) {
		double[] minSub = new double[min.length];
		double[] maxSub = new double[max.length];
		double len = (max[0]-min[0])/2;
		int mask = 1<<min.length;
		for (int d = 0; d < min.length; d++) {
			mask >>= 1;
			if ((subNodePos & mask) > 0) {
				minSub[d] = min[d]+len;
				maxSub[d] = max[d];
			} else {
				minSub[d] = min[d];
				maxSub[d] = max[d]-len; 
			}
		}
		return new QNode<>(minSub, maxSub);		
	}
	
	/**
	 * The subnode position has reverse ordering of the point's
	 * dimension ordering. Dimension 0 of a point is the highest
	 * ordered bit in the position.
	 * @param p point
	 * @return subnode position
	 */
	private int calcSubPosition(double[] p) {
		int subNodePos = 0;
		double len = (max[0]-min[0])/2;
		for (int d = 0; d < min.length; d++) {
			subNodePos <<= 1;
			if (p[d] >= min[d]+len) {
				subNodePos |= 1;
			}
		}
		return subNodePos;
	}

	QEntry<T> remove(QNode<T> parent, double[] key, int maxNodeSize) {
		if (values == null) {
			int pos = calcSubPosition(key);
			QNode<T> sub = subs[pos];
			if (sub != null) {
				return sub.remove(this, key, maxNodeSize);
			}
			return null;
		}
		
		for (int i = 0; i < values.size(); i++) {
			QEntry<T> e = values.get(i);
			if (QUtil.isPointEqual(e.getPoint(), key)) {
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
	QEntry<T> update(QNode<T> parent, double[] keyOld, double[] keyNew, int maxNodeSize,
			boolean[] requiresReinsert, int currentDepth, int maxDepth) {
		if (values == null) {
			int pos = calcSubPosition(keyOld);
			QNode<T> sub = subs[pos];
			if (sub == null) {
				return null;
			}
			QEntry<T> ret = sub.update(this, keyOld, keyNew, maxNodeSize, requiresReinsert,
					currentDepth+1, maxDepth);
			if (ret != null && requiresReinsert[0] && 
					QUtil.isPointEnclosed(ret.getPoint(), min, max)) {
				requiresReinsert[0] = false;
				Object r = this;
				while (r instanceof QNode) {
					r = ((QNode<T>)r).tryPut(ret, maxNodeSize, currentDepth++ > maxDepth);
				}
			}
			return ret;
		}
		
		for (int i = 0; i < values.size(); i++) {
			QEntry<T> e = values.get(i);
			if (QUtil.isPointEqual(e.getPoint(), keyOld)) {
				values.remove(i);
				e.setKey(keyNew);
				if (QUtil.isPointEnclosed(keyNew, min, max)) {
					//reinsert locally;
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
		//check
		int nTotal = 0;
		for (int i = 0; i < subs.length; i++) {
			if (subs[i] != null) {
				if (subs[i].values == null) {
					//can't merge directory nodes.
					return; 
				}
				nTotal += subs[i].values.size();
				if (nTotal > maxNodeSize) {
					//too many children
					return;
				}
			}
		}
		
		//okay, let's merge
		values = new ArrayList<>(nTotal);
		for (int i = 0; i < subs.length; i++) {
			if (subs[i] != null) {
				values.addAll(subs[i].values);
			}
		}
		subs = null;
	}

	double getSideLength() {
		return max[0]-min[0];
	}

	double[] getMin() {
		return min;
	}

	double[] getMax() {
		return max;
	}

	QEntry<T> getExact(double[] key) {
		if (values == null) {
			int pos = calcSubPosition(key);
			QNode<T> sub = subs[pos];
			if (sub != null) {
				return sub.getExact(key);
			}
			return null;
		}
		
		for (int i = 0; i < values.size(); i++) {
			QEntry<T> e = values.get(i);
			if (QUtil.isPointEqual(e.getPoint(), key)) {
				return e;
			}
		}
		return null;
	}

	ArrayList<QEntry<T>> getEntries() {
		return values;
	}

	Iterator<?> getChildIterator() {
		if (values != null) {
			return values.iterator();
		}
		return new ArrayIterator<>(subs);
	}

	private static class ArrayIterator<E> implements Iterator<E> {

		private final E[] data;
		private int pos;
		
		ArrayIterator(E[] data) {
			this.data = data;
			this.pos = 0;
			findNext();
		}
		
		private void findNext() {
			while (pos < data.length && data[pos] == null) {
				pos++;
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
		return "min/max=" + Arrays.toString(min) + Arrays.toString(max) + 
				" " + System.identityHashCode(this);
	}

	void checkNode(QStats s, QNode<T> parent, int depth) {
		if (depth > s.maxDepth) {
			s.maxDepth = depth;
		}
		s.nNodes++;
		
		if (parent != null) {
			if (!QUtil.isRectEnclosed(min, max, parent.min, parent.max)) {
				throw new IllegalStateException();
			}
		}
		if (values != null) {
			for (int i = 0; i < values.size(); i++) {
				QEntry<T> e = values.get(i);
				if (!QUtil.isPointEnclosed(e.getPoint(), min, max)) {
					throw new IllegalStateException();
				}
			}
			if (subs != null) {
				throw new IllegalStateException();
			}
		} else {
			for (int i = 0; i < subs.length; i++) {
				QNode<T> n = subs[i];
				//TODO check pos
				if (n != null) {
					n.checkNode(s, this, depth+1);
				}
			}
		}
	}

	boolean isLeaf() {
		return values != null;
	}

	QNode<T>[] getChildNodes() {
		return subs;
	}
}
