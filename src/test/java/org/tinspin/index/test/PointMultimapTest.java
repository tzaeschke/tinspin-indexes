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
import org.tinspin.index.PointMultimap;
import org.tinspin.index.test.util.TestInstances;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.tinspin.index.Index.*;
import static org.tinspin.index.test.util.TestInstances.IDX;

@RunWith(Parameterized.class)
public class PointMultimapTest extends AbstractWrapperTest {

    private static final int N_DUP = 4;
    private static final int BOUND = 100;
    private static final int LARGE = 50_000;
    private static final int MEDIUM = 5_000;

    private final TestInstances.IDX candidate;

    public PointMultimapTest(TestInstances.IDX candCls) {
        this.candidate = candCls;
    }

    @Parameterized.Parameters
    public static Iterable<Object[]> candidates() {
        ArrayList<Object[]> l = new ArrayList<>();
        // l.add(new Object[]{IDX.ARRAY});
        // l.add(new Object[]{IDX.COVER});
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
        for (int i = 0; i < n; i += N_DUP) {
            Entry e = new Entry(dim, i);
            data.add(e);
            Arrays.setAll(e.p, (x) -> R.nextInt(BOUND));
            for (int i2 = 1; i2 < N_DUP; ++i2) {
                Entry e2 = new Entry(dim, i + i2);
                data.add(e2);
                System.arraycopy(e.p, 0, e2.p, 0, e.p.length);
            }
        }
        return data;
    }

    @Test
    public void smokeTestDupl() {
        double[][] points = {{2, 3}, {2, 3}, {2, 3}, {2, 3}, {2, 3}, {2, 3}};
        int i = 0;
        smokeTest(Arrays.stream(points).flatMap(doubles -> Stream.of(new Entry(doubles, i))).collect(Collectors.toList()));
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
        PointMultimap<Entry> tree = createTree(data.size(), dim);

        for (Entry e : data) {
            tree.insert(e.p, e);
        }

        // Check consistency
        tree.getStats();

        for (Entry e : data) {
            PointIterator<Entry> it = tree.queryExactPoint(e.p);
            assertTrue("query(point) failed: " + e, it.hasNext());
            assertArrayEquals(e.p, it.next().value().p, 0.0000);
        }

        for (Entry e : data) {
            // System.out.println("kNN query: " + e);
            PointIteratorKnn<Entry> iter = tree.queryKnn(e.p, N_DUP);
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

        tree.getStats();

        for (Entry e : data) {
            // System.out.println("query: " + Arrays.toString(e.p));
            PointIterator<Entry> iter = tree.query(e.p, e.p);
            assertTrue("query() failed: " + e, iter.hasNext());
            for (int i = 0; i < N_DUP; ++i) {
                // System.out.println("  found: " + i + " " + e);
                assertTrue("Expected next for i=" + i + " / " + e, iter.hasNext());
                Entry answer = iter.next().value();
                assertArrayEquals("Expected " + e + " but got " + answer, answer.p, e.p, 0.0001);
            }
        }

        for (Entry e : data) {
            PointIterator<Entry> it = tree.queryExactPoint(e.p);
            assertTrue("queryExact() failed: " + e, it.hasNext());
            PointEntry<Entry> e2 = it.next();
            assertArrayEquals(e.p, e2.value().p, 0);
            assertTrue(tree.remove(e.p, e));
        }
    }

    @Test
    public void testUpdate() {
        Random r = new Random(0);
        int dim = 3;
        ArrayList<Entry> data = createInt(0, 1000, 3);
        PointMultimap<Entry> tree = createTree(data.size(), dim);

        for (Entry e : data) {
            tree.insert(e.p, e);
        }

        for (int i = 0; i < data.size(); ++i) {
            Entry e = data.get(i);
            double[] pOld = e.p.clone();
            double[] pNew = e.p.clone();
            Arrays.setAll(pNew, value -> (value + r.nextInt(BOUND / 10)));
            assertTrue(tree.update(pOld, pNew, e));
            assertFalse(tree.update(pOld, pNew, e));
            // Update entry
            System.arraycopy(pNew, 0, e.p, 0, dim);
            assertFalse(containsExact(tree, pOld, e.id));
            assertTrue(containsExact(tree, e.p, e.id));
        }
        tree.getStats();

        for (int i = 0; i < data.size(); ++i) {
            Entry e = data.get(i);
            assertTrue(containsExact(tree, e.p, e.id));
        }
    }

    private boolean containsExact(PointMultimap<Entry> tree, double[] p, int id) {
        PointIterator<Entry> it = tree.queryExactPoint(p);
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
        PointMultimap<Entry> tree = createTree(data.size(), dim);

        Collections.shuffle(data, r);

        for (Entry e : data) {
            tree.insert(e.p, e);
        }

        // remove 1st half
        for (int i = 0; i < data.size() / 2; ++i) {
            Entry e = data.get(i);
            assertTrue(tree.remove(e.p, e));
            assertFalse(containsExact(tree, e.p, e.id));
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
            assertTrue(tree.remove(e.p, e));
            assertFalse(containsExact(tree, e.p, e.id));
        }

        assertEquals(0, tree.size());
    }

    @Test
    public void testRemoveIf() {
        Random r = new Random(0);
        int dim = 3;
        ArrayList<Entry> data = createInt(0, 1000, 3);
        PointMultimap<Entry> tree = createTree(data.size(), dim);

        Collections.shuffle(data, r);

        for (Entry e : data) {
            tree.insert(e.p, e);
        }

        // remove 1st half
        for (int i = 0; i < data.size() / 2; ++i) {
            Entry e = data.get(i);
            assertTrue(tree.removeIf(e.p, e2 -> e2.value().id == e.id));
            assertFalse(containsExact(tree, e.p, e.id));
            assertFalse(tree.removeIf(e.p, e2 -> e2.value().id == e.id));
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
            assertTrue(tree.removeIf(e.p, e2 -> e2.value().id == e.id));
            assertFalse(containsExact(tree, e.p, e.id));
            assertFalse(tree.removeIf(e.p, e2 -> e2.value().id == e.id));
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
        PointMultimap<Entry> tree = createTree(data.size(), dim);

        Collections.shuffle(data, r);

        for (Entry e : data) {
            tree.insert(e.p, e);
        }
        tree.getStats();

        // remove 1st half
        for (int i = 0; i < nDelete; ++i) {
            Entry e = data.get(i);
            assertTrue(tree.remove(e.p, e));
            assertFalse(tree.remove(e.p, e));
            assertFalse(containsExact(tree, e.p, e.id));
            assertFalse(tree.contains(e.p, e));
        }
        tree.getStats();

        // check contains() & kNN
        for (int i = 0; i < nDelete; ++i) {
            Entry e = data.get(i);
            assertFalse(containsExact(tree, e.p, e.id));
            assertFalse(tree.contains(e.p, e));
            Index.PointEntryKnn<Entry> eKnn = tree.query1nn(e.p);
            assertTrue(tree.contains(eKnn.point(), eKnn.value()));
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
                assertFalse(tree.contains(e.p, e));
                assertFalse(kNNResult.contains(e.id));
            } else {
                assertTrue(containsExact(tree, e.p, e.id));
                assertTrue(tree.contains(e.p, e));
                assertTrue(kNNResult.contains(e.id));
            }
        }
        assertEquals(n - nDelete, kNNResult.size());
    }

    private <T> PointMultimap<T> createTree(int size, int dims) {
        switch (candidate) {
            case ARRAY:
                return PointMultimap.Factory.createArray(dims, size);
//            //case CRITBIT: return new PointArray<>(dims, size);
            case KDTREE:
                return PointMultimap.Factory.createKdTree(dims);
            case PHTREE_MM:
                return PointMultimap.Factory.createPhTree(dims);
            case QUAD_HC:
                return PointMultimap.Factory.createQuadtreeHC(dims);
            case QUAD_HC2:
                return PointMultimap.Factory.createQuadtreeHC2(dims);
            case QUAD_PLAIN:
                return PointMultimap.Factory.createQuadtree(dims);
            case RSTAR:
            case STR:
                return PointMultimap.Factory.createRStarTree(dims);
            //           case COVER: return CoverTree.create(dims);
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

    @Test
    public void testIssue0040_remove() {
        double[][] data = new double[][] {
                new double[]{-49.0949020385742, -2.05027413368225, 819588127, 0},
                new double[]{-49.0949020385742, -2.05027389526367, 819588127, 0},
                new double[]{-45.6938514709473, 32.9847145080566, -2056090140, 0},
                new double[]{-45.6938514709473, 32.9847145080566, -2056090140, 0},
                new double[]{-1.7595032453537, 112.097793579102, -267989921, 0},
                new double[]{-1.75950336456299, 112.097793579102, -267989921, 0},
                new double[]{45.6938438415527, 32.9847145080566, 1591613824, 0},
                new double[]{45.6938438415527, 32.9847145080566, 1591613824, 0},
                new double[]{49.0948944091797, -2.05027413368225, 14481734, 0},
                new double[]{49.0948944091797, -2.05027389526367, 14481734, 0},
                new double[]{-49.0949020385742, -2.05027413368225, 819588127, 1},
                new double[]{-49.0949020385742, -2.05027389526367, 819588127, 1},
                new double[]{-49.0949020385742, -2.05027413368225, 916603126, 0},
        };

        PointMultimap<Integer> tree = createTree(100,2);
        for (int i = 0; i < data.length; i++) {
            if (data[i][3] == 0) {
                tree.insert(Arrays.copyOf(data[i], 2), (int)data[i][2]);
            } else {
                tree.remove(Arrays.copyOf(data[i], 2), (int)data[i][2]);
            }
        }

        assertEquals(9, tree.size());
        int n = 0;
        double[] min = new double[]{-50, -3};
        double[] max = new double[]{50, 113};
        for (Index.PointIterator<Integer> it = tree.query(min, max); it.hasNext(); it.next()) {
            n++;
        }
        assertEquals(9, n);
    }

}
