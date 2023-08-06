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
import org.tinspin.index.BoxMap;
import org.tinspin.index.array.RectArray;
import org.tinspin.index.phtree.PHTreeR;
import org.tinspin.index.qthypercube.QuadTreeRKD;
import org.tinspin.index.qtplain.QuadTreeRKD0;
import org.tinspin.index.rtree.RTreeEntry;
import org.tinspin.index.rtree.RTree;

import static org.tinspin.index.Index.*;


public class BoxMapCandidate extends Candidate {
	
	private final BoxMap<Integer> idx;
	private final int dims;
	private final int N;
	private double[] data;
	private BoxIterator<Integer> query = null;
	private BoxIteratorKnn<Integer> queryKnn = null;
	private final boolean bulkloadSTR;

	public static BoxMapCandidate create(TestStats ts) {
		return new BoxMapCandidate(createIndex(ts), ts);
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
	public BoxMapCandidate(BoxMap<?> ri, TestStats ts) {
		this.N = ts.cfgNEntries;
		this.dims = ts.cfgNDims;
		this.idx = (BoxMap<Integer>) ri;
		this.bulkloadSTR = ts.INDEX.equals(TestInstances.IDX.STR);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void load(double[] data, int dims) {
		this.data = data;
		if (bulkloadSTR) {
			RTreeEntry<Integer>[] entries = new RTreeEntry[N];
			int pos = 0;
			for (int i = 0; i < N; i++) {
				double[] lo = new double[dims];
				double[] hi = new double[dims];
				System.arraycopy(data, pos, lo, 0, dims);
				pos += dims;
				System.arraycopy(data, pos, hi, 0, dims);
				pos += dims;
				entries[i] = RTreeEntry.createBox(lo, hi, i);
			}
			RTree<Integer> rt = (RTree<Integer>) idx;
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
				idx.insert(lo, hi, n);
			}
		}
	}

	@Override
	public Object preparePointQuery(double[][] q) {
		return q;
	}

	@Override
	public int pointQuery(Object qA, int[] ids) {
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
			BoxEntryKnn<Integer> e = queryKnn.next();
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
	public int update(double[][] updateTable, int[] ids) {
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
