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
public class TestRectangleCube extends TestRectangle {

	public static final double MIN_X = 0;
	
	public TestRectangleCube(Random R, TestStats S) {
		super(R, S);
	}
	
	@Override
	public double[] generate() {
		double rectLen = S.cfgRectLen;
		log("Running: TestCube (" + rectLen + ")");
		
		int dims = S.cfgNDims;
		int nEntries = S.cfgNEntries;
		double[] data = new double[nEntries*dims*2];
		
		//query create cube
		double minRange = S.cfgDataLen-MIN_X-rectLen;
		int posMin = 0;
		int posMax = dims;
		for (int i = 0; i < nEntries; i++) {
			for (int d = 0; d < dims; d++) {
				data[posMin++] = MIN_X+R.nextDouble()*minRange;
				data[posMax++] = data[posMin-1]+R.nextDouble()*rectLen;
			}
			posMin += dims;
			posMax += dims;
		} 
		return data;
	}
}
