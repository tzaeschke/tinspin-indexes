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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.tinspin.index.critbit.CritBit64COW;
import org.tinspin.index.critbit.CritBit64.CBIterator;
import org.tinspin.index.critbit.CritBit64.Entry;
import org.tinspin.index.critbit.CritBit64.QueryIterator;
import org.tinspin.index.critbit.CritBit64.QueryIteratorMask;

/**
 * 
 * @author Bogdan Vancea, Tilmann Zäschke
 *
 */
public class TestCritBit64COW {

    private <T> CritBit64COW<T> newCritBit() {
        return CritBit64COW.create();
    }

    @Test
    public void testInsertDelete_OK() {
        //make sure the operations work
        CritBit64COW<Integer> tree = newCritBit();
        tree.iterator();
        int size = 100;

        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }
        for (int i = 0; i < size; i++) {
            assertTrue(tree.contains(i));
        }
        for (int i = 0; i < size; i++) {
            tree.remove(i);
            assertFalse(tree.contains(i));
        }
        for (int i = 0; i < size; i++) {
            assertFalse(tree.contains(i));
        }
    }

    @Test
    public void testIteratorConsistency_Insert_Empty() {
        //make an empty tree
        CritBit64COW<Integer> tree = newCritBit();

        //get an iteraotr
        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        //add some entries
        int size = 100;
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }

        //make sure entries are there
        for (int i = 0; i < size; i++) {
            assertTrue(tree.contains(i));
        }

        //iterator should see the tree as empty
        int nrEntries = 0;
        while (it.hasNext()) {
            it.next();
            nrEntries++;
        }

        assertEquals(0, nrEntries);
    }

    @Test
    public void testIteratorConsistency_Insert_OneEntry() {
        //make a tree with one entry
        CritBit64COW<Integer> tree = newCritBit();
        tree.put(-1, -1);

        //get an iterator
        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        //add some data
        int size = 100;
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }

        //make sure data is there
        for (int i = 0; i < size; i++) {
            assertTrue(tree.contains(i));
        }

        //iterator should only see the original entry
        int nrEntries = 0;
        while (it.hasNext()) {
            it.next();
            nrEntries++;
        }

        assertEquals(1, nrEntries);
    }

    @Test
    public void testIteratorConsistency_Insert_MultipleEntries() {
        //make a tree and put some data into it
        CritBit64COW<Integer> tree = newCritBit();

        Map<Long, Integer> initial = new LinkedHashMap<>();
        int size = 100;
        for (int i = -size - 1; i < 0; i++) {
            tree.put(i, i);
            initial.put((long) i, i);
        }

        //get an iterator
        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        //add some more data
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }

        //make sure all data is there
        for (int i = -size - 1; i < size; i++) {
            assertTrue(tree.contains(i));
        }

        //the old iterator should only see the old data
        Map<Long, Integer> after = getEntries(it);
        assertEquals(initial, after);
    }

    @Test
    public void testIteratorConsistency_Delete_1Entry() {
        //make a tree with one entry
        CritBit64COW<Integer> tree = newCritBit();
        tree.put(1, 1);

        //get an iterator
        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        //remove the entry
        tree.remove(1);

        //iterator should sill see the entry there
        CritBit64COW.Entry<Integer> e = it.nextEntry();
        assertEquals(1L, e.key());
        assertEquals(new Integer(1), e.value());
        assertFalse(it.hasNext());
    }

    @Test
    public void testIteratorConsistency_Delete_MultipleEntries() {
        //make a tree and add some entries to it
        CritBit64COW<Integer> tree = newCritBit();

        Map<Long, Integer> all = new LinkedHashMap<>();
        Map<Long, Integer> remaining = new LinkedHashMap<>();
        int size = 100;
        int key;
        for (int i = 0; i < size; i++) {
            key = (2 * i);

            remaining.put((long) key, key);
            all.put((long) key, key);
            tree.put(key, key);

            key = (2 * i) + 1;
            all.put((long) key, key);
            tree.put(key, key);
        }

        //get an iterator
        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        //remove some of the entries
        for (int i = 0; i < size; i++) {
            tree.remove(2 * i + 1);
        }

        //make sure the old iterator doesn't see changes
        Map<Long, Integer> after = getEntries(it);
        assertEquals(after, all);

        //make sure a new iterator would see the changes
        after = getEntries(tree);
        assertEquals(after, remaining);
    }

    @Test
    public void testCopy_Insert() {
        //create a tree and make a copy of it
        CritBit64COW<Integer> tree = newCritBit();
        CritBit64COW<Integer> copy = tree.copy();

        //fill original with entries
        Map<Long, Integer> entries = new LinkedHashMap<>();
        int size = 100;
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
            entries.put((long) i, i);
        }

        //make sure copy is empty
        assertTrue(getEntries(copy).isEmpty());

        //make sure all entries are in the original tree
        assertEquals(entries, getEntries(tree));
    }

    @Test
    public void testCopy_Delete() {
        //create a tree and fill it with entries
        CritBit64COW<Integer> tree = newCritBit();
        Map<Long, Integer> entries = new LinkedHashMap<>();
        int size = 100;
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
            entries.put((long) i, i);
        }

        //make a copy
        CritBit64COW<Integer> copy = tree.copy();

        //remove all entries from original tree
        for (int i = 0; i < size; i++) {
            tree.remove(i);
        }

        //check the copy has all the entries
        assertEquals(entries, getEntries(copy));

        //check that original is empty
        assertTrue(getEntries(tree).isEmpty());
    }

    private Map<Long, Integer> getEntries(CritBit64COW<Integer> tree) {
        return getEntries(tree.iterator());
    }

    private Map<Long, Integer> getEntries(CritBit64COW.CBIterator<Integer> it) {
        Map<Long, Integer> entries = new LinkedHashMap<>();
        CritBit64COW.Entry<Integer> e;
        while (it.hasNext()) {
            e = it.nextEntry();
            entries.put(e.key(), e.value());
        }
        return entries;
    }
    
    private static class Writer implements Runnable {
    	private final int start;
    	private final int n;
    	private final CritBit64COW<Integer> tree;

    	Writer(CritBit64COW<Integer> tree, int start, int n) {
    		this.tree = tree;
    		this.start = start;
    		this.n = n;
    	}    	

    	@Override
    	public void run() {
			for (int i = start; i < start+n; i++) {
				tree.put(i, i);
			}
    	}
    }
    
    @Test
    public void testMultiThreading() throws InterruptedException {
        CritBit64COW<Integer> tree = newCritBit();

        int N_W = 10; //threads
        int N = 1*1000*1000;
        
        ExecutorService es = Executors.newFixedThreadPool(N_W);
    	int batchSize = N/N_W;
        for (int i = 0; i < N_W; i++) {
        	es.execute( new Writer(tree, i*batchSize, batchSize) );
		}
        es.shutdown();
        es.awaitTermination(10, TimeUnit.SECONDS);
		
        if (!es.isTerminated()) {
        	fail();
        }
		
        for (int i = 0; i < N; i++) {
            assertTrue(tree.contains(i));
        }
        for (int i = 0; i < N; i++) {
            assertEquals(i, (int)tree.remove(i));
        }
        for (int i = 0; i < N; i++) {
            assertFalse(tree.contains(i));
        }
    }


	@Test
	public void testIteratorWithNullValues() {
		int N = 10000;
		Random R = new Random(0);

		CritBit64COW<?> cb = newCritBit();
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
	public void testIssue0004() {
		CritBit64COW<Long> cb = newCritBit();
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
		CritBit64COW<Long> cb = newCritBit();
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

	@Test public void testEmptyQWM() {
		CritBit64COW<long[]> cb = CritBit64COW.create();
		cb.put(64, new long[]{0, 0, 0, 0, 0, 0, 0});
		cb.put(65, new long[]{5, 2, 2, 2, 2, 2, 3});

		CritBit64COW.QueryIteratorMask<long[]> it2 = cb.queryWithMask(0, 63);
		while (it2.hasNext()) {
			CritBit64COW.Entry<long[]> e = it2.nextEntry();
			assertTrue("key=" + e.key(), e.key() >= 64);
			assertNotNull(e.value());
		}
		CritBit64COW.QueryIteratorMask<long[]> it = cb.queryWithMask(0, 63);
		while (it.hasNext()) {
			long[] x = it.next();
			assertNotNull(x);
		}
	}
	
	@Test public void testEmptyQuery() {
		CritBit64COW<long[]> cb = CritBit64COW.create();
		cb.put(64, new long[]{0, 0, 0, 0, 0, 0, 0});
		cb.put(65, new long[]{5, 2, 2, 2, 2, 2, 3});

		CritBit64COW.QueryIterator<long[]> it2 = cb.query(0, 63);
		while (it2.hasNext()) {
			CritBit64COW.Entry<long[]> e = it2.nextEntry();
			assertTrue("key=" + e.key(), e.key() >= 64);
			assertNotNull(e.value());
		}
		CritBit64COW.QueryIterator<long[]> it = cb.query(0, 63);
		while (it.hasNext()) {
			long[] x = it.next();
			assertNotNull(x);
		}
	}
	
	@Test
	public void testPrint() {
		int N = 100;
		Random R = new Random(0);

		CritBit64COW<?> cb = newCritBit();
		long[] data = new long[N];
		for (int i = 0; i < N; i++) {
			long l = R.nextInt(123456789); 
			data[i] = l;
			cb.put(l, null);
		}

		assertTrue(cb.checkTree());
		
		String s = cb.toString();
		for (long l : data) {
			//This works for now, because we store all bits, not just the trailing postfix
			assertTrue(s.contains(BitTools.toBinary(l, 64)));
		}
	}
	
}
