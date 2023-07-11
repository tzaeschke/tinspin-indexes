/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import ch.ethz.globis.tinspin.TestStats;
import ch.ethz.globis.tinspin.wrappers.Candidate;
import org.tinspin.index.test.util.TestInstances.IDX;
import org.tinspin.index.test.util.TestInstances.TST;

public abstract class AbstractWrapperTest {

	protected static final int N = 10*1000;
	protected static final int dims = 3;

	public static TestStats createUnitTestStats(
			IDX idx, TST tst, int N, int dims, double param1) {
		TestStats ts = new TestStats(tst, idx, N, dims, param1);
		ts.paramEnforceGC = false;
		ts.cfgWindowQueryRepeat = 100;
		ts.cfgPointQueryRepeat = 1000;
		ts.cfgUpdateSize = 1000;
		ts.minimumMsPerTest = 0;
		return ts;
	}

	public static TestStats createUnitTestStatsDupl(
			IDX idx, TST tst, int N, int dims, double param1, int duplicates) {
		TestStats ts = createUnitTestStats(idx, tst, N, dims, param1);
		ts.cfgDuplicates = duplicates;
		ts.isMultimap = true;
		return ts;
	}

	public static void check(TestStats expected, TestStats ts, Candidate c) {
		
		double EPS = 0.000000001;

		assertEquals(expected.cfgNDims, ts.cfgNDims);
		assertEquals(expected.cfgNEntries, ts.cfgNEntries);
		if (c.supportsWindowQuery()) {
			assertEquals(expected.statNq1, ts.statNq1);
			assertEquals(expected.statNq2, ts.statNq2);
		}
		if (c.supportsPointQuery()) {
			assertEquals(expected.statNqp1, ts.statNqp1);
			assertEquals(expected.statNqp2, ts.statNqp2);
		}
		if (c.supportsUpdate()) {
			assertEquals(expected.statNu1, ts.statNu1);
			assertEquals(expected.statNu2, ts.statNu1);
		}
		if (c.supportsKNN()) {
			assertEquals(expected.statDqk1_1, ts.statDqk1_1, EPS);
			assertEquals(expected.statDqk1_2, ts.statDqk1_2, EPS);
			assertEquals(expected.statDqk10_1, ts.statDqk10_1, EPS);
			assertEquals(expected.statDqk10_2, ts.statDqk10_2, EPS);
		}
		assertEquals(0, c.size());
		assertNotNull(c.toString());
	}
	
}
