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

public class MutableInt {
    private int i;

    public MutableInt() {
        i = 0;
    }

    public MutableInt(int i) {
        this.i = i;
    }

    public int get() {
        return i;
    }

    public void set(int i) {
        this.i = i;
    }

    public MutableInt inc() {
        ++i;
        return this;
    }

    public String toString() {
        return Integer.toString(i);
    }

    public void add(int i) {
        this.i += i;
    }
}