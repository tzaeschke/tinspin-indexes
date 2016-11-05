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

import org.zoodb.index.RectangleIndex;

/**
 * X-Tree implementation based on the paper from
 * Beckmann, N.; Kriegel, H. P.; Schneider, R.; Seeger, B. (1990). 
 * "The R*-tree: an efficient and robust access method for points and rectangles".
 * 
 * Revised R*Tree (for DBMS, see conclusion?) -- RR*Tree
 * "A Revised R*-tree in Comparison with Related Index Structures"
 * Norbert Beckmann; Bernhard Seeger
 * 
 * 
 * Interesting comparison:
 * http://www.boost.org/doc/libs/1_58_0/libs/geometry/doc/html/geometry/spatial_indexes/introduction.html
 * @author ztilmann
 *
 * @param <T>
 */
public class XTree<T> extends RTree<T> {

	static RTreeLogic logic = new RStarTreeLogic();
	
	/**
	 * Create an RTree. By default it is an R*tree.
	 * @param dims dimensionality
	 */
	private XTree(int dims) {
		super(dims);
	} 
	
	public static <T> XTree<T> createXTree(int dims) {
		return new XTree<>(dims);
	}
	
	/**
	 * @param e
	 * @param desiredInsertionLevel Entries have to be inserted at the desired level.
	 * The level is usually '0' (for data points) but can be higher for
	 * reinsertion of subtrees.
	 */
	private void insert(Entry<T> e, boolean[] blockedLevels, int desiredInsertionLevel) {
		TODO XTREE
		//I1
		RTreeNode<T> node = logic.chooseSubTree(root, e, desiredInsertionLevel, depth);
		//I2
		if (logic.hasSpace(node)) {
			node.addEntry(e);
			node.extendParentMBB();
		} else {
			RTreeNode<T> newNode = overflowTreatment(
					node, e, blockedLevels, desiredInsertionLevel);
			//I3 propagate overflow up the tree.
			if (newNode != null) {
				nNodes++;
				if (desiredInsertionLevel+1 < depth) {
					insert(newNode, blockedLevels, desiredInsertionLevel+1);
				} else {
					RTreeNodeDir<T> newRoot = new RTreeNodeDir<>(dims);
					nNodes++; //for the new root
					newRoot.addEntry(newNode);
					newRoot.addEntry(root);
					root = newRoot;
					depth++;
				}
			}
		}
		//I4 adjust governing rectangles
		// -> done inside split/reinsert
	}
	
	
	private int insert(RTreeNode<T> baseNode, T obj, RTreeNode<T> new_node) {
		SET_OF_MBR s1, s2;
		RTreeNode<T> follow;
		RTreeNode<T> new_son;
		int return_value;
		follow = logic.choose_subtree(obj);
		// choose a son node to insert obj into
		return_value = insert(follow, obj, new_son);
		// insert obj into subtree
		baseNode.extendMBB(follow); //TODO extend parent?
		// update MBR of old son node
		if (return_value == SPLIT){
			baseNode.addEntry(new_son);
			// insert mbr of new son node into current node
			if (!baseNode.hasSpace())
			{ // overflow occurs
				if (logic.split(mbrs, s1, s2) == true){
					// topological or overlap-minimal split was successfull
					set_mbrs(s1);
					new_node = new X_DirectoryNode(s2);
					return SPLIT;
				}
				else // there is no good split
				{
					new_node = new X_SuperNode();
					new_node.set_mbrs(mbrs);
					return SUPERNODE;
				}
			}
		} else if (return_value == SUPERNODE){
			// node â€˜followâ€™ becomes a supernode
			baseNode.remove_son(follow);
			baseNode.insert_son(new_son);
		}
		return NO_SPLIT;
	}
	
	private boolean split(RTreeNode<T> node, SET_OF_MBR in, SET_OF_MBR out1, SET_OF_MBR out2)
	{
		SET_OF_MBR t1, t2;
		MBR r1, r2;
		// first try topological split, resulting in two sets of MBRs t1 and t2
		node.topological_split(in, t1, t2);
		r1 = t1.calc_mbr(); r2 = t2.calc_mbr();
		// test for overlap
		if (overlap(r1, r2) > MAX_OVERLAP)
		{
			// topological split fails -> try overlap minimal split
			node.overlap_minimal_split(in, t1, t2);
			// test for unbalanced nodes
			if (t1.num_of_mbrs() < MIN_FANOUT || t2.num_of_mbrs() < MIN_FANOUT)
				// overlap-minimal split also fails (-> caller has to create supernode)
				return false;
		}
		//*out1 = t1; *out2 = t2;
		out1.addAll(t1);
		out2.addAll(t2);
		return true;
	}
	
	@Override
	public String toString() {
		return "XTreeZ;" + logic.getClass().getSimpleName() +
				";size=" + size() + ";nNodes=" + getNodeCount() +
				";dir_m/M=" + NODE_MIN_DIR + "/" + NODE_MAX_DIR +
				";data_m/M=" + NODE_MIN_DATA + "/" + NODE_MAX_DATA;
	}
}
