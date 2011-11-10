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

import gda.data.nexus.tree.NexusTreeNodeSelection;
import gda.device.detector.NXDetectorData;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.commands.ExecutionException;
import org.xml.sax.InputSource;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IntegerDataset;
import uk.ac.diamond.scisoft.analysis.dataset.LazyDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;

/**
 * This class contains utility methods for manipulating data for NCD data reduction
 *
 */
public class NcdDataUtils {
	
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
				tmpList.add(IntegerDataset.arange(frames[i]).getData());
				continue;
			}
			
			String[] tmpFormatComma = tmpFormat.split(",");
			
			// Loop over list of indexes on the selected axis
			for (int j = 0; j < tmpFormatComma.length; j++) {
				
				if (tmpFormatComma[j].equals("")) {
					continue;
				}
				
				String[] tmpFormatDash = tmpFormatComma[j].split("-");
				int sliceStart = 0;
				int sliceEnd  = frames[i];
				if (!(tmpFormatDash[0].isEmpty()))
					sliceStart = Math.max(0, Integer.valueOf(tmpFormatDash[0]));
				if (!(tmpFormatDash[tmpFormatDash.length-1].isEmpty()))
					sliceEnd = Math.min(frames[i],Integer.valueOf(tmpFormatDash[tmpFormatDash.length-1]) + 1);
				int[] slice = IntegerDataset.arange(sliceStart, sliceEnd, 1).getData();
				for (int l = 0; l < slice.length; l++)
					tmpSel.add(slice[l]);
			}
			if (tmpSel.isEmpty())
				tmpList.add(IntegerDataset.arange(frames[i]).getData());
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
			int sliceStart = 1;
			int sliceEnd  = axes;
			if (!(tmpFormatDash[0].isEmpty()))
				sliceStart = Math.max(1, Integer.valueOf(tmpFormatDash[0]));
			if (!(tmpFormatDash[tmpFormatDash.length-1].isEmpty()))
				sliceEnd = Math.min(axes,Integer.valueOf(tmpFormatDash[tmpFormatDash.length-1]) + 1);
			int[] slice = IntegerDataset.arange(sliceStart, sliceEnd, 1).getData();
			for (int l = 0; l < slice.length; l++)
				tmpSel.add(slice[l]);
		}
		if (tmpSel.isEmpty())
			return IntegerDataset.arange(1, axes, 1).getData();

		return ArrayUtils.toPrimitive(tmpSel.toArray(new Integer[] {}));
	}
	
	public static NexusTreeNodeSelection getDetectorSelection(String detName, String calibrationName) throws Exception {
		String calib = "";
		if (calibrationName != null) {
			calib = "<nexusTreeNodeSelection><nxClass>NXdetector</nxClass><name>"+ calibrationName + "</name><wanted>2</wanted><dataType>2</dataType>" +
			"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>data</name><wanted>2</wanted><dataType>1</dataType>" +
			"</nexusTreeNodeSelection>" +
			"</nexusTreeNodeSelection>";
		}
		String xml = "<?xml version='1.0' encoding='UTF-8'?>" +
		"<nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>NXentry</nxClass><wanted>1</wanted><dataType>2</dataType>" +
		"<nexusTreeNodeSelection><nxClass>NXinstrument</nxClass><wanted>1</wanted><dataType>2</dataType>" +
		"<nexusTreeNodeSelection><nxClass>NXdetector</nxClass><name>"+ detName + "</name><wanted>2</wanted><dataType>2</dataType>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>data</name><wanted>2</wanted><dataType>1</dataType>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" + calib +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>";
		return NexusTreeNodeSelection.createFromXML(new InputSource(new StringReader(xml)));
	}

	private static void checkDataShape(int[] shape, int axis) throws ExecutionException {
		if (shape.length < axis) {
			throw new ExecutionException("SCISOFT NCD: Wrong dimensionality of the input data");
		}
	}

	public static NXDetectorData selectNAxisFrames(String det, String calibration, NXDetectorData tmpNXdata, int order, int[] start, int[] stop) throws ExecutionException {
		
		NXDetectorData tmpData = new NXDetectorData();
		
		try {
			int[] dims = tmpNXdata.getDetTree(det).getNode("data").getData().dimensions;
			checkDataShape(dims, order);
	
			int[] step =  new int[dims.length];
			Arrays.fill(step, 1);
			
			ILazyDataset tmpLazyDataset = Nexus.createLazyDataset(tmpNXdata.getDetTree(det).getNode("data"));
			AbstractDataset tmpDataset = DatasetUtils.cast((AbstractDataset) tmpLazyDataset.getSlice(start, stop, step), AbstractDataset.FLOAT32);
			
			int[] newShape = new int[order];
			for (int i = 0; i < order; i++) 
				newShape[i] = stop[dims.length - order + i] - start[dims.length - order + i];
	
			tmpData.addData(det, "data", newShape, tmpDataset.getDtype(), tmpDataset.getBuffer(), "counts", 1);
		} catch (Exception e) {
			throw new ExecutionException("Error processing data from " + det, e);
		}

		if (calibration != null) {
			try {
				order = 2;
				int[] dims = tmpNXdata.getDetTree(calibration).getNode("data").getData().dimensions;
				checkDataShape(dims, order);

				int[] startCal =  dims.clone();
				int[] stopCal =  dims.clone();
				int[] stepCal =  new int[dims.length];
				Arrays.fill(stepCal, 1);
				for (int i = 0; i <= dims.length-order; i++) {
					startCal[i] = start[i];
					stopCal[i] = stop[i];
				}
				startCal[dims.length - 1] = 0;
				stopCal[dims.length - 1] = dims[dims.length - 1];
				LazyDataset tmpLazyDataset = (LazyDataset) Nexus.createLazyDataset(tmpNXdata.getDetTree(calibration).getNode("data"));
				AbstractDataset tmpDataset = DatasetUtils.cast(tmpLazyDataset.getSlice(startCal, stopCal, stepCal), AbstractDataset.FLOAT32);

				int[] newShape = new int[order];
				for (int i = 0; i < order; i++) 
					newShape[i] = stopCal[dims.length - order + i] - startCal[dims.length - order + i];

				tmpData.addData(calibration, "data", newShape, tmpDataset.getDtype(), tmpDataset.getBuffer(), "counts", 1);
			} catch (Exception e) {
				throw new ExecutionException("Error processing calibration data from " + calibration, e);
			}
		}
		return tmpData;
	}
	
	public static int[] convertFlatIndex(int n, int[] frames, int dim) {
		
		int[] result = new int[frames.length - dim];
		int res = n;
		
		for (int i = 0; i < (result.length - 1); i++) {
			int div = 1;
			for (int j = (frames.length - 1 - dim); j > i; j--)
				div *= frames[j];
			result[i] = res / div;
			res %= div;
		}
		result[result.length - 1] = res;
		
		return result;
	}

	public static void selectGridRange(final int[] frames, final int[] gridFrame, int i, int currentBatch, int[] start, int[] stop) {
		
		for (int n = 0; n < gridFrame.length; n++) {
			start[n] = gridFrame[n];
			stop[n] = gridFrame[n]+1;
		}
		
		start[gridFrame.length] = i;
		stop[gridFrame.length] = i + currentBatch;
		
		for (int n = (frames.length-1); n > gridFrame.length; n--) {
			start[n] = 0;
			stop[n] = frames[n];
		}
		
	}
	
	public static int gridPoints(final int[] frames, int dim) {
		int gridIdx = 1;
		for (int n = 0; n < (frames.length-1-dim); n++ )
			gridIdx *= frames[n];
		
		return gridIdx;
	}
	
}
