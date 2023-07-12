package org.tinspin.index.util;

import java.util.Arrays;
import java.util.NoSuchElementException;

public class MinMaxHeapZ<T extends Comparable<T>> implements MinMaxHeapI<T> {

    // Data. The first slot is left empty, i.e. the first element is at [1]!
    private T[] data;
    // index of first free entry
    private int size = 0;

    public MinMaxHeapZ(int capacity) {
        data = (T[]) new Comparable[capacity];
    }

    public static <T extends Comparable<T>> MinMaxHeapZ<T> create() {
        return new MinMaxHeapZ<>(16);
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
        int end = end(); // TODO next line
        pushUp(end, value);
        size++;
    }

    // TODO remove
    public static void main(String[] args) {
        for (int i = 0; i < 35; i++) {
            System.out.println("i=" + i + " --> " + isMinLevel(i) + "  p=" + getGrandparent(i));
        }
    }

    private static boolean isMinLevel(int index) {
        //        // 0 -> 0; 1 -> 1; 2-3 -> 2; 4-7 -> 3; 8-15 -> 4; ...
        //        int highestBit = 32 - Integer.numberOfLeadingZeros(index);
        //        // min levels are 'odd'
        //        return (highestBit & 1) != 0;
        // We could even remove the "32 - x" because it doesn't change the trailing bit
        return (Integer.numberOfLeadingZeros(index) & 1) != 0;
    }

    private static int getGrandparent(int index) {
        return index >> 2;
    }

    private static int getParent(int index) {
        return index >> 1;
    }

    private void pushUp(int index, T value) {
        int parent = getParent(index);
        if (isMinLevel(index)) {
            // 4 = minimum index of min-child
            if (index >= 4 && value.compareTo(data[parent]) > 0) {
                data[index] = data[parent];
                index = parent;
                pushUpMax(index, value);
            } else {
                pushUpMin(index, value);
            }
            // Done !!!!
        } else {
            if (index >= 2 && value.compareTo(data[parent]) < 0) {
                data[index] = data[parent];
                index = parent; // move to next line TODO
                pushUpMin(index, value);
            } else {
                pushUpMax(index, value);
            }
        }
    }

    private void pushUpMin(int index, T value) {
        int parent = getGrandparent(index);
        if (parent == 0) {
            data[index] = value;
            return;
        }
        while (parent != 0) {
            if (value.compareTo(data[parent]) < 0) {
                data[index] = data[parent];
                index = parent;
                parent = getGrandparent(index);
            } else {
                break;
            }
        }
        data[index] = value;
    }

    private void pushUpMax(int index, T value) {
        int parent = getGrandparent(index);
        if (parent == 0) {
            data[index] = value;
            return;
        }
        while (parent != 0) {
            if (value.compareTo(data[parent]) > 0) {
                data[index] = data[parent];
                index = parent;
                parent = getGrandparent(index);
            } else {
                break;
            }
        }
        data[index] = value;
    }

    @Override
    public void popMin() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        int index = 1;
        int end = end();
        T value = data[end - 1];
        data[end - 1] = null;
        size--;

        if (size == 0) {
            return;
        }
        data[1] = value;
        if (size == 1) {
            return;
        }


//        end--; // TODO reduce duplication..?
        PUSH_DOWN(1);

//        while (true) {
//            // has grandchildren
//             if (index * 4 < end ) {
//                int start = index * 4;
//                int minChild = end > start + 1 && data[start].compareTo(data[start + 1]) >= 0 ? start + 1 : start;
//                if (end > start + 2) {
//                    int child23 = end > start + 3 && data[start + 2].compareTo(data[start + 3]) >= 0 ? start + 3 : start + 2;
//                    minChild = data[minChild].compareTo(data[child23]) < 0 ? minChild : child23;
//                }
//                if (value.compareTo(data[minChild]) <= 0) {
//                    data[index] = value;
//                    return;
//                }
//                data[index] = data[minChild];
//                index = minChild;
//            } else if (index * 2 < end) {
//                // handle children
//                int start = index * 2;
//                int minChild = end > start + 1 && data[start].compareTo(data[start + 1]) >= 0 ? start + 1 : start;
//                if (end > start + 2) {
//                    int child23 = end > start + 3 && data[start + 2].compareTo(data[start + 3]) >= 0 ? start + 3 : start + 2;
//                    minChild = data[minChild].compareTo(data[child23]) < 0 ? minChild : child23;
//                }
//
//                if (value.compareTo(data[minChild]) <= 0) {
//                    data[index] = value;
//                    return;
//                }
//                data[index] = data[minChild];
//                data[minChild] = value;
//                return;
//            } else {
//                data[index] = value;
//                return;
//            }
//        }
    }

    private boolean hasChildren(int i) {
        return i*2 <= size;
    }

    private boolean isGrandchildOf(int m, int i) {
        return i*4 == m;
    }

    private void swap(int i1, int i2) {
        T v = data[i1];
        data[i1] = data[i2];
        data[i2] = v;
    }

    private int parent(int i) {
        return i >> 1;
    }

    private int indexOfSmallestChildOrGrandchild(int index) {
        int end = end();
        int min = -1;
        if (index * 4 + 3 < end) {
            // 4 grand children
            int start = index * 4;
            // TODO remove ternary operator!
            int minG12 = end > start + 1 && data[start].compareTo(data[start + 1]) >= 0 ? start + 1 : start;
            int minG34 = end > start + 3 && data[start + 2].compareTo(data[start + 3]) >= 0 ? start + 3 : start + 2;
            min = data[minG12].compareTo(data[minG34]) < 0 ? minG12 : minG34;
        } else if (index * 4 + 2 < end) {
            // 3 grand children
            int start = index * 4;
            int minG12 = end > start + 1 && data[start].compareTo(data[start + 1]) >= 0 ? start + 1 : start;
            min = data[minG12].compareTo(data[start + 2]) < 0 ? minG12 : start + 2;
        } else if (index * 4 + 1 < end) {
            // 2 grand children + 1 children
            int start = index * 4;
            int minG12 = end > start + 1 && data[start].compareTo(data[start + 1]) >= 0 ? start + 1 : start;
            int startC = index * 2;
            min = data[minG12].compareTo(data[startC + 1]) < 0 ? minG12 : startC + 1;
        } else if (index * 4 < end) {
            // 1 grand child + 1 child
            int startG = index * 4;
            int startC = index * 2;
            min = data[startG].compareTo(data[startC + 1]) < 0 ? startG : startC + 1;
        } else if (index * 2 + 1 < end) {
            // 2 children
            int startC = index * 2;
            min = data[startC].compareTo(data[startC + 1]) < 0 ? startC : startC + 1;
        } else if (index * 2 < end) {
            // 1 child
            min = index * 2;
        } else {
            throw new IllegalStateException();
        }
        return min;
    }

    private int indexOfLargestChildOrGrandchild(int index) {
        int end = end();
        int min = -1;
        if (index * 4 + 3 < end) {
            // 4 grand children
            int start = index * 4;
            // TODO remove ternary operator!
            int minG12 = end > start + 1 && data[start].compareTo(data[start + 1]) <= 0 ? start + 1 : start;
            int minG34 = end > start + 3 && data[start + 2].compareTo(data[start + 3]) <= 0 ? start + 3 : start + 2;
            min = data[minG12].compareTo(data[minG34]) > 0 ? minG12 : minG34;
        } else if (index * 4 + 2 < end) {
            // 3 grand children
            int start = index * 4;
            int minG12 = end > start + 1 && data[start].compareTo(data[start + 1]) <= 0 ? start + 1 : start;
            min = data[minG12].compareTo(data[start + 2]) > 0 ? minG12 : start + 2;
        } else if (index * 4 + 1 < end) {
            // 2 grand children + 1 children
            int start = index * 4;
            int minG12 = end > start + 1 && data[start].compareTo(data[start + 1]) <= 0 ? start + 1 : start;
            int startC = index * 2;
            min = data[minG12].compareTo(data[startC + 1]) > 0 ? minG12 : startC + 1;
        } else if (index * 4 < end) {
            // 1 grand child + 1 child
            int startG = index * 4;
            int startC = index * 2;
            min = data[startG].compareTo(data[startC + 1]) > 0 ? startG : startC + 1;
        } else if (index * 2 + 1 < end) {
            // 2 children
            int startC = index * 2;
            min = data[startC].compareTo(data[startC + 1]) > 0 ? startC : startC + 1;
        } else if (index * 2 < end) {
            // 1 child
            min = index * 2;
        } else {
            throw new IllegalStateException();
        }
        return min;
    }

    private void PUSH_DOWN(int i) {
        if (isMinLevel(i)) {
            PUSH_DOWN_MIN(i);
        } else {
            PUSH_DOWN_MAX(i);
        }
    }
     private void PUSH_DOWN_MIN(int i) {
         if (hasChildren(i)) {
             int m = indexOfSmallestChildOrGrandchild(i);
             if (isGrandchildOf(m, i)){
                 if (data[m].compareTo(data[i]) < 0) {
                     swap(m, i);
                     if (data[m].compareTo(data[parent(m)]) > 0) {
                         swap(m, parent(m));
                     }
                     PUSH_DOWN(m);
                 }

             } else if (data[m].compareTo(data[i]) < 0) {
                 swap(m, i);
             }
         }
     }
     private void PUSH_DOWN_MAX(int i) {
         if (hasChildren(i)) {
             int m = indexOfLargestChildOrGrandchild(i);
             if (isGrandchildOf(m, i)){
                 if (data[m].compareTo(data[i]) > 0) {
                     swap(m, i);
                     if (data[m].compareTo(data[parent(m)]) < 0) {
                         swap(m, parent(m));
                     }
                     PUSH_DOWN(m);
                 }
             } else if (data[m].compareTo(data[i]) > 0) {
                 swap(m, i);
             }
         }
     }

    private int end() {
        return size + 1;
    }

    @Override
    public void popMax() {

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

    public void print() {
        int x = 2;
        for (int i = 1; i <= size; i++) {
            if (i % x == 0) {
                System.out.println();
                x *= 2;
            }
            System.out.print(data[i] + "   ");
        }
        System.out.println();
    }

    private void checkMin(int index) {
        int child = index * 4;
        for (int i = 0; i < 4 && child+i < end(); i++) {
            if (data[index].compareTo(data[child + i]) > 0) {
                throw new IllegalStateException("min tree broken: " + index + " > " + (child + i));
            }
            checkMin(child + i);
        }
    }

    private void checkMax(int index) {
        int child = index * 4;
        for (int i = 0; i < 4 && child+i < end(); i++) {
            if (data[index].compareTo(data[child + i]) < 0) {
                throw new IllegalStateException("max tree broken: " + index + " < " + (child + i));
            }
            checkMax(child + i);
        }
    }

}
