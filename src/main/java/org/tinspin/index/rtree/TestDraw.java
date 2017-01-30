/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.rtree;

import java.awt.Graphics;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class TestDraw extends JPanel {

	public static enum MODE {
		/** Draw each point. */
		POINTS,
		/** Draw lines for each pair of points. P1-P2, P3-P4, P5-P6 ... */
		LINES,
		/** Draw rectangles for each pair of points. P1-P2, P3-P4, P5-P6 ... */
		RECTANGLES;
	}
	
	/** svUID */
	private static final long serialVersionUID = 1L;
	private static final int LEN_X = 1000;
	private static final int LEN_Y = 1000;
	private static final int OFS_X = 50;
	private static final int OFS_Y = 50;
	
	//public class BasicJPanel extends JPanel{

	private int[] data;
	private final MODE mode;
	
	private TestDraw(MODE mode) {
		super();
		this.mode = mode;
	}
	
	/**
	 * 
	 * @param data
	 * @param DIM
	 * @param dx dimension to use as X [0..DIM-1]
	 * @param dy dimension to use as Y [0..DIM-1]
	 */
	private void setData(double[] data, int DIM, int dx, int dy) {
		//get min/max 
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (int i = 0; i < data.length; i+=DIM) {
			double x = data[i+dx];
			double y = data[i+dy];
			minX = min(x, minX);
			minY = min(y, minY);
			maxX = max(x, maxX);
			maxY = max(y, maxY);
		}
		System.out.println("TestDraw min/max: " + minX + "/" + maxX + "    " + minY + "/" + maxY);
		
		minY = minX = min(minX, minY);
		maxY = maxX = max(maxX, maxY);
//		minX = 0.0;
//		maxX = 0.002;
//		minY = 0.499;
//		maxY = 0.501;
		
		//normalise data
		this.data = new int[data.length/DIM*2];
		int i2 = 0;
		double xNorm = LEN_X/(maxX-minX);
		double yNorm = LEN_Y/(maxY-minY);
		for (int i = 0; i < data.length; i+=DIM) {
			double x = data[i+dx];
			double y = data[i+dy];
			int x2 = (int) ((x-minX)*xNorm); 
			int y2 = (int) ((y-minY)*yNorm);
			this.data[i2++] = x2;
			this.data[i2++] = y2;
//			System.out.println("x /y =" + x + "/" + y);
//			System.out.println("x2/y2=" + x2 + "/" + y2);
		}		
	}

	private void setData(long[] data, int DIM, int dx, int dy) {
		//get min/max 
		long minX = Long.MAX_VALUE;
		long minY = Long.MAX_VALUE;
		long maxX = -Long.MAX_VALUE;
		long maxY = -Long.MAX_VALUE;
		int n = 0;
		for (int i = 0; i < data.length; i+=DIM) {
			n++;
			long x = data[i+dx];
			long y = data[i+dy];
			minX = min(x, minX);
			minY = min(y, minY);
			maxX = max(x, maxX);
			maxY = max(y, maxY);
		}
		System.out.println("TestDraw min/max: " + minX + "/" + maxX + "    " + minY + "/" + maxY);
		
		minY = minX = min(minX, minY);
		maxY = maxX = max(maxX, maxY);
//		minX = 0.0;
//		maxX = 0.002;
//		minY = 0.499;
//		maxY = 0.501;
		
		//normalise data
		this.data = new int[2*n];
		int i2 = 0;
		for (int i = 0; i < data.length; i+=DIM) {
			long x = data[i+dx];
			long y = data[i+dy];
			int x2 = (int) ((x-minX)*LEN_X/(maxX-minX)); 
			int y2 = (int) ((y-minY)*LEN_Y/(maxY-minY));
			this.data[i2++] = x2;
			this.data[i2++] = y2;
//			System.out.println("x /y =" + x + "/" + y);
//			System.out.println("x2/y2=" + x2 + "/" + y2);
		}		
	}

	private long min(long x, long minX) {
		return x < minX ? x : minX;
	}
	
	private long max(long x, long maxX) {
		return x > maxX ? x : maxX;
	}
	
	private double min(double x, double minX) {
		return x < minX ? x : minX;
	}
	
	private double max(double x, double maxX) {
		return x > maxX ? x : maxX;
	}
	
	/** 
	 * This gets called automatically whenever the panel needs to be redrawn.
	 */
	public void paintComponent(Graphics g) {
		int MAX = (int) min(10*1000*1000, data.length);
		int n = 0;
		if (mode == MODE.POINTS) {
			for (int i = 0; i < MAX; ) {
				int x = data[i++];
				int y = data[i++];
				g.drawOval(x, LEN_Y-y, 0, 0);
				n++;
			}
			System.out.println("Points drawn: " + n);
		} else if (mode == MODE.LINES) {
			for (int i = 0; i < MAX; ) {
				int x1 = data[i++];
				int y1 = data[i++];
				int x2 = data[i++];
				int y2 = data[i++];
				g.drawLine(x1, LEN_Y-y1, x2, LEN_Y-y2);
				n++;
			}
			System.out.println("Lines drawn: " + n);
		} else {
			for (int i = 0; i < MAX; ) {
				int x1 = data[i++];
				int y1 = data[i++];
				int x2 = data[i++];
				int y2 = data[i++];
				g.drawRect(OFS_X+x1, OFS_Y+y1, x2-x1, y2-y1);
				n++;
			}
			System.out.println("Rectangles drawn: " + n);
		}
	}

	public static void draw(double[][] data) {
		int dim = data[0].length;
		double[] data2 = new double[data.length*dim];
		int i = 0;
		for (double[] d: data) {
			System.arraycopy(d, 0, data2, i, d.length);
			i+=dim;
		}
		draw(data2, dim);
	}

	public static void draw(double[] data, int dim) {
		draw(data, dim, MODE.POINTS);
	}

	public static void draw(double[] data, int dim, MODE mode) {
		JFrame frame = new JFrame("MyPanel");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		//frame.setSize(LEN_X+20,LEN_Y+40);
		frame.setBounds(30,  30, LEN_X+2*OFS_X, LEN_Y+2*OFS_Y);

		TestDraw panel = new TestDraw(mode);
		panel.setData(data, dim, 0, 1);
		frame.setContentPane(panel);
		frame.setVisible(true);
	}

	public static void draw(double[][] data, int dim, MODE mode) {
		double[] data2 = new double[data.length*data[0].length];
		int i = 0;
		for (double[] da: data) {
			for (double d: da) {
				data2[i++] = d;
			}
		}
		draw(data2, dim, mode);
	}

	/**
	 * 
	 * @param data
	 * @param dim Total number of dimension in original data
	 * @param dx Id of dimension that should be drawn as 'x'
	 * @param dy Id of dimension that should be drawn as 'y'
	 */
	public static void draw(long[] data, int dim, int dx, int dy) {
		JFrame frame = new JFrame("MyPanel");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		//frame.setSize(LEN_X+20,LEN_Y+40);
		frame.setBounds(30,  30, LEN_X+2*OFS_X, LEN_Y+2*OFS_Y);

		TestDraw panel = new TestDraw(MODE.POINTS);
		panel.setData(data, dim, dx, dy);
		frame.setContentPane(panel);
		frame.setVisible(true);
	}

	/**
	 * 
	 * @param data
	 * @param dim Total number of dimension in original data
	 * @param dx Id of dimension that should be drawn as 'x'
	 * @param dy Id of dimension that should be drawn as 'y'
	 */
	public static void draw(long[][] data, int dim, int dx, int dy) {
		long[] d = new long[2*data.length];
		int i = 0;
		for (long[] l: data) {
			d[i++] = l[dx];
			d[i++] = l[dy];
		}
		draw(d, 2, 0, 1);
	}
}