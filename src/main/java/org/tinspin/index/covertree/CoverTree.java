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
import java.util.Arrays;
import java.util.Iterator;

import org.tinspin.index.PointDistance;
import org.tinspin.index.PointMap;
import org.tinspin.index.Stats;


/**
 * A 'faster' CoverTree implementation based on the paper:
 * <p>
 * "Faster Cover Trees", Mike Izbicki, Christian R. Shelton,
 * Proceedings of the 32nd International Conference on Machine Learning,
 * Lille, France, 2015. 
 * <p>
 * Changes over original algorithms:
 *  - findNearestNeighbour compares
 *  	if (d(y, x) greater (d(_x_, q.point()) - q.maxdist(this)))
 *    instead of 
 *      if (d(y, x) greater (d(_y_, q.point()) - q.maxdist(this)))
 *  - maxDist: 
 *    - lazily calculated.
 *    - calculated while excluding subbranches that don't need to be calculated
 *    - Invalidated on modifications (should speed up remove/insert)
 *  - Algorithm 2 (insert) uses BASE=1.3:
 *     BASE*covdist(p)  
 *     instead of
 *     2*covdist(p)) 
 *  - Rebalancing:
 *    - The paper proposes to sort the children on their distance to 'x' and then
 *      insert into the first one that has enough covdist.
 *      The sorting appears unnecessary because we anyway have to check all children,
 *      and finding the closest one is trivially possible without sorting.
 *  - Some optimizations for kNN which are not discussed in the paper
 *  <p>
 * Other:
 * - We also implemented the kNN algorithm by Hjaltason and Samet,
 *   but it about 2x as long as our own algorithm in all scenarios we tested.
 * TODO
 * - Rebalancing (nearest-ancestor)
 * - Merge Node/Point classes
 *     
 * @author Tilmann Zäschke
 */
public class CoverTree<T> implements PointMap<T> {

	private final int dims;
	private int nEntries;
	
	private Node<T> root = null;
	
	private static final double DEFAULT_BASE = 2.0;
	
	private static final boolean NEAREST_ANCESTOR = true;
	
	private final double BASE;
	private final double LOG_BASE;
	private final PointDistance dist;
	private static final PEComparator comparator = new PEComparator();
	private long nDistCalc = 0;
	private long nDist1NN = 0;
	private long nDistKNN = 0;
	
	private final double log13(double n) {
		//log_b(n) = log_e(n) / log_e(b)
		return Math.log(n) / LOG_BASE;
	}
	
	private CoverTree(int nDims, double base, PointDistance dist) {
		this.dims = nDims;
		this.BASE = base;
		this.LOG_BASE = Math.log(BASE);
		this.dist = dist != null ? dist : PointDistance.L2;
	}
		
	public static <T> PointEntry<T> create(double[] point, T value) {
		return new PointEntry<>(point, value);
	}

	public static <T> CoverTree<T> create(int nDims) {
		return new CoverTree<>(nDims, DEFAULT_BASE, PointDistance.L2);
	}
	
	public static <T> CoverTree<T> create(int nDims, double base, PointDistance dist) {
		return new CoverTree<>(nDims, base, dist);
	}
	
	public static <T> CoverTree<T> create(PointEntry<T>[] data, double base,
			PointDistance distFn) {
		if (data == null || data.length == 0) {
			throw new IllegalStateException("Bulk load with empty data no possible.");
		}
		CoverTree<T> tree = new CoverTree<>(data[0].point().length, base, distFn);
		if (data.length == 1) {
			tree.root = new Node<>(data[0], 0);
			tree.nEntries++;
			return tree;
		}
		
		//The point of bulk load is (currently) only to initialize the tree with a
		//suitable 'level' and 'root', which should avoid any problems with having 
		//to pull up leaves to replace the root. Bulk loading thus allows a BASE < 2.0 .
		
		PointEntry<T> newRoot = data[0];
		PointEntry<T> mostDistantPoint = data[1];
		double largestDistance = tree.d(newRoot, mostDistantPoint); 
		for (int i = 2; i < data.length; i++) {
			PointEntry<T> x = data[i];
			double dist = tree.d(newRoot, x);
			if (dist > largestDistance) {
				largestDistance = dist;
				mostDistantPoint = x;
			}
		}
		
		int requiredLevel = (int) (tree.log13(largestDistance) + 1);
		tree.root = new Node<>(data[0], requiredLevel);
		//TODO insert farthest point as second point?
		
		for (int i = 1; i < data.length; i++) {
			Node<T> x = new Node<>(data[i], -1);
			tree.insert(tree.root, x);
		}

		tree.nEntries = data.length;

		return tree;
	}
	
	@Override
	public int getDims() {
		return dims;
	}

	@Override
	public int size() {
		return nEntries;
	}

	@Override
	public void clear() {
		nEntries = 0;
		root = null;
	}

	@Override
	public CTStats getStats() {
		CTStats stats = new CTStats(this);
		if (root != null) {
			getStats(root, stats);
		}
		return stats;
	}

	@Override
	public int getNodeCount() {
		//nEntries == nNodes
		return nEntries;
	}

	@Override
	public int getDepth() {
		if (root == null) {
			return 0;
		}
		return root.getLevel() - getStats().minLevel;
	}

	@Override
	public String toStringTree() {
		if (root != null) {
			return toStringTree(new StringBuilder(), root).toString();
		}
		return "";
	}

	private StringBuilder toStringTree(StringBuilder sb, Node<T> node) {
		toStringNode(sb, node);
		sb.append("\n");
		if (node.hasChildren()) {
			for (Node<T> c : node.getChildren()) {
				toStringTree(sb, c);
			}
		}
		return sb;
	}
	
	private StringBuilder toStringNode(StringBuilder sb, Node<T> node) {
		for (int i = root.getLevel(); i > node.getLevel(); i--) {
			sb.append(".");
		}
		sb.append("L=").append(node.getLevel());
		sb.append(";MaxD=")
		//.append(node.maxdist(this))
		.append("/").append(node.maxdistInternal());
		sb.append(";ParD=").append(node.getDistanceToParent());
		sb.append(";CovD=").append(covdist(node));
		if (node.hasChildren()) {
			sb.append(";nC=").append(node.getChildren().size());
		} else {
			sb.append(";nC=0");
		}
		sb.append(";coord=").append(Arrays.toString(node.point().point()));
		return sb;
	}
	
	@Override
	public void insert(double[] key, T value) {
		//System.out.println("Inserting(" + nEntries + "): " + Arrays.toString(key));
		Node<T> x = new Node<>(new PointEntry<>(key, value), -1);
		if (root == null) {
			root = x.initLevel(0);
			nEntries++;
			return;
		}
		if (!root.hasChildren()) {
			double dist = d(root, x);
			//initialize levels from current distance
			int level = (int) log13(dist);
			root.setLevel(level + 1);
			Node<T> q = x.initLevel(level);
			root.addChild(q, dist);
			nEntries++;
			return;
		}
		insert(root, x);
		nEntries++;
	}
	
	private void insert(Node<T> p, Node<T> x) {
//		Algorithm 2 Simplified cover tree insertion
//		function insert(cover tree p, data point x)
//		1: if d(p;x) > covdist(p) then
//		2: while d(p;x) > 2covdist(p) do
//		3: Remove any leaf q from p
//		4: p0  tree with root q and p as only child
//		5: p  p0
//		6: return tree with x as root and p as only child
//		7: return insert (p,x)
		double distPX = d(p,x); 
		if (distPX > covdist(p)) {
			//TODO reuse distPX in first iteration?!?
			//while (d(p.point(), x) > BASE*covdist(p)) {
			//TODO the above is WRONG! For example it does not work with [2,3],[5,4],[9,6],
			//     resulting in a covDist([9,6]) that does not cover [2,3]
			//  while (distPX + covdist(p)> BASE*covdist(p)) {
			//Even better: we could use:
			//  while (distPX + maxdist(p)> BASE*covdist(p)) {
			//TODO above fix can be simplified:
			while (distPX > (BASE-1)*covdist(p)) {
				//3: Remove any leaf q from p
				Node<T> q = p.removeAnyLeaf();
				//4: p0 = tree with root q and p as only child
				Node<T> p0 = q;
				q.setLevel(p.getLevel() + 1);
				q.addChild(p, d(q, p));
				//5: p  p0
				p = p0;
				distPX = d(p,x);
			}
			//return tree with x as root and p as only child;
			Node<T> newRoot = x.initLevel(p.getLevel() + 1);
			newRoot.addChild(p, distPX);
			//TODO set in caller?
			this.root = newRoot;
			return;
		}
		if (NEAREST_ANCESTOR) {
			insert2NA(p,x, distPX);
		} else {
			insert2(p, x, distPX);
		}
	}

	private Node<T> insert2(Node<T> p, Node<T> x, double distPX) { 
//		function insert (cover tree p, data point x)
//		prerequisites: d(p;x) <= covdist(p)
//		1: for q 2 children(p) do
//		2: if d(q;x) <= covdist(q) then
//		3: q0  insert (q;x)
//		4: p0   p with child q replaced with q0
//		5: return p0
//		6: return p with x added as a child

		//1: for q : children(p) do
		ArrayList<Node<T>> children = p.getOrCreateChildren();
		double covDistQ = children.isEmpty()? 0 : covdist(children.get(0));
		for (int i = 0; i < children.size(); i++) {
			Node<T> q = children.get(i);
			//2: if d(q;x) <= covdist(q) then
			double distQX = d(q, x); 
			if (distQX <= covDistQ) {
				//3: q0  insert (q;x)
				Node<T> qNew = insert2(q, x, distQX);
				//4: p0   p with child q replaced with q0
				if (qNew != q) {
					p.replaceChild(i, qNew);
				}
				//5: return p0
				p.adjustMaxDist(distPX);
				return p;
			}
		}
		//6: return p with x added as a child
		p.addChild(x.initLevel(p.getLevel() - 1), distPX);
		return p;
//		return rebalance(p, x);
	}

	/**
	 * Nearest ancestor insert
	 * @param p
	 * @param x
	 * @param distPX
	 * @return
	 */
	private Node<T> insert2NA(Node<T> p, Node<T> x, double distPX) { 
//		function insert (cover tree p, data point x)
//		prerequisites: d(p;x) <= covdist(p)

		//insert into closest child!
		
		//1: for q : children(p) do
		ArrayList<Node<T>> children = p.getOrCreateChildren();
		double covDistQ = covDist(p.getLevel() - 1);
		Node<T> best = null;
		double distBest = Double.MAX_VALUE;
		int posBest = -1;
		for (int i = 0; i < children.size(); i++) {
			Node<T> q = children.get(i);
			//2: if d(q;x) <= covdist(q) then
			double distQX = d(q, x); 
			if (distQX <= covDistQ && distQX < distBest) {
				distBest = distQX;
				best = q;
				posBest = i;
			}
		}
		if (best != null) {
			Node<T> q = best;
			double distQX = distBest;
			int i = posBest;
			//3: q0  insert (q;x)
			Node<T> qNew = insert2NA(q, x, distQX);
			//4: p0   p with child q replaced with q0
			if (qNew != q) {
				p.replaceChild(i, qNew);
			}
			//5: return p0
			p.adjustMaxDist(distPX);
			return p;
		}
		//6: return p with x added as a child
//		p.addChild(x.initLevel(p.getLevel() - 1), distPX);
//		return p;
		return rebalanceTZ(p, x, distPX);
	}

	private Node<T> rebalanceTZ(Node<T> p, Node<T> x, double distPX) {
		//Rebalance to ensure the nearest-ancestor invariant:
		//1) Find all nodes 'q' whose covdist && mindist overlaps with the new node x
		//2) For all 'q' traverse all subnode 'r' and check if:
		//   a) Parent-dist(r) > dist(x,q)-covdist(x)
		//   b1) (1st level): dist(x,r) < parentDist(r)
		//   b2)  
		
		//Remember the nearest-ancestor invariants only refers to the IMMEDIATE
		//ancestor!
		//Convention: 'x' is a new node that is inserted into a node 'p'. The other
		//children or 'p' are named 'q'. Any children of 'q' or 'x' are named 'r'.
		//Any children (descendants?) of any 'r' a named 's'.
		//Implications of invariant: 
		//- If we childnode r of q to x, we try to keep it on the same level,
		//  which means we can move it together with all it's children.
		//  -> Problem if we move r1 from q1 and r2 from q2 into x,
		//     there is no guarantee that r1 and r2 have a minimum distance of
		//     cov-dist, in other words they may need 'merging'!
		//     TODO is that geometrically possible?
		//  -> Merging entails a bigger problem: 
		//     If we reinsert all the children (r1, r2, ..) into x, on of the
		//     subchildren s of r, may be inserted directly into 'x', even though
		//     x may not be it's nearest ancestor (that can only happen for children
		//     or r's, because ...)
		//Solution: 
		//  - r's can be directly re-inserted into x, but any children of r's
		//     need to be queued and reinserted a root level....
		//  - Reinsertion of children r of q into x is fine as long as none 
		//    of the reinserted r's overlap (more precisely if they do not have children
		//    that could be in any other r of x.
		//  - Optimization: Even if an r cannot be reinserted into x, it's 's' may be 
		//    reinsertable if there is only one matching r in x.
		//  - In other words, any new r of x that overlaps with any 
		//  In orher words: Direct reinsertion is fine, as long as the new level is equal 
		//  or lower that the previous level.
		//
		//Algorithm for reinsertion:
		//- if there is no overlapping r1 in x: Insert r2 including all children
		//- If there is an overlapping r1, add r2 to List of reinsertables.
		//  for every child s or r2, check whether there is only one matching r in x.
		//  - if yes: insert s into that r. Then: recurse for children of s or add to reinsertables 
		//  - if no: add 's' and all it's children to reinsertables.
		//All reinsertion can be done directly into 'p' (parent of q and x) because
		//we know that there are at least two nodes in p (x and q) that can contain the
		//moved node. Insert directly into 'x' would be good for all direct children of
		//'q' because we know that 'x' is the closest ancestor. For sub-children of 'q'
		//we cannot guarantee that. If one of these subchildren can be reinsertedinto
		//any r of x, it would be fine. But if it is inserted directly in 'x', we would need
		//to verify that there is no other q in p that would be a better ancestor.
		//However, if we reinsert these subchildren in to 'p', we know that there
		//is at least one 'q' (namely it's original parent) that contain it, so there
		//is no need that 'p' is the closest ancestor because 'p' would never be a direct ancestor.
		//In short: Insert into 'x' if possible, otherwise reinsert into 'p'.
		ArrayList<Node<T>> reinsert = null;
		//init, because we may use (insert, covdist, ..) this node before we add it to p.
		x.initLevel(p.getLevel() - 1);
		if (p.hasChildren()) {
			ArrayList<Node<T>> children = p.getChildren();
			double covDist = covDist(p.getLevel() - 1);
			//Check all siblings q of x whether they may contain point that need
			//to be reassigned. 'q' itself cannot require reassignment. 
			for (int i = 0; i < children.size(); i++) {
				Node<T> q = children.get(i);
				if (q.hasChildren()) {
					double distQX = d(q, x); 
					if (distQX <= 2*covDist
							&& distQX <= covDist + q.maxdist(this)) {
						reinsert = (reinsert != null) ? reinsert : new ArrayList<>();
						rebalanceSub(x, q, reinsert, distQX);
					}
				}
			}
		}
		p.addChild(x, distPX);
		if (reinsert != null) {
			for (int i = 0; i < reinsert.size(); i++) {
				Node<T> q = reinsert.get(i);
				insert2NA(p, q, distPX);
			}
		}
		return p;
	}
	
	/**
	 * 
	 * @param x New ancestor
	 * @param q Current ancestor
	 * @param reinsert resinsertion queue 
	 */
	private void rebalanceSub(Node<T> x, Node<T> q, ArrayList<Node<T>> reinsert,
			double distQX) {
		if (q.hasChildren()) {
			ArrayList<Node<T>> children = q.getChildren();
			for (int i = 0; i < children.size(); i++) {
				Node<T> r = children.get(i);
				double distRQ = r.getDistanceToParent();
				if (distRQ*2 > distQX) {
					double distRX = d(r, x);
					if (distRX < distRQ) {
						//okay, needs reinsertion
						q.removeChild(i--);
						reinsert(x, r, reinsert, distRX);
					}
				}
			}
		}
	}
	
	private void reinsert(Node<T> x, Node<T> rNew, ArrayList<Node<T>> reinsertLater,
			double distRX) {
		ArrayList<Node<T>> children = x.getOrCreateChildren();
		double covDistR = covdist(rNew);
		for (int i = 0; i < children.size(); i++) {
			Node<T> rx = children.get(i);
			double distRxRNew = d(rx, rNew);
			//Overlap of covers?
			if (distRxRNew < 2*covDistR) {
				if (distRxRNew < covDistR) {
					//reinsert rNew into rX: not possible, reinsert in parent
					rNew.clearAndRemoveAllChildren(reinsertLater);
					reinsertLater.add(rNew);
					return;
				} else if (rNew.maxdist(this) > distRxRNew-covDistR) {
					//rNew's children overlap with rX: 
					//reinsert rNew and reinsert children into parent.
					//But we need to keep checking other rX.
					rNew.clearAndRemoveAllChildren(reinsertLater);
				}
			} 
		}
		if (distRX + rNew.maxdist(this) > covdist(x)) {
			//TODO only clear those that violate the limit
			rNew.clearAndRemoveAllChildren(reinsertLater);
		}
		x.addChild(rNew, distRX);
	}
	
	private Node<T> rebalance(Node<T> p, Node<T> x) {
//  	function rebalance(cover trees p, data point x)
//  	prerequisites: x can be added as a child of p without violating
//      the covering or separating invariants
//		1: create tree x0 with root node x at level level(p)􀀀1 x0
//		contains no other points
//		2: p0   p
//		3: for q 2 children(p) do
//		4: (q0;moveset; stayset) rebalance (p;q;x)
//		5: p0   p0 with child q replaced with q0
//		6: for r 2 moveset do
//		7: x0  insert(x0; r)
//		8: return p0 with x0 added as a child
	
		//	1: create tree x0 with root node x at level level(p)-1 x0
		//	contains no other points
		Node<T> x0 = x.initLevel(p.getLevel() - 1); 
		//	2: p0   p
		Node<T> p0 = p;
		//	3: for q 2 children(p) do
		if (p.hasChildren()) {
			ArrayList<Node<T>> children = p.getChildren();
			ArrayList<Node<T>> moveSet = new ArrayList<>();
			ArrayList<Node<T>> staySet = new ArrayList<>();
			for (int i = 0; i < children.size(); i++) {
				Node<T> q = children.get(i);
				//	4: (q0;moveset; stayset) rebalance (p;q;x)
				moveSet.clear();
				staySet.clear();
				Node<T> q0 = rebalance(p, q, x, moveSet, staySet);
				//	5: p0   p0 with child q replaced with q0
				if (q0 != null) {
					p0.replaceChild(i, q0);
				}
				q0.adjustMaxDist(d(p0, q0));
				//TODO adjust max?!?!
				//	6: for r 2 moveset do
				for (int j = 0; j < moveSet.size(); j++) {
					//	7: x0  insert(x0; r)
					//x0 = insert(x0, moveSet.get(j)); //TODO report!
					x0 = insert2(x0, moveSet.get(j), d(x0, moveSet.get(j)));
				}
			}
		}
		//	8: return p0 with x0 added as a child
		p0.addChild(x0, d(p0, x0));
		return p0;
	}
	
	private Node<T> rebalance(Node<T> p, Node<T> q, Node<T> x, 
			ArrayList<Node<T>> moveSet, ArrayList<Node<T>> staySet) {
//	function rebalance (cover trees p and q, point x)
//	prerequisites: p is an ancestor of q
//	1: if d(p;q) > d(q;x) then
//	2: moveset; stayset   /0
//	3: for r 2 descendants(q) do
//	4: if d(r; p) > d(r;x) then
//	5: moveset  moveset [frg
//	6: else
//	7: stayset  stayset [frg
//	8: return (null;moveset; stayset)
//	9: else
//	10: moveset0; stayset0   /0
//	11: q0  q
//	12: for r 2 children(q) do
//	13: (r0;moveset; stayset) rebalance (p; r;x)
//	14: moveset0  moveset[moveset0
//	15: stayset0  stayset[stayset0
//	16: if r0 = null then
//	17: q0  q with the subtree r removed
//	18: else
//	19: q0  q with the subtree r replaced by r0
//	20: for r 2 stayset0 do
//	21: if d(r;q)0  covdist(q)0 then
//	22: q0  insert(q0; r)
//	23: stayset0  stayset0􀀀frg
//	24: return (q0;moveset0; stayset0)
		
		// 1: if d(p;q) > d(q;x) then
		if (d(p, q) > d(q, x)) {
			//2: moveset; stayset   /0
			//3: for r 2 descendants(q) do
			if (q.hasChildren()) {
				ArrayList<Node<T>> children = q.getChildren();
				for (int i = 0; i < children.size(); i++) {
					Node<T> r = children.get(i);
					//4: if d(r; p) > d(r;x) then
					if (d(r, p) > d(r, x)) {
						//5: moveset  moveset [frg
						moveSet.add(r);
					} else {
						//6: else
						//7: stayset  stayset [frg
						staySet.add(r);
					}
				}
			}
			//	8: return (null;moveset; stayset)
			return null;
			//	9: else
		} else {  	
			//10: moveset0; stayset0   /0
			//11: q0  q
			Node<T> q0 = q;
			//12: for r 2 children(q) do
			if (q.hasChildren()) {
				ArrayList<Node<T>> children = q.getChildren();
				for (int i = 0; i < children.size(); i++) {
					Node<T> r = children.get(i);
					//13: (r0;moveset; stayset) rebalance (p; r;x)
					//TODO moveSet/stayset?????
					Node<T> r0 = rebalance(p, r, x, moveSet, staySet); 
					//14: moveset0 = moveset + moveset0
					//15: stayset0 = stayset + stayset0
					//16: if r0 = null then
					if (r0 == null) {
						//17: q0  q with the subtree r removed
						q0 = q;
						q0.removeChild(i);
					} else {
						//18: else
						//19: q0  q with the subtree r replaced by r0
						q0 = q;
						q0.replaceChild(i, r0);
						q0.adjustMaxDist(d(q0, r0));
					}
				}
			}
			//20: for r 2 stayset0 do
			for (int i = 0; i < staySet.size(); i++) {
				Node<T> r = staySet.get(i);
				//TODO typo, apostrophe _inside_ ()
				//21: if d(r;q)0 <= covdist(q)0 then
				if (d(r,q0) <= covdist(q0)) {
					//22: q0  insert(q0; r)
					//q0 = insert(q0, r); TODO report?????
					q0 = insert2(q0, r, d(r,q0));
					//23: stayset0 = stayset0 - {r}
					//TODO optimize?
					staySet.remove(i--);
				}
			}
			//24: return (q0;moveset0; stayset0)
			return q0;
		}
	}
	
	
	@Override
	public T remove(double[] point) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public T update(double[] oldPoint, double[] newPoint) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public T queryExact(double[] point) {
		if (root == null) {
			return null;
		}
		PointEntry<T> result = queryExact(root, point);
		return result == null ? null : result.value();
	}
	
	private PointEntry<T> queryExact(Node<T> p, double[] x) {
		if (Arrays.equals(p.point().point(), x)) {
			return p.point();
		}
		double distP = d(p.point(), x);
		if (distP > p.maxdist(this)) {
			return null;
		}

		ArrayList<Node<T>> children = p.getChildren();
		for (int i = 0; i < children.size(); i++) {
			Node<T> q = children.get(i);
			PointEntry<T> result = queryExact(q, x);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Override
	public PointIterator<T> iterator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public PointIterator<T> query(double[] min, double[] max) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}
	
	@Override
	public PointEntryKnn<T> query1nn(double[] center) {
		if (root == null) {
			return null;
		}
		//TODO avoid all these object creations
		PointEntry<T> x = new PointEntry<>(center, null);
		double distPX = d(root.point(), center);
		nDist1NN++;
		PointEntryKnn<T> y = new PointEntryKnn<>(root.point().point(), root.point().value(), distPX);
		findNearestNeighbor(root, x, y, distPX);
		return y;
	}

	private void findNearestNeighbor(Node<T> p, PointEntry<T> x, final PointEntryKnn<T> y,
			double distPX) {
//		Algorithm 1 Find nearest neighbor
//		function findNearestNeighbor(cover tree p, query
//		point x, nearest neighbor so far y)
//		1: if d(p;x) < d(y;x) then
//		2: y  p
//		3: for each child q of p sorted by distance to x do
//		4: if d(y;x) > d(y;q)-maxdist(q) then
//		5: y findNearestNeighbor(q;x;y)
//		6: return y
		if (distPX < y.dist()) {
			y.set(p.point(), distPX);
		}

		if (p.hasChildren()) {
			ArrayList<Node<T>> children = p.getChildren();
			for (int i = 0; i < children.size(); i++) {
				Node<T> q = children.get(i);
				
				//Exclude children that are (compared to x) too close to the node or too far away
				//to contain any useful points.
				double distPQ = q.getDistanceToParent();
				if (distPQ+q.maxdist(this) < distPX-y.dist()
						|| distPQ-q.maxdist(this)> distPX+y.dist()) {
					continue;
				}

				//TODO report to authors: use d(y;x) > d(_X_;q)-maxdist(q)
				double distQX = d(x, q.point());
				nDist1NN++;
				if (y.dist() > (distQX - q.maxdist(this))) {
					findNearestNeighbor(q, x, y, distQX);
				}
			}
		}
	}

	@Override
	public PointIteratorKnn<T> queryKnn(double[] center, int k) {
		return new KNNIterator<>(this).reset(center, k);
		//The kNN search above is consistently 2x faster so we use it instead of
		//the Hjaltason/Samet algorithm below.
		//return new CoverTreeQueryKnn<>(this, center, k, dist);
	}

	private void findNearestNeighbor(Node<T> p, double[] x,
									 int k, ArrayList<PointEntryKnn<T>> candidates, double distPX) {
//		Algorithm 1 Find nearest neighbor
//		function findNearestNeighbor(cover tree p, query
//		point x, nearest neighbor so far y)
//		1: if d(p;x) < d(y;x) then
//		2: y  p
//		3: for each child q of p sorted by distance to x do
//		4: if d(y;x) > d(y;q)-maxdist(q) then
//		5: y findNearestNeighbor(q;x;y)
//		6: return y
		PointEntry<T> nn = p.point();
		if (candidates.size() < k) {
			candidates.add(new PointEntryKnn<>(nn.point(), nn.value(), distPX));
			candidates.sort(comparator);
		} else if (distPX < candidates.get(k-1).dist()) {
			candidates.remove(k-1);
			candidates.add(new PointEntryKnn<>(nn.point(), nn.value(), distPX));
			candidates.sort(comparator);
		}

		if (p.hasChildren()) {
			ArrayList<Node<T>> children = p.getChildren();
			for (int i = 0; i < children.size(); i++) {
				Node<T> q = children.get(i);
				double distCurrentWorst = candidates.get(candidates.size() - 1).dist();
				
				//Exclude children that are (compared to x) too close to the node or too far away
				//to contain any useful points.
				double distPQ = q.getDistanceToParent();
				if (distPQ+q.maxdist(this) < distPX-distCurrentWorst
						|| distPQ-q.maxdist(this)> distPX+distCurrentWorst) {
					continue;
				}
				
				//TODO cache d(y, x)
	//			if (d(y, x) > (d(y, q.point()) - q.maxdist(this))) {
	//				y = findNearestNeighbor(q, x, y);
	//			}
				double distQX = d(q.point(), x);
				nDistKNN++;
				if (distCurrentWorst > (distQX - q.maxdist(this))) {
					findNearestNeighbor(q, x, k, candidates, distQX);
				}
			}
		}
	}

	private static class KNNIterator<T> implements PointIteratorKnn<T> {

		private final CoverTree<T> tree;
		private final ArrayList<PointEntryKnn<T>> result = new ArrayList<>();
		private Iterator<PointEntryKnn<T>> iter;
		
		public KNNIterator(CoverTree<T> tree) {
			this.tree = tree;
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public PointEntryKnn<T> next() {
			return iter.next();
		}

		@Override
		public PointIteratorKnn<T> reset(double[] center, int k) {
			result.clear();
			if (tree.root != null) {
				double distPX = tree.d(tree.root.point(), center);
				tree.nDistKNN++;
				tree.findNearestNeighbor(tree.root, center, k, result, distPX);
			}
			iter = result.iterator();
			return this;
		}	
	}
	
	double d(PointEntry<?> x, PointEntry<?> y) {
		return d(x, y.point());
	}

	double d(Node<?> x, Node<?> y) {
		return d(x.point(), y.point().point());
	}

	private double d(PointEntry<?> x, double[] p2) {
		nDistCalc++;
		return dist.dist(x.point(), p2);
	}
	
	private double covdist(Node<?> p) {
		return covDist(p.getLevel());
	}

	private double covDist(int level) {
		//covdist(p) = 1:3level(p)
		return Math.pow(BASE, level);
	}

	public boolean containsExact(double[] key) {
		return queryExact(key) != null;
	}

	public void check() {
		if (root != null) {
			CTStats stats = new CTStats(this); 
			getStats(root, stats);
			if (stats.nEntries != nEntries) {
				throw new IllegalStateException("nEntries: " + stats.nEntries + " / " + nEntries);
			}
		}
	}
	
	private void getStats(Node<T> node, CTStats stats) {
		stats.nEntries++;
		stats.nNodes++;
		stats.minLevel = Math.min(stats.minLevel, node.getLevel());
		stats.maxLevel = Math.max(stats.maxLevel, node.getLevel());
		stats.maxDepth = stats.maxLevel - stats.minLevel;
		stats.sumLevel += node.getLevel();
		
		if (node.hasChildren()) {
			double maxDist = -1;
			stats.maxNodeSize = Math.max(stats.maxNodeSize, node.getChildren().size());
			for (Node<T> c : node.getChildren()) {
				double distToParent = d(node, c);
				if (distToParent != c.getDistanceToParent()) {
					throw new IllegalStateException(
							"ParentDist: " + distToParent + " / " + c.getDistanceToParent());
				}
				if (node.getLevel() != c.getLevel() + 1) {
					throw new IllegalStateException(
							"Level: " + node.getLevel() + " / " + c.getLevel());
				}
				
				getStats(c, stats);
			}
			
			maxDist = getGlobalMaxDist(node); 
			stats.sumMaxDist+=maxDist;

			if (maxDist != node.maxdist(this)) {
				throw new IllegalStateException(
						"Maxdist: " + maxDist + " / " + node.maxdist(this) +
						" Node:" + toStringNode(new StringBuilder(), node));
			}
			if (maxDist > covdist(node))  {
				throw new IllegalStateException("Maxdist/maxdist()/CovDist: " + 
						maxDist + " / " + node.maxdist(this) + " / " + covdist(node));
			}
		} else {
			stats.nLeaf++;
		}
	}
	
	private double getGlobalMaxDist(Node<T> node) {
		double currentMax = 0;
		if (node.hasChildren()) {
			for (Node<T> sub : node.getChildren()) {
				currentMax = getGlobalMaxDist(node, sub, currentMax);
			}
		}
		return currentMax;
	}
	
	private double getGlobalMaxDist(Node<T> p, Node<T> node, double currentMax) {
		double distOfThisNode = d(p, node);
		currentMax = Math.max(currentMax, distOfThisNode);
		if (node.hasChildren() && distOfThisNode + node.maxdist(this) > currentMax) {
			for (Node<T> sub : node.getChildren()) {
				currentMax = Math.max(currentMax, getGlobalMaxDist(p, sub, currentMax));
			}
		}
		return currentMax;
	}
	
	 public static class CTStats extends Stats {
		double sumMaxDist = 0;
		
		CTStats(CoverTree<?> tree) {
			super(tree.nDistCalc, tree.nDist1NN, tree.nDistKNN);
		}
		
		@Override
		public String toString() {
			return super.toString() +
					";sumMaxDist=" + sumMaxDist;
		}
	}

	Node<T> getRoot() {
		return root;
	}
	
	@Override
	public String toString() {
		return this.getClass().getSimpleName() + 
				";BASE=" + BASE +  
				";NEAREST_ANCESTOR=" + NEAREST_ANCESTOR + 
				";DistFn=" + PointDistance.getName(dist);
	}

}
