/*
 * Copyright 2011 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.ncd.rcp.utils;

import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;

import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.junit.Test;

import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;

public class NcdDataUtilsTest {
	
	String format = "-3,6-33;3-5;1,2-4,5;;,,;7-12";
	int [] frames = new int[] {8,8,6,5,4,10};
	
	int [] axis0 = new int [] {0,1,2,3,6,7};
	int [] axis1 = new int [] {3,4,5};
	int [] axis2 = new int [] {1,2,3,4,5};
	int [] axis3 = new int [] {0,1,2,3,4};
	int [] axis4 = new int [] {0,1,2,3};
	int [] axis5 = new int [] {7,8,9};
	
	int [] shape1 = new int[] {5, 3, 14, 256, 128};
	int [] shape2 = new int[] {3, 16, 256, 128};
	int [] newShape1 = new int[] {5, 14, 3, 256, 128};
	int [] newShape2 = new int[] {16, 3, 256, 128};
	
	Dataset data1, data2;
	
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
	
	@Test
	public void matchDataDimensionsTest() {
		data1 = DatasetFactory.ones(shape1, Dataset.INT32);
		data2 = DatasetFactory.ones(shape2, Dataset.INT32);
		
		Dataset[] newDatasets = NcdDataUtils.matchDataDimensions(data1, data2);
		
		int[] testShape1 = newDatasets[0].getShape();
		int[] testShape2 = newDatasets[1].getShape();
		
		assertArrayEquals("Shape match failed for data1", newShape1, testShape1);
		assertArrayEquals("Shape match failed for data1", newShape2, testShape2);
	}
}
