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

package uk.ac.diamond.scisoft.ncd.utils;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeNode;
import gda.data.nexus.tree.NexusTreeNodeSelection;

import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math.exception.OutOfRangeException; 
import org.apache.commons.math.util.MultidimensionalCounter;
import org.eclipse.core.commands.ExecutionException;
import org.nexusformat.NexusFile;
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

	public static INexusTree selectNAxisFrames(String det, String calibration, INexusTree tmpNXdata, int order, int[] start, int[] stop) throws ExecutionException {
		
		NexusTreeNode tmpData = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
		
		try {
			int[] dims = tmpNXdata.getNode(det).getNode("data").getData().dimensions;
			checkDataShape(dims, order);
	
			int[] step =  new int[dims.length];
			Arrays.fill(step, 1);
			
			ILazyDataset tmpLazyDataset = Nexus.createLazyDataset(tmpNXdata.getNode(det).getNode("data"));
			AbstractDataset tmpDataset = DatasetUtils.cast((AbstractDataset) tmpLazyDataset.getSlice(start, stop, step), AbstractDataset.FLOAT32);
			
			int[] newShape = new int[order];
			for (int i = 0; i < order; i++) 
				newShape[i] = stop[dims.length - order + i] - start[dims.length - order + i];
	
			NexusTreeNode tmpDet = new NexusTreeNode(det, NexusExtractor.NXDetectorClassName, null);
			tmpDet.setIsPointDependent(true);
			addData(tmpDet, "data", newShape, tmpDataset.getDtype(), tmpDataset.getBuffer(), "counts", 1);
			tmpData.addChildNode(tmpDet);
		} catch (Exception e) {
			throw new ExecutionException("Error processing data from " + det, e);
		}

		if (calibration != null) {
			try {
				order = 2;
				int[] dims = tmpNXdata.getNode(calibration).getNode("data").getData().dimensions;
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
				LazyDataset tmpLazyDataset = (LazyDataset) Nexus.createLazyDataset(tmpNXdata.getNode(calibration).getNode("data"));
				AbstractDataset tmpDataset = DatasetUtils.cast(tmpLazyDataset.getSlice(startCal, stopCal, stepCal), AbstractDataset.FLOAT32);

				int[] newShape = new int[order];
				for (int i = 0; i < order; i++) 
					newShape[i] = stopCal[dims.length - order + i] - startCal[dims.length - order + i];

				NexusTreeNode tmpDet = new NexusTreeNode(calibration, NexusExtractor.NXDetectorClassName, null);
				tmpDet.setIsPointDependent(true);
				addData(tmpDet, "data", newShape, tmpDataset.getDtype(), tmpDataset.getBuffer(), "counts", 1);
				tmpData.addChildNode(tmpDet);
			} catch (Exception e) {
				throw new ExecutionException("Error processing calibration data from " + calibration, e);
			}
		}
		return tmpData;
	}
	
	public static INexusTree getDetTree(INexusTree parent, String detName){
		for(INexusTree branch : parent){
			if( branch.getNxClass().equals(NexusExtractor.NXDetectorClassName) && branch.getName().equals(detName)){
				return branch;
			}
		}
		//else add item and return that
		NexusTreeNode detTree = new NexusTreeNode(detName, NexusExtractor.NXDetectorClassName, null);
		detTree.setIsPointDependent(true);
		parent.addChildNode(detTree);
		return detTree;
	}
	
	/**
	 * @param detName
	 * @param dataName name of the child whose data is to be returned
	 * @param className class name of the child whose data is to be returned e.g. NexusExtractor.SDSClassName
	 * @return NexusGroupData
	 */
	public static NexusGroupData getData(INexusTree parent, String detName, String dataName, String className) {
		INexusTree detTree = getDetTree(parent, detName);
		
		for(int i = 0; i < detTree.getNumberOfChildNodes(); i++) {
			INexusTree dataTree = detTree.getChildNode(i);
			if(dataTree.getName().equals(dataName) && dataTree.getNxClass().equals(className)) {
				return dataTree.getData();
			}
		}
		
		return null;		
	}
	
	
	/**
	 * Adds the specified data to the named detector
	 * @param detName The name of the detector to add data to
	 * @param dataName The name of the detector to add data to
	 * @param dimensions the dimensions of the data to add
	 * @param type the nexus type of the data, e.g. NexusFile.NX_INT32
	 * @param dataValues the data to add
	 * @param units  - if not null a units attribute is added
	 * @param signalVal - if not null a signal attribute is added
	 * @return The node added.
	 */
	public static INexusTree addData(INexusTree parent, String detName, final String dataName, int[] dimensions, int type, Serializable dataValues, String units, Integer signalVal) {
		INexusTree detTree = getDetTree(parent, detName);
		return addData(detTree,dataName,dimensions,type,dataValues,units,signalVal);
	}
	
	public static INexusTree addData(INexusTree parent, final String dataName, int[] dimensions, int type, Serializable dataValues, String units, Integer signalVal ){
		NexusGroupData data_sds = new NexusGroupData(dimensions, type, dataValues);
		data_sds.isDetectorEntryData = true;
		NexusTreeNode data = new NexusTreeNode(dataName, NexusExtractor.SDSClassName, parent,data_sds);
		data.setIsPointDependent(true);
		if( units != null){
			data.addChildNode(new NexusTreeNode("units",NexusExtractor.AttrClassName, data, new NexusGroupData(units)));
		}
		if( signalVal != null){
			Integer[] signalValArray = {signalVal};
			data.addChildNode(new NexusTreeNode("signal",NexusExtractor.AttrClassName, data, 
					new NexusGroupData(new int[] {signalValArray.length}, NexusFile.NX_INT32, signalValArray)));
		}
		parent.addChildNode(data);	
		return data;
	}
	
	/**
	 * Adds the specified data to the named detector
	 * @param detName The name of the detector to add data to
	 * @param data_sds The implementation of NexusGroupData to be reported as the data
	 * @param units  - if not null a units attribute is added
	 * @param signalVal - if not null a signal attribute is added
	 */
	public static void addData(INexusTree parent, String detName, String dataName, NexusGroupData data_sds, String units, Integer signalVal) {
		INexusTree detTree = getDetTree(parent, detName);
		NexusTreeNode data = new NexusTreeNode(dataName, NexusExtractor.SDSClassName, null, data_sds);
		data.setIsPointDependent(data_sds.isDetectorEntryData);
		if( units != null){
			data.addChildNode(new NexusTreeNode("units",NexusExtractor.AttrClassName, data, new NexusGroupData(units)));
		}
		if( signalVal != null){
			Integer[] signalValArray = {signalVal};
			data.addChildNode(new NexusTreeNode("signal",NexusExtractor.AttrClassName, data, 
					new NexusGroupData(new int[] {signalValArray.length}, NexusFile.NX_INT32, signalValArray)));
		}
		detTree.addChildNode(data);			
	}
	
	/**
	 * Adds the specified Axis to the named detector
	 * @param detName The name of the detector to add data to
	 * @param name The name of the Axis
	 * @param axis_sds The implementation of NexusGroupData to be reported as the axis data
	 * @param axisValue The dimension which this axis relates to <b>from the detector point of view</b>, 
	 * 						i.e. 1 is the first detector axis, scan dimensions will be added as required 
	 * 						by the DataWriter	 * @param primaryValue The importance of this axis, 1 is the most relevant, then 2 etc.
	 * @param units The units the axis is specified in
	 * @param isPointDependent If this data should be added to the nexus at every point set this to true, if its a one off, make this false
	 */
	public static void addAxis(INexusTree parent,String detName, String name, NexusGroupData axis_sds, Integer axisValue, Integer primaryValue, String units,
			boolean isPointDependent) {
		INexusTree detTree = getDetTree(parent, detName);

		axis_sds.isDetectorEntryData = true;
		NexusTreeNode axis = new NexusTreeNode(name,NexusExtractor.SDSClassName, parent, axis_sds);
		Integer[] axisVal = {axisValue};
		axis.addChildNode(new NexusTreeNode("axis",NexusExtractor.AttrClassName, axis,new NexusGroupData(new int[] {axisVal.length}, NexusFile.NX_INT32, axisVal)));
		Integer[] primaryVal = {primaryValue};
		axis.addChildNode(new NexusTreeNode("primary",NexusExtractor.AttrClassName, axis,new NexusGroupData(new int[] {primaryVal.length}, NexusFile.NX_INT32, primaryVal)));
		axis.addChildNode(new NexusTreeNode("unit",NexusExtractor.AttrClassName, axis,new NexusGroupData(units)));
		axis.setIsPointDependent(isPointDependent);
		detTree.addChildNode(axis);
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

	/**
	 * Convert to multidimensional counter.
	 * 
	 * @param index
	 *            Index in unidimensional counter.
	 * @return the multidimensional counts.
	 * @throws OutOfRangeException
	 *             if {@code index} is not between {@code 0} and the value returned by getSize() (excluded).
	 *
	 * TODO: This method is a work-around for the bug in
	 *  org.apache.commons.math.utils.MultidimensionalCounter.getCounts(int index) method
	 *  in org.apache.commons.math.utils v2.2 release. It should become obsolete after upgrade to v3.0   
	 */
	public static int[] getCounts(MultidimensionalCounter counter, int index) {
		int totalSize = counter.getSize();
		int dimension = counter.getDimension();
		int[] sizes = counter.getSizes();
		int last = dimension - 1;
		if (index < 0 || index >= totalSize) {
			throw new OutOfRangeException(index, 0, totalSize);
		}

		final int[] indices = new int[dimension];
		int count = 0;
		for (int i = 0; i < last; i++) {
			int idx = 0;
			int offset = 1;
			for (int j = i + 1; j < dimension; j++)
				offset *= sizes[j];
			while (count <= index) {
				count += offset;
				++idx;
			}
			--idx;
			count -= offset;
			indices[i] = idx;
		}

		indices[last] = index - count;

		return indices;
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
