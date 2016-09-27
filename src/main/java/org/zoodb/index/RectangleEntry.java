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

/**
 * This represents a rectangle entry.
 * 
 * @param <T>
 */
public interface RectangleEntry<T> {

	/**
	 * @return The lower left corner of the entry. 
	 */
	double[] lower();
	
	/**
	 * @return The upper right corner of the entry. 
	 */
	double[] upper();
	
	/**
	 * @return The value associated with the rectangle or point. 
	 */
	T value();

}
