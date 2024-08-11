/*
 * Copyright 2016-2024 Tilmann Zaeschke
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
package org.tinspin.index.balltree;

import org.tinspin.index.Index;
import org.tinspin.index.PointDistance;

import java.util.ArrayList;
import java.util.Arrays;

class BTUtil {

    static final double EPS_MUL = 1.000000001;

    private BTUtil() {
    }

    public static boolean isPointEnclosed(double[] point, double[] min, double[] max) {
        for (int d = 0; d < min.length; d++) {
            if (point[d] < min[d] || point[d] > max[d]) {
                return false;
            }
        }
        return true;
    }


    /**
     * The tests for inclusion with UPPER BOUNDARY EXCLUSIVE!
     * I.e. it firs only if point is SMALLER than (center + radius).
     */
    public static boolean fitsIntoNode(double[] point, double[] center, double radius) {
        return PointDistance.l2(point, center) <= radius;
    }

    public static boolean isPointEqual(double[] p1, double[] p2) {
        for (int d = 0; d < p1.length; d++) {
            if (p1[d] != p2[d]) {
                return false;
            }
        }
        return true;
    }

//    public static boolean isRectEqual(double[] p1L, double[] p1U, double[] p2L, double[] p2U) {
//        return isPointEqual(p1L, p2L) && isPointEqual(p1U, p2U);
//    }
//
//    public static <T> boolean isRectEqual(BoxEntry<T> e, double[] keyL, double[] keyU) {
//        return isRectEqual(e.min(), e.max(), keyL, keyU);
//    }
//
//    public static <T> boolean isRectEqual(BoxEntry<T> e, BoxEntry<T> e2) {
//        return isRectEqual(e.min(), e.max(), e2.min(), e2.max());
//    }
//
//    public static boolean overlap(double[] min, double[] max, double[] min2, double[] max2) {
//        for (int d = 0; d < min.length; d++) {
//            if (max[d] < min2[d] || min[d] > max2[d]) {
//                return false;
//            }
//        }
//        return true;
//    }

    public static boolean overlap(double[] min, double[] max, double[] center, double radius) {
        double[] p = new double[min.length];
        for (int d = 0; d < min.length; d++) {
            if (center[d] <= min[d]) {
                p[d] = min[d];
            } else if (center[d] >= max[d]) {
                p[d] = max[d];
            } else {
                p[d] = center[d];
            }

        }
        // TODO sqr-dist?
        return PointDistance.l2(p, center) <= radius;
    }

//    public static boolean isRectEnclosed(double[] minEnclosed, double[] maxEnclosed, double[] minOuter, double[] maxOuter) {
//        for (int d = 0; d < minOuter.length; d++) {
//            if (maxOuter[d] < maxEnclosed[d] || minOuter[d] > minEnclosed[d]) {
//                return false;
//            }
//        }
//        return true;
//    }
//
//    /**
//     * The tests for inclusion with UPPER BOUNDARY EXCLUSIVE!
//     * I.e. it firs only if maxEnclosed is SMALLER than (center + radius).
//     */
//    public static boolean fitsIntoNode(double[] minEnclosed, double[] maxEnclosed, double[] centerNode, double radiusNode) {
//        double r2 = 0;
//        for (int d = 0; d < centerNode.length; d++) {
//            double r = centerNode[d] -
//            r2
//            if ((centerNode[d] + radiusNode) <= maxEnclosed[d] || (centerNode[d] - radiusNode) > minEnclosed[d]) {
//                return false;
//            }
//        }
//        return true;
//    }

    public static boolean isNodeEnclosed(double[] centerEnclosed, double radiusEnclosed, double[] centerOuter, double radiusOuter) {
        return PointDistance.l2(centerEnclosed, centerOuter) + radiusEnclosed <= radiusOuter;
    }

    public static <T> double[][] orderCoordinates(ArrayList<Index.PointEntry<T>> points) {
        if (points.isEmpty()) {
            return new double[0][0];
        }
        int dim = points.get(0).point().length;
        double[][] sorted = new double[dim][points.size()];
        for (int i = 0; i < points.size(); i++) {
            double[] v = points.get(i).point();
            for (int d = 0; d < dim; d++) {
                sorted[d][i] = v[d];
            }
        }
        for (int d = 0; d < dim; d++) {
            Arrays.sort(sorted[d]);
        }
        return sorted;
    }

    public static <T> double calcBoundingSphere(ArrayList<Index.PointEntry<T>> points, double[] center) {
		PointDistance dist = PointDistance.L2;
        // TODO alternative approach:
        //  - use orderCoordinates -> avg min/max -> center point.
        //  - Increase radius until all points are included.
        //  -> Traverses all points 2 times. -> 1 x min/max calculation + 1x distance calculation

        // Default approach: Ritter's bounding sphere Algorithm
		//  - random point, then find furthest, then find furthest again -> center is halfway distance
        //  - Traverse all points again to ensure they are all included
		//    Adjust radius and center. -> Center is moved by half of radius adjustment
		//    -> This covers the worst case where another extreme point is exactly on the other side of the center
        //  -> traverse all points 3 times. -> 3 x distance calculation
        double maxDist = -1;
        int posMaxDist = 0;
        // initial random point -> find furthest neighbor
        double[] p0 = points.get(0).point();
        for (int i = 1; i < points.size(); i++) {
            double d = dist.dist(p0, points.get(i));
            if (d > maxDist) {
                maxDist = d;
                posMaxDist = i;
            }
        }
        double[] pFurthest0 = points.get(posMaxDist).point();

        maxDist = -1;
        for (int i = 0; i < points.size(); i++) {
            double d = dist.dist(pFurthest0, points.get(i));
            if (d > maxDist) {
                maxDist = d;
                posMaxDist = i;
            }
        }
        double[] pFurthest1 = points.get(posMaxDist).point();
		double radius = maxDist / 2.0;

		// center
        int dim = pFurthest0.length;
        for (int d = 0; d < dim; d++) {
            center[d] = (pFurthest0[d] + pFurthest1[d]) / 2.;
        }

		for (int i = 0; i < points.size(); i++) {
			double d = dist.dist(center, points.get(i));
			if (d > radius) {
				radius = d;
				// TODO adjust center, see https://www.researchgate.net/profile/Jack-Ritter/publication/242453691_An_Efficient_Bounding_Sphere/links/56e9d24e08ae95bddc2a2358/An-Efficient-Bounding-Sphere
				// double delta = (d - radius) / 2.;
				// radius += delta;
			}
		}
		return radius;
    }
}
