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
package org.zoodb.index.quadtree2;

public class QUtil {
	
	static final double EPS_MUL = 1.00000002;

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
			double radOuter = radiusOuter * EPS_MUL * 1.0000001;
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
			double radOuter = radiusOuter * EPS_MUL * 1.0000001;
			double radEncl = radiusEnclosed;
			if ((centerOuter[d]+radOuter) < (centerEnclosed[d]+radEncl) || 
					(centerOuter[d]-radOuter) > (centerEnclosed[d]-radEncl)) {
				return false;
			}
		}
		return true;
	}

	public static double distance(double[] p1, double[] p2) {
		double dist = 0;
		for (int i = 0; i < p1.length; i++) {
			double d = p1[i]-p2[i];
			dist += d * d;
		}
		return Math.sqrt(dist);
	}
	
	/**
	 * Calculates distance to center point of rectangle.
	 * @param p point
	 * @param rMin rectangle min
	 * @param rMax rectangle max
	 * @return distance to center point
	 */
	public static double distanceToRect(double[] p, double[] rMin, double[] rMax) {
		double dist = 0;
		for (int i = 0; i < p.length; i++) {
			double d = (rMin[i]+rMax[i])/2 - p[i];
			dist += d * d;
		}
		return Math.sqrt(dist);
	}
	
	/**
	 * Calculates distance to center point of rectangle.
	 * @param p point
	 * @param e rectangle
	 * @return distance to center point
	 */
	public static double distanceToRect(double[] p, QREntry<?> e) {
		return distanceToRect(p, e.lower(), e.upper());
	}
	
}
