package org.tinspin.index.util;

import org.tinspin.index.QueryIterator;

import java.util.Iterator;
import java.util.function.BiFunction;

public class QueryIteratorWrapper<E> implements QueryIterator<E> {

    private Iterator<E> it;
    private final BiFunction<double[], double[], Iterator<E>> fn;

    public QueryIteratorWrapper(double[] min, double[] max, BiFunction<double[], double[], Iterator<E>> f) {
        fn = f;
        reset(min, max);
    }

    @Override
    public boolean hasNext() {
        return it.hasNext();
    }

    @Override
    public E next() {
        return it.next();
    }

    @Override
    public void reset(double[] min, double[] max) {
        it = fn.apply(min, max);
    }
}
