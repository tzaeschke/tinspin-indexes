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
package org.tinspin.index.test;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.tinspin.index.*;
import org.tinspin.index.array.RectArray;
import org.tinspin.index.qthypercube.QuadTreeRKD;
import org.tinspin.index.qtplain.QuadTreeRKD0;
import org.tinspin.index.rtree.RTree;
import org.tinspin.index.util.MutableInt;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.tinspin.index.Index.*;
import static org.tinspin.index.test.util.TestInstances.IDX;

@RunWith(Parameterized.class)
public class BoxMultimapTest extends AbstractWrapperTest {

    private static final int N_DUP = 4;
    private static final int BOUND = 10000;
    private static final int BOX_LEN_MAX = 10;
    private static final int LARGE = 10_000;
    private static final int MEDIUM = 5_000;

    private final IDX candidate;

    public BoxMultimapTest(IDX candCls) {
        this.candidate = candCls;
    }

    @Parameterized.Parameters
    public static Iterable<Object[]> candidates() {
        ArrayList<Object[]> l = new ArrayList<>();
        l.add(new Object[]{IDX.ARRAY});
        l.add(new Object[]{IDX.QUAD_PLAIN});
        l.add(new Object[]{IDX.QUAD_HC});
        l.add(new Object[]{IDX.RSTAR});
        l.add(new Object[]{IDX.STR});
        return l;
    }

    private ArrayList<Entry> createInt(long seed, int n, int dim) {
        ArrayList<Entry> data = new ArrayList<>(n);
        Random R = new Random(seed);
        for (int i = 0; i < n; i += N_DUP) {
            Entry e = new Entry(dim, i);
            data.add(e);
            for (int d = 0; d < dim; d++) {
                e.p1[d] = R.nextInt(BOUND);
                e.p2[d] = e.p1[d] + R.nextInt(BOX_LEN_MAX);
            }
            for (int i2 = 1; i2 < N_DUP; ++i2) {
                Entry e2 = new Entry(dim, i + i2);
                data.add(e2);
                System.arraycopy(e.p1, 0, e2.p1, 0, e.p1.length);
                System.arraycopy(e.p2, 0, e2.p2, 0, e.p2.length);
            }
        }
        assertEquals(n, data.size());
        return data;
    }

    @Test
    public void smokeTestDupl() {
        double[][] points = {{2, 3}, {2, 3}, {2, 3}, {2, 3}};
        MutableInt i = new MutableInt();
        smokeTest(Arrays.stream(points).flatMap(p -> Stream.of(new Entry(p, p, i.inc().get()))).collect(Collectors.toList()));
    }

    @Test
    public void smokeTest2D_0() {
        smokeTest(createInt(0, 20, 2));
    }

    @Test
    public void smokeTest2D_1() {
        smokeTest(createInt(1, 20, 2));
    }

    /**
     * Tests handling of all points being on a line, i.e. correct handling of <=, etc.
     */
    @Test
    public void smokeTest2D_Line() {
        for (int r = 0; r < 10; ++r) {
            List<Entry> data = createInt(0, 1000, 3);
            int nAll = 0;
            for (Entry e : data) {
                int n = nAll++ / N_DUP;
                e.p1[0] = n % 3;
                e.p1[1] = n++;
                e.p1[2] = n % 5;
                e.p2[0] = e.p1[0] + BOX_LEN_MAX;
                e.p2[1] = e.p1[1] + BOX_LEN_MAX;
                e.p2[2] = e.p1[2] + BOX_LEN_MAX;
            }
//            Collections.shuffle(data, new Random(r));
            smokeTest(data);
        }
    }

    @Test
    public void smokeTest5D() {
        smokeTest(createInt(0, 20, 5));
    }

    @Test
    public void smokeTest1D_Large() {
        smokeTest(createInt(0, LARGE, 1));
    }

    @Test
    public void smokeTest3D_Large() {
        smokeTest(createInt(0, LARGE, 3));
    }

    @Test
    public void smokeTest10D_Large() {
        smokeTest(createInt(0, MEDIUM, 10));
    }

    private void smokeTest(List<Entry> data) {
        int dim = data.get(0).p1.length;
        BoxMultimap<Entry> tree = createTree(data.size(), dim);

        for (Entry e : data) {
            tree.insert(e.p1, e.p2, e);
        }
        // System.out.println(tree.toStringTree());
        for (Entry e : data) {
            BoxIterator<Entry> it = tree.queryExactBox(e.p1, e.p2);
            assertTrue("query(point) failed: " + e, it.hasNext());
            BoxEntry<Entry> next = it.next();
            assertArrayEquals(e.p1, next.value().p1, 0.0000);
            assertArrayEquals(e.p2, next.value().p2, 0.0000);
        }

        for (Entry e : data) {
            // System.out.println("kNN query: " + e);
            BoxIteratorKnn<Entry> iter = tree.queryKnn(e.p1, N_DUP);
            assertTrue("kNNquery() failed: " + e, iter.hasNext());
            int nFound = 0;
            while (iter.hasNext()) {
                BoxEntryKnn<Entry> eDist = iter.next();
                nFound += eDist.dist() == 0 ? 1 : 0;
            }
            assertEquals(N_DUP, nFound);
        }

        if (candidate != IDX.COVER && candidate != IDX.KDTREE && candidate != IDX.QUAD_PLAIN
                && candidate != IDX.QUAD_HC && candidate != IDX.QUAD_HC2 && candidate != IDX.ARRAY) {
            int nExtent = 0;
            BoxIterator<Entry> extent = tree.iterator();
            while (extent.hasNext()) {
                extent.next();
                nExtent++;
            }
            assertEquals(data.size(), nExtent);
        }

        for (Entry e : data) {
            // System.out.println("query: " + Arrays.toString(e.p));
            BoxIterator<Entry> iter = tree.queryExactBox(e.p1, e.p2);
            assertTrue("query() failed: " + e, iter.hasNext());
            for (int i = 0; i < N_DUP; ++i) {
                // System.out.println("  found: " + i + " " + e);
                assertTrue("Expected next for i=" + i + " / " + e, iter.hasNext());
                Entry answer = iter.next().value();
                assertArrayEquals("Expected " + e + " but got " + answer, answer.p1, e.p1, 0.0001);
                assertArrayEquals("Expected " + e + " but got " + answer, answer.p2, e.p2, 0.0001);
            }
        }

        for (Entry e : data) {
            BoxIterator<Entry> it = tree.queryExactBox(e.p1, e.p2);
            assertTrue("queryExact() failed: " + e, it.hasNext());
            BoxEntry<Entry> e2 = it.next();
            assertArrayEquals(e.p1, e2.value().p1, 0);
            assertArrayEquals(e.p2, e2.value().p2, 0);
            assertTrue(tree.remove(e.p1, e.p2, e));
        }
    }

    @Test
    public void testUpdate() {
        Random r = new Random(42);
        int dim = 3;
        ArrayList<Entry> data = createInt(0, 1000, 3);
        BoxMultimap<Entry> tree = createTree(data.size(), dim);

        for (Entry e : data) {
            tree.insert(e.p1, e.p2, e);
        }

        for (int i = 0; i < data.size(); ++i) {
            Entry e = data.get(i);
            double[] p1Old = e.p1.clone();
            double[] p2Old = e.p2.clone();
            double[] p1New = e.p1.clone();
            double[] p2New = e.p2.clone();
            for (int d = 0; d < dim; d++) {
                p1New[d] = r.nextInt(BOUND);
                p2New[d] = p1New[d] + r.nextInt(BOX_LEN_MAX);
            }
            assertTrue(tree.update(p1Old, p2Old, p1New, p2New, e));
            // Update entry
            System.arraycopy(p1New, 0, e.p1, 0, dim);
            System.arraycopy(p2New, 0, e.p2, 0, dim);
            assertFalse(containsExact(tree, p1Old, p2Old, e.id));
            assertTrue(containsExact(tree, e.p1, e.p2, e.id));
        }

        for (int i = 0; i < data.size(); ++i) {
            Entry e = data.get(i);
            assertTrue(containsExact(tree, e.p1, e.p2, e.id));
        }
    }

    private boolean containsExact(BoxMultimap<Entry> tree, double[] p1, double[] p2, int id) {
        BoxIterator<Entry> it = tree.queryExactBox(p1, p2);
        while (it.hasNext()) {
            if (it.next().value().id == id) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testRemove() {
        Random r = new Random(0);
        int dim = 3;
        ArrayList<Entry> data = createInt(0, 1000, 3);
        BoxMultimap<Entry> tree = createTree(data.size(), dim);

        Collections.shuffle(data, r);

        for (Entry e : data) {
            tree.insert(e.p1, e.p2, e);
        }

        // remove 1st half
        for (int i = 0; i < data.size() / 2; ++i) {
            Entry e = data.get(i);
            assertTrue(tree.remove(e.p1, e.p2, e));
            assertFalse(containsExact(tree, e.p1, e.p2, e.id));
        }

        // check
        for (int i = 0; i < data.size() / 2; ++i) {
            Entry e = data.get(i);
            assertFalse(containsExact(tree, e.p1, e.p2, e.id));
        }
        for (int i = data.size() / 2; i < data.size(); ++i) {
            Entry e = data.get(i);
            assertTrue(containsExact(tree, e.p1, e.p2, e.id));
        }

        // remove 2nd half
        for (int i = data.size() / 2; i < data.size(); ++i) {
            Entry e = data.get(i);
            assertTrue(tree.remove(e.p1, e.p2, e));
            assertFalse(containsExact(tree, e.p1, e.p2, e.id));
        }

        assertEquals(0, tree.size());
    }

    @Test
    public void testRemoveIf() {
        Random r = new Random(0);
        int dim = 3;
        ArrayList<Entry> data = createInt(0, 1000, 3);
        BoxMultimap<Entry> tree = createTree(data.size(), dim);

        Collections.shuffle(data, r);

        for (Entry e : data) {
            tree.insert(e.p1, e.p2, e);
        }

        // remove 1st half
        for (int i = 0; i < data.size() / 2; ++i) {
            Entry e = data.get(i);
            assertTrue(tree.removeIf(e.p1, e.p2, e2 -> e2.value().id == e.id));
            assertFalse(containsExact(tree, e.p1, e.p2, e.id));
            assertFalse(tree.removeIf(e.p1, e.p2, e2 -> e2.value().id == e.id));
        }

        // check
        for (int i = 0; i < data.size() / 2; ++i) {
            Entry e = data.get(i);
            assertFalse(containsExact(tree, e.p1, e.p2, e.id));
        }
        for (int i = data.size() / 2; i < data.size(); ++i) {
            Entry e = data.get(i);
            assertTrue(containsExact(tree, e.p1, e.p2, e.id));
        }

        // remove 2nd half
        for (int i = data.size() / 2; i < data.size(); ++i) {
            Entry e = data.get(i);
            assertTrue(tree.removeIf(e.p1, e.p2, e2 -> e2.value().id == e.id));
            assertFalse(containsExact(tree, e.p1, e.p2, e.id));
            assertFalse(tree.removeIf(e.p1, e.p2, e2 -> e2.value().id == e.id));
        }

        assertEquals(0, tree.size());
    }

    private <T> BoxMultimap<T> createTree(int size, int dims) {
        switch (candidate) {
            case ARRAY:
                return new RectArray<>(dims, size);
            // case PHTREE_MM: return PHTreeMMP.create(dims);
            case QUAD_HC:
                return QuadTreeRKD.create(dims);
            case QUAD_PLAIN:
                return QuadTreeRKD0.create(dims);
            case RSTAR:
            case STR:
                return RTree.createRStar(dims);
            default:
                throw new UnsupportedOperationException(candidate.name());
        }
    }

    private static class Entry {
        double[] p1;
        double[] p2;
        int id;

        public Entry(int dim, int id) {
            this.p1 = new double[dim];
            this.p2 = new double[dim];
            this.id = id;
        }

        public Entry(double[] key1, double[] key2, int id) {
            this.p1 = key1;
            this.p2 = key2;
            this.id = id;
        }

        boolean equals(Entry e) {
            return id == e.id && Arrays.equals(p1, e.p1) && Arrays.equals(p2, e.p2);
        }

        @Override
        public String toString() {
            return "id=" + id + ":" + Arrays.toString(p1) + "/" + Arrays.toString(p2);
        }
    }
}
