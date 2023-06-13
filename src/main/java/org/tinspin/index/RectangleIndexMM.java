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
 * A common interface for spatial indexes (multimaps) that use rectangles as keys.
 * This interface requires indexes to be multimaps which mean a given keys
 * can exist multiple times and is not overwritten when a new entry with the same
 * key is added.
 *
 * @param <T> Type of the value associated with the rectangles key.
 */
public interface RectangleIndexMM<T> extends Index<T> {

	/**
	 * Insert a rectangle.
	 * @param lower minimum corner
	 * @param upper maximum corner
	 * @param value value
	 */
	void insert(double[] lower, double[] upper, T value);

	/**
	 * Remove *one*n entry with the given value.
	 * @param lower minimum corner
	 * @param upper maximum corner
	 * @return the value of the entry or null if the entry was not found
	 */
	T remove(double[] lower, double[] upper, T value);

	/**
	 * Remove all entries with the given rectangle key.
	 * @param lower minimum corner
	 * @param upper maximum corner
	 * @return the value of the entry or null if the entry was not found
	 */
	int removeAll(double[] lower, double[] upper);

	/**
	 * Update the position of an entry.
	 * @param lo1 old min
	 * @param up1 old max
	 * @param lo2 new min
	 * @param up2 new max
	 * @param value only entries with this value are updated
	 * @return the value, or null if the entries was not found
	 */
	T update(double[] lo1, double[] up1, double[] lo2, double[] up2, T value);

	/**
	 * Lookup an entry, using exact match.
	 * @param lower minimum corner
	 * @param upper maximum corner
	 * @return an iterator over all entries at with the exact given rectangle
	 */
	QueryIterator<RectangleEntry<T>> queryRectangle(double[] lower, double[] upper);
	
	/**
	 * @return An iterator over all entries.
	 */
	QueryIterator<RectangleEntry<T>> iterator();

	/**
	 * @param min Lower left corner of the query window
	 * @param max Upper right corner of the query window
	 * @return All rectangles that intersect with the query rectangle.
	 */
	QueryIterator<RectangleEntry<T>> queryIntersect(double[] min, double[] max);

	/**
	 * Finds the nearest neighbor. This uses Euclidean 'edge distance'.
	 * Other distance types can only be specified directly on the index implementations. 
	 * @param center center point
	 * @return the nearest neighbor
	 */
	default RectangleEntryDist<T> query1NN(double[] center) {
		Iterator<? extends RectangleEntryDist<T>> it = queryKNN(center, 1);
		if (it.hasNext()) {
			return it.next();
		}
		return null;
	}

	/**
	 * Finds the nearest neighbor. 
	 * This uses Euclidean 'edge distance', i.e. the distance to the edge of rectangle.
	 * Distance is 0 is the rectangle overlaps with the search point.
	 * Other distance types can only be specified directly on the index implementations. 
	 * @param center center point
	 * @param k number of neighbors
	 * @return list of nearest neighbors
	 */
	QueryIteratorKNN<RectangleEntryDist<T>> queryKNN(double[] center, int k);

	/**
	 * Finds the nearest neighbor.
	 * This uses a custom distance function for distances to rectangles.
	 * @param center center point
	 * @param k number of neighbors
	 * @param distFn distance function
	 * @param filterFn a filter function to filter out entries before they are returned
	 * @return list of nearest neighbors
	 */
	QueryIteratorKNN<RectangleEntryDist<T>> queryKNN(
			double[] center, int k, RectangleDistanceFunction distFn, Predicate<T> filterFn);
}