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

import ch.ethz.globis.phtree.PhTreeF;
import ch.ethz.globis.phtree.PhTreeF.*;
import ch.ethz.globis.phtree.PhTreeMultiMapF;
import org.tinspin.index.*;

/**
 * Multimap version of the PH-Tree.
 * @param <T> The value type associated with each entry.
 */
public class PHTreeMMP<T>  {

//	private final PhTreeMultiMapF<T> tree;
//
//	private PHTreeMMP(int dims) {
//		tree = PhTreeMultiMapF.create(dims);
//	}
//
//	public static <T> PHTreeMMP<T> createPHTree(int dims) {
//		return new PHTreeMMP<>(dims);
//	}
//
//	@Override
//	public int getDims() {
//		return tree.getDim();
//	}
//
//	@Override
//	public int size() {
//		return tree.size();
//	}
//
//	@Override
//	public void clear() {
//		tree.clear();
//	}
//
//	@Override
//	public Stats getStats() {
//		return new PHStats(tree.getInternalTree().getStats(), tree.getDim());
//	}
//
//	@Override
//	public int getNodeCount() {
//		return tree.getInternalTree().getStats().getNodeCount();
//	}
//
//	@Override
//	public int getDepth() {
//		return tree.getInternalTree().getStats().getBitDepth();
//	}
//
//	@Override
//	public String toStringTree() {
//		return tree.getInternalTree().toStringTree();
//	}
//
//	@Override
//	public void insert(double[] key, T value) {
//		tree.put(key, value);
//	}
//
//	@Override
//	public int removeAll(double[] point) {
//		return tree.remove(point);
//	}
//
//	@Override
//	public T update(double[] oldPoint, double[] newPoint) {
//		return tree.update(oldPoint, newPoint);
//	}
//
//	@Override
//	public QueryIterator<PointEntry<T>> query(double[] point) {
//		return tree.get(point);
//	}
//
//	@Override
//	public QueryIterator<PointEntry<T>> query(double[] min, double[] max) {
//		return new QueryIteratorPH<>(tree.query(min, max));
//	}
//
//	@Override
//	public QueryIterator<PointEntry<T>> iterator() {
//		return new IteratorPH<>(tree.queryExtent());
//	}
//
//	@Override
//	public QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k) {
//		return new QueryIteratorKnnPH<>(tree.nearestNeighbour(k, center));
//	}
//
//	private static class IteratorPH<T> implements QueryIterator<PointEntry<T>> {
//
//		private final PhIteratorF<T> iter;
//
//		private IteratorPH(PhIteratorF<T> iter) {
//			this.iter = iter;
//		}
//
//		@Override
//		public boolean hasNext() {
//			return iter.hasNext();
//		}
//
//		@Override
//		public PointEntry<T> next() {
//			//This reuses the entry object, but we have to clone the arrays...
//			PhEntryF<T> e = iter.nextEntryReuse();
//			return new EntryP<>(e.getKey().clone(), e.getValue());
//		}
//
//		@Override
//		public void reset(double[] min, double[] max) {
//			//TODO
//			throw new UnsupportedOperationException();
//		}
//
//	}
//
//	private static class QueryIteratorPH<T> implements QueryIterator<PointEntry<T>> {
//
//		private final PhQueryF<T> iter;
//
//		private QueryIteratorPH(PhQueryF<T> iter) {
//			this.iter = iter;
//		}
//
//		@Override
//		public boolean hasNext() {
//			return iter.hasNext();
//		}
//
//		@Override
//		public PointEntry<T> next() {
//			//This reuses the entry object, but we have to clone the arrays...
//			PhEntryF<T> e = iter.nextEntryReuse();
//			return new EntryP<>(e.getKey().clone(), e.getValue());
//		}
//
//		@Override
//		public void reset(double[] min, double[] max) {
//			iter.reset(min, max);
//		}
//
//	}
//
//	private static class QueryIteratorKnnPH<T> implements QueryIteratorKNN<PointEntryDist<T>> {
//
//		private final PhKnnQueryF<T> iter;
//
//		private QueryIteratorKnnPH(PhKnnQueryF<T> iter) {
//			this.iter = iter;
//		}
//
//		@Override
//		public boolean hasNext() {
//			return iter.hasNext();
//		}
//
//		@Override
//		public PointEntryDist<T> next() {
//			//This reuses the entry object, but we have to clone the arrays...
//			PhEntryDistF<T> e = iter.nextEntryReuse();
//			return new DistEntryP<>(e.getKey().clone(), e.getValue(), e.dist());
//		}
//
//		@Override
//		public QueryIteratorKnnPH<T> reset(double[] center, int k) {
//			iter.reset(k, null, center);
//			return this;
//		}
//
//	}

	
}
