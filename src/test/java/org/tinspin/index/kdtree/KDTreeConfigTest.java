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
import org.tinspin.index.IndexConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;
import static org.tinspin.index.Index.PointIterator;
import static org.tinspin.index.Index.PointIteratorKnn;

public class KDTreeConfigTest {

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
    public void smokeTest2D() {
        smokeTest(createInt(0, 100_000, 2));
    }

    @Test
    public void smokeTest3D() {
        smokeTest(createInt(0, 10_000, 3));
    }

    @Test
    public void testDefensiveCopyingDefault() {
        IndexConfig config = IndexConfig.create(42);
        assertEquals(42, config.getDimensions());
        assertTrue(config.getDefensiveKeyCopy());
        config.setDefensiveKeyCopy(false);
        assertFalse(config.getDefensiveKeyCopy());
    }

    @Test
    public void testDefensiveCopyingFalse() {
        IndexConfig config = IndexConfig.create(3).setDefensiveKeyCopy(false);
        KDTree<Entry> tree = KDTree.create(config);

        double[] point = new double[]{1, 2, 3};
        double[] pointOriginal = point.clone();
        Entry e = new Entry(point, 42);
        tree.insert(point, e);

        // Never do this! We just verify that the key is not copied.
        point[1] = 15;
        assertTrue(tree.contains(point));
        assertFalse(tree.contains(pointOriginal));
    }

    @Test
    public void testDefensiveCopyingTrue() {
        IndexConfig config = IndexConfig.create(3).setDefensiveKeyCopy(true);
        KDTree<Entry> tree = KDTree.create(config);

        double[] point = new double[]{1, 2, 3};
        double[] pointOriginal = point.clone();
        Entry e = new Entry(point, 42);
        tree.insert(point, e);

        // Never do this! We just verify that the key is not copied.
        point[1] = 15;
        assertFalse(tree.contains(point));
        assertTrue(tree.contains(pointOriginal));
    }

    private void smokeTest(List<Entry> data) {
        int dim = data.get(0).p.length;
        IndexConfig config = IndexConfig.create(dim).setDefensiveKeyCopy(false);
        KDTree<Entry> tree = KDTree.create(config);
        for (Entry e : data) {
            tree.insert(e.p, e);
        }
//	    System.out.println(tree.toStringTree());
        for (Entry e : data) {
            if (!tree.contains(e.p)) {
                throw new IllegalStateException(Arrays.toString(e.p));
            }
        }

        for (Entry e : data) {
            // System.out.println("kNN query: " + e);
            PointIteratorKnn<Entry> iter = tree.queryKnn(e.p, N_DUP);
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
            PointIterator<Entry> iter = tree.query(e.p, e.p);
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
            if (!tree.contains(e.p)) {
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
