/*
 * Copyright 2016-2024 Tilmann Zaeschke
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
package org.tinspin.index.util;

public class MathTools {

    private MathTools() {}

    /**
     * Similar to Math.ceil() with the ceiling being the next higher power of 2.
     * The resulting number can repeatedly and (almost) always be divided by two without loss of precision.
     * @param d input
     * @return next power of two above or equal to 'input'
     */
    public static double ceilPowerOfTwo(double d) {
        double ceil = floorPowerOfTwo(d);
        return ceil == d ? ceil : ceil * 2;
    }

    /**
     * Similar to Math.floor() with the floor being the next lower power of 2.
     * The resulting number can repeatedly and (almost) always be divided by two without loss of precision.
     * We calculate the "floor" by setting the "fraction" of the bit representation to 0.
     * @param d input
     * @return next power of two below or equal to 'input'
     */
    public static double floorPowerOfTwo(double d) {
        // Set fraction to "0".
        return Double.longBitsToDouble(Double.doubleToRawLongBits(d) & 0xFFF0_0000_0000_0000L);
    }

    /**
     * Calculates the {@link #floorPowerOfTwo(double)} of an array.
     * @param d input vector
     * @return copied vector with next lower power of two below 'input'
     * @see #floorPowerOfTwo(double)
     */
    public static double[] floorPowerOfTwoCopy(double[] d) {
        double[] d2 = new double[d.length];
        for (int i = 0; i < d.length; i++) {
            d2[i] = floorPowerOfTwo(d[i]);
        }
        return d2;
    }

    /**
     * Returns the maximal delta between any pair of scalars in the vector.
     * @param v1 vector 1
     * @param v2 vector 2
     * @return maximal delta (positive or zero).
     */
    public static double maxDelta(double[] v1, double[] v2) {
        double dMax = 0;
        for (int i = 0; i < v1.length; i++) {
            dMax = Math.max(dMax, Math.abs(v1[i] - v2[i]));
        }
        return dMax;
    }
}
