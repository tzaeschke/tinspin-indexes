package org.tinspin.index.util;

import java.util.Arrays;
import java.util.NoSuchElementException;

public class MinMaxHeapZ<T extends Comparable<T>> implements MinMaxHeapI<T> {

    // Data. The first slot is left empty, i.e. the first data element is at [1]!
    private T[] data;
    // index of first free entry
    private int size = 0;

    public MinMaxHeapZ(int capacity) {
        data = (T[]) new Comparable[capacity];
    }

    public static <T extends Comparable<T>> MinMaxHeapZ<T> create() {
        return new MinMaxHeapZ<>(16);
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
            // TODO replace mopareTo with something else...?  Avoid polymorphism ????
            min12 = data[start].compareTo(data[start + 1]) < 0 ? start : start + 1;
            min34 = data[start + 2].compareTo(data[start + 3]) < 0 ? start + 2 : start + 3;
        } else if (start + 2 < end) {
            // 3 grand children
            min12 = data[start].compareTo(data[start + 1]) < 0 ? start : start + 1;
            min34 = start + 2;
        } else if (start + 1 < end) {
            // 2 grand children + 1 children
            min12 = data[start].compareTo(data[start + 1]) < 0 ? start : start + 1;
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
        return data[min12].compareTo(data[min34]) < 0 ? min12 : min34;
    }

    private int indexOfLargestChildOrGrandchild(int index) {
        int end = end();
        int start = index * 4;
        int max12 = -1;
        int max34 = -1;
        if (start + 3 < end) {
            // 4 grand children
            max12 = data[start].compareTo(data[start + 1]) > 0 ? start : start + 1;
            max34 = data[start + 2].compareTo(data[start + 3]) > 0 ? start + 2 : start + 3;
        } else if (start + 2 < end) {
            // 3 grand children
            max12 = data[start].compareTo(data[start + 1]) > 0 ? start : start + 1;
            max34 = start + 2;
        } else if (start + 1 < end) {
            // 2 grand children + 1 children
            max12 = data[start].compareTo(data[start + 1]) > 0 ? start : start + 1;
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
        return data[max12].compareTo(data[max34]) > 0 ? max12 : max34;
    }

    private void pushDown(int m) {
        while (hasChildren(m)) {
            int i = m;
            if (isMinLevel(i)) {
                m = indexOfSmallestChildOrGrandchild(i);
                if (data[m].compareTo(data[i]) < 0) {
                    swap(m, i);
                    if (isGrandchildOf(m, i)) {
                        if (data[m].compareTo(data[parent(m)]) > 0) {
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
                if (data[m].compareTo(data[i]) > 0) {
                    swap(m, i);
                    if (isGrandchildOf(m, i)) {
                        if (data[m].compareTo(data[parent(m)]) < 0) {
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
                if (data[index].compareTo(data[parent(index)]) > 0) {
                    swap(index, parent(index));
                    pushUpMax(parent(index));
                } else {
                    pushUpMin(index);
                }
            } else {
                if (data[index].compareTo(data[parent(index)]) < 0) {
                    swap(index, parent(index));
                    pushUpMin(parent(index));
                } else {
                    pushUpMax(index);
                }
            }
        }
    }

    private void pushUpMin(int index) {
        while (hasGrandparent(index) && data[index].compareTo(data[grandparent(index)]) < 0) {
            swap(index, grandparent(index));
            index = grandparent(index);
        }
    }

    private void pushUpMax(int index) {
        while (hasGrandparent(index) && data[index].compareTo(data[grandparent(index)]) > 0) {
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
        int max = data[2].compareTo(data[3]) > 0 ? 2 : 3;

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
        return data[2].compareTo(data[3]) >= 0 ? data[2] : data[3];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    public void checkConsistency() {
        if (size < 0) {
            throw new IllegalStateException("size=" + size);
        }
        if (data[size] == null) {
            throw new IllegalStateException("data[size] == null: " + size);
        }
        if (size + 1 < data.length && data[size + 1] != null) {
            throw new IllegalStateException("data[size + 1] != null: " + size);
        }
        if (size >= 1) {
            checkMin(1);
        }
        if (size >= 2) {
            checkMax(2);
        }
        if (size >= 3) {
            checkMax(3);
        }
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

    private void checkMin(int index) {
        int child = index * 4;
        for (int i = 0; i < 4 && child + i < end(); i++) {
            if (data[index].compareTo(data[child + i]) > 0) {
                throw new IllegalStateException("min tree broken: " + index + " > " + (child + i));
            }
            checkMin(child + i);
        }
    }

    private void checkMax(int index) {
        int child = index * 4;
        for (int i = 0; i < 4 && child + i < end(); i++) {
            if (data[index].compareTo(data[child + i]) < 0) {
                throw new IllegalStateException("max tree broken: " + index + " < " + (child + i));
            }
            checkMax(child + i);
        }
    }
}
