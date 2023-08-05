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

import org.tinspin.index.array.PointArray;
import org.tinspin.index.kdtree.KDTree;
import org.tinspin.index.phtree.PHTreeMMP;
import org.tinspin.index.qthypercube.QuadTreeKD;
import org.tinspin.index.qthypercube2.QuadTreeKD2;
import org.tinspin.index.qtplain.QuadTreeKD0;
import org.tinspin.index.rtree.Entry;
import org.tinspin.index.rtree.RTree;
import org.tinspin.index.util.PointMultimapWrapper;

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
public interface PointMultimap<T> extends Index {

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
    boolean remove(double[] point, T value);

    /**
     * Remove *one* entry with the given condition.
     *
     * @param point     the point
     * @param condition the condition required for removing an entry
     * @return the value of the entry or null if the entry was not found
     */
    boolean removeIf(double[] point, Predicate<PointEntry<T>> condition);

    /**
     * Update the position of an entry.
     *
     * @param oldPoint old position
     * @param newPoint new position
     * @param value    only entries with this value are updated
     * @return the value of the entry or null if the entry was not found
     */
    boolean update(double[] oldPoint, double[] newPoint, T value);

    /**
     * Lookup an entry, using exact match.
     *
     * @param point the point
     * @param value the value
     * @return `true` if an entry was found, otherwise `false`.
     */
    boolean contains(double[] point, T value);

    /**
     * Lookup an entries at a given coordinate.
     *
     * @param point the point
     * @return an iterator over all entries at the given point
     */
    PointIterator<T> queryExactPoint(double[] point);

    /**
     * @return An iterator over all entries.
     */
    PointIterator<T> iterator();

    /**
     * @param min Lower left corner of the query window
     * @param max Upper right corner of the query window
     * @return All points that lie inside the query rectangle.
     */
    PointIterator<T> query(double[] min, double[] max);

    /**
     * Finds the nearest neighbor. This uses Euclidean distance.
     * Other distance types can only be specified directly on the index implementations.
     *
     * @param center center point
     * @return the nearest neighbor
     */
    default PointEntryKnn<T> query1nn(double[] center) {
        Iterator<PointEntryKnn<T>> it = queryKnn(center, 1);
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Finds the nearest neighbor. This uses Euclidean distance.
     * Other distance types can only be specified directly on the index implementations.
     *
     * @param center center point
     * @param k      number of neighbors
     * @return list of nearest neighbors
     */
    default PointIteratorKnn<T> queryKnn(double[] center, int k) {
        return queryKnn(center, k, PointDistance.L2);
    }

    /**
     * Finds the nearest neighbor. This uses Euclidean distance.
     * Other distance types can only be specified directly on the index implementations.
     *
     * @param center center point
     * @param k      number of neighbors
     * @param distFn the point distance function to be used
     * @return list of nearest neighbors
     */
    PointIteratorKnn<T> queryKnn(double[] center, int k, PointDistance distFn);


    interface Factory {
        /**
         * Create an array backed PointMap. This is only for testing and rather inefficient for large data sets.
         *
         * @param dims Number of dimensions.
         * @param size Number of entries.
         * @param <T>  Value type
         * @return New PointArray
         */
        static <T> PointMultimap<T> createArray(int dims, int size) {
            return new PointArray<>(dims, size);
        }

        /**
         * Create a kD-Tree
         *
         * @param dims Number of dimensions.
         * @param <T>  Value type
         * @return New PH-Tree
         */
        static <T> PointMultimap<T> createKdTree(int dims) {
            return KDTree.create(dims);
        }

        /**
         * Create a PH-Tree.
         *
         * @param dims Number of dimensions.
         * @param <T>  Value type
         * @return New PH-Tree
         */
        static <T> PointMultimap<T> createPhTree(int dims) {
            return PHTreeMMP.create(dims);
        }

        /**
         * Create a plain Quadtree.
         *
         * @param dims Number of dimensions.
         * @param <T>  Value type
         * @return New Quadtree
         */
        static <T> PointMultimap<T> createQuadtree(int dims) {
            return QuadTreeKD0.create(dims);
        }

        /**
         * Create a plain Quadtree.
         * Center/radius are used to find a good initial root. They do not need to be exact. If possible, they should
         * span an area that is somewhat larger rather than smaller than the actual data.
         *
         * @param dims            Number of dimensions.
         * @param maxNodeCapacity Maximum entries in a node before the node is split. The default is 10.
         * @param center          Estimated center of all coordinates.
         * @param radius          Estimated maximum orthogonal distance from center for all coordinates.
         * @param <T>             Value type
         * @return New Quadtree
         */
        static <T> PointMultimap<T> createQuadtree(int dims, int maxNodeCapacity, double[] center, double radius) {
            return QuadTreeKD0.create(dims, maxNodeCapacity, center, radius);
        }

        /**
         * Create a Quadtree with hypercube navigation.
         *
         * @param dims Number of dimensions.
         * @param <T>  Value type
         * @return New QuadtreeHC
         */
        static <T> PointMultimap<T> createQuadtreeHC(int dims) {
            return QuadTreeKD.create(dims);
        }

        /**
         * Create a plain Quadtree.
         * Center/radius are used to find a good initial root. They do not need to be exact. If possible, they should
         * span an area that is somewhat larger rather than smaller than the actual data.
         *
         * @param dims            Number of dimensions.
         * @param maxNodeCapacity Maximum entries in a node before the node is split. The default is 10.
         * @param center          Estimated center of all coordinates.
         * @param radius          Estimated maximum orthogonal distance from center for all coordinates.
         * @param <T>             Value type
         * @return New QuadtreeHC
         */
        static <T> PointMultimap<T> createQuadtreeHC(int dims, int maxNodeCapacity, double[] center, double radius) {
            return QuadTreeKD.create(dims, maxNodeCapacity, center, radius);
        }

        /**
         * Create a Quadtree with hypercube navigation.
         *
         * @param dims Number of dimensions.
         * @param <T>  Value type
         * @return New QuadtreeHC2
         */
        static <T> PointMultimap<T> createQuadtreeHC2(int dims) {
            return QuadTreeKD2.create(dims);
        }

        /**
         * Create a plain Quadtree.
         * Center/radius are used to find a good initial root. They do not need to be exact. If possible, they should
         * span an area that is somewhat larger rather than smaller than the actual data.
         *
         * @param dims            Number of dimensions.
         * @param maxNodeCapacity Maximum entries in a node before the node is split. The default is 10.
         * @param center          Estimated center of all coordinates.
         * @param radius          Estimated maximum orthogonal distance from center for all coordinates.
         * @param <T>             Value type
         * @return New QuadtreeHC2
         */
        static <T> PointMultimap<T> createQuadtreeHC2(int dims, int maxNodeCapacity, double[] center, double radius) {
            return QuadTreeKD2.create(dims, maxNodeCapacity, center, radius);
        }

        /**
         * Create an R*Tree. R*Tree can be "turned into" STR-Trees by using {@link RTree#load(Entry[])}.
         *
         * @param dims Number of dimensions.
         * @param <T>  Value type
         * @return New R*Tree
         */
        static <T> PointMultimap<T> createRStarTree(int dims) {
            return PointMultimapWrapper.create(RTree.createRStar(dims));
        }
    }
}