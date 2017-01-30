/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test;

import java.util.ArrayList;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.tinspin.index.test.util.TestRunner;
import org.tinspin.index.test.util.TestStats;
import org.tinspin.index.test.util.TestStats.INDEX;
import org.tinspin.index.test.util.TestStats.TST;

@RunWith(Parameterized.class)
public class TestRectangleWrappers extends AbstractWrapperTest {

	private final INDEX candidate;
	
	private static TestStats expectedCube = null;
	private static TestStats expectedCluster = null;

	public TestRectangleWrappers(INDEX candCls) {
		this.candidate = candCls;
	}
	
	@BeforeClass
	public static void beforeClass() {
		//init results
		//use this as reference for all others
		//if the naive implementation should be wrong, the others should fail as well
		expectedCube = createUnitTestStats(INDEX.ARRAY, TST.CUBE, N, dims, true, 1.0);
		new TestRunner(expectedCube).run();

		expectedCluster = createUnitTestStats(INDEX.ARRAY, TST.CLUSTER, N, dims, true, 5.0);
		new TestRunner(expectedCluster).run();
	}
	
	@Parameters
	public static Iterable<Object[]> data1() {
		ArrayList<Object[]> l = new ArrayList<>();
		l.add(new Object[]{INDEX.ARRAY});
		l.add(new Object[]{INDEX.QUAD_OLD});
		l.add(new Object[]{INDEX.QUAD});
		l.add(new Object[]{INDEX.RSTAR});
		l.add(new Object[]{INDEX.STR});
		return l;
	}

	@Test
	@Parameters
    public void testCube() {
		//0.00001 is the default size of the rectangles
		TestStats ts = createUnitTestStats(candidate, TST.CUBE, N, dims, true, 1.0);
		TestRunner tr = new TestRunner(ts);
		tr.run();
		
		check(expectedCube, ts, tr.getCandidate());
	}
	
	@Test
	@Parameters
    public void testCluster() {
		TestStats ts = createUnitTestStats(candidate, TST.CLUSTER, N, dims, true, 5.0);
		TestRunner tr = new TestRunner(ts);
		tr.run();

		check(expectedCluster, ts, tr.getCandidate());
	}
	
}
