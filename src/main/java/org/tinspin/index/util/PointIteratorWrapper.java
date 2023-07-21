package org.tinspin.index.util;

import org.tinspin.index.PointEntry;

import java.util.Iterator;
import java.util.function.BiFunction;

import static org.tinspin.index.Index.*;

public class PointIteratorWrapper<E> implements PointIterator<E> {

    private Iterator<PointEntry<E>> it;
    private final BiFunction<double[], double[], Iterator<PointEntry<E>>> fn;

    public PointIteratorWrapper(double[] min, double[] max, BiFunction<double[], double[], Iterator<PointEntry<E>>> f) {
        fn = f;
        reset(min, max);
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
