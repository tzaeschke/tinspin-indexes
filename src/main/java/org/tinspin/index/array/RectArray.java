/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.array;

import java.util.*;
import java.util.function.Predicate;

import org.tinspin.index.*;
import org.tinspin.index.util.BoxIteratorWrapper;

public class RectArray<T> implements BoxMap<T>, BoxMultimap<T> {

	private final double[][] phc;
	private final int dims;
	private int N;
	private int size;
	private final BoxEntry<T>[] values;
	private int insPos = 0; 

	/**
	 * Setup of a simple array data structure (no indexing).
	 * 
	 * @param dims dimensions
	 * @param size size
	 * 
	 */
	@SuppressWarnings("unchecked")
	public RectArray(int dims, int size) {
		this.N = size;
		this.size = 0;
		this.dims = dims;
		phc = new double[2*N][dims];
		values = new BoxEntry[N];
	}


	@Override
	public void insert(double[] lower, double[] upper, T value) {
		System.arraycopy(lower, 0, phc[insPos*2], 0, dims);
		System.arraycopy(upper, 0, phc[insPos*2+1], 0, dims);
		values[insPos] = new BoxEntryKnn<>(lower, upper, value, -1);
		insPos++;
		size++;
	}


	@Override
	public T remove(double[] lower, double[] upper) {
		for (int i = 0; i < N; i++) {
			if (phc[i*2] != null && eq(phc[i*2], lower)
					&& eq(phc[(i*2)+1], upper)) {
				phc[i*2] = null;
				phc[(i*2)+1] = null;
				size--;
				return values[i].value();
			}
		}
		return null;
	}

	@Override
	public boolean remove(double[] lower, double[] upper, T value) {
		return removeIf(lower, upper, e -> Objects.equals(value, e.value()));
	}

	@Override
	public boolean removeIf(double[] lower, double[] upper, Predicate<BoxEntry<T>> condition) {
		for (int i = 0; i < N; i++) {
			if (phc[i*2] != null && eq(phc[i*2], lower)
					&& eq(phc[(i*2)+1], upper) && condition.test(values[i])) {
				phc[i*2] = null;
				phc[(i*2)+1] = null;
				size--;
				return true;
			}
		}
		return false;
	}

	@Override
	public T update(double[] lo1, double[] up1, double[] lo2, double[] up2) {
		for (int i = 0; i < N; i++) {
			if (eq(phc[i*2], lo1) && eq(phc[(i*2)+1], up1)) {
				System.arraycopy(lo2, 0, phc[i*2], 0, dims);
				System.arraycopy(up2, 0, phc[(i*2)+1], 0, dims);
				return values[i].value();
			}
		}
		return null;
	}

	@Override
	public boolean contains(double[] min, double[] max) {
		return queryExact(min, max) != null;
	}

	@Override
	public boolean update(double[] lo1, double[] up1, double[] lo2, double[] up2, T value) {
		for (int i = 0; i < N; i++) {
			if (eq(phc[i*2], lo1) && eq(phc[(i*2)+1], up1)) {
				if (eq(phc[i*2], lo1) && eq(phc[(i*2)+1], up1) && Objects.equals(value, values[i].value())) {
					System.arraycopy(lo2, 0, phc[i * 2], 0, dims);
					System.arraycopy(up2, 0, phc[(i * 2) + 1], 0, dims);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public T queryExact(double[] lower, double[] upper) {
		for (int i = 0; i < N; i++) {
			if (phc[i*2] != null && eq(phc[i*2], lower) && eq(phc[i*2+1], upper)) {
				return values[i].value();
			}
		}
		return null;
	}

	@Override
	public boolean contains(double[] lower, double[] upper, T value) {
		for (int i = 0; i < N; i++) {
			if (phc[i*2] != null && eq(phc[i*2], lower) && eq(phc[i*2+1], upper) && Objects.equals(value, values[i].value())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public BoxIterator<T> queryRectangle(double[] lower, double[] upper) {
		return new BoxIteratorWrapper<>(lower, upper, (low, upp) -> {
			ArrayList<BoxEntry<T>> result = new ArrayList<>();
			for (int i = 0; i < N; i++) {
				if (phc[i*2] != null && eq(phc[i*2], low) && eq(phc[i*2+1], upp)) {
					result.add(values[i]);
				}
			}
			return result.iterator();
		});
	}

	private boolean eq(double[] a, double[] b) {
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	private boolean geq(double[] a, double[] b) {
		for (int i = 0; i < a.length; i++) {
			if (a[i] < b[i]) {
				return false;
			}
		}
		return true;
	}

	private boolean leq(double[] a, double[] b) {
		for (int i = 0; i < a.length; i++) {
			if (a[i] > b[i]) {
				return false;
			}
		}
		return true;
	}

	@Override
	public BoxIterator<T> queryIntersect(double[] min, double[] max) {
		return new BoxIteratorWrapper<>(min, max, (lower, upper) -> {
			ArrayList<BoxEntry<T>> results = new ArrayList<>();
			for (int i = 0; i < N; i++) {
				if (leq(phc[i*2], upper) && geq(phc[i*2+1], lower)) {
					results.add(values[i]);
				}
			}
			return results.iterator();
		});
	}

	@Override
	public BoxEntryKnn<T> query1nn(double[] center) {
		return queryKnn(center, 1).next();
	}

	@Override
	public BoxIterator<T> iterator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public BoxIteratorKnn<T> queryKnn(double[] center, int k) {
		return new AQueryIteratorKnn(center, k);
	}

	@Override
	public BoxIteratorKnn<T> queryKnn(double[] center, int k, BoxDistance distFn) {
		return null;
	}


	private class AQueryIteratorKnn implements BoxIteratorKnn<T> {

		private Iterator<BoxEntryKnn<T>> it;

		public AQueryIteratorKnn(double[] center, int k) {
			reset(center, k);
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public BoxEntryKnn<T> next() {
			return it.next();
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public AQueryIteratorKnn reset(double[] center, int k) {
			it = ((List)knnQuery(center, k)).iterator();
			return this;
		}

		private ArrayList<BoxEntryKnn<T>> knnQuery(double[] center, int k) {
			ArrayList<BoxEntryKnn<T>> ret = new ArrayList<>(k);
			for (int i = 0; i < phc.length/2; i++) {
				double[] min = phc[i*2];
				double[] max = phc[i*2+1];
				double dist = distREdge(center, min, max);
				if (ret.size() < k) {
					ret.add(new BoxEntryKnn<>(min, max, values[i].value(), dist));
					ret.sort(COMP);
				} else if (ret.get(k-1).dist() > dist) {
					ret.remove(k-1);
					ret.add(new BoxEntryKnn<>(min, max, values[i].value(), dist));
					ret.sort(COMP);
				}
			}
			return ret;
		}
	}

	private static double distREdge(double[] center, double[] rLower, double[] rUpper) {
		double dist = 0;
		for (int i = 0; i < center.length; i++) {
			double d = 0;
			if (center[i] > rUpper[i]) {
				d = center[i] - rUpper[i];
			} else  if (center[i] < rLower[i]) {
				d = rLower[i] - center[i];
			}
			dist += d*d;
		}
		return Math.sqrt(dist);
	}

	private static final BEComparator COMP = new BEComparator();

	@Override
	public String toString() {
		return "NaiveArray";
	}

	@Override
	public int getDims() {
		return dims;
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public void clear() {
		for (int i = 0; i < N; i++) {
			values[i] = null;
		}
		for (int i = 0; i < 2*N; i++) {
			phc[i] = null;
		}
		N = 0;
		size = 0;
	}

	@Override
	public Stats getStats() {
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public int getNodeCount() {
		return 1;
	}

	@Override
	public int getDepth() {
		return 0;
	}

	@Override
	public String toStringTree() {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < N; i++) {
			s.append(Arrays.toString(phc[i * 2])).append("/").append(Arrays.toString(phc[i * 2 + 1])).append(" v=").append(values[i]);
		}
		return s.toString();
	}
}
