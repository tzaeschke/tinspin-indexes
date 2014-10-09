/*
 * Copyright 2009-2014 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of ZooDB.
 * 
 * ZooDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ZooDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ZooDB.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * See the README and COPYING files for further information. 
 */
package org.zoodb.index.critbit;

/**
 * CritBit64 is a 1D crit-bit tree with 64bit key length.
 * 
 * In order to store floating point values, please convert them to 'long' with
 * BitTools.toSortableLong(...), also when supplying query parameters.
 * Extracted values can be converted back with BitTools.toDouble() or toFloat().
 * 
 * Version 1.3
 * - Removed separate field for prefix
 * - Shared reference for sub-node and value
 * 
 * Version 1.2.2  
 *  - simplified updateParentAfterRemove()
 *  - removed Node.posFirstBit
 *  - Moved test to tst folder
 * 
 * Version 1.2.1  
 *  - Replaced compare() with '==' where possible
 *  - Simplified compare(), doesInfixMatch(), get(), contains()
 *  - Replaced setBit() with set0()/set1()
 * 
 * Version 1.1:
 *  - added queryWithMask()
 * 
 * Version 1.0
 *  - Initial release
 * 
 * @author Tilmann Zaeschke
 */
import java.util.Iterator;
import java.util.NoSuchElementException;

public class CritBit64<V> implements Iterable<V> {

	private final int DEPTH = 64;
	
	private Object root;
	//the following contains either the actual key or the prefix for the sub-node
	private long rootKey;

	private int size;
	
	private static class Node<V> {
		//TODO replace posDiff with posMask 
		//     --> Possibly easier to calculate (non-log?)
		//     --> Similarly powerful....
		
		//Contains either the sub-node or the value of the entry
		Object lo;
		Object hi;
		//the following contain either the actual key or the prefix for sub-nodes
		long loPost;
		long hiPost;
		byte posDiff;
		
		Node(long loPost, Object lo, long hiPost, Object hi, int posDiff) {
			this.loPost = loPost;
			this.lo = lo;
			this.hiPost = hiPost;
			this.hi = hi;
			this.posDiff = (byte) posDiff;
		}
		
		@SuppressWarnings("unchecked")
		V loVal() {
			return (V) lo;
		}
		
		@SuppressWarnings("unchecked")
		V hiVal() {
			return (V) hi;
		}
		
		@SuppressWarnings("unchecked")
		Node<V> loN() {
			return (Node<V>) lo;
		}
		
		@SuppressWarnings("unchecked")
		Node<V> hiN() {
			return (Node<V>) hi;
		}
	}
	
	private CritBit64() {
		//private 
	}
	
	/**
	 * Create a 1D crit-bit tree with 64 bit key length. 
	 * @return a 1D crit-bit tree
	 */
	public static <V> CritBit64<V> create() {
		return new CritBit64<V>();
	}
	
	@SuppressWarnings("unchecked")
	private final V rootVal() {
		return (V) root;
	}
	
	@SuppressWarnings("unchecked")
	private final Node<V> rootNode() {
		return (Node<V>) root;
	}
	
	/**
	 * Add a key value pair to the tree or replace the value if the key already exists.
	 * @param key
	 * @param val
	 * @return The previous value or {@code null} if there was no previous value
	 */
	public V put(long key, V val) {
		if (size == 0) {
			rootKey = key;
			root = val;
			size++;
			return null;
		}
		if (size == 1) {
			Node<V> n2 = createNode(key, val, rootKey, root);
			if (n2 == null) {
				V prev = rootVal();
				root = val;
				return prev; 
			}
			root = n2;
			rootKey = extractPrefix(key, n2.posDiff-1);
			size++;
			return null;
		}
		Node<V> n = rootNode();
		int parentPosDiff = -1;
		long prefix = rootKey;
		Node<V> parent = null;
		boolean isCurrentChildLo = false;
		while (true) {
			if (parentPosDiff+1 != n.posDiff) {
				//split in prefix?
				int posDiff = compare(key, prefix);
				if (posDiff < n.posDiff && posDiff != -1) {
					Node<V> newSub;
					long subPrefix = extractPrefix(prefix, posDiff-1);
					if (BitTools.getBit(key, posDiff)) {
						newSub = new Node<V>(prefix, n, key, val, posDiff);
					} else {
						newSub = new Node<V>(key, val, prefix, n, posDiff);
					}
					if (parent == null) {
						rootKey = subPrefix;
						root = newSub;
					} else if (isCurrentChildLo) {
						parent.loPost = subPrefix;
						parent.lo = newSub;
					} else {
						parent.hiPost = subPrefix;
						parent.hi = newSub;
					}
					size++;
					return null;
				}
			}			
			
			//prefix matches, so now we check sub-nodes and postfixes
			if (BitTools.getBit(key, n.posDiff)) {
				if (n.hi instanceof Node) {
					prefix = n.hiPost;
					parent = n;
					n = n.hiN();
					isCurrentChildLo = false;
				} else {
					Node<V> n2 = createNode(key, val, n.hiPost, n.hi);
					if (n2 == null) {
						V prev = n.hiVal();
						n.hi = val;
						return prev; 
					}
					n.hi = n2;
					n.hiPost = extractPrefix(key, n2.posDiff-1);
					size++;
					return null;
				}
			} else {
				if (n.lo instanceof Node) {
					prefix = n.loPost;
					parent = n;
					n = n.loN();
					isCurrentChildLo = true;
				} else {
					Node<V> n2 = createNode(key, val, n.loPost, n.lo);
					if (n2 == null) {
						V prev = n.loVal();
						n.lo = val;
						return prev; 
					}
					n.lo = n2;
					n.loPost = extractPrefix(key, n2.posDiff-1);
					size++;
					return null;
				}
			}
			parentPosDiff = n.posDiff;
		}
	}
	
	public void printTree() {
		System.out.println("Tree: \n" + toString());
	}
	
	@Override
	public String toString() {
		if (size == 0) {
			return "- -";
		}
		if (!(root instanceof Node)) {
			return "-" + BitTools.toBinary(rootKey, 64) + " v=" + rootVal();
		}
		Node<V> n = rootNode();
		StringBuilder s = new StringBuilder();
		printNode(n, s, "", 0, rootKey);
		return s.toString();
	}
	
	private void printNode(Node<V> n, StringBuilder s, String level, int currentDepth, long infix) {
		char NL = '\n'; 
		if (currentDepth != n.posDiff) {
			s.append(level + "n: " + currentDepth + "/" + n.posDiff + " " + 
					BitTools.toBinary(infix, 64) + NL);
		} else {
			s.append(level + "n: " + currentDepth + "/" + n.posDiff + " i=0" + NL);
		}
		if (n.lo instanceof Node) {
			printNode(n.loN(), s, level + "-", n.posDiff+1, n.loPost);
		} else {
			s.append(level + " " + BitTools.toBinary(n.loPost, 64) + " v=" + n.loVal() + NL);
		}
		if (n.hi instanceof Node) {
			printNode(n.hiN(), s, level + "-", n.posDiff+1, n.hiPost);
		} else {
			s.append(level + " " + BitTools.toBinary(n.hiPost,64) + " v=" + n.hiVal() + NL);
		}
	}
	
	public boolean checkTree() {
		if (root == null || !(root instanceof Node)) {
			if (size > 1) {
				System.err.println("root node = null AND size = " + size);
				return false;
			}
			return true;
		}
		return checkNode(rootNode(), 0, rootKey);
	}
	
	private boolean checkNode(Node<V> n, int firstBitOfNode, long prefix) {
		//check prefix
		if (n.posDiff < firstBitOfNode) {
			System.err.println("prefix with len=0 detected!");
			return false;
		}
		if (n.lo instanceof Node) {
			if (!doesPrefixMatch(n.posDiff-1, n.loPost, prefix)) {
				System.err.println("lo: prefix mismatch");
				return false;
			}
			checkNode(n.loN(), n.posDiff+1, n.loPost);
		}
		if (n.hi instanceof Node) {
			if (!doesPrefixMatch(n.posDiff-1, n.hiPost, prefix)) {
				System.err.println("hi: prefix mismatch");
				return false;
			}
			checkNode(n.hiN(), n.posDiff+1, n.hiPost);
		}
		return true;
	}
	
	private Node<V> createNode(long k1, Object val1, long k2, Object val2) {
		int posDiff = compare(k1, k2);
		if (posDiff == -1) {
			return null;
		}
		if (BitTools.getBit(k2, posDiff)) {
			return new Node<V>(k1, val1, k2, val2, posDiff);
		} else {
			return new Node<V>(k2, val2, k1, val1, posDiff);
		}
	}
	
	/**
	 * 
	 * @param v
	 * @param endPos last bit of prefix, counting starts with 0 for 1st bit 
	 * @return The prefix.
	 */
	private static long extractPrefix(long v, int endPos) {
		long inf = v;
		//avoid shifting by 64 bit which means 0 shifting in Java!
		if (endPos < 63) {
			inf &= ~((-1L) >>> (1+endPos)); // & 0x3f == %64
		}
		return inf;
	}

	/**
	 * 
	 * @param v
	 * @param startPos
	 * @return True if the prefix matches the value or if no prefix is defined
	 */
	private boolean doesPrefixMatch(int posDiff, long v, long prefix) {
		if (posDiff > 0) {
			return (v ^ prefix) >>> (64-posDiff) == 0;
		}
		return true;
	}
	
	/**
	 * Compares two values.
	 * @param v1
	 * @param v2
	 * @return Position of the differing bit, or -1 if both values are equal
	 */
	private static int compare(long v1, long v2) {
		return (v1 == v2) ? -1 : Long.numberOfLeadingZeros(v1 ^ v2);
	}

	/**
	 * Get the size of the tree.
	 * @return the number of keys in the tree
	 */
	public int size() {
		return size;
	}

	/**
	 * Check whether a given key exists in the tree.
	 * @param key
	 * @return {@code true} if the key exists otherwise {@code false}
	 */
	public boolean contains(long key) {
		if (size == 0) {
			return false;
		} 
		if (size == 1) {
			return key == rootKey;
		}
		Node<V> n = rootNode();
		long prefix = rootKey;
		while (doesPrefixMatch(n.posDiff, key, prefix)) {
			//prefix matches, so now we check sub-nodes and postfixes
			if (BitTools.getBit(key, n.posDiff)) {
				if (n.hi instanceof Node) {
					prefix = n.hiPost;
					n = n.hiN();
					continue;
				} 
				return key == n.hiPost;
			} else {
				if (n.lo instanceof Node) {
					prefix = n.loPost;
					n = n.loN();
					continue;
				}
				return key == n.loPost;
			}
		}
		return false;
	}
	
	/**
	 * Get the value for a given key. 
	 * @param key
	 * @return the values associated with {@code key} or {@code null} if the key does not exist.
	 */
	public V get(long key) {
		if (size == 0) {
			return null;
		}
		if (size == 1) {
			return (key == rootKey) ? rootVal() : null;
		}
		Node<V> n = rootNode();
		long prefix = rootKey;
		while (doesPrefixMatch(n.posDiff, key, prefix)) {
			//prefix matches, so now we check sub-nodes and postfixes
			if (BitTools.getBit(key, n.posDiff)) {
				if (n.hi instanceof Node) {
					prefix = n.hiPost;
					n = n.hiN();
					continue;
				} 
				return (key == n.hiPost) ? n.hiVal() : null;
			} else {
				if (n.lo instanceof Node) {
					prefix = n.loPost;
					n = n.loN();
					continue;
				}
				return (key == n.loPost) ? n.loVal() : null;
			}
		}
		return null;
	}
	
	/**
	 * Remove a key and its value
	 * @param key
	 * @return The value of the key of {@code null} if the value was not found. 
	 */
	public V remove(long key) {
		if (size == 0) {
			return null;
		}
		if (size == 1) {
			if (key == rootKey) {
				size--;
				rootKey = 0;
				V prev = rootVal();
				root = null;
				return prev;
			}
			return null;
		}
		Node<V> n = rootNode();
		Node<V> parent = null;
		boolean isParentHigh = false;
		long prefix = rootKey;
		while (doesPrefixMatch(n.posDiff, key, prefix)) {
			//prefix matches, so now we check sub-nodes and postfixes
			if (BitTools.getBit(key, n.posDiff)) {
				if (n.hi instanceof Node) {
					isParentHigh = true;
					prefix = n.hiPost; 
					parent = n;
					n = n.hiN();
					continue;
				} else {
					if (key != n.hiPost) {
						return null;
					}
					//match! --> delete node
					//replace data in parent node
					updateParentAfterRemove(parent, n.loPost, n.lo, isParentHigh);
					return n.hiVal();
				}
			} else {
				if (n.lo instanceof Node) {
					isParentHigh = false;
					prefix = n.loPost; 
					parent = n;
					n = n.loN();
					continue;
				} else {
					if (key != n.loPost) {
						return null;
					}
					//match! --> delete node
					//replace data in parent node
					//for new prefixes...
					updateParentAfterRemove(parent, n.hiPost, n.hi, isParentHigh);
					return n.loVal();
				}
			}
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private void updateParentAfterRemove(Node<V> parent, long newPost, Object newSub,
			boolean isParentHigh) {
		
		if (newSub instanceof Node) {
			newPost = extractPrefix(newPost, ((Node<V>)newSub).posDiff-1);
		}

		if (parent == null) {
			rootKey = newPost;
			root = newSub;
		} else if (isParentHigh) {
			parent.hiPost = newPost;
			parent.hi = newSub;
		} else {
			parent.loPost = newPost;
			parent.lo = newSub;
		}
		size--;
	}

	public CBIterator<V> iterator() {
		return new CBIterator<V>(this, DEPTH);
	}
	
	public static class CBIterator<V> implements Iterator<V> {
		private long nextKey = 0; 
		private V nextValue = null;
		private final Node<V>[] stack;
		 //0==read_lower; 1==read_upper; 2==go_to_parent
		private static final byte READ_LOWER = 0;
		private static final byte READ_UPPER = 1;
		private static final byte RETURN_TO_PARENT = 2;
		private final byte[] readHigherNext;
		private int stackTop = -1;

		@SuppressWarnings("unchecked")
		public CBIterator(CritBit64<V> cb, int DEPTH) {
			this.stack = new Node[DEPTH];
			this.readHigherNext = new byte[DEPTH];  // default = false

			if (cb.size == 0) {
				//Tree is empty
				return;
			}
			if (cb.size == 1) {
				nextValue = cb.rootVal();
				nextKey = cb.rootKey;
				return;
			}
			stack[++stackTop] = cb.rootNode();
			findNext();
		}

		private void findNext() {
			while (stackTop >= 0) {
				Node<V> n = stack[stackTop];
				//check lower
				if (readHigherNext[stackTop] == READ_LOWER) {
					readHigherNext[stackTop] = READ_UPPER;
					if (n.lo instanceof Node) {
						stack[++stackTop] = n.loN();
						readHigherNext[stackTop] = READ_LOWER;
						continue;
					} else {
						nextValue = n.loVal();
						nextKey = n.loPost;
						return;
					}
				}
				//check upper
				if (readHigherNext[stackTop] == READ_UPPER) {
					readHigherNext[stackTop] = RETURN_TO_PARENT;
					if (n.hi instanceof Node) {
						stack[++stackTop] = n.hiN();
						readHigherNext[stackTop] = READ_LOWER;
						continue;
					} else {
						nextValue = n.hiVal();
						nextKey = n.hiPost;
						--stackTop;
						return;
						//proceed to move up a level
					}
				}
				//proceed to move up a level
				--stackTop;
			}
			//Finished
			nextValue = null;
			nextKey = 0;
		}

		@Override
		public boolean hasNext() {
			return nextValue != null;
		}

		@Override
		public V next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			V ret = nextValue;
			findNext();
			return ret;
		}

		public long nextKey() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			long ret = nextKey;
			findNext();
			return ret;
		}

		public Entry<V> nextEntry() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			Entry<V> ret = new Entry<V>(nextKey, nextValue);
			findNext();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}
	
	/**
	 * Queries the tree for entries with min<=key<=max. 
	 * @param min
	 * @param max
	 * @return An iterator over the matching entries.
	 */
	public QueryIterator<V> query(long min, long max) {
		return new QueryIterator<V>(this, min, max, DEPTH);
	}
	
	public static class QueryIterator<V> implements Iterator<V> {
		private final long minOrig;
		private final long maxOrig;
		private long nextKey = 0; 
		private V nextValue = null;
		private final Node<V>[] stack;
		 //0==read_lower; 1==read_upper; 2==go_to_parent
		private static final byte READ_LOWER = 0;
		private static final byte READ_UPPER = 1;
		private static final byte RETURN_TO_PARENT = 2;
		private final byte[] readHigherNext;
		private final long[] prefixes;
		private int stackTop = -1;

		@SuppressWarnings("unchecked")
		public QueryIterator(CritBit64<V> cb, long minOrig, long maxOrig, int DEPTH) {
			this.stack = new Node[DEPTH];
			this.readHigherNext = new byte[DEPTH];  // default = false
			this.prefixes = new long[DEPTH];
			this.minOrig = minOrig;
			this.maxOrig = maxOrig;

			if (cb.size == 0) {
				//Tree is empty
				return;
			}
			if (cb.size == 1) {
				checkMatchFullIntoNextVal(cb.rootKey, cb.rootVal());
				return;
			}
			Node<V> n = cb.rootNode();
			if (!checkMatch(cb.rootKey, n.posDiff)) {
				return;
			}
			stack[++stackTop] = n;
			prefixes[stackTop] = cb.rootKey;
			findNext();
		}

		private void findNext() {
			while (stackTop >= 0) {
				Node<V> n = stack[stackTop];
				//check lower
				if (readHigherNext[stackTop] == READ_LOWER) {
					readHigherNext[stackTop] = READ_UPPER;
					long valTemp = BitTools.set0(prefixes[stackTop], n.posDiff);
					if (checkMatch(valTemp, n.posDiff)) {
						if (n.lo instanceof Node) {
							stack[++stackTop] = n.loN();
							prefixes[stackTop] = n.loPost;
							readHigherNext[stackTop] = READ_LOWER;
							continue;
						} else {
							if (checkMatchFullIntoNextVal(n.loPost, n.lo)) {
								return;
							} 
							//proceed to check upper
						}
					}
				}
				//check upper
				if (readHigherNext[stackTop] == READ_UPPER) {
					readHigherNext[stackTop] = RETURN_TO_PARENT;
					long valTemp = BitTools.set1(prefixes[stackTop], n.posDiff);
					if (checkMatch(valTemp, n.posDiff)) {
						if (n.hi instanceof Node) {
							stack[++stackTop] = n.hiN();
							prefixes[stackTop] = n.hiPost;
							readHigherNext[stackTop] = READ_LOWER;
							continue;
						} else {
							if (checkMatchFullIntoNextVal(n.hiPost, n.hi)) {
								--stackTop;
								return;
							} 
							//proceed to move up a level
						}
					}
				}
				//proceed to move up a level
				--stackTop;
			}
			//Finished
			nextValue = null;
			nextKey = 0;
		}


		/**
		 * Full comparison on the parameter. Assigns the parameter to 'nextVal' if comparison
		 * fits.
		 * @param keyTemplate
		 * @return Whether we have a match or not
		 */
		@SuppressWarnings("unchecked")
		private boolean checkMatchFullIntoNextVal(long keyTemplate, Object value) {
			if ((minOrig > keyTemplate) || (keyTemplate > maxOrig)) { 
				return false;
			}
			nextValue = (V) value;
			nextKey = keyTemplate;
			return true;
		}
		
		private boolean checkMatch(long keyTemplate, int currentDepth) {
			int toIgnore = 64 - currentDepth;
			long mask = (-1L) << toIgnore;
			if ((minOrig & mask) > (keyTemplate & mask)) {  
				return false;
			}
			if ((keyTemplate & mask) > (maxOrig & mask)) {  
				return false;
			}
			
			return true;
		}

		@Override
		public boolean hasNext() {
			return nextValue != null;
		}

		@Override
		public V next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			V ret = nextValue;
			findNext();
			return ret;
		}

		public long nextKey() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			long ret = nextKey;
			findNext();
			return ret;
		}

		public Entry<V> nextEntry() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			Entry<V> ret = new Entry<V>(nextKey, nextValue);
			findNext();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}
	
	/**
	 * Queries the tree for entries with min<=key<=max. Unlike the normal query, this
	 * query also excludes all elements with (key|min)!=key and (key&max!=max).
	 * See PH-Tree for a discussion.
	 * @param min
	 * @param max
	 * @return An iterator over the matching entries.
	 */
	public QueryIteratorMask<V> queryWithMask(long min, long max) {
		return new QueryIteratorMask<V>(this, min, max, DEPTH);
	}
	
	public static class QueryIteratorMask<V> implements Iterator<V> {
		private final long minOrig;
		private final long maxOrig;
		private long nextKey = 0; 
		private V nextValue = null;
		private final Node<V>[] stack;
		 //0==read_lower; 1==read_upper; 2==go_to_parent
		private static final byte READ_LOWER = 0;
		private static final byte READ_UPPER = 1;
		private static final byte RETURN_TO_PARENT = 2;
		private final byte[] readHigherNext;
		private final long[] prefixes;
		private int stackTop = -1;

		@SuppressWarnings("unchecked")
		public QueryIteratorMask(CritBit64<V> cb, long minOrig, long maxOrig, int DEPTH) {
			this.stack = new Node[DEPTH];
			this.readHigherNext = new byte[DEPTH];  // default = false
			this.prefixes = new long[DEPTH];
			this.minOrig = minOrig;
			this.maxOrig = maxOrig;

			if (cb.size == 0) {
				//Tree is empty
				return;
			}
			if (cb.size == 1) {
				checkMatchFullIntoNextVal(cb.rootKey, cb.rootVal());
				return;
			}
			Node<V> n = cb.rootNode();
			if (!checkMatch(cb.rootKey, n.posDiff)) {
				return;
			}
			stack[++stackTop] = n;
			prefixes[stackTop] = cb.rootKey;
			findNext();
		}

		private void findNext() {
			while (stackTop >= 0) {
				Node<V> n = stack[stackTop];
				//check lower
				if (readHigherNext[stackTop] == READ_LOWER) {
					readHigherNext[stackTop] = READ_UPPER;
					long valTemp = BitTools.set0(prefixes[stackTop], n.posDiff);
					if (checkMatch(valTemp, n.posDiff)) {
						if (n.lo instanceof Node) {
							stack[++stackTop] = n.loN();
							prefixes[stackTop] = n.loPost;
							readHigherNext[stackTop] = READ_LOWER;
							continue;
						} else {
							if (checkMatchFullIntoNextVal(n.loPost, n.lo)) {
								return;
							} 
							//proceed to check upper
						}
					}
				}
				//check upper
				if (readHigherNext[stackTop] == READ_UPPER) {
					readHigherNext[stackTop] = RETURN_TO_PARENT;
					long valTemp = BitTools.set1(prefixes[stackTop], n.posDiff);
					if (checkMatch(valTemp, n.posDiff)) {
						if (n.hi instanceof Node) {
							stack[++stackTop] = n.hiN();
							prefixes[stackTop] = n.hiPost;
							readHigherNext[stackTop] = READ_LOWER;
							continue;
						} else {
							if (checkMatchFullIntoNextVal(n.hiPost, n.hi)) {
								--stackTop;
								return;
							} 
							//proceed to move up a level
						}
					}
				}
				//proceed to move up a level
				--stackTop;
			}
			//Finished
			nextValue = null;
			nextKey = 0;
		}


		/**
		 * Full comparison on the parameter. Assigns the parameter to 'nextVal' if comparison
		 * fits.
		 * @param keyTemplate
		 * @return Whether we have a match or not
		 */
		@SuppressWarnings("unchecked")
		private boolean checkMatchFullIntoNextVal(long keyTemplate, Object value) {
			if ((keyTemplate | minOrig) != keyTemplate) {
				return false;
			}
			if ((keyTemplate & maxOrig) != keyTemplate) {
				return false;
			}
			nextValue = (V) value;
			nextKey = keyTemplate;
			return true;
		}
		
		private boolean checkMatch(long keyTemplate, int currentDepth) {
			int toIgnore = 64 - currentDepth;
			long mask = (-1L) << toIgnore;
			long myKey = keyTemplate & mask;
			if ((myKey | (minOrig&mask)) != myKey) {
				return false;
			}
			if ((myKey & (maxOrig&mask)) != myKey) {
				return false;
			}
			
			return true;
		}

		@Override
		public boolean hasNext() {
			return nextValue != null;
		}

		@Override
		public V next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			V ret = nextValue;
			findNext();
			return ret;
		}

		public long nextKey() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			long ret = nextKey;
			findNext();
			return ret;
		}

		public Entry<V> nextEntry() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			Entry<V> ret = new Entry<V>(nextKey, nextValue);
			findNext();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}
	
	public static class Entry<V> {
		private final long key;
		private final V value;
		Entry(long key, V value) {
			this.key = key;
			this.value = value;		
		}
		public long key() {
			return key;
		}
		public V value() {
			return value;
		}
	}
	
}
