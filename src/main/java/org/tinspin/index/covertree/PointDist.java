package org.tinspin.index.covertree;

import org.tinspin.index.PointEntryDist;

public class PointDist<T> extends Point<T> implements PointEntryDist<T> {

	private double dist;
	
	PointDist(double[] data, T value, double dist) {
		super(data, value);
		this.dist = dist;
	}

	@Override
	public double dist() {
		return dist;
	}
	
}
