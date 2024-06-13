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
package org.tinspin.index.qt2;

import org.junit.Test;
import org.tinspin.index.qthypercube2.QuadTreeKD2;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class Quadtree2Test {

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

        QuadTreeKD2<Integer> tree = QuadTreeKD2.create(2);
        for (int i = 0; i < data.length; i++) {
            System.out.println("===================");
            System.out.println(tree.toStringTree());
            tree.getStats();
            if (data[i][3] == 0) {
                tree.insert(Arrays.copyOf(data[i], 2), (int)data[i][2]);
            } else {
                tree.remove(Arrays.copyOf(data[i], 2), (int)data[i][2]);
            }
        }
    }

    @Test
    public void testIssue0040_rootExpansion() {
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
        };

        QuadTreeKD2<Integer> tree = QuadTreeKD2.create(2);
        for (int i = 0; i < data.length; i++) {
            tree.getStats();
            tree.insert(Arrays.copyOf(data[i], 2), (int)data[i][2]);
        }
        System.out.println(tree.toStringTree());

        // root test
        for (int i = 0; i < data.length; i++) {
            assertTrue(tree.contains(data[i]));
            assertTrue(tree.contains(data[i], (int)data[i][2]));
            assertEquals( (int)data[i][2], (int) tree.queryExact(data[i]));
        }
    }
}
