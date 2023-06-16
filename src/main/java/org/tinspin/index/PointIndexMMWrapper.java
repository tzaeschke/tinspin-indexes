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
package org.tinspin.index;

import org.tinspin.index.rtree.Entry;
import org.tinspin.index.rtree.RTree;

import java.util.function.Predicate;

public class PointIndexMMWrapper<T> implements PointIndexMM<T> {

	private final RectangleIndexMM<T> ind;

	private PointIndexMMWrapper(RectangleIndexMM<T> ind) {
		this.ind = ind;
	}
	
	public static <T> PointIndexMM<T> create(RectangleIndexMM<T> ind) {
		return new PointIndexMMWrapper<>(ind);
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

		private final double[] point;
		private final T value;
		
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
			return new PointW<>(e.lower(), e.value());
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

	@Override
	public QueryIteratorKNN<PointEntryDist<T>> queryKNN(
			double[] center, int k, PointDistanceFunction distFn, Predicate<T> filterFn) {
		RectangleDistanceFunction.EdgeDistance fn = new RectangleDistanceFunction.EdgeDistance(distFn);
		return new PointDIter<>(ind.queryKNN(center, k, fn::edgeDistance, filterFn));
	}

	private static class PointDistW<T> extends PointW<T> implements PointEntryDist<T> {

		private final double dist;
		
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
	public boolean remove(double[] point, T value) {
		return ind.remove(point, point, value);
	}

	@Override
	public boolean removeIf(double[] point, Predicate<PointEntry<T>> condition) {
		return ind.removeIf(point, point, e -> condition.test(new PointW<>(e.lower(), e.value())));
	}

	@Override
	public QueryIterator<PointEntry<T>> query(double[] point) {
		return new PointIter<>(ind.queryRectangle(point, point));
	}

	@Override
	public boolean update(double[] oldPoint, double[] newPoint, T value) {
		return ind.update(oldPoint, oldPoint, newPoint, newPoint, value);
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

	public void load(Entry<T>[] entries) {
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
