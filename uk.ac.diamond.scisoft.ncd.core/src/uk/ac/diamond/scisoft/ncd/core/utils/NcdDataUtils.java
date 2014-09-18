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

package uk.ac.diamond.scisoft.ncd.core.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.validator.routines.IntegerValidator;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.IntegerDataset;

/**
 * This class contains utility methods for manipulating data for NCD data reduction
 *
 */
public class NcdDataUtils {
	
	private static IntegerValidator integerValidator = IntegerValidator.getInstance();
	
	/**
	 * Method for generating all combination of elements supplied in the input array
	 * 
	 * @param input
	 * 			Array containing arrays of elements.
	 * 			Resulting combinations will pick single element from every input array. 
	 * @return
	 * 			Array containing all combinations of the input elements with only one element
	 * 			picked from every input array at a time.
	 */
	public static ArrayList<int[]> generateCombinations(final ArrayList<int[]> input) {
		ArrayList<int[]> result = new ArrayList<int[]>(); 
		@SuppressWarnings("unchecked")
		ArrayList<int[]> tmpInput = (ArrayList<int[]>) input.clone(); 
		int[] first = tmpInput.remove(0);
		for (int i = 0; i < first.length; i++) {
			if (!tmpInput.isEmpty()) {
				ArrayList<int[]> recursive = generateCombinations(tmpInput);
				for (int j = 0; j < recursive.size(); j++) {
					ArrayList<Integer> tmp = new ArrayList<Integer>();
					tmp.add(first[i]);
					tmp.addAll(Arrays.asList(ArrayUtils.toObject(recursive.get(j))));
					result.add(ArrayUtils.toPrimitive(tmp.toArray(new Integer[] {})));
				}
			}
			else {
				ArrayList<Integer> tmp = new ArrayList<Integer>();
				tmp.add(first[i]);
				result.add(ArrayUtils.toPrimitive(tmp.toArray(new Integer[] {})));
			}
		}
		return result;
	}

	/**
	 * Method for parsing input string for multidimensional frame selection
	 * 
	 * @param format
	 * 			String containing information on grid slice selection
	 * @param frames
	 * 			Dimensions of the input dataset
	 * @return
	 * 			Array with list of selected grid points in every dimension
	 */
	public static ArrayList<int[]>  createSliceList(String format, int[] frames) {
		String [] dimFormat = format.split(";", -1);
		int dims = dimFormat.length;
		if (dims < frames.length) {
			for (int i = 0; i < frames.length - dims; i++)
				format += ";";
			dimFormat = format.split(";", -1);
			dims = dimFormat.length;
		}
		ArrayList<int[]> tmpList = new ArrayList<int[]>();
		
		// Loop over axes index
		for (int i = 0; i < frames.length; i++) {
			
			ArrayList<Integer> tmpSel = new ArrayList<Integer>();
			String tmpFormat = dimFormat[i];
			
			if (tmpFormat.equals("")) {
				tmpList.add(IntegerDataset.createRange(frames[i]).getData());
				continue;
			}
			
			String[] tmpFormatComma = tmpFormat.split(",");
			
			// Loop over list of indexes on the selected axis
			for (int j = 0; j < tmpFormatComma.length; j++) {
				
				if (tmpFormatComma[j].equals("")) {
					continue;
				}
				
				String[] tmpFormatDash = tmpFormatComma[j].split("-");
				String firstValue = tmpFormatDash[0];
				String lastValue = tmpFormatDash[tmpFormatDash.length-1];
				int sliceStart = 0;
				int sliceEnd  = frames[i];
				
				if (!(firstValue.isEmpty()) && integerValidator.isValid(firstValue))
					sliceStart = Math.max(0, Integer.valueOf(firstValue));
				
				if (!(lastValue.isEmpty()) && integerValidator.isValid(lastValue))
					sliceEnd = Math.min(frames[i],Integer.valueOf(lastValue) + 1);
				
				int[] slice = IntegerDataset.createRange(sliceStart, sliceEnd, 1).getData();
				for (int l = 0; l < slice.length; l++)
					tmpSel.add(slice[l]);
			}
			if (tmpSel.isEmpty())
				tmpList.add(IntegerDataset.createRange(frames[i]).getData());
			else
				tmpList.add(ArrayUtils.toPrimitive(tmpSel.toArray(new Integer[] {})));
		}
		
		return tmpList;
		
	}
	
	/**
	 * Method for parsing input string for grid axes selection
	 * 
	 * @param format
	 * 			String containing information on grid slice selection
	 * @param axes
	 * 			Maximum grid axis index
	 * @return
	 * 			Array with list of selected grid axis
	 */
	public static int[]  createGridAxesList(String format, int axes) {
		
		String[] tmpFormatComma = format.split(",");

		ArrayList<Integer> tmpSel = new ArrayList<Integer>();
		for (int j = 0; j < tmpFormatComma.length; j++) {

			if (tmpFormatComma[j].equals("")) {
				continue;
			}

			String[] tmpFormatDash = tmpFormatComma[j].split("-");
			String firstValue = tmpFormatDash[0];
			String lastValue = tmpFormatDash[tmpFormatDash.length-1];
			int sliceStart = 1;
			int sliceEnd  = axes;
			
			if (!(firstValue.isEmpty()) && integerValidator.isValid(firstValue))
				sliceStart = Math.max(1, Integer.valueOf(firstValue));
			
			if (!(lastValue.isEmpty()) && integerValidator.isValid(lastValue))
				sliceEnd = Math.min(axes,Integer.valueOf(lastValue) + 1);
			
			int[] slice = IntegerDataset.createRange(sliceStart, sliceEnd, 1).getData();
			for (int l = 0; l < slice.length; l++)
				tmpSel.add(slice[l]);
		}
		if (tmpSel.isEmpty())
			return IntegerDataset.createRange(1, axes, 1).getData();

		return ArrayUtils.toPrimitive(tmpSel.toArray(new Integer[] {}));
	}
	
	public static Dataset[] matchDataDimensions(Dataset data, Dataset bgData) {
		int bgRank = bgData.getRank();
		int[] bgShape = bgData.getShape();
		int rank = data.getRank();
		int[] shape = data.getShape();
		
		ArrayList<Integer> matchBgDims = new ArrayList<Integer>();
		ArrayList<Integer> nomatchBgDims = new ArrayList<Integer>();
		for (int i = 0; i < bgRank; i++) {
			nomatchBgDims.add(i);
		}
		ArrayList<Integer> matchDataDims = new ArrayList<Integer>();
		ArrayList<Integer> nomatchDataDims = new ArrayList<Integer>();
		for (int i = 0; i < rank; i++) {
			nomatchDataDims.add(i);
		}
		
		for (int i = 0; i < Math.min(bgRank, rank); i++) {
			if (bgShape[bgRank - i - 1] == shape[rank - i - 1]) {
				matchDataDims.add(new Integer(rank - i - 1));
				matchBgDims.add(new Integer(bgRank - i - 1));
				nomatchDataDims.remove(new Integer(rank - i - 1));
				nomatchBgDims.remove(new Integer(bgRank - i - 1));
			}
		}
		
		Collections.reverse(matchDataDims);
		nomatchDataDims.addAll(matchDataDims);
		data = DatasetUtils.transpose(data, ArrayUtils.toPrimitive(nomatchDataDims.toArray(new Integer[] {})));
		
		Collections.reverse(matchBgDims);
		nomatchBgDims.addAll(matchBgDims);
		bgData = DatasetUtils.transpose(bgData, ArrayUtils.toPrimitive(nomatchBgDims.toArray(new Integer[] {})));
		
		// Calculate permutations to restore original data shapes after processing
		IntegerDataset revPermData = new IntegerDataset(nomatchDataDims.size());
		for(int i = 0; i < nomatchDataDims.size(); i++) {
			revPermData.set(nomatchDataDims.indexOf(i), i);
		}
		IntegerDataset revPermBg = new IntegerDataset(nomatchBgDims.size());
		for(int i = 0; i < nomatchBgDims.size(); i++) {
			revPermBg.set(nomatchBgDims.indexOf(i), i);
		}
		return new Dataset[] {data, bgData, revPermData, revPermBg}; 
	}
	
	public static Dataset flattenGridData(Dataset data, int dimension) {
		
		int dataRank = data.getRank();
		int[] dataShape = data.getShape();
		if (dataRank > (dimension + 1)) {
			int[] frameArray = Arrays.copyOf(dataShape, dataRank - dimension);
			int totalFrames = 1;
			for (int val : frameArray) {
				totalFrames *= val;
			}
			int[] newShape = Arrays.copyOfRange(dataShape, dataRank - dimension - 1, dataRank);
			newShape[0] = totalFrames;
			return data.reshape(newShape);
		}
		return data;
	}
}
