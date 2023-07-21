/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import java.util.Arrays;

import ch.ethz.globis.tinspin.TestStats;
import ch.ethz.globis.tinspin.wrappers.Candidate;
import org.tinspin.index.Index;
import org.tinspin.index.BoxEntry;
import org.tinspin.index.BoxEntryDist;
import org.tinspin.index.BoxMap;
import org.tinspin.index.array.RectArray;
import org.tinspin.index.phtree.PHTreeR;
import org.tinspin.index.qthypercube.QuadTreeRKD;
import org.tinspin.index.qtplain.QuadTreeRKD0;
import org.tinspin.index.rtree.Entry;
import org.tinspin.index.rtree.RTree;


public class RectangleIndexCandidate extends Candidate {
	
	private final BoxMap<Object> idx;
	private final int dims;
	private final int N;
	private double[] data;
	private static final Object O = new Object();
	private QueryIterator<BoxEntry<Object>> query = null;
	private QueryIteratorKnn<BoxEntryDist<Object>> queryKnn = null;
	private final boolean bulkloadSTR;

	public static RectangleIndexCandidate create(TestStats ts) {
		return new RectangleIndexCandidate(createIndex(ts), ts);
	}

	private static <T> BoxMap<T> createIndex(TestStats s) {
		int dims = s.cfgNDims;
		int size = s.cfgNEntries;
		switch ((TestInstances.IDX)s.INDEX) {
			case ARRAY: return new RectArray<>(dims, size);
			case PHTREE: return PHTreeR.createPHTree(dims);
			case QUAD_HC: return QuadTreeRKD.create(dims);
			case QUAD_PLAIN: return QuadTreeRKD0.create(dims);
			case RSTAR:
			case STR: return RTree.createRStar(dims);
			default:
				throw new UnsupportedOperationException();
		}
	}

	/**
	 * @param ri index 
	 * @param ts test stats
	 */
	@SuppressWarnings("unchecked")
	public RectangleIndexCandidate(BoxMap<?> ri, TestStats ts) {
		this.N = ts.cfgNEntries;
		this.dims = ts.cfgNDims;
		this.idx = (BoxMap<Object>) ri;
		this.bulkloadSTR = ts.INDEX.equals(TestInstances.IDX.STR);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void load(double[] data, int dims) {
		this.data = data;
		if (bulkloadSTR) {
			Entry<Object>[] entries = new Entry[N];
			int pos = 0;
			for (int i = 0; i < N; i++) {
				double[] lo = new double[dims];
				double[] hi = new double[dims];
				System.arraycopy(data, pos, lo, 0, dims);
				pos += dims;
				System.arraycopy(data, pos, hi, 0, dims);
				pos += dims;
				entries[i] = new Entry<Object>(lo, hi, O);
			}
			RTree<Object> rt = (RTree<Object>) idx;
			rt.load(entries);
		} else {
			int pos = 0;
			for (int n = 0; n < N; n++) {
				double[] lo = new double[dims];
				double[] hi = new double[dims];
				System.arraycopy(data, pos, lo, 0, dims);
				pos += dims;
				System.arraycopy(data, pos, hi, 0, dims);
				pos += dims;
				idx.insert(lo, hi, O);
			}
		}
	}

	@Override
	public Object preparePointQuery(double[][] q) {
		return q;
	}

	@Override
	public int pointQuery(Object qA) {
		int n = 0;
		double[][] dA = (double[][]) qA; 
		for (int i = 0; i < dA.length; i+=2) {
			if (idx.queryExact(dA[i], dA[i+1]) != null) {
				n++;
			}
		}
		return n;
	}
	
	@Override
	public boolean supportsPointQuery() {
		return dims <= 16;
	}
	
	@Override
	public int unload() {
		int n = 0;
		double[] lo = new double[dims];
		double[] hi = new double[dims];
		for (int i = 0; i < N>>1; i++) {
			System.arraycopy(data, i*dims*2, lo, 0, dims);
			System.arraycopy(data, i*dims*2+dims, hi, 0, dims);
			n += idx.remove(lo, hi) != null ? 1 : 0;
			int i2 = N-i-1;
			System.arraycopy(data, i2*dims*2, lo, 0, dims);
			System.arraycopy(data, i2*dims*2+dims, hi, 0, dims);
			n += idx.remove(lo, hi) != null? 1 : 0;
		}
		if ((N%2) != 0) {
			int i = (N>>1);
			System.arraycopy(data, i*dims*2, lo, 0, dims);
			System.arraycopy(data, i*dims*2+dims, hi, 0, dims);
			n += idx.remove(lo, hi) != null ? 1 : 0;
		}
		return n;
	}
	
	
	@Override
	public int query(double[] min, double[] max) {
		if (query == null) {
			query = idx.queryIntersect(min, max);
		} else {
			query.reset(min, max);
		}
		int n = 0;
		while (query.hasNext()) {
			query.next();
			n++;
		}
		return n;
	}
	
	@Override
	public double knnQuery(int k, double[] center) {
		if (k == 1) {
			return idx.query1nn(center).dist();
		}
		if (queryKnn == null) {
			queryKnn = idx.queryKnn(center, k);
		} else {
			queryKnn.reset(center, k);
		}
		double ret = 0;
		int i = 0;
		while (queryKnn.hasNext() && i < k) {
			BoxEntryDist<Object> e = queryKnn.next();
			ret += e.dist();
			i++;
		}
		if (i != k) {
			throw new IllegalStateException();
		}
		return ret;
	}

	@Override
	public boolean supportsKNN() {
		return true;
	}
	
	@Override
	public void release() {
		data = null;
	}

	public Index getNative() {
		return idx;
	}

	@Override
	public void getStats(TestStats S) {
		S.statNnodes = idx.getNodeCount(); 
		S.statNpostlen = idx.getDepth();
	}
	
	@Override
	public int update(double[][] updateTable) {
		int n = 0;
		for (int i = 0; i < updateTable.length; ) {
			double[] lo1 = updateTable[i++];
			double[] up1 = updateTable[i++];
			double[] lo2 = Arrays.copyOf(updateTable[i++], dims);
			double[] up2 = Arrays.copyOf(updateTable[i++], dims);
			if (idx.update(lo1, up1, lo2, up2) != null) {
				n++;
			}
		}
		return n;
	}
	
	@Override
	public boolean supportsUpdate() {
		return dims <= 16;
	}

	@Override
	public boolean supportsUnload() {
		return dims <= 16;
	}
	
	@Override
	public String toString() {
		return idx.toString(); 
	}
	
	@Override
	public String toStringTree() {
		return idx.toStringTree();
	}

	@Override
	public void clear() {
		idx.clear();
	}

	@Override
	public int size() {
		return idx.size();
	}
}
