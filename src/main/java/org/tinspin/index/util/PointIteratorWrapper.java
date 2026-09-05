package org.tinspin.index.util;

import static org.tinspin.index.Index.*;

import java.util.Iterator;
import java.util.function.BiFunction;

/**
 * Wrapper for point iterator.
 *
 * @param <E> entry type.
 */
public class PointIteratorWrapper<E> implements PointIterator<E> {

  private Iterator<PointEntry<E>> it;
  private final BiFunction<double[], double[], Iterator<PointEntry<E>>> fn;

  /**
   * Create point iterator wrapper instance.
   *
   * @param min min of window
   * @param max max of window
   * @param f original iterator.
   */
  public PointIteratorWrapper(
      double[] min, double[] max, BiFunction<double[], double[], Iterator<PointEntry<E>>> f) {
    fn = f;
    it = fn.apply(min, max);
  }

  @Override
  public boolean hasNext() {
    return it.hasNext();
  }

  @Override
  public PointEntry<E> next() {
    return it.next();
  }

  @Override
  public PointIterator<E> reset(double[] min, double[] max) {
    it = fn.apply(min, max);
    return this;
  }
}
