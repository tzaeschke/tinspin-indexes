package org.tinspin.index.util;

import org.tinspin.index.BoxEntry;

import java.util.Iterator;
import java.util.function.BiFunction;

import static org.tinspin.index.Index.BoxIterator;

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
    public void reset(double[] min, double[] max) {
        it = fn.apply(min, max);
    }
}
