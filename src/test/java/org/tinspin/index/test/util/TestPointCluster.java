/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import java.util.Random;

/**
 * CLUSTER 3.4/3.5 are introduced by:
 * [1] L. Arge, M. de Berg, H. J. Haverkort and K. Yi: 
 * "The Priority R-Tree: A Practically Efficient and Worst-Case Optimal R-Tree"
 * 
 * @author Tilmann Zaeschke
 */
public class TestPointCluster extends TestPoint {

	public TestPointCluster(Random R, TestStats S) {
		super(R, S);
	}

	private static double BOX_YZ_OFFS = 0.01; //0.5;
	private static int BOX_N = 10*1000;
	private static final double BOX_LEN = 0.00001;
	private static int GAUSS_N = 1000;
	private static final double GAUSS_SIGMA = 0.001;
	
	private static enum TYPE {
		ORIGINAL(2),
		HORIZONTAL05(3.5),
		HORIZONTAL04(3.4),
		HORIZONTAL001(3.01),
		DIAGONAL(4),
		GAUSS(5);
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
		case ORIGINAL: return generateOriginal(len);
		case HORIZONTAL05: return generateHorizontal(len, 0.5); 
		case HORIZONTAL04: return generateHorizontal(len, 0.4); 
		case HORIZONTAL001: return generateHorizontal(len, 0.01);
		case DIAGONAL: return generateDiagonal(len);
		case GAUSS: return generateGauss(len);
		}
		throw new IllegalArgumentException("param1=" + param1);
	}
	
	private double[] generateDiagonal(final double LEN) {
		int N_C = getN()/BOX_N; //=points per cluster (10000 clusters)
		double[] data = new double[getN()*DIM];

		//loop over clusters
		for (int c = 0; c < BOX_N; c++) {
			double x0 = LEN * (c+0.5)/(double)BOX_N; //=0.5/1000 ||  1.5/1000  ||  ...
			for (int p = 0; p < N_C; p++) { 
				int ii = (c*N_C+p) * DIM;
				for (int d = 0; d < DIM; d++) {
					data[ii + d] = x0 + LEN * (R.nextDouble()-0.5)*BOX_LEN; //confine to small rectangle
				}
			}
		}
		return data;
	}
	
	//Proper version
	private double[] generateHorizontal(final double LEN, double offsYZ) {
		int N_C = getN()/BOX_N; //=points per cluster (10000 clusters)
		double[] data = new double[getN()*DIM];

		//loop over clusters
		for (int c = 0; c < BOX_N; c++) {
			double x0 = LEN * (c+0.5)/(double)BOX_N; //=0.5/1000 ||  1.5/1000  ||  ...
			//TODO using 0.5 i.o. 0.1 raises nodeCount from 0.7 to 1.5 million per 10 million
			//TODO 0.9 requires only 0.45 million nodes...
			//TODO 1.0 : 9.8 million
			//TODO 0.51 : 0.799 million
			//TODO 0.0: 0.5 million
			double yz0 = LEN * offsYZ; //line is centered in all dimensions
			for (int p = 0; p < N_C; p++) { 
				int ii = (c*N_C+p) * DIM;
				for (int d = 0; d < DIM; d++) {
					data[ii + d] = LEN * (R.nextDouble()-0.5)*BOX_LEN; //confine to small rectangle
					//data[ii + d] += 0.00005;  TODO difference between 1.5 and 9.9 million nodes per 10 million entries (for offs=0.5).
					if (d==0) {
						data[ii+d] += x0;
					} else {
						data[ii+d] += yz0;
					}
				}
			}
		}
		return data;
	}
	
	//old version
	private double[] generateOriginal(final double LEN) {
		int N_C = getN()/1000; //=nClusters: 1000 point per cluster
		
		double[] data = new double[getN()*DIM];

		//loop over clusters
		for (int c = 0; c < N_C; c++) {
			double x0 = LEN * (c+0.5)/(double)N_C;
			double yz0 = LEN * 0.5f; //line is centered in all dimensions
			for (int p = 0; p < 1000; p++) { 
				int ii = (c*1000+p) * DIM;
				for (int d = 0; d < DIM; d++) {
					data[ii+ d] = LEN * R.nextDouble()*BOX_LEN; //confine to small rectangle
					if (d==0) {
						data[ii+d] += x0;
					} else {
						data[ii+d] += yz0;
					}
				}
			}
		}
		return data;
	}
	
	
	private double[] generateGauss(final double domainLength) {
		int N_C = getN()/GAUSS_N; //=points per cluster (10000 clusters)
		double[] data = new double[getN()*DIM];

		//loop over clusters
		double[] cp = new double[DIM]; //center point of cluster
		for (int c = 0; c < GAUSS_N; c++) {
			for (int d = 0; d < DIM; d++) {
				cp[d] = R.nextDouble() * domainLength;
			}
			for (int p = 0; p < N_C; p++) { 
				int ii = (c*N_C+p) * DIM;
				for (int d = 0; d < DIM; d++) {
					double x = (R.nextGaussian()-0.5)*GAUSS_SIGMA; //confine to small rectangle
					x *= domainLength; //stretch if domain>1.0
					x += cp[d]; //offset of cluster
					data[ii + d] = x;
				}
			}
		}
		return data;
	}
	
	@Override
	public void generateQuery(double[] min, double[] max, 
			final double maxLen, final double avgQVol) {
		switch (TYPE.toType(param1)) {
		case ORIGINAL: queryCuboidOld(min, max); break;
		case HORIZONTAL04:
		case HORIZONTAL05: queryCuboidHorizontalNormal(min, max); break;
		case HORIZONTAL001: queryCuboidHorizontal001(min, max); break;
		case DIAGONAL: queryCuboidDiag(min, max); break;
		case GAUSS: super.generateQuery(min, max, maxLen, avgQVol); break;
		default: throw new IllegalArgumentException("param1=" + param1);
		}
	}
	
	//diagonal
	private void queryCuboidDiag(double[] min, double[] max) {
		min[0] = R.nextDouble();
		max[0] = min[0] + 0.001;
		for (int i = 1; i < DIM; i++) {
			min[i] = R.nextDouble()*min[0];  //TODO this can lie outside...
			max[i] = min[0] + (1-min[0])* R.nextDouble();
		}
	}
	
	//normal
	private void queryCuboidOld(double[] min, double[] max) {
		double cLen = TestPointCluster.BOX_LEN;
		double cOffs = TestPointCluster.BOX_YZ_OFFS;
		for (int i = 0; i < DIM; i++) {
			min[i] = cOffs-cLen*0.5 + R.nextDouble()*cLen;  //TODO this can lie outside...
			max[i] = min[i] + 0.01*cLen;
		}
		max[1] = 1.0; //len[1] = 1.0; !!!!  
		max[0] = min[0]+ 0.001;//len[0] = 0.001;
		min[1] = 0;
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

	private void queryCuboidHorizontal001(double[] min, double[] max) {
		min[0] = R.nextDouble();
		max[0] = min[0] + 0.0001;
		for (int i = 1; i < DIM; i++) {
			min[i] = 0;
			max[i] = 1;//R.nextDouble();  //0..1
//			xyz[i] = cOffs - (R.nextDouble()*0.5);  
//			len[i] = Math.abs(cOffs-xyz[i]) + R.nextDouble()*0.5;
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
