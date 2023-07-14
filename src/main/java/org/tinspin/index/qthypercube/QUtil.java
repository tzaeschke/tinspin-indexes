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
package org.tinspin.index.qthypercube;

import org.tinspin.index.PointDistanceFunction;

public class QUtil {
	
	static final double EPS_MUL = 1.000000001;

	public static boolean isPointEnclosed(double[] point,
			double[] min, double[] max) {
		for (int d = 0; d < min.length; d++) {
			if (point[d] < min[d] || point[d] > max[d]) {
				return false;
			}
		}
		return true;
	}

	public static boolean isPointEnclosed(double[] point,
			double[] center, double radius) {
		for (int d = 0; d < center.length; d++) {
			if (point[d] < center[d]-radius || point[d] > center[d]+radius) {
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

	public static boolean isRectEqual(double[] p1L, double[] p1U, double[] p2L, double[] p2U) {
		return isPointEqual(p1L, p2L) && isPointEqual(p1U, p2U);
	}

	public static <T> boolean isRectEqual(QREntry<T> e, double[] keyL, double[] keyU) {
		return isRectEqual(e.lower(), e.upper(), keyL, keyU);
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

	public static boolean isRectEnclosed(double[] minEnclosed, double[] maxEnclosed,
			double[] centerOuter, double radiusOuter) {
		for (int d = 0; d < centerOuter.length; d++) {
			double radOuter = radiusOuter;
			if ((centerOuter[d]+radOuter) < maxEnclosed[d] || 
					(centerOuter[d]-radOuter) > minEnclosed[d]) {
				return false;
			}
		}
		return true;
	}

	public static boolean isRectEnclosed(double[] centerEnclosed, double radiusEnclosed,
			double[] centerOuter, double radiusOuter) {
		for (int d = 0; d < centerOuter.length; d++) {
			double radOuter = radiusOuter;
			double radEncl = radiusEnclosed;
			if ((centerOuter[d]+radOuter) < (centerEnclosed[d]+radEncl) || 
					(centerOuter[d]-radOuter) > (centerEnclosed[d]-radEncl)) {
				return false;
			}
		}
		return true;
	}

	@Deprecated // Not really, I guess it's ok for adjustRoot()
	public static double distance(double[] p1, double[] p2) {
		double dist = 0;
		for (int i = 0; i < p1.length; i++) {
			double d = p1[i]-p2[i];
			dist += d * d;
		}
		return Math.sqrt(dist);
	}

	// TODO remove
//	/**
//	 * Calculates distance to center point of rectangle.
//	 * @param p point
//	 * @param rMin rectangle min
//	 * @param rMax rectangle max
//	 * @return distance to center point
//	 */
//	public static double distToRectCenter(double[] p, double[] rMin, double[] rMax) {
//		double dist = 0;
//		for (int i = 0; i < p.length; i++) {
//			double d = (rMin[i]+rMax[i])/2. - p[i];
//			dist += d * d;
//		}
//		return Math.sqrt(dist);
//	}
	
//	/**
//	 * Calculates distance to center point of rectangle.
//	 * @param p point
//	 * @param e rectangle
//	 * @return distance to center point
//	 */
//	public static double distToRectCenter(double[] p, QREntry<?> e) {
//		return distToRectCenter(p, e.lower(), e.upper());
//	}
	
	/**
	 * Calculates distance to the edge of rectangle.
	 * @param p point
	 * @param rMin rectangle min
	 * @param rMax rectangle max
	 * @return distance to edge
	 */
	@Deprecated
	static double distToRectEdge(double[] center, double[] rLower, double[] rUpper) {
		double dist = 0;
		for (int i = 0; i < center.length; i++) {
			double d = 0;
			if (center[i] > rUpper[i]) {
				d = center[i] - rUpper[i];
			} else if (center[i] < rLower[i]) {
				d = rLower[i] - center[i];
			}
			dist += d*d;
		}
		return Math.sqrt(dist);
	}
	
	/**
	 * Calculates distance to edge of rectangle.
	 * @param p point
	 * @param e rectangle
	 * @return distance to edge point
	 */
	@Deprecated
	public static double distToRectEdge(double[] p, QREntry<?> e) {
		return distToRectEdge(p, e.lower(), e.upper());
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
