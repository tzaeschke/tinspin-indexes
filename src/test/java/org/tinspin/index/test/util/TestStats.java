/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import java.io.Serializable;
import java.util.List;

import org.tinspin.index.*;
import org.tinspin.index.array.PointArray;
import org.tinspin.index.array.RectArray;
import org.tinspin.index.covertree.CoverTree;
import org.tinspin.index.kdtree.KDTree;
import org.tinspin.index.phtree.PHTreeMMP;
import org.tinspin.index.phtree.PHTreeP;
import org.tinspin.index.phtree.PHTreeR;
import org.tinspin.index.qthypercube.QuadTreeKD;
import org.tinspin.index.qthypercube.QuadTreeRKD;
import org.tinspin.index.qthypercube2.QuadTreeKD2;
import org.tinspin.index.qtplain.QuadTreeKD0;
import org.tinspin.index.qtplain.QuadTreeRKD0;
import org.tinspin.index.rtree.RTree;
import org.tinspin.index.util.PointMapWrapper;
import org.tinspin.index.util.PointMultimapWrapper;

public class TestStats implements Serializable, Cloneable {

	public enum INDEX {
		/** Naive array implementation, for verification only */
		ARRAY,
		/** kD-Tree */
		KDTREE,
		/** PH-Tree */
		PHTREE,
		/** PH-Tree multimap */
		PHTREE_MM,
		/** CritBit */
		CRITBIT,
		/** Quadtree with HC navigation*/
		QUAD_HC,
		/** Quadtree with HC navigation version 2 */
		QUAD_HC2,
		/** Plain Quadtree */
		QUAD_PLAIN,
		/** RStarTree */
		RSTAR,
		/** STR-loaded RStarTree */
		STR,
		/** CoverTree */
		COVER
	}

	static <T> PointMap<T> createPI(INDEX idx, int dims, int size) {
		switch (idx) {
			case ARRAY: return new PointArray<>(dims, size);
			//case CRITBIT: return new PointArray<>(dims, size);
			case KDTREE: return KDTree.create(dims);
			case PHTREE: return PHTreeP.create(dims);
			case QUAD_HC: return QuadTreeKD.create(dims);
			case QUAD_HC2: return QuadTreeKD2.create(dims);
			case QUAD_PLAIN: return QuadTreeKD0.create(dims);
			case RSTAR:
			case STR: return PointMapWrapper.create(RTree.createRStar(dims));
			case COVER: return CoverTree.create(dims);
			default:
				throw new UnsupportedOperationException();
		}
	}

	static <T> PointMultimap<T> createPIMM(INDEX idx, int dims, int size) {
		switch (idx) {
			//case ARRAY: return new PointArray<>(dims, size);
			//case CRITBIT: return new PointArray<>(dims, size);
			case KDTREE: return KDTree.create(dims);
			case PHTREE_MM: return PHTreeMMP.create(dims);
			case QUAD_HC: return QuadTreeKD.create(dims);
			case QUAD_HC2: return QuadTreeKD2.create(dims);
			case QUAD_PLAIN: return QuadTreeKD0.create(dims);
			case RSTAR:
			case STR: return PointMultimapWrapper.create(RTree.createRStar(dims));
			//case COVER: return CoverTree.create(dims);
			default:
				throw new UnsupportedOperationException();
		}
	}

	static <T> BoxMap<T> createRI(INDEX idx, int dims, int size) {
		switch (idx) {
		case ARRAY: return new RectArray<>(dims, size);
		//case CRITBIT: return new PointArray<>(dims, size);
		case PHTREE: return PHTreeR.createPHTree(dims);
		case QUAD_HC: return QuadTreeRKD.create(dims);
		case QUAD_PLAIN: return QuadTreeRKD0.create(dims);
		case RSTAR: 
		case STR: return RTree.createRStar(dims);
		default:
			throw new UnsupportedOperationException();
		}
	}
	
	
	/** Edge length of the populated data area. */
	//private final double DEFAULT_LEN = (1L<<31)-1;
	//private final double DEFAULT_LEN = 1000.0;
	public static final double DEFAULT_DATA_LEN = 1.0;
	/** Average edge length of the data rectangles. */
	public static final double DEFAULT_RECT_LEN = 0.00001;

	public static final int DEFAULT_DUPLICATES = 1;
	

	public enum TST {
		CUBE,
		CLUSTER,
		SKYLINE,
		TIGER, 
		TIGER32,
		OSM,
		MBR_SIZE,
		MBR_ASPECT, 
		VORTEX,
		CUSTOM,
		TOUCH,
		CSV;
	}

	/** How often are tests repeated? */
	public static int DEFAULT_CFG_REPEAT = 3;
	
	public static int DEFAULT_W_QUERY_SIZE = 1000;
	public static int DEFAULT_N_WINDOW_QUERY = 1000; //number of range queries
	public static int DEFAULT_N_POINT_QUERY = 1000*1000; //number of point queries
	public static int DEFAULT_N_KNN_QUERY = 10*1000;
	public static int DEFAULT_N_UPDATES = 100*1000;
	public static int DEFAULT_N_UPDATE_CYCLES = 10;


	/** */
	private static final long serialVersionUID = 1L;
	public TestStats(TestStats.TST test, TestStats.INDEX index, int N, int DIM, boolean isRangeData,
			double param1) {
		this(test, index, N, DIM, isRangeData, param1, 0);
	}
	public TestStats(TestStats.TST test, TestStats.INDEX index, int N, int DIM, boolean isRangeData,
			double param1, double param2) {
		this.cfgNEntries = N;
		this.cfgNDims = DIM;
		this.INDEX = index;
		this.TEST = test;
		this.SEEDmsg = "" + seed;
		this.isRangeData = isRangeData;
		this.param1 = param1;
		this.param2 = param2;
	}

	public TestStats setParam2(double param2) {
		this.param2 = param2;
		return this;
	}

	public TestStats setDuplicates(int duplicates) {
		cfgDuplicates = duplicates;
		return this;
	}

	//configuration
	/** how often to repeat the test. */
	public int cfgNRepeat = DEFAULT_CFG_REPEAT;
	int cfgNBits = 64; //default
	public int cfgNDims;
	public int cfgNEntries;

	/** How often kNN queries are repeated. This is reduced
	 * automatically with increasing dimensionality. */
	public int cfgKnnQueryBaseRepeat = DEFAULT_N_KNN_QUERY;
	public int cfgPointQueryRepeat = DEFAULT_N_POINT_QUERY;
	public int cfgUpdateRepeat = DEFAULT_N_UPDATE_CYCLES;
	public int cfgUpdateSize = DEFAULT_N_UPDATES;
	public int cfgWindowQueryRepeat = DEFAULT_N_WINDOW_QUERY;
	/** Expected average number of entries in a query result. */
	public int cfgWindowQuerySize = DEFAULT_W_QUERY_SIZE;
	
	/** length of the populated data area */
	public double cfgDataLen = DEFAULT_DATA_LEN;
	/** length of the data rectangles */
	public double cfgRectLen = DEFAULT_RECT_LEN;

	/** Number of point duplicates. n=1 means no duplicates, n=2 means every point exists 2 times. */
	public int cfgDuplicates = DEFAULT_DUPLICATES;

	public final TestStats.INDEX INDEX;
	public final TestStats.TST TEST;
	public String SEEDmsg;
	public long seed;
	public final double param1;
	public double param2 = 0;
	public String paramStr;
	public boolean paramEnforceGC = true;
	public final boolean isRangeData;

	//results
	long statTGen;
	long statTLoad;
	long statTUnload;
	long statTq1;
	long statTq1E;
	long statTq2;
	long statTq2E;
	long statTqp1;
	long statTqp1E;
	long statTqp2;
	long statTqp2E;
	long statTqk1_1;
	long statTqk1_1E;
	long statTqk1_2;
	long statTqk1_2E;
	long statTqk10_1;
	long statTqk10_1E;
	long statTqk10_2;
	long statTqk10_2E;
	long statTu1;
	long statTu1E;
	long statTu2;
	long statTu2E;
	public int statNnodes;
	public long statNpostlen;
	public int statNNodeAHC;
	int statNNodeNT;
	int statNNodeInternalNT;
	public int statNq1;
	public int statNq2;
	public int statNqp1;
	public int statNqp2;
	public double statDqk1_1;
	public double statDqk1_2;
	public double statDqk10_1;
	public double statDqk10_2;
	public int statNu1;
	public int statNu2;
	long statSCalc;
	public long statSjvmF;
	public long statSjvmE;
	long statGcDiffL;
	long statGcTimeL;
	long statGcDiffWq;
	long statGcTimeWq;
	long statGcDiffPq;
	long statGcTimePq;
	long statGcDiffUp;
	long statGcTimeUp;
	long statGcDiffK1;
	long statGcTimeK1;
	long statGcDiffK10;
	long statGcTimeK10;
	long statGcDiffUl;
	long statGcTimeUl;
	String assortedInfo = "";

	Throwable exception = null;

	public void setFailed(Throwable t) {
		SEEDmsg = SEEDmsg + "-F";
		exception = t;
	}

	public void setSeed(long seed) {
		this.seed = seed;
		SEEDmsg = Long.toString(seed);
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	public TestStats cloneStats() {
		try {
			return (TestStats) clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	public static String[] testHeader() {
		String D = "\t"; //delimiter

		String[][] h2 = {
//				{"", "", "",    "",     "",  "Space", "", "", "", "", 
//					"Times", "", "", "", "", "", "", "", "", "", "", "", 
//					"Stats", "", "", "", "", "Result sizes for verification", 
//					"", "", "", "", "", "", "", "", "", "", "", "", "GC"},
				{"Index", "data", "dim", "bits", "N", "calcMem", 
						"memory", "memory/n", "gen", 
						"load", "load/n", "q1/n", "q2/n", "pq1/n", "pq2/n", "up1/n", "up2/n", 
						"1-NN1", "1-NN2", "10-NN1", "10-NN2", 
						"unload", "unload/n", 
						"nodes", "postLen", "AHC", "NT", "NTinternal", 
						"q1-n", "q2-n", "q1p-n", "q2p-n", "up1-n", "up2-n", 
						"d1-1NN", "d2-1NN", "d1-kNN", "d2-kNN", 
						"load-s", "load-t", "w-query-s", "w-query-t", 
						"p-query-s", "p-query-t", "update-s", "update-t", 
						"1-NN-s", "1-NN-t", "10-NN-s", "10-NN-t", 
						"unload-s", "unload-t", "msg"},	
//				{"", "", "",    "",     "",  "MiB", "MiB", "MiB", "bytes", 
//							"[ms]", "[ms]", 
//							"[ns/result]", "[ns/call]", "[ns/call]", "[ns/call]", 
//							"[ns/call]", "", "", "", "", "", "", "", "", "", "", 
//							"", "", "", "", "", "", "", "", 
//							"[MB]", "[ms]", "[MB]", "[ms]", 
//							"[MB]", "[ms]", "[MB]", "[ms]", 
//							"[MB]", "[ms]", "[MB]", "[ms]", "[MB]", "[ms]"}		
		};
		
		String[] ret = new String[h2.length];
		for (int i = 0; i < h2.length; i++) {
			StringBuilder sb = new StringBuilder();
			for (String col: h2[i]) {
				sb.append(col);
				sb.append(D);
			}
			ret[i] = sb.toString();
		}
		return ret;
	}
	
	public String testDescription1() {
		String ret = "";
		ret += INDEX.name();
		ret += "-" + (isRangeData ? "R" : "P");
		return ret;
	}
	
	public String testDescription2() {
		return TEST.name() + "(" + param1 + "," + param2 + "," + paramStr + ")";
	}
	
	@Override
	public String toString() {
		String D = "\t"; //delimiter
		String ret = "";

		ret += testDescription1() + "-" + SEEDmsg + D;
		ret += testDescription2() + D;

		ret += cfgNDims + D + cfgNBits + D + cfgNEntries + D; 
		ret += (statSCalc>>20) + D + statSjvmF + D + statSjvmE + D; 
		ret += statTGen + D;
		
		//times
		ret += statTLoad + D + (statTLoad*1000000/cfgNEntries) + D;
		//			ret += statTq1 + D + statTq1E + D + statTq2 + D + statTq2E + D + statTqp1 + D + statTqp1E + D + statTqp2 + D + statTqp2E + D;
		ret += statTq1E + D + statTq2E + D + statTqp1E + D + statTqp2E + D; 
		ret += statTu1E + D + statTu2E + D;
		ret += statTqk1_1E + D + statTqk1_2E + D;
		ret += statTqk10_1E + D + statTqk10_2E + D;
		ret += statTUnload + D + (statTUnload*1000000/cfgNEntries) + D;
		
		//Result sizes, etc
		ret += statNnodes + D + statNpostlen + D + statNNodeAHC + D + statNNodeNT + D + statNNodeInternalNT + D;
		ret += statNq1 + D + statNq2 + D + statNqp1 + D + statNqp2 + D;
		ret += statNu1 + D + statNu2 + D;
		ret += statDqk1_1 + D + statDqk1_2 + D + statDqk10_1 + D + statDqk10_2 + D;

		//GC
		ret += statGcDiffL/1000000 + D + statGcTimeL + D;
		ret += statGcDiffWq/1000000 + D + statGcTimeWq + D;
		ret += statGcDiffPq/1000000 + D + statGcTimePq + D;
		ret += statGcDiffUp/1000000 + D + statGcTimeUp + D;
		ret += statGcDiffK1/1000000 + D + statGcTimeK1 + D;
		ret += statGcDiffK10/1000000 + D + statGcTimeK10 + D;
		ret += statGcDiffUl/1000000 + D + statGcTimeUl + D;
		ret += assortedInfo;
		if (exception != null) {
			ret += D + exception.getMessage();
		}
		return ret;
	}
	public void setN(int N) {
		cfgNEntries = N;
	}
	public int getN() {
		return cfgNEntries;
	}

	public static TestStats aggregate(List<TestStats> stats) {
		TestStats t1 = stats.get(0);
		//TestStats avg = new TestStats(t1.TEST, t1.INDEX, t1.cfgNEntries, t1.cfgNDims, 
		//		t1.isRangeData, t1.param1);
		TestStats avg = t1.cloneStats();
//		avg.cfgNBits = t1.cfgNBits;
//		avg.cfgNEntries = t1.cfgNEntries;
//		avg.param2 = t1.param2;
//		avg.paramStr = t1.paramStr;
//		avg.paramWQSize = t1.paramWQSize;

		int cnt = 1;
		for (int i = 1; i < stats.size(); i++) {
			TestStats t = stats.get(i);

//			avg.testClass = t.testClass;
//			avg.indexClass = t.indexClass;

			if (t.exception != null) {
				//skip failed results
				continue;
			}
			avg.statTGen += t.statTGen;
			avg.statTLoad += t.statTLoad;
			avg.statTUnload += t.statTUnload;
			avg.statTq1 += t.statTq1;
			avg.statTq1E += t.statTq1E;
			avg.statTq2 += t.statTq2;
			avg.statTq2E += t.statTq2E;
			avg.statTqp1 += t.statTqp1;
			avg.statTqp1E += t.statTqp1E;
			avg.statTqp2 += t.statTqp2;
			avg.statTqp2E += t.statTqp2E;
			avg.statTqk1_1 += t.statTqk1_1;
			avg.statTqk1_1E += t.statTqk1_1E;
			avg.statTqk1_2 += t.statTqk1_2;
			avg.statTqk1_2E += t.statTqk1_2E;
			avg.statTqk10_1 += t.statTqk10_1;
			avg.statTqk10_1E += t.statTqk10_1E;
			avg.statTqk10_2 += t.statTqk10_2;
			avg.statTqk10_2E += t.statTqk10_2E;
			avg.statTu1 += t.statTu1;
			avg.statTu1E += t.statTu1E;
			avg.statTu2 += t.statTu2;
			avg.statTu2E += t.statTu2E;
			avg.statNnodes += t.statNnodes;
			//avg.statNBits += t.statNBits;
			//avg.statNDims += t.statNDims;
			//avg.statNEntries += t.statNEntries;
			avg.statNpostlen += t.statNpostlen;
			avg.statNNodeAHC += t.statNNodeAHC;
			avg.statNNodeNT += t.statNNodeNT;
			avg.statNNodeInternalNT += t.statNNodeInternalNT;
			avg.statNq1 += t.statNq1;
			avg.statNq2 += t.statNq2;
			avg.statNqp1 += t.statNqp1;
			avg.statNqp2 += t.statNqp2;
			avg.statDqk1_1 += t.statDqk1_1;
			avg.statDqk1_2 += t.statDqk1_2;
			avg.statDqk10_1 += t.statDqk10_1;
			avg.statDqk10_2 += t.statDqk10_2;
			avg.statNu1 += t.statNu1;
			avg.statNu2 += t.statNu2;
			avg.statSCalc += t.statSCalc;
			avg.statSjvmF += t.statSjvmF;
			avg.statSjvmE += t.statSjvmE;
			avg.statGcDiffL += t.statGcDiffL;
			avg.statGcTimeL += t.statGcTimeL;
			avg.statGcDiffWq += t.statGcDiffWq;
			avg.statGcTimeWq += t.statGcTimeWq;
			avg.statGcDiffPq += t.statGcDiffPq;
			avg.statGcTimePq += t.statGcTimePq;
			avg.statGcDiffUp += t.statGcDiffUp;
			avg.statGcTimeUp += t.statGcTimeUp;
			avg.statGcDiffUl += t.statGcDiffUl;
			avg.statGcTimeUl += t.statGcTimeUl;
			//we just use the info of the last test run
			avg.assortedInfo = t.assortedInfo;
			cnt++;
		}

		avg.statTGen /= (double)cnt;
		avg.statTLoad /= (double)cnt;
		avg.statTUnload /= (double)cnt;
		avg.statTq1 /= (double)cnt;
		avg.statTq1E /= (double)cnt;
		avg.statTq2 /= (double)cnt;
		avg.statTq2E /= (double)cnt;
		avg.statTqp1 /= (double)cnt;
		avg.statTqp1E /= (double)cnt;
		avg.statTqp2 /= (double)cnt;
		avg.statTqp2E /= (double)cnt;
		avg.statTqk1_1 /= (double)cnt;
		avg.statTqk1_1E /= (double)cnt;
		avg.statTqk1_2 /= (double)cnt;
		avg.statTqk1_2E /= (double)cnt;
		avg.statTqk10_1 /= (double)cnt;
		avg.statTqk10_1E /= (double)cnt;
		avg.statTqk10_2 /= (double)cnt;
		avg.statTqk10_2E /= (double)cnt;
		avg.statTu1 /= (double)cnt;
		avg.statTu1E /= (double)cnt;
		avg.statTu2 /= (double)cnt;
		avg.statTu2E /= (double)cnt;
		avg.statNnodes /= (double)cnt;
		//avg.statNBits /= (double)cnt;
		//avg.statNDims /= (double)cnt;
		//avg.statNEntries /= (double)cnt;
		avg.statNpostlen /= (double)cnt;
		avg.statNNodeAHC /= (double)cnt;
		avg.statNNodeNT /= (double)cnt;
		avg.statNNodeInternalNT /= (double)cnt;
		avg.statNq1 /= (double)cnt;
		avg.statNq2 /= (double)cnt;
		avg.statNqp1 /= (double)cnt;
		avg.statNqp2 /= (double)cnt;
		avg.statDqk1_1 /= (double)cnt;
		avg.statDqk1_2 /= (double)cnt;
		avg.statDqk10_1 /= (double)cnt;
		avg.statDqk10_2 /= (double)cnt;
		avg.statNu1 /= (double)cnt;
		avg.statNu2 /= (double)cnt;
		avg.statSCalc /= (double)cnt;
		avg.statSjvmF /= (double)cnt;
		avg.statSjvmE /= (double)cnt;
		avg.statGcDiffL /= (double)cnt;
		avg.statGcTimeL /= (double)cnt;
		avg.statGcDiffWq /= (double)cnt;
		avg.statGcTimeWq /= (double)cnt;
		avg.statGcDiffPq /= (double)cnt;
		avg.statGcTimePq /= (double)cnt;
		avg.statGcDiffUp /= (double)cnt;
		avg.statGcTimeUp /= (double)cnt;
		avg.statGcDiffUl /= (double)cnt;
		avg.statGcTimeUl /= (double)cnt;

		avg.SEEDmsg = "AVG-" + cnt + "/" + stats.size();

		return avg;
	}

//	public Candidate createTree(int size, TestStats ts) {
//		if (isRangeData) {
//			RectangleIndex<?> ri = createRI(INDEX, cfgNDims, size);
//			return new RectangleIndexCandidate(ri, ts);
//		} else {
//			PointIndex<?> pi = createPI(INDEX, cfgNDims, size);
//			return new PointIndexCandidate(pi, ts);
//		}
//	}
}