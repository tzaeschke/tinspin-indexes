/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import static org.junit.Assert.assertNotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;


import ch.ethz.globis.tinspin.IndexHandle;
import ch.ethz.globis.tinspin.data.AbstractTest;
import ch.ethz.globis.tinspin.wrappers.Candidate;
import ch.ethz.globis.tinspin.TestStats;
import org.tinspin.index.array.PointArray;
import org.tinspin.index.array.RectArray;
import org.tinspin.index.test.util.TestInstances.IDX;
import org.tinspin.index.test.util.TestInstances.TST;


/**
 * Main test runner class.
 * The test runner can be executed directly or remotely in a separate
 * process via the TestManager.
 *
 * @author Tilmann Zaeschke
 */
public class TestRunner {
	
	private static final SimpleDateFormat FT = new SimpleDateFormat ("yyyy-MM-dd' 'HH:mm:ss");

	public static boolean USE_NEW_QUERIES = true;

	private final TestStats S;
	private Random R;
	private double[] data;
	private Candidate tree;
	private AbstractTest test = null;

	
	public static void main(String[] args) {
		//-Xmx28G -XX:+UseConcMarkSweepGC -Xprof -XX:MaxInlineSize=0 -XX:FreqInlineSize=0 -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining 
		//-XX:+PrintHeapAtGC - Prints detailed GC info including heap occupancy before and after GC
		//-XX:+PrintTenuringDistribution - Prints object aging or tenuring information
		
		final int DIM = 3;
		final int N = 1*1000*1000;
						
		//TestStats s0 = new TestStats(TST.CLUSTER, IDX.QKDZ, N, DIM, true, 5);
		TestStats s0 = new TestStats(TST.CUBE_P, IDX.QUAD, N, DIM, 1.0);
		//TestStats s0 = new TestStats(TST.OSM, IDX.PHC, N, 2, true, 1.0);
		//TestStats s0 = new TestStats(TST.CUBE, IDX.PHC, N, DIM, true, 1.0E-5);
		//TestStats s0 = new TestStats(TST.CLUSTER, IDX.RSZ, N, DIM, false, 3.4);
		//TestStats s0 = new TestStats(TST.CUBE, INDEX.QUAD_OLD, N, DIM, false, 1.0);
		//TestStats s0 = new TestStats(TST.OSM, INDEX.RSTAR, N, 2, false, 1.0);
		//s0.cfgWindowQueryRepeat = 1000;
		s0.cfgPointQueryRepeat = 1000*1000;
		//s0.cfgUpdateSize = 1000;

//		s0.cfgWindowQuerySize = 1;

		s0.setSeed(0);
		TestRunner test = new TestRunner(s0);
		TestStats s = test.run();
		System.out.println(test.tree.getNativeStats());
		System.out.println(s.toStringNew());
		//System.out.println(BitsLong.POOL.print());
//		System.out.println("AMM: " + PhIteratorNoGC.AMM1 + " / " + PhIteratorNoGC.AMM2 + " / " + PhIteratorNoGC.AMM3 + " / " + PhIteratorNoGC.AMM4 + " / " + PhIteratorNoGC.AMM5 + " / ");
//		System.out.println("VMM: " + PhIteratorNoGC.VMM1 + " / " + PhIteratorNoGC.VMM2 + " / " + PhIteratorNoGC.VMM3);
//		System.out.println("HCI-A/L/N: " + PhIteratorNoGC.HCIA + " / " + PhIteratorNoGC.HCIL + " / " + PhIteratorNoGC.HCIN);
	}
	

	public TestRunner(TestStats S) { 
		this.S = S;
		this.R = new Random(S.seed);
	}
	
	public TestStats run() {
		JmxTools.startUp();

		//load
		resetR();
		load(S);
		
//		if (false) {
//			TestDraw.draw(data, 2);
//			return S;
//		}

		assertNotNull(tree.toStringTree());
		
		//window queries
		if (S.cfgNDims <= 60 && tree.supportsWindowQuery()) {
			resetR();
			repeatQuery(S.cfgWindowQueryRepeat, 0);
			repeatQuery(S.cfgWindowQueryRepeat, 1);
			S.assortedInfo += " WINDOW_RESULTS=" + S.cfgWindowQuerySize;
		} else if (S.cfgNDims > 60 ) {
			System.err.println("WARNING: skipping window queries for dims=" + S.cfgNDims);
		} else {
			System.err.println("WARNING: window queries disabled");
		}
		
		//point queries.
		if (tree.supportsPointQuery()) {
			resetR();
			repeatPointQuery(S.cfgPointQueryRepeat, 0);
			repeatPointQuery(S.cfgPointQueryRepeat, 1);
		} else {
			System.err.println("WARNING: point queries disabled");
		}

		//kNN queries
		if (tree.supportsKNN()) {
			int repeat = getKnnRepeat(S.cfgNDims);
			S.assortedInfo += " KNN_REPEAT=" + repeat;
			resetR(12345);
			repeatKnnQuery(repeat, 0, 1);
			repeatKnnQuery(repeat, 1, 1);
			repeatKnnQuery(repeat, 0, 10);
			repeatKnnQuery(repeat, 1, 10);
		} else {
			System.err.println("WARNING: kNN queries disabled");
		}
		
		//update
		if (tree.supportsUpdate()) {
			S.assortedInfo += " UPD_DIST=" + test.maxUpdateDistance();
			resetR();
			update(0);
			update(1);
		} else {
			System.err.println("WARNING: update() disabled");
		}
		
		//unload
		if (tree.supportsUnload()) {
			unload();
		} else {
			System.err.println("WARNING: unload() disabled");
		}

		tree.getStats(S);

		tree.clear();
		tree.release();

		// TODO remove
//		if (tree != null) {
//			tree.clear();
//			tree.release();
//		}
		
		return S;
	} 


	/**
	 * This method sets the random seed to the default seed plus a given delta.
	 * This solves the problem that, for example, the kNN generator
	 * would generate the same points as the data generator, which
	 * resulted in 0.0 distance for all queried points. 
	 * @param delta
	 */
	private void resetR(int delta) {
		R.setSeed(S.seed + delta);
	}

	private void resetR() {
		R.setSeed(S.seed);
	}
	
	private int getKnnRepeat(int dims) {
		if ((S.TEST == TestInstances.TST.CLUSTER_P
				|| S.TEST == TestInstances.TST.CLUSTER_R)
				&& S.cfgNDims > 5 ) {
			S.cfgKnnQueryBaseRepeat /= 10;//100;
		}
		if (dims <= 3) {
			return S.cfgKnnQueryBaseRepeat;
		}
		if (dims <= 6) {
			return S.cfgKnnQueryBaseRepeat/10;
		}
		if (dims <= 10) {
			return S.cfgKnnQueryBaseRepeat/10;
		}
		return S.cfgKnnQueryBaseRepeat/50;
	}
	
	private void load(TestStats ts) {
		log(date() + "generating data ...");
		long t1g = System.currentTimeMillis();

		test = ts.TEST.createInstance(R, ts);

//		if (ts.isRangeData) {
//			test = TestRectangle.create(R, ts);
//		} else {
//			test = TestPoint.create(R, ts);
//		}
//
//		switch (ts.TEST) {
//		case CUBE:
//		case CLUSTER:
//		case CSV:
//		case OSM:
//		case TIGER:
//		case TOUCH:
//		case VORTEX: {
			data = test.generate();
//			break;
//		}
//		//case ASPECT:
//		case MBR_SIZE: {
//			//IS_POINT_DATA = PR_TestSize.generate(R, cfgDataLen, N, DIM, 0.001f);
//			//IS_POINT_DATA = PR_TestSize.generate(R, cfgDataLen, N, DIM, 0.02f);
//			//data = PR_TestAspect.generate(R, cfgDataLen, N, DIM, 1e3f);//10.0f);
//			data = test.generate();
//			if (!ts.isRangeData) throw new IllegalStateException();
//			break;
//		}
//		default:
//			throw new UnsupportedOperationException("No data for: " + ts.TEST.name());
//		}
		long t2g = System.currentTimeMillis();
		log("data generation finished in: " + (t2g-t1g));
		S.statTGen = t2g-t1g;
		
		int dims = S.cfgNDims;
		int N = S.cfgNEntries;

		long memTree = MemTools.getMemUsed();
		if (ts.paramEnforceGC) {
			MemTools.cleanMem(N, memTree);
		}

		
		//load index
		log(date() + "loading index ...");
		memTree = MemTools.getMemUsed();
		JmxTools.reset();
		long t1 = timer();

		if (ts.isRangeData) {
			tree = RectangleIndexCandidate.create(ts);
		} else {
			if (ts.isMultimap) {
				tree = PointIndexMMCandidate.create(ts);
			} else {
				tree = PointIndexCandidate.create(ts);
			}
		}

		tree.load(data, dims);

		long t2 = timer();
		S.statGcDiffL = JmxTools.getDiff();
		S.statGcTimeL = JmxTools.getTime();
		log("loading finished in: " + (long)toMS(t1, t2) + "ms");
		if (ts.paramEnforceGC) {
			S.statSjvmF = MemTools.cleanMem(N, memTree);
		}
		S.statSjvmE = S.statSjvmF / N;
		S.statTLoad = (long) toMS(t1, t2);
		S.statPSLoad = opsPerSec(N, t1, t2);
		
		tree.getStats(S);
		S.assortedInfo += tree.toString();
		
		//This avoid premature garbage collection...
		log("loaded objects: " + N + " " + data[0]);
	}
		
	private void repeatQuery(int repeat, int round) {
		int dims = S.cfgNDims;
		log("N=" + S.cfgNEntries);
		log(date() + "querying index ... repeat = " + repeat);
		double[][] lower = new double[repeat][dims]; 
		double[][] upper = new double[repeat][dims];
		test.generateWindowQueries(lower, upper);

		long t00 = timer();
		int n;
		long t1, t2;
		//Use result count from first run as control value
		int control = -1;
		int nTotalRepeat = 0;
		do {
			JmxTools.reset();
			t1 = timer();
			n = 0;
			if (tree.supportsWindowQuery()) {
				n = repeatQueries(lower, upper);
			} else {
				n = -1;
			}
			t2 = timer();
			if (control == -1) {
				control = n;
			}
			logNLF("*");
			nTotalRepeat += repeat;
		} while (toMS(t00, timer()) < S.minimumMsPerTest);
		if (t2 == t1) {
			t2++;
		}

		log("n/q=" + n/(double)lower.length);
		log("Query time: " + toMS(t1, t2) + " ms -> " +
				toMS(t1, t2)/(double)repeat + " ms/q -> " +
				toNSPerOp(t1, t2, repeat) + " ns/q/r  (n=" + n + ")" +
				"; total queries: " + nTotalRepeat);
		if (round == 0) {
			S.statTq1 = (long) toMS(t1, t2);
			S.statTq1E = toNSPerOp(t1, t2, repeat);
			S.statPSq1 = opsPerSec(repeat, t1, t2);
			S.statNq1 = control;
		} else {
			S.statTq2 = (long) toMS(t1, t2);
			S.statTq2E = toNSPerOp(t1, t2, repeat);
			S.statPSq2 = opsPerSec(repeat, t1, t2);
			S.statNq2 = control;
		}
		S.statGcDiffWq = JmxTools.getDiff();
		S.statGcTimeWq = JmxTools.getTime();
	}
	
	private void repeatPointQuery(int repeat, int round) {
		log(date() + "point queries ...");
		//prepare query
		//TODO return only double[], convert inside query function!
		int[] ids = new int[repeat];
		double[][] qDA = preparePointQuery(repeat, ids);
		Object q = tree.preparePointQuery(qDA);

		long t00 = timer();
		int n;
		long t1, t2;
		//Use result count from first run as control value
		int control = -1;
		do {
			JmxTools.reset();

			//query
			t1 = timer();
			n = tree.pointQuery(q, ids);
			t2 = timer();
			if (control == -1) {
				control = n;
			}
			logNLF("*");
		} while (toMS(t00, timer()) < S.minimumMsPerTest);
		if (t2 == t1) {
			t2++;
		}

		log("Elements found: " + n + " -> " + n/(double)repeat);
		log("Query time: " + toMS(t1, t2) + " ms -> " +
				toMS(t1, t2)/(double)repeat + " ms/q -> " +
				toNSPerOp(t1, t2, repeat) + " ns/q");
		if (round == 0) {
			S.statTqp1 = (long) toMS(t1, t2);
			S.statTqp1E = toNSPerOp(t1, t2, repeat);
			S.statPSqp1 = opsPerSec(repeat, t1, t2);
			S.statNqp1 = control;
		} else {
			S.statTqp2 = (long) toMS(t1, t2);
			S.statTqp2E = toNSPerOp(t1, t2, repeat);
			S.statPSqp2 = opsPerSec(repeat, t1, t2);
			S.statNqp2 = control;
		}
		S.statGcDiffPq = JmxTools.getDiff();
		S.statGcTimePq = JmxTools.getTime();
	}

	private double[][] preparePointQuery(int repeat, int[] ids) {
		int dims = S.cfgNDims;
		double[][] qA;
		if (!S.isRangeData) {
			qA = new double[repeat][];
			for (int i = 0; i < repeat; i++) {
				qA[i] = generateQueryPointD(S.cfgNEntries, dims, ids, i);
			}
		} else {
			qA = new double[repeat*2][];
			for (int i = 0; i < repeat; i++) {
				double[] lo = new double[dims];
				double[] hi = new double[dims];
				generateQueryPointDRect(lo, hi, S.cfgNEntries, dims);
				qA[2*i] = lo;
				qA[2*i+1] = hi;
			}
		}
		return qA;
	}

	private void repeatKnnQuery(int repeat, int round, int k) {
		log(date() + "kNN queries ...");
		//prepare query
		double[][] q = prepareKnnQuery(repeat);

		long t00 = timer();
		long t1, t2;
		double dist;
		//Use average distance of first run as control value
		double control = -1;
		do {
			JmxTools.reset();

			//query
			dist = 0;
			t1 = timer();
			for (int i = 0; i < repeat; i++) {
				dist += tree.knnQuery(k, q[i]);
			}
			t2 = timer();
			if (control == -1) {
				control = dist/repeat/k;
			}
			logNLF("*");
		} while (toMS(t00, timer()) < S.minimumMsPerTest);
		if (t2 == t1) {
			t2++;
		}

		log("Element distance: " + dist + " -> " + control);
		log("kNN query time (repeat=" + repeat + "): " + toMS(t1, t2) + " ms -> " +
				toMS(t1, t2)/(double)repeat + " ms/q -> " +
				toNSPerOp(t1, t2, k*repeat) + " ns/q/r");
		if (k == 1) {
			if (round == 0) {
				S.statTqk1_1 = (long) toMS(t1, t2);
				S.statTqk1_1E = toNSPerOp(t1, t2, repeat);
				S.statPSqk1_1 = opsPerSec(repeat, t1, t2);
				S.statDqk1_1 = control;
			} else {
				S.statTqk1_2 = (long) toMS(t1, t2);
				S.statTqk1_2E = toNSPerOp(t1, t2, repeat);
				S.statPSqk1_2 = opsPerSec(repeat, t1, t2);
				S.statDqk1_2 = control;
			}
			S.statGcDiffK1 = JmxTools.getDiff();
			S.statGcTimeK1 = JmxTools.getTime();
		} else {
			if (round == 0) {
				S.statTqk10_1 = (long) toMS(t1, t2);
				S.statTqk10_1E = toNSPerOp(t1, t2, repeat);
				S.statPSqk10_1 = opsPerSec(repeat, t1, t2);
				S.statDqk10_1 = control;
			} else {
				S.statTqk10_2 = (long) toMS(t1, t2);
				S.statTqk10_2E = toNSPerOp(t1, t2, repeat);
				S.statPSqk10_2 = opsPerSec(repeat, t1, t2);
				S.statDqk10_2 = control;
			}
			S.statGcDiffK10 = JmxTools.getDiff();
			S.statGcTimeK10 = JmxTools.getTime();
		}
	}
	
	private double[][] prepareKnnQuery(int repeat) {
		int dims = S.cfgNDims;
		double[][] qA;
		if (!S.isRangeData) {
			qA = new double[repeat][];
			for (int i = 0; i < repeat; i++) {
				qA[i] = generateKnnQueryPointD(dims);
			}
		} else {
			qA = new double[repeat*2][];
			for (int i = 0; i < repeat; i++) {
				double[] lo = new double[dims];
				double[] hi = new double[dims];
				generateKnnQueryPointDRect(lo, hi, dims);
				qA[2*i] = lo;
				qA[2*i+1] = hi;
			}
		}
		return qA;
	}

	private int repeatQueries(double[][] lower, double[][] upper) {
		long t0 = System.currentTimeMillis();
		int n=0;
		int mod = lower.length / 100;
		for (int i = 0; i < lower.length; i++) {
			n += tree.query(lower[i], upper[i]);
			if (i%mod == 0 && System.currentTimeMillis()-t0 > 1000) System.out.print('.');
		}
		return n;
	}
	
	static void log(String string) {
		System.out.println(string);
	}

	static void logNLF(String string) {
		System.out.print(string);
	}


	private double[] generateQueryPointD(final int N, final int dims, int[] ids, int idPos) {
		double[] xyz = new double[dims];
		int pos = R.nextInt(N*2);
		if (pos >= N) {
			//randomise
			for (int d = 0; d < dims; d++) {
				xyz[d] = test.min(d) + R.nextDouble()*test.len(d);
			}
			ids[idPos] = -1;
		} else {
			//get existing element
			System.arraycopy(data, pos*dims, xyz, 0, dims);
			ids[idPos] = pos;
		}
		return xyz;
	}
	
	private void generateQueryPointDRect(double[] lo, double[] hi, final int N, final int dims) {
		int pos = R.nextInt(N*2);
		if (pos >= N) {
			//randomise
			for (int d = 0; d < dims; d++) {
				lo[d] = test.min(d) + R.nextDouble()*test.len(d);
				hi[d] = lo[d] + R.nextDouble()*test.len(d)/1000.;
			}
		} else {
			//get existing element
			System.arraycopy(data, pos*dims*2, lo, 0, dims);
			System.arraycopy(data, pos*dims*2+dims, hi, 0, dims);
		}
	}
	
	private double[] generateKnnQueryPointD(final int dims) {
		double[] xyz = new double[dims];
		//randomise
		for (int d = 0; d < dims; d++) {
			xyz[d] = test.min(d) + R.nextDouble()*test.len(d);
		}
		return xyz;
	}
	
	private void generateKnnQueryPointDRect(double[] lo, double[] hi, final int dims) {
		//randomise
		for (int d = 0; d < dims; d++) {
			lo[d] = test.min(d) + R.nextDouble()*test.len(d);
			hi[d] = lo[d] + R.nextDouble()*test.len(d)/1000.;
		}
	}
	
	private void update(int round) {
		log(date() + "updates ...");

		long t00 = timer();
		int n;
		long t;
		//Use result count from first run as control value
		int control = -1;
		do {
			n = 0;
			t = 0;
			double[][] u = null; //2 points, 2 versions
			//TODO we actually use a different dataset for each run here,
			//     this makes it a bit less comparable if different indexes
			//     perform different number of runs. But this seems to be the
			//     (relatively) most accurate solution.
			int nUpdates = S.cfgUpdateSize > S.cfgNEntries/4 ? S.cfgNEntries/4 : S.cfgUpdateSize;
			int[] ids = new int[nUpdates];
			for (int i = 0; i < S.cfgUpdateRepeat; i++) {
				//prepare query
				u = test.generateUpdates(nUpdates, data, u, ids);
				JmxTools.reset();
				//updates
				long t1 = timer();
				n += tree.update(u, ids);
				long t2 = timer();
				t += t2-t1;
				S.statGcDiffUp += JmxTools.getDiff();
				S.statGcTimeUp += JmxTools.getTime();
			}
			if (control == -1) {
				control = n;
			}
			logNLF("*");
		} while (toMS(t00, timer()) < S.minimumMsPerTest);

		log("Elements updated: " + n + " -> " + n);
		log("Update time: " + toMS(t) + " ms -> " + toNSPerOp(t, n) + " ns/update");
		if (round == 0) {
			S.statTu1 = (long) toMS(t);
			S.statTu1E = toNSPerOp(t, n);
			S.statPSu1E = opsPerSec(n, t);
			S.statNu1 = control;
		} else {
			S.statTu2 = (long) toMS(t);
			S.statTu2E = toNSPerOp(t, n);
			S.statPSu2E = opsPerSec(n, t);
			S.statNu2 = control;
		}
	}

	private void unload() {
		log("Unloading...");
		JmxTools.reset();

		long t1 = timer();
		int n = tree.unload();
		long t2 = timer();

		log("Deletion time: " + toMS(t1, t2) + " ms -> " +
				toNSPerOp(t1, t2, S.cfgNEntries) + " ns/delete");
		S.statTUnload = (long) toMS(t1, t2);
		S.statPSUnload = opsPerSec(n, t1, t2);
		S.statGcDiffUl = JmxTools.getDiff();
		S.statGcTimeUl = JmxTools.getTime();
		if (S.cfgNEntries != n) {
			System.err.println("Delete N/n: " + S.cfgNEntries + "/" + n);
			//throw new IllegalStateException("N/n: " + S.cfgNEntries + "/" + n);
		}
	}
	
	public TestStats getTestStats() {
		return S;
	}

	private String date() {
		return FT.format(new Date()) + " ";
	}

	public Candidate getCandidate() {
		return tree;
	}

	private static long timer() {
		return System.nanoTime();
	}

	private static long opsPerSec(int nOps, double t1, double t2) {
		return opsPerSec(nOps, t2-t1);
	}

	private static long opsPerSec(int nOps, double t) {
		return (long) (nOps / t * 1_000_000_000L);
	}

	private static double toMS(double t1, double t2) {
		return (t2-t1)/1_000_000;
	}

	private static double toMS(double t) {
		return t/1_000_000;
	}

	//	private static double toSec(double t1, double t2) {
	//		return toMS(t1, t2) * 1_000;
	//	}

	private static long toNSPerOp(double t1, double t2, long nOps) {
		return toNSPerOp(t2 - t1, nOps);
	}

	private static long toNSPerOp(double t, long nOps) {
		return (long) t / nOps;
	}
}
