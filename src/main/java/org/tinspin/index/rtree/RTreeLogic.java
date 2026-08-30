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

/**
 * Interface for different splitting algorithms.
 */
public interface RTreeLogic {

	/**
	 * Function to choose a subtree.
	 * @param root root node
	 * @param e new entry
	 * @param desiredInsertionLevel insertion level
	 * @param nLevels depth of the tree
	 * @return node for insertion
	 * @param <T> value type
	 */
	<T> RTreeNode<T> chooseSubTree(RTreeNode<T> root, RTreeEntry<T> e,
			int desiredInsertionLevel, int nLevels);

	/**
	 * Function to determine whether a node still has space.
	 * @param node the node to check
	 * @return true if the node has space for another entry
	 * @param <T> value type
	 */
	<T> boolean hasSpace(RTreeNode<T> node);

	/**
	 * Node split function.
	 * @param node nod to split
	 * @param e new entry
	 * @return new node created from split
	 * @param <T> value type
	 */
	<T> RTreeNode<T> split(RTreeNode<T> node, RTreeEntry<T> e);

	/**
	 * Reinsert entry.
	 * @param node node
	 * @param e entry
	 * @return other entries that new reinsertion
	 * @param <T> value type
	 */
	<T> RTreeEntry<T>[] reInsert(RTreeNode<T> node, RTreeEntry<T> e);

}
