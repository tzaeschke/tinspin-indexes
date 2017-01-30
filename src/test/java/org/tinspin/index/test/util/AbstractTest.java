/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import java.util.Arrays;
import java.util.Random;

public abstract class AbstractTest {

	protected final Random R;
	protected int DIM;
	protected final double param1;
	protected final double param2;
	protected String paramStr;
	protected final TestStats S;
	//global min/max of data area
	protected double[] globalMin;
	protected double[] globalMax;

	protected AbstractTest(Random R, TestStats S) {
		this.R = R;
		this.S = S;
		this.DIM = S.cfgNDims;
		this.param1 = S.param1;
		this.param2 = S.param2;
		this.paramStr = S.paramStr;
		this.globalMin = new double[DIM];
		this.globalMax = new double[DIM];
		Arrays.fill(globalMin, 0);
		Arrays.fill(globalMax, S.cfgDataLen);
	}

	public int getN() {
		//This may change, for example when exceeding maximum size for
		//CSV, Tiger, OpenStreeMap datasets
		return S.cfgNEntries;
	}

	public final TestStats.TST getTestType() {
		return S.TEST;
	}

	/**
	 * 
	 * @return Maximum distance for each update().
	 */
	public double maxUpdateDistance() {
		double d = 0;
		for (int i = 0; i < globalMin.length; i++) {
			if (d < globalMax[i]-globalMin[i]) {
				d = globalMax[i]-globalMin[i];
			}
		}
		return d * 10e-5;
	}

	public abstract double[] generate();
	public abstract double[][] generateUpdates(int n, double[] data, double[][] ups);
	
	public void log(String str) {
		System.out.println(str);
	}
	
	public void logWarning(String str) {
		System.err.println(str);
	}
	
	public TestStats getTestStats() {
		return S;
	}
	
	public void generateWindowQueries(double[][] lower, double[][] upper) {
		int nEntries = S.cfgNEntries;
		if (nEntries < S.cfgWindowQuerySize*10) {
			//N < 10*000 ? -> N = 100
			nEntries = S.cfgWindowQuerySize*10;
		}

		//find minimum extent (maximum allowable query box size)
		double maxQueryLen = Double.MAX_VALUE;
		//Here is a fixed size version, returning 1% of the space.
		//final double qVolume = 0.01 * Math.pow(cfgDataLen, DIM);//(float) Math.pow(0.1, DIM); //0.01 for DIM=2
		double totalV = 1;
		for (int i = 0; i < DIM; i++) {
			double len = globalMax[i] - globalMin[i]; 
			totalV *= len;
			maxQueryLen = len < maxQueryLen ? len : maxQueryLen;  
		}
		double avgQueryVolume = S.cfgWindowQuerySize/(double)nEntries * totalV;
		//final double avgLen = Math.pow(avgVolume, 1./DIM);
		//final double avgLenVar = 0.5*avgLen;
		//final double minLen = 0.5*avgLen;
		for (int i = 0; i < lower.length; i++) {
			generateQuery(lower[i], upper[i], maxQueryLen, avgQueryVolume);
		}
	}
	
	/**
	 * Generate query rectangle.
	 * This method should be overwritten by tests that provide
	 * non-standard queries.
	 * @param min output: query box minimum
	 * @param max output: query box maximum
	 * @param maxLen maximum allowed length for a query box in any dimension.
	 * @param avgQVol Average expected volume of a query box
	 */
	public void generateQuery(double[] min, double[] max, 
			final double maxLen, final double avgQVol) {
		int dims = DIM;
		
		//query create cube
		double[] len = new double[min.length];
		int nTries = 0;
		do {
			double vol = 1;
			for (int d = 0; d < dims-1; d++) {
				//calculate the average required len 
				final double avgLen = Math.pow(avgQVol/vol, 1./dims);
				//create a len between 0.5 and 1.5 of the required length
				len[d] = (0.5*avgLen) + R.nextDouble()*(avgLen);
				len[d] = len[d] > maxLen*0.99 ? maxLen*0.99 : len[d];
				vol *= len[d];
			}
			//create cuboid/box of desired size by dropping random length
			len[dims-1] = avgQVol/vol;  //now the new len creates a rectangle/box of SIZE.
			if (nTries++ > 100) {
				System.out.println(Arrays.toString(len) + " vol=" + vol + " aVol=" + avgQVol);
				throw new IllegalStateException("dims=" + dims + "  N=" + S.cfgNEntries);
			}
		} while (len[dims-1] >= maxLen); //drop bad rectangles
		
		
		shuffle(len);
		
		//create location
		for (int d = 0; d < dims; d++) {
			min[d] = globalMin[d] + R.nextDouble()*(maxLen-len[d]);
			max[d] = min[d]+len[d];
			if (min[d]+len[d] >= globalMax[d]) {
				//drop bad rectangles 
				throw new RuntimeException();
			}
		}
	}

	private void shuffle(double[] da) {
		// Fisher-Yates shuffle
		for (int i = da.length - 1; i > 0; i--) {
			int index = R.nextInt(i + 1);
			double a = da[index];
			da[index] = da[i];
			da[i] = a;
		}
	}

	public double min(int d) {
		return globalMin[d];
	}

	public double max(int d) {
		return globalMax[d];
	}

	public double len(int d) {
		return globalMax[d] - globalMin[d];
	}

}
