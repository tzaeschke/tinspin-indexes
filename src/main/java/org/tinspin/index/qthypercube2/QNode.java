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
package org.tinspin.index.qthypercube2;

import java.util.Arrays;
import java.util.function.Predicate;

import org.tinspin.index.PointEntry;
import org.tinspin.index.qthypercube2.QuadTreeKD2.QStats;

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
	// null indicates that we have sub-node i.o. values
	private QEntry<T>[] values;
	private Object[] subs;
	private int nValues = 0;
	private boolean isLeaf;
	
	@SuppressWarnings("unchecked")
	QNode(double[] center, double radius) {
		this.center = center;
		this.radius = radius;
		this.values = new QEntry[2];
		this.isLeaf = true;
	}

	QNode(double[] center, double radius, QNode<T> subNode, int subNodePos) {
		this.center = center;
		this.radius = radius;
		this.values = null;
		this.subs = new Object[1 << center.length];
		subs[subNodePos] = subNode;
		this.isLeaf = false;
	}

	@SuppressWarnings("unused")
	QNode<T> tryPut(QEntry<T> e, int maxNodeSize, boolean enforceLeaf) {
		if (QuadTreeKD2.DEBUG && !QUtil.fitsIntoNode(e.point(), center, radius)) {
			throw new IllegalStateException("e=" + Arrays.toString(e.point()) + 
					" center/radius=" + Arrays.toString(center) + "/" + radius);
		}
		
		//traverse subs?
		if (!isLeaf()) {
			return getOrCreateSub(e, maxNodeSize, enforceLeaf);
		}
		
		//add if:
		//a) we have space
		//b) we have maxDepth
		//c) elements are equal (work only for n=1, avoids splitting
		//   in cases where splitting won't help. For n>1 the
		//   local limit is (temporarily) violated.
		if (nValues < maxNodeSize || enforceLeaf || areAllPointsIdentical(e)) {
			addValue(e, maxNodeSize);
			return null;
		}
		
		//split
		QEntry<T>[] vals = values;
		int nVal = nValues;
		clearValues();
		subs = new Object[1 << center.length];
		isLeaf = false;
		for (int i = 0; i < nVal; i++) {
			QEntry<T> e2 = vals[i]; 
			QNode<T> sub = getOrCreateSub(e2, maxNodeSize, enforceLeaf);
			while (sub != null) {
				//This may recurse if all entries fall 
				//into the same subnode
				sub = sub.tryPut(e2, maxNodeSize, false);
			}
		}
		return getOrCreateSub(e, maxNodeSize, enforceLeaf);
	}

	private boolean areAllPointsIdentical(QEntry<T> e) {
		//This discovers situation where a node overflows, but splitting won't help because all points are identical
		for (int i = 0; i < nValues; i++) {
			if (!e.isExact(values[i])) {
				return false;
			}
		}
		return true;
	}
	
	QEntry<T>[] getValues() {
		return values;
	}
	
	private void addValue(QEntry<T> e, int maxNodeSize) {
		//Allow overflow over max node size (for example for lots of identical values in node)
		int maxLen = nValues >= maxNodeSize ? nValues * 2 : maxNodeSize;
		if (nValues >= getValues().length) {
			values = Arrays.copyOf(getValues(), nValues * 3 > maxLen ? maxLen : nValues * 3); 
		}
		getValues()[nValues++] = e;
	}
	
	private void removeValue(int pos) {
		if (isLeaf) {
			if (pos < --nValues) {
				System.arraycopy(getValues(), pos+1, getValues(), pos, nValues-pos);
			}
		} else {
			nValues--;
			subs[pos] = null;
		}
	}
	
	private void clearValues() {
		values = null;
		nValues = 0;
	}
	
	@SuppressWarnings("unchecked")
	private QNode<T> getOrCreateSub(QEntry<T> e, int maxNodeSize, boolean enforceLeaf) {
		int pos = calcSubPosition(e.point());
		Object n = subs[pos];
		
		if (n instanceof QNode) {
			return (QNode<T>)n;
		}
		
		if (n == null) {
			subs[pos] = e;
			nValues++;
			return null;
		}

		QEntry<T> e2 = (QEntry<T>) n;
		nValues--;
		QNode<T> sub = createSubForEntry(pos);
		subs[pos] = sub;
		sub.tryPut(e2, maxNodeSize, enforceLeaf);
		return sub;
	}
	
	private QNode<T> createSubForEntry(int subNodePos) {
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
		return new QNode<>(centerSub, radiusSub);		
	}
	
	/**
	 * The subnode position has reverse ordering of the point's
	 * dimension ordering. Dimension 0 of a point is the highest
	 * ordered bit in the position.
	 * @param p point
	 * @return subnode position
	 */
	int calcSubPosition(double[] p) {
		int subNodePos = 0;
		for (int d = 0; d < center.length; d++) {
			subNodePos <<= 1;
			if (p[d] >= center[d]) {
				subNodePos |= 1;
			}
		}
		return subNodePos;
	}

	@SuppressWarnings("unchecked")
	QEntry<T> remove(QNode<T> parent, double[] key, int maxNodeSize, Predicate<PointEntry<T>> pred) {
		if (!isLeaf()) {
			int pos = calcSubPosition(key);
			Object o = subs[pos];
			if (o instanceof QNode) {
				return ((QNode<T>)o).remove(this, key, maxNodeSize, pred);
			} else if (o instanceof QEntry) {
				QEntry<T> e = (QEntry<T>) o;
				if (removeSub(parent, key, pos, e, maxNodeSize, pred)) {
					return e;
				}
			}
			return null;
		}
		
		for (int i = 0; i < nValues; i++) {
			QEntry<T> e = values[i];
			if (removeSub(parent, key, i, e, maxNodeSize, pred)) {
				return e;
			}
		}
		return null;
	}

	private boolean removeSub(
			QNode<T> parent, double[] key, int pos, QEntry<T> e, int maxNodeSize, Predicate<PointEntry<T>> pred) {
		if (QUtil.isPointEqual(e.point(), key) && pred.test(e)) {
			removeValue(pos);
			//TODO provide threshold for re-insert
			//i.e. do not always merge.
			if (parent != null) {
				parent.checkAndMergeLeafNodes(maxNodeSize);
			}
			return true;
		}
		return false;
	}
	
	@SuppressWarnings("unchecked")
	QEntry<T> update(QNode<T> parent, double[] keyOld, double[] keyNew, int maxNodeSize,
			boolean[] requiresReinsert, int currentDepth, int maxDepth, Predicate<PointEntry<T>> pred) {
		if (!isLeaf()) {
			int pos = calcSubPosition(keyOld);
			Object e = subs[pos];
			if (e == null) {
				return null;
			}
			if (e instanceof QNode) {
				QNode<T> sub = (QNode<T>) e;
				QEntry<T> ret = sub.update(this, keyOld, keyNew, maxNodeSize, requiresReinsert,
						currentDepth+1, maxDepth, pred);
				if (ret != null && requiresReinsert[0] && 
						QUtil.fitsIntoNode(ret.point(), center, radius/QUtil.EPS_MUL)) {
					requiresReinsert[0] = false;
					QNode<T> r = this;
					while (r != null) {
						r = r.tryPut(ret, maxNodeSize, currentDepth++ > maxDepth);
					}
				}
				return ret;
			}
			// Entry
			QEntry<T> qe = (QEntry<T>) e;
			if (QUtil.isPointEqual(qe.point(), keyOld) && pred.test(qe)) {
				removeValue(pos);
				qe.setKey(keyNew);
				if (QUtil.fitsIntoNode(keyNew, center, radius/QUtil.EPS_MUL)) {
					// reinsert locally;
					QNode<T> r = this;
					while (r != null) {
						r = r.tryPut(qe, maxNodeSize, currentDepth++ > maxDepth);
					}
					requiresReinsert[0] = false;
				} else {
					requiresReinsert[0] = true;
					if (parent != null) {
						parent.checkAndMergeLeafNodes(maxNodeSize);
					}
				}
				return qe;
			}
			throw new IllegalStateException();
		}
		
		for (int i = 0; i < nValues; i++) {
			QEntry<T> e = getValues()[i];
			if (QUtil.isPointEqual(e.point(), keyOld) && pred.test(e)) {
				removeValue(i);
				e.setKey(keyNew);
				updateSub(keyNew, e, parent, maxNodeSize, requiresReinsert);
				return e;
			}
		}
		requiresReinsert[0] = false;
		return null;
	}

	private void updateSub(double[] keyNew, QEntry<T> e, QNode<T> parent, int maxNodeSize, boolean[] requiresReinsert) {
		if (QUtil.fitsIntoNode(keyNew, center, radius/QUtil.EPS_MUL)) {
			// reinsert locally
			addValue(e, maxNodeSize);
			requiresReinsert[0] = false;
		} else {
			requiresReinsert[0] = true;
			//TODO provide threshold for re-insert
			//i.e. do not always merge.
			if (parent != null) {
				parent.checkAndMergeLeafNodes(maxNodeSize);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void checkAndMergeLeafNodes(int maxNodeSize) {
		//check: We start with including all local values: nValues
		int nTotal = nValues;
		for (int i = 0; i < subs.length; i++) {
			Object e = subs[i];
			if (e instanceof QNode) {
				QNode<T> sub = (QNode<T>) e;
				if (!sub.isLeaf()) {
					//can't merge directory nodes.
					//Merge only makes sense if we switch to list-mode, for which we don;t support subnodes!
					return;
				}
				nTotal += sub.getValueCount();
				if (nTotal > maxNodeSize) {
					//too many children
					return;
				}
			}
		}
		
		//okay, let's merge
		values = new QEntry[nTotal];
		nValues = 0;
		for (int i = 0; i < subs.length; i++) {
			Object e = subs[i];
			if (e instanceof QNode) {
				QNode<T> sub = (QNode<T>) e; 
				for (int j = 0; j < sub.nValues; j++) {
					values[nValues++] = sub.values[j];
				}
			} else if (e instanceof QEntry) {
				values[nValues++] = (QEntry<T>) e;
			}
		}
		subs = null;
		isLeaf = true;
	}

	double[] getCenter() {
		return center;
	}

	double getRadius() {
		return radius;
	}

	@SuppressWarnings("unchecked")
	QEntry<T> getExact(double[] key, Predicate<PointEntry<T>> pred) {
		if (!isLeaf()) {
			int pos = calcSubPosition(key);
			Object sub = subs[pos];
			if (sub instanceof QNode) {
				return ((QNode<T>)sub).getExact(key, pred);
			} else  if (sub != null) {
				QEntry<T> e = (QEntry<T>) sub;
				if (QUtil.isPointEqual(e.point(), key)) {
					return e;
				}
			}
			return null;
		}
		
		for (int i = 0; i < nValues; i++) {
			QEntry<T> e = values[i];
			if (QUtil.isPointEqual(e.point(), key) && pred.test(e)) {
				return e;
			}
		}
		return null;
	}

	Object[] getEntries() {
		return isLeaf ? values : subs;
	}


	@Override
	public String toString() {
		return "center/radius=" + Arrays.toString(center) + "/" + radius + 
				" " + System.identityHashCode(this);
	}

	@SuppressWarnings("unchecked")
	void checkNode(QStats s, QNode<T> parent, int depth) {
		if (depth > s.maxDepth) {
			s.maxDepth = depth;
		}
		s.nNodes++;
		
		if (parent != null) {
			if (!QUtil.isNodeEnclosed(center, radius, parent.center, parent.radius*QUtil.EPS_MUL)) {
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
			s.nEntries += nValues;
			s.histoValues[nValues]++;
			for (int i = 0; i < nValues; i++) {
				QEntry<T> e = values[i];
				checkEntry(e);
			}
			if (subs != null) {
				throw new IllegalStateException();
			}
		} else {
			s.nInner++;
			if (subs.length != 1L<<s.dims) {
				throw new IllegalStateException();
			}
			int nSubs = 0;
			for (int i = 0; i < subs.length; i++) {
				Object n = subs[i];
				//TODO check pos
				if (n instanceof QNode) {
					nSubs++;
					((QNode<T>)n).checkNode(s, this, depth+1);
				} else if (n != null) {
					s.nEntries++;
					checkEntry(n);
				}
			}
			s.histo(nSubs);
		}
	}

	@SuppressWarnings("unchecked")
	private void checkEntry(Object o) {
		QEntry<T> e = (QEntry<T>) o;
		if (!QUtil.fitsIntoNode(e.point(), center, radius*QUtil.EPS_MUL)) {
			System.out.println("Node: " + radius + " " + Arrays.toString(center));
			System.out.println("Child: " + Arrays.toString(e.point()));
			for (int d = 0; d < center.length; d++) {
//				if ((centerOuter[d]+radiusOuter) / (centerEnclosed[d]+radiusEnclosed) < 0.9999999 || 
//						(centerOuter[d]-radiusOuter) / (centerEnclosed[d]-radiusEnclosed) > 1.0000001) {
//					return false;
//				}
				System.out.println("min/max for " + d);
				System.out.println("min: " + (center[d]-radius) + " vs " + (e.point()[d]));
				System.out.println("r=" + (center[d]-radius) / (e.point()[d]));
				System.out.println("max: " + (center[d]+radius) + " vs " + (e.point()[d])); 
				System.out.println("r=" + (center[d]+radius) / (e.point()[d])); 
			}
			throw new IllegalStateException();
		}
		
	}
	
	boolean isLeaf() {
		return isLeaf;
	}

	public int getValueCount() {
		return nValues;
	}


	void adjustRadius(double radius) {
		if (!isLeaf()) {
			throw new IllegalStateException();
		}
		this.radius = radius;
	}
}
