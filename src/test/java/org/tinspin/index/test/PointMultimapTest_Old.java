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
import org.tinspin.index.array.PointArray;
import org.tinspin.index.covertree.CoverTree;
import org.tinspin.index.kdtree.KDTree;
import org.tinspin.index.phtree.PHTreeP;
import org.tinspin.index.qthypercube.QuadTreeKD;
import org.tinspin.index.qthypercube2.QuadTreeKD2;
import org.tinspin.index.qtplain.QuadTreeKD0;
import org.tinspin.index.rtree.RTree;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.tinspin.index.test.PointMultimapTest.INDEX.KDTREE;

@RunWith(Parameterized.class)
public class PointMultimapTest_Old extends AbstractWrapperTest {

    private static final int N_DUP = 4;
    private static final int BOUND = 100;

    private final INDEX candidate;
    public PointMultimapTest_Old(INDEX candCls) {
        this.candidate = candCls;
    }

    private List<Entry> createInt(long seed, int n, int dim) {
        List<Entry> data = new ArrayList<>(n);
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

    @Parameterized.Parameters
    public static Iterable<Object[]> candidates() {
        ArrayList<Object[]> l = new ArrayList<>();
//        l.add(new Object[]{INDEX.ARRAY});
// TODO		l.add(new Object[]{INDEX.COVER});
        l.add(new Object[]{KDTREE});
//        l.add(new Object[]{INDEX.PHTREE});
        l.add(new Object[]{INDEX.QUAD});
        l.add(new Object[]{INDEX.QUAD2});
        l.add(new Object[]{INDEX.QUAD_OLD});
        l.add(new Object[]{INDEX.RSTAR});
        l.add(new Object[]{INDEX.STR});
//		l.add(new Object[]{INDEX.CRITBIT});
        return l;
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
        smokeTest(createInt(0, 100_000, 1));
    }

    @Test
    public void smokeTest3D_Large() {
        smokeTest(createInt(0, 100_000, 3));
    }

    @Test
    public void smokeTest10D_Large() {
        smokeTest(createInt(0, 10_000, 10));
    }

    private void smokeTest(List<Entry> data) {
        int dim = data.get(0).p.length;
        PointIndex<Entry> tree = createTree(data.size(), dim);

        //PointIndex<Entry> tree = KDTree.create(dim);
        for (Entry e : data) {
            tree.insert(e.p, e);
        }
	    // System.out.println(tree.toStringTree());
        for (Entry e : data) {
            Entry e2 = tree.queryExact(e.p);
            assertNotNull("queryExact() failed: " + e, e2);
        }

        for (Entry e : data) {
            // System.out.println("kNN query: " + e);
            QueryIteratorKNN<PointEntryDist<Entry>> iter = tree.queryKNN(e.p, N_DUP);
            assertTrue("kNNquery() failed: " + e, iter.hasNext());
            Entry answer = iter.next().value();
            assertArrayEquals("Expected " + e + " but got " + answer, answer.p, e.p, 0.0001);
        }

        for (Entry e : data) {
            // System.out.println("query: " + Arrays.toString(e.p));
            QueryIterator<PointEntry<Entry>> iter = tree.query(e.p, e.p);
            assertTrue("query() failed: " + e, iter.hasNext());
            for (int i = 0; i < N_DUP; ++i) {
                // System.out.println("  found: " + i + " " + e);
                assertTrue("Expected next for i=" + i + " / " + e, iter.hasNext());
                Entry answer = iter.next().value();
                assertArrayEquals("Expected " + e + " but got " + answer, answer.p, e.p, 0.0001);
            }
        }

        for (Entry e : data) {
            //			System.out.println(tree.toStringTree());
            //			System.out.println("Removing: " + Arrays.toString(key));
            Entry e2 = tree.queryExact(e.p);
            assertNotNull("queryExact() failed: " + e, e2);
            Entry answer = tree.remove(e.p);
            assertArrayEquals("Expected " + e + " but got " + answer, answer.p, e.p, 0.0001);
        }
    }

    enum INDEX {
        /** Naive array implementation, for verification only */
        ARRAY,
        /** kD-Tree */
        KDTREE,
        /** PH-Tree */
        PHTREE,
        /** CritBit */
        CRITBIT,
        /** Quadtree with HC navigation*/
        QUAD,
        /** Quadtree with HC navigation version 2 */
        QUAD2,
        /** Plain Quadtree */
        QUAD_OLD,
        /** RStarTree */
        RSTAR,
        /** STR-loaded RStarTree */
        STR,
        /** CoverTree */
        COVER
    }

    private <T> PointIndex<T> createTree(int size, int dims) {
        switch (candidate) {
            case ARRAY: return new PointArray<>(dims, size);
            //case CRITBIT: return new PointArray<>(dims, size);
            case KDTREE: return KDTree.create(dims);
            case PHTREE: return PHTreeP.createPHTree(dims);
            case QUAD: return QuadTreeKD.create(dims);
            case QUAD2: return QuadTreeKD2.create(dims);
            case QUAD_OLD: return QuadTreeKD0.create(dims);
            case RSTAR:
            case STR: return PointIndexWrapper.create(RTree.createRStar(dims));
            case COVER: return CoverTree.create(dims);
            default:
                throw new UnsupportedOperationException();
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