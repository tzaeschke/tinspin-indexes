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

import org.tinspin.index.PointDistanceFunction;
import org.tinspin.index.PointEntry;
import org.tinspin.index.PointEntryDist;
import org.tinspin.index.PointIndex;
import org.tinspin.index.QueryIterator;
import org.tinspin.index.QueryIteratorKNN;

/**
 * A 'faster' CoverTree implementation based on the paper:
 * 
 * "Faster Cover Trees", Mike Izbicki, Christian R. Shelton,
 * Proceedings of the 32nd International Conference on Machine Learning,
 * Lille, France, 2015. JMLR: W&CP volume 37.
 * 
 * Changes over original algorithms:
 *  - findNearestNeighbour compares
 *  	if (d(y, x) > (d(_x_, q.point()) - q.maxdist(this)))
 *    instead of 
 *      if (d(y, x) > (d(_y_, q.point()) - q.maxdist(this)))
 *  - maxDist: 
 *    - lazily calculated.
 *    - calculated while excluding subbranches that don't need to be calculated
 *    - Invalidated on modifications (should speed up remove/insert)
 *  - Algorithm 2 (insert) uses BASE=1.3:
 *     BASE*covdist(p)  
 *     instead of
 *     2*covdist(p)) 
 *  - Some optimizations for kNN which are not discussed in the paper
 *  
 * Other:
 * - We also implemented the kNN algorithm by Hjaltason and Samet,
 *   but it about 2x as long as our own algorithm in all scenarios we tested.
 * TODO
 * - Rebalancing (nearest-ancestor)
 * - Merge Node/Point classes
 *     
 * @author Tilmann Zäschke
 */
public class CoverTree<T> implements PointIndex<T> {

	private final int dims;
	private int nEntries;
	
	private Node<T> root = null;
	
	private static final double DEFAULT_BASE = 2.0;
	
	private final double BASE;
	private final double LOG_BASE;
	
	private final PointDistanceFunction dist;

	
	private final double log13(double n) {
		//log_b(n) = log_e(n) / log_e(b)
		return Math.log(n) / LOG_BASE;
	}
	
	private CoverTree(int nDims, double base, PointDistanceFunction dist) {
		this.dims = nDims;
		this.BASE = base;
		this.LOG_BASE = Math.log(BASE);
		this.dist = dist != null ? dist : PointDistanceFunction.L2;
	}
		
	public static <T> Point<T> create(double[] point, T value) {
		return new Point<>(point, value);
	}

	public static <T> CoverTree<T> create(int nDims) {
		return new CoverTree<>(nDims, DEFAULT_BASE, PointDistanceFunction.L2);
	}
	
	public static <T> CoverTree<T> create(int nDims, double base, PointDistanceFunction dist) {
		return new CoverTree<>(nDims, base, dist);
	}
	
	public static <T> CoverTree<T> create(Point<T>[] data, double base, 
			PointDistanceFunction distFn) {
		if (data == null || data.length == 0) {
			throw new IllegalStateException("Bulk load with empty data no possible.");
		}
		CoverTree<T> tree = new CoverTree<>(data[0].point().length, base, distFn);
		if (data.length == 1) {
			tree.root = new Node<>(data[0], 0);
			tree.nEntries++;
			return tree;
		}
		
		//The point of bulkload is (currently) only to initialize the tree with a 
		//suitable 'level' and 'root', which should avoid any problems with having 
		//to pull up leaves to replace the root. Bulkloading thus allows a BASE < 2.0 .
		
		Point<T> newRoot = data[0];
		Point<T> mostDistantPoint = data[1];
		double largestDistance = tree.d(newRoot, mostDistantPoint); 
		for (int i = 2; i < data.length; i++) {
			Point<T> x = data[i];
			double dist = tree.d(newRoot, x);
			if (dist > largestDistance) {
				largestDistance = dist;
				mostDistantPoint = x;
			}
		}
		
		int requiredLevel = (int) (tree.log13(largestDistance) + 1);
		tree.root = new Node<T>(data[0], requiredLevel);
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
	public Stats getStats() {
		Stats stats = new Stats();
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
		//Point<T> x = new Point<>(key, value);
		Node<T> x = new Node<>(new Point<>(key, value), -1);
		if (root == null) {
			root = x.initLevel(0);
			nEntries++;
			return;
		}
		if (!root.hasChildren()) {
			double dist = d(root.point(), x);
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
	
	private Node<T> insert(Node<T> p, Node<T> x) {
//		Algorithm 2 Simplified cover tree insertion
//		function insert(cover tree p, data point x)
//		1: if d(p;x) > covdist(p) then
//		2: while d(p;x) > 2covdist(p) do
//		3: Remove any leaf q from p
//		4: p0  tree with root q and p as only child
//		5: p  p0
//		6: return tree with x as root and p as only child
//		7: return insert (p,x)
		double distPX = d(p.point(),x); 
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
				q.addChild(p, d(q.point(), p.point()));
				//5: p  p0
				p = p0;
				distPX = d(p.point(),x);
			}
			//return tree with x as root and p as only child;
			Node<T> newRoot = x.initLevel(p.getLevel() + 1);
			newRoot.addChild(p, distPX);
			//TODO set in caller?
			this.root = newRoot;
			return newRoot;
		}
		return insert2(p,x, distPX);
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
		for (int i = 0; i < children.size(); i++) {
			Node<T> q = children.get(i);
			//2: if d(q;x) <= covdist(q) then
			double distQX = d(q.point(), x); 
			if (distQX <= covdist(q)) {
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
		Point<T> result = queryExact(root, point);
		return result == null ? null : result.value();
	}
	
	private Point<T> queryExact(Node<T> p, double[] x) {
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
			Point<T> result = queryExact(q, x);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Override
	public QueryIterator<? extends PointEntry<T>> iterator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public QueryIterator<PointEntry<T>> query(double[] min, double[] max) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}
	
	@Override
	public PointEntryDist<T> query1NN(double[] center) {
		if (root == null) {
			return null;
		}
		//TODO avoid all these object creations
		Point<T> x = new Point<>(center, null);
		double distPX = d(root.point(), center);
		PointDist<T> y = new PointDist<>(root.point().point(), root.point().value(), distPX);
		findNearestNeighbor(root, x, y, distPX);
		return y;
	}

	private Point<T> findNearestNeighbor(Node<T> p, Point<T> x, final PointDist<T> y, 
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

				//TODO report: use d(y;x) > d(_X_;q)-maxdist(q)
				double distQX = d(x, q.point());
				if (y.dist() > (distQX - q.maxdist(this))) {
					findNearestNeighbor(q, x, y, distQX);
				}
			}
		}
		return y;
	}

	@Override
	public QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k) {
		return new KNNIterator<>(this).reset(center, k);
		//The kNN search above is consistently 2x faster so we use it instead of
		//the Hjaltason/Samet algorithm below.
		//return new CoverTreeQueryKnn<>(this, center, k, dist);
	}

	private void findNearestNeighbor(Node<T> p, double[] x, 
			int k, ArrayList<PointDist<T>> candidates, double distPX) {
//		Algorithm 1 Find nearest neighbor
//		function findNearestNeighbor(cover tree p, query
//		point x, nearest neighbor so far y)
//		1: if d(p;x) < d(y;x) then
//		2: y  p
//		3: for each child q of p sorted by distance to x do
//		4: if d(y;x) > d(y;q)-maxdist(q) then
//		5: y findNearestNeighbor(q;x;y)
//		6: return y
		Point<T> nn = p.point();
		if (candidates.size() < k) {
			candidates.add(new PointDist<>(nn.point(), nn.value(), distPX));
			candidates.sort(PointDist.COMPARATOR);
		} else if (distPX < candidates.get(k-1).dist()) {
			candidates.remove(k-1);
			candidates.add(new PointDist<>(nn.point(), nn.value(), distPX));
			candidates.sort(PointDist.COMPARATOR);
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
				if (distCurrentWorst > (distQX - q.maxdist(this))) {
					findNearestNeighbor(q, x, k, candidates, distQX);
				}
			}
		}
	}

	private static class KNNIterator<T> implements QueryIteratorKNN<PointEntryDist<T>> {

		private final CoverTree<T> tree;
		private final ArrayList<PointDist<T>> result = new ArrayList<>();
		private Iterator<PointDist<T>> iter;
		
		public KNNIterator(CoverTree<T> tree) {
			this.tree = tree;
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public PointEntryDist<T> next() {
			return iter.next();
		}

		@Override
		public QueryIteratorKNN<PointEntryDist<T>> reset(double[] center, int k) {
			result.clear();
			if (tree.root != null) {
				double distPX = tree.d(tree.root.point(), center);
				tree.findNearestNeighbor(tree.root, center, k, result, distPX);
			}
			iter = result.iterator();
			return this;
		}	
	}
	
	double d(Point<?> x, Point<?> y) {
		return d(x, y.point());
	}

	double d(Point<?> x, Node<?> y) {
		return d(x, y.point());
	}

	private double d(Node<?> x, Point<?> y) {
		return d(x.point(), y.point());
	}

	private double d(Node<?> x, Node<?> y) {
		return d(x.point(), y.point().point());
	}

	private double d(Point<?> x, double[] p2) {
		return dist.dist(x.point(), p2);
//		double d = 0;
//		double[] p1 = x.point();
//		for (int i = 0; i < dims; i++) {
//			double dx = p1[i] - p2[i]; 
//			d += dx * dx;
//		}
//		return Math.sqrt(d);
	}
	
	private double covdist(Node<?> p) {
		//covdist(p) = 1:3level(p)
		return Math.pow(BASE, p.getLevel());
	}

	public boolean containsExact(double[] key) {
		return queryExact(key) != null;
	}

	public void check() {
		if (root != null) {
			Stats stats = new Stats(); 
			getStats(root, stats);
			if (stats.nEntries != nEntries) {
				throw new IllegalStateException("nEntries: " + stats.nEntries + " / " + nEntries);
			}
		}
	}
	
	private void getStats(Node<T> node, Stats stats) {
		stats.nEntries++;
		stats.minLevel = Math.min(stats.minLevel, node.getLevel());
		
		if (node.hasChildren()) {
			double maxDist = -1;
			stats.maxNodeSize = Math.max(stats.maxNodeSize, node.getChildren().size());
			for (Node<T> c : node.getChildren()) {
				double distToParent = d(node.point(), c.point());
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
			
			if (maxDist != node.maxdist(this)) {
				throw new IllegalStateException(
						"Maxdist: " + maxDist + " / " + node.maxdist(this) +
						" Node:" + toStringNode(new StringBuilder(), node));
			}
			if (maxDist > covdist(node))  {
				throw new IllegalStateException("Maxdist/maxdist()/CovDist: " + 
						maxDist + " / " + node.maxdist(this) + " / " + covdist(node));
			}
		}
	}
	
	private double getGlobalMaxDist(Node<T> node) {
		double currentMax = 0;
		Point<T> p = node.point();
		if (node.hasChildren()) {
			for (Node<T> sub : node.getChildren()) {
				currentMax = getGlobalMaxDist(p, sub, currentMax);
			}
		}
		return currentMax;
	}
	
	private double getGlobalMaxDist(Point<T> p, Node<T> node, double currentMax) {
		double distOfThisNode = d(p, node.point());
		currentMax = Math.max(currentMax, distOfThisNode);
		if (node.hasChildren() && distOfThisNode + node.maxdist(this) > currentMax) {
			for (Node<T> sub : node.getChildren()) {
				currentMax = Math.max(currentMax, getGlobalMaxDist(p, sub, currentMax));
			}
		}
		return currentMax;
	}
	
	 static class Stats {
		int nEntries = 0;
		int minLevel = Integer.MAX_VALUE;
		int maxNodeSize = -1;
		
	}

	Node<T> getRoot() {
		return root;
	}
}
