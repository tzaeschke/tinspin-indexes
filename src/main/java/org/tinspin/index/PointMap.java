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

/**
 * A common interface for spatial indexes (maps) that use points as keys.
 * This interface is somewhat inconsistent because it suggests that
 * implementations acrt as "maps" which means that a given keys
 * can exist only once and is overwritten when a new entry with the same key is added.
 * <p>
 * However, most implementations in this library (except for the PH-Tree) act as
 * multimaps which means they allow multiple entries with identical keys.
 *
 * @param <T> Type of the value associated with the point key.
 */
public interface PointMap<T> extends Index {

	/**
	 * Insert a point.
	 * @param key point
	 * @param value value
	 */
	void insert(double[] key, T value);

	/**
	 * Remove a point entry.
	 * @param point the point
	 * @return the value of the entry or null if the entry was not found
	 */
	T remove(double[] point);

	/**
	 * Update the position of an entry.
	 * @param oldPoint old position
	 * @param newPoint new position
	 * @return the value of the entry or null if the entry was not found
	 */
	T update(double[] oldPoint, double[] newPoint);

	/**
	 * Lookup an entry, using exact match.
	 * @param point the point
	 * @return the value of the entry or null if the entry was not found
	 */
	T queryExact(double[] point);
	
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
	 * @param center center point
	 * @return the nearest neighbor
	 */
	default PointEntryDist<T> query1nn(double[] center) {
		PointIteratorKnn<T> it = queryKnn(center, 1);
		if (it.hasNext()) {
			return it.next();
		}
		return null;
	}

	/**
	 * Finds the nearest neighbor. This uses euclidean distance. 
	 * Other distance types can only be specified directly on the index implementations. 
	 * @param center center point
	 * @param k number of neighbors
	 * @return list of nearest neighbors
	 */
	PointIteratorKnn<T> queryKnn(double[] center, int k);

}