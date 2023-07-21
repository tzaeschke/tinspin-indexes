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
		QueryIterator<T> reset(double[] min, double[] max);
	}


	interface PointIterator<T> extends QueryIterator<PointEntry<T>> {
	}

	interface BoxIterator<T> extends QueryIterator<BoxEntry<T>> {
	}

	interface QueryIteratorKnn<T> extends Iterator<T> {
		QueryIteratorKnn<T> reset(double[] center, int k);
	}

	interface PointIteratorKnn<T> extends QueryIteratorKnn<PointEntryDist<T>> {
	}

	interface BoxIteratorKnn<T> extends QueryIteratorKnn<BoxEntryDist<T>> {
	}
}