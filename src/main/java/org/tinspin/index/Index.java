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

    interface QueryIterator<T> extends Iterator<T> {
        /**
         * This method resets an iterator. The arguments determin new iterator properties:
         * - For Extent iterators, see e.g. {@link PointMap#iterator()}, both arguments must be `null`.
         * - For point query iterators, see e.g. {@link PointMultimap#query(double[])}, the first argument
         * is the new query point and the second argument must be `null`.
         * - For window queries, see e.g. {@link PointMap#query(double[], double[])}, the arguments are
         * the min/max corners of the new query window.
         *
         * @param point1 point or `null`
         * @param point2 point or `null`
         * @return this iterator after reset.
         */
        QueryIterator<T> reset(double[] point1, double[] point2);
    }

    interface PointIterator<T> extends QueryIterator<PointEntry<T>> {
    }

    interface BoxIterator<T> extends QueryIterator<BoxEntry<T>> {
    }

    interface QueryIteratorKnn<T> extends Iterator<T> {
        QueryIteratorKnn<T> reset(double[] center, int k);
    }

    interface PointIteratorKnn<T> extends QueryIteratorKnn<PointEntryKnn<T>> {
    }

    interface BoxIteratorKnn<T> extends QueryIteratorKnn<BoxEntryKnn<T>> {
    }

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
         * @return The value associated with the box or point.
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

    class PointEntryKnn<T> extends PointEntry<T> {

        private double dist;

        public PointEntryKnn(double[] point, T value, double dist) {
            super(point, value);
            this.dist = dist;
        }

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

        public void set(double[] point, T value, double dist) {
            super.set(point, value);
            this.dist = dist;
        }

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
    public class BoxEntry<T> {
        private double[] min;
        private double[] max;
        private T val;

        public BoxEntry(double[] min, double[] max, T val) {
            this.min = min;
            this.max = max;
            this.val = val;
        }

        /**
         * @return The lower left corner of the box.
         */
        public double[] min() {
            return min;
        }

        /**
         * @return The upper right corner of the entry.
         */
        public double[] max() {
            return max;
        }

        /**
         * @return The lower left corner of the box.
         */
        @Deprecated // Please use min() instead
        double[] lower() {
            return min;
        }

        /**
         * @return The upper right corner of the entry.
         */
        @Deprecated // Please use max() instead
        double[] upper() {
            return max;
        }

        /**
         * @return The value associated with the box or point.
         */
        public T value() {
            return val;
        }

        public void set(double[] min, double[] max) {
            this.min = min;
            this.max = max;
        }

        public void set(double[] min, double[] max, T val) {
            this.set(min, max);
            this.val = val;
        }
    }

    class BoxEntryKnn<T> extends BoxEntry<T> {
        private double dist;

        public BoxEntryKnn(double[] min, double[] max, T value, double dist) {
            super(min, max, value);
            this.dist = dist;
        }

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

        public void set(double[] min, double[] max, T val, double dist) {
            super.set(min, max, val);
            this.dist = dist;
        }
    }

    @FunctionalInterface
    interface PointFilterKnn<T> {
        boolean test(PointEntry<T> entry, double distance);
    }

    @FunctionalInterface
    interface BoxFilterKnn<T> {
        boolean test(BoxEntry<T> entry, double distance);
    }

    @Deprecated
    class PEComparator implements Comparator<PointEntryKnn<?>> {

        @Override
        public int compare(PointEntryKnn<?> o1, PointEntryKnn<?> o2) {
            double d = o1.dist - o2.dist;
            return d < 0 ? -1 : d > 0 ? 1 : 0;
        }
    }

    @Deprecated
    class BEComparator implements Comparator<BoxEntryKnn<?>> {
	    @Override
	    public int compare(BoxEntryKnn<?> o1, BoxEntryKnn<?> o2) {
	        double d = o1.dist() - o2.dist();
	        return d < 0 ? -1 : (d > 0 ? 1 : 0);
	    }
	}
}