/*
 * Copyright 2016-2023 Tilmann Zaeschke
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
 */package org.tinspin;

import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Test;
import org.tinspin.index.util.*;

import java.util.Arrays;
import java.util.Random;

public class MinMaxTest {

    static class Entry implements Comparable<Entry> {

        Entry(double d, int id) {
            this.d = d;
            this.id = id;
        }
        double d;
        int id;
        @Override
        public int compareTo(Entry o) {
            return Double.compare(d, o.d);
        }

        @Override
        public String toString() {
            // return String.format("(%d,%.2f)", id, d);
            return String.format("%.2f", d);
        }
    }

    private <T extends Comparable<T>> MinMaxHeapI<T> create() {
        // return new MinMaxHeapB(64);
        // return MinMaxHeapC2.create();
        return MinMaxHeapZ.create();
    }

    private Entry[] data(int n) {
        Random rnd = new Random(0);
        Entry[] data = new Entry[n];
        for (int i = 0; i < data.length; i++) {
            data[i] = new Entry(rnd.nextDouble(), i);
        }
        return data;
    }

    @Test
    public void testMin() {
        for (int i = 16; i < 35; i++) { // TODO
            testMin(i);
        }
        for (int i = 1; i < 100; i++) {
            testMin(i * 100);
        }
    }

    private void populate(MinMaxHeapI<Entry> heap, Entry[] data) {
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < data.length; i++) {
            heap.push(data[i]);
            assertFalse(heap.isEmpty());
            assertEquals(i+1, heap.size());
            min = Math.min(min, data[i].d);
            max = Math.max(max, data[i].d);
            assertNotNull(heap.peekMin());
            System.out.println("peek: i= " + i); // TODO
            assertEquals(min, heap.peekMin().d, 0.0);
            assertEquals(max, heap.peekMax().d, 0.0);
        }
    }

    private void testMin(int n) {
        Entry[] data = data(n);
        MinMaxHeapI<Entry> heap = create();
        populate(heap, data);

        Arrays.sort(data);

        for (int i = 0; i < data.length; i++) {
            System.out.println("pop i=" + i);
            ((MinMaxHeapZ)heap).print();
            assertFalse(heap.isEmpty());
            assertEquals(data.length - i, heap.size());
            assertEquals(data[i].d, heap.peekMin().d, 0.0);
            assertEquals(data[n-1].d, heap.peekMax().d, 0.0);
            ((MinMaxHeapZ)heap).checkConsistency();
            heap.popMin();
        }
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
    }

    @Test
    public void testMax() {
        for (int i = 1; i < 35; i++) {
            testMax(i);
        }
        for (int i = 1; i < 100; i++) {
            testMax(i * 100);
        }
    }

    private void testMax(int n) {
        Entry[] data = data(n);
        MinMaxHeapI<Entry> heap = create();
        populate(heap, data);

        Arrays.sort(data);

        for (int i = 0; i < data.length; i++) {
            assertFalse(heap.isEmpty());
            assertEquals(data.length - i, heap.size());
            assertEquals(data[0].d, heap.peekMin().d, 0.0);
            assertEquals(data[n-1-i].d, heap.peekMax().d, 0.0);
            heap.popMax();
        }
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
    }
}
