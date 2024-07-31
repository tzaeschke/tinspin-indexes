/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import ch.ethz.globis.tinspin.TestStats;

import java.util.Random;

/**
 * 
 * @author Tilmann Zaeschke
 */
public class TestBoxCube extends TestBox {

	public static final double MIN_X = 0;
	
	public TestBoxCube(Random R, TestStats S) {
		super(R, S);
	}
	
	@Override
	public double[] generate() {
		double rectLen = S.cfgRectLen;
		if (TestRunner.PRINT) {
			log("Running: TestCube (" + rectLen + "," + S.cfgDuplicates + ")");
		}
		
		int dims = S.cfgNDims;
		int nEntries = S.cfgNEntries;
		double[] data = new double[nEntries*dims*2];
		
		//query create cube
		double minRange = S.cfgDataLen-MIN_X-rectLen;
		for (int i = 0; i < nEntries; i += S.cfgDuplicates) {
			int posMin = dims * i * 2;
			int posMax = posMin + dims;
			for (int d = 0; d < dims; d++) {
				data[posMin + d] = MIN_X + R.nextDouble() * minRange;
				data[posMax + d] = data[posMin + d] + R.nextDouble() * rectLen;
			}
			for (int i2 = 1; i2 < S.cfgDuplicates && (i + i2) < getN(); i2++) {
				System.arraycopy(data, posMin, data, posMin + 2 * i2 * DIM, 2 * DIM);
			}
		}
		return data;
	}
}
