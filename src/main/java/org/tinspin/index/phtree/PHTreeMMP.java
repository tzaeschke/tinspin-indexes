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
package org.tinspin.index.phtree;

import ch.ethz.globis.phtree.PhEntryDistF;
import ch.ethz.globis.phtree.PhEntryF;
import ch.ethz.globis.phtree.PhTreeMultiMapF2;
import org.tinspin.index.*;

import java.util.Iterator;
import java.util.function.Predicate;

/**
 * Multimap version of the PH-Tree.
 * @param <T> The value type associated with each entry.
 */
public class PHTreeMMP<T> implements PointIndexMM<T> {

	private final PhTreeMultiMapF2<T> tree;

	private PHTreeMMP(int dims) {
		this.tree = PhTreeMultiMapF2.create(dims);
	}

	public static <T> PHTreeMMP<T> create(int dims) {
		return new PHTreeMMP<>(dims);
	}

	@Override
	public int getDims() {
		return tree.getDim();
	}

	@Override
	public int size() {
		return tree.size();
	}

	@Override
	public void clear() {
		tree.clear();
	}

	@Override
	public Stats getStats() {
		return new PHStats(tree.getStats(), tree.getDim());
	}

	@Override
	public int getNodeCount() {
		return tree.getStats().getNodeCount();
	}

	@Override
	public int getDepth() {
		return tree.getStats().getBitDepth();
	}

	@Override
	public String toStringTree() {
		return tree.toStringTree(); // TODO
	}

	@Override
	public void insert(double[] key, T value) {
		tree.put(key, value);
	}

	@Override
	public boolean remove(double[] key, T value) {
		return tree.remove(key, value);
	}

	@Override
	public boolean removeIf(double[] point, Predicate<PointEntry<T>> condition) {
		for (T t : tree.get(point)) {
			if (condition.test(new EntryP<>(point, t))) {
				return tree.remove(point, t);
			}
		}
		return false;
	}

	@Override
	public boolean update(double[] oldPoint, double[] newPoint, T value) {
		return tree.update(oldPoint, value, newPoint);
	}

	@Override
	public QueryIterator<PointEntry<T>> query(double[] key) {
		return new IteratorPlain(key, tree.get(key).iterator());
	}

	@Override
	public QueryIterator<PointEntry<T>> query(double[] min, double[] max) {
		return new IteratorPH<>(tree.query(min, max));
	}

	@Override
	public QueryIterator<PointEntry<T>> iterator() {
		return new IteratorPH<>(tree.queryExtent());
	}

	@Override
	public QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k) {
		return new QueryIteratorKnnPH<>(tree.nearestNeighbour(k, center));
	}

	@Override
	public QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k, PointDistanceFunction distFn) {
		return null;
	}

	private static class IteratorPH<T> implements QueryIterator<PointEntry<T>> {

		private final PhTreeMultiMapF2.PhIteratorF<T> iter;

		private IteratorPH(PhTreeMultiMapF2.PhIteratorF<T> iter) {
			this.iter = iter;
		}

		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public PointEntry<T> next() {
			//This reuses the entry object, but we have to clone the arrays...
			PhEntryF<T> e = iter.nextEntryReuse();
			return new EntryP<>(e.getKey().clone(), e.getValue());
		}

		@Override
		public void reset(double[] min, double[] max) {
			//TODO
			throw new UnsupportedOperationException();
		}

	}

	private static class IteratorPlain<T> implements QueryIterator<PointEntry<T>> {

		private final Iterator<T> iter;
		private final double[] key;

		private IteratorPlain(double[] key, Iterator<T> iter) {
			this.iter = iter;
			this.key = key;
		}

		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public PointEntry<T> next() {
			return new EntryP<>(key, iter.next());
		}

		@Override
		public void reset(double[] min, double[] max) {
			throw new UnsupportedOperationException();
		}
	}

	private static class QueryIteratorKnnPH<T> implements QueryIteratorKNN<PointEntryDist<T>> {

		private final PhTreeMultiMapF2.PhKnnQueryF<T> iter;

		private QueryIteratorKnnPH(PhTreeMultiMapF2.PhKnnQueryF<T> iter) {
			this.iter = iter;
		}

		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public PointEntryDist<T> next() {
			//This reuses the entry object, but we have to clone the arrays...
			PhEntryDistF<T> e = iter.nextEntryReuse();
			return new DistEntryP<>(e.getKey().clone(), e.getValue(), e.dist());
		}

		@Override
		public QueryIteratorKnnPH<T> reset(double[] center, int k) {
			iter.reset(k, null, center);
			return this;
		}

	}

}
