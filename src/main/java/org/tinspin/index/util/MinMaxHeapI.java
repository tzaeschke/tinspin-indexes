package org.tinspin.index.util;

public interface MinMaxHeapI<T> {

    void push(T value);

    void popMin();
    void popMax();

    T peekMin();
    T peekMax();

    int size();

    boolean isEmpty();
}
