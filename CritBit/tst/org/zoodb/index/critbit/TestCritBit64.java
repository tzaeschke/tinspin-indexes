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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Random;

import org.junit.Test;
import org.zoodb.index.critbit.CritBit64.CBIterator;
import org.zoodb.index.critbit.CritBit64.Entry;
import org.zoodb.index.critbit.CritBit64.QueryIterator;
import org.zoodb.index.critbit.CritBit64.QueryIteratorMask;

/**
 * 
 * @author Tilmann Zaeschke
 */
public class TestCritBit64 {

	private <T> CritBit64<T> newCritBit() {
		return CritBit64.create();
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
		CritBit64<Integer> cb = newCritBit();
		for (long l: a) {
			//cb.printTree();
			//System.out.println("Inserting: " + l + " --- " + Bits.toBinary(l, 64));
			assertNull(cb.put(l, 1));
			assertNotNull(cb.put(l, 2));
		}
	}
	
	@Test
	public void testInsertIntRBug4() {
		randomInsertCheck(23, 0, 8);
	}
	
	@Test
	public void testInsertIntRBug5() {
		randomInsertCheck(6, 95109, 8);
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
		int shift = 64-DEPTH;
		Random R = new Random(SEED);
		long[] a = new long[N];
		CritBit64<Integer> cb = newCritBit(); 
		for (int i = 0; i < N; i++) {
			iFail = i;
			a[i] = R.nextLong() >> shift;
			//System.out.println((int)(a[i]>>>32) + ",");
			//System.out.println("Inserting: " + a[i] + " / " + BitTools.toBinary(a[i], 32));
			//System.out.println("i1=" + i + " / " + a[i]);
			if (cb.contains(a[i])) {
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
			assertNull("i=" + i + " v=" + a[i], cb.put(a[i], 12345+i));
			//cb.printTree();
			assertEquals("i=" + i, 12345+i, (int)cb.put(a[i], i));
			//cb.printTree();
			assertEquals(i+1, cb.size());
			assertTrue(cb.contains(a[i]));
			assertEquals("i=" + i, i, (int)cb.get(a[i]));
		}
		
		assertEquals(N, cb.size());

		for (int i = 0; i < N; i++) {
			//System.out.println("Checking: " + i + "   " + BitTools.toBinary(a[i], 32));
			assertTrue(cb.contains(a[i]));
			if (i != (int)cb.get(a[i])) {
				cb.printTree();
			}
			assertEquals("i=" + i, i, (int)cb.get(a[i]));
		}
		
		for (int i = 0; i < N; i++) {
			//System.out.println("Removing: " + a[i] + " / " + BitTools.toBinary(a[i], 32));
			//cb.printTree();
			assertTrue("i="+ i, cb.contains(a[i]));
			assertEquals(i, (int)cb.remove(a[i]));
			assertEquals(null, cb.remove(a[i]));
			assertFalse(cb.contains(a[i]));
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
		CritBit64<Integer> cb = newCritBit(); 
		for (int i = 0; i < a.length; i++) {
			a[i] = a[i] << 32;
			assertFalse(cb.contains(a[i]));
			assertNull(cb.put(a[i], i));
			//cb.printTree();
			assertEquals(i, (int)cb.put(a[i], i));
			//cb.printTree();
			assertEquals(i+1, cb.size());
			assertTrue(cb.contains(a[i]));
		}

		assertEquals(a.length, cb.size());

		for (int i = 0; i < a.length; i++) {
			assertTrue(cb.contains(a[i]));
		}
	}
	
	@Test
	public void testBugInsert2() {
		long[] a = new long[]{ 
				-65105105,
				-73789608,
				-518907128};
		CritBit64<Integer> cb = newCritBit(); 
		for (int i = 0; i < a.length; i++) {
			a[i] = a[i] << 32;
			//System.out.println("Inserting: " + a[i] + " / " + BitsInt.toBinary(a[i] >>> 32));
			assertFalse(cb.contains(a[i]));
			assertNull(cb.put(a[i], i));
			//cb.printTree();
			assertEquals(i, (int)cb.put(a[i], i));
			//cb.printTree();
			assertEquals(i+1, cb.size());
			assertTrue(cb.contains(a[i]));
		}

		assertEquals(a.length, cb.size());

		for (int i = 0; i < a.length; i++) {
			//System.out.println("Checking: " + i + "   " + BitsInt.toBinary(a[i] >>> 32));
			assertTrue(cb.contains(a[i]));
		}
	}
	
	@Test
	public void test64() {
		for (int r = 0; r < 1000; r++) {
			Random R = new Random(r);
			int N = 1000;
			long[] a = new long[N];
			CritBit64<Integer> cb = newCritBit(); 
			for (int i = 0; i < N; i++) {
				a[i] = R.nextLong();
				//System.out.println(a[i]>>>32 + ",");
				//System.out.println("Inserting: " + a[i] + " / " + BitTools.toBinary(a[i], 64));
				if (cb.contains(a[i])) {
					for (int j = 0; j < i; j++) {
						if (a[j] == a[i]) {
							//System.out.println("Duplicate: " + a[i]);
							i--;
							continue;
						}
					}
					fail("r=" + r + "  i= " + i);
				}
				assertNull(cb.put(a[i], i+12345));
				//cb.printTree();
				assertEquals(i+12345, (int)cb.put(a[i], i));
				//cb.printTree();
				assertEquals(i+1, cb.size());
				assertTrue(cb.contains(a[i]));
			}
			
			assertEquals(N, cb.size());
	
			for (int i = 0; i < N; i++) {
				//System.out.println("Checking: " + i + "   " + BitsInt.toBinary(a[i] >>> 32));
				assertTrue(cb.contains(a[i]));
			}

			for (int i = 0; i < N; i++) {
				assertTrue(cb.contains(a[i]));
				assertEquals(i, (int)cb.remove(a[i]));
				assertNull(cb.remove(a[i]));
				assertFalse(cb.contains(a[i]));
				assertEquals(N-i-1, cb.size());
			}
			
			assertEquals(0, cb.size());
		}
	}

	@Test
	public void testQueries64() {
		for (int r = 0; r < 1000; r++) {
			Random R = new Random(r);
			int N = 10000;
			long[] a = new long[N];
			CritBit64<Integer> cb = newCritBit(); 
			for (int i = 0; i < N; i++) {
				a[i] = R.nextLong();
				//System.out.println(a[i]>>>32 + ",");
				//System.out.println("Inserting: " + a[i] + " / " + BitTools.toBinary(a[i], 64));
				if (cb.contains(a[i])) {
					i--;
					continue;
				}
				assertNull(cb.put(a[i], i));
			}
			
			assertEquals(N, cb.size());
	
			//test iterators
			long min = Long.MIN_VALUE;
			long max = Long.MAX_VALUE;
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
				long key = it.nextKey();
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
				long key = e.key();
				assertEquals(sortedResults.get(n), cb.get(key));
				assertEquals(sortedResults.get(n), e.value());
				n++;
			}
			assertEquals(N, n);
		}
	}

	@Test
	public void testIterators64() {
		for (int r = 0; r < 1000; r++) {
			Random R = new Random(r);
			int N = 10000;
			long[] a = new long[N];
			CritBit64<Integer> cb = newCritBit(); 
			for (int i = 0; i < N; i++) {
				a[i] = R.nextLong();
				//System.out.println(a[i]>>>32 + ",");
				//System.out.println("Inserting: " + a[i] + " / " + BitTools.toBinary(a[i], 64));
				if (cb.contains(a[i])) {
					i--;
					continue;
				}
				assertNull(cb.put(a[i], i));
			}
			
			assertEquals(N, cb.size());
	
			//test iterators
			//value iteration
			CBIterator<Integer> it = cb.iterator();
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
			it = cb.iterator();
			n = 0;
			while (it.hasNext()) {
				long key = it.nextKey();
				//assure same order
				assertEquals(sortedResults.get(n), cb.get(key));
				n++;
			}
			assertEquals(N, n);
			//entry iteration
			it = cb.iterator();
			n = 0;
			while (it.hasNext()) {
				Entry<Integer> e = it.nextEntry();
				long key = e.key();
				assertEquals(sortedResults.get(n), cb.get(key));
				assertEquals(sortedResults.get(n), e.value());
				n++;
			}
			assertEquals(N, n);
		}
	}

	@Test
	public void testIteratorWithNullValues() {
		int N = 10000;
		Random R = new Random(0);

		CritBit64<?> cb = newCritBit();
		long[] data = new long[N];
		for (int i = 0; i < N; i++) {
			long l = R.nextInt(123456789); 
			data[i] = l;
			cb.put(l, null);
		}
		
		//test extent
		CBIterator<?> it = cb.iterator();
		int n = 0;
		while (it.hasNext()) {
			Entry<?> e = it.nextEntry();
			assertNull(e.value());
			n++;
		}
		assertEquals(N, n);
		
		//test query
		long min = Long.MIN_VALUE;
		long max = Long.MAX_VALUE;
		QueryIterator<?> qi = cb.query(min, max);
		n = 0;
		while (qi.hasNext()) {
			Entry<?> e = qi.nextEntry();
			assertNull(e.value());
			n++;
		}
		assertEquals(N, n);
		
		//test query
		QueryIteratorMask<?> qim = cb.queryWithMask(0, Long.MAX_VALUE);
		n = 0;
		while (qim.hasNext()) {
			Entry<?> e = qim.nextEntry();
			assertNull(e.value());
			n++;
		}
		assertEquals(N, n);
	}
	
	@Test
	public void test64Bug1() {
		Random R = new Random(0);
		int N = 6;
		long[] a = new long[N];
		CritBit64<Integer> cb = newCritBit(); 
		for (int i = 0; i < N; i++) {
			a[i] = R.nextLong();
			//System.out.println(a[i]>>>32 + ",");
			//System.out.println("Inserting: " + a[i] + " / " + BitTools.toBinary(a[i], 64));
			if (cb.contains(a[i])) {
				for (int j = 0; j < i; j++) {
					if (a[j] == a[i]) {
						//System.out.println("Duplicate: " + a[i]);
						i--;
						continue;
					}
				}
				fail("i= " + i);
			}
			assertNull(cb.put(a[i], i));
		}

		assertEquals(N, cb.size());

		for (int i = 0; i < N; i++) {
			//System.out.println("Removing: " + a[i] + " / " + BitTools.toBinary(a[i], 64));
			//cb.printTree();
			assertTrue("i="+i, cb.contains(a[i]));
			assertEquals("i="+ i, i, (int)cb.remove(a[i]));
			//cb.printTree();
			assertTrue("i="+i, cb.checkTree());
			assertNull(cb.remove(a[i]));
			assertTrue("i="+i, cb.checkTree());
		}

		assertEquals(0, cb.size());
	}

	@Test
	public void test64Bug2() {
		Random R = new Random(1);
		int N = 7;
		long[] a = new long[N];
		CritBit64<Integer> cb = newCritBit(); 
		for (int i = 0; i < N; i++) {
			a[i] = R.nextLong();
			//System.out.println(a[i]>>>32 + ",");
			//System.out.println("Inserting: " + a[i] + " / " + BitTools.toBinary(a[i], 64));
			if (cb.contains(a[i])) {
				for (int j = 0; j < i; j++) {
					if (a[j] == a[i]) {
						//System.out.println("Duplicate: " + a[i]);
						i--;
						continue;
					}
				}
				fail("i= " + i);
			}
			assertNull(cb.put(a[i], i));
			assertEquals(i+1, cb.size());
			assertTrue(cb.checkTree());
		}

		assertEquals(N, cb.size());

		for (int i = 0; i < N; i++) {
			//cb.printTree();
			assertTrue("i=" + i, cb.checkTree());
			assertTrue("i=" + i, cb.contains(a[i]));
			assertEquals(i, (int)cb.remove(a[i]));
			assertNull(cb.remove(a[i]));
			assertFalse(cb.contains(a[i]));
			assertEquals(N-i-1, cb.size());
		}

		assertEquals(0, cb.size());
	}

	@Test
	public void test64_True1D_queries_PositiveNumbers() {
		for (int r = 0; r < 1000; r++) {
			Random R = new Random(r);
			int N = 1000;
			long[] aa = new long[N];
			CritBit64<Integer> cb = newCritBit(); 
			for (int i = 0; i < N; i++) {
				long a = Math.abs(R.nextLong());
				
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
	
			long qMin = 0;
			long qMax = 0;
			QueryIterator<Integer> it = null;

			//test normal queries
			for (int i = 0; i < 10; i++) {
				qMin = R.nextLong();
				qMax = R.nextLong();
				if (qMin > qMax) {
					long d = qMin;
					qMin = qMax;
					qMax = d;
				}
			
				ArrayList<Long> result = executeQuery(aa, qMin, qMax);
				it = cb.query(qMin, qMax);
				
				int nResult = 0;
				while (it.hasNext()) {
					long ra = it.nextKey();
					nResult++;
					assertContains(aa, ra);
				}
					
				assertEquals("r=" + r + " i=" + i, result.size(), nResult);
			}			

			//assert all
			int n = 0;
			qMin = Long.MIN_VALUE;
			qMax = Long.MAX_VALUE;
			it = cb.query(qMin, qMax);
			while (it.hasNext()) {
				it.next();
				//System.out.println("r1:" + it.next()[0]);
				n++;
			}
			assertEquals(N, n);

			//assert point search
			for (long a: aa) {
				it = cb.query(a, a);
				assertTrue(it.hasNext());
				long r2 = it.nextKey();
				assertFalse(it.hasNext());
				assertEquals(a, r2);
			}
			
			
			//assert none on invert
			qMin = Long.MIN_VALUE;
			qMax = Long.MAX_VALUE;
			it = cb.query(qMax, qMin);
			assertFalse(it.hasNext());
			
			// delete stuff
			for (int i = 0; i < N; i++) {
				assertEquals(i, (int)cb.remove(aa[i]));
			}
			
			// assert none on empty tree
			qMin = Long.MIN_VALUE;
			qMax = Long.MAX_VALUE;
			it = cb.query(qMin, qMax);
			assertFalse(it.hasNext());
		}
	}


	@Test
	public void test64_True1D_queries_Single() {
		long a = 3;
		CritBit64<Integer> cb = newCritBit();
		assertNull(cb.put(a, 123));
		assertTrue(cb.contains(a));

		assertEquals(1, cb.size());
		
		long qMin = 3;
		long qMax = 3;
		QueryIterator<Integer> it = null;
		
		//test normal queries
		it = cb.query(qMin, qMax);
		long ra = it.nextKey();
		assertEquals(a, ra);
		assertFalse(it.hasNext());

		//assert all
		//assert point search
		qMin = -2;
		qMax = 4;
		it = cb.query(qMin, qMax);
		ra = it.nextKey();
		assertEquals(a, ra);
		assertFalse(it.hasNext());


		//assert none on invert
		qMin = Long.MIN_VALUE; 
		qMax = Long.MAX_VALUE;
		it = cb.query(qMax, qMin);
		assertFalse(it.hasNext());

		//assert none on too low
		qMin = Long.MIN_VALUE;
		qMax = 2;
		it = cb.query(qMin, qMax);
		assertFalse(it.hasNext());

		//assert none on too high
		qMin = 4; 
		qMax = Long.MAX_VALUE;
		it = cb.query(qMin, qMax);
		assertFalse(it.hasNext());

		// delete stuff
		assertEquals(123, (int)cb.remove(a));

		// assert none on empty tree
		qMin = Long.MIN_VALUE;
		qMax = Long.MAX_VALUE;
		it = cb.query(qMin, qMax);
		assertFalse(it.hasNext());
	}

	@Test
	public void testIssue0004() {
		CritBit64<Long> cb = newCritBit();
		assertNull(cb.put(5L, 5L));
		assertNull(cb.put(8L, 8L));
		assertTrue(cb.contains(5L));
		assertTrue(cb.contains(8L));
		
		QueryIterator<Long> it = cb.query(5L, 5L);
		assertTrue(it.hasNext());
		assertEquals(5L, it.nextKey());
		assertFalse(it.hasNext());
		
		it = cb.query(8L, 8L);
		assertTrue(it.hasNext());
		assertEquals(8L, it.nextKey());
		assertFalse(it.hasNext());
	}

	@Test
	public void testIssue0004_Mask() {
		CritBit64<Long> cb = newCritBit();
		assertNull(cb.put(5L, 5L));
		assertNull(cb.put(8L, 8L));
		assertTrue(cb.contains(5L));
		assertTrue(cb.contains(8L));
		
		QueryIteratorMask<Long> it = cb.queryWithMask(5L, 5L);
		assertTrue(it.hasNext());
		assertEquals(5L, it.nextKey());
		assertFalse(it.hasNext());
		
		it = cb.queryWithMask(8L, 8L);
		assertTrue(it.hasNext());
		assertEquals(8L, it.nextKey());
		assertFalse(it.hasNext());
	}
	
	private void assertContains(long[] aa, long r) {
		for (long a: aa) {
			if (a == r) {
				return;
			}
		}
		fail();
	}

	private ArrayList<Long> executeQuery(long[] aa, long qMin, long qMax) {
		ArrayList<Long> r = new ArrayList<Long>();
		for (long a: aa) {
			if (a >= qMin && a <= qMax) {
				r.add(a);
			}
		}
		return r;
	}
}
