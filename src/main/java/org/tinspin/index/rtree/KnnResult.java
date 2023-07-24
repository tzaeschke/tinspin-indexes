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
package org.tinspin.index.rtree;

//import java.util.Iterator;
//import java.util.NoSuchElementException;
//
//import static org.tinspin.index.Index.*;
//
//public class KnnResult<T> implements Iterable<BoxEntryKnn<T>> {
//
//	private int capacity;
//	private BoxEntryKnn<T>[] entries;
//	private int size;
//	private double maxDist;
//
//	@SuppressWarnings("unchecked")
//	public KnnResult(int capacity) {
//		this.capacity = capacity;
//		this.entries = new BoxEntryKnn[capacity];
//		this.maxDist = Double.MAX_VALUE;
//	}
//
//	public double add(Entry<T> e, double distance) {
//		BoxEntryKnn<T> de;
//		if (size < capacity) {
//			de = new BoxEntryKnn<>(e.min(), e.max(), e.value(), distance);
//			entries[size++] = de;
//		} else {
//			de = entries[size-1];
//			if (distance >= de.dist()) {
//				//nothing to add
//				return maxDist;
//			}
//			de.set(e, distance);
//		}
//
//		//TODO use binary search for capacity>10
//
//		//move 'de' to correct position
//		int pos = size-1;
//		while (pos >= 1 && entries[pos-1].dist() > distance) {
//			entries[pos] = entries[pos-1];
//			pos--;
//		}
//		entries[pos] = de;
//		if (size == capacity) {
//			maxDist = entries[size-1].dist();
//		}
//		return maxDist;
//	}
//
//	@Override
//	public Iterator<BoxEntryKnn<T>> iterator() {
//		return new KnnResultIterator();
//	}
//
//	private class KnnResultIterator implements Iterator<DistEntry<T>> {
//
//		int pos = 0;
//
//		@Override
//		public boolean hasNext() {
//			return pos < size;
//		}
//
//		@Override
//		public DistEntry<T> next() {
//			if (!hasNext()) {
//				throw new NoSuchElementException();
//			}
//			return entries[pos++];
//		}
//	}
//
//	@SuppressWarnings("unchecked")
//	public void clear(int k) {
//		if (k > entries.length) {
//			entries = new BoxEntryKnn[k];
//		} else {
//			for (int i = 0; i < size; i++) {
//				entries[i] = null;
//			}
//		}
//		capacity = k;
//		size = 0;
//		maxDist = Double.MAX_VALUE;
//	}
//}
