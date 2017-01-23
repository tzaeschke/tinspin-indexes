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
package org.tinspin.index;

import org.tinspin.index.rtree.Entry;
import org.tinspin.index.rtree.RTree;

public class PointIndexWrapper<T> implements PointIndex<T> {

	private final RectangleIndex<T> ind;
	
	private PointIndexWrapper(RectangleIndex<T> ind) {
		this.ind = ind;
	}
	
	public static <T> PointIndex<T> create(RectangleIndex<T> ind) {
		return new PointIndexWrapper<T>(ind);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public QueryIterator<PointEntry<T>> iterator() {
		return new PointIter(ind.iterator());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public QueryIterator<PointEntry<T>> query(double[] min, double[] max) {
		return new PointIter(ind.queryIntersect(min, max));
	}

	private static class PointW<T> implements PointEntry<T> {

		private double[] point;
		private T value;
		
		PointW(double[] point, T value) {
			this.point = point;
			this.value = value;
		}
		
		@Override
		public double[] point() {
			return point;
		}

		@Override
		public T value() {
			return value;
		}
		
	}
	
	private static class PointIter<T> implements QueryIterator<PointEntry<T>> {

		private final QueryIterator<RectangleEntry<T>> it;
		
		PointIter(QueryIterator<RectangleEntry<T>> it) {
			this.it = it;
		}
		
		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public PointEntry<T> next() {
			RectangleEntry<T> e = it.next();
			return new PointW<T>(e.lower(), e.value());
		}

		@Override
		public void reset(double[] min, double[] max) {
			it.reset(min, max);
		}
	}
	
	@Override
	public PointEntryDist<T> query1NN(double[] center) {
		RectangleEntryDist<T> r = ind.query1NN(center);
		return new PointDistW<>(r.lower(), r.value(), r.dist());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k) {
		return new PointDIter(ind.queryKNN(center, k));
	}

	private static class PointDistW<T> extends PointW<T> implements PointEntryDist<T> {

		private double dist;
		
		PointDistW(double[] point, T value, double dist) {
			super(point, value);
			this.dist = dist;
		}
		
		@Override
		public double dist() {
			return dist;
		}
	}
	
	private static class PointDIter<T> implements QueryIteratorKNN<PointEntryDist<T>> {

		private final QueryIteratorKNN<RectangleEntryDist<T>> it;
		
		PointDIter(QueryIteratorKNN<RectangleEntryDist<T>> it) {
			this.it = it;
		}
		
		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public PointEntryDist<T> next() {
			RectangleEntryDist<T> e = it.next();
			return new PointDistW<>(e.lower(), e.value(), e.dist());
		}

		@Override
		public void reset(double[] center, int k) {
			it.reset(center, k);
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
	public Object getStats() {
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

	public void load(Entry<T>[] entries) {
		if (!(ind instanceof RTree)) {
			throw new UnsupportedOperationException(
					"Bulkloading is only supported for RTrees");
		}
		((RTree<T>)ind).load(entries);
	}
}
