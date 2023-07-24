/*
 * Copyright 2016-2023 Tilmann Zaeschke
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

import java.util.Iterator;
import java.util.function.BiFunction;

import static org.tinspin.index.Index.BoxIterator;

import static org.tinspin.index.Index.*;

public class BoxIteratorWrapper<E> implements BoxIterator<E> {

    private final BiFunction<double[], double[], Iterator<BoxEntry<E>>> fn;
    private Iterator<BoxEntry<E>> it;

    public BoxIteratorWrapper(double[] min, double[] max, BiFunction<double[], double[], Iterator<BoxEntry<E>>> f) {
        fn = f;
        reset(min, max);
    }

    @Override
    public boolean hasNext() {
        return it.hasNext();
    }

    @Override
    public BoxEntry<E> next() {
        return it.next();
    }

    @Override
    public BoxIterator<E> reset(double[] min, double[] max) {
        it = fn.apply(min, max);
        return this;
    }
}
