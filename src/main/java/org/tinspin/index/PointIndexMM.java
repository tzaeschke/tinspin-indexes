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

import java.util.Iterator;
import java.util.function.Predicate;

/**
 * A common interface for spatial indexes (multimaps) that use points as keys.
 * This interface requires indexes to be multimaps which mean a given keys
 * can exist multiple times and is not overwritten when a new entry with the same
 * key is added.
 *
 * @param <T> Type of the value associated with the point key.
 */
public interface PointIndexMM<T> extends Index<T> {

    /**
     * Insert a point.
     *
     * @param key   point
     * @param value value
     */
    void insert(double[] key, T value);

    /**
     * Remove *one* entry with the given value.
     *
     * @param point the point
     * @param value only entries with this value are removed
     * @return the value of the entry or null if the entry was not found
     */
    T remove(double[] point, T value);

    /**
     * Remove all entries at the given point.
     *
     * @param point the point
     * @return the number of entries that were removed
     */
    int removeAll(double[] point);

    /**
     * Update the position of an entry.
     *
     * @param oldPoint old position
     * @param newPoint new position
     * @param value    only entries with this value are updated
     * @return the value of the entry or null if the entry was not found
     */
    T update(double[] oldPoint, double[] newPoint, T value);

    /**
     * Lookup an entry, using exact match.
     *
     * @param point the point
     * @return an iterator over all entries at the given point
     */
    QueryIterator<PointEntry<T>> query(double[] point);

    /**
     * @return An iterator over all entries.
     */
    QueryIterator<PointEntry<T>> iterator();

    /**
     * @param min Lower left corner of the query window
     * @param max Upper right corner of the query window
     * @return All points that lie inside the query rectangle.
     */
    QueryIterator<PointEntry<T>> query(double[] min, double[] max);

    /**
     * Finds the nearest neighbor. This uses Euclidean distance.
     * Other distance types can only be specified directly on the index implementations.
     *
     * @param center center point
     * @return the nearest neighbor
     */
    default PointEntryDist<T> query1NN(double[] center) {
        Iterator<? extends PointEntryDist<T>> it = queryKNN(center, 1);
        if (it.hasNext()) {
            return it.next();
        }
        return null;
    }

    /**
     * Finds the nearest neighbor. This uses Euclidean distance.
     * Other distance types can only be specified directly on the index implementations.
     *
     * @param center center point
     * @param k      number of neighbors
     * @return list of nearest neighbors
     */
    default QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k) {
		return queryKNN(center, k, PointDistanceFunction.L2, e -> true);
	}

    /**
     * Finds the nearest neighbor. This uses Euclidean distance.
     * Other distance types can only be specified directly on the index implementations.
     *
     * @param center center point
     * @param k      number of neighbors
	 * @param dist   the point distance function to be used
	 * @param filter the filter function to be used. It should return `true` if the entry passes
	 *               or `false` if it should be ignored.
     * @return list of nearest neighbors
     */
    QueryIteratorKNN<PointEntryDist<T>> queryKNN(double[] center, int k, PointDistanceFunction dist, Predicate<T> filter);
}