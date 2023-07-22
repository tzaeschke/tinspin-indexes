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
package org.tinspin.index;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

public interface Index {

	/**
	 * @return the number of dimensions
	 */
	int getDims();

	/**
	 * @return the number of entries
	 */
	int size();

	/**
	 * Clear all entries.
	 */
	void clear();

	/**
	 * @return Collect and return some index statistics. Note that indexes are not required
	 * to fill all fields. Also, individual indexes may use subclasses with additional fields.
	 */
	Stats getStats();

	int getNodeCount();
	
	int getDepth();
	
	/**
	 * 
	 * @return a full string output of the tree structure with all entries 
	 */
	String toStringTree();

	class PointEntry<T> {

		private double[] point;
		private T value;

		public PointEntry(double[] point, T value) {
			this.point = point;
			this.value = value;
		}

		/**
		 * @return The coordinates of the entry.
		 */
		public double[] point() {
			return point;
		}

		/**
		 * @return The value associated with the rectangle or point.
		 */
		public T value() {
			return value;
		}

		@Override
		public String toString() {
			return Arrays.toString(point) + ";v=" + value;
		}

		public void setPoint(double[] point) {
			this.point = point;
		}

		protected void set(double[] point, T value) {
			this.point = point;
			this.value = value;
		}
	}

	class PointEntryDist<T> extends PointEntry<T> {

		private double dist;

		public PointEntryDist(double[] point, T value, double dist) {
			super(point, value);
			this.dist = dist;
		}

		public PointEntryDist(PointEntry<T> entry, double dist) {
			super(entry.point(), entry.value());
			this.dist = dist;
		}

		/**
		 * An entry with distance property. This is, for example, used
		 * as a return value for nearest neighbour queries.
		 * @return the distance
		 */
		public double dist() {
			return dist;
		}

		public void set(double[] point, T value, double dist) {
			super.set(point, value);
			this.dist = dist;
		}

		public void set(PointEntry<T> entry, double dist) {
			super.set(entry.point(), entry.value);
			this.dist = dist;
		}
	}

	//Comparator<PointEntryDist<T>> PEComparator = (o1, o2) -> (int)(o1.dist() - o2.dist());
	class PEComparator<T> implements Comparator<PointEntryDist<T>> {

		@Override
		public int compare(PointEntryDist<T> o1, PointEntryDist<T> o2) {
			double d = o1.dist - o2.dist;
			return d < 0 ? -1 : d > 0 ? 1 : 0;
		}
	}

	interface QueryIterator<T> extends Iterator<T> {
		QueryIterator<T> reset(double[] min, double[] max);
	}

	interface PointIterator<T> extends QueryIterator<PointEntry<T>> {
	}

	interface BoxIterator<T> extends QueryIterator<BoxEntry<T>> {
	}

	interface QueryIteratorKnn<T> extends Iterator<T> {
		QueryIteratorKnn<T> reset(double[] center, int k);
	}

	interface PointIteratorKnn<T> extends QueryIteratorKnn<PointEntryDist<T>> {
	}

	interface BoxIteratorKnn<T> extends QueryIteratorKnn<BoxEntryDist<T>> {
	}
}