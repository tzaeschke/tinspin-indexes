/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.array;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.tinspin.index.PointEntry;
import org.tinspin.index.PointEntryDist;
import org.tinspin.index.PointIndex;
import org.tinspin.index.QueryIterator;
import org.tinspin.index.QueryIteratorKNN;

public class PointArray<T> implements PointIndex<T> {
	
	private final double[][] phc;
	private final int dims;
	private final int N;
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
	public T queryExact(double[] point) {
		for (int j = 0; j < N; j++) { 
			if (eq(phc[j], point)) {
				return values[j].value();
			}
		}
		return null;
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
	
    private class AQueryIterator implements QueryIterator<PointEntry<T>> {

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
		public void reset(double[] min, double[] max) {
			ArrayList<PointEntry<T>> results = new ArrayList<>(); 
			for (int i = 0; i < N; i++) { 
				if (leq(phc[i], max) && geq(phc[i], min)) {
					results.add(values[i]);
				}
			}
			it = results.iterator();
		}
    }
    
	@Override
	public QueryIterator<? extends PointEntry<T>> iterator() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
		//return null;
	}

	@Override
	public AQueryIteratorKNN queryKNN(double[] center, int k) {
		return new AQueryIteratorKNN(center, k);
	}

    
    private class AQueryIteratorKNN implements QueryIteratorKNN<PointEntryDist<T>> {

    	private Iterator<PointEntryDist<T>> it;
    	
		public AQueryIteratorKNN(double[] center, int k) {
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
		public void reset(double[] center, int k) {
			it = ((List)knnQuery(center, k)).iterator();
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

	private final Comparator<KnnEntry<T>> COMP = new Comparator<KnnEntry<T>>() {
		@Override
		public int compare(KnnEntry<T> o1, KnnEntry<T> o2) {
			return o1.compareTo(o2);
		}
	};
	
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
	}

	@Override
	public Object getStats() {
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



}
