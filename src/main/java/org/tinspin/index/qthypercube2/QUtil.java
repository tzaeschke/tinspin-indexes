/*
 * Copyright 2016-2017 Tilmann Zaeschke
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
package org.tinspin.index.qthypercube2;

import org.tinspin.index.PointDistanceFunction;

class QUtil {

	static final double EPS_MUL = 1.000000001;

	private QUtil() {}

	public static boolean isPointEnclosed(double[] point,
			double[] min, double[] max) {
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
		for (int d = 0; d < center.length; d++) {
			if (point[d] < center[d] - radius || point[d] >= center[d] + radius) {
				return false;
			}
		}
		return true;
	}

	public static boolean isPointEqual(double[] p1, double[] p2) {
		for (int d = 0; d < p1.length; d++) {
			if (p1[d] != p2[d]) {
				return false;
			}
		}
		return true;
	}

	public static boolean overlap(double[] min, double[] max, double[] min2, double[] max2) {
		for (int d = 0; d < min.length; d++) {
			if (max[d] < min2[d] || min[d] > max2[d]) {
				return false;
			}
		}
		return true;
	}

	public static boolean overlap(double[] min, double[] max, double[] center, double radius) {
		for (int d = 0; d < min.length; d++) {
			if (max[d] < center[d]-radius || min[d] > center[d]+radius) {
				return false;
			}
		}
		return true;
	}

	public static boolean isRectEnclosed(double[] minEnclosed, double[] maxEnclosed,
			double[] minOuter, double[] maxOuter) {
		for (int d = 0; d < minOuter.length; d++) {
			if (maxOuter[d] < maxEnclosed[d] || minOuter[d] > minEnclosed[d]) {
				return false;
			}
		}
		return true;
	}

	public static boolean isNodeEnclosed(double[] centerEnclosed, double radiusEnclosed,
										 double[] centerOuter, double radiusOuter) {
		for (int d = 0; d < centerOuter.length; d++) {
			if ((centerOuter[d] + radiusOuter) < (centerEnclosed[d] + radiusEnclosed) ||
					(centerOuter[d] - radiusOuter) > (centerEnclosed[d] - radiusEnclosed)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Calculates distance to the edge of a node.
	 * @param point the point
	 * @param nodeCenter the center of the node
	 * @param nodeRadius radius of the node
	 * @return distance to edge of the node or 0 if the point is inside the node
	 */
	static double distToRectNode(double[] point, double[] nodeCenter, double nodeRadius, PointDistanceFunction distFn) {
		double[] dist = new double[point.length];
		for (int i = 0; i < point.length; i++) {
			double d = point[i];
			if (point[i] > nodeCenter[i] + nodeRadius) {
				d = nodeCenter[i] + nodeRadius;
			} else if (point[i] < nodeCenter[i] - nodeRadius) {
				d = nodeCenter[i] - nodeRadius;
			}
			dist[i] = d;
		}
		return distFn.dist(dist, point);
	}
}
