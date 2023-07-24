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

@FunctionalInterface
public interface BoxDistance {

	BoxDistance CENTER = BoxDistance::centerDistance;
	BoxDistance EDGE = BoxDistance::edgeDistance;

	/**
	 * @param point A point
	 * @param min Minimum corner of axis aligned box
	 * @param max Maximum corner of axis aligned box
	 * @return Distance between point and box
	 */
	double dist(double[] point, double[] min, double[] max);

	/**
	 * Some algorithm use this method on the entries containing user supplied values.
	 * This can be overridden if the min/max coordinates only represent the 
	 * bounding-box of the object.
	 * <p>
	 * If your entry is actually a sphere, a car, an human or a cat, you may need this.
	 * 
	 * @param center a point
	 * @param entry a rectangle
	 * @return distance between point and rectangle
	 */
	default double dist(double[] center, Index.BoxEntry<?> entry) {
		return dist(center, entry.min(), entry.max());
	}

	/**
	 * This class calculates the distance to a rectangular shaped object.
	 * <p>
	 * This class completely ignores the center given as parameter to the interface.
	 * 
	 * TODO: maybe we could get rid of the center parameter altogether and always let 
	 *       the RectangleDistanceFunction hold it's reference points?
	 */
	class RectangleDist implements BoxDistance {
		private final double[] lower;
		private final double[] upper;

		public RectangleDist(double[] lower, double[] upper) {
			this.lower = lower;
			this.upper = upper;
		}

		@Override
		public double dist(double[] ignored, double[] min, double[] max) {
			double dist = 0;
			for (int i = 0; i < lower.length; i++) {
				double d = 0;
				if (min[i] > upper[i]) {
					// "right" side of our rectangle
					d = min[i] - upper[i];
				} else if (max[i] < lower[i]) {
					// "left" side of our rectangle
					d = lower[i] - max[i];
				} // else intersecting
				dist += d * d;
			}
			return dist;
		}
	}

	/**
	 * Special wrapper class which takes the inverse or the given function.
	 * Can be used to get the farthest neighbors using the nearest neighbor algorithm.
	 */
	class FarthestNeighbor implements BoxDistance {
		private static final double EPSILON = 2 * Double.MIN_VALUE;
		private final BoxDistance dist;

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

	static double centerDistance(double[] center, double[] min, double[] max) {
		double dist = 0;
		for (int i = 0; i < center.length; i++) {
			double d = (min[i] + max[i]) * 0.5 - center[i];
			dist += d*d;
		}
		return Math.sqrt(dist);
	}

	static double edgeDistance(double[] center, double[] min, double[] max) {
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

	class EdgeDistance {
		final PointDistance distFn;
		public EdgeDistance(PointDistance distFn) {
			this.distFn = distFn;
		}

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
