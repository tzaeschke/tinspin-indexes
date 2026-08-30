/*
 * Copyright 2016-2017 Tilmann Zaeschke
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
package org.tinspin.index;


/**
 * Class to collect statistics for a given tree.
 */
public class Stats {

	/** Dimensions. */
	public int dims;
	/** Number of entries. */
	public int nEntries = 0;
	/** Number of nodes. */
	public int nNodes = 0;
	/** Min level (CoverTree only). */
	public int minLevel = Integer.MAX_VALUE;
	/** Max level. For RTree: max measured level. */
	public int maxLevel = -1;
	/** Dimensions. For RTree: specified depth. */
	public int maxDepth = 0;
	/** Max values in a single node. */
	public int maxValuesInNode = 0;
	/** Sum of levels (CoverTree only). */
	public double sumLevel;
	/** Maximum node size. */
	public int maxNodeSize = -1;
	/** Number of leaf nodes. */
	public int nLeaf;
	/** Number of inner nodes. */
	public int nInner;
	/** Number of performed distance calculations. */
	public long nDistCalc;
	/** Number of performed distance calculations for 1-NN. */
	public long nDistCalc1NN;
	/** Number of performed distance calculations for k-NN.  */
	public long nDistCalcKNN;

	/**
	 * Constructors.
	 * @param nDistCalc number of distance calculations
	 * @param nDistCalc1NN number of distance calculations for 1-NN
	 * @param nDistCalcKNN number of distance calculations for k-NN
	 */
	protected Stats(long nDistCalc, long nDistCalc1NN, long nDistCalcKNN) {
		this.nDistCalc = nDistCalc;
		this.nDistCalc1NN = nDistCalc1NN;
		this.nDistCalcKNN = nDistCalcKNN;
	}
	
	@Override
	public String toString() {
		return 
				"dims=" + dims +
				";nEntries=" + nEntries +
				";nNodes=" + nNodes +
				";nLeaf=" + nLeaf + 
				";nInner=" + nInner +
				";maxDepth=" + maxDepth +
				";maxValues=" + maxValuesInNode +
				";minLevel=" + minLevel +
				";maxLevel=" + maxLevel + 
				";avgLevel=" + (sumLevel/nEntries) +
				";maxNodeSize=" + maxNodeSize;
	}

	/**
	 * Dimensions.
	 * @return dimensions.
	 */
	public int getDims() {
		return dims;
	}

	/**
	 * Max depth.
	 * @return max depth
	 */
	public int getMaxDepth() {
		return maxDepth;
	}

	/**
	 * Number of entries.
	 * @return number of entries
	 */
	public int getEntryCount() {
		return nEntries;
	}

	/**
	 * Number of nodes.
	 * @return number of nodes
	 */
	public int getNodeCount() {
		return nNodes;
	}

	/**
	 * Maximum node size.
	 * @return max node size
	 */
	public int getMaxNodeSize() {
		return maxNodeSize;
	}

	/**
	 * Number of leaf nodes.
	 * @return number of leaf nodes
	 */
	public int getLeafNodeCount() {
		return nLeaf;
	}

	/**
	 * Number of inner nodes.
	 * @return number of inner nodes.
	 */
	public int getInnerNodeCount() {
		return nInner;
	}

	/**
	 * Number of performed distance calculations.
	 * @return number of distance calculations.
	 */
	public long getNDistCalc() {
		return nDistCalc;
	}

	/**
	 * Number of performed distance calculations for 1-NN.
	 * @return number of distance calculations.
	 */
	public long getNDistCalc1NN() {
		return nDistCalc1NN;
	}

	/**
	 * Number of performed distance calculations for k-NN.
	 * @return number of distance calculations.
	 */
	public long getNDistCalcKNN() {
		return nDistCalcKNN;
	}
}
