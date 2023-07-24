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

import org.tinspin.index.PointMap;
import org.tinspin.index.Stats;

import ch.ethz.globis.phtree.PhTreeF;
import ch.ethz.globis.phtree.PhTreeF.PhEntryDistF;
import ch.ethz.globis.phtree.PhTreeF.PhEntryF;
import ch.ethz.globis.phtree.PhTreeF.PhIteratorF;
import ch.ethz.globis.phtree.PhTreeF.PhKnnQueryF;
import ch.ethz.globis.phtree.PhTreeF.PhQueryF;

public class PHTreeP<T> implements PointMap<T> {

	private final PhTreeF<T> tree;
	
	private PHTreeP(int dims) {
		tree = PhTreeF.create(dims);
	} 
	
	public static <T> PHTreeP<T> createPHTree(int dims) {
		return new PHTreeP<>(dims);
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
		return new PHStats(tree.getInternalTree().getStats(), tree.getDim());
	}

	@Override
	public int getNodeCount() {
		return tree.getInternalTree().getStats().getNodeCount();
	}

	@Override
	public int getDepth() {
		return tree.getInternalTree().getStats().getBitDepth();
	}

	@Override
	public String toStringTree() {
		return tree.getInternalTree().toStringTree();
	}

	@Override
	public void insert(double[] key, T value) {
		tree.put(key, value);
	}

	@Override
	public T remove(double[] point) {
		return tree.remove(point);
	}

	@Override
	public T update(double[] oldPoint, double[] newPoint) {
		return tree.update(oldPoint, newPoint);
	}

	@Override
	public boolean contains(double[] key) {
		return tree.contains(key);
	}

	@Override
	public T queryExact(double[] point) {
		return tree.get(point);
	}

	@Override
	public PointIterator<T> query(double[] min, double[] max) {
		return new QueryIteratorPH<>(tree.query(min, max));
	}

	@Override
	public PointIterator<T> iterator() {
		return new ExtentWrapper();
	}

	@Override
	public PointIteratorKnn<T> queryKnn(double[] center, int k) {
		return new QueryIteratorKnnPH<>(tree.nearestNeighbour(k, center));
	}

	private class ExtentWrapper implements PointIterator<T> {

		private PhIteratorF<T> iter;
		
		private ExtentWrapper() {
			reset(null, null);
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public PointEntry<T> next() {
			//This reuses the entry object, but we have to clone the arrays...
			PhEntryF<T> e = iter.nextEntryReuse();
			return new PointEntry<>(e.getKey().clone(), e.getValue());
		}

		@Override
		public PointIterator<T> reset(double[] min, double[] max) {
			if (min != null || max != null) {
				throw new UnsupportedOperationException("min/max must be `null`");
			}
			iter = tree.queryExtent();
			return this;
		}
	}
	
	private static class QueryIteratorPH<T> implements PointIterator<T> {

		private final PhQueryF<T> iter;
		
		private QueryIteratorPH(PhQueryF<T> iter) {
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
			return new PointEntry<>(e.getKey().clone(), e.getValue());
		}

		@Override
		public QueryIterator<PointEntry<T>> reset(double[] min, double[] max) {
			iter.reset(min, max);
			return this;
		}
		
	}
	
	private static class QueryIteratorKnnPH<T> implements PointIteratorKnn<T> {

		private final PhKnnQueryF<T> iter;
		
		private QueryIteratorKnnPH(PhKnnQueryF<T> iter) {
			this.iter = iter;
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public PointEntryKnn<T> next() {
			//This reuses the entry object, but we have to clone the arrays...
			PhEntryDistF<T> e = iter.nextEntryReuse();
			return new PointEntryKnn<>(e.getKey().clone(), e.getValue(), e.dist());
		}

		@Override
		public QueryIteratorKnnPH<T> reset(double[] center, int k) {
			iter.reset(k, null, center);
			return this;
		}
		
	}

	
}
