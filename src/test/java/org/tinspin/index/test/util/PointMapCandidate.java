/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import java.util.Arrays;

import ch.ethz.globis.tinspin.IndexHandle;
import ch.ethz.globis.tinspin.TestStats;
import ch.ethz.globis.tinspin.wrappers.Candidate;
import org.tinspin.index.*;
import org.tinspin.index.array.PointArray;
import org.tinspin.index.covertree.CoverTree;
import org.tinspin.index.kdtree.KDTree;
import org.tinspin.index.phtree.PHTreeP;
import org.tinspin.index.qthypercube.QuadTreeKD;
import org.tinspin.index.qthypercube2.QuadTreeKD2;
import org.tinspin.index.qtplain.QuadTreeKD0;
import org.tinspin.index.rtree.Entry;
import org.tinspin.index.rtree.RTree;
import org.tinspin.index.test.util.TestInstances.IDX;
import org.tinspin.index.util.PointMapWrapper;

import static org.tinspin.index.Index.*;

public class PointMapCandidate extends Candidate {
	
	private final PointMap<double[]> idx;
	private final int dims;
	private final int N;
	private double[] data;
	private QueryIterator<PointEntry<double[]>> it;
	private QueryIteratorKnn<PointEntryDist<double[]>> itKnn;
	private final boolean bulkloadSTR;
	private final IndexHandle index;

	public static PointMapCandidate create(TestStats ts) {
		IDX idx = (IDX) ts.INDEX;
		int dims = ts.cfgNDims;
		int size = ts.cfgNEntries;
		return new PointMapCandidate(create(idx, dims, size), ts);
	}

	private static <T> PointMap<T> create(IDX idx, int dims, int size) {
		switch (idx) {
			case ARRAY: return new PointArray<>(dims, size);
			//case CRITBIT: return new PointArray<>(dims, size);
			case KDTREE: return KDTree.create(dims);
			case PHTREE: return PHTreeP.createPHTree(dims);
			case QUAD_HC: return QuadTreeKD.create(dims);
			case QUAD_HC2: return QuadTreeKD2.create(dims);
			case QUAD_PLAIN: return QuadTreeKD0.create(dims);
			case RSTAR:
			case STR: return PointMapWrapper.create(RTree.createRStar(dims));
			case COVER: return CoverTree.create(dims);
			default:
				throw new UnsupportedOperationException(idx.name());
		}
	}

	/**
	 * @param pi the index to be tested
	 * @param ts test stats
	 */
	@SuppressWarnings("unchecked")
	public PointMapCandidate(PointMap<?> pi, TestStats ts) {
		this.N = ts.cfgNEntries;
		this.dims = ts.cfgNDims;
		idx = (PointMap<double[]>) pi;
		this.index = ts.INDEX;
		this.bulkloadSTR = IDX.STR == this.index;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void load(double[] data, int dims) {
		this.data = data;
		if (bulkloadSTR) {
			Entry<double[]>[] entries = new Entry[N];
			int pos = 0;
			for (int i = 0; i < N; i++) {
				double[] buf = new double[dims];
				System.arraycopy(data, pos, buf, 0, dims);
				pos += dims;
				entries[i] = new Entry<double[]>(buf, buf, buf);
			}
			PointMapWrapper<double[]> rt = (PointMapWrapper<double[]>) idx;
			rt.load(entries);
		} else {
			for (int i = 0; i < N; i++) {
				double[] buf = new double[dims];
				for (int d = 0; d < dims; d++) {
					buf[d] = data[i*dims+d]; 
				}
				idx.insert(buf, buf);
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
		for (double[] q: (double[][])qA) {
			if (idx.queryExact(q) != null) {
				n++;
			}
			//log("q=" + Arrays.toString(q));
		}
		return n;
	}

	@Override
	public int unload() {
		int n = 0;
		double[] l = new double[dims];
		for (int i = 0; i < N>>1; i++) {
			n += idx.remove(getEntry(l, i)) != null ? 1 : 0;
			n += idx.remove(getEntry(l, N-i-1)) != null ? 1 : 0;
		}
		if ((N%2) != 0) {
			int i = (N>>1);
			n += idx.remove(getEntry(l, i)) != null ? 1 : 0;
		}
		return n;
	}

	private double[] getEntry(double[] val, int pos) {
		for (int d = 0; d < dims; d++) {
			val[d] = data[pos*dims+d];
		}
		return val;
	}
	
	@Override
	public int query(double[] min, double[] max) {
		if (it == null) {
			it = idx.query(min, max);
		} else {
			it.reset(min, max);
		}
		int n = 0;
		while (it.hasNext()) {
			it.next();
			n++;
		}
//		int n = ((PhTree7)idx).queryAll(min2, max2).size();
		//log("q=" + Arrays.toString(q));
		return n;
	}
	
	@Override
	public double knnQuery(int k, double[] center) {
		if (k == 1) {
			return idx.query1nn(center).dist();
		}
		if (itKnn == null) {
			itKnn = idx.queryKnn(center, k);
		} else {
			itKnn.reset(center, k);
		}
		double ret = 0;
		int i = 0;
		while (itKnn.hasNext() && i < k) {
			ret += itKnn.next().dist();
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

	
	/**
	 * Used to test the native code during development process
	 * 
	 * @return The internally used index structure
	 */
	public Index getNative() {
		return idx;
	}

	@Override
	public void getStats(TestStats s) {
		s.statNnodes = idx.getNodeCount();
		s.statNpostlen = idx.getDepth();
	}
	
	@Override
	public int update(double[][] updateTable, int[] ids) {
		int n = 0;
		for (int i = 0; i < updateTable.length; ) {
			double[] p1 = updateTable[i++];
			double[] p2 = Arrays.copyOf(updateTable[i++], dims);
			if (idx.update(p1, p2) != null) {
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
	public boolean supportsWindowQuery() {
		return this.index != IDX.COVER;
	}
	
	@Override
	public boolean supportsUpdate() {
		return dims <= 16 && this.index != IDX.COVER;
	}

	@Override
	public boolean supportsUnload() {
		return dims <= 16 && this.index != IDX.COVER;
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
