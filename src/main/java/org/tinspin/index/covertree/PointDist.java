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

import java.util.Comparator;

import org.tinspin.index.PointEntryDist;

public class PointDist<T> extends Point<T> implements PointEntryDist<T> {

	public static final Comparator<PointDist<?>> COMPARATOR = 
			(o1, o2) -> Double.compare(o1.dist,  o2.dist);
	
	private double dist;
	
	PointDist(double[] data, T value, double dist) {
		super(data, value);
		this.dist = dist;
	}

	@Override
	public double dist() {
		return dist;
	}

	public void set(Point<T> point, double dist) {
		super.set(point);
		this.dist = dist;
	}

	public void set(double[] point, T val, double dist) {
		super.set(point, val);
		this.dist = dist;
	}
	
}
