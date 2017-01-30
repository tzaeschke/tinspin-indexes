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
package org.tinspin.index.qtplain;

import java.util.Comparator;

import org.tinspin.index.RectangleEntryDist;

public class QREntryDist<T> extends QREntry<T> implements RectangleEntryDist<T> {
	private double distance;
	
	public QREntryDist(QREntry<T> e, double dist) {
		super(e.lower(), e.upper(), e.value());
		this.distance = dist;
	}
	
	@Override
	public double dist() {
		return distance;
	}
	
	public static final QEntryComparator COMP = new QEntryComparator();
	
	static class QEntryComparator implements Comparator<QREntryDist<?>> {

	    /**
	    * Compares the two specified MBRs according to
	    * the sorting dimension and the sorting co-ordinate for the dimension
	     * of this Comparator.
	    *
	    * @param o1 the first SpatialPoint
	    * @param o2 the second SpatialPoint
	    * @return a negative integer, zero, or a positive integer as the
	    *         first argument is less than, equal to, or greater than the
	    *         second.
	    */
	    @Override
	    public int compare(QREntryDist<?> o1, QREntryDist<?> o2) {
	        double d = o1.dist() - o2.dist();
	        return d < 0 ? -1 : (d > 0 ? 1 : 0);
	    }
	}

}