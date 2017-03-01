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

@FunctionalInterface
public interface DistanceFunction {

	public static CenterDistance CENTER = new CenterDistance();
	public static EdgeDistance EDGE = new EdgeDistance();
	public static DistanceFunction CENTER_SQUARE = DistanceFunction::centerSquareDistance;
	public static DistanceFunction EDGE_SQUARE = DistanceFunction::edgeSquareDistance;

	double dist(double[] center, double[] min, double[] max);
	
	public static class CenterDistance implements DistanceFunction {

		@Override
		public double dist(double[] center, double[] min, double[] max) {
			double dist = 0;
			for (int i = 0; i < center.length; i++) {
				double d = (min[i] + max[i]) * 0.5 - center[i];
				dist += d*d;
			}
			return Math.sqrt(dist);
		}
	}
	
	public static class EdgeDistance implements DistanceFunction {

		@Override
		public double dist(double[] center, double[] min, double[] max) {
			double dist = 0;
			for (int i = 0; i < center.length; i++) {
				double d = 0;
				if (min[i] > center[i]) {
					d = min[i] - center[i];
				} else if (max[i] < center[i]) {
					d = center[i] - max[i];
				}
				dist += d*d;
			}
			return Math.sqrt(dist);
		}
	}
	
	/**
	 * The square root is costly, and generally not required for sorting.
	 */
	public static double centerSquareDistance(double[] center, double[] min, double[] max) {
		double dist = 0;
		for (int i = 0; i < center.length; i++) {
			double d = (min[i] + max[i]) * 0.5 - center[i];
			dist += d * d;
		}
		return dist;
	}
	
	/**
	 * The square root is costly, and generally not required for sorting.
	 */
	public static double edgeSquareDistance(double[] center, double[] min, double[] max) {
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
		return dist;
	}
	
}
