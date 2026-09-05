/*
 * Copyright 2009-2023 Tilmann Zaeschke. All rights reserved.
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
package org.tinspin.index.util;

/**
 * Common interface for MinHeap implementations.
 *
 * @param <T> entry type.
 */
public interface MinHeapI<T> {

  /**
   * Add entry.
   *
   * @param value entry
   */
  void push(T value);

  /** Remove first entry. */
  void popMin();

  /**
   * Look at first entry.
   *
   * @return first entry
   */
  T peekMin();

  /**
   * Heap size.
   *
   * @return number of entries.
   */
  int size();

  /**
   * Is empty.
   *
   * @return 'true' if empty
   */
  boolean isEmpty();

  /** Remove all entries. */
  void clear();
}
