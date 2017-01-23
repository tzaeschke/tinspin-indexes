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

class RTreeNodeLeaf<T> extends RTreeNode<T> {

	private ArrayList<Entry<T>> entries;
	
	public RTreeNodeLeaf(int dim) {
		super(dim);
		entries = new ArrayList<>();
	}

	@Override
	public void addEntry(Entry<T> e) {
		entries.add(e);
		if (entries.size() > 1) {
			extendMBB(e);
		} else {
			setMBB(e);
		}
	}

	@Override
	public ArrayList<Entry<T>> getEntries() {
		return entries;
	}

	@Override
	public void clear() {
		entries.clear();
		resetMBB();
	}

	@Override
	public boolean hasSpace() {
		return entries.size() < RTree.NODE_MAX_DATA;
	}
	
	@Override
	public String toString() {
		double[] len = new double[min.length];
		Arrays.setAll(len, (i)->(max[i]-min[i]));
		return "NodeData;n=" + entries.size() + 
				";min/max=" + Arrays.toString(lower()) + "/" + Arrays.toString(upper()) +
				";lengths=" + Arrays.toString(len) +
				";id=" + System.identityHashCode(this);
	}

	@Override
	public boolean isUnderfull() {
		return entries.size() < RTree.NODE_MIN_DATA;
	}
}