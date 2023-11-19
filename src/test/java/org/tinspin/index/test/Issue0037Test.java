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
import org.tinspin.index.PointMap;
import org.tinspin.index.Stats;
import org.tinspin.index.qthypercube.QuadTreeKD;
import org.tinspin.index.qthypercube2.QuadTreeKD2;
import org.tinspin.index.qtplain.QuadTreeKD0;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public class Issue0037Test {

    @Test
    public void testQT() throws IOException {
        QuadTreeKD<Integer> tree = QuadTreeKD.create(2);
        testTree(tree);
    }

    @Test
    public void testQT0() throws IOException {
        QuadTreeKD0<Integer> tree = QuadTreeKD0.create(2);
        testTree(tree);
    }

    @Test
    public void testQT2() throws IOException {
        QuadTreeKD2<Integer> tree = QuadTreeKD2.create(2);
        testTree(tree);
    }

    /**
     * p=20.7747859954834, -335.053844928741
     * knn=[13.7747859954834, -335.053844928741]
     * 1476
     * true
     * p=20.7747859954834, -335.053844928741
     * knn=[24.7677965164185, -335.507710456848]
     * 1476
     * false
     *
     *
     * p=20.7747859954834, -335.053844928741
     * knn=[13.7747859954834, -335.053844928741]
     * 1476
     * true
     */
    private void testTree(PointMap<Integer> tree) throws IOException {
        File file = new File("src/test/resources/issue0037.txt");

        BufferedReader reader = new BufferedReader(new FileReader(file));
        int n = Integer.parseInt(reader.readLine());
        for (int i = 0; i < n; i++) {
            String[] sp = reader.readLine().split(" ");
            double d1 = Double.parseDouble(sp[0]), d2 = Double.parseDouble(sp[1]);

            tree.insert(new double[]{d1, d2}, i);
        }
        int k = Integer.parseInt(reader.readLine());
        for (int i = 0; i < k; i++) {
            String[] sp = reader.readLine().split(" ");
            double d1 = Double.parseDouble(sp[0]), d2 = Double.parseDouble(sp[1]);
            System.out.println("Remove: " + d1 + ", " + d2);
            tree.remove(new double[]{d1, d2});
        }
        Stats stats = tree.getStats();
        System.out.println("Stats: n=" + stats.nEntries);
        {
            String[] sp = reader.readLine().split(" ");
            double d1 = Double.parseDouble(sp[0]), d2 = Double.parseDouble(sp[1]);
            System.out.println("p=" + d1 + ", " + d2);
            boolean has13_335 = tree.contains(new double[]{13.7747859954834, -335.053844928741});
            System.out.println("contains: 13.7747859954834, -335.053844928741 ? " + has13_335);
            var node = tree.query1nn(new double[]{d1, d2});
            System.out.println("knn=" + Arrays.toString(node.point()));
            boolean has = tree.contains(node.point());
            System.out.println(n);

            System.out.println(has); // Should print true, but prints false with QuadTreeKD2
            assertTrue(has);
        }
    }

    private void testTree2(PointMap<Integer> tree) throws IOException {
        Random R = new Random(0);
        int n = 100;
        int k = n/2;

        ArrayList<double[]> array = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double d1 = R.nextDouble(), d2 = R.nextDouble();
            double[] p = new double[]{d1, d2};
            array.add(p);
            tree.insert(p, i);
        }
        for (int i = 0; i < k; i++) {
            double d1 = R.nextDouble(), d2 = R.nextDouble();
            tree.remove(new double[]{d1, d2});
        }
//        {
//            String[] sp = reader.readLine().split(" ");
//            double d1 = Double.parseDouble(sp[0]), d2 = Double.parseDouble(sp[1]);
//            var node = tree.query1nn(new double[]{d1, d2});
//            boolean has = tree.contains(node.point());
//            System.out.println(n);
//
//            System.out.println(has); // Should print true, but prints false with QuadTreeKD2
//            assertTrue(has);
//        }
        for (int i = 0; i < n; i++) {
                var node = tree.query1nn(array.get(i));
                boolean has = tree.contains(node.point());
                System.out.println(n);

                System.out.println(has); // Should print true, but prints false with QuadTreeKD2
                assertTrue(has);
        }
    }
}
