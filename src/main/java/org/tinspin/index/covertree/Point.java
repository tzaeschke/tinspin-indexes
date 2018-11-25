package org.tinspin.index.covertree;

import org.tinspin.index.PointEntry;

public class Point<T> implements PointEntry<T> {

	private final double[] point;
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
	
}
