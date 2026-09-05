/*
 * Copyright 2023 Tilmann Zaeschke
 *
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

/** StringBuilder with new-line functionality. */
public class StringBuilderLn {
  private final StringBuilder sb = new StringBuilder();

  /** Create new StringBuilder. */
  public StringBuilderLn() {
    // Nothing to do here
  }

  /**
   * Append string.
   *
   * @param str string
   * @return this StringBuilder
   */
  public StringBuilderLn append(String str) {
    this.sb.append(str);
    return this;
  }

  /**
   * Append object.
   *
   * @param obj object
   * @return this StringBuilder
   */
  public StringBuilderLn append(Object obj) {
    this.sb.append(obj);
    return this;
  }

  /**
   * Append newline.
   *
   * @return this StringBuilder
   */
  public StringBuilderLn appendLn() {
    this.sb.append(System.lineSeparator());
    return this;
  }

  /**
   * Append string and newline.
   *
   * @param str string
   * @return this StringBuilder
   */
  public StringBuilderLn appendLn(String str) {
    this.sb.append(str).append(System.lineSeparator());
    return this;
  }

  @Override
  public String toString() {
    return this.sb.toString();
  }
}
