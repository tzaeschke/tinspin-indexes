/*
 * Copyright 2017 Christophe Schmaltz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tinspin.index.rtree;

import static org.tinspin.index.Index.*;

/** Filter function interface for R-tree queries. */
public interface Filter {

  /** Doesn't filter anything. */
  Filter ALL = (min, max) -> true;

  /**
   * Intersects is used for the tree nodes and should only check for intersection.
   *
   * @param min Min bound of rectangle,
   * @param max Max bound of rectangle,
   * @return True if there could exist a matching element in given range.
   */
  boolean intersects(double[] min, double[] max);

  /**
   * This is used on the actual entries. Anything that matches will be returned.
   *
   * @param entry An entry with an existing value()
   * @return True if this entry is part of the result set
   */
  default boolean matches(BoxEntry<?> entry) {
    return intersects(entry.min(), entry.max());
  }

  /** Rectangular region filter. */
  class RectangleIntersectFilter implements Filter {

    private final double[] lower;
    private final double[] upper;

    /**
     * Constructor.
     *
     * @param lower window min
     * @param upper window max
     */
    public RectangleIntersectFilter(double[] lower, double[] upper) {
      this.lower = lower;
      this.upper = upper;
    }

    @Override
    public boolean intersects(double[] min, double[] max) {
      boolean inter = true;
      for (int i = 0; i < min.length; i++) {
        inter &= this.upper[i] > min[i];
        inter &= this.lower[i] < max[i];
      }
      return inter;
    }
  }

  /** Union of different "ranges". */
  class UnionFilter implements Filter {

    private final Filter filter1;
    private final Filter filter2;

    /**
     * Constructor.
     *
     * @param filter1 filter 1
     * @param filter2 filter 2
     */
    public UnionFilter(Filter filter1, Filter filter2) {
      this.filter1 = filter1;
      this.filter2 = filter2;
    }

    @Override
    public boolean intersects(double[] min, double[] max) {
      return filter1.intersects(min, max) || filter2.intersects(min, max);
    }

    @Override
    public boolean matches(BoxEntry<?> entry) {
      return filter1.matches(entry) || filter2.matches(entry);
    }

    /**
     * Chaining another filter.
     *
     * @param anotherFilter the filter to merge
     * @return resulting merged filter
     */
    public UnionFilter union(Filter anotherFilter) {
      return new UnionFilter(this, anotherFilter);
    }
  }
}
