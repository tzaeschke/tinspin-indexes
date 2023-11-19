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
import org.tinspin.index.Index;
import org.tinspin.index.PointMap;

import java.util.*;

import static org.junit.Assert.*;
import static org.tinspin.index.Index.PointIterator;
import static org.tinspin.index.Index.PointIteratorKnn;
import static org.tinspin.index.test.util.TestInstances.IDX;

@RunWith(Parameterized.class)
public class PointMapTest extends AbstractWrapperTest {

    private static final int BOUND = 100;
    private static final int LARGE = 50_000;
    private static final int MEDIUM = 5_000;

    private final IDX candidate;

    public PointMapTest(IDX candCls) {
        this.candidate = candCls;
    }

    @Parameterized.Parameters
    public static Iterable<Object[]> candidates() {
        ArrayList<Object[]> l = new ArrayList<>();
        // l.add(new Object[]{IDX.ARRAY});
        l.add(new Object[]{IDX.COVER});
        l.add(new Object[]{IDX.KDTREE});
        l.add(new Object[]{IDX.PHTREE_MM});
        l.add(new Object[]{IDX.QUAD_HC});
        l.add(new Object[]{IDX.QUAD_HC2});
        l.add(new Object[]{IDX.QUAD_PLAIN});
        l.add(new Object[]{IDX.RSTAR});
        l.add(new Object[]{IDX.STR});
        // l.add(new Object[]{IDX.CRITBIT});
        return l;
    }

    private ArrayList<Entry> createInt(long seed, int n, int dim) {
        ArrayList<Entry> data = new ArrayList<>(n);
        Random R = new Random(seed);
        for (int i = 0; i < n; i++) {
            Entry e = new Entry(dim, i);
            data.add(e);
            Arrays.setAll(e.p, (x) -> R.nextDouble() * BOUND);
        }
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
                e.p[0] = n % 3;
                e.p[1] = n++;
                e.p[2] = n % 5;
            }
            Collections.shuffle(data, new Random(r));
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
        int dim = data.get(0).p.length;
        PointMap<Entry> tree = createTree(data.size(), dim);

        for (Entry e : data) {
            tree.insert(e.p, e);
        }
        // System.out.println(tree.toStringTree());
        for (Entry e : data) {
            assertTrue("contains(point) failed: " + e, tree.contains(e.p));
            Entry e2 = tree.queryExact(e.p);
            assertNotNull("queryExact(point) failed: " + e, e2);
            assertArrayEquals(e.p, e2.p, 0.0000);
        }

        for (Entry e : data) {
            // System.out.println("kNN query: " + e);
            PointIteratorKnn<Entry> iter = tree.queryKnn(e.p, 1);
            assertTrue("kNNquery() failed: " + e, iter.hasNext());
            Entry answer = iter.next().value();
            assertArrayEquals("Expected " + e + " but got " + answer, answer.p, e.p, 0.0001);
        }

        if (candidate != IDX.COVER && candidate != IDX.KDTREE && candidate != IDX.QUAD_PLAIN
                && candidate != IDX.QUAD_HC && candidate != IDX.QUAD_HC2) {
            int nExtent = 0;
            PointIterator<Entry> extent = tree.iterator();
            while (extent.hasNext()) {
                extent.next();
                nExtent++;
            }
            assertEquals(data.size(), nExtent);
        }

        if (candidate != IDX.COVER) {
            for (Entry e : data) {
                // System.out.println("query: " + Arrays.toString(e.p));
                PointIterator<Entry> iter = tree.query(e.p, e.p);
                assertTrue("query() failed: " + e, iter.hasNext());
            }

            for (Entry e : data) {
                assertTrue("contains(point) failed: " + e, tree.contains(e.p));
                Entry e2 = tree.queryExact(e.p);
                assertNotNull("queryExact(point) failed: " + e, e2);
                assertArrayEquals(e.p, e2.p, 0.0000);
                assertNotNull(tree.remove(e.p));

                assertFalse("contains(point) failed: " + e, tree.contains(e.p));
                assertNull("queryExact(point) failed: " + e, tree.queryExact(e.p));
                assertNull(tree.remove(e.p));
            }
        }
    }

    @Test
    public void testUpdate() {
        if (candidate == IDX.COVER) {
            return;
        }
        Random r = new Random(0);
        int dim = 3;
        ArrayList<Entry> data = createInt(0, 1000, 3);
        PointMap<Entry> tree = createTree(data.size(), dim);

        for (Entry e : data) {
            tree.insert(e.p, e);
        }

        for (int i = 0; i < data.size(); ++i) {
            Entry e = data.get(i);
            double[] pOld = e.p.clone();
            double[] pNew = e.p.clone();
            Arrays.setAll(pNew, value -> (value + r.nextDouble() * BOUND / 10));
            assertNotNull(tree.update(pOld, pNew));
            assertNull(tree.update(pOld, pNew));
            // Update entry
            System.arraycopy(pNew, 0, e.p, 0, dim);
            assertFalse(containsExact(tree, pOld, e.id));
            assertTrue(containsExact(tree, e.p, e.id));
        }

        for (int i = 0; i < data.size(); ++i) {
            Entry e = data.get(i);
            assertTrue(containsExact(tree, e.p, e.id));
        }
    }

    private boolean containsExact(PointMap<Entry> tree, double[] p, int id) {
        Entry e = tree.queryExact(p);
        return e != null && e.id == id;
    }

    @Test
    public void testRemove() {
        if (candidate == IDX.COVER) {
            return;
        }
        Random r = new Random(0);
        int dim = 3;
        ArrayList<Entry> data = createInt(0, 1000, 3);
        PointMap<Entry> tree = createTree(data.size(), dim);

        Collections.shuffle(data, r);

        for (Entry e : data) {
            tree.insert(e.p, e);
        }

        // remove 1st half
        for (int i = 0; i < data.size() / 2; ++i) {
            Entry e = data.get(i);
            assertNotNull(tree.remove(e.p));
            assertNull(tree.remove(e.p));
            assertFalse(containsExact(tree, e.p, e.id));
            assertFalse(tree.contains(e.p));
        }

        // check
        for (int i = 0; i < data.size() / 2; ++i) {
            Entry e = data.get(i);
            assertFalse(containsExact(tree, e.p, e.id));
        }
        for (int i = data.size() / 2; i < data.size(); ++i) {
            Entry e = data.get(i);
            assertTrue(containsExact(tree, e.p, e.id));
        }

        // remove 2nd half
        for (int i = data.size() / 2; i < data.size(); ++i) {
            Entry e = data.get(i);
            assertNotNull(tree.remove(e.p));
            assertNull(tree.remove(e.p));
            assertFalse(containsExact(tree, e.p, e.id));
            assertFalse(tree.contains(e.p));
        }

        assertEquals(0, tree.size());
    }

    @Test
    public void testIssueKnnRemove() {
        if (candidate == IDX.COVER) {
            return;
        }
        Random r = new Random(0);
        int dim = 3;
        int n = 1000;
        int nDelete = n/2;
        ArrayList<Entry> data = createInt(0, n, 3);
        PointMap<Entry> tree = createTree(data.size(), dim);

        Collections.shuffle(data, r);

        for (Entry e : data) {
            tree.insert(e.p, e);
        }

        // remove 1st half
        for (int i = 0; i < nDelete; ++i) {
            Entry e = data.get(i);
            assertNotNull(tree.remove(e.p));
            assertNull(tree.remove(e.p));
            assertFalse(containsExact(tree, e.p, e.id));
            assertFalse(tree.contains(e.p));
        }

        // check contains() & kNN
        for (int i = 0; i < nDelete; ++i) {
            Entry e = data.get(i);
            assertFalse(containsExact(tree, e.p, e.id));
            assertFalse(tree.contains(e.p));
            Index.PointEntryKnn<Entry> eKnn = tree.query1nn(e.p);
            assertFalse(Arrays.equals(e.p, eKnn.point()));
            assertTrue(tree.contains(eKnn.point()));
        }

        // Issue #37: Entries remained referenced in arrays after removal.
        //   Moreover, the entry count was ignored by kNN, so it looked at the whole
        //   array instead of just the valid entries.
        Index.PointIteratorKnn<Entry> itKnn = tree.queryKnn(data.get(0).p, n);
        Set<Integer> kNNResult = new HashSet<>();
        while (itKnn.hasNext()) {
            Index.PointEntryKnn<Entry> eKnn = itKnn.next();
            kNNResult.add(eKnn.value().id);
        }
        for (int i = 0; i < n; i++) {
            Entry e = data.get(i);
            if (i < nDelete) {
                assertFalse(containsExact(tree, e.p, e.id));
                assertFalse(tree.contains(e.p));
                assertFalse(kNNResult.contains(e.id));
            } else {
                assertTrue(containsExact(tree, e.p, e.id));
                assertTrue(tree.contains(e.p));
                assertTrue(kNNResult.contains(e.id));
            }
        }
        assertEquals(n - nDelete, kNNResult.size());
    }

    private <T> PointMap<T> createTree(int size, int dims) {
        switch (candidate) {
            case ARRAY:
                return PointMap.Factory.createArray(dims, size);
            //case CRITBIT: return new PointArray<>(dims, size);
            case KDTREE:
                return PointMap.Factory.createKdTree(dims);
            case PHTREE_MM:
                return PointMap.Factory.createPhTree(dims);
            case QUAD_HC:
                return PointMap.Factory.createQuadtreeHC(dims);
            case QUAD_HC2:
                return PointMap.Factory.createQuadtreeHC2(dims);
            case QUAD_PLAIN:
                return PointMap.Factory.createQuadtree(dims);
            case RSTAR:
            case STR:
                return PointMap.Factory.createRStarTree(dims);
            case COVER:
                return PointMap.Factory.createCoverTree(dims);
            default:
                throw new UnsupportedOperationException(candidate.name());
        }
    }

    private static class Entry {
        double[] p;
        int id;

        public Entry(int dim, int id) {
            this.p = new double[dim];
            this.id = id;
        }

        public Entry(double[] key, int id) {
            this.p = key;
            this.id = id;
        }

        boolean equals(Entry e) {
            return id == e.id && Arrays.equals(p, e.p);
        }

        @Override
        public String toString() {
            return "id=" + id + ":" + Arrays.toString(p);
        }
    }
}
