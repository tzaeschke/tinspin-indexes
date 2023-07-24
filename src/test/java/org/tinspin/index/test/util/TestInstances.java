/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

import ch.ethz.globis.tinspin.IndexHandle;
import ch.ethz.globis.tinspin.TestHandle;
import ch.ethz.globis.tinspin.data.AbstractTest;
import org.tinspin.index.array.PointArray;
import org.tinspin.index.array.RectArray;
import org.tinspin.index.covertree.CoverTree;
import org.tinspin.index.kdtree.KDTree;
import org.tinspin.index.phtree.PHTreeMMP;
import org.tinspin.index.phtree.PHTreeP;
import org.tinspin.index.phtree.PHTreeR;
import org.tinspin.index.qthypercube.QuadTreeKD;
import org.tinspin.index.qthypercube.QuadTreeRKD;
import org.tinspin.index.qthypercube2.QuadTreeKD2;
import org.tinspin.index.qtplain.QuadTreeKD0;
import org.tinspin.index.rtree.RTree;

public class TestInstances {

	/**
	 * Enum with shortcuts to the candidate test classes.
	 * 
	 * The class names can be overridden in the TestStats class.
	 */
	public enum IDX implements IndexHandle {
		//Our implementations
		//===================
		/** Naive array implementation, for verification only */
		ARRAY(PointArray.class.getName(), RectArray.class.getName()),
		/** PH-Tree */
		PHTREE(PHTreeP.class.getName(), PHTreeR.class.getName()),
		/** PH-Tree multimap based on PhTreeMultiMapF2 */
		PHTREE_MM(PHTreeMMP.class.getName(), ""),
		/** CoverTree */
		COVER(CoverTree.class.getName(), ""),
		/** KD-Tree */
		KDTREE(KDTree.class.getName(), ""),
		/** Quadtree with HC navigation */
		QUAD_HC(QuadTreeKD.class.getName(), QuadTreeRKD.class.getName()),
		/** Quadtree with HC navigation v2 */
		QUAD_HC2(QuadTreeKD2.class.getName(), ""),
		/** Plain Quadtree */
		QUAD_PLAIN(QuadTreeKD0.class.getName(), QuadTreeRKD.class.getName()),
		/** RStarTree */
		RSTAR(RTree.class.getName(), RTree.class.getName()),
		/** STR-loaded RStarTree */
		STR(RTree.class.getName(), RTree.class.getName()),

		//Other
		//=====
		CUSTOM1(null, null),
		CUSTOM2(null, null),
		CUSTOM3(null, null),
		USE_PARAM_CLASS(null, null);

		private final String candidateClassNamePoint;
		private final String candidateClassNameBox;

		IDX(String candidateClassNamePoint, 
				String candidateClassNameBox) {
			this.candidateClassNamePoint = candidateClassNamePoint;
			this.candidateClassNameBox = candidateClassNameBox;
		}

		@Override
		public String getCandidateClassNamePoint() {
			return candidateClassNamePoint;
		}

		@Override
		public String getCandidateClassNameRectangle() {
			return candidateClassNameBox;
		}
	}

	public enum TST implements TestHandle {
		CUBE_P(		TestPointCube.class, 	false),
		CLUSTER_P(	TestPointCluster.class, false),

		CUBE_R(		TestBoxCube.class, true),
		CLUSTER_R(	TestBoxCluster.class, true),
		CUSTOM( 	"", false);

		private final String className;
		private boolean isRangeData;
		
		TST(Class<? extends AbstractTest> cls, boolean isRangeData) {
			this.className = cls.getName();
			this.isRangeData = isRangeData;
		}
		
		TST(String className, boolean isRangeData) {
			this.className = className;
			this.isRangeData = isRangeData;
		}
		
		@Override
		public String getTestClassName() {
			return className;
		}
		
		@Override
		public boolean isRangeData() {
			return isRangeData;
		}
	}

}
