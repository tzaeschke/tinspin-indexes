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

import org.tinspin.index.PointEntry;
import org.tinspin.index.kdtree.KDTree.KDStats;

/**
 * Node class for the quadtree.
 * 
 * @author ztilmann
 *
 * @param <T>
 */
public class Node<T> implements PointEntry<T> {

	private double[] coordinate;
	private T value;
	private Node<T> left;
	private Node<T> right;
	
	Node(double[] p, T value) {
		this.coordinate = p;
		this.value = value;
	}
	
	Node<T> getClosestNodeOrAddPoint(double[] p, T value, int depth, int dims) {
		//Find best sub-node.
		//If there is no node, we create one and return null
		int pos = depth % dims;
		if (p[pos] >= coordinate[pos]) {
			if (right != null) {
				return right;
			}
			right = new Node<>(p, value);
			return null;
		} 
		if (left != null) {
			return left;
		}
		left = new Node<>(p, value);
		return null;
	}

	double[] getKey() {
		return coordinate;
	}

	T getValue() {
		return value;
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

	void setKeyValue(double[] key, T value) {
		this.coordinate = key;
		this.value = value;
	}

	@Override
	public double[] point() {
		return this.coordinate;
	}

	@Override
	public T value() {
		return this.value;
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
}