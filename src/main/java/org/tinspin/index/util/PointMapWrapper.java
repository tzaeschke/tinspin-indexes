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
package org.tinspin.index.util;

import org.tinspin.index.*;
import org.tinspin.index.rtree.RTreeEntry;
import org.tinspin.index.rtree.RTree;

public class PointMapWrapper<T> implements PointMap<T> {

	private final BoxMap<T> ind;
	
	private PointMapWrapper(BoxMap<T> ind) {
		this.ind = ind;
	}
	
	public static <T> PointMap<T> create(BoxMap<T> ind) {
		return new PointMapWrapper<>(ind);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public PointIterator<T> iterator() {
		return new PointIter(ind.iterator());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public PointIterator<T> query(double[] min, double[] max) {
		return new PointIter(ind.queryIntersect(min, max));
	}

	private static class PointIter<T> implements PointIterator<T> {

		private final BoxIterator<T> it;
		
		PointIter(BoxIterator<T> it) {
			this.it = it;
		}
		
		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public PointEntry<T> next() {
			BoxEntry<T> e = it.next();
			return new PointEntry<>(e.min(), e.value());
		}

		@Override
		public PointIterator<T> reset(double[] min, double[] max) {
			it.reset(min, max);
			return this;
		}
	}
	
	@Override
	public PointEntryKnn<T> query1nn(double[] center) {
		BoxEntryKnn<T> r = ind.query1nn(center);
		return new PointEntryKnn<>(r.min(), r.value(), r.dist());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public PointIteratorKnn<T> queryKnn(double[] center, int k) {
		return new PointDIter(ind.queryKnn(center, k));
	}

	private static class PointDIter<T> implements PointIteratorKnn<T> {

		private final BoxIteratorKnn<T> it;
		
		PointDIter(BoxIteratorKnn<T> it) {
			this.it = it;
		}
		
		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public PointEntryKnn<T> next() {
			BoxEntryKnn<T> e = it.next();
			return new PointEntryKnn<>(e.min(), e.value(), e.dist());
		}

		@Override
		public PointDIter<T> reset(double[] center, int k) {
			it.reset(center, k);
			return this;
		}
	}
	
	@Override
	public void insert(double[] key, T value) {
		ind.insert(key, key, value);
	}

	@Override
	public T remove(double[] point) {
		return ind.remove(point, point);
	}

	@Override
	public T update(double[] oldPoint, double[] newPoint) {
		return ind.update(oldPoint, oldPoint, newPoint, newPoint);
	}

	@Override
	public boolean contains(double[] key) {
		return ind.queryExact(key, key) != null;
	}

	@Override
	public T queryExact(double[] point) {
		return ind.queryExact(point, point);
	}

	@Override
	public int getDims() {
		return ind.getDims();
	}

	@Override
	public int size() {
		return ind.size();
	}

	@Override
	public void clear() {
		ind.clear();
	}

	@Override
	public Stats getStats() {
		return ind.getStats();
	}

	@Override
	public int getNodeCount() {
		return ind.getNodeCount();
	}

	@Override
	public int getDepth() {
		return ind.getDepth();
	}

	public void load(RTreeEntry<T>[] entries) {
		if (!(ind instanceof RTree)) {
			throw new UnsupportedOperationException(
					"Bulkloading is only supported for RTrees");
		}
		((RTree<T>)ind).load(entries);
	}

	@Override
	public String toStringTree() {
		return ind.toStringTree();
	}
}
