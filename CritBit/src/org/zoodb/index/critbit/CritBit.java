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
 * CritBit is a multi-dimensional OR arbitrary length crit-bit tree.
 * 
 * Cribit trees are very space efficient due to prefix-sharing and suitable for
 * multi-dimensional data with low dimensionality (e.g. less than 10 dimensions or so).
 * They are also stable, that means unlike kD-trees or quadtrees they do not require
 * rebalancing, this makes update performance much more predictable.
 * 
 * There is 1 1D-version and a kD-version (kD: k-dimensional).
 * The 1D version supports keys with arbitrary length (e.g. 256bit), the kD-version
 * supports k-dimensional keys with a maximum length of 64 bit per dimension. 
 * 
 * Both tree versions use internally the same methods, except for the range queries.
 * For range queries, the 1D version interprets the parameters as one minimum and one
 * maximum value. For kD queries, the parameters are interpreted as arrays of
 * minimum and maximum values (i.e. the low left and upper right 
 * corner of a query (hyper-)rectangle). 
 * 
 * All method ending with 'KD' are for k-dimensional use of the tree, all other methods are for
 * 1-dimensional use. Exceptions are the size(), printTree() and similar methods, which work  for
 * all dimensions. 
 * 
 * In order to store floating point values, please convert them to 'long' with
 * BitTools.toSortableLong(...), also when supplying query parameters.
 * Extracted values can be converted back with BitTools.toDouble() or toFloat().
 * This conversion is taken from: 
 * T.Zaeschke, C.Zimmerli, M.C.Norrie:  The PH-Tree - A Space-Efficient Storage Structure and 
 * Multi-Dimensional Index (SIGMOD 2014)
 * 
 * 
 * Version 1.2.1  
 *  - Replaced compare() with isEqual() where possible
 *  - Simplified compare(), doesInfixMatch()
 *  - Removed unused arguments
 * 
 * Version 1.2  
 *  - Added iterator() to iterate over all entries
 * 
 * Version 1.1  
 *  - Slight performance improvements in mergeLong() and  readAndSplit()
 * 
 * Version 1.0  
 *  - Initial release
 * 
 * @author Tilmann Zaeschke
 */
import java.util.Iterator;
import java.util.NoSuchElementException;

public class CritBit<V> implements CritBit1D<V>, CritBitKD<V> {

	private final int DEPTH;
	private final int DIM;
	
	private Node<V> root;
	private long[] rootKey;
	private V rootVal;

	private int size;
	
	private static final int SINGLE_DIM = -1;
	
	private static class Node<V> {
		//TODO store both post in one array
		//TODO reduce space usage by using same reference for lo and loPost/loVal
 		//TODO --> use only one ref for hi/low each
		//TODO ? put other fields into long[]????
		V loVal;
		V hiVal;
		Node<V> lo;
		Node<V> hi;
		long[] loPost;
		long[] hiPost;
		long[] infix;
		int posFirstBit;  
		int posDiff;
		
		Node(int posFirstBit, long[] loPost, V loVal, long[] hiPost, V hiVal, 
				long[] infix, int posDiff) {
			this.loPost = loPost;
			this.loVal = loVal;
			this.hiPost = hiPost;
			this.hiVal = hiVal;
			this.infix = infix;
			this.posFirstBit = posFirstBit;
			this.posDiff = posDiff;
		}
	}
	
	private CritBit(int depth, int dim) {
		this.DEPTH = depth;
		//we deliberately allow dim=1 here 
		this.DIM = dim;
	}
	
	/**
	 * Create a 1D crit-bit tree with arbitrary key length. 
	 * @param width The number of bits per value
	 * @return a 1D crit-bit tree
	 */
	public static <V> CritBit1D<V> create1D(int width) {
		if (width < 1) {
			throw new IllegalArgumentException("Illegal bit width: " + width);
		}
		// SINGLE_DIM ensures that DIM is never used in this case.
		return new CritBit<V>(width, SINGLE_DIM);
	}
	
	/**
	 * Create a kD crit-bit tree with maximum 64bit key length. 
	 * 
	 * @param width The number of bits per value
	 * @param dim The number of dimensions
	 * @return k-dimensional tree
	 */
	public static <V> CritBitKD<V> createKD(int width, int dim) {
		if (width < 1 || width > 64) {
			throw new IllegalArgumentException("Illegal bit width: " + width);
		}
		if (dim < 1) {
			throw new IllegalArgumentException("Illegal dimension count: " + dim);
		}
		return new CritBit<V>(width, dim);
	}
	
	/**
	 * Add a key value pair to the tree or replace the value if the key already exists.
	 * @param key
	 * @param val
	 * @return The previous value or {@code null} if there was no previous value
	 */
	public V put(long[] key, V val) {
		checkDim0();
		return putNoCheck(key, val);
	}
	
	private V putNoCheck(long[] key, V val) {
		if (root == null) {
			if (rootKey == null) {
				rootKey = new long[key.length];
				System.arraycopy(key, 0, rootKey, 0, key.length);
				rootVal = val;
			} else {
				Node<V> n2 = createNode(key, val, rootKey, rootVal, 0);
				if (n2 == null) {
					V prev = rootVal;
					rootVal = val;
					return prev; 
				}
				root = n2;
				rootKey = null;
				rootVal = null;
			}
			size++;
			return null;
		}
		Node<V> n = root;
		long[] currentPrefix = new long[key.length];
		while (true) {
			readInfix(n, currentPrefix);
			
			if (n.infix != null) {
				//split infix?
				int posDiff = compare(key, currentPrefix);
				if (posDiff < n.posDiff && posDiff != -1) {
					long[] subInfix = extractInfix(currentPrefix, posDiff+1, n.posDiff-1);
					//new sub-node
					Node<V> newSub = new Node<V>(posDiff+1, n.loPost, n.loVal, n.hiPost, n.hiVal, 
							subInfix, n.posDiff);
					newSub.hi = n.hi;
					newSub.lo = n.lo;
					if (BitTools.getAndCopyBit(key, posDiff, currentPrefix)) {
						n.hi = null;
						n.hiPost = createPostFix(key, posDiff);
						n.hiVal = val;
						n.lo = newSub;
						n.loPost = null;
						n.loVal = null;
					} else {
						n.hi = newSub;
						n.hiPost = null;
						n.hiVal = null;
						n.lo = null;
						n.loPost = createPostFix(key, posDiff);
						n.loVal = val;
					}
					n.infix = extractInfix(currentPrefix, n.posFirstBit, posDiff-1);
					n.posDiff = posDiff;
					size++;
					return null;
				}
			}			
			
			//infix matches, so now we check sub-nodes and postfixes
			if (BitTools.getAndCopyBit(key, n.posDiff, currentPrefix)) {
				if (n.hi != null) {
					n = n.hi;
					continue;
				} else {
					readPostFix(n.hiPost, currentPrefix);
					Node<V> n2 = createNode(key, val, currentPrefix, n.hiVal, n.posDiff + 1);
					if (n2 == null) {
						V prev = n.hiVal;
						n.hiVal = val;
						return prev; 
					}
					n.hi = n2;
					n.hiPost = null;
					n.hiVal = null;
					size++;
					return null;
				}
			} else {
				if (n.lo != null) {
					n = n.lo;
					continue;
				} else {
					readPostFix(n.loPost, currentPrefix);
					Node<V> n2 = createNode(key, val, currentPrefix, n.loVal, n.posDiff + 1);
					if (n2 == null) {
						V prev = n.loVal;
						n.loVal = val;
						return prev; 
					}
					n.lo = n2;
					n.loPost = null;
					n.loVal = null;
					size++;
					return null;
				}
			}
		}
	}
	
	private void checkDim0() {
		if (DIM != SINGLE_DIM) {
			throw new IllegalStateException("Please use ___KD() methods for k-dimensional data.");
		}
	}

	public void printTree() {
		System.out.println("Tree: \n" + toString());
	}
	
	public String toString() {
		if (root == null) {
			if (rootKey != null) {
				return "-" + BitTools.toBinary(rootKey, 64) + " v=" + rootVal;
			}
			return "- -";
		}
		Node<V> n = root;
		StringBuilder s = new StringBuilder();
		printNode(n, s, "", 0);
		return s.toString();
	}
	
	private void printNode(Node<V> n, StringBuilder s, String level, int currentDepth) {
		char NL = '\n'; 
		if (n.infix != null) {
			s.append(level + "n: " + currentDepth + "/" + n.posDiff + " " + 
					BitTools.toBinary(n.infix, 64) + NL);
		} else {
			s.append(level + "n: " + currentDepth + "/" + n.posDiff + " i=0" + NL);
		}
		if (n.lo != null) {
			printNode(n.lo, s, level + "-", n.posDiff+1);
		} else {
			s.append(level + " " + BitTools.toBinary(n.loPost, 64) + " v=" + n.loVal + NL);
		}
		if (n.hi != null) {
			printNode(n.hi, s, level + "-", n.posDiff+1);
		} else {
			s.append(level + " " + BitTools.toBinary(n.hiPost,64) + " v=" + n.hiVal + NL);
		}
	}
	
	public boolean checkTree() {
		if (root == null) {
			if (rootKey != null) {
				return true;
			}
			return true;
		}
		if (rootKey != null) {
			System.err.println("root node AND value != null");
			return false;
		}
		return checkNode(root, 0);
	}
	
	private boolean checkNode(Node<V> n, int firstBitOfNode) {
		//check infix
		if (n.posDiff == firstBitOfNode && n.infix != null) {
			System.err.println("infix with len=0 detected!");
			return false;
		}
		if (n.posFirstBit != firstBitOfNode) {
			System.err.println("infix inconsistency detected!");
			return false;
		}
		if (n.lo != null) {
			if (n.loPost != null) {
				System.err.println("lo: sub-node AND key != null");
				return false;
			}
			checkNode(n.lo, n.posDiff+1);
		} else {
			if (n.loPost == null) {
				System.err.println("lo: sub-node AND key == null");
				return false;
			}
		}
		if (n.hi != null) {
			if (n.hiPost != null) {
				System.err.println("hi: sub-node AND key != null");
				return false;
			}
			checkNode(n.hi, n.posDiff+1);
		} else {
			if (n.hiPost == null) {
				System.err.println("hi: sub-node AND key == null");
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Creates a postfix starting at posDiff+1.
	 * @param val
	 * @param posDiff
	 * @return the postfix.
	 */
	private long[] createPostFix(long[] val, int posDiff) {
		int preLen = posDiff >>> 6;
		long[] p = new long[val.length - preLen];
		System.arraycopy(val, preLen, p, 0, p.length);
		return p;
	}

	private static void readPostFix(long[] postVal, long[] currentPrefix) {
		int preLen = currentPrefix.length - postVal.length;
		System.arraycopy(postVal, 0, currentPrefix, preLen, postVal.length);
	}

	private Node<V> createNode(long[] k1, V val1, long[] k2, V val2, int posFirstBit) {
		int posDiff = compare(k1, k2);
		if (posDiff == -1) {
			return null;
		}
		long[] infix = extractInfix(k1, posFirstBit, posDiff-1);
		long[] p1 = createPostFix(k1, posDiff);
		long[] p2 = createPostFix(k2, posDiff);
		//if (isABitwiseSmallerB(v1, v2)) {
		if (BitTools.getBit(k2, posDiff)) {
			return new Node<V>(posFirstBit, p1, val1, p2, val2, infix, posDiff);
		} else {
			return new Node<V>(posFirstBit, p2, val2, p1, val1, infix, posDiff);
		}
	}
	
	/**
	 * 
	 * @param n
	 * @param infixStart The bit-position of the first infix bits relative to the whole value
	 * @param currentPrefix
	 */
	private static <T> void readInfix(Node<T> n, long[] currentPrefix) {
		if (n.infix == null) {
			return;
		}
		int dst = n.posFirstBit >>> 6;
		System.arraycopy(n.infix, 0, currentPrefix, dst, n.infix.length);
	}

	/**
	 * 
	 * @param v
	 * @param startPos first bit of infix, counting starts with 0 for 1st bit 
	 * @param endPos last bit of infix
	 * @return The infix PLUS leading bits before the infix that belong in the same 'long'.
	 */
	private static long[] extractInfix(long[] v, int startPos, int endPos) {
		if (endPos < startPos) {
			//no infix (LEN = 0)
			return null;
		}
		//TODO In half of the cases we could avoid one 'long' by shifting the bits such that there
		// are less then 64 unused bits
		int start = startPos >>> 6;
		int end = endPos >>> 6;
		long[] inf = new long[end-start+1];
		//System.out.println("s/e/l/sp/ep=" + start + "/" + end + "/" + inf.length + "/" + startPos + "/" + endPos);
		//System.out.println("vl/s/il=" + v.length + "/" + start + "/" + inf.length);
		System.arraycopy(v, start, inf, 0, inf.length);
		//avoid shifting by64 bit which means 0 shifting in Java!
		if ((endPos & 0x3F) < 63) {
			inf[inf.length-1] &= ~((-1L) >>> (1+(endPos & 0x3F))); // & 0x3f == %64
		}
		return inf;
	}

	/**
	 * 
	 * @param v
	 * @param startPos
	 * @return True if the infix matches the value or if no infix is defined
	 */
	private boolean doesInfixMatch(Node<V> n, long[] v, long[] currentVal) {
		if (n.infix == null) {
			return true;
		}
		
		int start = n.posFirstBit >>> 6;
		int end = (n.posDiff-1) >>> 6; 
		for (int i = start; i < end; i++) {
			if (v[i] != currentVal[i]) {
				return false;
			}
		}
		//last element
		int shift = 63 - ((n.posDiff-1) & 0x3f);
		return (v[end] ^ currentVal[end]) >>> shift == 0;
	}

	/**
	 * Compares two values.
	 * @param v1
	 * @param v2
	 * @return Position of the differing bit, or -1 if both values are equal
	 */
	private static int compare(long[] v1, long[] v2) {
		for (int i = 0; i < v1.length; i++) {
			if (v1[i] != v2[i]) {
				return (i*64) + Long.numberOfLeadingZeros(v1[i] ^ v2[i]);
			}
		}
		return -1;
	}

	/**
	 * Compares two values.
	 * @param v1
	 * @param v2
	 * @return {@code true} iff both values are equal
	 */
	private static boolean isEqual(long[] v1, long[] v2) {
		for (int i = 0; i < v1.length; i++) {
			if (v1[i] != v2[i]) {
				return false;
			}
		}
		return true;
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
	public boolean contains(long[] key) {
		checkDim0();
		return containsNoCheck(key);
	}

	private boolean containsNoCheck(long[] val) {
		if (root == null) {
			if (rootKey != null) {
				if (isEqual(val, rootKey)) {
					return true;
				}
			}
			return false;
		}
		Node<V> n = root;
		long[] currentPrefix = new long[val.length];
		while (true) {
			readInfix(n, currentPrefix);
			
			if (!doesInfixMatch(n, val, currentPrefix)) {
				return false;
			}			
			
			//infix matches, so now we check sub-nodes and postfixes
			if (BitTools.getAndCopyBit(val, n.posDiff, currentPrefix)) {
				if (n.hi != null) {
					n = n.hi;
					continue;
				} 
				readPostFix(n.hiPost, currentPrefix);
			} else {
				if (n.lo != null) {
					n = n.lo;
					continue;
				}
				readPostFix(n.loPost, currentPrefix);
			}
			return isEqual(val, currentPrefix);
		}
	}
	
	/**
	 * Get the value for a given key. 
	 * @param key
	 * @return the values associated with {@code key} or {@code null} if the key does not exist.
	 */
	public V get(long[] key) {
		checkDim0();
		return getNoCheck(key);
	}

	private V getNoCheck(long[] key) {
		if (root == null) {
			if (rootKey != null) {
				if (isEqual(key, rootKey)) {
					return rootVal;
				}
			}
			return null;
		}
		Node<V> n = root;
		long[] currentPrefix = new long[key.length];
		while (true) {
			readInfix(n, currentPrefix);
			
			if (!doesInfixMatch(n, key, currentPrefix)) {
				return null;
			}			
			
			//infix matches, so now we check sub-nodes and postfixes
			if (BitTools.getAndCopyBit(key, n.posDiff, currentPrefix)) {
				if (n.hi != null) {
					n = n.hi;
					continue;
				} 
				readPostFix(n.hiPost, currentPrefix);
				if (isEqual(key, currentPrefix)) {
					return n.hiVal;
				}
			} else {
				if (n.lo != null) {
					n = n.lo;
					continue;
				}
				readPostFix(n.loPost, currentPrefix);
				if (isEqual(key, currentPrefix)) {
					return n.loVal;
				}
			}
			return null;
		}
	}
	
	private static long[] clone(long[] v) {
		long[] r = new long[v.length];
		System.arraycopy(v, 0, r, 0, v.length);
		return r;
	}

	/**
	 * Remove a key and its value
	 * @param key
	 * @return The value of the key of {@code null} if the value was not found. 
	 */
	public V remove(long[] key) {
		checkDim0();
		return removeNoCheck(key);
	}
	
	private V removeNoCheck(long[] val2) {
		if (root == null) {
			if (rootKey != null) {
				if (isEqual(val2, rootKey)) {
					size--;
					rootKey = null;
					V prev = rootVal;
					rootVal = null;
					return prev;
				}
			}
			return null;
		}
		Node<V> n = root;
		long[] currentPrefix = new long[val2.length];
		Node<V> parent = null;
		boolean isParentHigh = false;
		while (true) {
			readInfix(n, currentPrefix);
			
			if (!doesInfixMatch(n, val2, currentPrefix)) {
				return null;
			}
			
			//infix matches, so now we check sub-nodes and postfixes
			if (BitTools.getAndCopyBit(val2, n.posDiff, currentPrefix)) {
				if (n.hi != null) {
					isParentHigh = true;
					parent = n;
					n = n.hi;
					continue;
				} else {
					readPostFix(n.hiPost, currentPrefix);
					if (!isEqual(val2, currentPrefix)) {
						return null;
					}
					//match! --> delete node
					//a) first recover other values
					long[] newPost = null;
					if (n.loPost != null) {
						readPostFix(n.loPost, currentPrefix);
						newPost = currentPrefix;
					}
					//b) replace data in parent node
					BitTools.setBit(currentPrefix, n.posDiff, false);
					updateParentAfterRemove(parent, newPost, n.loVal, n.lo, isParentHigh, currentPrefix, n);
					return n.hiVal;
				}
			} else {
				if (n.lo != null) {
					isParentHigh = false;
					parent = n;
					n = n.lo;
					continue;
				} else {
					readPostFix(n.loPost, currentPrefix);
					if (!isEqual(val2, currentPrefix)) {
						return null;
					}
					//match! --> delete node
					//a) first recover other values
					long[] newPost = null;
					if (n.hiPost != null) {
						readPostFix(n.hiPost, currentPrefix);
						newPost = currentPrefix;
					}
					//b) replace data in parent node
					//for new infixes...
					BitTools.setBit(currentPrefix, n.posDiff, true);
					updateParentAfterRemove(parent, newPost, n.hiVal, n.hi, isParentHigh, currentPrefix, n);
					return n.loVal;
				}
			}
		}
	}
	
	private void updateParentAfterRemove(Node<V> parent, long[] newPost, V newVal,
			Node<V> newSub, boolean isParentHigh, long[] currentPrefix, Node<V> n) {
		
		if (newSub != null) {
			readInfix(newSub, currentPrefix);
		}
		if (parent == null) {
			rootKey = newPost;
			rootVal = newVal;
			root = newSub;
		} else if (isParentHigh) {
			if (newSub == null) {
				parent.hiPost = createPostFix(currentPrefix, parent.posDiff);
				parent.hiVal = newVal;
			} else {
				parent.hiPost = null;
				parent.hiVal = null;
			}
			parent.hi = newSub;
		} else {
			if (newSub == null) {
				parent.loPost = createPostFix(currentPrefix, parent.posDiff);
				parent.loVal = newVal;
			} else {
				parent.loPost = null;
				parent.loVal = null;
			}
			parent.lo = newSub;
		}
		if (newSub != null) {
			newSub.posFirstBit = n.posFirstBit;
			newSub.infix = extractInfix(currentPrefix, newSub.posFirstBit, newSub.posDiff-1);
		}
		size--;
	}

	/**
	 * Create an iterator over all values, keys or entries.
	 * @return the iterator
	 */
	public FullIterator<V> iterator() {
		checkDim0(); 
		return new FullIterator<V>(this, DEPTH);
	}
	
	public static class FullIterator<V> implements Iterator<V> {
		private final long[] valIntTemplate;
		private long[] nextKey = null; 
		private V nextValue = null;
		private final Node<V>[] stack;
		 //0==read_lower; 1==read_upper; 2==go_to_parent
		private static final byte READ_LOWER = 0;
		private static final byte READ_UPPER = 1;
		private static final byte RETURN_TO_PARENT = 2;
		private final byte[] readHigherNext;
		private int stackTop = -1;

		@SuppressWarnings("unchecked")
		public FullIterator(CritBit<V> cb, int DEPTH) {
			this.stack = new Node[DEPTH];
			this.readHigherNext = new byte[DEPTH];  // default = false
			int intArrayLen = (DEPTH+63) >>> 6;
			this.valIntTemplate = new long[intArrayLen];

			if (cb.rootKey != null) {
				readNextVal(cb.rootKey, cb.rootVal);
				return;
			}
			if (cb.root == null) {
				//Tree is empty
				return;
			}
			Node<V> n = cb.root;
			CritBit.readInfix(n, valIntTemplate);
			stack[++stackTop] = cb.root;
			findNext();
		}

		private void findNext() {
			while (stackTop >= 0) {
				Node<V> n = stack[stackTop];
				//check lower
				if (readHigherNext[stackTop] == READ_LOWER) {
					readHigherNext[stackTop] = READ_UPPER;
					//TODO use bit directly to check validity
					BitTools.setBit(valIntTemplate, n.posDiff, false);
					if (n.loPost != null) {
						CritBit.readPostFix(n.loPost, valIntTemplate);
						readNextVal(valIntTemplate, n.loVal);
						return;
						//proceed to check upper
					} else {
						CritBit.readInfix(n.lo, valIntTemplate);
						stack[++stackTop] = n.lo;
						readHigherNext[stackTop] = READ_LOWER;
						continue;
					}
				}
				//check upper
				if (readHigherNext[stackTop] == READ_UPPER) {
					readHigherNext[stackTop] = RETURN_TO_PARENT;
					BitTools.setBit(valIntTemplate, n.posDiff, true);
					if (n.hiPost != null) {
						CritBit.readPostFix(n.hiPost, valIntTemplate);
						readNextVal(valIntTemplate, n.hiVal);
						--stackTop;
						return;
						//proceed to move up a level
					} else {
						CritBit.readInfix(n.hi, valIntTemplate);
						stack[++stackTop] = n.hi;
						readHigherNext[stackTop] = READ_LOWER;
						continue;
					}
				}
				//proceed to move up a level
				--stackTop;
			}
			//Finished
			nextValue = null;
			nextKey = null;
		}


		/**
		 * Full comparison on the parameter. Assigns the parameter to 'nextVal' if comparison
		 * fits.
		 * @param keyTemplate
		 * @return Whether we have a match or not
		 */
		private void readNextVal(long[] keyTemplate, V value) {
			nextValue = value;
			nextKey = CritBit.clone(keyTemplate);
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

		public long[] nextKey() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			long[] ret = nextKey;
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
	
	public QueryIterator<V> query(long[] min, long[] max) {
		checkDim0(); 
		return new QueryIterator<V>(this, min, max, DEPTH);
	}
	
	public static class QueryIterator<V> implements Iterator<V> {
		private final long[] valIntTemplate;
		private final long[] minOrig;
		private final long[] maxOrig;
		private long[] nextKey = null; 
		private V nextValue = null;
		private final Node<V>[] stack;
		 //0==read_lower; 1==read_upper; 2==go_to_parent
		private static final byte READ_LOWER = 0;
		private static final byte READ_UPPER = 1;
		private static final byte RETURN_TO_PARENT = 2;
		private final byte[] readHigherNext;
		private int stackTop = -1;

		@SuppressWarnings("unchecked")
		public QueryIterator(CritBit<V> cb, long[] minOrig, long[] maxOrig, int DEPTH) {
			this.stack = new Node[DEPTH];
			this.readHigherNext = new byte[DEPTH];  // default = false
			int intArrayLen = (DEPTH+63) >>> 6;
			this.valIntTemplate = new long[intArrayLen];
			this.minOrig = minOrig;
			this.maxOrig = maxOrig;

			if (cb.rootKey != null) {
				checkMatchFullIntoNextVal(cb.rootKey, cb.rootVal);
				return;
			}
			if (cb.root == null) {
				//Tree is empty
				return;
			}
			Node<V> n = cb.root;
			readInfix(n, valIntTemplate);
			if (!checkMatch(valIntTemplate, n.posDiff)) {
				return;
			}
			stack[++stackTop] = cb.root;
			findNext();
		}

		private void findNext() {
			while (stackTop >= 0) {
				Node<V> n = stack[stackTop];
				//check lower
				if (readHigherNext[stackTop] == READ_LOWER) {
					readHigherNext[stackTop] = READ_UPPER;
					//TODO use bit directly to check validity
					BitTools.setBit(valIntTemplate, n.posDiff, false);
					if (checkMatch(valIntTemplate, n.posDiff)) {
						if (n.loPost != null) {
							readPostFix(n.loPost, valIntTemplate);
							if (checkMatchFullIntoNextVal(valIntTemplate, n.loVal)) {
								return;
							} 
							//proceed to check upper
						} else {
							readInfix(n.lo, valIntTemplate);
							stack[++stackTop] = n.lo;
							readHigherNext[stackTop] = READ_LOWER;
							continue;
						}
					}
				}
				//check upper
				if (readHigherNext[stackTop] == READ_UPPER) {
					readHigherNext[stackTop] = RETURN_TO_PARENT;
					BitTools.setBit(valIntTemplate, n.posDiff, true);
					if (checkMatch(valIntTemplate, n.posDiff)) {
						if (n.hiPost != null) {
							readPostFix(n.hiPost, valIntTemplate);
							if (checkMatchFullIntoNextVal(valIntTemplate, n.hiVal)) {
								--stackTop;
								return;
							} 
							//proceed to move up a level
						} else {
							readInfix(n.hi, valIntTemplate);
							stack[++stackTop] = n.hi;
							readHigherNext[stackTop] = READ_LOWER;
							continue;
						}
					}
				}
				//proceed to move up a level
				--stackTop;
			}
			//Finished
			nextValue = null;
			nextKey = null;
		}


		/**
		 * Full comparison on the parameter. Assigns the parameter to 'nextVal' if comparison
		 * fits.
		 * @param keyTemplate
		 * @return Whether we have a match or not
		 */
		private boolean checkMatchFullIntoNextVal(long[] keyTemplate, V value) {
			//TODO optimise: do not check dimensions that can not possibly fail
			//  --> Track dimensions that could fail.
			// --> Check only dimensions between depth of parent and current depth.
			//     At the same time, don't check more than (currentDept-K) dimensions (i.e. =K dim)

			boolean loMatch = false;
			boolean hiMatch = false;
			for (int i = 0; i < keyTemplate.length; i++) {
				if ((!loMatch && minOrig[i] > keyTemplate[i]) || 
						(!hiMatch && keyTemplate[i] > maxOrig[i])) { 
					return false;
				}
				if (minOrig[i] < keyTemplate[i]) { 
					loMatch = true;
					if (loMatch && hiMatch) {
						break;
					}
				}
				if (keyTemplate[i] < maxOrig[i]) { 
					hiMatch = true;
					if (loMatch && hiMatch) {
						break;
					}
				}
			}
			nextValue = value;
			nextKey = CritBit.clone(keyTemplate);
			return true;
		}
		
		private boolean checkMatch(long[] keyTemplate, int currentDepth) {
			int i;
			boolean loMatch = false;
			boolean hiMatch = false;
			for (i = 0; (i+1)*64 <= currentDepth; i++) {
//				if (minOrig[i] > valTemplate[i]	|| valTemplate[i] > maxOrig[i]) {  
//					return false;
//				}
				if ((!loMatch && minOrig[i] > keyTemplate[i]) || 
						(!hiMatch && keyTemplate[i] > maxOrig[i])) { 
					return false;
				}
				if (minOrig[i] < keyTemplate[i]) { 
					loMatch = true;
					if (loMatch && hiMatch) {
						break;
					}
				}
				if (keyTemplate[i] < maxOrig[i]) { 
					hiMatch = true;
					if (loMatch && hiMatch) {
						break;
					}
				}
			}
			if ((i+1)*64 != currentDepth+1) {
				int toIgnore = ((i+1)*64) - currentDepth;
				long mask = (-1L) << toIgnore;
				if (!loMatch && (minOrig[i] & mask) > (keyTemplate[i] & mask)) {  
					return false;
				}
				if (!hiMatch && (keyTemplate[i] & mask) > (maxOrig[i] & mask)) {  
					return false;
				}
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

		public long[] nextKey() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			long[] ret = nextKey;
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
	 * Add a key value pair to the tree or replace the value if the key already exists.
	 * @param key
	 * @param val
	 * @return The previous value or {@code null} if there was no previous value
	 */
	@Override
	public V putKD(long[] key, V val) {
		checkDIM(key);
		long[] vi = BitTools.mergeLong(DEPTH, key);
		return putNoCheck(vi, val);
	}
	
	/**
	 * Check whether a given key exists in the tree.
	 * @param key
	 * @return {@code true} if the key exists otherwise {@code false}
	 */
	public boolean containsKD(long[] key) {
		checkDIM(key);
		long[] vi = BitTools.mergeLong(DEPTH, key);
		return containsNoCheck(vi);
	}

	/**
	 * Get the value for a given key. 
	 * @param key
	 * @return the values associated with {@code key} or {@code null} if the key does not exist.
	 */
	public V getKD(long[] key) {
		checkDIM(key);
		long[] vi = BitTools.mergeLong(DEPTH, key);
		return getNoCheck(vi);
	}

	/**
	 * Remove a key and its value
	 * @param key
	 * @return The value of the key of {@code null} if the value was not found. 
	 */
	public V removeKD(long[] key) {
		checkDIM(key);
		long[] vi = BitTools.mergeLong(DEPTH, key);
		return removeNoCheck(vi);
	}
	
	private void checkDIM(long[] key) {
		if (key.length != DIM) {
			throw new IllegalArgumentException("Dimension mismatch: " + key.length + " vs " + DIM);
		}
	}
	
	/**
	 * Performs a k-dimensional query.
	 * @param min
	 * @param max
	 * @return Result iterator
	 */
	public QueryIteratorKD<V> queryKD(long[] min, long[] max) {
		checkDIM(min);
		checkDIM(max);
		return new QueryIteratorKD<V>(this, min, max, DIM, DEPTH);
	}
	
	public static class QueryIteratorKD<V> implements Iterator<V> {

		private final long[] keyOrigTemplate;
		private final long[] minOrig;
		private final long[] maxOrig;
		private final int DIM;
		private final int DIM_INV_16;
		private final int DEPTH;
		private final int DEPTH_OFFS;
		private V nextValue = null;
		private long[] nextKey = null;
		private final Node<V>[] stack;
		 //0==read_lower; 1==read_upper; 2==go_to_parent
		private static final byte READ_LOWER = 0;
		private static final byte READ_UPPER = 1;
		private static final byte RETURN_TO_PARENT = 2;
		private final byte[] readHigherNext;
		private int stackTop = -1;

		@SuppressWarnings("unchecked")
		public QueryIteratorKD(CritBit<V> cb, long[] minOrig, long[] maxOrig, int DIM, int DEPTH) {
			this.stack = new Node[DIM*DEPTH];
			this.readHigherNext = new byte[DIM*DEPTH];  // default = false
			this.keyOrigTemplate = new long[DIM];
			this.minOrig = minOrig;
			this.maxOrig = maxOrig;
			this.DIM = DIM;
			this.DIM_INV_16 = 1 + ((1<<16)+1)/DIM;
			this.DEPTH = DEPTH;
			this.DEPTH_OFFS = 64-DEPTH;  //the shift local to any Long

			if (cb.rootKey != null) {
				readPostFixAndSplit(cb.rootKey, 0, keyOrigTemplate);
				checkMatchOrigKDFullIntoNextVal(keyOrigTemplate, cb.rootVal);
				return;
			}
			if (cb.root == null) {
				//Tree is empty
				return;
			}
			Node<V> n = cb.root;
			readAndSplitInfix(n, keyOrigTemplate);
			if (!checkMatchOrigKD(keyOrigTemplate, n.posDiff)) {
				return;
			}
			stack[++stackTop] = cb.root;
			findNext();
		}

		private void findNext() {
			while (stackTop >= 0) {
				Node<V> n = stack[stackTop];
				//check lower
				if (readHigherNext[stackTop] == READ_LOWER) {
					readHigherNext[stackTop] = READ_UPPER;
					//TODO use bit directly to check validity
					unsetBitAfterSplit(keyOrigTemplate, n.posDiff);
					if (checkMatchOrigKD(keyOrigTemplate, n.posDiff)) {
						if (n.loPost != null) {
							readPostFixAndSplit(n.loPost, n.posDiff+1, keyOrigTemplate);
							if (checkMatchOrigKDFullIntoNextVal(keyOrigTemplate, n.loVal)) {
								return;
							} 
							//proceed to check upper
						} else {
							readAndSplitInfix(n.lo, keyOrigTemplate);
							stack[++stackTop] = n.lo;
							readHigherNext[stackTop] = READ_LOWER;
							continue;
						}
					}
				}
				//check upper
				if (readHigherNext[stackTop] == READ_UPPER) {
					readHigherNext[stackTop] = RETURN_TO_PARENT;
					setBitAfterSplit(keyOrigTemplate, n.posDiff);
					if (checkMatchOrigKD(keyOrigTemplate, n.posDiff)) {
						if (n.hiPost != null) {
							readPostFixAndSplit(n.hiPost, n.posDiff+1, keyOrigTemplate);
							if (checkMatchOrigKDFullIntoNextVal(keyOrigTemplate, n.hiVal)) {
								--stackTop;
								return;
							} 
							//proceed to move up a level
						} else {
							readAndSplitInfix(n.hi, keyOrigTemplate);
							stack[++stackTop] = n.hi;
							readHigherNext[stackTop] = READ_LOWER;
							continue;
						}
					}
				}
				//proceed to move up a level
				--stackTop;
			}
			//Finished
			nextKey = null;
			nextValue = null;
		}

		private void setBitAfterSplit(long[] keyOrigTemplate, int posBitInt) {
			int k = posBitInt % DIM;
			long maskDst = 0x8000000000000000L >>> (posBitInt / DIM)+DEPTH_OFFS;
			keyOrigTemplate[k] |= maskDst;
		}

		private void unsetBitAfterSplit(long[] keyOrigTemplate, int posBitInt) {
			int k = posBitInt % DIM;
			long maskDst = 0x8000000000000000L >>> (posBitInt / DIM)+DEPTH_OFFS;
			keyOrigTemplate[k] &= ~maskDst;
		}

		/**
		 * Full comparison on the parameter. Assigns the parameter to 'nextKey' if comparison
		 * fits.
		 * @param keyTemplate
		 * @return Whether we have a match or not
		 */
		private boolean checkMatchOrigKDFullIntoNextVal(long[] keyOrigTemplate, V value) {
			//TODO optimise: do not check dimensions that can not possibly fail
			//  --> Track dimensions that could fail.

			for (int k = 0; k < DIM; k++) {
				if (minOrig[k] > keyOrigTemplate[k] || keyOrigTemplate[k] > maxOrig[k]) { 
					return false;
				}
			}
			nextKey = CritBit.clone(keyOrigTemplate);
			nextValue = value;
			return true;
		}
		
		private boolean checkMatchOrigKD(long[] keyOrigTemplate, int currentDepth) {
			//TODO no startBit given. Could we use it to avoid unnecessary checking of prefix?
			//TODO optimise: do not check dimensions that can not possibly fail
			//  --> Track dimensions that could fail.

			//TODO avoid this! For example track DEPTHs separately for each k in an currentDep[]
			int commonDepth = getDepthAcrossDims(currentDepth);//currentDepth / DIM;
			int openBits = DEPTH-commonDepth;
			long minMask = (-1L) << openBits;  // 0xFF00
			long maxMask = ~minMask;           // 0x00FF
			//We don't need to check the same number of bits in all dimensions. 
			//--> calc number of dimensions with more bits than others
			int kLimit = currentDepth - DIM*commonDepth;
			
			//if all have the same length, we can use a simple loop
			if (kLimit == 0) {
				for (int k = 0; k < DIM; k++) {
					if (minOrig[k] > (keyOrigTemplate[k] | maxMask)    // > 0x1212FFFF ? -> exit
							|| (keyOrigTemplate[k] & minMask) > maxOrig[k]) {  // < 0x12120000 ? -> exit 
						return false;
					}
				}
				return true;
			}

			//first check DIMs with fewer bits
			for (int k = kLimit; k < DIM; k++) {
				if (minOrig[k] > (keyOrigTemplate[k] | maxMask)    // > 0x1212FFFF ? -> exit
						|| (keyOrigTemplate[k] & minMask) > maxOrig[k]) {  // < 0x12120000 ? -> exit 
					return false;
				}
			}
			//know proceed with one more bit
			maxMask >>>= 1;
			minMask = ~maxMask;
			for (int k = 0; k < kLimit; k++) {
				if (minOrig[k] > (keyOrigTemplate[k] | maxMask) 
						|| (keyOrigTemplate[k] & minMask) > maxOrig[k]) {
					return false;
				}
			}
			return true;

		}
		
		/**
		 * 
		 * @param n
		 * @param infixStart The bit-position of the first infix bits relative to the whole value
		 * @param currentPrefix
		 */
		private <T> void readAndSplitInfix(Node<T> n, long[] currentPrefixOrig) {
			if (n.infix == null) {
				return;
			}
			readAndSplit(n.infix, n.posFirstBit, n.posDiff, currentPrefixOrig);
		}

		private void readPostFixAndSplit(long[] postVal, int posFirstBit, long[] currentPrefixOrig) {
			int stopBit = DIM*DEPTH;
			readAndSplit(postVal, posFirstBit, stopBit, currentPrefixOrig);
		}
		
		/**
		 * 
		 * @param src Interleaved src array
		 * @param posFirstBit First bit to be transferred
		 * @param stopBit Stop bit (last bit to be transferred + 1)
		 * @param dst Non-interleaved destination array
		 */
		private void readAndSplit(long[] srcVal, int posFirstBit, long stopBit, long[] dstVal) {
			long maskSrc = 0x8000000000000000L >>> (posFirstBit & 0x3F);
			int k = posFirstBit % DIM;
			//long maskDst = 0x8000000000000000L >>> (posFirstBit / DIM)+DEPTH_OFFS;
			long maskDst = 0x8000000000000000L >>> (getDepthAcrossDims(posFirstBit)+DEPTH_OFFS);
			int src = 0;
			for (int i = posFirstBit; i < stopBit; i++) {
				if ((srcVal[src] & maskSrc) == 0) {
					dstVal[k] &= ~maskDst;
				} else {
					dstVal[k] |= maskDst;
				}
				if (++k >= DIM) {
					k = 0;
					maskDst >>>= 1;
				}
				maskSrc >>>= 1;
				if (maskSrc == 0) {
					//overflow for i multiple of 64
					src++;
					maskSrc = 0x8000000000000000L;
				}
			}
		}

		/**
		 * Calculate the common minimum depth across all dimensions.
		 * This is equal to {@code floor(posDirstBit/DIM)}.
		 * @param posFirstBit
		 * @return depth across dims.
		 */
		private int getDepthAcrossDims(int posFirstBit) {
//			int depthAcrossDims = posFirstBit/DIM;
			int depthAcrossDims = (posFirstBit*DIM_INV_16) >>> 16;
//			if (depthAcrossDims != posFirstBit/DIM) {
//				System.out.println("dad=" + depthAcrossDims + " pfb/D=" + (posFirstBit/DIM) + " DIM="+DIM + " pfb=" + posFirstBit + "  di16=" + DIM_INV_16);
//			}
			return depthAcrossDims;
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

		public long[] nextKey() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			long[] ret = nextKey;
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
		private final long[] key;
		private final V value;
		Entry(long[] key, V value) {
			this.key = key;
			this.value = value;		
		}
		public long[] key() {
			return key;
		}
		public V value() {
			return value;
		}
	}
	
}
