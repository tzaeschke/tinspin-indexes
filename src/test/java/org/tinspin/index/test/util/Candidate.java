/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import java.util.List;
import java.util.Random;

import org.tinspin.index.critbit.BitTools;

public abstract class Candidate {
	
	/**
	 * This method is invoked at the end of the test run
	 * to allow freeing of allocated resources
	 */
	public abstract void release();
	
	public abstract void load(double[] data, int idxDim);

	/**
	 * This method is called before the actual point
	 * query, providing the underlying tree with the
	 * possibility to pre-process the query data
	 * @param q raw query data
	 * @return pre-processed query data
	 */
	public abstract Object preparePointQuery(double[][] q);

	/**
	 * Called when the tree should execute the point
	 * queries defined by the arguments.
	 * @param qA pre-processed query data
	 * 
	 * @return number of point queries that matched a point
	 */
	public abstract int pointQuery(Object qA);

	/**
	 * Called when the tree should execute the 
	 * k-nearest-neighbor queries defined by the arguments.
	 * @param k number of desired neighbors
	 * @param center of query
	 * 
	 * @return the squared distance to the returned points
	 */
	public double knnQuery(int k, double [] center) {
		if (k < 1 || center == null) {
			throw new IllegalArgumentException();
		}
		return -1;
	}

	/**
	 * Called when the tree should delete all the points
	 * it contains. The deletion should happen in N/2 steps
	 * where the first and last element (in order of insertion)
	 * that are still in the tree should be deleted
	 * 
	 * @return number of effectively deleted points
	 */
	public abstract int unload();

	/**
	 * Called when tree should execute a range query
	 * 
	 * @param min lower corner
	 * @param max upper corner
	 * @return number of matched points
	 */
	public abstract int query(double[] min, double[] max);
	
	
	public abstract int update(double[][] updateTable);

	public List<?> queryToList(double[] min, double[] max) {
		if (min == null || max == null) {
			throw new IllegalArgumentException();
		}
		throw new UnsupportedOperationException();
	}
	
	

	public void getStats(TestStats S) {
		//Nothing to do for trees other than PhTrees
		assert(S != null);
	}

	public boolean supportsWindowQuery() {
		return true;
	}
	
	public boolean supportsPointQuery() {
		return true;
	}

	public boolean supportsKNN() {
		return false;
	}
	
	public boolean supportsUpdate() {
		return true;
	}

	public boolean supportsUnload() {
		return true;
	}


	
	@Override
	public String toString() {
		return "Please provide configuration information for "
				+ " every test candidate class in the toString() method.";
	}

	public abstract String toStringTree();

	public abstract void clear();

	public abstract int size();

}
