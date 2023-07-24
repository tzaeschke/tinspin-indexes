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
package org.tinspin.index.phtree;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

import ch.ethz.globis.tinspin.TestStats;
import org.junit.Test;
import org.tinspin.index.BoxMap;
import org.tinspin.index.array.RectArray;
import org.tinspin.index.test.util.JmxTools;
import org.tinspin.index.test.util.TestBox;
import org.tinspin.index.test.util.TestBoxCube;
import org.tinspin.index.test.util.TestInstances.TST;

import static org.tinspin.index.Index.*;

public class PhTreeTest {

	private static final int N = 10000;
	private static final int DIM = 3;
	private static final double param1 = 0.01;
	private static final RectComp COMP = new RectComp();

	@Test
	public void testR() {
		Random R = new Random(0);
		TestStats ts = new TestStats(TST.CUBE_R, null, N, DIM, param1);
		TestBox test = new TestBoxCube(R, ts);
		double[] data = test.generate();

		RectArray<Integer> tree1 = new RectArray<Integer>(DIM, N);
		PHTreeR<Integer> tree2 = PHTreeR.createPHTree(DIM);
		load(tree1, data);
		load(tree2, data);
	
		for (int i = 0; i < 100; i++) {
			repeatQuery(test, tree1, tree2, i);
		}
	}
	
	
	private void load(BoxMap<Integer> tree, double[] data) {
		int pos = 0;
		for (int n = 0; n < N; n++) {
			double[] lo = new double[DIM];
			double[] hi = new double[DIM];
			System.arraycopy(data, pos, lo, 0, DIM);
			pos += DIM;
			System.arraycopy(data, pos, hi, 0, DIM);
			pos += DIM;
			tree.insert(lo, hi, n);
		}
	}
	
	private void repeatQuery(TestBox test, BoxMap<Integer> tree1, BoxMap<Integer> tree2, int repeat) {
		int dims = DIM;
		//log("N=" + N);
		log("querying index ... repeat = " + repeat);
		double[][] lower = new double[repeat][dims]; 
		double[][] upper = new double[repeat][dims];
		test.generateWindowQueries(lower, upper);
		JmxTools.reset();
		long t1 = System.currentTimeMillis();
		int n = 0;
		n = repeatQueries(tree1, tree2, lower, upper);
		long t2 = System.currentTimeMillis();
		log("Query time: " + (t2-t1) + " ms -> " + (t2-t1)/(double)repeat + " ms/q -> " +
				(t2-t1)*1000*1000/(double)n + " ns/q/r  (n=" + n + ")");
	}
	
	private int repeatQueries(BoxMap<Integer> tree1, BoxMap<Integer> tree2, double[][] lower, double[][] upper) {
		int n=0;
		for (int i = 0; i < lower.length; i++) {
			int n1 = 0;
			ArrayList<BoxEntry<Integer>> set1 = new ArrayList<>();
			QueryIterator<BoxEntry<Integer>> it1 = tree1.queryIntersect(lower[i], upper[i]);
			while (it1.hasNext()) {
				set1.add(it1.next());
				n1++;
			}
			int n2 = 0;
			ArrayList<BoxEntry<Integer>> set2 = new ArrayList<>();
			QueryIterator<BoxEntry<Integer>> it2 = tree2.queryIntersect(lower[i], upper[i]);
			while (it2.hasNext()) {
				set2.add(it2.next());
				n2++;
			}
			if (n1 != n2) {
				log("n1/n2=" + n1 + "/" + n2);
				log("q=" + Arrays.toString(lower[i]) + "/" + Arrays.toString(upper[i]));
				set1.sort(COMP);
				set2.sort(COMP);
				for (int j = 0; j < set1.size(); j++) {
					BoxEntry<Integer> e1 = set1.get(j);
					BoxEntry<Integer> e2 = set2.get(j);
					if (!Arrays.equals(e1.min(), e2.min()) ||
							!Arrays.equals(e1.max(), e2.max())) {
						log("j=" + j + " mismatch: " + e1 + " -/- " + e2);
					}
				}
			}
			assertEquals(n1, n2);
			n += n1;
			if (i%10 == 0) System.out.print('.');
		}
		System.out.println();
		//log("n=" + n/(double)lower.length);
		return n;
	}
	
	private static final class RectComp implements Comparator<BoxEntry<?>> {

		@Override
		public int compare(BoxEntry<?> o1, BoxEntry<?> o2) {
			for (int d = 0; d < DIM; d++) {
				double diff = o1.min()[d] - o2.min()[d];
				if (diff != 0) {
					return diff < 0 ? -1 : 1;
				}
				diff = o1.max()[d] - o2.max()[d];
				if (diff != 0) {
					return diff < 0 ? -1 : 1;
				}
			}
			return 0;
		}
	}
	
	private static void log(String string) {
		System.out.println(string);
	}

}
