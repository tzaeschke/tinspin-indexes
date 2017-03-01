package org.tinspin.index.rtree;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.tinspin.index.RectangleEntryDist;

public class RTreeMixedQueryTest {

	// seed chosen randomly using a well equilibrated dice :-)
	// [makes test reproducible]
	Random rnd = new Random(4);

	@Test
	public void test() {
		RTree<String> tree = RTree.createRStar(3);

		for (int i = 0; i < 100000; i++) {
			tree.insert(randDouble(3), "#" + i);
		}
		
		Iterable<RectangleEntryDist<String>> q = tree.queryMixed(new double[] { 1, 1, 1 }, DistanceFunction.CENTER_SQUARE, DistanceFunction.EDGE_SQUARE,
				new double[] { 0.5, 0.5, 0.5 }, new double[] { 1, 1, 1 });
		
		
		double d = 0;
		int maxQueueSize = 0;
		int nElements = 0;
		for (Iterator<RectangleEntryDist<String>> iterator = q.iterator(); iterator.hasNext();) {
			RectangleEntryDist<String> e = iterator.next();
				System.out.println(iterator + " " + e);
			assertTrue(d <= e.dist());
			d = e.dist();
			nElements++;
			maxQueueSize = Math.max(maxQueueSize, ((RTreeMixedQuery) iterator).queueSize());
		}

		
		perfTestNN(tree);
		
		// should be about the size of the tree
		System.out.println(nElements * (1 << tree.getDims()));
		System.out.println(nElements);
		System.out.println(maxQueueSize);
	}

	private void perfTestNN(RTree<String> tree) {
		int k = tree.size() / 8;
		double[] center = new double[] { 1, 1, 1 };
		
		{
			Iterable<RectangleEntryDist<String>> q = tree.queryMixed(center, DistanceFunction.EDGE,
					DistanceFunction.EDGE, Filter.ALL);
			RTreeQueryKnn<String> res = tree.queryKNN(center, k, DistanceFunction.EDGE);
			// test equivalence
			Iterator<RectangleEntryDist<String>> iterator = q.iterator();
			int i=0;
			for (; iterator.hasNext();) {
				assertTrue("I="+i, res.hasNext());
				assertEquals(res.next().value(), iterator.next().value());
				i++;
				if (i >= k)
					break;
			}
			assertFalse(res.hasNext());
		}
		
		long timeRef = timeOf(() -> {
			RTreeQueryKnn<String> res = tree.queryKNN(center, k, DistanceFunction.EDGE);
			int cnt = 0;
			for(;res.hasNext();) {
				cnt++;
				DistEntry<String> e = res.next();
				assertNotNull(e);
			}
			System.out.println(cnt);
			assertTrue(cnt<=k);
		});
		
		long timeMixed = timeOf(() -> {
			Iterable<RectangleEntryDist<String>> q = tree.queryMixed(center, DistanceFunction.EDGE,
					DistanceFunction.EDGE, Filter.ALL);
			int cnt = 0;
			for (Iterator<RectangleEntryDist<String>> iterator = q.iterator(); iterator.hasNext();) {
				RectangleEntryDist<String> e = iterator.next();
				assertNotNull(e);
				cnt++;
				if (cnt >= k)
					break;
			}
		});
		
		
		
		System.out.println("timeMixed=" + timeMixed + ", timeRef=" + timeRef);
	}
	
	public long timeOf(Runnable run) {
		final int nRuns = 2;
		long time = 0;
		for (int i = 0; i <= nRuns; i++) {
			long timeBefore = System.currentTimeMillis();
			run.run();
			long delta = System.currentTimeMillis() - timeBefore;
			if (i > 0) {
				// ignore the first one for warm up
				time += delta;
			}
		}
		return time / nRuns;
	}

	private double[] randDouble(int n) {
		double[] r = new double[n];
		for (int i = 0; i < n; i++) {
			r[i] = rnd.nextDouble();
		}
		return r;
	}

}
