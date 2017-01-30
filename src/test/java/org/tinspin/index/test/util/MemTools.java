/*
 * Copyright 2011-2017 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package org.tinspin.index.test.util;

public class MemTools {
	
	public static long getMemUsed() {
		long tot1 = Runtime.getRuntime().totalMemory();
		long free1 = Runtime.getRuntime().freeMemory();
		long used1 = tot1 - free1;
		return used1;
	}
	
	public static long printMemUsed(String txt, long prev, int n) {
		long current = getMemUsed();
		System.out.println(txt + ": " + (current-prev) + "   per item: " + 
				((n==0) ? "NaN" : (current-prev)/n));
		return current-prev;
	}
	
	public static long cleanMem(int N, long prevMemUsed) {
		long ret = 0;
        for (int i = 0; i < 5 ; i++) {
	        ret = MemTools.printMemUsed("MemTree", prevMemUsed, N);
        	System.gc();
        	try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }
        return ret;
	}
}
