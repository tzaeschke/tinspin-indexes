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
	
}
