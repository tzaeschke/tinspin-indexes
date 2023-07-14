/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.array;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.tinspin.index.*;
import org.tinspin.index.util.QueryIteratorWrapper;

public class RectArray<T> implements RectangleIndex<T>, RectangleIndexMM<T> {

	private final double[][] phc;
	private final int dims;
	private int N;
	private int size;
	private final RectangleEntry<T>[] values;
	private int insPos = 0; 

	/**
	 * Setup of an simple array data structure (no indexing).
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
		values = new RectangleEntry[N];
	}


	@Override
	public void insert(double[] lower, double[] upper, T value) {
		System.arraycopy(lower, 0, phc[insPos*2], 0, dims);
		System.arraycopy(upper, 0, phc[insPos*2+1], 0, dims);
		values[insPos] = new KnnEntry<>(lower, upper, value, -1);
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
	public boolean removeIf(double[] lower, double[] upper, Predicate<RectangleEntry<T>> condition) {
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
		for (int j = 0; j < N; j++) {
			if (eq(phc[j*2], lower) && eq(phc[j*2+1], upper)) {
				return values[j].value();
			}
		}
		return null;
	}

	@Override
	public boolean contains(double[] lower, double[] upper, T value) {
		for (int i = 0; i < N; i++) {
			if (eq(phc[i*2], lower) && eq(phc[i*2+1], upper) && Objects.equals(value, values[i].value())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public QueryIterator<RectangleEntry<T>> queryRectangle(double[] lower, double[] upper) {
		return new QueryIteratorWrapper<>(lower, upper, (low, upp) -> {
			ArrayList<RectangleEntry<T>> result = new ArrayList<>();
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
	public QueryIterator<RectangleEntry<T>> queryIntersect(double[] min, double[] max) {
		return new QueryIteratorWrapper<>(min, max, (lower, upper) -> {
			ArrayList<RectangleEntry<T>> results = new ArrayList<>();
			for (int i = 0; i < N; i++) {
				if (leq(phc[i*2], upper) && geq(phc[i*2+1], lower)) {
					results.add(values[i]);
				}
			}
			return results.iterator();
		});
	}

	@Override
	public RectangleEntryDist<T> query1NN(double[] center) {
		return queryKNN(center, 1).next();
	}

	@Override
	public QueryIterator<RectangleEntry<T>> iterator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public QueryIteratorKNN<RectangleEntryDist<T>> queryKNN(double[] center, int k) {
		return new AQueryIteratorKNN(center, k);
	}

	@Override
	public QueryIteratorKNN<RectangleEntryDist<T>> queryKNN(double[] center, int k, RectangleDistanceFunction distFn) {
		return null;
	}


	private class AQueryIteratorKNN implements QueryIteratorKNN<RectangleEntryDist<T>> {

		private Iterator<RectangleEntryDist<T>> it;

		public AQueryIteratorKNN(double[] center, int k) {
			reset(center, k);
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public RectangleEntryDist<T> next() {
			return it.next();
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public AQueryIteratorKNN reset(double[] center, int k) {
			it = ((List)knnQuery(center, k)).iterator();
			return this;
		}
	}


	private ArrayList<KnnEntry<T>> knnQuery(double[] center, int k) {
		ArrayList<KnnEntry<T>> ret = new ArrayList<>(k);
		for (int i = 0; i < phc.length/2; i++) {
			double[] min = phc[i*2];
			double[] max = phc[i*2+1];
			double dist = distREdge(center, min, max);
			if (ret.size() < k) {
				ret.add(new KnnEntry<>(min, max, values[i].value(), dist));
				ret.sort(COMP);
			} else if (ret.get(k-1).dist > dist) {
				ret.remove(k-1);
				ret.add(new KnnEntry<>(min, max, values[i].value(), dist));
				ret.sort(COMP);
			}
		}
		return ret;
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

	private final Comparator<KnnEntry<T>> COMP = KnnEntry::compareTo;

	private static class KnnEntry<T> implements Comparable<KnnEntry<T>>, RectangleEntryDist<T> {
		private final double[] min;
		private final double[] max;
		private final T val;
		private final double dist;
		KnnEntry(double[] min, double[] max, T val, double dist) {
			this.min = min;
			this.max = max;
			this.val = val;
			this.dist = dist;
		}
		@Override
		public int compareTo(KnnEntry<T> o) {
			double d = dist-o.dist;
			return d < 0 ? -1 : d > 0 ? 1 : 0;
		}

		@Override
		public String toString() {
			return "d=" + dist + ":" + Arrays.toString(min) + "/" + Arrays.toString(max);
		}
		@Override
		public double[] lower() {
			return min;
		}
		@Override
		public double[] upper() {
			return max;
		}
		@Override
		public T value() {
			return val;
		}
		@Override
		public double dist() {
			return dist;
		}
	}

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
