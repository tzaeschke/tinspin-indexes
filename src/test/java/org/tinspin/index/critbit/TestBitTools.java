/*
 * Copyright 2009-2017 Tilmann Zaeschke. All rights reserved.
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
package org.tinspin.index.critbit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Random;

import org.junit.Ignore;
import org.junit.Test;

public class TestBitTools {

	private static final int D = 10;
	
	@Test
	public void testFloat() {
		Random R = new Random(0);
		for (int i = 0; i < 100; i++) {
			float f = R.nextFloat();
			assertEquals(f, BitTools.toFloat(BitTools.toSortableLong(f)), 0);
		}

		for (int i = 0; i < 100; i++) {
			long l = R.nextInt();
			assertEquals(l, BitTools.toSortableLong(BitTools.toFloat(l)));
		}
	}
	
	@Test
	public void testDouble() {
		Random R = new Random(0);
		for (int i = 0; i < 100; i++) {
			double f = R.nextDouble();
			assertEquals(f, BitTools.toDouble(BitTools.toSortableLong(f)), 0);
		}

		for (int i = 0; i < 100; i++) {
			long l = R.nextLong();
			assertEquals(l, BitTools.toSortableLong(BitTools.toDouble(l)));
		}
	}
	
	@Test
	public void testFloatArray() {
		Random R = new Random(0);
		for (int i = 0; i < 100; i++) {
			float[] f = new float[D];
			for (int j = 0; j < f.length; j++) {
				f[j] = R.nextFloat();
			}
			long[] l = new long[D];
			float[] f2 = new float[D];
			BitTools.toSortableLong(f, l);
			BitTools.toFloat(l, f2);
			assertArrayEquals(f,  f2, 0);
		}

		for (int i = 0; i < 100; i++) {
			long[] l = new long[D];
			Arrays.setAll(l, (x) -> R.nextInt());
			float[] f = new float[D];
			long[] l2 = new long[D];
			BitTools.toFloat(l, f);
			BitTools.toSortableLong(f, l2);
			assertArrayEquals(l,  l2);
		}
	}
	
	@Test
	public void testDoubleArray() {
		Random R = new Random(0);
		for (int i = 0; i < 100; i++) {
			double[] f = new double[D];
			Arrays.setAll(f, (x) -> R.nextDouble());
			long[] l = new long[D];
			double[] f2 = new double[D];
			BitTools.toSortableLong(f, l);
			BitTools.toDouble(l, f2);
			assertArrayEquals(f,  f2, 0);
		}

		for (int i = 0; i < 100; i++) {
			long[] l = new long[D];
			Arrays.setAll(l, (x) -> R.nextInt());
			double[] f = new double[D];
			long[] l2 = new long[D];
			BitTools.toDouble(l, f);
			BitTools.toSortableLong(f, l2);
			assertArrayEquals(l,  l2);
		}
	}

	
	@Test
	@Ignore
	public void testMergeLong() {
		Random R = new Random(0);
		for (int i = 1; i < 60; i++) {
			long[] src = new long[D];
			Arrays.setAll(src, (x) -> R.nextLong());
			long[] dst = BitTools.mergeLong(i, src);
			long[] src2 = BitTools.splitLong(D, i, dst);
			assertArrayEquals(src, src2);
		}

	}

}
