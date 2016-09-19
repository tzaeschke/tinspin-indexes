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
package org.zoodb.index.quadtree;

import java.util.Arrays;

public class QEntry<T> {

	private double[] point;
	private final T value;
	
	public QEntry(double[] key, T value) {
		this.point = key;
		this.value = value;
	}
	
	public double[] getPoint() {
		return point;
	}
	
	public T getValue() {
		return value;
	}

	public boolean enclosedBy(double[] min, double[] max) {
		return QUtil.isPointEnclosed(point, min, max);
	}

	public boolean isExact(QEntry<T> e) {
		return QUtil.isPointEqual(point, e.getPoint());
	}

	@Override
	public String toString() {
		return "p=" + Arrays.toString(point) + "  v=" + value + " " + 
				System.identityHashCode(this);
	}

	public void setKey(double[] newPoint) {
		this.point = newPoint;
	}

}
