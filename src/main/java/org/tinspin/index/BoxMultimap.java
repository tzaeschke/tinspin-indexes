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
 * A common interface for spatial indexes (multimaps) that use boxes as keys.
 * This interface requires indexes to be multimaps which mean a given keys
 * can exist multiple times and is not overwritten when a new entry with the same
 * key is added.
 *
 * @param <T> Type of the value associated with the boxes key.
 */
public interface BoxMultimap<T> extends Index {

    /**
     * Insert a box.
     *
     * @param lower minimum corner
     * @param upper maximum corner
     * @param value value
     */
    void insert(double[] lower, double[] upper, T value);

    /**
     * Remove *one*n entry with the given value.
     *
     * @param lower minimum corner
     * @param upper maximum corner
     * @param value value
     * @return the value of the entry or null if the entry was not found
     */
    boolean remove(double[] lower, double[] upper, T value);

    /**
     * Remove *one* entry with the given condition.
     *
     * @param lower     minimum corner
     * @param upper     maximum corner
     * @param condition the condition required for removing an entry
     * @return the value of the entry or null if the entry was not found
     */
    boolean removeIf(double[] lower, double[] upper, Predicate<BoxEntry<T>> condition);

    /**
     * Update the position of an entry.
     *
     * @param lo1   old min
     * @param up1   old max
     * @param lo2   new min
     * @param up2   new max
     * @param value only entries with this value are updated
     * @return the value, or null if the entries was not found
     */
    boolean update(double[] lo1, double[] up1, double[] lo2, double[] up2, T value);

    /**
     * Lookup an entry, using exact match.
     *
     * @param lower minimum corner
     * @param upper maximum corner
     * @param value the value
     * @return `true` if an entry was found, otherwise `false`.
     */
    boolean contains(double[] lower, double[] upper, T value);

    /**
     * Lookup an entry, using exact match.
     *
     * @param lower minimum corner
     * @param upper maximum corner
     * @return an iterator over all entries with the exact given box shape
     */
    BoxIterator<T> queryRectangle(double[] lower, double[] upper);

    /**
     * @return An iterator over all entries.
     */
    BoxIterator<T> iterator();

    /**
     * @param min Lower left corner of the query window
     * @param max Upper right corner of the query window
     * @return All boxes that intersect with the query rectangle.
     */
    BoxIterator<T> queryIntersect(double[] min, double[] max);

    /**
     * Finds the nearest neighbor. This uses Euclidean 'edge distance'.
     * Other distance types can only be specified directly on the index implementations.
     *
     * @param center center point
     * @return the nearest neighbor
     */
    default BoxEntryKnn<T> query1nn(double[] center) {
        Iterator<BoxEntryKnn<T>> it = queryKnn(center, 1);
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Finds the nearest neighbor.
     * This uses Euclidean 'edge distance', i.e. the distance to the edge of a box.
     * Distance is 0 if the box overlaps with the search point.
     * Other distance types can only be specified directly on the index implementations.
     *
     * @param center center point
     * @param k      number of neighbors
     * @return list of nearest neighbors
     */
    BoxIteratorKnn<T> queryKnn(double[] center, int k);

    /**
     * Finds the nearest neighbor.
     * This uses a custom distance function for distances to boxes.
     *
     * @param center center point
     * @param k      number of neighbors
     * @param distFn distance function
     * @return list of nearest neighbors
     */
    BoxIteratorKnn<T> queryKnn(double[] center, int k, BoxDistance distFn);
}