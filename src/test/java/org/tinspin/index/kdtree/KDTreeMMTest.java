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
package org.tinspin.index.kdtree;

import org.junit.Test;
import org.tinspin.index.PointEntry;
import org.tinspin.index.PointEntryDist;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class KDTreeMMTest {

    private static final int N_DUP = 4;
    private static final int BOUND = 100;

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
        List<Entry> data = createInt(0, 10000, 3);
        int nAll = 0;
        for (Entry e : data) {
            int n = nAll++ / N_DUP;
            e.p[0] = n % 3;
            e.p[1] = n++;
            e.p[2] = n % 5;
        }
        Collections.shuffle(data);
        smokeTest(data);
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
        smokeTest(createInt(0, 100_000, 10));
    }

    private void smokeTest(List<Entry> data) {
        int dim = data.get(0).p.length;
        KDTree<Entry> tree = KDTree.create(dim);
        for (Entry e : data) {
            tree.insert(e.p, e);
        }
//	    System.out.println(tree.toStringTree());
        for (Entry e : data) {
            if (!tree.containsExact(e.p)) {
                throw new IllegalStateException(Arrays.toString(e.p));
            }
        }

        for (Entry e : data) {
            // System.out.println("kNN query: " + e);
            QueryIteratorKnn<PointEntryDist<Entry>> iter = tree.queryKnn(e.p, N_DUP);
            if (!iter.hasNext()) {
                throw new IllegalStateException("kNN() failed: " + Arrays.toString(e.p));
            }
            Entry answer = iter.next().value();
            if (answer.p != e.p && !Arrays.equals(answer.p, e.p)) {
                throw new IllegalStateException("Expected " + Arrays.toString(e.p) + " but got " + Arrays.toString(answer.p));
            }
        }

        for (Entry e : data) {
            // System.out.println("query: " + Arrays.toString(e.p));
            QueryIterator<PointEntry<Entry>> iter = tree.query(e.p, e.p);
            if (!iter.hasNext()) {
                throw new IllegalStateException("query() failed: " + Arrays.toString(e.p));
            }
            for (int i = 0; i < N_DUP; ++i) {
                // System.out.println("  found: " + i + " " + e);
                Entry answer = iter.next().value();
                if (!Arrays.equals(answer.p, e.p)) {
                    throw new IllegalStateException("Expected " + e + " but got " + answer);
                }
            }
        }

        for (Entry e : data) {
//			System.out.println(tree.toStringTree());
//			System.out.println("Removing: " + Arrays.toString(key));
            if (!tree.containsExact(e.p)) {
                throw new IllegalStateException("containsExact() failed: " + Arrays.toString(e.p));
            }
            Entry answer = tree.remove(e.p);
            if (answer.p != e.p && !Arrays.equals(answer.p, e.p)) {
                throw new IllegalStateException("Expected " + Arrays.toString(e.p) + " but got " + Arrays.toString(answer.p));
            }
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
