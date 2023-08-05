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
import org.tinspin.index.BoxMap;

import java.util.*;

import static org.junit.Assert.*;
import static org.tinspin.index.Index.BoxEntryKnn;
import static org.tinspin.index.Index.BoxIterator;
import static org.tinspin.index.Index.BoxIteratorKnn;
import static org.tinspin.index.test.util.TestInstances.IDX;

@RunWith(Parameterized.class)
public class BoxMapTest extends AbstractWrapperTest {

    private static final int BOUND = 10000;
    private static final int BOX_LEN_MAX = 10;
    private static final int LARGE = 10_000;
    private static final int MEDIUM = 5_000;

    private final IDX candidate;

    public BoxMapTest(IDX candCls) {
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
        for (int i = 0; i < n; i++) {
            Entry e = new Entry(dim, i);
            data.add(e);
            for (int d = 0; d < dim; d++) {
                e.p1[d] = R.nextInt() * BOUND;
                e.p2[d] = e.p1[d] + R.nextDouble() * BOX_LEN_MAX;
            }
        }
        assertEquals(n, data.size());
        return data;
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
                int n = nAll++;
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
        BoxMap<Entry> tree = createTree(data.size(), dim);
        assertEquals(dim, tree.getDims());

        for (Entry e : data) {
            tree.insert(e.p1, e.p2, e);
        }
        // System.out.println(tree.toStringTree());
        for (Entry e : data) {
            assertTrue("contains(point) failed: " + e, tree.contains(e.p1, e.p2));
            Entry e2 = tree.queryExact(e.p1, e.p2);
            assertNotNull("queryExact(point) failed: " + e, e2);
            assertArrayEquals(e.p1, e2.p1, 0.0000);
            assertArrayEquals(e.p2, e2.p2, 0.0000);
        }

        for (Entry e : data) {
            // System.out.println("kNN query: " + e);
            BoxIteratorKnn<Entry> iter = tree.queryKnn(e.p1, 1);
            assertTrue("kNNquery() failed: " + e, iter.hasNext());
            BoxEntryKnn<Entry> be = iter.next();
            Entry answer = be.value();
            // assertArrayEquals("Expected " + e + " but got " + answer, answer.p1, e.p1, 0.0001);
            // assertArrayEquals("Expected " + e + " but got " + answer, answer.p2, e.p2, 0.0001);
            assertEquals(0, be.dist(), 0.0);
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
            Entry e2 = tree.queryExact(e.p1, e.p2);
            assertNotNull("query() failed: " + e, e2);
            assertArrayEquals("Expected " + e + " but got " + e2, e2.p1, e.p1, 0.0001);
            assertArrayEquals("Expected " + e + " but got " + e2, e2.p2, e.p2, 0.0001);
            assertEquals(e.id, e2.id);
        }

        for (Entry e : data) {
            Entry e2 = tree.queryExact(e.p1, e.p2);
            assertNotNull("queryExact() failed: " + e, e2);
            assertArrayEquals(e.p1, e2.p1, 0);
            assertArrayEquals(e.p2, e2.p2, 0);
            assertNotNull(tree.remove(e.p1, e.p2));
            assertNull(tree.remove(e.p1, e.p2));
        }
    }

    @Test
    public void testUpdate() {
        Random r = new Random(42);
        int dim = 3;
        ArrayList<Entry> data = createInt(0, 1000, 3);
        BoxMap<Entry> tree = createTree(data.size(), dim);

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
                p1New[d] = r.nextDouble() * BOUND;
                p2New[d] = p1New[d] + r.nextDouble() * BOX_LEN_MAX;
            }
            assertNotNull(tree.update(p1Old, p2Old, p1New, p2New));
            assertNull(tree.update(p1Old, p2Old, p1New, p2New));
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

    private boolean containsExact(BoxMap<Entry> tree, double[] p1, double[] p2, int id) {
        Entry e = tree.queryExact(p1, p2);
        return e != null && e.id == id;
    }

    @Test
    public void testRemove() {
        Random r = new Random(0);
        int dim = 3;
        ArrayList<Entry> data = createInt(0, 1000, 3);
        BoxMap<Entry> tree = createTree(data.size(), dim);

        Collections.shuffle(data, r);

        for (Entry e : data) {
            tree.insert(e.p1, e.p2, e);
        }

        // remove 1st half
        for (int i = 0; i < data.size() / 2; ++i) {
            Entry e = data.get(i);
            assertNotNull(tree.remove(e.p1, e.p2));
            assertNull(tree.remove(e.p1, e.p2));
            assertFalse(containsExact(tree, e.p1, e.p2, e.id));
            assertFalse(tree.contains(e.p1, e.p2));
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
            assertNotNull(tree.remove(e.p1, e.p2));
            assertNull(tree.remove(e.p1, e.p2));
            assertFalse(containsExact(tree, e.p1, e.p2, e.id));
            assertFalse(tree.contains(e.p1, e.p2));
        }

        assertEquals(0, tree.size());
    }

    private <T> BoxMap<T> createTree(int size, int dims) {
        switch (candidate) {
            case ARRAY:
                return BoxMap.Factory.createArray(dims, size);
            // case PHTREE_MM: return PHTreeMMP.create(dims);
            case QUAD_HC:
                return BoxMap.Factory.createQuadtreeHC(dims);
            case QUAD_PLAIN:
                return BoxMap.Factory.createQuadtree(dims);
            case RSTAR:
            case STR:
                return BoxMap.Factory.createRStarTree(dims);
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
