///*
// * Copyright 2009-2017 Tilmann Zaeschke. All rights reserved.
// *
// * This file is part of TinSpin.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package org.tinspin.index.test;
//
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.junit.runners.Parameterized;
//import org.tinspin.index.*;
//import org.tinspin.index.kdtree.KDTree;
//import org.tinspin.index.phtree.PHTreeMMP;
//import org.tinspin.index.qthypercube.QuadTreeKD;
//import org.tinspin.index.qthypercube2.QuadTreeKD2;
//import org.tinspin.index.qtplain.QuadTreeKD0;
//import org.tinspin.index.rtree.RTree;
//
//import java.util.*;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//import static org.junit.Assert.*;
//import static org.tinspin.index.test.util.TestInstances.IDX;
//
//@RunWith(Parameterized.class)
//public class BoxMultimapTest extends AbstractWrapperTest {
//
//    private static final int N_DUP = 4;
//    private static final int BOUND = 10000;
//    private static final int BOX_LEN_MAX = 10;
//
//    private final IDX candidate;
//    public BoxMultimapTest(IDX candCls) {
//        this.candidate = candCls;
//    }
//
//    private ArrayList<Entry> createInt(long seed, int n, int dim) {
//        ArrayList<Entry> data = new ArrayList<>(n);
//        Random R = new Random(seed);
//        for (int i = 0; i < n; i += N_DUP) {
//            Entry e = new Entry(dim, i);
//            data.add(e);
//            for (int d = 0; d < dim; d++) {
//                e.p1[d] = R.nextInt(BOUND);
//                e.p2[d] = e.p1[d] + R.nextInt(BOX_LEN_MAX);
//            }
//            for (int i2 = 1; i2 < N_DUP; ++i2) {
//                Entry e2 = new Entry(dim, i + i2);
//                data.add(e2);
//                System.arraycopy(e.p1, 0, e2.p1, 0, e.p1.length);
//                System.arraycopy(e.p2, 0, e2.p2, 0, e.p2.length);
//            }
//        }
//        return data;
//    }
//
//    @Parameterized.Parameters
//    public static Iterable<Object[]> candidates() {
//        ArrayList<Object[]> l = new ArrayList<>();
//        l.add(new Object[]{IDX.ARRAY});
//        l.add(new Object[]{IDX.PHTREE_MM});
//        l.add(new Object[]{IDX.QUAD_PLAIN});
//        l.add(new Object[]{IDX.QUAD_HC});
//        l.add(new Object[]{IDX.RSTAR});
//        l.add(new Object[]{IDX.STR});
//        return l;
//    }
//
//
//    @Test
//    public void smokeTestDupl() {
//        double[][] points = {{2, 3}, {2, 3}, {2, 3}, {2, 3}, {2, 3}, {2, 3}};
//        int i = 0;
//        smokeTest(Arrays.stream(points).flatMap(doubles -> Stream.of(new Entry(doubles, i))).collect(Collectors.toList()));
//    }
//
//    @Test
//    public void smokeTest2D_0() {
//        smokeTest(createInt(0, 20, 2));
//    }
//
//    @Test
//    public void smokeTest2D_1() {
//        smokeTest(createInt(1, 20, 2));
//    }
//
//    /**
//     * Tests handling of all points being on a line, i.e. correct handling of <=, etc.
//     */
//    @Test
//    public void smokeTest2D_Line() {
//        for (int r = 0; r < 10; ++r) {
//            List<Entry> data = createInt(0, 1000, 3);
//            int nAll = 0;
//            for (Entry e : data) {
//                int n = nAll++ / N_DUP;
//                e.p1[0] = n % 3;
//                e.p1[1] = n++;
//                e.p1[2] = n % 5;
//                e.p2[0] = e.p1[0] + BOX_LEN_MAX;
//                e.p2[1] = e.p1[1] + BOX_LEN_MAX;
//                e.p2[2] = e.p1[2] + BOX_LEN_MAX;
//            }
//            Collections.shuffle(data, new Random(r));
//            smokeTest(data);
//        }
//    }
//
//    @Test
//    public void smokeTest5D() {
//        smokeTest(createInt(0, 20, 5));
//    }
//
//    @Test
//    public void smokeTest1D_Large() {
//        smokeTest(createInt(0, 100_000, 1));
//    }
//
//    @Test
//    public void smokeTest3D_Large() {
//        smokeTest(createInt(0, 100_000, 3));
//    }
//
//    @Test
//    public void smokeTest10D_Large() {
//        smokeTest(createInt(0, 10_000, 10));
//    }
//
//    private void smokeTest(List<Entry> data) {
//        int dim = data.get(0).p1.length;
//        PointIndexMM<Entry> tree = createTree(data.size(), dim);
//
//        for (Entry e : data) {
//            tree.insert(e.p1, e.p2, e);
//        }
//	    // System.out.println(tree.toStringTree());
//        for (Entry e : data) {
//            QueryIterator<PointEntry<Entry>> it = tree.query(e.p);
//            assertTrue("query(point) failed: " + e, it.hasNext());
//            assertArrayEquals(e.p, it.next().value().p, 0.0000);
//        }
//
//        for (Entry e : data) {
//            // System.out.println("kNN query: " + e);
//            QueryIteratorKNN<PointEntryDist<Entry>> iter = tree.queryKNN(e.p, N_DUP);
//            assertTrue("kNNquery() failed: " + e, iter.hasNext());
//            Entry answer = iter.next().value();
//            assertArrayEquals("Expected " + e + " but got " + answer, answer.p, e.p, 0.0001);
//        }
//
//        for (Entry e : data) {
//            // System.out.println("query: " + Arrays.toString(e.p));
//            QueryIterator<PointEntry<Entry>> iter = tree.query(e.p, e.p);
//            assertTrue("query() failed: " + e, iter.hasNext());
//            for (int i = 0; i < N_DUP; ++i) {
//                // System.out.println("  found: " + i + " " + e);
//                assertTrue("Expected next for i=" + i + " / " + e, iter.hasNext());
//                Entry answer = iter.next().value();
//                assertArrayEquals("Expected " + e + " but got " + answer, answer.p, e.p, 0.0001);
//            }
//        }
//
//        for (Entry e : data) {
//            //			System.out.println(tree.toStringTree());
//            //			System.out.println("Removing: " + Arrays.toString(key));
//            QueryIterator<PointEntry<Entry>> it = tree.query(e.p);
//            assertTrue("queryExact() failed: " + e, it.hasNext());
//            PointEntry<Entry> e2 = it.next();
//            assertArrayEquals(e.p, e2.value().p, 0);
//            assertTrue(tree.remove(e.p, e));
//        }
//    }
//
//    @Test
//    public void testUpdate() {
//        Random r = new Random(0);
//        int dim = 3;
//        ArrayList<Entry> data = createInt(0, 1000, 3);
//        PointIndexMM<Entry> tree = createTree(data.size(), dim);
//
//        for (Entry e : data) {
//            tree.insert(e.p, e);
//        }
//
//        for (int i = 0; i < data.size(); ++i) {
//            Entry e = data.get(i);
//            double[] pOld = e.p.clone();
//            double[] pNew = e.p.clone();
//            Arrays.setAll(pNew, value -> (value + r.nextInt(BOUND / 10)));
//            assertTrue(tree.update(pOld, pNew, e));
//            // Update entry
//            System.arraycopy(pNew, 0, e.p, 0, dim);
//            assertFalse(containsExact(tree, pOld, e.id));
//            assertTrue(containsExact(tree, e.p, e.id));
//        }
//
//        for (int i = 0; i < data.size(); ++i) {
//            Entry e = data.get(i);
//            assertTrue(containsExact(tree, e.p, e.id));
//        }
//    }
//
//    private boolean containsExact(PointIndexMM<Entry> tree, double[] p, int id) {
//        QueryIterator<PointEntry<Entry>> it = tree.query(p);
//        while (it.hasNext()) {
//            if (it.next().value().id == id) {
//                return true;
//            }
//        }
//        return false;
//    }
//
//    @Test
//    public void testRemove() {
//        Random r = new Random(0);
//        int dim = 3;
//        ArrayList<Entry> data = createInt(0, 1000, 3);
//        PointIndexMM<Entry> tree = createTree(data.size(), dim);
//
//        Collections.shuffle(data, r);
//
//        for (Entry e : data) {
//            tree.insert(e.p1, e.p2, e);
//        }
//
//        // remove 1st half
//        for (int i = 0; i < data.size()/2; ++i) {
//            Entry e = data.get(i);
//            assertTrue(tree.remove(e.p, e));
//            assertFalse(containsExact(tree, e.p, e.id));
//        }
//
//        // check
//        for (int i = 0; i < data.size()/2; ++i) {
//            Entry e = data.get(i);
//            assertFalse(containsExact(tree, e.p, e.id));
//        }
//        for (int i = data.size()/2; i < data.size(); ++i) {
//            Entry e = data.get(i);
//            assertTrue(containsExact(tree, e.p, e.id));
//        }
//
//        // remove 2nd half
//        for (int i = data.size()/2; i < data.size(); ++i) {
//            Entry e = data.get(i);
//            assertTrue(tree.remove(e.p, e));
//            assertFalse(containsExact(tree, e.p, e.id));
//        }
//
//        assertEquals(0, tree.size());
//    }
//
//    @Test
//    public void testRemoveIf() {
//        Random r = new Random(0);
//        int dim = 3;
//        ArrayList<Entry> data = createInt(0, 1000, 3);
//        PointIndexMM<Entry> tree = createTree(data.size(), dim);
//
//        Collections.shuffle(data, r);
//
//        for (Entry e : data) {
//            tree.insert(e.p, e);
//        }
//
//        // remove 1st half
//        for (int i = 0; i < data.size()/2; ++i) {
//            Entry e = data.get(i);
//            assertTrue(tree.removeIf(e.p, e2 -> e2.value().id == e.id));
//            assertFalse(containsExact(tree, e.p, e.id));
//            assertFalse(tree.removeIf(e.p, e2 -> e2.value().id == e.id));
//        }
//
//        // check
//        for (int i = 0; i < data.size()/2; ++i) {
//            Entry e = data.get(i);
//            assertFalse(containsExact(tree, e.p, e.id));
//        }
//        for (int i = data.size()/2; i < data.size(); ++i) {
//            Entry e = data.get(i);
//            assertTrue(containsExact(tree, e.p, e.id));
//        }
//
//        // remove 2nd half
//        for (int i = data.size()/2; i < data.size(); ++i) {
//            Entry e = data.get(i);
//            assertTrue(tree.removeIf(e.p1, e.p2, e2 -> e2.value().id == e.id));
//            assertFalse(containsExact(tree, e.p1, e.p2, e.id));
//            assertFalse(tree.removeIf(e.p1, e.p2, e2 -> e2.value().id == e.id));
//        }
//
//        assertEquals(0, tree.size());
//    }
//
//    private <T> PointIndexMM<T> createTree(int size, int dims) {
//        switch (candidate) {
////            case ARRAY: return new PointArray<>(dims, size);
////            //case CRITBIT: return new PointArray<>(dims, size);
//            case KDTREE: return KDTree.create(dims);
//            case PHTREE_MM: return PHTreeMMP.create(dims);
//            case QUAD_HC: return QuadTreeKD.create(dims);
//            case QUAD_HC2: return QuadTreeKD2.create(dims);
//            case QUAD_PLAIN: return QuadTreeKD0.create(dims);
//            case RSTAR:
//            case STR: return PointIndexMMWrapper.create(RTree.createRStar(dims));
// //           case COVER: return CoverTree.create(dims);
//            default:
//                throw new UnsupportedOperationException(candidate.name());
//        }
//    }
//
//    private static class Entry {
//        double[] p1;
//        double[] p2;
//        int id;
//
//        public Entry(int dim, int id) {
//            this.p1 = new double[dim];
//            this.p2 = new double[dim];
//            this.id = id;
//        }
//
//        public Entry(double[] key1, double[] key2, int id) {
//            this.p1 = key1;
//            this.p2 = key2;
//            this.id = id;
//        }
//
//        boolean equals(Entry e) {
//            return id == e.id && Arrays.equals(p1, e.p1) && Arrays.equals(p2, e.p2);
//        }
//
//        @Override
//        public String toString() {
//            return "id=" + id + ":" + Arrays.toString(p1) + "/" + Arrays.toString(p2);
//        }
//    }
//}