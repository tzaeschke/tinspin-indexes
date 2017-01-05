/*
 * Copyright 2009-2014 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of ZooDB.
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
package org.zoodb.index.critbit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;
import org.zoodb.index.critbit.CritBit.Entry;
import org.zoodb.index.critbit.CritBit.QueryIteratorKD;

/**
 * 
 * @author Tilmann Zaeschke
 */
public class TestCritBitKD {

	private CritBitKD<Integer> newCritBit(int depth, int K) {
		return CritBit.createKD(depth, K);
	}
	
	@Test
	public void test64_2() {
		final int K = 2;
		for (int r = 0; r < 1000; r++) {
			Random R = new Random(r);
			int N = 1000;
			long[] aa = new long[2*N];
			CritBitKD<Integer> cb = newCritBit(64, 2); 
			int n = 0;
			for (int i = 0; i < N; i+=K, n++) {
				aa[i] = R.nextLong();
				aa[i+1] = R.nextLong();
				//System.out.println(a[i]>>>32 + ",");
				//System.out.println("Inserting: " + a[i] + " / " + BitsInt.toBinary(a[i]));
				long[] a = new long[]{aa[i], aa[i+1]};
				if (cb.containsKD(a)) {
					for (int j = 0; j < i; j++) {
						if (a[j] == a[i] && a[j+1] == a[i+1]) {
							//System.out.println("Duplicate: " + a[i]);
							i-=K;
							continue;
						}
					}
					fail("r=" + r + "  i= " + i);
				}
				assertNull(cb.putKD(a, n+12345));
				//cb.printTree();
				assertEquals(n+12345, (int)cb.putKD(a, n));
				//cb.printTree();
				assertEquals(i+K, cb.size()*K);
				assertTrue(cb.containsKD(a));
			}
			
			assertEquals(N, cb.size()*K);
	
			for (int i = 0; i < N; i+=K) {
				//System.out.println("Checking: " + i + "   " + BitsInt.toBinary(a[i] >>> 32));
				long[] a = new long[]{aa[i], aa[i+1]};
				assertTrue(cb.containsKD(a));
			}
			
			((CritBit<?>)cb).checkTree();
			
			n = 0;
			for (int i = 0; i < N; i+=K, n++) {
				long[] a = new long[]{aa[i], aa[i+1]};
				assertTrue(cb.containsKD(a));
				assertEquals("n="+ n, n, (int)cb.removeKD(a));
				assertNull(cb.removeKD(a));
				assertFalse(cb.containsKD(a));
				assertEquals(N-i-K, cb.size()*K);
			}
			
			assertEquals(0, cb.size());
		}
	}

	@Test
	public void test64_K() {
		final int K = 5;
		for (int r = 0; r < 100; r++) {
			Random R = new Random(r);
			int N = 1000;
			long[][] aa = new long[N][];
			CritBitKD<Integer> cb = newCritBit(64, K); 
			for (int i = 0; i < N; i++) {
				long[] a = new long[K];
				for (int k = 0; k < K; k++) {
					a[k] = R.nextLong();
				}
				aa[i] = a;
				//System.out.println(a[i]>>>32 + ",");
				//System.out.println("Inserting: " + a[i] + " / " + BitsInt.toBinary(a[i]));
				if (cb.containsKD(a)) {
					for (int j = 0; j < i; j++) {
						boolean match = true;
						for (int k = 0; k < K; k++) {
							if (aa[j*K+k] != aa[i*K+k]) {
								match = false;
								break;
							}
						}
						if (match) {
							//System.out.println("Duplicate: " + a[i]);
							i--;
							continue;
						}
					}
					fail("r=" + r + "  i= " + i);
				}
				assertNull(cb.putKD(a, 12345+i));
				//cb.printTree();
				assertEquals(12345+i, (int)cb.putKD(a, i));
				//cb.printTree();
				assertEquals(i+1, cb.size());
				assertTrue(cb.containsKD(a));
			}
			
			assertEquals(N, cb.size());
	
			for (int i = 0; i < N; i++) {
				//System.out.println("Checking: " + i + "   " + BitsInt.toBinary(a[i] >>> 32));
				assertTrue(cb.containsKD(aa[i]));
			}
			
			//test iterators
			long[] min = new long[K];
			long[] max = new long[K];
			Arrays.fill(min, Long.MIN_VALUE);
			Arrays.fill(max, Long.MAX_VALUE);
			//value iteration
			QueryIteratorKD<Integer> it = cb.queryKD(min, max);
			int n = 0;
			ArrayList<Integer> sortedResults = new ArrayList<>();
			while (it.hasNext()) {
				Integer val = it.next();
				assertNotNull(val);
				sortedResults.add(val);
				n++;
			}
			assertEquals(N, n);
			//key iteration
			it = cb.queryKD(min, max);
			n = 0;
			while (it.hasNext()) {
				long[] key = it.nextKey();
				//assure same order
				assertEquals(sortedResults.get(n), cb.getKD(key));
				n++;
			}
			assertEquals(N, n);
			//entry iteration
			it = cb.queryKD(min, max);
			n = 0;
			while (it.hasNext()) {
				Entry<Integer> e = it.nextEntry();
				long[] key = e.key();
				assertEquals(sortedResults.get(n), cb.getKD(key));
				assertEquals(sortedResults.get(n), e.value());
				n++;
			}
			assertEquals(N, n);
			
			((CritBit<?>)cb).checkTree();
			
			for (int i = 0; i < N; i++) {
				long[] a = aa[i];
				assertTrue(cb.containsKD(a));
				assertEquals(i, (int)cb.removeKD(a));
				assertNull(cb.removeKD(a));
				assertFalse(cb.containsKD(a));
				assertEquals(N-i-1, cb.size());
			}
			
			assertEquals(0, cb.size());
		}
	}

	@Test
	public void test64_1D_queries_PositiveNumbers() {
		final int K = 1;
		for (int r = 0; r < 100; r++) {
			Random R = new Random(r);
			int N = 1000;
			long[][] aa = new long[N][];
			CritBitKD<Integer> cb = newCritBit(64, K); 
			for (int i = 0; i < N; i++) {
				long[] a = new long[K];
				for (int k = 0; k < K; k++) {
					a[k] = Math.abs(R.nextLong());
				}
				
				if (cb.containsKD(a)) {
					//System.out.println("Duplicate: " + a[i]);
					i--;
					continue;
				}
				aa[i] = a;
				assertNull(cb.putKD(a, i));
				assertTrue(cb.containsKD(a));
			}
			
			assertEquals(N, cb.size());
	
			long[] qMin = new long[K];
			long[] qMax = new long[K];
			QueryIteratorKD<Integer> it = null;
			
			//test normal queries
			for (int i = 0; i < 10; i++) {
				createQuery(R, qMin, qMax);
			
				ArrayList<long[]> result = executeQuery(aa, qMin, qMax);
				it = cb.queryKD(qMin, qMax);
				
				int nResult = 0;
				while (it.hasNext()) {
					long[] ra = it.nextKey();
					nResult++;
					assertContains(aa, ra);
					//System.out.println("ra0=" + ra[0]);
				}
					
				assertEquals("r=" + r + " i=" + i, result.size(), nResult);
			}			

			//assert all
			int n = 0;
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.queryKD(qMin, qMax);
			while (it.hasNext()) {
				it.next();
				//System.out.println("r1:" + it.next()[0]);
				n++;
			}
			assertEquals(N, n);

			//assert point search
			for (long[] a: aa) {
				it = cb.queryKD(a, a);
				assertTrue(it.hasNext());
				long[] r2 = it.nextKey();
				assertFalse(it.hasNext());
				assertTrue(isEqual(a, r2));
				//System.out.println("r2:" + r2[0]);
			}
			
			
			//assert none on invert
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.queryKD(qMax, qMin);
			assertFalse(it.hasNext());
			
			((CritBit<?>)cb).checkTree();
			
			// delete stuff
			for (int i = 0; i < N; i++) {
				assertEquals(i, (int)cb.removeKD(aa[i]));
			}
			
			// assert none on empty tree
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.queryKD(qMin, qMax);
			assertFalse(it.hasNext());
		}
	}

	@Test
	public void test64_True1D_256bit_1() {
		final int K = 4;
		final int W = 64;
		long[][] aa = new long[][]{{
			0x5555555555555555L,0x5555555555555555L,
			0x5555555555555555L,0x5555555555555555L}};
		int N = aa.length;
		CritBitKD<Integer> cb = newCritBit(W, K); 
		assertNull(cb.putKD(aa[0], 5555));
		assertTrue(cb.containsKD(aa[0]));

		assertEquals(N, cb.size());

		long[] qMin = new long[K];
		long[] qMax = new long[K];
		QueryIteratorKD<Integer> it = null;

		//test special
		qMin = new long[]{
				0x5555555500000000L,0x5555555500000000L,
				0x5555555500000000L,0x5555555500000000L};
		qMax = new long[]{
				0x5555555588888888L,0x5555555588888888L,
				0x5555555588888888L,0x5555555588888888L};
		int n = 0;
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(N, n);

		qMin = new long[]{//5,4,16,0};
				0x55554444FFFF0000L,0x55554444FFFF0000L,
				0x55554444FFFF0000L,0x55554444FFFF0000L};
		qMax = new long[]{//5,8,16,0};
				0x55558888FFFF0000L,0x55558888FFFF0000L,
				0x55558888FFFF0000L,0x55558888FFFF0000L};
		n = 0;
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(N, n);

		qMin = new long[]{//5,8,0,0};
				0x5555888800000000L,0x5555888800000000L,
				0x5555888800000000L,0x5555888800000000L};
		qMax = new long[]{//5,9,0,0};
				0x5555999900000000L,0x5555999900000000L,
				0x5555999900000000L,0x5555999900000000L};
		n = 0;
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(0, n);

		//assert all
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMin, qMax);
		n = 0;
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(1, n);

		//assert point search
		for (long[] a: aa) {
			it = cb.queryKD(a, a);
			assertTrue(it.hasNext());
			long[] r2 = it.nextKey();
			assertFalse(it.hasNext());
			assertTrue(isEqual(a, r2));
		}


		//assert none on invert
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMax, qMin);
		assertFalse(it.hasNext());
		
		((CritBit<?>)cb).checkTree();

		// delete stuff
		for (int i = 0; i < N; i++) {
			assertEquals(5555, (int)cb.removeKD(aa[i]));
		}

		// assert none on empty tree
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMin, qMax);
		assertFalse(it.hasNext());
	}

	@Test
	public void test64_True1D_256bit_2() {
		final int K = 4;
		final int W = 64;
		long[][] aa = new long[][]{
				{0x5555555555555555L,0x5555555555555555L,
					0x5555555555555555L,0x5555555555555555L},
					{0x5555555588888888L,0x5555555588888888L,
						0x5555555588888888L,0x5555555588888888L}};
		final int N = aa.length;
		CritBitKD<Integer> cb = newCritBit(W, K); 
		assertNull(cb.putKD(aa[0], 5555));
		assertTrue(cb.containsKD(aa[0]));
		assertNull(cb.putKD(aa[1], 5588));
		assertTrue(cb.containsKD(aa[1]));

		assertEquals(2, cb.size());

		long[] qMin = new long[K];
		long[] qMax = new long[K];
		QueryIteratorKD<Integer> it = null;

		//test special
		qMin = new long[]{
				0x5555555500000000L,0x5555555500000000L,
				0x5555555500000000L,0x5555555500000000L};
		qMax = new long[]{
				0x5555555588888888L,0x5555555588888888L,
				0x5555555588888888L,0x5555555588888888L};
		int n = 0;
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(N, n);

		qMin = new long[]{//5,4,16,0};
				0x55554444FFFF0000L,0x55554444FFFF0000L,
				0x55554444FFFF0000L,0x55554444FFFF0000L};
		qMax = new long[]{//5,8,16,0};
				0x55558888FFFF0000L,0x55558888FFFF0000L,
				0x55558888FFFF0000L,0x55558888FFFF0000L};
		n = 0;
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(N, n);

		qMin = new long[]{//5,8,0,0};
				0x5555888800000000L,0x5555888800000000L,
				0x5555888800000000L,0x5555888800000000L};
		qMax = new long[]{//5,9,0,0};
				0x5555999900000000L,0x5555999900000000L,
				0x5555999900000000L,0x5555999900000000L};
		n = 0;
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(0, n);

		//assert all
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(2, n);

		//assert point search
		for (long[] a: aa) {
			it = cb.queryKD(a, a);
			assertTrue(it.hasNext());
			long[] r2 = it.nextKey();
			assertFalse(it.hasNext());
			assertTrue(isEqual(a, r2));
		}


		//assert none on invert
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMax, qMin);
		assertFalse(it.hasNext());

		// delete stuff
		//for (int i = 0; i < 2; i++) {
		assertEquals(5555, (int)cb.removeKD(aa[0]));
		assertEquals(5588, (int)cb.removeKD(aa[1]));
		//}
		
		((CritBit<?>)cb).checkTree();

		// assert none on empty tree
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMin, qMax);
		assertFalse(it.hasNext());
	}

	@Test
	public void test64_True1D_256bit_2b() {
		final int K = 4;
		final int W = 64;
		long[][] aa = new long[][]{//{5,8,5,5}, {5,8,8,8}};
				{0x5555888855555555L, 0x5555888855555555L,
					0x5555888855555555L, 0x5555888855555555L},
					{0x5555888888888888L, 0x5555888888888888L,
						0x5555888888888888L, 0x5555888888888888L}
		};
		final int N = aa.length;
		CritBitKD<Integer> cb = newCritBit(W, K); 
		assertNull(cb.putKD(aa[0], 5555));
		assertTrue(cb.containsKD(aa[0]));
		assertNull(cb.putKD(aa[1], 5588));
		assertTrue(cb.containsKD(aa[1]));

		assertEquals(2, cb.size());

		long[] qMin = new long[K];
		long[] qMax = new long[K];
		QueryIteratorKD<Integer> it = null;

		//test special
		qMin = new long[]{//5,5,0,0};
				0x5555555500000000L, 0x5555555500000000L,
				0x5555555500000000L, 0x5555555500000000L};
		qMax = new long[]{//5,8,8,8};
				0x5555888888888888L, 0x5555888888888888L,
				0x5555888888888888L, 0x5555888888888888L};
		int n = 0;
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(N, n);

		qMin = new long[]{//5,4,16,0};
				0x55554444FFFF0000L, 0x55554444FFFF0000L,
				0x55554444FFFF0000L, 0x55554444FFFF0000L};
		qMax = new long[]{//5,8,16,0};
				0x55558888FFFF0000L, 0x55558888FFFF0000L,
				0x55558888FFFF0000L, 0x55558888FFFF0000L};
		n = 0;
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(N, n);

		qMin = new long[]{//5,18,0,0};
				0x5555EEEE00000000L, 0x5555EEEE00000000L,
				0x5555EEEE00000000L, 0x5555EEEE00000000L};
		qMax = new long[]{//5,19,0,0};
				0x5555FFFF00000000L, 0x5555FFFF00000000L,
				0x5555FFFF00000000L, 0x5555FFFF00000000L};
		n = 0;
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(0, n);

		//assert all
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(2, n);

		//assert point search
		for (long[] a: aa) {
			it = cb.queryKD(a, a);
			assertTrue(it.hasNext());
			long[] r2 = it.nextKey();
			assertFalse(it.hasNext());
			assertTrue(isEqual(a, r2));
		}


		//assert none on invert
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMax, qMin);
		assertFalse(it.hasNext());
		
		((CritBit<?>)cb).checkTree();

		// delete stuff
		//for (int i = 0; i < 2; i++) {
		assertEquals(5555, (int)cb.removeKD(aa[0]));
		assertEquals(5588, (int)cb.removeKD(aa[1]));
		//}

		// assert none on empty tree
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMin, qMax);
		assertFalse(it.hasNext());
	}

	@Test
	public void test64_1D_queries_1() {
		final int K = 1;
		long[][] aa = new long[][]{{1},{2},{3},{4},{5},{6}};
		CritBitKD<Integer> cb = newCritBit(64, K); 
		for (int i = 0; i < aa.length; i++) {
			long[] a = aa[i];
			if (cb.containsKD(a)) {
				//System.out.println("Duplicate: " + a[i]);
				i--;
				continue;
			}
			aa[i] = a;
			assertNull(cb.putKD(a, i));
			//cb.printTree();
			assertTrue(cb.containsKD(a));
			//cb.printTree();
		}

		assertEquals(aa.length, cb.size());
		
		long[] qMin = new long[]{0};
		long[] qMax = new long[]{2};
		QueryIteratorKD<Integer> it = null;

		//test normal queries
		for (int i = 0; i < 10; i++) {
			ArrayList<long[]> result = executeQuery(aa, qMin, qMax);
			it = cb.queryKD(qMin, qMax);

			int nResult = 0;
			while (it.hasNext()) {
				long[] ra = it.nextKey();
				nResult++;
				assertContains(aa, ra);
			}
			qMin[0]++;
			qMax[0]++;
			
			assertEquals("i=" + i, result.size(), nResult);
		}			

		//assert all
		int n = 0;
		Arrays.fill(qMin, Long.MIN_VALUE); 
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			n++;
		}
		assertEquals(aa.length, n);

		//assert point search
		for (long[] a: aa) {
			it = cb.queryKD(a, a);
			assertTrue(it.hasNext());
			long[] r2 = it.nextKey();
			assertFalse(it.hasNext());
			assertTrue(isEqual(a, r2));
		}


		//assert none on invert
		Arrays.fill(qMin, Long.MIN_VALUE); 
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMax, qMin);
		assertFalse(it.hasNext());
		
		((CritBit<?>)cb).checkTree();

		// delete stuff
		for (long[] a: aa) {
			assertNotNull(cb.removeKD(a));
		}

		// assert none on empty tree
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMin, qMax);
		assertFalse(it.hasNext());
	}


	@Test
	public void test64_1D_queries_Single() {
		final int K = 1;
		long[] a = new long[]{3};
		CritBitKD<Integer> cb = newCritBit(64, K);
		assertNull(cb.putKD(a, 123));
		assertTrue(cb.containsKD(a));

		assertEquals(1, cb.size());
		
		long[] qMin = new long[]{3};
		long[] qMax = new long[]{3};
		QueryIteratorKD<Integer> it = null;
		
		//test normal queries
		it = cb.queryKD(qMin, qMax);
		long[] ra = it.nextKey();
		assertEquals(a[0], ra[0]);
		assertFalse(it.hasNext());

		//assert all
		//assert point search
		qMin = new long[]{-2};
		qMax = new long[]{4};
		it = cb.queryKD(qMin, qMax);
		ra = it.nextKey();
		assertEquals(a[0], ra[0]);
		assertFalse(it.hasNext());


		//assert none on invert
		Arrays.fill(qMin, Long.MIN_VALUE); 
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMax, qMin);
		assertFalse(it.hasNext());

		//assert none on too low
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, 2);
		it = cb.queryKD(qMin, qMax);
		assertFalse(it.hasNext());

		//assert none on too high
		Arrays.fill(qMin, 4); 
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMin, qMax);
		assertFalse(it.hasNext());
		
		((CritBit<?>)cb).checkTree();

		// delete stuff
		assertEquals(123, (int)cb.removeKD(a));

		// assert none on empty tree
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMin, qMax);
		assertFalse(it.hasNext());
	}


	
	@Test
	public void test64_K_queries_PositiveNumbers() {
		final int K = 5;
		final int W = 32; //bit depth
		final int N = 1000;
		for (int r = 0; r < 100; r++) {
			Random R = new Random(r);
			long[][] aa = new long[N][];
			CritBitKD<Integer> cb = newCritBit(W, K); 
			for (int i = 0; i < N; i++) {
				long[] a = new long[K];
				for (int k = 0; k < K; k++) {
					a[k] = Math.abs(R.nextLong())>>>(64-W);
				}
				
				if (cb.containsKD(a)) {
					//System.out.println("Duplicate: " + a[i]);
					i--;
					continue;
				}
				aa[i] = a;
				assertNull(cb.putKD(a, i));
				assertTrue(cb.containsKD(a));
			}
			
			assertEquals(N, cb.size());
	
			long[] qMin = new long[K];
			long[] qMax = new long[K];
			QueryIteratorKD<Integer> it = null;
			
			//test normal queries
			for (int i = 0; i < 10; i++) {
				createQuery(R, qMin, qMax);
			
				ArrayList<long[]> result = executeQuery(aa, qMin, qMax);
				it = cb.queryKD(qMin, qMax);
				
				int nResult = 0;
				while (it.hasNext()) {
					long[] ra = it.nextKey();
					nResult++;
					assertContains(aa, ra);
				}
					
				assertEquals("r=" + r + " i=" + i, result.size(), nResult);
			}			

			//assert all
			int n = 0;
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.queryKD(qMin, qMax);
			while (it.hasNext()) {
				it.next();
				n++;
			}
			assertEquals(aa.length, n);
			
			//assert point search
			for (long[] a: aa) {
				it = cb.queryKD(a, a);
//				System.out.println("Checking: " + Bits.toBinary(a, 32));
//				System.out.println("Checking: " + Bits.toBinary(BitTools.mergeLong(W, a), 64));
				assertTrue(it.hasNext());
				long[] r2 = it.nextKey();
				assertFalse(it.hasNext());
				assertTrue(isEqual(a, r2));
			}
			
			
			//assert none on invert
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.queryKD(qMax, qMin);
			assertFalse(it.hasNext());
			
			((CritBit<?>)cb).checkTree();
			
			// delete stuff
			for (long[] a: aa) {
				assertNotNull(cb.removeKD(a));
			}
			
			// assert none on empty tree
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.queryKD(qMin, qMax);
			assertFalse(it.hasNext());
		}
	}

	@Test
	public void testDelete64K3() {
		final int K = 3;
		for (int r = 0; r < 100; r++) {
			Random R = new Random(r);
			int N = 10000; 
			long[][] aa = new long[N][];
			CritBitKD<Integer> cb = newCritBit(64, K); 
			for (int i = 0; i < N; i++) {
				long[] a = new long[K];
				for (int k = 0; k < K; k++) {
					a[k] = BitTools.toSortableLong(R.nextDouble());//R.nextLong();
				}
				aa[i] = a;

				//System.out.println(a[i]>>>32 + ",");
				//System.out.println("Inserting: " + a[i] + " / " + BitsInt.toBinary(a[i]));
				if (cb.containsKD(a)) {
					boolean match = true;
					for (int j = 0; j < i; j++) {
						for (int k = 0; k < K; k++) {
							if (aa[j][k] != a[k]) {
								match = false;
								break;
							}
						}
					}
					if (match) {
						//System.out.println("Duplicate: " + a[i]);
						i--;
						continue;
					}
					fail("r=" + r + "  i= " + i);
				}
				assertNull(cb.putKD(a, i));
				assertEquals(i+1, cb.size());
			}
			
			assertEquals(N, cb.size());

			checkValues1D(cb, aa);
			
			((CritBit<?>)cb).checkTree();
			
			for (int i = 0; i < N>>1; i++) {
				long[] a = aa[i];
				assertTrue("r="+ r + " i=" + i, cb.containsKD(a));
				assertEquals(i, (int)cb.removeKD(a));
				a = aa[N-i-1];
				assertTrue("r="+ r + " i=" + (N-i-1), cb.containsKD(a));
				assertEquals(N-i-1, (int)cb.removeKD(a));
			}
			
			assertEquals(0, cb.size());
		}
	}

	@Test
	public void testDelete64K5() {
		final int K = 5;
		for (int r = 0; r < 100; r++) {
			Random R = new Random(r);
			int N = 10000;
			long[][] aa = new long[N][];
			CritBitKD<Integer> cb = newCritBit(64, K); 
			for (int i = 0; i < N; i++) {
				long[] a = new long[K];
				for (int k = 0; k < K; k++) {
					a[k] = BitTools.toSortableLong(R.nextDouble());//R.nextLong();
				}
				aa[i] = a;

				//System.out.println(a[i]>>>32 + ",");
				//System.out.println("Inserting: " + a[i] + " / " + BitsInt.toBinary(a[i]));
				if (cb.containsKD(a)) {
					boolean match = true;
					for (int j = 0; j < i; j++) {
						for (int k = 0; k < K; k++) {
							if (aa[j][k] != a[k]) {
								match = false;
								break;
							}
						}
					}
					if (match) {
						//System.out.println("Duplicate: " + a[i]);
						i--;
						continue;
					}
					fail("r=" + r + "  i= " + i);
				}
				assertNull(cb.putKD(a, i));
				assertEquals(i+1, cb.size());
			}
			
			assertEquals(N, cb.size());

			for (int i = 0; i < N>>1; i++) {
				long[] a = aa[i];
				assertEquals(i, (int)cb.removeKD(a));
				cb.removeKD(a);
				a = aa[N-i-1];
				assertEquals(N-i-1, (int)cb.removeKD(a));
			}
			
			assertEquals(0, cb.size());
		}
	}


	@Test
	public void testInsert64KBug1() {
		final int K = 3;
		CritBitKD<Integer> cb = newCritBit(64, K); 
		long[] A = new long[]{4603080768121727233L, 4602303061770585570L, 4604809596301821093L};
		long[] B = new long[]{4603082763292946186L, 4602305978608368320L, 4604812210005530572L};
		cb.putKD(A, 11);
		assertTrue(cb.containsKD(A));
		//cb.printTree();
		cb.putKD(B, 22);
		//cb.printTree();
		assertTrue(cb.containsKD(A));
		assertTrue(cb.containsKD(B));
	}
	
	@Test
	public void testInsert64KBug2() {
		final int K = 3;
		CritBitKD<Integer> cb = newCritBit(64, K); 
		long[] A = new long[]{4603080768121727233L, 4602303061770585570L, 4604809596301821093L};
		long[] B = new long[]{4603082763292946186L, 4602305978608368320L, 4604812210005530572L};
		cb.putKD(A, 11);
		assertTrue(cb.containsKD(A));
		cb.putKD(B, 22);
		assertTrue(cb.containsKD(A));
		assertTrue(cb.containsKD(B));
		cb.removeKD(B);
		assertTrue(cb.containsKD(A));
	}
	
//	@Test
//	public void testNegativeKeys() {
//		final int K = 2;
//		CritBitKD<Integer> cb = newCritBit(64, K); 
//		long[] MM = new long[]{-10, -10};
//		long[] MP = new long[]{-10,  10};
//		long[] PM = new long[]{ 10, -10};
//		long[] PP = new long[]{ 10,  10};
//		cb.putKD(MM, 11);
//		cb.putKD(MP, 22);
//		cb.putKD(PM, 33);
//		cb.putKD(PP, 44);
//		assertTrue(cb.containsKD(MM));
//		assertTrue(cb.containsKD(MP));
//		assertTrue(cb.containsKD(PM));
//		assertTrue(cb.containsKD(PP));
//		
//		//test all 4
//		long[] qMM = new long[]{-11, -11};
//		long[] qPP = new long[]{ 11,  11};
//		QueryIteratorKD<Integer> it;
//		
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 11, 22, 33, 44);
//		
//		//test negative quadrants
//		qMM = new long[]{ -11,  -11};
//		qPP = new long[]{  -1,   -1};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 11);
//		
//		qMM = new long[]{ -11, -11};
//		qPP = new long[]{   1,   1};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 11);
//		
//		qMM = new long[]{ -1,  -11};
//		qPP = new long[]{ 11,   -1};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 33);
//		
//		qMM = new long[]{ -11,  -1};
//		qPP = new long[]{  -1,  11};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 22);
//
//		//test 2-s
//		qMM = new long[]{  1,  11};
//		qPP = new long[]{ 11,  11};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 33, 44);
//		
//		qMM = new long[]{ -1,  11};
//		qPP = new long[]{ 11,  11};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 33, 44);
//
//		
//		qMM = new long[]{ 11,   1};
//		qPP = new long[]{ 11,  11};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 22, 44);
//		
//		qMM = new long[]{ 11,  -1};
//		qPP = new long[]{ 11,  11};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 22, 44);
//
//		
//		qMM = new long[]{ -11, -11};
//		qPP = new long[]{   1,  11};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 11, 33);
//		
//		qMM = new long[]{ -11, -11};
//		qPP = new long[]{  -1,  11};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 11, 33);
//
//		
//		qMM = new long[]{ -11, -11};
//		qPP = new long[]{  11,   1};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 11, 22);
//		
//		qMM = new long[]{ -11, -11};
//		qPP = new long[]{  11,  -1};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it, 11, 22);
//
//	
//		//test 00
//		qMM = new long[]{ -1,  -1};
//		qPP = new long[]{  1,   1};
//		it = cb.queryKD(qMM, qPP);
//		checkResult(it);
//
//	}
//	
//	@SuppressWarnings("unchecked")
//	private <T> void checkResult(QueryIteratorKD<T> it, T...ts) {
//		HashSet<T> hs = new HashSet<>();
//		hs.addAll(Arrays.asList(ts));
//		while (it.hasNext()) {
//			T t = it.next();
//			System.out.println("Found: " + t);
//			assertTrue(hs.remove(t));
//		}
//		for (T t: hs) {
//			System.out.println("Expected to find: " + t);
//		}
//		assertTrue(hs.isEmpty());
//	}
	
	
	@Test
	public void testIteratorWithNullValues() {
		int N = 10000;
		int k = 5;
		Random R = new Random(0);

		CritBitKD<?> cb = newCritBit(64, k);
		long[][] data = new long[N][];
		for (int i = 0; i < N; i++) {
			long[] l = new long[k];
			for (int d = 0; d < k; d++) {
				l[d] = R.nextInt(12345); 
			}
			data[i] = l;
			cb.putKD(l, null);
		}
		
		//test extent
//		CritBitKD<?> it = cb.iterator();
		int n = 0;
//		while (it.hasNext()) {
//			Entry<?> e = it.nextEntry();
//			assertNull(e.value());
//			n++;
//		}
//		assertEquals(N, n);
		
		//test query
		long[] min = new long[k];
		long[] max = new long[k];
		Arrays.fill(min, Long.MIN_VALUE);
		Arrays.fill(max, Long.MAX_VALUE);
		QueryIteratorKD<?> qi = cb.queryKD(min, max);
		n = 0;
		while (qi.hasNext()) {
			Entry<?> e = qi.nextEntry();
			assertNull(e.value());
			n++;
		}
		assertEquals(N, n);
	}
	
	private void checkValues1D(CritBitKD<Integer> cb, long[][] aa) {
		for (int i = 0; i < aa.length; i++) {
			Integer v = cb.getKD(aa[i]);
			if (v == null) {
				cb.printTree();
			}
			assertNotNull("i="+ i, v);
			assertEquals("i="+ i, i, (int)v);
		}
	}
	
	private boolean isEqual(long[] a, long[] r) {
		for (int k = 0; k < a.length; k++) {
			if (a[k] != r[k]) {
				return false;
			}
		}
		return true;
	}

	private void assertContains(long[][] aa, long[] r) {
		for (long[] a: aa) {
			boolean match = true;
			for (int k = 0; k < a.length; k++) {
				if (a[k] != r[k]) {
					match = false;
					break;
				}
			}
			if (match) {
				return;
			}
		}
		fail();
	}

	private ArrayList<long[]> executeQuery(long[][] aa, long[] qMin, long[] qMax) {
		final int K = qMin.length;
		final int N = aa.length;
		ArrayList<long[]> r = new ArrayList<long[]>();
		for (int i = 0; i < N; i++) {
			boolean match = true;
			for (int k = 0; k < K; k++) {
				if (aa[i][k] < qMin[k] || aa[i][k] > qMax[k]) {
					match = false;
					break;
				}
			}
			if (match) {
				r.add(aa[i]);
			}
		}
		return r;
	}

	private void createQuery(Random R, long[] qMin, long[] qMax) {
		final int K = qMin.length;
		for (int k = 0; k < K; k++) {
			qMin[k] = R.nextLong();
			qMax[k] = R.nextLong();
			if (qMax[k] < qMin[k]) {
				long l = qMin[k];
				qMin[k] = qMax[k];
				qMax[k] = l;
			}
		}
	}
}
