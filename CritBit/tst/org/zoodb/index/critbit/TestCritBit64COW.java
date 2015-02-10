package org.zoodb.index.critbit;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestCritBit64COW {

    private CritBit64COW<Integer> newCritBit() {
        return CritBit64COW.create();
    }

    @Test
    public void testInsertDelete_OK() {
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
        CritBit64COW<Integer> tree = newCritBit();
        CritBit64COW.CBIterator<Integer> it = tree.iterator();
        int size = 100;
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }
        for (int i = 0; i < size; i++) {
            assertTrue(tree.contains(i));
        }

        int nrEntries = 0;
        while (it.hasNext()) {
            it.next();
            nrEntries++;
        }

        assertEquals(0, nrEntries);
    }

    @Test
    public void testIteratorConsistency_Insert_OneEntry() {
        CritBit64COW<Integer> tree = newCritBit();
        tree.put(-1, -1);
        CritBit64COW.CBIterator<Integer> it = tree.iterator();
        int size = 100;
        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }
        for (int i = 0; i < size; i++) {
            assertTrue(tree.contains(i));
        }

        int nrEntries = 0;
        while (it.hasNext()) {
            it.next();
            nrEntries++;
        }

        assertEquals(1, nrEntries);
    }

    @Test
    public void testIteratorConsistency_Insert_MultipleEntries() {
        CritBit64COW<Integer> tree = newCritBit();

        Map<Long, Integer> initial = new LinkedHashMap<>();
        int size = 100;
        for (int i = -size - 1; i < 0; i++) {
            tree.put(i, i);
            initial.put((long) i, i);
        }

        CritBit64COW.CBIterator<Integer> it = tree.iterator();

        for (int i = 0; i < size; i++) {
            tree.put(i, i);
        }
        for (int i = 0; i < size; i++) {
            assertTrue(tree.contains(i));
        }

        CritBit64COW.Entry<Integer> e;
        Map<Long, Integer> after = new LinkedHashMap<>();
        while (it.hasNext()) {
            e = it.nextEntry();
            after.put(e.key(), e.value());
        }
        assertEquals(initial, after);
    }

    @Test
    public void testIteratorConsistency_Delete_1Entry() {
        CritBit64COW<Integer> tree = newCritBit();
        tree.put(1, 1);
        CritBit64COW.CBIterator<Integer> it = tree.iterator();
        tree.remove(1);

        CritBit64COW.Entry<Integer> e = it.nextEntry();
        assertEquals(1L, e.key());
        assertEquals(new Integer(1), e.value());
        assertFalse(it.hasNext());
    }

    @Test
    public void testIteratorConsistency_Delete_MultipleEntries() {
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

        CritBit64COW.CBIterator<Integer> it = tree.iterator();
        for (int i = 0; i < size; i++) {
            tree.remove(2 * i + 1);
        }

        CritBit64COW.Entry<Integer> e;
        Map<Long, Integer> after = new LinkedHashMap<>();
        while (it.hasNext()) {
            e = it.nextEntry();
            after.put(e.key(), e.value());
        }
        assertEquals(after, all);

        it = tree.iterator();
        after.clear();
        while (it.hasNext()) {
            e = it.nextEntry();
            after.put(e.key(), e.value());
        }
        assertEquals(after, remaining);
    }
}
