/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.util;

import java.lang.reflect.Array;

/**
 * Utility class.
 *
 * @author ztilmann
 */
public class Refs {

  private Refs() {}

  /**
   * This method has two purposes: avoid compiler lint warnings and enable object pooling in a
   * future release.
   *
   * @param c array type
   * @param size array size
   * @return new array
   * @param <T> array type
   */
  @SuppressWarnings("unchecked")
  public static <T> T[] newArray(Class<T> c, int size) {
    return (T[]) Array.newInstance(c, size);
  }
}
