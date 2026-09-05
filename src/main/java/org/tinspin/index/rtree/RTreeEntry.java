/*
 * Copyright 2016 Tilmann Zaeschke
 *
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
package org.tinspin.index.rtree;

import static org.tinspin.index.Index.*;

import java.util.Arrays;

/**
 * R-Tree entry.
 *
 * @param <T> value type.
 */
public class RTreeEntry<T> extends BoxEntry<T> {

  /**
   * Create a new entry based on an axis aligned box.
   *
   * @param min Box minimum
   * @param max Box maximum
   * @param value The value associated with the box.
   * @param <T> Value type
   * @return New BoxEntry
   */
  public static <T> RTreeEntry<T> createBox(double[] min, double[] max, T value) {
    return new RTreeEntry<>(min, max, value);
  }

  /**
   * Create a new R-Tree entry based on a point coordinate.
   *
   * @param point The point.
   * @param value The value associated with the point.
   * @param <T> Value type
   * @return New PointEntry
   */
  public static <T> RTreeEntry<T> createPoint(double[] point, T value) {
    return new RTreeEntry<>(point, point, value);
  }

  RTreeEntry(double[] min, double[] max, T val) {
    super(min, max, val);
  }

  double calcOverlap(RTreeEntry<T> e) {
    double area = 1;
    for (int i = 0; i < min().length; i++) {
      double d = Math.min(max()[i], e.max()[i]) - Math.max(min()[i], e.min()[i]);
      if (d <= 0) {
        return 0;
      }
      area *= d;
    }
    return area;
  }

  /**
   * Check whether the current entry geometrically includes the rectangle defined by min2 and max2.
   *
   * @param min2 Rectangle min
   * @param max2 Rectangle max
   * @return Whether min2/max2 is included in the current entry.
   */
  public boolean checkInclusion(double[] min2, double[] max2) {
    for (int i = 0; i < min().length; i++) {
      if (min()[i] > min2[i] || max()[i] < max2[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check min/max identity of this entry with min/max.
   *
   * @param min2 min
   * @param max2 max
   * @return true if local min/max is equal to given min/max.
   */
  public boolean checkExactMatch(double[] min2, double[] max2) {
    for (int i = 0; i < min().length; i++) {
      if (min()[i] != min2[i] || max()[i] != max2[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Calculate area of this entry
   *
   * @return area or volume
   */
  public double calcArea() {
    double area = 1;
    for (int i = 0; i < min().length; i++) {
      double d = max()[i] - min()[i];
      area *= d;
    }
    return area;
  }

  /**
   * Expand entry to fit e1 and e2
   *
   * @param e1 entry 1
   * @param e2 entry 2
   */
  public void setToCover(RTreeEntry<T> e1, RTreeEntry<T> e2) {
    for (int i = 0; i < min().length; i++) {
      min()[i] = Math.min(e1.min()[i], e2.min()[i]);
      max()[i] = Math.max(e1.max()[i], e2.max()[i]);
    }
  }

  /**
   * Calculate volume of box.
   *
   * @param e entry
   * @return volume
   */
  public static double calcVolume(RTreeEntry<?> e) {
    return calcVolume(e.min(), e.max());
  }

  /**
   * Calculate volume of box.
   *
   * @param min min
   * @param max max
   * @return volume
   */
  public static double calcVolume(double[] min, double[] max) {
    double v = 1;
    for (int d = 0; d < min.length; d++) {
      v *= max[d] - min[d];
    }
    return v;
  }

  /**
   * Calculate bounding box.
   *
   * @param entries entries
   * @param start first entry to check
   * @param end last entry to check
   * @param minOut new BB min
   * @param maxOut new BB max
   */
  public static void calcBoundingBox(
      RTreeEntry<?>[] entries, int start, int end, double[] minOut, double[] maxOut) {
    System.arraycopy(entries[start].min(), 0, minOut, 0, minOut.length);
    System.arraycopy(entries[start].max(), 0, maxOut, 0, maxOut.length);
    for (int i = start + 1; i < end; i++) {
      for (int d = 0; d < minOut.length; d++) {
        minOut[d] = Math.min(minOut[d], entries[i].min()[d]);
        maxOut[d] = Math.max(maxOut[d], entries[i].max()[d]);
      }
    }
  }

  /**
   * Check for overlap of min/max with e
   *
   * @param min1 min
   * @param max1 max
   * @param min2 min
   * @param max2 max
   * @return the overlapping area/volume
   */
  public static double calcOverlap(double[] min1, double[] max1, double[] min2, double[] max2) {
    double area = 1;
    for (int i = 0; i < min1.length; i++) {
      double d = Math.min(max1[i], max2[i]) - Math.max(min1[i], min2[i]);
      if (d <= 0) {
        return 0;
      }
      area *= d;
    }
    return area;
  }

  /**
   * Check for overlap of min/max with e
   *
   * @param min min
   * @param max max
   * @param e e
   * @return true if the two boxes overlap
   */
  public static boolean checkOverlap(double[] min, double[] max, RTreeEntry<?> e) {
    for (int i = 0; i < min.length; i++) {
      if (min[i] > e.max()[i] || max[i] < e.min()[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check for inclusion of "in" inside of "out".
   *
   * @param minOut outer min
   * @param maxOut outer max
   * @param minIn inner min
   * @param maxIn inner max
   * @return true if "in" is inside of "out"
   */
  public static boolean calcIncludes(
      double[] minOut, double[] maxOut, double[] minIn, double[] maxIn) {
    for (int i = 0; i < minOut.length; i++) {
      if (minOut[i] > minIn[i] || maxOut[i] < maxIn[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Calculates the bounding boxes and the estimated dead space.
   *
   * @param entries Entries
   * @param start start
   * @param end end
   * @param minOut min return
   * @param maxOut max return
   * @return estimated dead space
   */
  public static double calcDeadspace(
      RTreeEntry<?>[] entries, int start, int end, double[] minOut, double[] maxOut) {
    Arrays.fill(minOut, Double.POSITIVE_INFINITY);
    Arrays.fill(maxOut, Double.NEGATIVE_INFINITY);
    double volumeSum = 0;
    for (int i = start; i < end; i++) {
      double v = 1;
      for (int d = 0; d < minOut.length; d++) {
        minOut[d] = Math.min(minOut[d], entries[i].min()[d]);
        maxOut[d] = Math.max(maxOut[d], entries[i].max()[d]);
        v *= entries[i].max()[d] - entries[i].min()[d];
      }
      volumeSum += v;
    }
    // The dead volume does not consider overlapping boundary boxes of children. The
    // resulting deadspace estimate can therefore be negative.
    return calcVolume(minOut, maxOut) - volumeSum;
  }

  /**
   * Calculate margin (L1 distance)
   *
   * @param min2 min
   * @param max2 max
   * @return margin
   */
  public static double calcMargin(double[] min2, double[] max2) {
    double d = 0;
    for (int i = 0; i < min2.length; i++) {
      d += max2[i] - min2[i];
    }
    return d;
  }

  /**
   * Calculate distance between the centers of to entries.
   *
   * @param e1 entry 1
   * @param e2 entry 2
   * @return distance
   */
  public static double calcCenterDistance(RTreeEntry<?> e1, RTreeEntry<?> e2) {
    double[] min1 = e1.min();
    double[] max1 = e1.max();
    double[] min2 = e2.min();
    double[] max2 = e2.max();
    double dist = 0;
    for (int i = 0; i < min1.length; i++) {
      double d = (min1[i] + max1[i]) - (min2[i] + max2[i]);
      d *= 0.5;
      dist += d * d;
    }
    return Math.sqrt(dist);
  }

  @Override
  public String toString() {
    double[] len = new double[min().length];
    Arrays.setAll(len, i -> (max()[i] - min()[i]));
    return Arrays.toString(min())
        + "/"
        + Arrays.toString(max())
        + ";len="
        + Arrays.toString(len)
        + ";v="
        + value();
  }

  /**
   * Set entry to be a copy of "e".
   *
   * @param e other entry
   */
  protected void set(RTreeEntry<T> e) {
    super.set(e.min(), e.max(), e.value());
  }
}
