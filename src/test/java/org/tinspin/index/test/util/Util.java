/*
 * Copyright 2016-2023 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import org.tinspin.index.critbit.BitTools;

import java.util.Random;

public class Util {
    /**
     * Float to long.
     * @param f float
     * @return long.
     */
    public static long f2l(double f) {
        return BitTools.toSortableLong(f);
    }

    public static void f2l(double[] f, long[] l) {
        BitTools.toSortableLong(f, l);
    }

    /**
     * Long to float.
     * @param l long
     * @return long.
     */
    public static double l2f(long l) {
        return BitTools.toDouble(l);
    }

    public static void l2f(long[] l, double[] f) {
        BitTools.toDouble(l, f);
    }

    public static double dist(double[] a, double[] b) {
        double dist = 0;
        for (int i = 0; i < a.length; i++) {
            double d =  a[i]-b[i];
            dist += d*d;
        }
        return Math.sqrt(dist);
    }

    public static double distRCenter(double[] center, double[] rLower, double[] rUpper) {
        double dist = 0;
        for (int i = 0; i < center.length; i++) {
            double d = center[i]-(rUpper[i]+rLower[i])/2;
            dist += d*d;
        }
        return Math.sqrt(dist);
    }

    public static double distREdge(double[] center, double[] rLower, double[] rUpper) {
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

    public static void run(Candidate x, int N, int DIM) {
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

    public static void runSmokeTest(Candidate x, int dims) {
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

}
