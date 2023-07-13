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

import java.util.Arrays;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.function.ToIntBiFunction;

/**
 * Min-max heap implementation based on:
 * <a href="https://en.wikipedia.org/wiki/Min-max_heap">https://en.wikipedia.org/wiki/Min-max_heap</a>
 *
 * @param <T>
 */
public class MinMaxHeapZ<T> implements MinMaxHeapI<T> {

    private static final int DEFAULT_SIZE = 16;
    // Data. The first slot is left empty, i.e. the first data element is at [1]!
    private T[] data;
    // index of first free entry
    private int size = 0;
    private final Comparator<T> comp;

    @SuppressWarnings("unchecked")
    private MinMaxHeapZ(int capacity, Comparator<T> comp) {
        data = (T[]) new Object[capacity];
        this.comp = comp;
    }

    public static <T extends Comparable<T>> MinMaxHeapZ<T> create() {
        return new MinMaxHeapZ<>(DEFAULT_SIZE, Comparable::compareTo);
    }

    public static <T> MinMaxHeapZ<T> create(ToIntBiFunction<T, T> less) {
        return new MinMaxHeapZ<>(DEFAULT_SIZE, less::applyAsInt);
    }

    private static boolean isMinLevel(int index) {
        //        // 0 -> 0; 1 -> 1; 2-3 -> 2; 4-7 -> 3; 8-15 -> 4; ...
        //        int highestBit = 32 - Integer.numberOfLeadingZeros(index);
        //        // min levels are 'odd'
        //        return (highestBit & 1) != 0;
        // We can remove the "32 - x" because it doesn't change the trailing bit
        return (Integer.numberOfLeadingZeros(index) & 1) != 0;
    }

    private boolean hasChildren(int i) {
        return i * 2 <= size;
    }

    private boolean isGrandchildOf(int m, int i) {
        return m >> 2 == i;
    }

    private void swap(int i1, int i2) {
        T v = data[i1];
        data[i1] = data[i2];
        data[i2] = v;
    }

    private int parent(int i) {
        return i >> 1;
    }

    private boolean hasGrandparent(int i) {
        return i >> 2 > 0;
    }

    private int grandparent(int i) {
        return i >> 2;
    }

    private int indexOfSmallestChildOrGrandchild(int index) {
        int end = end();
        int start = index * 4;
        int min12 = -1;
        int min34 = -1;
        if (start + 3 < end) {
            // 4 grand children
            min12 = comp.compare(data[start], data[start + 1]) < 0 ? start : start + 1;
            min34 = comp.compare(data[start + 2], data[start + 3]) < 0 ? start + 2 : start + 3;
        } else if (start + 2 < end) {
            // 3 grand children
            min12 = comp.compare(data[start], data[start + 1]) < 0 ? start : start + 1;
            min34 = start + 2;
        } else if (start + 1 < end) {
            // 2 grand children + 1 children
            min12 = comp.compare(data[start], data[start + 1]) < 0 ? start : start + 1;
            min34 = index * 2 + 1;
        } else if (start < end) {
            // 1 grand child + 1 child
            min12 = start;
            min34 = index * 2 + 1;
        } else if (index * 2 + 1 < end) {
            // 2 children
            min12 = index * 2;
            min34 = min12 + 1;
        } else {
            // 1 child
            return index * 2;
        }
        return comp.compare(data[min12], data[min34]) < 0 ? min12 : min34;
    }

    private int indexOfLargestChildOrGrandchild(int index) {
        int end = end();
        int start = index * 4;
        int max12 = -1;
        int max34 = -1;
        if (start + 3 < end) {
            // 4 grand children
            max12 = comp.compare(data[start], data[start + 1]) > 0 ? start : start + 1;
            max34 = comp.compare(data[start + 2], data[start + 3]) > 0 ? start + 2 : start + 3;
        } else if (start + 2 < end) {
            // 3 grand children
            max12 = comp.compare(data[start], data[start + 1]) > 0 ? start : start + 1;
            max34 = start + 2;
        } else if (start + 1 < end) {
            // 2 grand children + 1 children
            max12 = comp.compare(data[start], data[start + 1]) > 0 ? start : start + 1;
            max34 = index * 2 + 1;
        } else if (start < end) {
            // 1 grand child + 1 child
            max12 = start;
            max34 = index * 2 + 1;
        } else if (index * 2 + 1 < end) {
            // 2 children
            max12 = index * 2;
            max34 = max12 + 1;
        } else {
            // 1 child
            return index * 2;
        }
        return comp.compare(data[max12], data[max34]) > 0 ? max12 : max34;
    }

    private void pushDown(int m) {
        while (hasChildren(m)) {
            int i = m;
            if (isMinLevel(i)) {
                m = indexOfSmallestChildOrGrandchild(i);
                if (comp.compare(data[m], data[i]) < 0) {
                    swap(m, i);
                    if (isGrandchildOf(m, i)) {
                        if (comp.compare(data[m], data[parent(m)]) > 0) {
                            swap(m, parent(m));
                        }
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            } else {
                m = indexOfLargestChildOrGrandchild(i);
                if (comp.compare(data[m], data[i]) > 0) {
                    swap(m, i);
                    if (isGrandchildOf(m, i)) {
                        if (comp.compare(data[m], data[parent(m)]) < 0) {
                            swap(m, parent(m));
                        }
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            }
        }
    }

//    private void pushDown2(int i) {
//        if (isMinLevel(i)) {
//            pushDownMin(i);
//        } else {
//            pushDownMax(i);
//        }
//    }
//
//    private void pushDownMin(int i) {
//        if (hasChildren(i)) {
//            int m = indexOfSmallestChildOrGrandchild(i);
//            if (isGrandchildOf(m, i)) {
//                if (data[m].compareTo(data[i]) < 0) {
//                    swap(m, i);
//                    if (data[m].compareTo(data[parent(m)]) > 0) {
//                        swap(m, parent(m));
//                    }
//                    pushDown(m);
//                }
//
//            } else if (data[m].compareTo(data[i]) < 0) {
//                swap(m, i);
//            }
//        }
//    }
//
//    private void pushDownMax(int i) {
//        if (hasChildren(i)) {
//            int m = indexOfLargestChildOrGrandchild(i);
//            if (isGrandchildOf(m, i)) {
//                if (data[m].compareTo(data[i]) > 0) {
//                    swap(m, i);
//                    if (data[m].compareTo(data[parent(m)]) < 0) {
//                        swap(m, parent(m));
//                    }
//                    pushDown(m);
//                }
//            } else if (data[m].compareTo(data[i]) > 0) {
//                swap(m, i);
//            }
//        }
//    }

    private void pushUp(int index) {
        if (index != 1) { // is not root?
            if (isMinLevel(index)) {
                if (comp.compare(data[index], data[parent(index)]) > 0) {
                    swap(index, parent(index));
                    pushUpMax(parent(index));
                } else {
                    pushUpMin(index);
                }
            } else {
                if (comp.compare(data[index], data[parent(index)]) < 0) {
                    swap(index, parent(index));
                    pushUpMin(parent(index));
                } else {
                    pushUpMax(index);
                }
            }
        }
    }

    private void pushUpMin(int index) {
        while (hasGrandparent(index) && comp.compare(data[index], data[grandparent(index)]) < 0) {
            swap(index, grandparent(index));
            index = grandparent(index);
        }
    }

    private void pushUpMax(int index) {
        while (hasGrandparent(index) && comp.compare(data[index], data[grandparent(index)]) > 0) {
            swap(index, grandparent(index));
            index = grandparent(index);
        }
    }


    private int end() {
        return size + 1;
    }

    @Override
    public void push(T value) {
        if (size == 0) {
            data[1] = value;
            size++;
            return;
        }

        if (size + 1 >= data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }

        data[end()] = value;
        size++;
        pushUp(size);
    }

    @Override
    public void popMin() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        int end = end();
        T value = data[end - 1];
        data[end - 1] = null;
        size--;

        if (size == 0) {
            return;
        }
        data[1] = value;

        pushDown(1);
    }

    @Override
    public void popMax() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        if (size == 1) {
            size--;
            data[1] = null;
            return;
        } else if (size == 2) {
            size--;
            data[2] = null;
            return;
        }
        int max = comp.compare(data[2], data[3]) > 0 ? 2 : 3;

        if (size == 3) {
            size--;
            if (max == 2) {
                data[2] = data[3];
            }
            data[3] = null;
            return;
        }

        int end = end();
        T value = data[end - 1];
        data[end - 1] = null;
        size--;

        data[max] = value;
        pushDown(max);
    }

    @Override
    public T peekMin() {
        if (size < 1) {
            throw new NoSuchElementException();
        }
        return data[1];
    }

    @Override
    public T peekMax() {
        if (size < 1) {
            throw new NoSuchElementException();
        }
        if (size == 1) {
            return data[1];
        } else if (size == 2) {
            return data[2];
        }
        return comp.compare(data[2], data[3]) >= 0 ? data[2] : data[3];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    public String print() {
        StringBuilderLn s = new StringBuilderLn();
        int x = 2;
        for (int i = 1; i <= size; i++) {
            if (i % x == 0) {
                s.appendLn();
                x *= 2;
            }
            s.append(data[i] + "   ");
        }
        s.appendLn();
        return s.toString();
    }

    @SuppressWarnings("unchecked")
    public void clear() {
        size = 0;
        if (data.length > DEFAULT_SIZE) {
            data = (T[]) new Object[DEFAULT_SIZE];
        } else {
            Arrays.fill(data, null);
        }
    }
}
