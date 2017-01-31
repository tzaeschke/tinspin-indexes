/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import java.util.List;
import java.util.Random;

import org.tinspin.index.critbit.BitTools;

public abstract class Candidate {
	
	/**
	 * This method is invoked at the end of the test run
	 * to allow freeing of allocated resources
	 */
	public abstract void release();
	
	public abstract void load(double[] data, int idxDim);

	/**
	 * This method is called before the actual point
	 * query, providing the underlying tree with the
	 * possibility to pre-process the query data
	 * @param q raw query data
	 * @return pre-processed query data
	 */
	public abstract Object preparePointQuery(double[][] q);

	/**
	 * Called when the tree should execute the point
	 * queries defined by the arguments.
	 * @param qA pre-processed query data
	 * 
	 * @return number of point queries that matched a point
	 */
	public abstract int pointQuery(Object qA);

	/**
	 * Called when the tree should execute the 
	 * k-nearest-neighbor queries defined by the arguments.
	 * @param k number of desired neighbors
	 * @param center of query
	 * 
	 * @return the squared distance to the returned points
	 */
	public double knnQuery(int k, double [] center) {
		if (k < 1 || center == null) {
			throw new IllegalArgumentException();
		}
		return -1;
	}

	/**
	 * Called when the tree should delete all the points
	 * it contains. The deletion should happen in N/2 steps
	 * where the first and last element (in order of insertion)
	 * that are still in the tree should be deleted
	 * 
	 * @return number of effectively deleted points
	 */
	public abstract int unload();

	/**
	 * Called when tree should execute a range query
	 * 
	 * @param min lower corner
	 * @param max upper corner
	 * @return number of matched points
	 */
	public abstract int query(double[] min, double[] max);
	
	
	public abstract int update(double[][] updateTable);

	public List<?> queryToList(double[] min, double[] max) {
		if (min == null || max == null) {
			throw new IllegalArgumentException();
		}
		throw new UnsupportedOperationException();
	}
	
	
	protected void run(Candidate x, int N, int DIM) {
		int NPQ = 1000;
		Random R = new Random(0);
		double[] data = 
				//new double[]{0.01,0.01,0.01, 0.02,0.02,0.02, 0.03,0.03,0.03, 0.04,0.04,0.04};
				new double[N*DIM];
		for (int i = 0; i < N*DIM; i++) {
			data[i] = R.nextDouble();
		}
		long t1 = System.currentTimeMillis();
		x.load(data, DIM);
		long t2 = System.currentTimeMillis();
		System.out.println("load time [ms]: " + (t2-t1) + " ms");
		
		//point queries
		double[][] qA = new double[][]{{0.00,0.00,0.00}, {0.02,0.02,0.02}};
		Object qAP = x.preparePointQuery(qA);
		t1 = System.currentTimeMillis();
		int np = 0;
		for (int i = 0; i < NPQ; i++) {
			np += x.pointQuery(qAP);
		}
		
		t2 = System.currentTimeMillis();
		System.out.println("point query: " + np);
		System.out.println("point query time [ms]: " + (t2-t1) + "  = " + (t2-t1)/NPQ + " ms/q");
		
		//range queries
		//double[] qD = new double[]{0.02,0.04,0.02, 0.04,0.02,0.04};
		//double[] qD = new double[]{0.02,0.031, 0.02,0.031, 0.02,0.031};
		double[][] qD = new double[NPQ][];
		for (int i = 0; i < NPQ; i++) {
			double[] q = new double[DIM<<1];
			for (int j = 0; j < DIM; j++) {
				q[j*2] = data[i*DIM+j];
				q[j*2+1] = data[i*DIM+j]+0.001;
			}
			qD[i] = q;
		}
		int nq = 0;
		t1 = System.currentTimeMillis();
		for (int i = 0; i < NPQ; i++) {
//			nq += x.query(qD[i], N, DIM); // TODO: replace with new query(min, max) method
			if (i%10==0) System.out.print('.');
		}
		t2 = System.currentTimeMillis();
		System.out.println();
		System.out.println("range query: " + nq);
		System.out.println("range query time [ms]: " + (t2-t1) + "  = " + (t2-t1)/NPQ + " ms/q");
//		int nu = x.unload();
//		System.out.println("unload: " + nu);
	}
	
	protected void runSmokeTest(Candidate x, int dims) {
//		Random R = new Random(0);
		double[] data = new double[]{
				0.01,0.01,0.01, 0.02,0.02,0.02, 0.03,0.03,0.03, 0.04,0.04,0.04};
		int N = data.length/dims;
		int NPQ = 1000 < N ? 1000 : N;
		int NQ = 1;
//		for (int i = 0; i < N*DIM; i++) {
//			data[i] = R.nextDouble();
//		}
		long t1 = System.currentTimeMillis();
		x.load(data, dims);
		long t2 = System.currentTimeMillis();
		System.out.println("load time [ms]: " + (t2-t1) + " ms");
		
		//point queries
		double[][] qA = new double[][]{{0.00,0.00,0.00}, {0.02,0.02,0.02}};
		Object qAP = x.preparePointQuery(qA);
		t1 = System.currentTimeMillis();
		int np = 0;
		for (int i = 0; i < NPQ; i++) {
			np += x.pointQuery(qAP);
		}
		
		t2 = System.currentTimeMillis();
		System.out.println("point query hits: " + np);
		System.out.println("point query time [ms]: " + (t2-t1) + "  = " + (t2-t1)/NPQ + " ms/q");
		
		//range queries
		//double[] qD = new double[]{0.02,0.04,0.02, 0.04,0.02,0.04};
		//double[] qD = new double[]{0.02,0.031, 0.02,0.031, 0.02,0.031};
		double[][] qDLow = new double[NPQ][];
		double[][] qDUpp = new double[NPQ][];
		for (int i = 0; i < NPQ; i++) {
			double[] qLow = new double[dims];
			double[] qUpp = new double[dims];
			for (int j = 0; j < dims; j++) {
				qLow[j] = data[i*dims+j];
				qUpp[j] = data[i*dims+j]+0.001;
			}
			qDLow[i] = qLow;
			qDUpp[i] = qUpp;
		}
		int nq = 0;
		t1 = System.currentTimeMillis();
		for (int i = 0; i < NQ; i++) {
			nq += x.query(qDLow[i], qDUpp[i]);
			if (i%10==0) System.out.print('.');
		}
		t2 = System.currentTimeMillis();
		System.out.println();
		System.out.println("range query hits: " + nq);
		System.out.println("range query time [ms]: " + (t2-t1) + "  = " + (t2-t1)/NPQ + " ms/q");
		int nu = x.unload();
		System.out.println("unload: " + nu);
	}

	public void getStats(TestStats S) {
		//Nothing to do for trees other than PhTrees
		assert(S != null);
	}

	public boolean supportsWindowQuery() {
		return true;
	}
	
	public boolean supportsPointQuery() {
		return true;
	}

	public boolean supportsKNN() {
		return false;
	}
	
	public boolean supportsUpdate() {
		return true;
	}

	public boolean supportsUnload() {
		return true;
	}

	/**
	 * Float to long.
	 * @param f
	 * @return long.
	 */
	static long f2l(double f) {
		return BitTools.toSortableLong(f);
	}

	static void f2l(double[] f, long[] l) {
		BitTools.toSortableLong(f, l);
	}

	/**
	 * Float to long.
	 * @param f
	 * @return long.
	 */
	static double l2f(long l) {
		return BitTools.toDouble(l);
	}

	static void l2f(long[] l, double[] f) {
		BitTools.toDouble(l, f);
	}

	static double dist(double[] a, double[] b) {
		double dist = 0;
		for (int i = 0; i < a.length; i++) {
			double d =  a[i]-b[i];
			dist += d*d;
		}
		return Math.sqrt(dist);
	}
	
	static double distRCenter(double[] center, double[] rLower, double[] rUpper) {
		double dist = 0;
		for (int i = 0; i < center.length; i++) {
			double d = center[i]-(rUpper[i]+rLower[i])/2;
			dist += d*d;
		}
		return Math.sqrt(dist);
	}
	
	static double distREdge(double[] center, double[] rLower, double[] rUpper) {
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
	
	@Override
	public String toString() {
		return "Please provide configuration information for "
				+ " every test candidate class in the toString() method.";
	}

	public abstract String toStringTree();
}
