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
package org.tinspin.util;

import static org.junit.Assert.*;
import org.junit.Test;
import org.tinspin.index.util.MathTools;

import java.util.Arrays;

public class MathToolsTest {

    @Test
    public void powerOfTwoCeil() {
        assertEquals(1./32., MathTools.ceilPowerOfTwo(0.03), 0.0);
        assertEquals(0.5, MathTools.ceilPowerOfTwo(0.3), 0.0);
        assertEquals(4, MathTools.ceilPowerOfTwo(3), 0.0);
        assertEquals(32, MathTools.ceilPowerOfTwo(30), 0.0);
        assertEquals(512, MathTools.ceilPowerOfTwo(300), 0.0);

        assertEquals(-0.5, MathTools.ceilPowerOfTwo(-0.3), 0.0);
        assertEquals(-4, MathTools.ceilPowerOfTwo(-3), 0.0);
        assertEquals(-32, MathTools.ceilPowerOfTwo(-30), 0.0);

        // identity
        assertEquals(0, MathTools.ceilPowerOfTwo(0), 0.0);
        assertEquals(-0.5, MathTools.ceilPowerOfTwo(-0.5), 0.0);
        assertEquals(0.5, MathTools.ceilPowerOfTwo(0.5), 0.0);
        assertEquals(-1, MathTools.ceilPowerOfTwo(-1), 0.0);
        assertEquals(1, MathTools.ceilPowerOfTwo(1), 0.0);
        assertEquals(-2, MathTools.ceilPowerOfTwo(-2), 0.0);
        assertEquals(2, MathTools.ceilPowerOfTwo(2), 0.0);
    }

    @Test
    public void powerOfTwoFloor() {
        assertEquals(1./64., MathTools.floorPowerOfTwo(0.03), 0.0);
        assertEquals(0.25, MathTools.floorPowerOfTwo(0.3), 0.0);
        assertEquals(2, MathTools.floorPowerOfTwo(3), 0.0);
        assertEquals(16, MathTools.floorPowerOfTwo(30), 0.0);
        assertEquals(256, MathTools.floorPowerOfTwo(300), 0.0);

        assertEquals(-0.25, MathTools.floorPowerOfTwo(-0.3), 0.0);
        assertEquals(-2, MathTools.floorPowerOfTwo(-3), 0.0);
        assertEquals(-16, MathTools.floorPowerOfTwo(-30), 0.0);

        // identity
        assertEquals(0, MathTools.ceilPowerOfTwo(0), 0.0);
        assertEquals(-0.5, MathTools.ceilPowerOfTwo(-0.5), 0.0);
        assertEquals(0.5, MathTools.ceilPowerOfTwo(0.5), 0.0);
        assertEquals(-1, MathTools.ceilPowerOfTwo(-1), 0.0);
        assertEquals(1, MathTools.ceilPowerOfTwo(1), 0.0);
        assertEquals(-2, MathTools.ceilPowerOfTwo(-2), 0.0);
        assertEquals(2, MathTools.ceilPowerOfTwo(2), 0.0);
    }

    @Test
    public void powerOfTwoFloor_vector() {
        double[] d = {0.03, 0.3, 3, 30, 300};
        double[] dCopy = MathTools.floorPowerOfTwoCopy(d);
        assertFalse(Arrays.equals(d, dCopy));
        assertEquals(1./64., dCopy[0], 0.0);
        assertEquals(0.25, dCopy[1], 0.0);
        assertEquals(2, dCopy[2], 0.0);
        assertEquals(16, dCopy[3], 0.0);
        assertEquals(256, dCopy[4], 0.0);
    }

    @Test
    public void maxDelta() {
        assertEquals(12, MathTools.maxDelta(new double[]{-4.}, new double[]{8.}), 0.0);
        assertEquals(12, MathTools.maxDelta(new double[]{8.}, new double[]{-4.}), 0.0);

        assertEquals(4, MathTools.maxDelta(new double[]{2, 4, 2}, new double[]{3, 8, 4}), 0.0);
    }
}
