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

import java.util.Objects;

/**
 * Mutable reference.
 * @param <T> reference type
 */
public class MutableRef<T> {
    private T t;

    /**
     * Create.
     */
    public MutableRef() {
        t = null;
    }

    /**
     * Create.
     * @param t initial value
     */
    public MutableRef(T t) {
        this.t = t;
    }

    /**
     * Get.
     * @return current value
     */
    public T get() {
        return t;
    }

    /**
     * Set.
     * @param t new value
     */
    public void set(T t) {
        this.t = t;
    }

    @Override
    public String toString() {
        return Objects.toString(t);
    }
}