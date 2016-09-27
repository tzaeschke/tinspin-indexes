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
import java.util.Comparator;

public class STRLoader<T> {

	private int nNodes = 0;
	private int size = 0;
	private RTreeNode<T> root;
	private int depth;
	
	public RTreeNode<T> getRoot() {
		return root;
	}

	public int getNNodes() {
		return nNodes;
	}

	public int getSize() {
		return size;
	}

	public int getDepth() {
		return depth;
	}

	@SuppressWarnings("unchecked")
	public void load(Entry<T>[] entries) {
		int dims = entries[0].lower().length;
		int N = entries.length;
		int M = RTree.NODE_MAX_DATA;
		int slicesPerAxis = (int) Math.pow(N/M, 1/(double)dims);
		CenterComp comp = new CenterComp();
		
		sortChunks(entries, dims, M, slicesPerAxis, comp);
		
		RTreeNode<T>[] nodes = new RTreeNode[(int) Math.ceil(N/(double)M)];
		int posNode = 0;
		RTreeNode<T> node = null;
		for (int i = 0; i < entries.length; i++) {
			if (i % M == 0) {
				node = new RTreeNodeLeaf<>(dims);
				nodes[posNode++] = node;
			}
			node.addEntry(entries[i]);
		}
		nNodes += nodes.length;
		
		depth = 1;
		if (nodes.length == 1) {
			root = nodes[0];
			nNodes = 1;
			size = entries.length;
			return;
		}
		
		int MDir = RTree.NODE_MAX_DIR;
		RTreeNodeDir<T>[] parentNodes = null; 
		slicesPerAxis = (int) Math.pow(N/MDir, 1/(double)dims);
		do {
			depth++;
			parentNodes = new RTreeNodeDir[(int) Math.ceil(nodes.length/(double)MDir)];
			//sort
			sortChunks(nodes, dims, MDir, slicesPerAxis, comp);
			nNodes += parentNodes.length;
			RTreeNodeDir<T> p = null;
			int posParent = 0;
			for (int i = 0; i < nodes.length; i++) {
				if (i % MDir == 0) {
					p = new RTreeNodeDir<>(dims);
					parentNodes[posParent++] = p;
				}
				// add
				p.addEntry(nodes[i]);
			}
			nodes = parentNodes;
		} while (parentNodes.length > 1);
		root = parentNodes[0];
		size = entries.length;
		//verify(entries);
		return;
	}
	
	private void verify(Entry<T>[] entries) {
		System.err.println("Verifying bulkload");
		int n = verifyNode(root, depth-1);
		if (n != entries.length) {
			throw new IllegalStateException("n=" + n + "/" + entries.length);
		}
	}

	
	private int verifyNode(RTreeNode<T> node, int level) {
		System.out.println("Checking node: " + node);
		ArrayList<Entry<T>> el = node.getEntries();
		int nEntries = 0;
		int nNodes = 0;
		for (int i = 0; i < el.size(); i++) {
			Entry<T> e = el.get(i);
			if (!Entry.calcIncludes(node.lower(), node.upper(), e.lower(), e.upper())) {
				throw new IllegalStateException();
			}
			if (level == 0) {
				if (e instanceof RTreeNode) {
					throw new IllegalStateException();
				}
				nEntries++;
				continue;
			}
			nNodes++;
			if (level == 1) {
				if (!(e instanceof RTreeNodeLeaf)) {
					throw new IllegalStateException();
				}
			}
			if (level > 1) {
				if (!(e instanceof RTreeNodeDir)) {
					throw new IllegalStateException();
				}
			}
			RTreeNode<T> subNode = (RTreeNode<T>) e;
			if (subNode.getParent() != node) {
				throw new IllegalStateException();
			}

			//depth first
			nEntries += verifyNode(subNode, level-1);
			
			//Only for point data
//			for (int j = i+1; j < el.size(); j++) {
//				Entry<T> e2 = el.get(j);
//				if (Entry.checkOverlap(e.min, e.max, e2)) {
//					System.out.println("Overlapping:");
//					System.out.println(e);
//					System.out.println(e2);
//					System.out.println(Arrays.toString(((RTreeNode<T>)e).getEntries().toArray()));
//					System.out.println(Arrays.toString(((RTreeNode<T>)e2).getEntries().toArray()));
//					throw new IllegalStateException();
//				}
//			}			
		}
		return nEntries;
	}

	private void sortChunks(Entry<T>[] entries, int dims, int M,
			int nodesPerAxis, CenterComp comp) {
		comp.setDim(0);
		Arrays.sort(entries, comp);
		for (int d = 1; d < dims; d++) {
			comp.setDim(d);
			int chunkSize = (int) Math.pow(nodesPerAxis*M, dims-d);
			int pos = 0;
			while (pos < entries.length) {
				int end = Math.min(pos+chunkSize, entries.length);
				Arrays.sort(entries, pos, end, comp);
				pos += chunkSize;
			}
		}
	}
	
	private class CenterComp implements Comparator<Entry<T>> {
		int dim = -1;
		
		void setDim(int dim) {
			this.dim = dim;
		}
		
		@Override
		public int compare(Entry<T> o1, Entry<T> o2) {
			double c1 = o1.upper()[dim] + o1.lower()[dim]; // *0.5
			double c2 = o2.upper()[dim] + o2.lower()[dim]; // *0.5
			return c1 < c2 ? -1 : c1 > c2 ? 1 : 0;
		}
	}
	
}
