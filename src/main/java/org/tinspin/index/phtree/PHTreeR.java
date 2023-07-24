/*
 * Copyright 2017 Tilmann Zaeschke
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

import org.tinspin.index.BoxMap;

import ch.ethz.globis.phtree.PhTreeSolidF;
import ch.ethz.globis.phtree.PhTreeSolidF.PhEntryDistSF;
import ch.ethz.globis.phtree.PhTreeSolidF.PhEntrySF;
import ch.ethz.globis.phtree.PhTreeSolidF.PhIteratorSF;
import ch.ethz.globis.phtree.PhTreeSolidF.PhKnnQuerySF;
import ch.ethz.globis.phtree.PhTreeSolidF.PhQuerySF;

public class PHTreeR<T> implements BoxMap<T> {

	private final PhTreeSolidF<T> tree;
	
	private PHTreeR(int dims) {
		tree = PhTreeSolidF.create(dims);
	} 
	
	public static <T> PHTreeR<T> createPHTree(int dims) {
		return new PHTreeR<>(dims);
	}
	
	@Override
	public int getDims() {
		return tree.getDims();
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
	public PHStats getStats() {
		return new PHStats(tree.getInternalTree().getStats(), tree.getDims());
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
	public void insert(double[] lower, double[] upper, T value) {
		tree.put(lower, upper, value);
	}

	@Override
	public T remove(double[] lower, double[] upper) {
		return tree.remove(lower, upper);
	}

	@Override
	public T update(double[] lo1, double[] up1, double[] lo2, double[] up2) {
		return tree.update(lo1, up1, lo2, up2);
	}

	@Override
	public T queryExact(double[] lower, double[] upper) {
		return tree.get(lower, upper);
	}

	@Override
	public BoxIterator<T> iterator() {
		return new ExtentWrapper();
	}

	@Override
	public BoxIterator<T> queryIntersect(double[] min, double[] max) {
		return new QueryIteratorPH<>(tree.queryIntersect(min, max));
	}

	@Override
	public BoxIteratorKnn<T> queryKnn(double[] center, int k) {
		return new QueryIteratorKnnPH<>(tree.nearestNeighbour(k, null, center));
	}

	private class ExtentWrapper implements BoxIterator<T> {

		private PhIteratorSF<T> iter;
		
		private ExtentWrapper() {
			reset(null, null);
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public BoxEntry<T> next() {
			//This reuses the entry object, but we have to clone the arrays...
			PhEntrySF<T> e = iter.nextEntryReuse();
			return new BoxEntry<>(e.lower().clone(), e.upper().clone(), e.value());
		}

		@Override
		public QueryIterator<BoxEntry<T>> reset(double[] min, double[] max) {
			if (min != null || max != null) {
				throw new UnsupportedOperationException("min/max must be `null`");
			}
			iter = tree.iterator();
			return this;
		}
	}
	
	private static class QueryIteratorPH<T> implements BoxIterator<T> {

		private final PhQuerySF<T> iter;
		
		private QueryIteratorPH(PhQuerySF<T> iter) {
			this.iter = iter;
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public BoxEntry<T> next() {
			//This reuses the entry object, but we have to clone the arrays...
			PhEntrySF<T> e = iter.nextEntryReuse();
			return new BoxEntry<>(e.lower().clone(), e.upper().clone(), e.value());
		}

		@Override
		public QueryIterator<BoxEntry<T>> reset(double[] min, double[] max) {
			iter.reset(min, max);
			return this;
		}
	}
	
	private static class QueryIteratorKnnPH<T> implements BoxIteratorKnn<T> {

		private final PhKnnQuerySF<T> iter;
		
		private QueryIteratorKnnPH(PhKnnQuerySF<T> iter) {
			this.iter = iter;
		}
		
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public BoxEntryKnn<T> next() {
			//This reuses the entry object, but we have to clone the arrays...
			PhEntryDistSF<T> e = iter.nextEntryReuse();
			return new BoxEntryKnn<>(e.lower().clone(), e.upper().clone(), e.value(), e.dist());
		}

		@Override
		public QueryIteratorKnnPH<T> reset(double[] center, int k) {
			iter.reset(k, null, center);
			return this;
		}
		
	}
	
}
