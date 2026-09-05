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
package org.tinspin.covetree;

import static org.tinspin.index.Index.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import org.tinspin.index.PointDistance;
import org.tinspin.index.covertree.CoverTree;
import org.tinspin.index.util.Refs;

public class CoverTreeTest {

  @Test
  public void smokeTestShort() {
    double[][] pointList = {{2, 3}, {5, 4}, {9, 6}, {4, 7}}; // , {8,1}, {7,2}};
    smokeTest(pointList);
  }

  @Test
  public void smokeTestDupl() {
    double[][] pointList = {{2, 3}, {2, 3}, {2, 3}, {2, 3}, {2, 3}, {2, 3}};
    smokeTest(pointList);
  }

  @Test
  public void smokeTest2D_0() {
    double[][] pointList = new double[20][2];
    Random rnd = new Random(0);
    for (double[] p : pointList) {
      Arrays.setAll(
          p,
          i -> {
            return (double) rnd.nextInt(100);
          });
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest2D_1() {
    double[][] pointList = new double[20][2];
    Random rnd = new Random(1);
    for (double[] p : pointList) {
      Arrays.setAll(
          p,
          i -> {
            return (double) rnd.nextInt(100);
          });
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest2D_245() {
    double[][] pointList = new double[5][2];
    Random rnd = new Random(245);
    for (double[] p : pointList) {
      Arrays.setAll(
          p,
          i -> {
            return (double) rnd.nextInt(100);
          });
    }
    smokeTest(pointList);
  }

  /** Tests handling of all points being on a line, i.e. correct handling of <=, etc. */
  @Test
  public void smokeTest2D_Line() {
    double[][] pointList = new double[10000][3];
    int n = 0;
    for (double[] p : pointList) {
      p[0] = n % 3;
      p[1] = n++;
      p[2] = n % 5;
    }
    List<double[]> list = Arrays.asList(pointList);
    Collections.shuffle(list);
    pointList = list.toArray(pointList);
    smokeTest(pointList);
  }

  @Test
  public void smokeTest2D_LineR() {
    for (int r = 0; r < 1000; r++) {
      // System.out.println("r=" + r);
      double[][] pointList = new double[20][3];
      int n = 0;
      for (double[] p : pointList) {
        p[0] = n % 3;
        p[1] = n++;
        p[2] = n % 5;
      }
      List<double[]> list = Arrays.asList(pointList);
      Collections.shuffle(list, new Random(r));
      pointList = list.toArray(pointList);
      smokeTest(pointList);
    }
  }

  @Test
  public void smokeTest2D_LineR141() {
    double[][] pointList = new double[20][3];
    int n = 0;
    for (double[] p : pointList) {
      p[0] = n % 3;
      p[1] = n++;
      p[2] = n % 5;
    }
    List<double[]> list = Arrays.asList(pointList);
    Collections.shuffle(list, new Random(141));
    pointList = list.toArray(pointList);
    smokeTest(pointList);
  }

  @Test
  public void smokeTest5D() {
    double[][] pointList = new double[20][5];
    Random rnd = new Random(0);
    for (double[] p : pointList) {
      Arrays.setAll(
          p,
          i -> {
            return (double) rnd.nextInt(100);
          });
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest1D_Large() {
    double[][] pointList = new double[100_000][1];
    Random rnd = new Random(0);
    for (double[] p : pointList) {
      Arrays.setAll(
          p,
          i -> {
            return (double) rnd.nextInt(100);
          });
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest3D_Large() {
    double[][] pointList = new double[100_000][3];
    Random rnd = new Random(0);
    for (double[] p : pointList) {
      Arrays.setAll(
          p,
          i -> {
            return (double) rnd.nextInt(100);
          });
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest10D_Large() {
    double[][] pointList = new double[10_000][10];
    Random rnd = new Random(0);
    for (double[] p : pointList) {
      Arrays.setAll(
          p,
          i -> {
            return (double) rnd.nextInt(100);
          });
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest2D_10_1() {
    double[][] pointList = new double[10][2];
    Random rnd = new Random(1);
    for (double[] p : pointList) {
      Arrays.setAll(p, i -> rnd.nextDouble() * 10 - 5);
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest2D_10_5() {
    double[][] pointList = new double[10][2];
    Random rnd = new Random(5);
    for (double[] p : pointList) {
      Arrays.setAll(p, i -> rnd.nextDouble() * 10 - 5);
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest25D_Large() {
    for (int r = 0; r < 2000; r++) {
      // System.out.println("r=" + r);
      double[][] pointList = new double[10][2];
      Random rnd = new Random(r);
      for (double[] p : pointList) {
        Arrays.setAll(p, i -> rnd.nextDouble() * 10 - 5);
      }
      smokeTest(pointList);
    }
  }

  @Test
  public void smokeTest2D_Reinsert0() {
    double[][] pointList = new double[4][2];
    Random rnd = new Random(0);
    for (double[] p : pointList) {
      Arrays.setAll(p, i -> rnd.nextDouble() * 10 - 5);
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest2D_Reinsert5() {
    double[][] pointList = new double[5][2];
    Random rnd = new Random(275);
    for (double[] p : pointList) {
      Arrays.setAll(p, i -> rnd.nextDouble() * 10 - 5);
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest2D_Reinsert398() {
    double[][] pointList = new double[5][2];
    Random rnd = new Random(398);
    for (double[] p : pointList) {
      Arrays.setAll(p, i -> rnd.nextDouble() * 10 - 5);
    }
    smokeTest(pointList);
  }

  @Test
  public void smokeTest3D_Bulk() {
    for (int r = 0; r < 1000; r++) {
      // System.out.println("r=" + r);
      double[][] pointList = new double[100][2];
      Random rnd = new Random(r);
      for (double[] p : pointList) {
        Arrays.setAll(p, i -> rnd.nextDouble() * 10 - 5);
      }
      smokeTestBulk(pointList);
    }
  }

  private void smokeTest(double[][] pointList) {
    int dim = pointList[0].length;
    CoverTree<double[]> tree = CoverTree.create(dim);
    for (double[] data : pointList) {
      tree.insert(data, data);
      // System.out.println(tree.toStringTree());
      // tree.check();
    }

    smokeTestAccess(tree, pointList);
  }

  @SuppressWarnings("unchecked")
  private void smokeTestBulk(double[][] pointList) {
    PointEntry<double[]>[] points = Refs.newArray(PointEntry.class, pointList.length);
    for (int i = 0; i < pointList.length; i++) {
      PointEntry<double[]> p = CoverTree.create(pointList[i], pointList[i]);
      points[i] = p;
    }
    CoverTree<double[]> tree = CoverTree.create(points, 1.3, PointDistance.L2);
    smokeTestAccess(tree, pointList);
  }

  private void smokeTestAccess(CoverTree<double[]> tree, double[][] pointList) {
    tree.check();
    //	    System.out.println(tree.toStringTree());
    for (double[] key : pointList) {
      if (!tree.contains(key)) {
        throw new IllegalStateException(Arrays.toString(key));
      }
    }

    for (double[] key : pointList) {
      //			System.out.println("1NN query: " + Arrays.toString(key));
      PointEntryKnn<double[]> p = tree.query1nn(key);
      if (p == null) {
        throw new IllegalStateException("1NN() failed: " + Arrays.toString(key));
      }
      double[] answer = p.point();
      if (answer != key && !Arrays.equals(answer, key)) {
        throw new IllegalStateException(
            "Expected " + Arrays.toString(key) + " but got " + Arrays.toString(answer));
      }
    }

    for (double[] key : pointList) {
      //			System.out.println("kNN query: " + Arrays.toString(key));
      PointIteratorKnn<double[]> iter = tree.queryKnn(key, 1);
      if (!iter.hasNext()) {
        throw new IllegalStateException("kNN() failed: " + Arrays.toString(key));
      }
      double[] answer = iter.next().point();
      if (answer != key && !Arrays.equals(answer, key)) {
        throw new IllegalStateException(
            "Expected " + Arrays.toString(key) + " but got " + Arrays.toString(answer));
      }
    }

    //		for (double[] key : pointList) {
    ////			System.out.println(tree.toStringTree());
    ////			System.out.println("Removing: " + Arrays.toString(key));
    //			if (!tree.containsExact(key)) {
    //				throw new IllegalStateException("containsExact() failed: " + Arrays.toString(key));
    //			}
    //			double[] answer = tree.remove(key);
    //			if (answer != key && !Arrays.equals(answer, key)) {
    //				throw new IllegalStateException("Expected " + Arrays.toString(key) + " but got " +
    // Arrays.toString(answer));
    //			}
    //		}
  }
}
