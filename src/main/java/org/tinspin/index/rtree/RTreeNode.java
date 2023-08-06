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
package org.tinspin.index.rtree;

import java.util.ArrayList;
import java.util.Arrays;

import static org.tinspin.index.Index.*;

abstract class RTreeNode<T> extends RTreeEntry<T> {

	private RTreeNodeDir<T> parent;

	RTreeNode(int dim) {
		super(new double[dim], new double[dim], null);
	}

	abstract void addEntry(RTreeEntry<T> e);

	abstract ArrayList<RTreeEntry<T>> getEntries();

	/**
	 * Calculates the overlap of this node with 'othernode' if this node would be 
	 * enlarged to contain 'enlargement'.
	 * @param enlargement
	 * @param otherNode
	 * @return overlap of enlarged nodes.
	 */
	double calcOverlapEnlarged(RTreeEntry<T> enlargement, RTreeNode<T> otherNode) {
		double area = 1;
		for (int i = 0; i < min().length; i++) {
			double d = Math.min(Math.max(max()[i], enlargement.max()[i]), otherNode.max()[i])
					- Math.max(Math.min(min()[i], enlargement.min()[i]), otherNode.min()[i]);
			if (d <= 0) {
				return 0;
			}
			area *= d;
		}
		return area;
	}

	public double calcAreaEnlarged(RTreeEntry<T> e) {
		double area = 1;
		for (int i = 0; i < min().length; i++) {
			double d = Math.max(max()[i], e.max()[i])
					- Math.min(min()[i], e.min()[i]);
			area *= d;
		}
		return area;
	}


	protected void setMBB(RTreeEntry<T> e) {
		for (int i = 0; i < min().length; i++) {
			min()[i] = e.min()[i];
			max()[i] = e.max()[i];
		}
	}


	/**
	 * Extends the MBB to ensure it covers the new entry.
	 * @param e new entry
	 */
	protected void extendMBB(RTreeEntry<T> e) {
		for (int i = 0; i < min().length; i++) {
			if (min()[i] > e.min()[i]) {
				min()[i] = e.min()[i];
			}
			if (max()[i] < e.max()[i]) {
				max()[i] = e.max()[i];
			}
		}
	}

	/**
	 * Recalculates the MBB for all elements. This is for example 
	 * required after removing elements.
	 * @return 'true' iff the MBB has changed
	 */
	public boolean recalcMBB() {
		double[] minOld = min().clone();
		double[] maxOld = max().clone();
		resetMBB();
		ArrayList<RTreeEntry<T>> entries = getEntries();
		for (int i = 0; i < entries.size(); i++) {
			BoxEntry<T> e = entries.get(i);
			for (int d = 0; d < min().length; d++) {
				if (min()[d] > e.min()[d]) {
					min()[d] = e.min()[d];
				}
				if (max()[d] < e.max()[d]) {
					max()[d] = e.max()[d];
				}
			}
		}
		return !Arrays.equals(min(), minOld) || !Arrays.equals(max(), maxOld);
	}

	protected void resetMBB() {
		Arrays.fill(min(), Double.POSITIVE_INFINITY);
		Arrays.fill(max(), Double.NEGATIVE_INFINITY);
	}

	public abstract void clear();

	public void setParent(RTreeNodeDir<T> parent) {
		this.parent = parent;
	}

	public RTreeNodeDir<T> getParent() {
		return parent;
	}
	
	public void extendParentMBB() {
		RTreeNodeDir<T> current = this.parent;
		// TODO ?
		//stop adjusting parent if we get a root or if there was no change
		// TODO remove this method? Difference to recalcParentMBB() ?
		while (current != null) {
			current.extendMBB(this);
			current = current.getParent();
		}
	}

	public void recalcParentMBB() {
		RTreeNodeDir<T> current = this.parent;
		//stop adjusting parent if we get a root or if there was no change
		while (current != null && current.recalcMBB()) {
			current = current.getParent();
		}
	}

	public void recalcRecursiveMBB() {
		if (recalcMBB()) {
			recalcParentMBB();
		}
	}

	public abstract boolean hasSpace();

	public abstract boolean isUnderfull();

	public void removeEntry(int i) {
		getEntries().remove(i);
		recalcRecursiveMBB();
	}
}