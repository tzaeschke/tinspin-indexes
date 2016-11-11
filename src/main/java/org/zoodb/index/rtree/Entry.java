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
package org.zoodb.index.rtree;

import java.util.Arrays;

import org.zoodb.index.RectangleEntry;

public class Entry<T> implements RectangleEntry<T>, Comparable<Entry<T>> {
	protected double[] min;
	protected double[] max;
	private T val;
	
	public Entry(double[] min, double[] max, T val) {
		this.min = min;
		this.max = max;
		this.val = val;
	}

	@Override
	public double[] lower() {
		return min;
	}

	@Override
	public double[] upper() {
		return max;
	}

	@Override
	public T value() {
		return val;
	}
	
	@Override
	public int compareTo(Entry<T> o) {
		return Double.compare(min[0], o.min[0]);
	}
	
	double calcOverlap(Entry<T> e) {
		double area = 1;
		for (int i = 0; i < min.length; i++) {
			double d = min(max[i], e.max[i]) - max(min[i], e.min[i]);
			if (d <= 0) {
				return 0;
			}
			area *= d;
		}
		return area;
	}

	/**
	 * Check whether the current entry geometrically includes the
	 * rectangle defined by min2 and max2.
	 * @param min2
	 * @param max2
	 * @return WHether min2/max is included in the current entry.
	 */
	public boolean checkInclusion(double[] min2, double[] max2) {
		for (int i = 0; i < min.length; i++) {
			if (min[i] > min2[i] || max[i] < max2[i]) {
				return false;
			}
		}
		return true;
	}

	public boolean checkExactMatch(double[] min2, double[] max2) {
		for (int i = 0; i < min.length; i++) {
			if (min[i] != min2[i] || max[i] != max2[i]) {
				return false;
			}
		}
		return true;
	}

	public double calcArea() {
		double area = 1;
		for (int i = 0; i < min.length; i++) {
			double d = max[i] - min[i];
			area *= d;
		}
		return area;
	}

	public void setToCover(Entry<T> e1, Entry<T> e2) {
		for (int i = 0; i < min.length; i++) {
			min[i] = min(e1.min[i], e2.min[i]);
			max[i] = max(e1.max[i], e2.max[i]);
		}
	}
	
	static double min(double d1, double d2) {
		return d1 < d2 ? d1 : d2;
	}
	
	static double max(double d1, double d2) {
		return d1 > d2 ? d1 : d2;
	}

	public static double calcVolume(Entry<?> e) {
		return calcVolume(e.min, e.max);
	}

	public static double calcVolume(double[] min, double[] max) {
		double v = 1;
		for (int d = 0; d < min.length; d++) {
			v *= max[d] - min[d];
		}
		return v;
	}

	public static void calcBoundingBox(Entry<?>[] entries, int start, int end, 
			double[] minOut, double[] maxOut) {
		System.arraycopy(entries[start].min, 0, minOut, 0, minOut.length);
		System.arraycopy(entries[start].max, 0, maxOut, 0, maxOut.length);
		for (int i = start+1; i < end; i++) {
			for (int d = 0; d < minOut.length; d++) {
				minOut[d] = min(minOut[d], entries[i].min[d]);
				maxOut[d] = max(maxOut[d], entries[i].max[d]);
			}
		}
	}

	public static double calcOverlap(double[] min1, double[] max1, double[] min2, double[] max2) {
		double area = 1;
		for (int i = 0; i < min1.length; i++) {
			double d = min(max1[i], max2[i]) - max(min1[i], min2[i]);
			if (d <= 0) {
				return 0;
			}
			area *= d;
		}
		return area;
	}
	
	public static boolean checkOverlap(double[] min, double[] max, Entry<?> e) {
		for (int i = 0; i < min.length; i++) {
			if (min[i] > e.max[i] || max[i] < e.min[i]) {
				return false;
			}
		}
		return true;
	}

	public static boolean calcIncludes(double[] minOut, double[] maxOut, 
			double[] minIn, double[] maxIn) {
		for (int i = 0; i < minOut.length; i++) {
			if (minOut[i] > minIn[i] || maxOut[i] < maxIn[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Calculates the bounding boxes and the estimated dead space.
	 * @param entries
	 * @param start
	 * @param end
	 * @param minOut
	 * @param maxOut
	 * @return estimated dead space
	 */
	public static double calcDeadspace(Entry<?>[] entries, int start, int end, 
			double[] minOut, double[] maxOut) {
		Arrays.fill(minOut, Double.POSITIVE_INFINITY);
		Arrays.fill(maxOut, Double.NEGATIVE_INFINITY);
		double volumeSum = 0;
		for (int i = start; i < end; i++) {
			double v = 1;
			for (int d = 0; d < minOut.length; d++) {
				minOut[d] = min(minOut[d], entries[i].min[d]);
				maxOut[d] = max(maxOut[d], entries[i].max[d]);
				v *= entries[i].max[d]-entries[i].min[d];
			}
			volumeSum += v;
		}
		//The dead volume does not consider overlapping boundary boxes of children. The
		//resulting deadspace estimate can therefore be negative.
		return calcVolume(minOut, maxOut) - volumeSum;
	}

	public static double calcMargin(double[] min2, double[] max2) {
		double d = 0;
		for (int i = 0; i < min2.length; i++) {
			d += max2[i] - min2[i];
		}
		return d;
	}

	public static double calcCenterDistance(Entry<?> e1, Entry<?> e2) {
		double[] min1 = e1.min;
		double[] max1 = e1.max;
		double[] min2 = e2.min;
		double[] max2 = e2.max;
		double dist = 0;
		for (int i = 0; i < min1.length; i++) {
			double d = (min1[i]+max1[i])-(min2[i]+max2[i]);
			d *= 0.5;
			dist += d*d;
		}
		return Math.sqrt(dist);
	}
	
	@Override
	public String toString() {
		double[] len = new double[min.length];
		Arrays.setAll(len, (i)->(max[i]-min[i]));
		return Arrays.toString(min) + "/" + Arrays.toString(max) + ";len=" + 
		Arrays.toString(len) + ";v=" + val;
	}
}