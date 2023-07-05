/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.tinspin.index.test.util.TestRunner;
import org.tinspin.index.test.util.TestStats;
import org.tinspin.index.test.util.TestStats.INDEX;
import org.tinspin.index.test.util.TestStats.TST;

import java.util.ArrayList;

@RunWith(Parameterized.class)
public class TestPointWrappersMM extends AbstractWrapperTest {

	private static final int N_DUPL = 4;
	private final INDEX candidate;

	private static TestStats expectedCube = null;
	private static TestStats expectedCluster = null;

	public TestPointWrappersMM(INDEX candCls) {
		this.candidate = candCls;
	}

	@BeforeClass
	public static void beforeClass() {
		//init results
		//use this as reference for all others
		//if the naive implementation should be wrong, the others should fail as well
		expectedCube = createUnitTestStatsDupl(INDEX.ARRAY, TST.CUBE, N, dims, false, 1.0, N_DUPL);
		new TestRunner(expectedCube).run();

		expectedCluster = createUnitTestStatsDupl(INDEX.ARRAY, TST.CLUSTER, N, dims, false, 5.0, N_DUPL);
		new TestRunner(expectedCluster).run();
	}
	
	@Parameters
	public static Iterable<Object[]> candidates() {
		ArrayList<Object[]> l = new ArrayList<>();
		l.add(new Object[]{INDEX.ARRAY});
// TODO		l.add(new Object[]{INDEX.COVER});
		l.add(new Object[]{INDEX.KDTREE});
		l.add(new Object[]{INDEX.PHTREE});
		l.add(new Object[]{INDEX.QUAD});
		l.add(new Object[]{INDEX.QUAD2});
		l.add(new Object[]{INDEX.QUAD_OLD});
		l.add(new Object[]{INDEX.RSTAR});
		l.add(new Object[]{INDEX.STR});
//		l.add(new Object[]{INDEX.CRITBIT});
		return l;
	}

	@Test
	@Parameters
    public void testCube() {
		System.out.println("Testing: " + candidate.name());
		TestStats ts = createUnitTestStatsDupl(candidate, TST.CUBE, N, dims, false, 1.0, N_DUPL);
		TestRunner tr = new TestRunner(ts);
		tr.run();

		check(expectedCube, ts, tr.getCandidate());
	}
	
	@Test
	@Parameters
    public void testCluster() {
		System.out.println("Testing: " + candidate.name());
		TestStats ts = createUnitTestStatsDupl(candidate, TST.CLUSTER, N, dims, false, 5.0, N_DUPL);
		TestRunner tr = new TestRunner(ts);
		tr.run();

		check(expectedCluster, ts, tr.getCandidate());
	}
}
