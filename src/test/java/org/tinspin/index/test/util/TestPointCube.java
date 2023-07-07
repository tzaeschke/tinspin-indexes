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
 * Creates a k-dimensional cuboid between 0.0 and 'param1' in every dimension,
 * randomly filled with points.
 * 
 * @author Tilmann Zaeschke
 */
public class TestPointCube extends TestPoint {

	public TestPointCube(Random R, TestStats S) {
		super(R, S);
	}

	/**
	 * @return Elements
	 */
	@Override
	public double[] generate() {
		log("Running: TestCube(" + S.cfgDataLen + ")");
		double[] data = new double[getN()*DIM];
		int dup = S.cfgDuplicates;
		for (int i = 0; i < getN(); i += dup) {
			int pos = DIM*i;
			for (int d = 0; d < DIM; d++) {
				data[pos+d] = R.nextDouble() * S.cfgDataLen;
			}
			for (int i2 = 1; i2 < S.cfgDuplicates && pos < getN()*DIM; ++i2) {
				System.arraycopy(data, i * DIM, data, (i + i2) * DIM, DIM);
			}
		}
		return data;
	}
}
