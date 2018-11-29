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
package org.tinspin.index.covertree;

import org.tinspin.index.PointEntry;

public class Point<T> implements PointEntry<T> {

	private double[] point;
	private T value;
	
	Point(double[] data, T value) {
		this.point = data;
		this.value = value;
	}
	
	@Override
	public double[] point() {
		return point;
	}

	@Override
	public T value() {
		return this.value;
	}

	public void set(Point<T> point) {
		this.point = point.point();
		this.value = point.value();
	}

	public void set(double[] point, T val) {
		this.point = point;
		this.value = val;
	}
	
}
