/*-
 * Copyright © 2011 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd.rcp.utils;

import java.util.ArrayList;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

public class NcdDataUtilsTest {
	
	String format = "-3,6-33;3-5;1,2-4,5;;,,;7-12";
	int [] frames = new int[] {8,8,6,5,4,10};
	
	int [] axis0 = new int [] {0,1,2,3,6,7};
	int [] axis1 = new int [] {3,4,5};
	int [] axis2 = new int [] {1,2,3,4,5};
	int [] axis3 = new int [] {0,1,2,3,4};
	int [] axis4 = new int [] {0,1,2,3};
	int [] axis5 = new int [] {7,8,9};
	
	@Test
	public void createSliceListTest() {
		ArrayList<int[]> list = NcdDataUtils.createSliceList(format, frames);
		
		assertArrayEquals("check array at index 0", axis0, list.get(0));
		assertArrayEquals("check array at index 1", axis1, list.get(1));
		assertArrayEquals("check array at index 2", axis2, list.get(2));
		assertArrayEquals("check array at index 3", axis3, list.get(3));
		assertArrayEquals("check array at index 4", axis4, list.get(4));
		assertArrayEquals("check array at index 5", axis5, list.get(5));
	}
	
	@Test
	public void generatecombinationsTest() {
		ArrayList<int[]> list = NcdDataUtils.createSliceList(format, frames);
		ArrayList<int[]> comb = NcdDataUtils.generateCombinations(list);
		
		int idxList = 0;
		for (int idx0 = 0; idx0 < axis0.length; idx0++) 
			for (int idx1 = 0; idx1 < axis1.length; idx1++) 
				for (int idx2 = 0; idx2 < axis2.length; idx2++) 
					for (int idx3 = 0; idx3 < axis3.length; idx3++) 
						for (int idx4 = 0; idx4 < axis4.length; idx4++) 
							for (int idx5 = 0; idx5 < axis5.length; idx5++) {
								int[] tmpVal = comb.get(idxList);
								int[] expected = new int[] {axis0[idx0], axis1[idx1], axis2[idx2], axis3[idx3], axis4[idx4], axis5[idx5]};
								assertArrayEquals(String.format("At index %d", idxList), expected, tmpVal);
								idxList++;
							}
	}
}
