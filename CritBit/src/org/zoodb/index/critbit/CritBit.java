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
 * @author Tilmann Zaeschke
 */
import java.util.Iterator;
import java.util.NoSuchElementException;

public class CritBit {

	private final int DEPTH;
	private final int DIM;
	
	private Node root;
	private long[] rootVal;

	private int size;
	
	private static class Node {
		Node lo;
		Node hi;
		long[] loVal;
		long[] hiVal;
		long[] infix;
		int posDiff;
		
		Node(int localDepth, int DEPTH, long[] loVal, long[] hiVal, long[] infix, int posDiff) {
			this.loVal = loVal;
			this.hiVal = hiVal;
			this.infix = infix;
			this.posDiff = posDiff;
		}
	}
	
	
	private CritBit(int depth, int dim) {
		//TODO remove DEPTH?
		this.DEPTH = depth;
		this.DIM = dim;
	}
	
	public static CritBit create(int depth) {
		// -1 ensures that DIM is never used in this case.
		return new CritBit(depth, -1);
	}
	
	/**
	 * 
	 * @param depth
	 * @param dim
	 * @return k-dimensional tree
	 */
	public static CritBit createKD(int depth, int dim) {
		return new CritBit(depth, dim);
	}
	
	/**
	 * 
	 * @param val
	 * @return False if the value already exists.
	 */
	public boolean insert(long[] val) {
		checkDim0();
		return insertNoCheck(val);
	}
	
	private boolean insertNoCheck(long[] val) {
		if (root == null) {
			if (rootVal == null) {
				rootVal = new long[val.length];
				System.arraycopy(val, 0, rootVal, 0, val.length);
			} else {
				Node n2 = createNode(val, rootVal, 0);
				if (n2 == null) {
					return false; 
				}
				root = n2;
				rootVal = null;
			}
			size++;
			return true;
		}
		Node n = root;
		int currentDepth = 0;
		long[] currentPrefix = new long[val.length];
		while (true) {
			readInfix(n, currentDepth, currentPrefix);
			
			if (n.infix != null) {
				//split infix?
				int posDiff = compare(val, currentPrefix);
				if (posDiff < n.posDiff && posDiff != -1) {
					long[] subInfix = extractInfix(n.infix, posDiff+1, n.posDiff-1);
					//new sub-node
					Node newSub = new Node(-1, DEPTH, null, null, subInfix, n.posDiff);
					newSub.hi = n.hi;
					newSub.lo = n.lo;
					newSub.hiVal = n.hiVal;
					newSub.loVal = n.loVal;
					if (BitTools.getBit(val, posDiff)) {
						n.hi = null;
						n.hiVal = createPostFix(val, posDiff);
						n.lo = newSub;
						n.loVal = null;
					} else {
						n.hi = newSub;
						n.hiVal = null;
						n.lo = null;
						n.loVal = createPostFix(val, posDiff);
					}
					n.infix = extractInfix(currentPrefix, currentDepth, posDiff-1);
					n.posDiff = posDiff;
					size++;
					return true;
				}
			}			
			
			//infix matches, so now we check sub-nodes and postfixes
			currentDepth = n.posDiff;
			if (BitTools.getBit(val, currentDepth)) {
				currentDepth++;
				if (n.hi != null) {
					n = n.hi;
					continue;
				} else {
					readPostFix(n.hiVal, currentPrefix);
					Node n2 = createNode(val, currentPrefix, currentDepth);
					if (n2 == null) {
						return false; 
					}
					n.hi = n2;
					n.hiVal = null;
					size++;
					return true;
				}
			} else {
				currentDepth++;
				if (n.lo != null) {
					n = n.lo;
					continue;
				} else {
					readPostFix(n.loVal, currentPrefix);
					Node n2 = createNode(val, currentPrefix, currentDepth);
					if (n2 == null) {
						return false; 
					}
					n.lo = n2;
					n.loVal = null;
					size++;
					return true;
				}
			}
		}
	}
	
	private void checkDim0() {
		if (DIM != -1) {
			throw new IllegalStateException("Please use ___KD() methods for k-dimensional data.");
		}
	}

	public void printTree() {
		System.out.println("Tree: \n" + toString());
	}
	
	public String toString() {
		if (root == null) {
			if (rootVal != null) {
				return "-" + BitTools.toBinary(rootVal, 64);
			}
			return "- -";
		}
		Node n = root;
		StringBuilder s = new StringBuilder();
		printNode(n, s, "", 0);
		return s.toString();
	}
	
	private void printNode(Node n, StringBuilder s, String level, int currentDepth) {
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
			s.append(level + " " + BitTools.toBinary(n.loVal, 64) + NL);
		}
		if (n.hi != null) {
			printNode(n.hi, s, level + "-", n.posDiff+1);
		} else {
			s.append(level + " " + BitTools.toBinary(n.hiVal,64) + NL);
		}
	}
	
	private void printNode(Node n, int currentDepth) {
		StringBuilder s = new StringBuilder();
		printNode(n, s, "+", currentDepth);
		System.out.println("Node: \n" + s);
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

	private Node createNode(long[] v1, long[] v2, int currentDepth) {
		int posDiff = compare(v1, v2);
		if (posDiff == -1) {
			return null;
		}
		long[] infix = extractInfix(v1, currentDepth, posDiff-1);
		long[] p1 = createPostFix(v1, posDiff);
		long[] p2 = createPostFix(v2, posDiff);
		//if (isABitwiseSmallerB(v1, v2)) {
		if (BitTools.getBit(v2, posDiff)) {
			return new Node(0, DEPTH, p1, p2, infix, posDiff);
		} else {
			return new Node(0, DEPTH, p2, p1, infix, posDiff);
		}
	}
	
	/**
	 * 
	 * @param n
	 * @param infixStart The bit-position of the first infix bits relative to the whole value
	 * @param currentPrefix
	 */
	private static void readInfix(Node n, int infixStart, long[] currentPrefix) {
		if (n.infix == null) {
			return;
		}
		int dst = infixStart >>> 6;
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
	private boolean doesInfixMatch(Node n, long[] v, int startPos) {
		if (n.infix == null) {
			return true;
		}
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
			if (rootVal != null) {
				int posDiff = compare(val, rootVal);
				if (posDiff == -1) {
					return true;
				}
			}
			return false;
		}
		Node n = root;
		int currentDepth = 0;
		long[] currentPrefix = new long[val.length];
		while (true) {
			readInfix(n, currentDepth, currentPrefix);
			
			if (!doesInfixMatch(n, val, currentDepth)) {
				return false;
			}			
			
			//infix matches, so now we check sub-nodes and postfixes
			currentDepth = n.posDiff;
			if (BitTools.getBit(val, currentDepth)) {
				currentDepth++;
				if (n.hi != null) {
					n = n.hi;
					continue;
				} 
				readPostFix(n.hiVal, currentPrefix);
			} else {
				currentDepth++;
				if (n.lo != null) {
					n = n.lo;
					continue;
				}
				readPostFix(n.loVal, currentPrefix);
			}
			return compare(val, currentPrefix) == -1;
		}
	}
	
	private static long[] clone(long[] v) {
		long[] r = new long[v.length];
		System.arraycopy(v, 0, r, 0, v.length);
		return r;
	}

	public boolean remove(long[] val) {
		checkDim0();
		return removeNoCheck(val);
	}
	
	private boolean removeNoCheck(long[] val2) {
		if (root == null) {
			if (rootVal != null) {
				int posDiff = compare(val2, rootVal);
				if (posDiff == -1) {
					size--;
					rootVal = null;
					return true;
				}
			}
			return false;
		}
		Node n = root;
		int currentDepth = 0;
		long[] currentPrefix = new long[val2.length];
		Node parent = null;
		boolean isParentHigh = false;
		while (true) {
			readInfix(n, currentDepth, currentPrefix);
			
			if (!doesInfixMatch(n, val2, currentDepth)) {
				return false;
			}
			
			//infix matches, so now we check sub-nodes and postfixes
			currentDepth = n.posDiff;
			if (BitTools.getBit(val2, currentDepth)) {
				currentDepth++;
				if (n.hi != null) {
					isParentHigh = true;
					parent = n;
					n = n.hi;
					continue;
				} else {
					readPostFix(n.hiVal, currentPrefix);
					int posDiff = compare(val2, currentPrefix);
					if (posDiff != -1) {
						return false;
					}
					//match! --> delete node
					//a) first recover other values
					long[] newPost = null;
					if (n.loVal != null) {
						readPostFix(n.loVal, currentPrefix);
						newPost = currentPrefix;
					}
					//b) replace data in parent node
					updateParentAfterRemove(parent, newPost, n.lo, isParentHigh, currentPrefix, n);
					return true;
				}
			} else {
				currentDepth++;
				if (n.lo != null) {
					isParentHigh = false;
					parent = n;
					n = n.lo;
					continue;
				} else {
					readPostFix(n.loVal, currentPrefix);
					int posDiff = compare(val2, currentPrefix);
					if (posDiff != -1) {
						return false;
					}
					//match! --> delete node
					//a) first recover other values
					long[] newPost = null;
					if (n.hiVal != null) {
						readPostFix(n.hiVal, currentPrefix);
						newPost = currentPrefix;
					}
					//b) replace data in parent node
					updateParentAfterRemove(parent, newPost, n.hi, isParentHigh, currentPrefix, n);
					return true;
				}
			}
		}
	}
	
	private void updateParentAfterRemove(Node parent, long[] newPost,
			Node newSub, boolean isParentHigh, long[] currentPrefix, Node n) {
		if (parent == null) {
			rootVal = newPost;
			root = newSub;
		} else if (isParentHigh) {
			parent.hiVal = createPostFix(currentPrefix, parent.posDiff);
			parent.hi = newSub;
		} else {
			parent.loVal = createPostFix(currentPrefix, parent.posDiff);
			parent.lo = newSub;
		}
		//n.infix = extractInfix(val2, currentDepth-1, n.posDiff-1);
		//FIXME re-evaluate infix
		if (parent != null) {
			//   ((n.posDiff-1)/64)+1 - len_inf
			int start = ((n.posDiff-1) & 0x3F)+1-  (n.infix != null ? n.infix.length : 0);
			start <<= 6; //*64
			parent.infix = extractInfix(currentPrefix, start, n.posDiff-1);
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
		private final Node[] stack;
		 //0==read_lower; 1==read_upper; 2==go_to_parent
		private static final byte READ_LOWER = 0;
		private static final byte READ_UPPER = 1;
		private static final byte RETURN_TO_PARENT = 2;
		private final byte[] readHigherNext;
		private int stackTop = -1;

		public QueryIterator(CritBit cb, long[] minOrig, long[] maxOrig, int DEPTH) {
			this.stack = new Node[DEPTH];
			this.readHigherNext = new byte[DEPTH];  // default = false
			int intArrayLen = (DEPTH+63) >>> 6;
			this.valIntTemplate = new long[intArrayLen];
			this.minOrig = minOrig;
			this.maxOrig = maxOrig;
			this.DEPTH = DEPTH;

			if (cb.rootVal != null) {
				checkMatchFullIntoNextVal(cb.rootVal);
				return;
			}
			if (cb.root == null) {
				//Tree is empty
				return;
			}
			Node n = cb.root;
			readInfix(n, 0, valIntTemplate);
			if (!checkMatch(valIntTemplate, n.posDiff)) {
				return;
			}
			stack[++stackTop] = cb.root;
			findNext();
		}

		private void findNext() {
			while (stackTop >= 0) {
				Node n = stack[stackTop];
				int currentDepth = n.posDiff;
				//check lower
				if (readHigherNext[stackTop] == READ_LOWER) {
					readHigherNext[stackTop] = READ_UPPER;
					//TODO use bit directly to check validity
					BitTools.setBit(valIntTemplate, currentDepth, false);
					if (checkMatch(valIntTemplate, currentDepth)) {
						if (n.loVal != null) {
							readPostFix(n.loVal, valIntTemplate);
							if (checkMatchFullIntoNextVal(valIntTemplate)) {
								return;
							} 
							//proceed to check upper
						} else {
							readInfix(n.lo, currentDepth + 1, valIntTemplate);
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
						if (n.hiVal != null) {
							readPostFix(n.hiVal, valIntTemplate);
							if (checkMatchFullIntoNextVal(valIntTemplate)) {
								--stackTop;
								return;
							} 
							//proceed to move up a level
						} else {
							readInfix(n.hi, currentDepth + 1, valIntTemplate);
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
	 * @param val
	 * @return False if the value already exists.
	 */
	public boolean insertKD(long[] val) {
		checkDIM(val);
		long[] vi = BitTools.mergeLong(DEPTH, val);
		return insertNoCheck(vi);
	}
	
	public boolean containsKD(long[] val) {
		checkDIM(val);
		long[] vi = BitTools.mergeLong(DEPTH, val);
		return containsNoCheck(vi);
	}

	public boolean removeKD(long[] val) {
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
	 * @return
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
		private final Node[] stack;
		 //0==read_lower; 1==read_upper; 2==go_to_parent
		private static final byte READ_LOWER = 0;
		private static final byte READ_UPPER = 1;
		private static final byte RETURN_TO_PARENT = 2;
		private final byte[] readHigherNext;
		private int stackTop = -1;

		public QueryIteratorKD(CritBit cb, long[] minOrig, long[] maxOrig, int DIM, int DEPTH) {
			this.stack = new Node[DIM*DEPTH];
			this.readHigherNext = new byte[DIM*DEPTH];  // default = false
			int intArrayLen = (DIM*DEPTH+63) >>> 6;
			this.valIntTemplate = new long[intArrayLen];
			this.minOrig = minOrig;
			this.maxOrig = maxOrig;
			this.DIM = DIM;
			this.DEPTH = DEPTH;

			if (cb.rootVal != null) {
				checkMatchKDFullIntoNextVal(cb.rootVal);
				return;
			}
			if (cb.root == null) {
				//Tree is empty
				return;
			}
			Node n = cb.root;
			readInfix(n, 0, valIntTemplate);
			if (!checkMatchKD(valIntTemplate, n.posDiff)) {
				return;
			}
			stack[++stackTop] = cb.root;
			findNext();
		}

		private void findNext() {
			while (stackTop >= 0) {
				Node n = stack[stackTop];
				int currentDepth = n.posDiff;
				//check lower
				if (readHigherNext[stackTop] == READ_LOWER) {
					readHigherNext[stackTop] = READ_UPPER;
					//TODO use bit directly to check validity
					BitTools.setBit(valIntTemplate, currentDepth, false);
					if (checkMatchKD(valIntTemplate, currentDepth)) {
						if (n.loVal != null) {
							readPostFix(n.loVal, valIntTemplate);
							if (checkMatchKDFullIntoNextVal(valIntTemplate)) {
								return;
							} 
							//proceed to check upper
						} else {
							readInfix(n.lo, currentDepth + 1, valIntTemplate);
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
					if (checkMatchKD(valIntTemplate, currentDepth)) {
						if (n.hiVal != null) {
							readPostFix(n.hiVal, valIntTemplate);
							if (checkMatchKDFullIntoNextVal(valIntTemplate)) {
								--stackTop;
								return;
							} 
							//proceed to move up a level
						} else {
							readInfix(n.hi, currentDepth + 1, valIntTemplate);
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
