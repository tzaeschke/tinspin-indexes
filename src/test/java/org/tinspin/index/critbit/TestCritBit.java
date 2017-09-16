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
package org.tinspin.index.critbit;

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
import org.tinspin.index.critbit.CritBit;
import org.tinspin.index.critbit.CritBit1D;
import org.tinspin.index.critbit.CritBit.CheckEmptyWithMask;
import org.tinspin.index.critbit.CritBit.Entry;
import org.tinspin.index.critbit.CritBit.FullIterator;
import org.tinspin.index.critbit.CritBit.QueryIterator;
import org.tinspin.index.critbit.CritBit.QueryIteratorWithMask;

/**
 * 
 * @author Tilmann Zaeschke
 */
public class TestCritBit {

	private CritBit1D<Integer> newCritBit(int depth) {
		return CritBit.create1D(depth);
	}
	
	@Test
	public void testInsertIntRBug1() {
		randomInsertCheck(1000, 7374, 32);
	}
	
	@Test
	public void testInsertIntRBug2() {
		randomInsertCheck(1000, 95109, 32);
	}
	
	@Test
	public void testInsertIntRBug3() {
		//randomInsertCheck(1000, 32810, 16);
		long[] a = {3026137474616262656L,
				-8808477921183268864L,
				6451124991231524864L,
				-8025696010950934528L,
				6684749221901369344L,
				-1779484802764767232L,
				-9223372036854775808L				
		};
		CritBit1D<Integer> cb = newCritBit(16);
		for (long l: a) {
			//cb.printTree();
			//System.out.println("Inserting: " + l + " --- " + Bits.toBinary(l, 64));
			assertNull(cb.put(new long[]{l}, 1));
			assertNotNull(cb.put(new long[]{l}, 2));
		}
	}
	
	@Test
	public void testInsertIntRBug4() {
		randomInsertCheck(23, 0, 8);
	}
	
	@Test
	public void testInsertIntR() {
		randomInsertCheck(1000000, 0, 32);
	}
	
	private int iMinFail = Integer.MAX_VALUE;
	private int iFail = Integer.MAX_VALUE;
	private int rMinFail = 0;
	private Throwable tMinFail = null;
	
	@Test
	public void testInsertIntR2() {
		int r = 0;
		for (r = 0; r < 1000; r++) {
			try {
				randomInsertCheck(1000, r, 16);
			} catch (AssertionError e) {
				if (iFail < iMinFail) {
					iMinFail = iFail;
					rMinFail = r;
					tMinFail = e;
				}
			}
		}
		if (tMinFail != null) {
			tMinFail.printStackTrace();
			fail("i=" + iMinFail + "  r=" + rMinFail + "  " + tMinFail.getMessage());
		}
	}
	
	
	private void randomInsertCheck(final int N, final int SEED, int DEPTH) {
		Random R = new Random(SEED);
		long[] a = new long[N];
		CritBit1D<Integer> cb = newCritBit(DEPTH); 
		for (int i = 0; i < N; i++) {
			iFail = i;
			a[i] = ((long)R.nextInt()) << (64-DEPTH);
			//System.out.println((int)(a[i]>>>32) + ",");
			//System.out.println("Inserting: " + a[i] + " / " + BitsInt.toBinary(a[i] >>> 32));
			//System.out.println("i1=" + i + " / " + a[i]);
			if (cb.contains(new long[]{a[i]})) {
				//System.out.println("i2=" + i);
				boolean isDuplicate = false;
				for (int j = 0; j < i; j++) {
					if (a[j] == a[i]) {
						//System.out.println("Duplicate: " + a[i]);
						isDuplicate = true;
						break;
					}
				}
				if (isDuplicate) {
					//Check if at least insert() recognises
					i--;
					continue;
				}
				fail("i= " + i);
			}
			assertNull("i=" + i + " v=" + a[i], cb.put(new long[]{a[i]}, 12345+i));
			//cb.printTree();
			assertEquals("i=" + i, 12345+i, (int)cb.put(new long[]{a[i]}, i));
			//cb.printTree();
			assertEquals(i+1, cb.size());
			assertTrue(cb.contains(new long[]{a[i]}));
			assertEquals("i=" + i, i, (int)cb.get(new long[]{a[i]}));
		}
		
		assertEquals(N, cb.size());

		for (int i = 0; i < N; i++) {
			//System.out.println("Checking: " + i + "   " + BitsInt.toBinary(a[i] >>> 32));
			assertTrue(cb.contains(new long[]{a[i]}));
			if (i != (int)cb.get(new long[]{a[i]})) {
				cb.printTree();
			}
			assertEquals("i=" + i, i, (int)cb.get(new long[]{a[i]}));
		}
		
		((CritBit<?>)cb).checkTree();
		
		for (int i = 0; i < N; i++) {
			assertTrue(cb.contains(new long[]{a[i]}));
			assertEquals(i, (int)cb.remove(new long[]{a[i]}));
			assertEquals(null, cb.remove(new long[]{a[i]}));
			assertFalse(cb.contains(new long[]{a[i]}));
			assertEquals(N-i-1, cb.size());
		}
		
		assertEquals(0, cb.size());		
	}
	
	@Test
	public void testBugInsert1() {
		long[] a = new long[]{ 
				-723955400,
				-1690734402,
				-1728529858,
				-1661998771};
		CritBit1D<Integer> cb = newCritBit(32); 
		for (int i = 0; i < a.length; i++) {
			a[i] = a[i] << 32;
			assertFalse(cb.contains(new long[]{a[i]}));
			assertNull(cb.put(new long[]{a[i]}, i));
			//cb.printTree();
			assertEquals(i, (int)cb.put(new long[]{a[i]}, i));
			//cb.printTree();
			assertEquals(i+1, cb.size());
			assertTrue(cb.contains(new long[]{a[i]}));
		}

		assertEquals(a.length, cb.size());

		for (int i = 0; i < a.length; i++) {
			assertTrue(cb.contains(new long[]{a[i]}));
		}
	}
	
	@Test
	public void testBugInsert2() {
		long[] a = new long[]{ 
				-65105105,
				-73789608,
				-518907128};
		CritBit1D<Integer> cb = newCritBit(32); 
		for (int i = 0; i < a.length; i++) {
			a[i] = a[i] << 32;
			//System.out.println("Inserting: " + a[i] + " / " + BitsInt.toBinary(a[i] >>> 32));
			assertFalse(cb.contains(new long[]{a[i]}));
			assertNull(cb.put(new long[]{a[i]}, i));
			//cb.printTree();
			assertEquals(i, (int)cb.put(new long[]{a[i]}, i));
			//cb.printTree();
			assertEquals(i+1, cb.size());
			assertTrue(cb.contains(new long[]{a[i]}));
		}

		assertEquals(a.length, cb.size());

		for (int i = 0; i < a.length; i++) {
			//System.out.println("Checking: " + i + "   " + BitsInt.toBinary(a[i] >>> 32));
			assertTrue(cb.contains(new long[]{a[i]}));
		}
	}
	
	@Test
	public void test64() {
		for (int r = 0; r < 1000; r++) {
			Random R = new Random(r);
			int N = 1000;
			long[] a = new long[N];
			CritBit1D<Integer> cb = newCritBit(64); 
			for (int i = 0; i < N; i++) {
				a[i] = R.nextLong();
				//System.out.println(a[i]>>>32 + ",");
				//System.out.println("Inserting: " + a[i] + " / " + BitTools.toBinary(a[i], 64));
				if (cb.contains(new long[]{a[i]})) {
					for (int j = 0; j < i; j++) {
						if (a[j] == a[i]) {
							//System.out.println("Duplicate: " + a[i]);
							i--;
							continue;
						}
					}
					fail("r=" + r + "  i= " + i);
				}
				assertNull(cb.put(new long[]{a[i]}, i+12345));
				//cb.printTree();
				assertEquals(i+12345, (int)cb.put(new long[]{a[i]}, i));
				//cb.printTree();
				assertEquals(i+1, cb.size());
				assertTrue(cb.contains(new long[]{a[i]}));
			}
			
			assertEquals(N, cb.size());
	
			for (int i = 0; i < N; i++) {
				//System.out.println("Checking: " + i + "   " + BitsInt.toBinary(a[i] >>> 32));
				assertTrue(cb.contains(new long[]{a[i]}));
			}
			
			((CritBit<?>)cb).checkTree();

			for (int i = 0; i < N; i++) {
				assertTrue(cb.contains(new long[]{a[i]}));
				assertEquals(i, (int)cb.remove(new long[]{a[i]}));
				assertNull(cb.remove(new long[]{a[i]}));
				assertFalse(cb.contains(new long[]{a[i]}));
				assertEquals(N-i-1, cb.size());
			}
			
			assertEquals(0, cb.size());
		}
	}

	@Test
	public void testIterators64() {
		for (int r = 0; r < 1000; r++) {
			Random R = new Random(r);
			int N = 1000;
			long[] a = new long[N];
			CritBit1D<Integer> cb = newCritBit(64); 
			for (int i = 0; i < N; i++) {
				a[i] = R.nextLong();
				//System.out.println(a[i]>>>32 + ",");
				//System.out.println("Inserting: " + a[i] + " / " + BitTools.toBinary(a[i], 64));
				if (cb.contains(new long[]{a[i]})) {
					i--;
					continue;
				}
				assertNull(cb.put(new long[]{a[i]}, i));
			}
			
			assertEquals(N, cb.size());
	
			//test iterators
			long[] min = new long[1];
			long[] max = new long[1];
			Arrays.fill(min, Long.MIN_VALUE);
			Arrays.fill(max, Long.MAX_VALUE);
			//value iteration
			QueryIterator<Integer> it = cb.query(min, max);
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
			it = cb.query(min, max);
			n = 0;
			while (it.hasNext()) {
				long[] key = it.nextKey();
				//assure same order
				assertEquals(sortedResults.get(n), cb.get(key));
				n++;
			}
			assertEquals(N, n);
			//entry iteration
			it = cb.query(min, max);
			n = 0;
			while (it.hasNext()) {
				Entry<Integer> e = it.nextEntry();
				long[] key = e.key();
				assertEquals(sortedResults.get(n), cb.get(key));
				assertEquals(sortedResults.get(n), e.value());
				n++;
			}
			assertEquals(N, n);
		}
	}

	@Test
	public void test64Bug1() {
		Random R = new Random(0);
		int N = 6;
		long[] a = new long[N];
		CritBit1D<Integer> cb = newCritBit(64); 
		for (int i = 0; i < N; i++) {
			a[i] = R.nextLong();
			//System.out.println(a[i]>>>32 + ",");
			//System.out.println("Inserting: " + a[i] + " / " + BitTools.toBinary(a[i], 64));
			if (cb.contains(new long[]{a[i]})) {
				for (int j = 0; j < i; j++) {
					if (a[j] == a[i]) {
						//System.out.println("Duplicate: " + a[i]);
						i--;
						continue;
					}
				}
				fail("i= " + i);
			}
			assertNull(cb.put(new long[]{a[i]}, i));
		}

		assertEquals(N, cb.size());
		
		((CritBit<?>)cb).checkTree();

		for (int i = 0; i < N; i++) {
			//cb.printTree();
			assertEquals(i, (int)cb.remove(new long[]{a[i]}));
			//cb.printTree();
			assertTrue("i="+i, ((CritBit<?>)cb).checkTree());
			assertNull(cb.remove(new long[]{a[i]}));
			assertTrue("i="+i, ((CritBit<?>)cb).checkTree());
		}

		assertEquals(0, cb.size());
	}

	@Test
	public void test64Bug2() {
		Random R = new Random(1);
		int N = 7;
		long[] a = new long[N];
		CritBit1D<Integer> cb = newCritBit(64); 
		for (int i = 0; i < N; i++) {
			a[i] = R.nextLong();
			//System.out.println(a[i]>>>32 + ",");
			//System.out.println("Inserting: " + a[i] + " / " + BitTools.toBinary(a[i], 64));
			if (cb.contains(new long[]{a[i]})) {
				for (int j = 0; j < i; j++) {
					if (a[j] == a[i]) {
						//System.out.println("Duplicate: " + a[i]);
						i--;
						continue;
					}
				}
				fail("i= " + i);
			}
			assertNull(cb.put(new long[]{a[i]}, i));
			assertEquals(i+1, cb.size());
			assertTrue(((CritBit<?>)cb).checkTree());
		}

		assertEquals(N, cb.size());
		
		((CritBit<?>)cb).checkTree();

		for (int i = 0; i < N; i++) {
			//cb.printTree();
			assertTrue("i=" + i, ((CritBit<?>)cb).checkTree());
			assertTrue("i=" + i, cb.contains(new long[]{a[i]}));
			assertEquals(i, (int)cb.remove(new long[]{a[i]}));
			assertNull(cb.remove(new long[]{a[i]}));
			assertFalse(cb.contains(new long[]{a[i]}));
			assertEquals(N-i-1, cb.size());
		}

		assertEquals(0, cb.size());
	}

	@Test
	public void test64_True1D_queries_PositiveNumbers() {
		final int K = 1;
		for (int r = 0; r < 1000; r++) {
			Random R = new Random(r);
			int N = 1000;
			long[][] aa = new long[N][];
			CritBit1D<Integer> cb = newCritBit(64); 
			for (int i = 0; i < N; i++) {
				long[] a = new long[K];
				for (int k = 0; k < K; k++) {
					a[k] = Math.abs(R.nextLong());
				}
				
				if (cb.contains(a)) {
					//System.out.println("Duplicate: " + a[i]);
					i--;
					continue;
				}
				aa[i] = a;
				assertNull(cb.put(a, i));
				assertTrue(cb.contains(a));
			}
			
			assertEquals(N, cb.size());
	
			long[] qMin = new long[K];
			long[] qMax = new long[K];
			QueryIterator<Integer> it = null;

			//test normal queries
			for (int i = 0; i < 10; i++) {
				createQuery(R, qMin, qMax);
			
				ArrayList<long[]> result = executeQuery(aa, qMin, qMax);
				it = cb.query(qMin, qMax);
				
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
			it = cb.query(qMin, qMax);
			while (it.hasNext()) {
				it.next();
				//System.out.println("r1:" + it.next()[0]);
				n++;
			}
			assertEquals(N, n);

			//assert point search
			for (long[] a: aa) {
				it = cb.query(a, a);
				assertTrue(it.hasNext());
				long[] r2 = it.nextKey();
				assertFalse(it.hasNext());
				assertTrue(isEqual(a, r2));
			}
			
			
			//assert none on invert
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.query(qMax, qMin);
			assertFalse(it.hasNext());
			
			((CritBit<?>)cb).checkTree();
			
			// delete stuff
			for (int i = 0; i < N; i++) {
				assertEquals(i, (int)cb.remove(aa[i]));
			}
			
			// assert none on empty tree
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.query(qMin, qMax);
			assertFalse(it.hasNext());
		}
	}

	@Test
	public void test64_True1D_256bit_1() {
		final int K = 1;
		long[][] aa = new long[][]{{5,5,5,5}};
		int N = aa.length;
		CritBit1D<Integer> cb = newCritBit(256); 
		assertNull(cb.put(aa[0], 5555));
		assertTrue(cb.contains(aa[0]));

		assertEquals(N, cb.size());

		long[] qMin = new long[K];
		long[] qMax = new long[K];
		QueryIterator<Integer> it = null;

		//test special
		qMin = new long[]{5,5,0,0};
		qMax = new long[]{5,5,8,8};
		int n = 0;
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(N, n);

		qMin = new long[]{5,4,16,0};
		qMax = new long[]{5,8,16,0};
		n = 0;
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(N, n);

		qMin = new long[]{5,8,0,0};
		qMax = new long[]{5,9,0,0};
		n = 0;
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(0, n);

		//assert all
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMin, qMax);
		n = 0;
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(1, n);

		//assert point search
		for (long[] a: aa) {
			it = cb.query(a, a);
			assertTrue(it.hasNext());
			long[] r2 = it.nextKey();
			assertFalse(it.hasNext());
			assertTrue(isEqual(a, r2));
		}


		//assert none on invert
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMax, qMin);
		assertFalse(it.hasNext());
		
		((CritBit<?>)cb).checkTree();

		// delete stuff
		for (int i = 0; i < N; i++) {
			assertEquals(5555, (int)cb.remove(aa[i]));
		}

		// assert none on empty tree
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMin, qMax);
		assertFalse(it.hasNext());
	}

	@Test
	public void test64_True1D_256bit_2() {
		final int K = 1;
		long[][] aa = new long[][]{{5,5,5,5}, {5,5,8,8}};
		CritBit1D<Integer> cb = newCritBit(256); 
		assertNull(cb.put(aa[0], 5555));
		assertTrue(cb.contains(aa[0]));
		assertNull(cb.put(aa[1], 5588));
		assertTrue(cb.contains(aa[1]));

		assertEquals(2, cb.size());

		long[] qMin = new long[K];
		long[] qMax = new long[K];
		QueryIterator<Integer> it = null;

		//test special
		qMin = new long[]{5,5,0,0};
		qMax = new long[]{5,5,8,8};
		int n = 0;
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(2, n);

		qMin = new long[]{5,4,16,0};
		qMax = new long[]{5,8,16,0};
		n = 0;
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(2, n);

		qMin = new long[]{5,8,0,0};
		qMax = new long[]{5,9,0,0};
		n = 0;
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(0, n);

		//assert all
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(2, n);

		//assert point search
		for (long[] a: aa) {
			it = cb.query(a, a);
			assertTrue(it.hasNext());
			long[] r2 = it.nextKey();
			assertFalse(it.hasNext());
			assertTrue(isEqual(a, r2));
		}


		//assert none on invert
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMax, qMin);
		assertFalse(it.hasNext());
		
		((CritBit<?>)cb).checkTree();

		// delete stuff
		//for (int i = 0; i < 2; i++) {
		assertEquals(5555, (int)cb.remove(aa[0]));
		assertEquals(5588, (int)cb.remove(aa[1]));
		//}

		// assert none on empty tree
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMin, qMax);
		assertFalse(it.hasNext());
	}

	@Test
	public void test64_True1D_256bit_2b() {
		final int K = 1;
		long[][] aa = new long[][]{{5,8,5,5}, {5,8,8,8}};
		CritBit1D<Integer> cb = newCritBit(256); 
		assertNull(cb.put(aa[0], 5555));
		assertTrue(cb.contains(aa[0]));
		assertNull(cb.put(aa[1], 5588));
		assertTrue(cb.contains(aa[1]));

		assertEquals(2, cb.size());

		long[] qMin = new long[K];
		long[] qMax = new long[K];
		QueryIterator<Integer> it = null;

		//test special
		qMin = new long[]{5,5,0,0};
		qMax = new long[]{5,8,8,8};
		int n = 0;
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(2, n);

		qMin = new long[]{5,4,16,0};
		qMax = new long[]{5,8,16,0};
		n = 0;
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(2, n);

		qMin = new long[]{5,18,0,0};
		qMax = new long[]{5,19,0,0};
		n = 0;
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(0, n);

		
		//assert all
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMin, qMax);
		while (it.hasNext()) {
			it.next();
			//System.out.println("r1:" + it.next()[0]);
			n++;
		}
		assertEquals(2, n);

		//assert point search
		for (long[] a: aa) {
			it = cb.query(a, a);
			assertTrue(it.hasNext());
			long[] r2 = it.nextKey();
			assertFalse(it.hasNext());
			assertTrue(isEqual(a, r2));
		}


		//assert none on invert
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMax, qMin);
		assertFalse(it.hasNext());
		
		((CritBit<?>)cb).checkTree();

		// delete stuff
		//for (int i = 0; i < 2; i++) {
		assertEquals(5555, (int)cb.remove(aa[0]));
		assertEquals(5588, (int)cb.remove(aa[1]));
		//}

		// assert none on empty tree
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMin, qMax);
		assertFalse(it.hasNext());
	}

	@Test
	public void test64_True1D_queries_Single() {
		long[] a = new long[]{3};
		CritBit1D<Integer> cb = newCritBit(64);
		assertNull(cb.put(a, 123));
		assertTrue(cb.contains(a));

		assertEquals(1, cb.size());
		
		long[] qMin = new long[]{3};
		long[] qMax = new long[]{3};
		QueryIterator<Integer> it = null;
		
		//test normal queries
		it = cb.query(qMin, qMax);
		long[] ra = it.nextKey();
		assertEquals(a[0], ra[0]);
		assertFalse(it.hasNext());

		//assert all
		//assert point search
		qMin = new long[]{-2};
		qMax = new long[]{4};
		it = cb.query(qMin, qMax);
		ra = it.nextKey();
		assertEquals(a[0], ra[0]);
		assertFalse(it.hasNext());


		//assert none on invert
		Arrays.fill(qMin, Long.MIN_VALUE); 
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMax, qMin);
		assertFalse(it.hasNext());

		//assert none on too low
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, 2);
		it = cb.query(qMin, qMax);
		assertFalse(it.hasNext());

		//assert none on too high
		Arrays.fill(qMin, 4); 
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMin, qMax);
		assertFalse(it.hasNext());
		
		((CritBit<?>)cb).checkTree();

		// delete stuff
		assertEquals(123, (int)cb.remove(a));

		// assert none on empty tree
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMin, qMax);
		assertFalse(it.hasNext());
	}

	@Test
	public void testIterator() {
		int N = 10000;
		int k = 5;
		Random R = new Random(0);

		CritBit1D<Integer> cb = newCritBit(k*64);
		long[][] data = new long[N][];
		for (int i = 0; i < N; i++) {
			long[] l = new long[k];
			for (int d = 0; d < k; d++) {
				l[d] = R.nextInt(12345); 
			}
			data[i] = l;
			cb.put(l, i);
		}
		
		//test
		FullIterator<Integer> it = cb.iterator();
		int n = 0;
		while (it.hasNext()) {
			Entry<Integer> e = it.nextEntry();
			assertTrue(isEqual(data[e.value()], e.key()));
			n++;
		}
		assertEquals(N, n);
	}
	
	@Test
	public void testIteratorWithNullValues() {
		int N = 10000;
		int k = 5;
		Random R = new Random(0);

		CritBit1D<?> cb = newCritBit(k*64);
		long[][] data = new long[N][];
		for (int i = 0; i < N; i++) {
			long[] l = new long[k];
			for (int d = 0; d < k; d++) {
				l[d] = R.nextInt(12345); 
			}
			data[i] = l;
			cb.put(l, null);
		}
		
		//test extent
		FullIterator<?> it = cb.iterator();
		int n = 0;
		while (it.hasNext()) {
			Entry<?> e = it.nextEntry();
			assertNull(e.value());
			n++;
		}
		assertEquals(N, n);
		
		//test query
		long[] min = new long[k];
		long[] max = new long[k];
		Arrays.fill(min, Long.MIN_VALUE);
		Arrays.fill(max, Long.MAX_VALUE);
		QueryIterator<?> qi = cb.query(min, max);
		n = 0;
		while (qi.hasNext()) {
			Entry<?> e = qi.nextEntry();
			assertNull(e.value());
			n++;
		}
		assertEquals(N, n);
	}
	
	@Test
	public void testIteratorWithMask() {
		int N = 10000;
		int k = 5;
		int w = 64;
		Random R = new Random(0);

		CritBit1D<Integer> cb = newCritBit(64*w);
		long[][] data = new long[N][];
		for (int i = 0; i < N; i++) {
			long[] l = new long[k];
			for (int d = 0; d < k; d++) {
				l[d] = R.nextInt(12345); 
			}
			data[i] = l;
			cb.put(l, i);
		}
		
		long[] minMask = new long[64];
		long[] maxMask = new long[64];
		Arrays.fill(maxMask, 0x7FFFFFFFFFFFFFFFL);
		
		//test
		QueryIteratorWithMask<Integer> it = new QueryIteratorWithMask<>((CritBit<Integer>)cb, minMask, maxMask, k);
		int n = 0;
		while (it.hasNext()) {
			Entry<Integer> e = it.nextEntry();
			assertNotNull(e);
			//TODO proper test
			//assertTrue(isEqual(data[e.value()], e.key()));
			n++;
		}
		assertEquals(N, n);
	}
	
	@Test
	public void testCheckEmptyWithMask() {
		int N = 10000;
		int k = 5;
		int w = 64;
		Random R = new Random(0);

		CritBit1D<Integer> cb = newCritBit(64*w);
		long[][] data = new long[N][];
		for (int i = 0; i < N; i++) {
			long[] l = new long[k];
			for (int d = 0; d < k; d++) {
				l[d] = R.nextInt(12345); 
			}
			data[i] = l;
			cb.put(l, i);
		}
		
		long[] minMask = new long[w];
		long[] maxMask = new long[w];
		Arrays.fill(maxMask, 0x7FFFFFFFFFFFFFFFL);
		
		//test
		CheckEmptyWithMask it = new CheckEmptyWithMask((CritBit<Integer>)cb, k);
		//TODO proper test
		assertFalse(it.isEmpty(minMask, maxMask, false));
		assertFalse(it.isEmpty(minMask, maxMask, false));
		assertTrue(it.isEmpty(minMask, minMask, false));
		assertTrue(it.isEmpty(minMask, minMask, false));
	}
	
	@Test
	public void testPrint() {
		int N = 100;
		int k = 5;
		Random R = new Random(0);

		CritBit1D<?> cb = newCritBit(k*64);
		long[][] data = new long[N][];
		for (int i = 0; i < N; i++) {
			long[] l = new long[k];
			for (int d = 0; d < k; d++) {
				l[d] = R.nextInt(12345); 
			}
			data[i] = l;
			cb.put(l, null);
		}

		String s = cb.toString();
		for (long[] d : data) {
			//This works for now, because we store all bits, not just the trailing postfix
			String x = BitTools.toBinary(d, 64);
			assertTrue(s.contains(x));
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


	@Test
	public void test64_doesInfixMatch() {
		long[] a1 = new long[]{0,0,0};
		long[] a2 = new long[]{0,0,1};
		long[] a3 = new long[]{1,0,0};
		long[] a4 = new long[]{1,1,0};
		
		assertTrue(doesInfixMatch(a1, 191, a2, 0)); 
		assertFalse(doesInfixMatch(a1, 192, a2, 0)); 
		
		assertTrue(doesInfixMatch(a1, 63, a3, 0)); 
		assertFalse(doesInfixMatch(a1, 64, a3, 0)); 
		
		assertTrue(doesInfixMatch(a3, 127, a4, 0)); 
		assertFalse(doesInfixMatch(a3, 128, a4, 0)); 
	}
	
	@Test
	public void testExamples() {
		//just test that it does not fail
		Examples.main(null);
	}
	
	private boolean doesInfixMatch(long[] currentVal, int posDiff, long[] v,
			int startSlot) {
//		if (n.infix == null) {
//			return true;
//		}
		
		int end = (posDiff-1) >>> 6; 
		for (int i = startSlot; i < end; i++) {
			if (v[i] != currentVal[i]) {
				return false;
			}
		}
		//last elements
		int shift = 63 - ((posDiff-1) & 0x3f);
		return (v[end] ^ currentVal[end]) >>> shift == 0;
	}


}
