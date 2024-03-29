/*
 * Copyright 2016 Tilmann Zaeschke
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
package org.tinspin.index.phtree;

import ch.ethz.globis.phtree.PhEntryDistF;
import ch.ethz.globis.phtree.PhEntryF;
import ch.ethz.globis.phtree.PhTreeMultiMapF2;
import ch.ethz.globis.phtree.PhTreeMultiMapF2.PhIteratorF;
import ch.ethz.globis.phtree.PhTreeMultiMapF2.PhKnnQueryF;
import ch.ethz.globis.phtree.PhTreeMultiMapF2.PhQueryF;
import org.tinspin.index.*;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Multimap version of the PH-Tree.
 *
 * @param <T> The value type associated with each entry.
 */
public class PHTreeMMP<T> implements PointMultimap<T> {

    private final PhTreeMultiMapF2<T> tree;

    private PHTreeMMP(int dims) {
        this.tree = PhTreeMultiMapF2.create(dims);
    }

    public static <T> PHTreeMMP<T> create(int dims) {
        return new PHTreeMMP<>(dims);
    }

    @Override
    public int getDims() {
        return tree.getDim();
    }

    @Override
    public int size() {
        return tree.size();
    }

    @Override
    public void clear() {
        tree.clear();
    }

    @Override
    public Stats getStats() {
        return new PHStats(tree.getStats(), tree.getDim());
    }

    @Override
    public int getNodeCount() {
        return tree.getStats().getNodeCount();
    }

    @Override
    public int getDepth() {
        return tree.getStats().getBitDepth();
    }

    @Override
    public String toStringTree() {
        return tree.toStringTree();
    }

    @Override
    public void insert(double[] key, T value) {
        tree.put(key, value);
    }

    @Override
    public boolean remove(double[] key, T value) {
        return tree.remove(key, value);
    }

    @Override
    public boolean removeIf(double[] point, Predicate<PointEntry<T>> condition) {
        for (T t : tree.get(point)) {
            if (condition.test(new PointEntry<>(point, t))) {
                return tree.remove(point, t);
            }
        }
        return false;
    }

    @Override
    public boolean update(double[] oldPoint, double[] newPoint, T value) {
        return tree.update(oldPoint, value, newPoint);
    }

    @Override
    public boolean contains(double[] point, T value) {
        for (T t : tree.get(point)) {
            if (Objects.equals(value, t)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PointIterator<T> queryExactPoint(double[] key) {
        return new IteratorPlain(key);
    }

    @Override
    public PointIterator<T> query(double[] min, double[] max) {
        return new IteratorWQ<>(tree.query(min, max));
    }

    @Override
    public PointIterator<T> iterator() {
        return new ExtentWrapper();
    }

    @Override
    public PointIteratorKnn<T> queryKnn(double[] center, int k) {
        return new IteratorKnn<>(tree.nearestNeighbour(k, center));
    }

    @Override
    public PointIteratorKnn<T> queryKnn(double[] center, int k, PointDistance distFn) {
        throw new UnsupportedOperationException();
    }

    private class ExtentWrapper implements PointIterator<T> {

        private PhIteratorF<T> iter;

        private ExtentWrapper() {
            reset(null, null);
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public PointEntry<T> next() {
            //This reuses the entry object, but we have to clone the arrays...
            PhEntryF<T> e = iter.nextEntryReuse();
            return new PointEntry<>(e.getKey().clone(), e.getValue());
        }

        @Override
        public PointIterator<T> reset(double[] min, double[] max) {
            if (min != null || max != null) {
                throw new UnsupportedOperationException("min/max must be `null`");
            }
            iter = tree.queryExtent();
            return this;
        }

    }

    private static class IteratorWQ<T> implements PointIterator<T> {

        private final PhQueryF<T> iter;

        private IteratorWQ(PhQueryF<T> iter) {
            this.iter = iter;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public PointEntry<T> next() {
            //This reuses the entry object, but we have to clone the arrays...
            PhEntryF<T> e = iter.nextEntryReuse();
            return new PointEntry<>(e.getKey().clone(), e.getValue());
        }

        @Override
        public PointIterator<T> reset(double[] min, double[] max) {
            iter.reset(min, max);
            return this;
        }

    }

    private class IteratorPlain implements PointIterator<T> {

        private Iterator<T> iter;
        private double[] key;

        private IteratorPlain(double[] key) {
            reset(key, null);
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public PointEntry<T> next() {
            return new PointEntry<>(key, iter.next());
        }

        @Override
        public PointIterator<T> reset(double[] point, double[] mustBeNull) {
            if (mustBeNull != null) {
                throw new UnsupportedOperationException("second argument must be `null`");
            }
            iter = tree.get(point).iterator();
            key = point;
            return this;
        }
    }

    private static class IteratorKnn<T> implements PointIteratorKnn<T> {

        private final PhKnnQueryF<T> iter;

        private IteratorKnn(PhKnnQueryF<T> iter) {
            this.iter = iter;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public PointEntryKnn<T> next() {
            //This reuses the entry object, but we have to clone the arrays...
            PhEntryDistF<T> e = iter.nextEntryReuse();
            return new PointEntryKnn<>(e.getKey().clone(), e.getValue(), e.dist());
        }

        @Override
        public IteratorKnn<T> reset(double[] center, int k) {
            iter.reset(k, null, center);
            return this;
        }

    }

}
