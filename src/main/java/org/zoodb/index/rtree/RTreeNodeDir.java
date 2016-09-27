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
package org.zoodb.index.rtree;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Directory node
 * 
 * @param <T>
 */
class RTreeNodeDir<T> extends RTreeNode<T> {

	private ArrayList<RTreeNode<T>> children;
	
	public RTreeNodeDir(int dim) {
		super(dim);
		children = new ArrayList<>();
	}

	@Override
	public void addEntry(Entry<T> e) {
		RTreeNode<T> node = (RTreeNode<T>) e;
		children.add(node);
		node.setParent(this);
		if (children.size() > 1) {
			extendMBB(e);
		} else {
			setMBB(e);
		}
		if (RTree.DEBUG && false) {
			for (int i = 0; i < children.size()-1; i++) {
				if (Entry.checkOverlap(e.lower(), e.upper(), children.get(i))) {
					System.out.println("Overlap 1: " + e);
					System.out.println("Overlap 2: " + children.get(i));
					System.out.println("Overlap 1 parent : " + ((RTreeNode)e).getParent());
					System.out.println("Overlap 2 parent : " + ((RTreeNode)children.get(i)).getParent());
					throw new IllegalStateException();
				}
			}
		}
	}

	public void removeChildByIdentity(Entry<T> e) {
		for (int i = 0; i < children.size(); i++) {
			if (children.get(i) == e) {
				children.remove(i);
				recalcMBB();
				recalcParentMBB();
				return;
			}
		}
		throw new IllegalStateException();
	}

	public boolean containsLeafNodes() {
		return children.get(0) instanceof RTreeNodeLeaf;
	}

	public ArrayList<RTreeNode<T>> getChildren() {
		return children;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public ArrayList<Entry<T>> getEntries() {
		return (ArrayList)children;
	}

	@Override
	public void clear() {
		children.clear();
		//TODO this may not be necessary
		resetMBB();
	}

	@Override
	public boolean hasSpace() {
		return children.size() < RTree.NODE_MAX_DIR;
	}
	
	@Override
	public String toString() {
		return "NodeDir;n=" + children.size() + 
				";min/max=" + Arrays.toString(lower()) + "/" + Arrays.toString(upper()) +
				";id=" + System.identityHashCode(this);
	}

	@Override
	public boolean isUnderfull() {
		return children.size() < RTree.NODE_MIN_DIR;
	}
}