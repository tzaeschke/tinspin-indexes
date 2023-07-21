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
public interface PointDistance {

	/** L1/Manhattan/taxi distance. */
	PointDistance L1 = PointDistance::l1;
	/** L2/Euclidean distance. */
	PointDistance L2 = PointDistance::l2;

	double dist(double[] p1, double[] p2);

	/**
	 * 
	 * @param p1 a point
	 * @param entry another point
	 * @return distance between the points
	 */
	default double dist(double[] p1, PointEntry<?> entry) {
		return dist(p1, entry.point());
	}


	/**
	 * Manhattan/Taxi distance / L1.
	 * @param p1 point 1
	 * @param p2 point 2
	 * @return distance
	 */
	static double l1(double[] p1, double[] p2) {
		double dist = 0;
		for (int i = 0; i < p1.length; i++) {
			double d = Math.abs(p1[i] - p2[i]);
			dist += d;
		}
		return dist;
	}

	/**
	 * Euclidean distance / L2.
	 * @param p1 point 1
	 * @param p2 point 2
	 * @return distance
	 */
	static double l2(double[] p1, double[] p2) {
		double dist = 0;
		for (int i = 0; i < p1.length; i++) {
			double d = p1[i] - p2[i];
			dist += d*d;
		}
		return Math.sqrt(dist);
	}

	static String getName(PointDistance fn) {
		//'nice' hack, eh?
		if (fn == L1) {
			return "L1";
		} else if (fn == L2) {
			return "L2";
		}
		return "unknown";
	}
}
