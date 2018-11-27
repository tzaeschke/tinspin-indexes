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
	
}
