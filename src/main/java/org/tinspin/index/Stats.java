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



public class Stats {
	
	public int dims;
	public int nEntries = 0;
	public int nNodes = 0;
	public int minLevel = Integer.MAX_VALUE;
	public int maxLevel = -1;
	public int maxDepth = 0;
	public int maxValuesInNode = 0;
	public double sumLevel;
	public int maxNodeSize = -1;
	public int nLeaf;
	public int nInner;
	public long nDistCalc;
	public long nDistCalc1NN;
	public long nDistCalcKNN;
	
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

	public int getDims() {
		return dims;
	}
	
	public int getMaxDepth() {
		return maxDepth;
	}

	public int getEntryCount() {
		return nEntries;
	}
	
	public int getNodeCount() {
		return nNodes;
	}
	
	public int getMaxNodeSize() {
		return maxNodeSize;
	}
	
	public int getLeafNodeCount() {
		return nLeaf;
	}

	public int getInnerNodeCount() {
		return nInner;
	}

	public long getNDistCalc() {
		return nDistCalc;
	}

	public long getNDistCalc1NN() {
		return nDistCalc1NN;
	}

	public long getNDistCalcKNN() {
		return nDistCalcKNN;
	}

}
