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
public class TestPointWrappersMM extends AbstractWrapperTest {

    private static final int N_DUPL = 4;
    private static TestStats expectedCube = null;
    private static TestStats expectedCluster = null;
    private final IDX candidate;

    public TestPointWrappersMM(IDX candCls) {
        this.candidate = candCls;
    }

    @BeforeClass
    public static void beforeClass() {
        //init results
        //use this as reference for all others
        //if the naive implementation should be wrong, the others should fail as well
        expectedCube = createUnitTestStatsDupl(IDX.ARRAY, TST.CUBE_P, N, dims, 1.0, N_DUPL);
        new TestRunner(expectedCube).run();

        expectedCluster = createUnitTestStatsDupl(IDX.ARRAY, TST.CLUSTER_P, N, dims, 5.0, N_DUPL);
        new TestRunner(expectedCluster).run();
    }

    @Parameters
    public static Iterable<Object[]> candidates() {
        ArrayList<Object[]> l = new ArrayList<>();
// TODO		l.add(new Object[]{IDX.ARRAY});
// TODO		l.add(new Object[]{IDX.COVER});
        l.add(new Object[]{IDX.KDTREE});
        l.add(new Object[]{IDX.PHTREE_MM});
        l.add(new Object[]{IDX.QUAD_HC});
        l.add(new Object[]{IDX.QUAD_HC2});
        l.add(new Object[]{IDX.QUAD_PLAIN});
        l.add(new Object[]{IDX.RSTAR});
        l.add(new Object[]{IDX.STR});
//		l.add(new Object[]{IDX.CRITBIT});
        return l;
    }

    @Test
    @Parameters
    public void testCube() {
        System.out.println("Testing: " + candidate.name());
        TestStats ts = createUnitTestStatsDupl(candidate, TST.CUBE_P, N, dims, 1.0, N_DUPL);
        TestRunner tr = new TestRunner(ts);
        tr.run();

        check(expectedCube, ts, tr.getCandidate());
    }

    @Test
    @Parameters
    public void testCluster() {
        System.out.println("Testing: " + candidate.name());
        TestStats ts = createUnitTestStatsDupl(candidate, TST.CLUSTER_P, N, dims, 5.0, N_DUPL);
        TestRunner tr = new TestRunner(ts);
        tr.run();

        check(expectedCluster, ts, tr.getCandidate());
    }
}
