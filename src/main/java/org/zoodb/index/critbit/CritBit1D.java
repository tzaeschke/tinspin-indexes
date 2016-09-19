/*
 * Copyright 2009-2014 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of ZooDB.
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
package org.zoodb.index.critbit;

import org.zoodb.index.critbit.CritBit.FullIterator;
import org.zoodb.index.critbit.CritBit.QueryIterator;

/**
 * 
 * @author Tilmann Zaeschke
 */
public interface CritBit1D<V> {

	/** @see CritBit#put(long[], Object) */
	V put(long[] key, V value);

	/** @see CritBit#contains(long[]) */
	boolean contains(long[] key);

	/** @see CritBit#query(long[], long[]) */
	QueryIterator<V> query(long[] min, long[] max);

	/** @see CritBit#size() */
	int size();

	/** @see CritBit#remove(long[]) */
	V remove(long[] key);

	/** @see CritBit#printTree() */
	void printTree();

	/** @see CritBit#get(long[]) */
	V get(long[] key);

	/** @see CritBit#iterator() */
	FullIterator<V> iterator();
}
