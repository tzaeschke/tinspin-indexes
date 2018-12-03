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
package org.tinspin.index.covertree;

import java.util.ArrayList;


public class Node<T> {

	private Point<T> p;
	private ArrayList<Node<T>> children;
	private int level;
	private double distToParent;
	private double maxDist = -1;
	
	Node(Point<T> p, int level) {
		this.p = p;
		this.level = level;
	}

	Node<T> initLevel(int level) {
		this.level = level;
		this.maxDist = 0;
		return this;
	}

	Point<T> point() {
		return p;
	}
	
	void setDistanceToParent(double d) {
		this.distToParent = d;
	}
	
	double getDistanceToParent() {
		return this.distToParent;
	}
	
	void addChild(Node<T> node, double distToParent) {
		if (node.level + 1 != level) {
			throw new IllegalStateException("level" + level + '/' + node.level);
		}
		if (children == null) {
			children = new ArrayList<>();
		}
		node.distToParent = distToParent;
		if (node.hasChildren()) {
			if (node.maxDist == -1 || node.distToParent + node.maxDist > maxDist) {
				maxDist = -1; //Needs recalc
			}
		} else if (maxDist != -1 && distToParent > maxDist) {
			maxDist = distToParent;
		}
		children.add(node);
	}

	void replaceChild(int i, Node<T> qNew) {
		children.set(i, qNew);
	}

	ArrayList<Node<T>> getChildren() {
		return children;
	}

	ArrayList<Node<T>> getOrCreateChildren() {
		if (children == null) {
			children = new ArrayList<>();
		}
		return children;
	}

	boolean hasChildren() {
		return children != null && !children.isEmpty(); 
	}
	
	Node<T> removeAnyLeaf() {
		//TODO closest?
		Node<T> any = children.get(0);
		if (any.hasChildren()) {
			//TODO only invalidate if maxDist == d(any, point);
			maxDist = -1;
			return any.removeAnyLeaf();
		}
		//Not cheap, but better than resorting or returning the farthest child.
		//TODO return farthest child?
		Node<T> leaf = children.remove(0);
		if (children.isEmpty()) {
			maxDist = 0;
		} else if (leaf.getDistanceToParent() >= maxDist) {
			maxDist = -1;
		}
		return leaf;
	}

	double maxdist(CoverTree<T> tree) {
		if (maxDist == -1) {
			maxDist = recalcMaxDist(this, this, tree);
		}
		return maxDist;
	}

	double maxdistInternal() {
		return maxDist;
	}

	void adjustMaxDist(double newDist) {
		maxDist = (maxDist == -1) ? -1 : Math.max(newDist, maxDist); 
	}
	
	private static <T> double recalcMaxDist(Node<T> p, Node<T> node, CoverTree<T> tree) {
		double maxDist = 0;
		if (node.children != null) {
			for (int i = 0; i < node.children.size(); i++) {
				Node<T> child = node.children.get(i);
				// level 'x-1' this is == distToParent....
				double distChild = p == node ? child.getDistanceToParent() : tree.d(p, child);
				//first check dist, then check children, otherwise the 'if' below may not work 
				maxDist = Math.max(maxDist, distChild);
				double maxDistChild = child.maxdist(tree);
				if (maxDistChild + distChild > maxDist) {
					//traverse children's children.
					maxDist = Math.max(maxDist, recalcMaxDist(p, child, tree));
				}
			}
		}
		return maxDist;
	}
	
	int getLevel() {
		return level;
	}

	void setLevel(int level) {
		this.level = level;
	}

	void invalidateMaxDist() {
		maxDist = -1;
	}

	void removeChild(int i) {
		Node<T> n = children.remove(i);
		if (maxDist != -1) {
			if (n.maxDist != -1 && n.getDistanceToParent() + n.maxDist >= maxDist) {
				maxDist = -1;
			} else if (n.maxDist == -1) {
				maxDist = -1;
			}
		}		
	}

	void clearAndRemoveAllChildren(ArrayList<Node<T>> clearedChildren) {
		if (hasChildren()) {
			for (int i = 0; i < children.size(); i++) {
				Node<T> c = children.get(i);
				c.clearAndRemoveAllChildren(clearedChildren);
			}
			clearedChildren.addAll(children);
			children.clear();
		}
		maxDist = 0;
		distToParent = 0;
	}

}
