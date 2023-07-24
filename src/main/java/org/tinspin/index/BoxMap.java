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

public interface BoxMap<T> extends Index {

	/**
	 * Insert a box.
	 * @param min minimum corner
	 * @param max maximum corner
	 * @param value value
	 */
	void insert(double[] min, double[] max, T value);

	/**
	 * Remove an entry.
	 * @param min minimum corner
	 * @param max maximum corner
	 * @return the value of the entry or null if the entry was not found
	 */
	T remove(double[] min, double[] max);

	/**
	 * Update the position of an entry.
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
	 * @param center center point
	 * @param k number of neighbors
	 * @return list of nearest neighbors
	 */
	BoxIteratorKnn<T> queryKnn(double[] center, int k);
}