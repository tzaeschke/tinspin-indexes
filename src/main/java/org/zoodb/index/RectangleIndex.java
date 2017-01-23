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
package org.zoodb.index;

import java.util.Iterator;

public interface RectangleIndex<T> extends Index<T> {

	/**
	 * Insert a rectangle.
	 * @param lower minimum corner
	 * @param upper maximum corner
	 * @param value value
	 */
	void insert(double[] lower, double[] upper, T value);

	/**
	 * Remove an entry.
	 * @param lower minimum corner
	 * @param upper maximum corner
	 * @return the value of the entry or null if the entry was not found
	 */
	T remove(double[] lower, double[] upper);

	/**
	 * Update the position of an entry.
	 * @param lo1 old min
	 * @param up1 old max
	 * @param lo2 new min
	 * @param up2 new max
	 * @return the value, or null if the entries was not found
	 */
	public T update(double[] lo1, double[] up1, double[] lo2, double[] up2);

	/**
	 * Lookup an entry, using exact match.
	 * @param lower minimum corner
	 * @param upper maximum corner
	 * @return the value of the entry or null if the entry was not found
	 */
	T queryExact(double[] lower, double[] upper);
	
	/**
	 * @return An iterator over all entries.
	 */
	QueryIterator<RectangleEntry<T>> iterator();

	/**
	 * @return All rectangles that intersect with the query rectangle.
	 */
	QueryIterator<RectangleEntry<T>> queryIntersect(double[] min, double[] max);

	/**
	 * Finds the nearest neighbor. This uses euclidean 'edge distance'.
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
	 * This uses euclidean 'edge distance', i.e. the distance to the edge of rectangle.
	 * Distance is 0 is the rectangle overlaps with the search point.
	 * Other distance types can only be specified directly on the index implementations. 
	 * @param center center point
	 * @param k number of neighbors
	 * @return list of nearest neighbors
	 */
	QueryIteratorKNN<RectangleEntryDist<T>> queryKNN(double[] center, int k);

}