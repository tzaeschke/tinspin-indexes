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

public class PointArray<T> implements PointMap<T>, PointMultimap<T> {
	
	private final double[][] phc;
	private final int dims;
	private int N;
	private PointEntry<T>[] values;
	private int insPos = 0; 
	
	/**
	 * Setup of an simple array data structure (no indexing).
	 * 
	 * @param dims dimensions
	 * @param size size
	 * 
	 */
	@SuppressWarnings("unchecked")
	public PointArray(int dims, int size) {
		this.N = size;
		this.dims = dims;
		phc = new double[N][dims];
		values = new PointEntry[N];
	}
	
	
	@Override
	public void insert(double[] key, T value) {
		System.arraycopy(key, 0, phc[insPos], 0, dims);
		values[insPos] = new KnnEntry<>(key, value, -1);
		insPos++;
	}

	@Override
	public boolean remove(double[] point, T value) {
		return false;
	}

	@Override
	public boolean removeIf(double[] point, Predicate<PointEntry<T>> condition) {
		return false;
	}

	@Override
	public PointIterator<T> query(double[] point) {
		return null;
	}

	@Override
	public T queryExact(double[] point) {
		for (int j = 0; j < N; j++) { 
			if (eq(phc[j], point)) {
				return values[j].value();
			}
		}
		return null;
	}

	@Override
	public boolean contains(double[] point, T value) {
		for (int j = 0; j < N; j++) {
			if (eq(phc[j], point) && Objects.equals(value, values[j].value())) {
				return true;
			}
		}
		return false;
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
	public AQueryIterator query(double[] min, double[] max) {
		return new AQueryIterator(min, max);
	}

	@Override
	public PointEntryDist<T> query1nn(double[] center) {
		PointIteratorKnn<T> it = queryKnn(center, 1);
		return it.hasNext() ? it.next() : null;
	}

	private class AQueryIterator implements PointIterator<T> {

    	private Iterator<PointEntry<T>> it;
    	
		public AQueryIterator(double[] min, double[] max) {
			reset(min, max);
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public PointEntry<T> next() {
			return it.next();
		}

		@Override
		public QueryIterator<PointEntry<T>> reset(double[] min, double[] max) {
			ArrayList<PointEntry<T>> results = new ArrayList<>(); 
			for (int i = 0; i < N; i++) { 
				if (leq(phc[i], max) && geq(phc[i], min)) {
					results.add(values[i]);
				}
			}
			it = results.iterator();
			return this;
		}
    }
    
	@Override
	public PointIterator<T> iterator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public AQueryIteratorKnn queryKnn(double[] center, int k) {
		return new AQueryIteratorKnn(center, k);
	}

	@Override
	public PointIteratorKnn<T> queryKnn(double[] center, int k, PointDistance distFn) {
		return null;
	}


	private class AQueryIteratorKnn implements PointIteratorKnn<T> {

    	private Iterator<PointEntryDist<T>> it;
    	
		public AQueryIteratorKnn(double[] center, int k) {
			reset(center, k);
		}

		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public PointEntryDist<T> next() {
			return it.next();
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public AQueryIteratorKnn reset(double[] center, int k) {
			it = ((List)knnQuery(center, k)).iterator();
			return this;
		}
    }
    

	private ArrayList<KnnEntry<T>> knnQuery(double[] center, int k) {
		ArrayList<KnnEntry<T>> ret = new ArrayList<>(k);
		for (int i = 0; i < phc.length; i++) {
			double[] p = phc[i];
			double dist = dist(center, p);
			if (ret.size() < k) {
				ret.add(new KnnEntry<>(p, values[i].value(), dist));
				ret.sort(COMP);
			} else if (ret.get(k-1).dist > dist) {
				ret.remove(k-1);
				ret.add(new KnnEntry<>(p, values[i].value(), dist));
				ret.sort(COMP);
			}
		}
		return ret;
	}
	
	private static double dist(double[] a, double[] b) {
		double dist = 0;
		for (int i = 0; i < a.length; i++) {
			double d =  a[i]-b[i];
			dist += d*d;
		}
		return Math.sqrt(dist);
	}

	private final Comparator<KnnEntry<T>> COMP = KnnEntry::compareTo;
	
	private static class KnnEntry<T> implements Comparable<KnnEntry<T>>, PointEntryDist<T> {
		private final double[] p;
		private final double dist;
		private final T val;
		KnnEntry(double[] p, T val, double dist) {
			this.p = p;
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
			return "d=" + dist + ":" + Arrays.toString(p);
		}
		@Override
		public double[] point() {
			return p;
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
	public T update(double[] oldPoint, double[] newPoint) {
		for (int i = 0; i < N; i++) { 
			if (eq(phc[i], oldPoint)) {
				System.arraycopy(newPoint, 0, phc[i], 0, dims);
				return values[i].value();
			}
		}
		return null;
	}

	@Override
	public boolean update(double[] oldPoint, double[] newPoint, T value) {
		for (int i = 0; i < N; i++) {
			if (eq(phc[i], oldPoint) && Objects.equals(values[i].value(), value)) {
				System.arraycopy(newPoint, 0, phc[i], 0, dims);
				return true;
			}
		}
		return false;
	}

	@Override
	public T remove(double[] point) {
		for (int i = 0; i < N; i++) { 
			if (phc[i] != null && eq(phc[i], point)) {
				T v = values[i].value();
				values[i] = null;
				phc[i] = null;
				return v;
			}
		}
		return null;
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
		return N;
	}

	@Override
	public void clear() {
		for (int i = 0; i < N; i++) {
			values[i] = null;
			phc[i] = null;
		}
		N = 0;
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
			s.append(Arrays.toString(phc[i]) + " v=" + values[i]);
		}
		return s.toString();
	}
}
