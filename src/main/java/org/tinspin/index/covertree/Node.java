package org.tinspin.index.covertree;

import java.util.ArrayList;
import java.util.Comparator;

public class Node<T> {

	private static final Comparator<Node<?>> COMPARATOR = (o1, o2) -> 
			Double.compare(o1.distToParent,  o2.distToParent);

	
	private Point<T> p;
	private ArrayList<Node<T>> children;
	private int level;
	private double distToParent;
	private double maxDist = -1;
	
	Node(Point<T> p, int level) {
		this.p = p;
		this.level = level;
	}

	public Point<T> point() {
		return p;
	}
	
	void setDistanceToParent(double d) {
		this.distToParent = d;
	}
	
	double getDistanceToParent() {
		return this.distToParent;
	}
	
	public void addChild(Node<T> node, double distToParent) {
		if (children == null) {
			children = new ArrayList<>();
		}
		node.distToParent = distToParent;
		if (node.hasChildren()) {
			//TODO if (node.distToParent + node.maxDist() > maxDist) {
			maxDist = -1; //Needs recalc
			//}
		} else if (distToParent > maxDist) {
			maxDist = distToParent;
		}
		children.add(node);
		children.sort(Node.COMPARATOR);
	}

	public void replaceChild(int i, Node<T> qNew) {
		//TODO update level or distance????
		//TODO update maxDIst?
		children.set(i, qNew);
	}

	public ArrayList<Node<T>> getChildren() {
		return children;
	}

	public ArrayList<Node<T>> getOrCreateChildren() {
		if (children == null) {
			children = new ArrayList<>();
		}
		return children;
	}

	public boolean hasChildren() {
		return children != null && !children.isEmpty(); 
	}
	
	public Node<T> removeAnyLeaf() {
		//TODO closest?
		Node<T> any = children.get(0);
		if (any.children != null && !any.children.isEmpty()) {
			//TODO only invalidate if maxDist == d(any, point);
			maxDist = -1;
			return any.removeAnyLeaf();
		}
		//Not cheap, but better than resorting or returning the furthest child.
		//TODO return farthest child?
		Node<T> leaf = children.remove(0);
		if (children.isEmpty()) {
			maxDist = 0;
			//TODO return list to pool
		}
		return leaf;
	}

	public double maxdist(CoverTree<T> tree) {
		if (maxDist == -1) {
			maxDist = recalcMaxDist(point(), this, tree);
		}
		return maxDist;
	}

	void adjustMaxDist(double newDist) {
		maxDist = Math.max(newDist, maxDist); 
	}
	
	private static <T> double recalcMaxDist(Point<T> p, Node<T> node, CoverTree<T> tree) {
		double maxDist = 0;
		if (node.children != null) {
			for (int i = 0; i < node.children.size(); i++) {
				Node<T> child = node.children.get(i);
				//TODO on level 'x-1' this is == distToParent....
				double distChild = tree.d(p, child.point());
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


}
