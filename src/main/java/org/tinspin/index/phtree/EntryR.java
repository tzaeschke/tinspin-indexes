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

import org.tinspin.index.BoxEntry;

public class EntryR<T> implements BoxEntry<T> {
	protected double[] min;
	protected double[] max;
	private T val;
	
	public EntryR(double[] min, double[] max, T val) {
		this.min = min;
		this.max = max;
		this.val = val;
	}

	@Override
	public double[] lower() {
		return min;
	}

	@Override
	public double[] upper() {
		return max;
	}

	@Override
	public T value() {
		return val;
	}
	
	@Override
	public String toString() {
		double[] len = new double[min.length];
		Arrays.setAll(len, (i)->(max[i]-min[i]));
		return Arrays.toString(min) + "/" + Arrays.toString(max) + ";len=" + 
		Arrays.toString(len) + ";v=" + val;
	}

	public void set(double[] lower, double[] upper, T val) {
		this.min = lower;
		this.max = upper;
		this.val = val;
	}
}