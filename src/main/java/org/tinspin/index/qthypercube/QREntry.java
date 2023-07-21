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
package org.tinspin.index.qthypercube;

import java.util.Arrays;

import org.tinspin.index.BoxEntry;

public class QREntry<T> implements BoxEntry<T> {

	private double[] pointL;
	private double[] pointU;
	private final T value;
	
	public QREntry(double[] keyL, double[] keyU, T value) {
		this.pointL = keyL;
		this.pointU = keyU;
		this.value = value;
	}
	
	@Override
	public double[] lower() {
		return pointL;
	}
	
	@Override
	public double[] upper() {
		return pointU;
	}
	
	@Override
	public T value() {
		return value;
	}

	public boolean isExact(QREntry<T> e) {
		return QUtil.isPointEqual(pointL, e.lower()) 
				&& QUtil.isPointEqual(pointU, e.upper());
	}

	@Override
	public String toString() {
		return "p=" + Arrays.toString(pointL) + "/" + Arrays.toString(pointU) + 
				"  v=" + value + " " + System.identityHashCode(this);
	}

	public void setKey(double[] newPointL, double[] newPointU) {
		this.pointL = newPointL;
		this.pointU = newPointU;
	}

}
