/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test;

import ch.ethz.globis.tinspin.TestStats;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.tinspin.index.test.util.TestInstances.IDX;
import org.tinspin.index.test.util.TestInstances.TST;
import org.tinspin.index.test.util.TestRunner;

import java.util.ArrayList;

@RunWith(Parameterized.class)
public class TestBoxWrappers extends AbstractWrapperTest {

    private static TestStats expectedCube = null;
    private static TestStats expectedCluster = null;
    private final IDX candidate;

    public TestBoxWrappers(IDX candCls) {
        this.candidate = candCls;
    }

    @BeforeClass
    public static void beforeClass() {
        TestRunner.PRINT = false;
        // init results
        // use this as reference for all others
        // if the naive implementation should be wrong, the others should fail as well
        expectedCube = createUnitTestStats(IDX.ARRAY, TST.CUBE_R, N, dims, 1.0);
        new TestRunner(expectedCube).run();

        expectedCluster = createUnitTestStats(IDX.ARRAY, TST.CLUSTER_R, N, dims, 5.0);
        new TestRunner(expectedCluster).run();
    }

    @Parameters
    public static Iterable<Object[]> data1() {
        ArrayList<Object[]> l = new ArrayList<>();
        l.add(new Object[]{IDX.ARRAY});
        l.add(new Object[]{IDX.PHTREE});
        l.add(new Object[]{IDX.QUAD_PLAIN});
        l.add(new Object[]{IDX.QUAD_HC});
        l.add(new Object[]{IDX.RSTAR});
        l.add(new Object[]{IDX.STR});
        return l;
    }

    @Test
    @Parameters
    public void testCube() {
        //0.00001 is the default size of the rectangles
        TestStats ts = createUnitTestStats(candidate, TST.CUBE_R, N, dims, 1.0);
        TestRunner tr = new TestRunner(ts);
        tr.run();

        check(expectedCube, ts, tr.getCandidate());
    }

    @Test
    @Parameters
    public void testCluster() {
        TestStats ts = createUnitTestStats(candidate, TST.CLUSTER_R, N, dims, 5.0);
        TestRunner tr = new TestRunner(ts);
        tr.run();

        check(expectedCluster, ts, tr.getCandidate());
    }
}
