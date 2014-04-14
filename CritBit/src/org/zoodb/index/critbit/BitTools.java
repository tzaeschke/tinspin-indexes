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

public class BitTools {

    /**
     * WARNING
     * This method turns -0.0 into 0.0. Therefore -0.0 is not smaller than 0.0 when stored in
     * an index.
     * @param value
     * @return long representation.
     */
	public static long toSortableLong(double value) {
		//To create a sortable long, we convert the double to a long using the IEEE-754 standard,
		//which stores floats in the form <sign><exponent-127><mantissa> .
		//This result is properly ordered longs for all positive doubles. Negative values have
		//inverse ordering. For negative doubles, we therefore simply invert them to make them 
		//sortable, however the sign must be inverted again to stay negative.
		if (value == -0.0) {
			value = 0.0;
		}
		if (value < 0.0) {
			long l = Double.doubleToRawLongBits(value);
			l = ~l;
			l |= (1l << 63l);
			return l;
		}
		return Double.doubleToRawLongBits(value);
	}

	public static long toSortableLong(float value) {
		//see toSortableLong(double)
		if (value == -0.0) {
			value = 0.0f;
		}
		if (value < 0.0) {
			int l = Float.floatToRawIntBits(value);
			l = ~l;
			l |= (1l << 31l);
			return l;
		}
		return Float.floatToRawIntBits(value);
	}

	public static double toDouble(long value) {
		if (value < 0.0) {
			long l = value;
			l = ~l;
			l |= (1l << 63l);
			return Double.longBitsToDouble(l);
		}
		return Double.longBitsToDouble(value);
	}

	public static float toFloat(long value) {
		if (value < 0.0) { // TODO: do we want to cast value to int before comparison?
			int l = (int) value;
			l = ~l;
			l |= (1l << 31l);
			return Float.intBitsToFloat(l);
		}
		return Float.intBitsToFloat((int) value);
	}

	public static long toSortableLong(String s) {
    	// store magic number: 6 chars + (hash >> 16)
		long n = 0;
    	int i = 0;
    	for ( ; i < 6 && i < s.length(); i++ ) {
    		n |= (byte) s.charAt(i);
    		n = n << 8;
    	}
    	//Fill with empty spaces if string is too short
    	for ( ; i < 6; i++) {
    		n = n << 8;
    	}
    	n = n << 8;

    	//add hashcode
    	n |= (0xFFFF & s.hashCode());
		return n;
	}
	
	
	/**
	 * Reverses the value, considering that not all 64bits of the long value are used.
	 * @param l
	 * @param usedBits
	 * @return Reversed value
	 */
	public static long reverse(long l, int usedBits) {
		long r = Long.reverse(l);
		r >>>= (64-usedBits);
		return r;
	}
	
	
	/**
	 * Splits a value and write it to trgV at position trg1 and trg2.
	 * This is the inverse operation to merge(...).
	 * @param toSplit
	 * @param trgV
	 * @param trg1
	 * @param trg2
	 * @param nBits Number of bits of source value
	 */
	public static void split(final long toSplit, long[] trgV, final int trg1, final int trg2, 
			int nBits) {
		long maskSrc = 1L << (nBits-1);
		long t1 = 0;
		long t2 = 0;
		for (int i = 0; i < nBits; i++) {
			if ((i&1) == 0) {
				t1 <<= 1;
				if ((toSplit & maskSrc) != 0L) {
					t1 |= 1L;
				}
			} else {
				t2 <<= 1;
				if ((toSplit & maskSrc) != 0L) {
					t2 |= 1L;
				}
			}
			maskSrc >>>= 1;
		}
		trgV[trg1] = t1;
		trgV[trg2] = t2;
	}

	/**
	 * Merges to long values into a single value by interleaving there respective bits.
	 * This is the inverse operation to split(...).
	 * @param srcV Source array
	 * @param src1 Position of 1st source value
	 * @param src2 Position of 2nd source value
	 * @param nBits Number of bits of RESULT
	 * @return Merged result
	 */
	public static long merge(long[] srcV, final int src1, final int src2, int nBits) {
		long maskTrg = 1L;
		long v = 0;
		long s1 = srcV[src1];
		long s2 = srcV[src2];
		for (int i = nBits-1; i >=0; i--) {
			if ( (i & 1) == 0) {
				if ((s1 & 1L) == 1L) {
					v |= maskTrg;
				}
				s1 >>>= 1;
			} else {
				if ((s2 & 1L) == 1L) {
					v |= maskTrg;
				}
				s2 >>>= 1;
			}
			maskTrg <<= 1;
		}
		return v;
	}

	/**
	 * Merges to long values into a single value by interleaving there respective bits.
	 * This is the inverse operation to split(...).
	 * @param src Source array
	 * @param nBitsPerValue Number of bits of each source value
	 * @return Merged result
	 */
//	public static int[] merge(int nBitsPerValue, long[] src) {
//		//int intArrayLen = (int) Math.ceil(src.length*nBitsPerValue/(double)32.0);
//		int intArrayLen = (src.length*nBitsPerValue+31) >>> 5;
//		int[] trg = new int[intArrayLen];
//		
//		for (int j = 0; j < src.length; j++) {
//			long maskSrc = 1L << (nBitsPerValue-1);
//			for (int k = 0; k < nBitsPerValue; k++) {
//				int posBit = k*src.length + j; 
//				boolean bit = (src[j] & maskSrc) != 0;
//				BitsInt.setBit(trg, posBit, bit);
//				maskSrc >>>= 1;
//			}
//		}
//		
//		return trg;
//	}
	
	/**
	 * Splits a value and write it to trgV at position trg1 and trg2.
	 * This is the inverse operation to merge(...).
	 * @param toSplit
	 * @param nBitsPerValue Number of bits of source value
	 */
//	public static long[] split(final int DIM, final int nBitsPerValue, final int[] toSplit) {
//		long[] trg = new long[DIM];
//
//		long maskTrg = 1L << (nBitsPerValue-1);
//		for (int k = 0; k < nBitsPerValue; k++) {
//			for (int j = 0; j < trg.length; j++) {
//				int posBit = k*trg.length + j; 
//				boolean bit = BitsInt.getBit(toSplit, posBit);
//				if (bit) {
//					trg[j] |= maskTrg;
//				}
//			}
//			maskTrg >>>= 1;
//		}
//		return trg;
//	}

	/**
	 * Merges to long values into a single value by interleaving there respective bits.
	 * This is the inverse operation to split(...).
	 * @param src Source array
	 * @param nBitsPerValue Number of bits of each source value
	 * @return Merged result
	 */
	public static long[] mergeLong(int nBitsPerValue, long[] src) {
		int intArrayLen = (src.length*nBitsPerValue+63) >>> 6;
		long[] trg = new long[intArrayLen];
		
		for (int j = 0; j < src.length; j++) {
			long maskSrc = 1L << (nBitsPerValue-1);
			for (int k = 0; k < nBitsPerValue; k++) {
				int posBit = k*src.length + j; 
				boolean bit = (src[j] & maskSrc) != 0;
				setBit(trg, posBit, bit);
				maskSrc >>>= 1;
			}
		}
		
		return trg;
	}
	
	/**
	 * Splits a value and write it to trgV at position trg1 and trg2.
	 * This is the inverse operation to merge(...).
	 * @param toSplit
	 * @param nBitsPerValue Number of bits of source value
	 */
	public static long[] splitLong(final int DIM, final int nBitsPerValue, final long[] toSplit) {
		long[] trg = new long[DIM];

		long maskTrg = 1L << (nBitsPerValue-1);
		for (int k = 0; k < nBitsPerValue; k++) {
			for (int j = 0; j < trg.length; j++) {
				int posBit = k*trg.length + j; 
				boolean bit = getBit(toSplit, posBit);
				if (bit) {
					trg[j] |= maskTrg;
				}
			}
			maskTrg >>>= 1;
		}
		return trg;
	}

	/**
	 * @Param posBit Counts from left to right!!!
	 */
    public static boolean getBit(long[] ba, int posBit) {
        int pA = posBit >>> 6; // 1/64
        //last 6 bit [0..63]
        posBit &= 0x3F;
        return (ba[pA] & (1L << (64-1-posBit))) != 0;
	}

	/**
	 * @Param posBit Counts from left to right (highest to lowest)!!!
	 */
    public static void setBit(long[] ba, int posBit, boolean b) {
        int pA = posBit >>> 6;  // 1/64
        //last 6 bit [0..63]
        posBit &= 0x3F;
        if (b) {
            ba[pA] |= (1L << (64-1-posBit));
        } else {
            ba[pA] &= (~(1L << (64-1-posBit)));
        }
	}


	public static String toBinary(long[] la, int DEPTH) {
	    StringBuilder sb = new StringBuilder();
	    for (long l: la) {
	    	sb.append(toBinary(l, DEPTH));
	        sb.append(", ");
	    }
	    return sb.toString();
	}

	public static String toBinary(long l, int DEPTH) {
        StringBuilder sb = new StringBuilder();
        //long mask = DEPTH < 64 ? (1<<(DEPTH-1)) : 0x8000000000000000L;
        for (int i = 0; i < DEPTH; i++) {
            long mask = (1l << (long)(DEPTH-i-1));
            if ((l & mask) != 0) { sb.append("1"); } else { sb.append("0"); }
            if ((i+1)%8==0 && (i+1)<DEPTH) sb.append('.');
        	mask >>>= 1;
        }
        return sb.toString();
    }


}
