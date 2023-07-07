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
 * CLUSTER 3.4/3.5 are introduced by:
 * [1] L. Arge, M. de Berg, H. J. Haverkort and K. Yi: 
 * "The Priority R-Tree: A Practically Efficient and Worst-Case Optimal R-Tree"
 * 
 * @author Tilmann Zaeschke
 */
public class TestRectangleCluster extends TestRectangle {

	private static int BOX_N = 10*1000;
	private static final double BOX_LEN = 0.00001;
	private static int GAUSS_N = 1000;
	private static final double GAUSS_SIGMA = 0.001;
	
	private static enum TYPE {
		HORIZONTAL05(3.5),
		HORIZONTAL04(3.4),
		GAUSS(5.0);
		final double x;
		TYPE(double x) {
			this.x = x;
		}
		static TYPE toType(double d) {
			for (TYPE t: values()) {
				if (t.x == d) {
					return t;
				}
			}
			throw new IllegalArgumentException("param1=" + d);
		}
	}
	
	
	public TestRectangleCluster(Random R, TestStats S) {
		super(R, S);
	}
	
	
	/**
	 * 1000 clusters with cfgDataLen/10,000 length with N/1000 points each.
	 * 
	 * From [1]:
	 * cluster: Our final dataset was designed to illustrate
	 * the worst-case behavior of the H, H4 and TGS R-trees.
	 * It is similar to the worst-case example discussed
	 * in Section 2. It consists of 10 000 clusters with centers
	 * equally spaced on a horizontal line. Each cluster
	 * consists of 1000 points uniformly distributed in a
	 * 0.000 01 * 0.000 01 square surrounding its center.
	 * 
	 * @return Elements
	 */
	
	//diagonal version
	@Override
	public double[] generate() {
		log("Running: TestCluster");
		double len = 1.0;
		
		switch (TYPE.toType(param1)) {
		case HORIZONTAL05: return generateHorizontal(len, 0.5); 
		case HORIZONTAL04: return generateHorizontal(len, 0.4);
		case GAUSS: return generateGauss(len);
		default:
			throw new IllegalArgumentException("param1=" + param1);
		}
	}
	
	//Proper version
	private double[] generateHorizontal(final double LEN, double offsYZ) {
		double MICROBOX_LEN = BOX_LEN * 0.001;
		int N_C = getN()/BOX_N; //=points per cluster (10000 clusters)
		if (getN() < BOX_N) {
			logWarning("WARNING Too few points, using 1 point per cluster!");
			N_C = 1;
			BOX_N = getN();
		}
		double[] data = new double[getN()*DIM*2];

		//loop over clusters
		for (int c = 0; c < BOX_N; c++) {
			double x0 = LEN * (c+0.5)/(double)BOX_N; //=0.5/1000 ||  1.5/1000  ||  ...
			double yz0 = LEN * offsYZ; //line is centered in all dimensions
			for (int p = 0; p < N_C; p++) { 
				int ii = (c*N_C+p) * DIM * 2;
				for (int d = 0; d < DIM; d++) {
					data[ii + d] = LEN * (R.nextDouble()-0.5)*BOX_LEN; //confine to small rectangle
					if (d==0) {
						data[ii+d] += x0;
					} else {
						data[ii+d] += yz0;
					}
				}
				ii += DIM;
				for (int d = 0; d < DIM; d++) {
					data[ii + d] = data[ii+d-DIM] + R.nextDouble()*MICROBOX_LEN;
				}
			}
		}
		return data;
	}
	
	private double[] generateGauss(final double domainLength) {
		double MICROBOX_LEN = GAUSS_SIGMA * 0.01;
		int N_C = getN()/GAUSS_N; //=points per cluster (10000 clusters)
		double[] data = new double[getN()*DIM*2];

		//loop over clusters
		double[] cp = new double[DIM]; //center point of cluster
		for (int c = 0; c < GAUSS_N; c++) {
			for (int d = 0; d < DIM; d++) {
				cp[d] = R.nextDouble() * domainLength;
			}
			for (int p = 0; p < N_C; p++) { 
				int ii = (c*N_C+p) * DIM * 2;
				for (int d = 0; d < DIM; d++) {
					double x = (R.nextGaussian()-0.5)*GAUSS_SIGMA; //confine to small rectangle
					x *= domainLength; //stretch if domain>1.0
					x += cp[d]; //offset of cluster
					data[ii + d] = x;
				}
				ii += DIM;
				for (int d = 0; d < DIM; d++) {
					data[ii + d] = data[ii+d-DIM] + R.nextDouble()*MICROBOX_LEN;
				}
			}
		}
		return data;
	}
	
	@Override
	public void generateQuery(double[] min, double[] max, 
			final double maxLen, final double avgQVol) {
		switch (TYPE.toType(param1)) {
		case HORIZONTAL04:
		case HORIZONTAL05: queryCuboidHorizontalNormal(min, max); break;
		case GAUSS: super.generateQuery(min, max, maxLen, avgQVol); break;
		default: throw new IllegalArgumentException("param1=" + param1);
		}
	}
	
	private void queryCuboidHorizontalNormal(double[] min, double[] max) {
		int resultsPerQuery = S.cfgWindowQuerySize;
		min[0] = R.nextDouble();
		max[0] = min[0] + (0.0001 * resultsPerQuery/1000);
		for (int i = 1; i < DIM; i++) {
			min[i] = 0;
			max[i] = 1;//R.nextDouble();  //0..1
//			xyz[i] = offsYZ - (R.nextDouble()*0.5);  
//			len[i] = Math.abs(offsYZ-xyz[i]) + R.nextDouble()*0.5;
		}
	}

	@Override
	public double maxUpdateDistance() {
		switch (TYPE.toType(param1)) {
		case HORIZONTAL04:
		case HORIZONTAL05: return BOX_LEN/100;
		case GAUSS: return super.maxUpdateDistance();//GAUSS_SIGMA/10;
		default: throw new IllegalArgumentException("param1=" + param1);
		}
	}
}
