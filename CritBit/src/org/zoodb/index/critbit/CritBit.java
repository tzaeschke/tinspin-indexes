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
 * CritBit is a multi-dimensional crit-bit tree.
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
 * @author Tilmann Zaeschke
 */
import java.util.Iterator;
import java.util.NoSuchElementException;

public class CritBit<V> {

	private final int DEPTH;
	private final int DIM;
	
	private Node<V> root;
	private long[] rootKey;
	private V rootVal;

	private int size;
	
	private static final int SINGLE_DIM = -1;
	
	private static class Node<V> {
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
	
	public static <V> CritBit<V> create(int depth) {
		// SINGLE_DIM ensures that DIM is never used in this case.
		return new CritBit<V>(depth, SINGLE_DIM);
	}
	
	/**
	 * 
	 * @param depth
	 * @param dim
	 * @return k-dimensional tree
	 */
	public static <V> CritBit<V> createKD(int depth, int dim) {
		return new CritBit<V>(depth, dim);
	}
	
	/**
	 * 
	 * @param key
	 * @param val
	 * @return False if the value already exists.
	 */
	public V insert(long[] key, V val) {
		checkDim0();
		return insertNoCheck(key, val);
	}
	
	private V insertNoCheck(long[] key, V val) {
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
					if (BitTools.getBit(key, posDiff)) {
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
			if (BitTools.getBit(key, n.posDiff)) {
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
		if (n.lo != null) {
			if (n.loPost != null) {
				System.err.println("lo: sub-node AND value != null");
				return false;
			}
			checkNode(n.lo, n.posDiff+1);
		}
		if (n.hi != null) {
			if (n.hiPost != null) {
				System.err.println("hi: sub-node AND value != null");
				return false;
			}
			checkNode(n.hi, n.posDiff+1);
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
	private void readInfix(Node<V> n, long[] currentPrefix) {
		if (n.infix == null) {
			return;
		}
		int dst = n.posFirstBit >>> 6;
		for (long l: n.infix) {
			currentPrefix[dst] = 0;  //the infix contains also bits to fill up the front.
			currentPrefix[dst++] |= l;
		}
	}

	/**
	 * 
	 * @param v
	 * @param startPos first bit of infix, counting starts with 0 for 1st bit 
	 * @param endPos last bit of infix
	 * @return The infix PLUS leading bits before the infix that belong in the same 'long'.
	 */
	private long[] extractInfix(long[] v, int startPos, int endPos) {
		//System.out.println("New infix s/e: " + startPos + " / " + endPos); //TODO
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
		//System.out.println("New infix: " + inf[0] + "  " + Bits.toBinary(inf, 64)); //TODO
		//avoid shifting by64 bit which means 0 shifting in Java!
		if ((endPos & 0x3F) < 63) {
			inf[inf.length-1] &= ~((-1L) >>> (1+(endPos & 0x3F))); // & 0x3f == %64
		}
		//System.out.println("New infix: " + inf[0] + "  " + Bits.toBinary(inf, 64)); //TODO
		return inf;
	}

	/**
	 * 
	 * @param v
	 * @param startPos
	 * @return True if the infix matches the value or if no infix is defined
	 */
	private boolean doesInfixMatch(Node<V> n, long[] v) {
		if (n.infix == null) {
			return true;
		}
		int startPos = n.posFirstBit;
		int endPos = n.posDiff-1;
		int start = startPos >>> 6;
		int end = ((endPos+63) >>> 6)-1;
		for (int i = start; i <= end; i++) {
			if (v[i] != n.infix[i-start] && i==end) {
				long mask = (-1L)<< (63-endPos);
				if ((v[i] & mask) != (n.infix[i-start] & mask)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * 
	 * @param a
	 * @param b
	 * @return true iff a<b or (a>0 && b<0)
	 */
	private static boolean isABitwiseSmallerB(long[]a, long[] b) {
		for (int i = 0; i < a.length; i++) {
			if (a[i] < b[i]) {
				if (a[i]<=-0 && b[i]>=0) {
					return false;
				}
				return true;
			}
			if (a[i] > b[i]) {
				if (a[i]>=0 && b[i]<=-0) {
					return true;
				}
				return false;
			}
		}
		throw new IllegalStateException();
	}
	
	/**
	 * Compares two values.
	 * @param v1
	 * @param v2
	 * @return Position of the differing bit, or -1 if both values are equal
	 */
	private int compare(long[] v1, long[] v2) {
		int pos = 0;
		for (int i = 0; i < v1.length; i++) {
			if (v1[i] != v2[i]) {
				long x = v1[i] ^ v2[i];
				pos += Long.numberOfLeadingZeros(x);
				return pos;
			}
			pos += 64;
		}
		return -1;
	}

	public int size() {
		return size;
	}

	public boolean contains(long[] val) {
		checkDim0();
		return containsNoCheck(val);
	}

	private boolean containsNoCheck(long[] val) {
		if (root == null) {
			if (rootKey != null) {
				int posDiff = compare(val, rootKey);
				if (posDiff == -1) {
					return true;
				}
			}
			return false;
		}
		Node<V> n = root;
		long[] currentPrefix = new long[val.length];
		while (true) {
			readInfix(n, currentPrefix);
			
			if (!doesInfixMatch(n, val)) {
				return false;
			}			
			
			//infix matches, so now we check sub-nodes and postfixes
			if (BitTools.getBit(val, n.posDiff)) {
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
			return compare(val, currentPrefix) == -1;
		}
	}
	
	public V get(long[] val) {
		checkDim0();
		return getNoCheck(val);
	}

	private V getNoCheck(long[] val) {
		if (root == null) {
			if (rootKey != null) {
				int posDiff = compare(val, rootKey);
				if (posDiff == -1) {
					return rootVal;
				}
			}
			return null;
		}
		Node<V> n = root;
		long[] currentPrefix = new long[val.length];
		while (true) {
			readInfix(n, currentPrefix);
			
			if (!doesInfixMatch(n, val)) {
				return null;
			}			
			
			//infix matches, so now we check sub-nodes and postfixes
			if (BitTools.getBit(val, n.posDiff)) {
				if (n.hi != null) {
					n = n.hi;
					continue;
				} 
				readPostFix(n.hiPost, currentPrefix);
				if (compare(val, currentPrefix) == -1) {
					return n.hiVal;
				}
			} else {
				if (n.lo != null) {
					n = n.lo;
					continue;
				}
				readPostFix(n.loPost, currentPrefix);
				if (compare(val, currentPrefix) == -1) {
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
	 * 
	 * @param key
	 * @return The value of the key of null if the value was not found. 
	 */
	public V remove(long[] key) {
		checkDim0();
		return removeNoCheck(key);
	}
	
	private V removeNoCheck(long[] val2) {
		if (root == null) {
			if (rootKey != null) {
				int posDiff = compare(val2, rootKey);
				if (posDiff == -1) {
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
			
			if (!doesInfixMatch(n, val2)) {
				return null;
			}
			
			//infix matches, so now we check sub-nodes and postfixes
			if (BitTools.getBit(val2, n.posDiff)) {
				if (n.hi != null) {
					isParentHigh = true;
					parent = n;
					n = n.hi;
					continue;
				} else {
					readPostFix(n.hiPost, currentPrefix);
					int posDiff = compare(val2, currentPrefix);
					if (posDiff != -1) {
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
					int posDiff = compare(val2, currentPrefix);
					if (posDiff != -1) {
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

	public Iterator<long[]> query(long[] min, long[] max) {
		checkDim0(); 
		return new QueryIterator(this, min, max, DEPTH);
	}
	
	public class QueryIterator implements Iterator<long[]> {
		private final long[] valIntTemplate;
		private final long[] minOrig;
		private final long[] maxOrig;
		private final int DEPTH;
		private long[] nextValue = null;
		private final Node<V>[] stack;
		 //0==read_lower; 1==read_upper; 2==go_to_parent
		private static final byte READ_LOWER = 0;
		private static final byte READ_UPPER = 1;
		private static final byte RETURN_TO_PARENT = 2;
		private final byte[] readHigherNext;
		private int stackTop = -1;

		public QueryIterator(CritBit<V> cb, long[] minOrig, long[] maxOrig, int DEPTH) {
			this.stack = new Node[DEPTH];
			this.readHigherNext = new byte[DEPTH];  // default = false
			int intArrayLen = (DEPTH+63) >>> 6;
			this.valIntTemplate = new long[intArrayLen];
			this.minOrig = minOrig;
			this.maxOrig = maxOrig;
			this.DEPTH = DEPTH;

			if (cb.rootKey != null) {
				checkMatchFullIntoNextVal(cb.rootKey);
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
				//TODO remove?
				int currentDepth = n.posDiff;
				//check lower
				if (readHigherNext[stackTop] == READ_LOWER) {
					readHigherNext[stackTop] = READ_UPPER;
					//TODO use bit directly to check validity
					BitTools.setBit(valIntTemplate, currentDepth, false);
					if (checkMatch(valIntTemplate, currentDepth)) {
						if (n.loPost != null) {
							readPostFix(n.loPost, valIntTemplate);
							if (checkMatchFullIntoNextVal(valIntTemplate)) {
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
					BitTools.setBit(valIntTemplate, currentDepth, true);
					if (checkMatch(valIntTemplate, currentDepth)) {
						if (n.hiPost != null) {
							readPostFix(n.hiPost, valIntTemplate);
							if (checkMatchFullIntoNextVal(valIntTemplate)) {
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
		}


		/**
		 * Full comparison on the parameter. Assigns the parameter to 'nextVal' if comparison
		 * fits.
		 * @param valTemplate
		 * @return
		 */
		private boolean checkMatchFullIntoNextVal(long[] valTemplate) {
			//TODO optimise: do not check dimensions that can not possibly fail
			//  --> Track dimensions that could fail.
			// --> Check only dimensions between depth of parent and current depth.
			//     At the same time, don't check more than (currentDept-K) dimensions (i.e. =K dim)

			
			for (int i = 0; i < valTemplate.length; i++) {
				if (minOrig[i] > valTemplate[i] || valTemplate[i] > maxOrig[i]) { 
					return false;
				}
			}
			nextValue = CritBit.clone(valTemplate);
			return true;
		}
		
		private boolean checkMatch(long[] valTemplate, int currentDepth) {
			int i;
			for (i = 0; (i+1)*DEPTH <= currentDepth; i++) {
				if (minOrig[i] > valTemplate[i]	|| valTemplate[i] > maxOrig[i]) {  
					return false;
				}
			}
			if ((i+1)*DEPTH != currentDepth+1) {
				int toIgnore = ((i+1)*DEPTH) - currentDepth;
				if (minOrig[i]>>>toIgnore > valTemplate[i]>>>toIgnore || 
					valTemplate[i]>>>toIgnore > maxOrig[i]>>>toIgnore) {  
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
		public long[] next() {
			if (nextValue == null) {
				throw new NoSuchElementException();
			}
			long[] ret = nextValue;
			findNext();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}
	
	/**
	 * 
	 * @param key
	 * @return False if the value already exists.
	 */
	public V insertKD(long[] key, V val) {
		checkDIM(key);
		long[] vi = BitTools.mergeLong(DEPTH, key);
		return insertNoCheck(vi, val);
	}
	
	public boolean containsKD(long[] val) {
		checkDIM(val);
		long[] vi = BitTools.mergeLong(DEPTH, val);
		return containsNoCheck(vi);
	}

	public V getKD(long[] val) {
		checkDIM(val);
		long[] vi = BitTools.mergeLong(DEPTH, val);
		return getNoCheck(vi);
	}

	public V removeKD(long[] val) {
		checkDIM(val);
		long[] vi = BitTools.mergeLong(DEPTH, val);
		return removeNoCheck(vi);
	}
	
	private void checkDIM(long[] val) {
		if (val.length != DIM) {
			throw new IllegalArgumentException("Dimension mismatch: " + val.length + " vs " + DIM);
		}
	}
	
	/**
	 * Performs a k-dimensional query.
	 * @param min
	 * @param max
	 * @return Result iterator
	 */
	public Iterator<long[]> queryKD(long[] min, long[] max) {
		checkDIM(min);
		checkDIM(max);
		return new QueryIteratorKD(this, min, max, DIM, DEPTH);
	}
	
	public class QueryIteratorKD implements Iterator<long[]> {

		private final long[] valIntTemplate;
		private final long[] minOrig;
		private final long[] maxOrig;
		private final int DIM;
		private final int DEPTH;
		private long[] nextValue = null;
		private final Node<V>[] stack;
		 //0==read_lower; 1==read_upper; 2==go_to_parent
		private static final byte READ_LOWER = 0;
		private static final byte READ_UPPER = 1;
		private static final byte RETURN_TO_PARENT = 2;
		private final byte[] readHigherNext;
		private int stackTop = -1;

		public QueryIteratorKD(CritBit<V> cb, long[] minOrig, long[] maxOrig, int DIM, int DEPTH) {
			this.stack = new Node[DIM*DEPTH];
			this.readHigherNext = new byte[DIM*DEPTH];  // default = false
			int intArrayLen = (DIM*DEPTH+63) >>> 6;
			this.valIntTemplate = new long[intArrayLen];
			this.minOrig = minOrig;
			this.maxOrig = maxOrig;
			this.DIM = DIM;
			this.DEPTH = DEPTH;

			if (cb.rootKey != null) {
				checkMatchKDFullIntoNextVal(cb.rootKey);
				return;
			}
			if (cb.root == null) {
				//Tree is empty
				return;
			}
			Node<V> n = cb.root;
			readInfix(n, valIntTemplate);
			if (!checkMatchKD(valIntTemplate, n.posDiff)) {
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
					if (checkMatchKD(valIntTemplate, n.posDiff)) {
						if (n.loPost != null) {
							readPostFix(n.loPost, valIntTemplate);
							if (checkMatchKDFullIntoNextVal(valIntTemplate)) {
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
					if (checkMatchKD(valIntTemplate, n.posDiff)) {
						if (n.hiPost != null) {
							readPostFix(n.hiPost, valIntTemplate);
							if (checkMatchKDFullIntoNextVal(valIntTemplate)) {
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
		}


		/**
		 * Full comparison on the parameter. Assigns the parameter to 'nextVal' if comparison
		 * fits.
		 * @param valTemplate
		 * @return
		 */
		private boolean checkMatchKDFullIntoNextVal(long[] valTemplate) {
			//TODO optimise: do not check dimensions that can not possibly fail
			//  --> Track dimensions that could fail.

			long[] valTOrig = BitTools.splitLong(DIM, DEPTH, valTemplate);
			for (int k = 0; k < DIM; k++) {
				if (minOrig[k] > valTOrig[k] || valTOrig[k] > maxOrig[k]) { 
					return false;
				}
			}
			nextValue = valTOrig;
			return true;
		}
		
		private boolean checkMatchKD(long[] valTemplate, int currentDepth) {
			//do fast superficial check on interleaved value

			//TODO?
			//System.err.println("Reenable for positive values?");
			//if (!isABitwiseSmallerB(minInt, valInt) || !isABitwiseSmallerB(valInt, maxInt)) {
			//	return false;
			//}

			//TODO avoid this! For example track DEPTHs separately for each k in an currentDep[]
			int commonDepth = currentDepth / DIM; 
			int kLimit = currentDepth - DIM*commonDepth;
			int openBits = DEPTH-commonDepth;
			long minMask = (-1L) << openBits;  // 0xFF00
			long maxMask = ~minMask;           // 0x00FF

			//TODO optimise: do not check dimensions that can not possibly fail
			//  --> Track dimensions that could fail.

			long[] valTOrig = BitTools.splitLong(DIM, DEPTH, valTemplate);
			for (int k = 0; k < DIM-kLimit; k++) {
				if (minOrig[k] > (valTOrig[k] | maxMask)    // > 0x1212FFFF ? -> exit
						|| (valTOrig[k] & minMask) > maxOrig[k]) {  // < 0x12120000 ? -> exit 
					return false;
				}
			}
			if (kLimit > 0) {
				minMask <<= 1;
				maxMask = ~minMask;
				for (int k = DIM-kLimit; k < DIM; k++) {
					if (minOrig[k] > (valTOrig[k] | maxMask) 
							|| (valTOrig[k] & minMask) > maxOrig[k]) {
						return false;
					}
				}
			}
			return true;
		}

		@Override
		public boolean hasNext() {
			return nextValue != null;
		}

		@Override
		public long[] next() {
			if (nextValue == null) {
				throw new NoSuchElementException();
			}
			long[] ret = nextValue;
			findNext();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

}
