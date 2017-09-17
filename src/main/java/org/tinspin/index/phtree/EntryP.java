/*
 * Copyright 2017 Tilmann Zaeschke
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
package org.tinspin.index.phtree;

import java.util.Arrays;

import org.tinspin.index.PointEntry;

public class EntryP<T> implements PointEntry<T> {
	protected double[] point;
	private T val;
	
	public EntryP(double[] point, T val) {
		this.point = point;
		this.val = val;
	}

	@Override
	public double[] point() {
		return point;
	}

	@Override
	public T value() {
		return val;
	}
	
	@Override
	public String toString() {
		return Arrays.toString(point) + ";v=" + val;
	}
}