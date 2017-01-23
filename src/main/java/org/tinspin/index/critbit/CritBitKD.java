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
package org.tinspin.index.critbit;

import org.tinspin.index.critbit.CritBit.QueryIteratorKD;

/**
 * 
 * @author Tilmann Zaeschke
 * 
 * @param <V> value type
 */
public interface CritBitKD<V> {

	/** @see CritBit#putKD(long[], Object) */
	V putKD(long[] key, V value);

	/** @see CritBit#containsKD(long[]) */
	boolean containsKD(long[] key);

	/** @see CritBit#size() */  
	int size();

	/** @see CritBit#queryKD(long[], long[]) */  
	QueryIteratorKD<V> queryKD(long[] lowerLeft, long[] upperRight);

	/** @see CritBit#removeKD(long[]) */  
	V removeKD(long[] key);

	/** @see CritBit#printTree() */  
	void printTree();

	/** @see CritBit#getKD(long[]) */  
	V getKD(long[] key);

}
