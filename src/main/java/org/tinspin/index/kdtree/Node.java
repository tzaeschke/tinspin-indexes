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
package org.tinspin.index.kdtree;

import java.util.Arrays;

import org.tinspin.index.Index;
import org.tinspin.index.kdtree.KDTree.KDStats;

/**
 * Node class for the quadtree.
 * 
 * @author ztilmann
 *
 * @param <T> Value type
 */
public class Node<T> extends Index.PointEntry<T> {

	private Node<T> left;
	private Node<T> right;
	private final int dim;
	
	Node(double[] p, T value, int dim, boolean defensiveKeyCopy) {
		super(defensiveKeyCopy ? p.clone() : p, value);
		this.dim = dim;
	}
	
	Node<T> getClosestNodeOrAddPoint(double[] p, T value, int dims, boolean defensiveKeyCopy) {
		//Find best sub-node.
		//If there is no node, we create one and return null
		if (p[dim] >= point()[dim]) {
			if (right != null) {
				return right;
			}
			right = new Node<>(p, value, (dim + 1) % dims, defensiveKeyCopy);
			return null;
		} 
		if (left != null) {
			return left;
		}
		left = new Node<>(p, value, (dim + 1) % dims, defensiveKeyCopy);
		return null;
	}

	Node<T> getLo() {
		return left;
	}
	
	Node<T> getHi() {
		return right;
	}

	void setLeft(Node<T> left) {
		this.left = left;
	}

	void setRight(Node<T> right) {
		this.right = right;
	}

	void checkNode(KDStats s, int depth) {
		s.nNodes++;
		if (depth > s.maxDepth) {
			s.maxDepth = depth;
		}
		if (left != null) {
			left.checkNode(s, depth + 1);
		}
		if (right != null) {
			right.checkNode(s, depth + 1);
		}
	}

	@Override
	public String toString() {
		return "center=" + Arrays.toString(point()) + " " + System.identityHashCode(this);
	}

	boolean isLeaf() {
		return this.left == null && this.right == null;
	}

	int getDim() {
		return dim;
	}

	public void set(double[] point, T value) {
		super.set(point, value);
	}
}