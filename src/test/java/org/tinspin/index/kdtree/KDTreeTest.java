/*
 * Copyright 2009-2017 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of TinSpin.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinspin.index.kdtree;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.tinspin.index.PointEntryDist;
import org.tinspin.index.QueryIteratorKNN;

public class KDTreeTest {

	@Test
	public void smokeTestShort() {
		double[][] point_list = {{2,3}, {5,4}, {9,6}, {4,7}, {8,1}, {7,2}};
		smokeTest(point_list);
	}
	
	@Test
	public void smokeTestDupl() {
		double[][] point_list = {{2,3}, {2,3}, {2,3}, {2,3}, {2,3}, {2,3}};
		smokeTest(point_list);
	}
	
	@Test
	public void smokeTest2D_0() {
		double[][] point_list = new double[20][2];
		Random R = new Random(0);
		for (double[] p : point_list) {
			Arrays.setAll(p, (i) -> { return (double)R.nextInt(100);} );
		}
		smokeTest(point_list);
	}
	
	@Test
	public void smokeTest2D_1() {
		double[][] point_list = new double[20][2];
		Random R = new Random(1);
		for (double[] p : point_list) {
			Arrays.setAll(p, (i) -> { return (double)R.nextInt(100);} );
		}
		smokeTest(point_list);
	}
	
	@Test
	public void smokeTest2D_245() {
		double[][] point_list = new double[5][2];
		Random R = new Random(245);
		for (double[] p : point_list) {
			Arrays.setAll(p, (i) -> { return (double)R.nextInt(100);} );
		}
		smokeTest(point_list);
	}
	
	/**
	 * Tests handling of all points being on a line, i.e. correct handling of <=, etc.
	 */
	@Test
	public void smokeTest2D_Line() {
		double[][] point_list = new double[10000][3];
		int n = 0;
		for (double[] p : point_list) {
			p[0] = n % 3;
			p[1] = n++; 
			p[2] = n % 5;
		}
		List<double[]> list = Arrays.asList(point_list);
		Collections.shuffle(list);
		point_list = list.toArray(point_list);
		smokeTest(point_list);
	}
	
	@Test
	public void smokeTest5D() {
		double[][] point_list = new double[20][5];
		Random R = new Random(0);
		for (double[] p : point_list) {
			Arrays.setAll(p, (i) -> { return (double)R.nextInt(100);} );
		}
		smokeTest(point_list);
	}
	
	@Test
	public void smokeTest1D_Large() {
		double[][] point_list = new double[100_000][1];
		Random R = new Random(0);
		for (double[] p : point_list) {
			Arrays.setAll(p, (i) -> { return (double)R.nextInt(100);} );
		}
		smokeTest(point_list);
	}
	
	@Test
	public void smokeTest3D_Large() {
		double[][] point_list = new double[100_000][3];
		Random R = new Random(0);
		for (double[] p : point_list) {
			Arrays.setAll(p, (i) -> { return (double)R.nextInt(100);} );
		}
		smokeTest(point_list);
	}
	
	@Test
	public void smokeTest10D_Large() {
		double[][] point_list = new double[100_000][10];
		Random R = new Random(0);
		for (double[] p : point_list) {
			Arrays.setAll(p, (i) -> { return (double)R.nextInt(100);} );
		}
		smokeTest(point_list);
	}
	
	private void smokeTest(double[][] point_list) {
		int dim = point_list[0].length;
		KDTree<double[]> tree = KDTree.create(dim);
		for (double[] data : point_list) {
			tree.insert(data, data);
		}
//	    System.out.println(tree.toStringTree());
		for (double[] key : point_list) {
			if (!tree.containsExact(key)) {
				throw new IllegalStateException("" + Arrays.toString(key));
			}
		}

		for (double[] key : point_list) {
//			System.out.println("kNN query: " + Arrays.toString(key));
			QueryIteratorKNN<PointEntryDist<double[]>> iter = tree.queryKNN(key, 1);
			if (!iter.hasNext()) {
				throw new IllegalStateException("kNN() failed: " + Arrays.toString(key));
			}
			double[] answer = iter.next().point();
			if (answer != key && !Arrays.equals(answer, key)) {
				throw new IllegalStateException("Expected " + Arrays.toString(key) + " but got " + Arrays.toString(answer));
			}
		}
	    
		for (double[] key : point_list) {
//			System.out.println(tree.toStringTree());
//			System.out.println("Removing: " + Arrays.toString(key));
			if (!tree.containsExact(key)) {
				throw new IllegalStateException("containsExact() failed: " + Arrays.toString(key));
			}
			double[] answer = tree.remove(key); 
			if (answer != key && !Arrays.equals(answer, key)) {
				throw new IllegalStateException("Expected " + Arrays.toString(key) + " but got " + Arrays.toString(answer));
			}
		}
	}

	
}
