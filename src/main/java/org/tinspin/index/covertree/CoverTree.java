package org.tinspin.index.covertree;

import java.util.ArrayList;
import java.util.Arrays;

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
 * @author Tilmann Zäschke
 */
public class CoverTree<T> implements PointIndex<T> {

	private final int dims;
	private int nEntries;
	private int nNodes;
	
	private Node<T> root = null;
	
	private static final double BASE = 1.3;
	private static final double LOG_BASE = Math.log(BASE); 
	
	private static final double log13(double n) {
		//log_b(n) = log_e(n) / log_e(b)
		return Math.log(n) / LOG_BASE;
	}
	
	
	private CoverTree(int nDims) {
		this.dims = nDims;
	}
	
	public static <T> CoverTree<T> create(int nDims) {
		return new CoverTree<>(nDims);
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
		throw new UnsupportedOperationException();
	}

	@Override
	public Stats getStats() {
		Stats stats = new Stats();
		if (root != null) {
			getStats(root, root.getLevel()+1, stats);
		}
		return stats;
	}

	@Override
	public int getNodeCount() {
		return nNodes;
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
		StringBuilder sb = new StringBuilder();
		if (root != null) {
			toStringTree(sb, root);
		}
		return sb.toString();
	}

	private void toStringTree(StringBuilder sb, Node<T> node) {
		for (int i = root.getLevel(); i > node.getLevel(); i--) {
			sb.append(".");
		}
		sb.append("L=").append(node.getLevel());
		sb.append(";MaxD=").append(node.maxdist(this));
		sb.append(";ParD=").append(node.getDistanceToParent());
		if (node.hasChildren()) {
			sb.append(";nC=").append(node.getChildren().size());
		} else {
			sb.append(";nC=0");
		}
		sb.append(";coord=").append(Arrays.toString(node.point().point()));
		sb.append("\n");
		if (node.hasChildren()) {
			for (Node<T> c : node.getChildren()) {
				toStringTree(sb, c);
			}
		}
	}
	
	@Override
	public void insert(double[] key, T value) {
		System.out.println("Inserting(" + nEntries + "): " + Arrays.toString(key));
		Point<T> x = new Point<>(key, value);
		if (root == null) {
			root = new Node<>(x, Double.NaN);
			nNodes++;
			nEntries++;
			return;
		}
		if (!root.hasChildren()) {
			double dist = d(root.point(), x);
			Node<T> q = new Node<>(x, dist);
			root.addChild(q);
			//initialize levels from current distance
			int level = (int) log13(dist);
			root.setLevel(level + 1);
			q.setLevel(level);
			nNodes++;
			nEntries++;
			return;
		}
		insert(root, x);
		nNodes++;
		nEntries++;
	}
	
	private Node<T> insert(Node<T> p, Point<T> x) {
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
			while (d(p.point(), x) > 2*covdist(p)) {
				//3: Remove any leaf q from p
				Node<T> q = p.removeAnyLeaf();
				//4: p0 = tree with root q and p as only child
				Node<T> p0 = q;
				q.setLevel(p.getLevel() + 1);
				q.addChild(p);
				//TODO maxDist?
				p.setDistanceToParent(Double.NaN);
				//5: p  p0
				p = p0;
				distPX = d(p.point(),x);
			}
			//return tree with x as root and p as only child;
			Node<T> newRoot = new Node<>(x, Double.NaN);
			newRoot.setLevel(p.getLevel() + 1);
			p.setDistanceToParent(distPX);
			newRoot.addChild(p);
			//TODO set in caller?
			this.root = newRoot;
			return newRoot;
		}
		return insert2(p,x, distPX);
	}

	private Node<T> insert2(Node<T> p, Point<T> x, double distPX) { 
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
			if (distQX <= covdist(p)) {
				//3: q0  insert (q;x)
				Node<T> qNew = insert2(q, x, distQX);
				//4: p0   p with child q replaced with q0
				p.replaceChild(i, qNew); 
				//5: return p0
				return p;
			}
		}
		//6: return p with x added as a child
		p.addChild(new Node<>(x, distPX));
		return p;
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
		//TODO avoid this
		Point<T> x = new Point<>(center, null);
		Point<T> nn = findNearestNeighbor(root, x, root.point());
		//TODO avoid creation and d(x, z) 
		return new PointDist<>(nn.point(), nn.value(), d(nn, x));
	}

	private Point<T> findNearestNeighbor(Node<T> p, Point<T> x, Point<T> y) {
//		Algorithm 1 Find nearest neighbor
//		function findNearestNeighbor(cover tree p, query
//		point x, nearest neighbor so far y)
//		1: if d(p;x) < d(y;x) then
//		2: y  p
//		3: for each child q of p sorted by distance to x do
//		4: if d(y;x) > d(y;q)-maxdist(q) then
//		5: y findNearestNeighbor(q;x;y)
//		6: return y
		if (d(p.point(), x) < d(y,x)) {
			y = p.point();
		}

		ArrayList<Node<T>> children = p.getChildren();
		for (int i = 0; i < children.size(); i++) {
			Node<T> q = children.get(i);
			//TODO cache d(y, x)
			if (d(y, x) > (d(y, q.point()) - q.maxdist(this))) {
				y = findNearestNeighbor(q, x, y);
			}
		}
		return y;
	}

	@Override
	public QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	private void findNearestNeighbor(Node<T> p, Point<T> x, 
			int k, ArrayList<PointDist<T>> candidates) {
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
		double distNN = d(nn, x);
//		if (candidates.size() < k) {
//			candidates.add(new PointDist<>(nn.point(), nn.value(), distNN));
//			candidates.sort(c);
//		} else if (distNN < candidates.get(k-1).dist()) {
//			candidates.remove(k-1);
//			candidates.add(new PointDist<>(nn.point(), nn.value(), distNN));
//			candidates.sort(c);
//		}
//
//		ArrayList<Node<T>> children = p.getChildren();
//		for (int i = 0; i < children.size(); i++) {
//			Node<T> q = children.get(i);
//			//TODO cache d(y, x)
//			if (d(y, x) > (d(y, q.point()) - q.maxdist(this))) {
//				y = findNearestNeighbor(q, x, y);
//			}
//		}
	}

	double d(Point<?> x, Point<?> y) {
		return d(x, y.point());
	}

	private double d(Point<?> x, double[] p2) {
		double d = 0;
		double[] p1 = x.point();
		for (int i = 0; i < dims; i++) {
			d += p1[i] * p1[i] + p2[i] * p2[i];
		}
		return Math.sqrt(d);
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
			getStats(root, root.getLevel() + 1, stats);
		}
	}
	
	private void getStats(Node<T> node, int parentLevel, Stats stats) {
		if (node.getLevel() != parentLevel - 1) {
			throw new IllegalStateException("Level: " + parentLevel + " / " + node.getLevel());
		}
		stats.nEntries++;
		stats.minLevel = Math.min(stats.minLevel, node.getLevel());
		
		if (node.hasChildren()) {
			double maxDist = -1;
			stats.maxNodeSize = Math.max(stats.maxNodeSize, node.getChildren().size());
			for (Node<T> c : node.getChildren()) {
				maxDist = Math.max(maxDist, c.maxdist(this));
				getStats(c, node.getLevel(), stats);
			}
			//TODO !=
			if (maxDist > node.maxdist(this)) {
				throw new IllegalStateException("Maxdist: " + maxDist + " / " + node.maxdist(this));
			}
			if (maxDist > covdist(node))  {
				throw new IllegalStateException();
			}
		}
	}
	
	 static class Stats {
		int nEntries = 0;
		int minLevel = Integer.MAX_VALUE;
		int maxNodeSize = -1;
		
	}
}
