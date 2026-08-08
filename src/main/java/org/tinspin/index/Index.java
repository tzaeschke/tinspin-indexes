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

/**
 * Common interface for all index implementations.
 */
public interface Index {

	/**
     * Number of key dimensions.
     *
	 * @return the number of dimensions
	 */
	int getDims();

	/**
     * Number of entries.
     *
	 * @return the number of entries
	 */
	int size();

	/**
	 * Clear all entries.
	 */
	void clear();

	/**
     * Index statistics.
     *
	 * @return Collect and return some index statistics. Note that indexes are not required
	 * to fill all fields. Also, individual indexes may use subclasses with additional fields.
     * Many indexes also perform consistency checks while gathering stats.
	 */
	Stats getStats();

    /**
     * The number of nodes in the tree.
     * @return The number of nodes.
     */
	int getNodeCount();

    /**
     * The maximum depth of the tree in terms of nodes.
     * @return maximum depth.
     */
	int getDepth();

	/**
     * String representation of the index.
	 *
	 * @return a full string output of the tree structure with all entries
	 */
	String toStringTree();

    /**
     * Common interface for query result iterators for extents queries, point queries and window queries.
     * @param <T> Entry type.
     */
    interface QueryIterator<T> extends Iterator<T> {
        /**
         * This method resets an iterator. The arguments determine new iterator properties:<br>
         * - For Extent iterators, see e.g. {@link PointMap#iterator()}, both arguments must be `null`.<br>
         * - For point query iterators, see e.g. {@link PointMultimap#queryExactPoint(double[])}, the first argument
         * is the new query point and the second argument must be `null`.<br>
         * - For window queries, see e.g. {@link PointMap#query(double[], double[])}, the arguments are
         * the min/max corners of the new query window.<br>
         *
         * @param point1 point or `null`
         * @param point2 point or `null`
         * @return this iterator after reset.
         */
        QueryIterator<T> reset(double[] point1, double[] point2);
    }

    /**
     * Iterator over Point entries.
     * @param <T> value type
     */
    interface PointIterator<T> extends QueryIterator<PointEntry<T>> {
    }

    /**
     * Iterator over Box entries.
     * @param <T> value type
     */
    interface BoxIterator<T> extends QueryIterator<BoxEntry<T>> {
    }

    /**
     * Common interface for query result iterators for k nearest neighbor queries.
     * @param <T> Entry type.
     */
    interface QueryIteratorKnn<T> extends Iterator<T> {
        /**
         * Reset the iterator (allows object reuse).
         * @param center new center point
         * @param k new k
         * @return fresh iterator
         */
        QueryIteratorKnn<T> reset(double[] center, int k);
    }

    /**
     * Iterator over Point entries.
     * @param <T> value type
     */
    interface PointIteratorKnn<T> extends QueryIteratorKnn<PointEntryKnn<T>> {
    }

    /**
     * Iterator over Box entries.
     * @param <T> value type
     */
    interface BoxIteratorKnn<T> extends QueryIteratorKnn<BoxEntryKnn<T>> {
    }

    /**
     * Entry for point indexes.
     * @param <T> value type
     */
    class PointEntry<T> {

        private double[] point;
        private T value;

        /**
         * Create entry.
         * @param point point
         * @param value value
         */
        public PointEntry(double[] point, T value) {
            this.point = point;
            this.value = value;
        }

        /**
         * Point.
         * @return The coordinates of the entry.
         */
        public double[] point() {
            return point;
        }

        /**
         * Value.
         * @return The value associated with the box or point.
         */
        public T value() {
            return value;
        }

        @Override
        public String toString() {
            return Arrays.toString(point) + ";v=" + value;
        }

        /**
         * Update entry.
         * @param point new point
         */
        public void setPoint(double[] point) {
            this.point = point;
        }

        /**
         * Update entry.
         * @param point new point
         * @param value value
         */
        protected void set(double[] point, T value) {
            this.point = point;
            this.value = value;
        }
    }

    /**
     * Entry for point indexes returned by nearest neighbor queries.
     * @param <T> value type
     */
    class PointEntryKnn<T> extends PointEntry<T> {

        private double dist;

        /**
         * Create entry.
         * @param point point
         * @param value value
         * @param dist distance
         */
        public PointEntryKnn(double[] point, T value, double dist) {
            super(point, value);
            this.dist = dist;
        }

        /**
         * Create entry.
         * @param entry other entry
         * @param dist distance
         */
        public PointEntryKnn(PointEntry<T> entry, double dist) {
            super(entry.point(), entry.value());
            this.dist = dist;
        }

        /**
         * An entry with distance property. This is, for example, used
         * as a return value for nearest neighbour queries.
         *
         * @return the distance
         */
        public double dist() {
            return dist;
        }

        /**
         * Update entry.
         * @param point new min
         * @param value new value
         * @param dist new distance
         */
        public void set(double[] point, T value, double dist) {
            super.set(point, value);
            this.dist = dist;
        }

        /**
         * Update entry from other (without distance).
         * @param entry other entry
         * @param dist distance
         */
        public void set(PointEntry<T> entry, double dist) {
            super.set(entry.point(), entry.value);
            this.dist = dist;
        }
    }

    /**
     * A box entry. Boxes are axis-aligned. They are defined by there minimum and maximum values,
     * i.e. their "lower left" and "upper right" corners.
     *
     * @param <T> Value type
     */
    class BoxEntry<T> {
        private double[] min;
        private double[] max;
        private T val;

        /**
         * Construct an entry defined by min, max and value.
         * @param min min
         * @param max max
         * @param val value
         */
        public BoxEntry(double[] min, double[] max, T val) {
            this.min = min;
            this.max = max;
            this.val = val;
        }

        /**
         * Min.
         * @return The lower left corner of the box.
         */
        public double[] min() {
            return min;
        }

        /**
         * Max.
         * @return The upper right corner of the entry.
         */
        public double[] max() {
            return max;
        }

        /**
         * Min.
         * @return The lower left corner of the box.
         * @deprecated Please use min() instead
         */
        @Deprecated // Please use min() instead
        double[] lower() {
            return min;
        }

        /**
         * Max.
         * @return The upper right corner of the entry.
         * @deprecated Please use max() instead
         */
        @Deprecated // Please use max() instead
        double[] upper() {
            return max;
        }

        /**
         * Value.
         * @return The value associated with the box or point.
         */
        public T value() {
            return val;
        }

        /**
         * Update entry.
         * @param min new min
         * @param max new max
         */
        public void set(double[] min, double[] max) {
            this.min = min;
            this.max = max;
        }

        /**
         * Update entry.
         * @param min new min
         * @param max new max
         * @param val new value
         */
        public void set(double[] min, double[] max, T val) {
            this.set(min, max);
            this.val = val;
        }
    }

    /**
     * Entry for box indexes returned by nearest neighbor queries.
     * @param <T> value type
     */
    class BoxEntryKnn<T> extends BoxEntry<T> {
        private double dist;

        /**
         * Construct an entry defined by min, max, value and distance.
         * @param min min
         * @param max max
         * @param value val
         * @param dist distance
         */
        public BoxEntryKnn(double[] min, double[] max, T value, double dist) {
            super(min, max, value);
            this.dist = dist;
        }

        /**
         * Construct an entry defined by another entry and a distance.
         * @param entry other entry
         * @param dist distance
         */
        public BoxEntryKnn(BoxEntry<T> entry, double dist) {
            super(entry.min(), entry.max(), entry.value());
            this.dist = dist;
        }

        /**
         * An entry with distance property. This is, for example, used
         * as a return value for nearest neighbor queries.
         * @return the distance
         */
        public double dist() {
            return dist;
        }

        /**
         * Update entry.
         * @param min new min
         * @param max new max
         * @param val new value
         * @param dist new distance
         */
        public void set(double[] min, double[] max, T val, double dist) {
            super.set(min, max, val);
            this.dist = dist;
        }
    }

    /**
     * Filter function for kNN queries.
     * @param <T> Value type
     */
    @FunctionalInterface
    interface PointFilterKnn<T> {
        boolean test(PointEntry<T> entry, double distance);
    }

    /**
     * Filter function for kNN queries.
     * @param <T> Value type
     */
    @FunctionalInterface
    interface BoxFilterKnn<T> {
        boolean test(BoxEntry<T> entry, double distance);
    }

    /**
     * Distance comparator function for Point kNN query result entries.
     */
    class PEComparator implements Comparator<PointEntryKnn<?>> {
        @Override
        public int compare(PointEntryKnn<?> o1, PointEntryKnn<?> o2) {
            return Double.compare(o1.dist, o2.dist);
        }
    }

    /**
     * Distance comparator function for Box kNN query result entries.
     */
    class BEComparator implements Comparator<BoxEntryKnn<?>> {
	    @Override
	    public int compare(BoxEntryKnn<?> o1, BoxEntryKnn<?> o2) {
            return Double.compare(o1.dist, o2.dist);
	    }
	}
}