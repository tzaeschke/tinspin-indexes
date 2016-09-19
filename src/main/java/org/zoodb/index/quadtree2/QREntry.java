/*
 * Copyright 2016 Tilmann Zäschke
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
package org.zoodb.index.quadtree2;

import java.util.Arrays;

public class QREntry<T> {

	private double[] pointL;
	private double[] pointU;
	private final T value;
	
	public QREntry(double[] keyL, double[] keyU, T value) {
		this.pointL = keyL;
		this.pointU = keyU;
		this.value = value;
	}
	
	public double[] getPointL() {
		return pointL;
	}
	
	public double[] getPointU() {
		return pointU;
	}
	
	public T getValue() {
		return value;
	}

	public boolean enclosedByXX(double[] min, double[] max) {
		return QUtil.isRectEnclosed(this.pointL, this.pointU, min, max);
	}

	public boolean isExact(QREntry<T> e) {
		return QUtil.isPointEqual(pointL, e.getPointL()) 
				&& QUtil.isPointEqual(pointU, e.getPointU());
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
