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
 * 
 * @author Tilmann Zäschke
 */
public class Examples {

	public static void main(String[] args) {
		ex1D_32();
		ex1D_float();
		ex1D_256();
		
		ex4D();
	}
	
	/**
	 * Example of a 1D crit-bit tree with 32 bit integer keys.
	 */
	private static void ex1D_32() {
		log("Testing 1D 32 bit tree");
		log("======================");
		CritBit1D<String> cb = CritBit.create1D(32);
		long[] key = new long[]{1234};
		cb.put(key, "hello 32");
		log("contains() --> " + cb.contains(key));
		log("get() --> "+ cb.get(key));
		log("");
		//TODO iterator
	}

	/**
	 * Example of a 1D crit-bit tree with 64 bit float keys.
	 */
	private static void ex1D_float() {
		log("Testing 1D float tree");
		log("=====================");
		//double-float requires 64 bit
		CritBit1D<String> cb = CritBit.create1D(64);
		long[] key = new long[]{BitTools.toSortableLong(12.34)};
		cb.put(key, "hello float");
		log("contains() --> " + cb.contains(key));
		log("get() --> "+ cb.get(key));
		log("");
		//TODO iterator
	}

	/**
	 * Example of a 1D crit-bit tree with 256 bit integer keys.
	 */
	private static void ex1D_256() {
		log("Testing 1D 256 bit tree");
		log("=======================");
		CritBit1D<String> cb = CritBit.create1D(256);  
		long[] key = new long[]{ 123456789012L, 3456, 4567, 12345678901234L};
		cb.put(key, "hello 256");
		log("contains() --> " + cb.contains(key));
		log("get() --> "+ cb.get(key));
		log("");
		//TODO iterator
	}

	/**
	 * Example of a 4D crit-bit tree with 1 float dimension.
	 */
	private static void ex4D() {
		log("Testing 4D tree");
		log("===============");
		CritBitKD<String> cb = CritBit.createKD(64, 4);  
		long[] key = new long[]{ 
				123456789012L, 
				3456, 
				BitTools.toSortableLong(1234.5678), // double dimension
				12345678901234L};
		cb.putKD(key, "hello 4D");
		log("contains() --> " + cb.containsKD(key));
		log("get() --> "+ cb.getKD(key));
		log("");
		//TODO iterator
	}

	private static void log(String msg) {
		System.out.println(msg);
	}
}
