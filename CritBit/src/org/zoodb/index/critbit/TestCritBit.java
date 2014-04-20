/*
 * Copyright 2009-2014 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of ZooDB.
 * 
 * ZooDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ZooDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ZooDB.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * See the README and COPYING files for further information. 
 */
package org.zoodb.index.critbit;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import org.junit.Test;

public class TestCritBit {

	private CritBit<Integer> newCritBit(int depth) {
		return CritBit.create(depth);
	}
	
	private CritBit<Integer> newCritBit(int depth, int K) {
		return CritBit.createKD(depth, K);
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
		CritBit<Integer> cb = newCritBit(16);
		for (long l: a) {
			//cb.printTree();
			//System.out.println("Inserting: " + l + " --- " + Bits.toBinary(l, 64));
			assertNull(cb.insert(new long[]{l}, 1));
			assertNotNull(cb.insert(new long[]{l}, 2));
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
		CritBit<Integer> cb = newCritBit(DEPTH); 
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
			assertNull("i=" + i + " v=" + a[i], cb.insert(new long[]{a[i]}, 12345+i));
			//cb.printTree();
			assertEquals("i=" + i, 12345+i, (int)cb.insert(new long[]{a[i]}, i));
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
		CritBit<Integer> cb = newCritBit(32); 
		for (int i = 0; i < a.length; i++) {
			a[i] = a[i] << 32;
			assertFalse(cb.contains(new long[]{a[i]}));
			assertNull(cb.insert(new long[]{a[i]}, i));
			//cb.printTree();
			assertEquals(i, (int)cb.insert(new long[]{a[i]}, i));
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
		CritBit<Integer> cb = newCritBit(32); 
		for (int i = 0; i < a.length; i++) {
			a[i] = a[i] << 32;
			//System.out.println("Inserting: " + a[i] + " / " + BitsInt.toBinary(a[i] >>> 32));
			assertFalse(cb.contains(new long[]{a[i]}));
			assertNull(cb.insert(new long[]{a[i]}, i));
			//cb.printTree();
			assertEquals(i, (int)cb.insert(new long[]{a[i]}, i));
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
			CritBit<Integer> cb = newCritBit(64); 
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
				assertNull(cb.insert(new long[]{a[i]}, i));
				//cb.printTree();
				assertEquals(i, (int)cb.insert(new long[]{a[i]}, i));
				//cb.printTree();
				assertEquals(i+1, cb.size());
				assertTrue(cb.contains(new long[]{a[i]}));
			}
			
			assertEquals(N, cb.size());
	
			for (int i = 0; i < N; i++) {
				//System.out.println("Checking: " + i + "   " + BitsInt.toBinary(a[i] >>> 32));
				assertTrue(cb.contains(new long[]{a[i]}));
			}

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
	public void test64Bug1() {
		Random R = new Random(0);
		int N = 6;
		long[] a = new long[N];
		CritBit<Integer> cb = newCritBit(64); 
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
			assertNull(cb.insert(new long[]{a[i]}, i));
		}

		assertEquals(N, cb.size());

		for (int i = 0; i < N; i++) {
			//cb.printTree();
			assertEquals(i, (int)cb.remove(new long[]{a[i]}));
			//cb.printTree();
			assertTrue("i="+i, cb.checkTree());
			assertNull(cb.remove(new long[]{a[i]}));
			assertTrue("i="+i, cb.checkTree());
		}

		assertEquals(0, cb.size());
	}

	@Test
	public void test64Bug2() {
		Random R = new Random(1);
		int N = 7;
		long[] a = new long[N];
		CritBit<Integer> cb = newCritBit(64); 
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
			assertNull(cb.insert(new long[]{a[i]}, i));
			assertEquals(i+1, cb.size());
			assertTrue(cb.checkTree());
		}

		assertEquals(N, cb.size());

		for (int i = 0; i < N; i++) {
			//cb.printTree();
			assertTrue("i=" + i, cb.checkTree());
			assertTrue("i=" + i, cb.contains(new long[]{a[i]}));
			assertEquals(i, (int)cb.remove(new long[]{a[i]}));
			assertNull(cb.remove(new long[]{a[i]}));
			assertFalse(cb.contains(new long[]{a[i]}));
			assertEquals(N-i-1, cb.size());
		}

		assertEquals(0, cb.size());
	}

	@Test
	public void test64_2() {
		final int K = 2;
		for (int r = 0; r < 1000; r++) {
			Random R = new Random(r);
			int N = 1000;
			long[] aa = new long[2*N];
			CritBit<Integer> cb = newCritBit(64, 2); 
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
				assertNull(cb.insertKD(a, n+12345));
				//cb.printTree();
				assertEquals(n+12345, (int)cb.insertKD(a, n));
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
			int N = 10000;
			long[] aa = new long[K*N];
			CritBit<Integer> cb = newCritBit(64, K); 
			for (int i = 0; i < N; i++) {
				for (int k = 0; k < K; k++) {
					aa[i*K+k] = R.nextLong();
				}
				//System.out.println(a[i]>>>32 + ",");
				//System.out.println("Inserting: " + a[i] + " / " + BitsInt.toBinary(a[i]));
				long[] a = new long[K];
				System.arraycopy(aa, i*K, a, 0, a.length);
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
				assertNull(cb.insertKD(a, 12345+i));
				//cb.printTree();
				assertEquals(12345+i, (int)cb.insertKD(a, i));
				//cb.printTree();
				assertEquals(i+1, cb.size());
				assertTrue(cb.containsKD(a));
			}
			
			assertEquals(N, cb.size());
	
			for (int i = 0; i < N; i++) {
				//System.out.println("Checking: " + i + "   " + BitsInt.toBinary(a[i] >>> 32));
				long[] a = new long[K];
				System.arraycopy(aa, i*K, a, 0, a.length);
				assertTrue(cb.containsKD(a));
			}
			
			for (int i = 0; i < N; i++) {
				long[] a = new long[K];
				System.arraycopy(aa, i*K, a, 0, a.length);
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
			CritBit<Integer> cb = newCritBit(64, K); 
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
				assertNull(cb.insertKD(a, i));
				assertTrue(cb.containsKD(a));
			}
			
			assertEquals(N, cb.size());
	
			long[] qMin = new long[K];
			long[] qMax = new long[K];
			Iterator<long[]> it = null;
			
			//test normal queries
			for (int i = 0; i < 10; i++) {
				createQuery(R, qMin, qMax);
			
				ArrayList<long[]> result = executeQuery(aa, qMin, qMax);
				it = cb.queryKD(qMin, qMax);
				
				int nResult = 0;
				while (it.hasNext()) {
					long[] ra = it.next();
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
				long[] r2 = it.next();
				assertFalse(it.hasNext());
				assertTrue(isEqual(a, r2));
				//System.out.println("r2:" + r2[0]);
			}
			
			
			//assert none on invert
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.queryKD(qMax, qMin);
			assertFalse(it.hasNext());
			
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
	public void test64_True1D_queries_PositiveNumbers() {
		final int K = 1;
		for (int r = 0; r < 1000; r++) {
			Random R = new Random(r);
			int N = 1000;
			long[][] aa = new long[N][];
			CritBit<Integer> cb = newCritBit(64); 
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
				assertNull(cb.insert(a, i));
				assertTrue(cb.contains(a));
			}
			
			assertEquals(N, cb.size());
	
			long[] qMin = new long[K];
			long[] qMax = new long[K];
			Iterator<long[]> it = null;

			//test normal queries
			for (int i = 0; i < 10; i++) {
				//TODO this is bad, allow normal queries!
				createQueryAbs(R, qMin, qMax);
			
				ArrayList<long[]> result = executeQuery(aa, qMin, qMax);
				it = cb.query(qMin, qMax);
				
				int nResult = 0;
				while (it.hasNext()) {
					long[] ra = it.next();
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
				long[] r2 = it.next();
				assertFalse(it.hasNext());
				assertTrue(isEqual(a, r2));
			}
			
			
			//assert none on invert
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.query(qMax, qMin);
			assertFalse(it.hasNext());
			
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
	public void test64_1D_queries_1() {
		final int K = 1;
		long[][] aa = new long[][]{{1},{2},{3},{4},{5},{6}};
		CritBit<Integer> cb = newCritBit(64, K); 
		for (int i = 0; i < aa.length; i++) {
			long[] a = aa[i];
			if (cb.containsKD(a)) {
				//System.out.println("Duplicate: " + a[i]);
				i--;
				continue;
			}
			aa[i] = a;
			assertNull(cb.insertKD(a, i));
			//cb.printTree();
			assertTrue(cb.containsKD(a));
			//cb.printTree();
		}

		assertEquals(aa.length, cb.size());
		
		long[] qMin = new long[]{0};
		long[] qMax = new long[]{2};
		Iterator<long[]> it = null;

		//test normal queries
		for (int i = 0; i < 10; i++) {
			ArrayList<long[]> result = executeQuery(aa, qMin, qMax);
			it = cb.queryKD(qMin, qMax);

			int nResult = 0;
			while (it.hasNext()) {
				long[] ra = it.next();
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
			long[] r2 = it.next();
			assertFalse(it.hasNext());
			assertTrue(isEqual(a, r2));
		}


		//assert none on invert
		Arrays.fill(qMin, Long.MIN_VALUE); 
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.queryKD(qMax, qMin);
		assertFalse(it.hasNext());

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
	public void test64_True1D_queries_Single() {
		long[] a = new long[]{3};
		CritBit<Integer> cb = newCritBit(64);
		assertNull(cb.insert(a, 123));
		assertTrue(cb.contains(a));

		assertEquals(1, cb.size());
		
		long[] qMin = new long[]{3};
		long[] qMax = new long[]{3};
		Iterator<long[]> it = null;
		
		//test normal queries
		it = cb.query(qMin, qMax);
		long[] ra = it.next();
		assertEquals(a[0], ra[0]);
		assertFalse(it.hasNext());

		//assert all
		//assert point search
		//TODO query for negative...
		qMin = new long[]{2};
		qMax = new long[]{4};
		it = cb.query(qMin, qMax);
		ra = it.next();
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

		// delete stuff
		assertEquals(123, (int)cb.remove(a));

		// assert none on empty tree
		Arrays.fill(qMin, Long.MIN_VALUE);
		Arrays.fill(qMax, Long.MAX_VALUE);
		it = cb.query(qMin, qMax);
		assertFalse(it.hasNext());
	}

	@Test
	public void test64_1D_queries_Single() {
		final int K = 1;
		long[] a = new long[]{3};
		CritBit<Integer> cb = newCritBit(64, K);
		assertNull(cb.insertKD(a, 123));
		assertTrue(cb.containsKD(a));

		assertEquals(1, cb.size());
		
		long[] qMin = new long[]{3};
		long[] qMax = new long[]{3};
		Iterator<long[]> it = null;
		
		//test normal queries
		it = cb.queryKD(qMin, qMax);
		long[] ra = it.next();
		assertEquals(a[0], ra[0]);
		assertFalse(it.hasNext());

		//assert all
		//assert point search
		//TODO query for negative...
		qMin = new long[]{2};
		qMax = new long[]{4};
		it = cb.queryKD(qMin, qMax);
		ra = it.next();
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
			CritBit<Integer> cb = newCritBit(W, K); 
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
				assertNull(cb.insertKD(a, i));
				assertTrue(cb.containsKD(a));
			}
			
			assertEquals(N, cb.size());
	
			long[] qMin = new long[K];
			long[] qMax = new long[K];
			Iterator<long[]> it = null;
			
			//test normal queries
			for (int i = 0; i < 10; i++) {
				createQuery(R, qMin, qMax);
			
				ArrayList<long[]> result = executeQuery(aa, qMin, qMax);
				it = cb.queryKD(qMin, qMax);
				
				int nResult = 0;
				while (it.hasNext()) {
					long[] ra = it.next();
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
				long[] r2 = it.next();
				assertFalse(it.hasNext());
				assertTrue(isEqual(a, r2));
			}
			
			
			//assert none on invert
			Arrays.fill(qMin, Long.MIN_VALUE);
			Arrays.fill(qMax, Long.MAX_VALUE);
			it = cb.queryKD(qMax, qMin);
			assertFalse(it.hasNext());
			
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
			int N = 20000; 
			long[][] aa = new long[N][];
			CritBit<Integer> cb = newCritBit(64, K); 
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
				assertNull(cb.insertKD(a, i));
				assertEquals(i+1, cb.size());
			}
			
			assertEquals(N, cb.size());

			checkValues1D(cb, aa);
			
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
			CritBit<Integer> cb = newCritBit(64, K); 
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
				assertNull(cb.insertKD(a, i));
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
		CritBit<Integer> cb = newCritBit(64, K); 
		long[] A = new long[]{4603080768121727233L, 4602303061770585570L, 4604809596301821093L};
		long[] B = new long[]{4603082763292946186L, 4602305978608368320L, 4604812210005530572L};
		cb.insertKD(A, 11);
		assertTrue(cb.containsKD(A));
		//cb.printTree();
		cb.insertKD(B, 22);
		//cb.printTree();
		assertTrue(cb.containsKD(A));
		assertTrue(cb.containsKD(B));
	}
	
	@Test
	public void testInsert64KBug2() {
		final int K = 3;
		CritBit<Integer> cb = newCritBit(64, K); 
		long[] A = new long[]{4603080768121727233L, 4602303061770585570L, 4604809596301821093L};
		long[] B = new long[]{4603082763292946186L, 4602305978608368320L, 4604812210005530572L};
		cb.insertKD(A, 11);
		assertTrue(cb.containsKD(A));
		cb.insertKD(B, 22);
		assertTrue(cb.containsKD(A));
		assertTrue(cb.containsKD(B));
		cb.removeKD(B);
		assertTrue(cb.containsKD(A));
	}
	
	private void checkValues1D(CritBit<Integer> cb, int start, int count) {
		for (int i = start; i < start + count; i++) {
			Integer v = cb.get(new long[]{i});
			assertNotNull("i="+ i, v);
			assertEquals("i="+ i, i, (int)v);
		}
	}
	
	private void checkValues1D(CritBit<Integer> cb, long[][] aa) {
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

	private void createQueryAbs(Random R, long[] qMin, long[] qMax) {
		createQuery(R, qMin, qMax);
		for (int i = 0; i < qMin.length; i++) {
			qMin[i] = Math.abs(qMin[i]);
			qMax[i] = Math.abs(qMax[i]);
		}
	}
}
