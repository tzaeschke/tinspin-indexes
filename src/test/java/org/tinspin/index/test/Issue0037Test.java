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
import org.tinspin.index.qthypercube.QuadTreeKD;
import org.tinspin.index.qthypercube2.QuadTreeKD2;
import org.tinspin.index.qtplain.QuadTreeKD0;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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

            tree.remove(new double[]{d1, d2});
        }
        {
            String[] sp = reader.readLine().split(" ");
            double d1 = Double.parseDouble(sp[0]), d2 = Double.parseDouble(sp[1]);
            var node = tree.query1nn(new double[]{d1, d2});
            boolean has = tree.contains(node.point());
            System.out.println(n);

            System.out.println(has); // Should print true, but prints false with QuadTreeKD2
            assertTrue(has);
        }
    }
}
