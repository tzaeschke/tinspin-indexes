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
package org.tinspin.index.covertree;

import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.Iterator;

import org.tinspin.index.PointDistance;

import static org.tinspin.index.Index.*;


/**
 * kNN search.
 * 
 * Implementation after Hjaltason and Samet.
 * G. R. Hjaltason and H. Samet., "Distance browsing in spatial databases.", ACM TODS 24(2):265--318. 1999
 * 
 * @author Tilmann Zäschke
 *
 * @param <T> Type
 */
public class CoverTreeQueryKnn<T> implements PointIteratorKnn<T> {
	
	private final Comparator<PointDist<?>> COMP = PointDist.COMPARATOR;
	private final CoverTree<T> tree;
	private double[] center;
	private Iterator<PointDist<T>> iter;
	private PointDistance dist;
	private final ArrayList<PointDist<T>> candidates = new ArrayList<>();
	private final ArrayList<PointDist<Object>> pool = new ArrayList<>();
	private final PriorityQueue<PointDist<Object>> queue = new PriorityQueue<>(COMP);
	
	
	public CoverTreeQueryKnn(CoverTree<T> tree, double[] center, int k, 
			PointDistance dist) {
		this.tree = tree;
		reset(center, k, dist == null ? PointDistance.L2 : dist);
	}

	
	@Override
	public CoverTreeQueryKnn<T> reset(double[] center, int k) {
		reset(center, k, null);
		return this;
	}
	
	
	public void reset(double[] center, int k, PointDistance dist) {
		if (dist != null) {
			this.dist = dist;
		}
		if (this.dist != PointDistance.L2) {
			System.err.println("This distance iterator only works for L2 distance");
		}
		this.center = center;
		
		//reset
		pool.addAll(queue);
		queue.clear();
		candidates.clear();
		candidates.ensureCapacity(k);

		//handle 0 cases
		if (k <= 0 || tree.size() == 0) {
			iter = candidates.iterator();
			return;
		}

		//search
		search(k);
		iter = candidates.iterator();
		//System.out.println("Queue size: " + queue.size());
	}
	
	
	@SuppressWarnings("unchecked")
	private void search(int k) {
		//Initialize queue
		addToQueue(tree.getRoot());

		while (!queue.isEmpty()) {
			PointDist<Object> candidate = queue.poll();
			Object o = candidate.value();
			if (!(o instanceof Node)) {
				//data entry
				candidates.add((PointDist<T>) candidate);
				if (candidates.size() >= k) {
					return;
				}
			} else {
				//node
				ArrayList<Node<T>> entries = ((Node<T>)o).getChildren();
				if (entries != null) {
					for (int i = 0; i < entries.size(); i++) {
						addToQueue( entries.get(i) );
					}
				}
				pool.add(candidate);
			}				
		}
	}
	
	private void addToQueue(Node<T> node) {
		double dRootPoint = dist.dist(center, node.point());
		double maxDist = node.maxdist(tree);
		double dRootNode = maxDist > dRootPoint ? 0 : (dRootPoint-maxDist);
		queue.add(createEntry(node.point().point(), node, dRootNode));
		queue.add(createEntry(node.point().point(), node.point(), dRootPoint));
	}
	
	/**
	 * 
	 * @param data vector
	 * @param val Value, can be Node<T> or T
	 * @param dist distance
	 * @return PointDist<Object>
	 */
	private PointDist<Object> createEntry(double[] data, Object val, double dist) {
		if (pool.isEmpty()) {
			return new PointDist<>(data, val, dist);
		}
		PointDist<Object> e = pool.remove(pool.size() - 1);
		e.set(data, val, dist);
		return e;
	}

	@Override
	public boolean hasNext() {
		return iter.hasNext();
	}

	
	@Override
	public PointDist<T> next() {
		return iter.next();
	}
}
