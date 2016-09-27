package org.zoodb.index;

import java.util.Iterator;

public class PointIndexWrapper<T> implements PointIndex<T> {

	private final RectangleIndex<T> ind;
	
	private PointIndexWrapper(RectangleIndex<T> ind) {
		this.ind = ind;
	}
	
	public static <T> PointIndex<T> create(RectangleIndex<T> ind) {
		return new PointIndexWrapper<T>(ind);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Iterator<? extends PointEntry<T>> iterator() {
		return new PointIter(ind.iterator());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<? extends PointEntry<T>> query(double[] min, double[] max) {
		return new PointIter(ind.queryIntersect(min, max));
	}

	private static class PointW<T> implements PointEntry<T> {

		private double[] point;
		private T value;
		
		PointW(double[] point, T value) {
			this.point = point;
			this.value = value;
		}
		
		@Override
		public double[] point() {
			return point;
		}

		@Override
		public T value() {
			return value;
		}
		
	}
	
	private static class PointIter<T> implements Iterator<PointEntry<T>> {

		private final Iterator<RectangleEntry<T>> it;
		
		PointIter(Iterator<RectangleEntry<T>> it) {
			this.it = it;
		}
		
		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public PointEntry<T> next() {
			RectangleEntry<T> e = it.next();
			return new PointW<T>(e.lower(), e.value());
		}
		
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Iterator<? extends PointEntryDist<T>> queryKNN(double[] center, int k) {
		return new PointDIter(ind.queryKNN(center, k));
	}

	private static class PointDistW<T> extends PointW<T> implements PointEntryDist<T> {

		private double dist;
		
		PointDistW(double[] point, T value, double dist) {
			super(point, value);
			this.dist = dist;
		}
		
		@Override
		public double dist() {
			return dist;
		}
		
	}
	
	private static class PointDIter<T> implements Iterator<PointEntryDist<T>> {

		private final Iterator<RectangleEntryDist<T>> it;
		
		PointDIter(Iterator<RectangleEntryDist<T>> it) {
			this.it = it;
		}
		
		@Override
		public boolean hasNext() {
			return it.hasNext();
		}

		@Override
		public PointEntryDist<T> next() {
			RectangleEntryDist<T> e = it.next();
			return new PointDistW<>(e.lower(), e.value(), e.dist());
		}
		
	}
	
	@Override
	public void insert(double[] key, T value) {
		ind.insert(key, key, value);
	}

	@Override
	public T remove(double[] point) {
		return ind.remove(point, point);
	}

	@Override
	public T update(double[] oldPoint, double[] newPoint) {
		return ind.update(oldPoint, oldPoint, newPoint, newPoint);
	}

	@Override
	public T queryExact(double[] point) {
		return ind.queryExact(point, point);
	}

	@Override
	public int getDims() {
		return ind.getDims();
	}

	@Override
	public int size() {
		return ind.size();
	}

	@Override
	public void clear() {
		ind.clear();
	}

	@Override
	public Object getStats() {
		return ind.getStats();
	}

	@Override
	public int getNodeCount() {
		return ind.getNodeCount();
	}
}
