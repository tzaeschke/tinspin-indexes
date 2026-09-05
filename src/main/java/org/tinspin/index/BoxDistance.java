/*
 * Copyright 2016 Tilmann Zaeschke
 * Modification Copyright 2017 Christophe Schmaltz
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
package org.tinspin.index;

/** Interface and standard implementations of Box distances. */
@FunctionalInterface
public interface BoxDistance {

  /** Distance based on box center points. */
  BoxDistance CENTER = BoxDistance::centerDistance;

  /** Distance based on closest points on box edges. */
  BoxDistance EDGE = BoxDistance::edgeDistance;

  /**
   * Distance between point and min-max-box.
   *
   * @param point A point
   * @param min Minimum corner of axis aligned box
   * @param max Maximum corner of axis aligned box
   * @return Distance between point and box
   */
  double dist(double[] point, double[] min, double[] max);

  /**
   * Some algorithm use this method on the entries containing user supplied values. This can be
   * overridden if the min/max coordinates only represent the bounding-box of the object.
   *
   * <p>If your entry is actually a sphere, a car, an human or a cat, you may need this.
   *
   * @param center a point
   * @param entry a box
   * @return distance between point and box
   */
  default double dist(double[] center, Index.BoxEntry<?> entry) {
    return dist(center, entry.min(), entry.max());
  }

  /**
   * Special wrapper class which takes the inverse of the given distance function. Can be used to
   * get the farthest neighbors using the nearest neighbor algorithm.
   */
  class FarthestNeighbor implements BoxDistance {
    private static final double EPSILON = 2 * Double.MIN_VALUE;
    private final BoxDistance dist;

    /**
     * Constructor.
     *
     * @param dist distance function
     */
    public FarthestNeighbor(BoxDistance dist) {
      this.dist = dist;
    }

    @Override
    public double dist(double[] center, double[] min, double[] max) {
      double d = dist.dist(center, min, max);
      if (d < EPSILON) {
        // no divide by zero
        return Double.POSITIVE_INFINITY;
      }
      return 1 / d;
    }

    @Override
    public double dist(double[] center, Index.BoxEntry<?> entry) {
      double d = dist.dist(center, entry);
      if (d < EPSILON) {
        return Double.POSITIVE_INFINITY;
      }
      return 1 / d;
    }
  }

  /**
   * Implementation of center distance. Calculates Euclidean distance between a point and the center
   * points of a box.
   *
   * @param center a point
   * @param min min values of box
   * @param max max values of box
   * @return distance
   */
  static double centerDistance(double[] center, double[] min, double[] max) {
    double dist = 0;
    for (int i = 0; i < center.length; i++) {
      double d = (min[i] + max[i]) * 0.5 - center[i];
      dist += d * d;
    }
    return Math.sqrt(dist);
  }

  /**
   * Implementation of edge distance. Calculates Euclidean distance between a point and the closest
   * edge point a box.
   *
   * @param center a point
   * @param min min values of box
   * @param max max values of box
   * @return distance
   */
  static double edgeDistance(double[] center, double[] min, double[] max) {
    double dist = 0;
    for (int i = 0; i < center.length; i++) {
      double d = 0;
      if (min[i] > center[i]) {
        d = min[i] - center[i];
      } else if (max[i] < center[i]) {
        d = center[i] - max[i];
      }
      dist += d * d;
    }
    return Math.sqrt(dist);
  }

  /** Edge distance implementation. */
  class EdgeDistance {
    final PointDistance distFn;

    /**
     * Constructor.
     *
     * @param distFn distance function.
     */
    public EdgeDistance(PointDistance distFn) {
      this.distFn = distFn;
    }

    /**
     * Distance calculator between a point and the closest point on the closest edge of the box
     * defined by min and max.
     *
     * @param center point
     * @param min box min
     * @param max box max
     * @return distance
     */
    public double edgeDistance(double[] center, double[] min, double[] max) {
      double[] dist = new double[center.length];
      for (int i = 0; i < center.length; i++) {
        double d = 0;
        if (min[i] > center[i]) {
          d = min[i] - center[i];
        } else if (max[i] < center[i]) {
          d = center[i] - max[i];
        }
        dist[i] = d;
      }
      return distFn.dist(dist, center);
    }
  }
}
