package org.tinspin.index.rtree;

public interface Filter {
	
	/**
	 * Doesn't filter anything.
	 */
	public static final Filter ALL = new Filter() {
		@Override
		public boolean intersects(double[] min, double[] max) {
			return true;
		}
	};
	
	/**
	 * Intersects is used for the tree nodes and should only check for
	 * intersection.
	 * 
	 * @param min  Min bound of rectangle,
	 * @param max  Max bound of rectangle,
	 * @return     True if there could exist a matching element in given range.
	 */
	boolean intersects(double[] min, double[] max);

	/**
	 * This is used on the actual entries. Anything that matches will be
	 * returned.
	 * 
	 * @param min
	 * @param max
	 * @return
	 */
	default boolean matches(double[] min, double[] max) {
		return intersects(min, max);
	}
	
	public static class RectangleIntersectFilter implements Filter {

		private final double[] min;
		private final double[] max;

		public RectangleIntersectFilter(double[] min, double[] max) {
			this.min = min;
			this.max = max;
		}

		@Override
		public boolean intersects(double[] min, double[] max) {
			boolean inter = true;
			for (int i = 0; i < min.length; i++) {
				inter &= this.max[i] > min[i];
				inter &= this.min[i] < max[i];
			}
			return inter;
		}

	}
}
