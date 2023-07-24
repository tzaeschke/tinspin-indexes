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

import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Iterator;

import org.tinspin.index.BoxDistance;

import static org.tinspin.index.Index.*;

/**
 * kNN search with EDGE distance and presorting of entries.
 * <p>
 * Implementation after Hjaltason and Samet (with some deviations: no MinDist or MaxDist used).
 * G. R. Hjaltason and H. Samet., "Distance browsing in spatial databases.", ACM TODS 24(2):265--318. 1999
 * 
 * @author Tilmann Zäschke
 *
 * @param <T> Type Value type.
 */
public class RTreeQueryKnn<T> implements BoxIteratorKnn<T> {
	
	private static final BEComparator COMP = new BEComparator();
	private final RTree<T> tree;
	private double[] center;
	private Iterator<BoxEntryKnn<T>> iter;
	private BoxDistance dist;
	private final ArrayList<BoxEntryKnn<T>> candidates = new ArrayList<>();
	private final ArrayList<BoxEntryKnn<Object>> pool = new ArrayList<>();
	private final PriorityQueue<BoxEntryKnn<Object>> queue = new PriorityQueue<>(COMP);
	
	
	public RTreeQueryKnn(RTree<T> tree, double[] center, int k, BoxDistance dist) {
		this.tree = tree;
		reset(center, k, dist == null ? BoxDistance.EDGE : dist);
	}

	
	@Override
	public RTreeQueryKnn<T> reset(double[] center, int k) {
		reset(center, k, null);
		return this;
	}

	public void reset(double[] center, int k, BoxDistance dist) {
		if (dist != null) {
			this.dist = dist;
		}
		if (this.dist != BoxDistance.EDGE) {
			System.err.println("This distance iterator only works for EDGE distance");
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
	}
	
	
	@SuppressWarnings("unchecked")
	private void search(int k) {
		//Initialize queue
		RTreeNode<T> eRoot = tree.getRoot();
		double dRoot = dist(center, eRoot.min(), eRoot.max());
		queue.add(createEntry(eRoot.min(), eRoot.max(), eRoot, dRoot));

		while (!queue.isEmpty()) {
			BoxEntryKnn<Object> candidate = queue.poll();
			Object o = candidate.value();
			if (!(o instanceof RTreeNode)) {
				//data entry
				candidates.add((BoxEntryKnn<T>) candidate);
				if (candidates.size() >= k) {
					return;
				}
			} else if (o instanceof RTreeNodeLeaf) {
				//leaf node
				ArrayList<Entry<T>> entries = ((RTreeNodeLeaf<T>)o).getEntries();
				for (int i = 0; i < entries.size(); i++) {
					Entry<T> e2 = entries.get(i);
					double d = dist(center, e2.min(), e2.max());
					queue.add(createEntry(e2.min(), e2.max(), e2.value(), d));
				}
				pool.add(candidate);
			} else {
				//inner node
				ArrayList<RTreeNode<T>> entries = ((RTreeNodeDir<T>)o).getChildren();
				for (int i = 0; i < entries.size(); i++) {
					RTreeNode<T> e2 = entries.get(i);
					double d = dist(center, e2.min(), e2.max());
					queue.add(createEntry(e2.min(), e2.max(), e2, d));
				}
				pool.add(candidate);
			}				
		}
	}
	
	private BoxEntryKnn<Object> createEntry(double[] min, double[] max, Object val, double dist) {
		if (pool.isEmpty()) {
			return new BoxEntryKnn<>(min, max, val, dist);
		}
		BoxEntryKnn<Object> e = pool.remove(pool.size() - 1);
		e.set(min, max, val, dist);
		return e;
	}

	@Override
	public boolean hasNext() {
		return iter.hasNext();
	}

	
	@Override
	public BoxEntryKnn<T> next() {
		return iter.next();
	}
	
	private double dist(double[] center, double[] min, double[] max) {
		tree.incNDistKNN();
		return dist.dist(center, min, max);
	}
}
