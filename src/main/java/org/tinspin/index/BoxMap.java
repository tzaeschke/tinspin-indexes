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

import org.tinspin.index.array.RectArray;
import org.tinspin.index.phtree.PHTreeR;
import org.tinspin.index.qthypercube.QuadTreeRKD;
import org.tinspin.index.qtplain.QuadTreeRKD0;
import org.tinspin.index.rtree.Entry;
import org.tinspin.index.rtree.RTree;

import java.util.Iterator;

public interface BoxMap<T> extends Index {

    /**
     * Insert a box.
     *
     * @param min   minimum corner
     * @param max   maximum corner
     * @param value value
     */
    void insert(double[] min, double[] max, T value);

    /**
     * Remove an entry.
     *
     * @param min minimum corner
     * @param max maximum corner
     * @return the value of the entry or null if the entry was not found
     */
    T remove(double[] min, double[] max);

    /**
     * Update the position of an entry.
     *
     * @param minOld old min
     * @param maxOld old max
     * @param minNew new min
     * @param maxNew new max
     * @return the value, or null if the entries was not found
     */
    T update(double[] minOld, double[] maxOld, double[] minNew, double[] maxNew);

    /**
     * Lookup an entry, using exact match.
     *
     * @param min minimum corner
     * @param max maximum corner
     * @return `true` if an entry was found, otherwise `false`.
     */
    boolean contains(double[] min, double[] max);

    /**
     * Lookup an entry, using exact match.
     *
     * @param min minimum corner
     * @param max maximum corner
     * @return the value of the entry or null if the entry was not found
     */
    T queryExact(double[] min, double[] max);

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

    interface Factory {
        /**
         * Create an array backed BoxMap. This is only for testing and rather inefficient for large data sets.
         *
         * @param dims Number of dimensions.
         * @param size Number of entries.
         * @param <T>  Value type
         * @return New RectArray
         */
        static <T> BoxMap<T> createArray(int dims, int size) {
            return new RectArray<>(dims, size);
        }

        /**
         * Create a PH-Tree.
         *
         * @param dims Number of dimensions.
         * @param <T>  Value type
         * @return New PH-Tree
         */
        static <T> BoxMap<T> createPhTree(int dims) {
            return PHTreeR.createPHTree(dims);
        }

        /**
         * Create a plain Quadtree.
         *
         * @param dims Number of dimensions.
         * @param <T>  Value type
         * @return New Quadtree
         */
        static <T> BoxMap<T> createQuadtree(int dims) {
            return QuadTreeRKD0.create(dims);
        }

        /**
         * Create a plain Quadtree.
         * Min/max are used to find a good initial root. They do not need to be exact. If possible, min/max should
         * span an area that is somewhat larger rather than smaller than the actual data.
         *
         * @param dims            Number of dimensions.
         * @param maxNodeCapacity Maximum entries in a node before the node is split. The default is 10.
         * @param min             Estimated minimum of all coordinates.
         * @param max             Estimated maximum of all coordinates.
         * @param <T>             Value type
         * @return New Quadtree
         */
        static <T> BoxMap<T> createQuadtree(int dims, int maxNodeCapacity, double[] min, double[] max) {
            return QuadTreeRKD0.create(dims, maxNodeCapacity, min, max);
        }

        /**
         * Create a Quadtree with hypercube navigation.
         *
         * @param dims Number of dimensions.
         * @param <T>  Value type
         * @return New QuadtreeHC
         */
        static <T> BoxMap<T> createQuadtreeHC(int dims) {
            return QuadTreeRKD.create(dims);
        }

        /**
         * Create a plain Quadtree.
         * Min/max are used to find a good initial root. They do not need to be exact. If possible, min/max should
         * span an area that is somewhat larger rather than smaller than the actual data.
         *
         * @param dims            Number of dimensions.
         * @param maxNodeCapacity Maximum entries in a node before the node is split. The default is 10.
         * @param min             Estimated minimum of all coordinates.
         * @param max             Estimated maximum of all coordinates.
         * @param <T>             Value type
         * @return New QuadtreeHC
         */
        static <T> BoxMap<T> createQuadtreeHC(int dims, int maxNodeCapacity, double[] min, double[] max) {
            return QuadTreeRKD.create(dims, maxNodeCapacity, min, max);
        }

        /**
         * Create an R*Tree. R*Trees can be "turned into" STR-Trees by using {@link RTree#load(Entry[])}.
         *
         * @param dims Number of dimensions.
         * @param <T>  Value type
         * @return New R*Tree
         */
        static <T> BoxMap<T> createRStarTree(int dims) {
            return RTree.createRStar(dims);
        }
    }
}