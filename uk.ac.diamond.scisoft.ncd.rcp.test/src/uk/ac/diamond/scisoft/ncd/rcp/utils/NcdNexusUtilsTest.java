/*
 * Copyright 2012 Diamond Light Source Ltd.
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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.apache.commons.math3.util.MultidimensionalCounter.Iterator;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.hdf5.api.HDF5File;
import org.eclipse.dawnsci.hdf5.api.HDF5Group;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.TestUtils;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

public class NcdNexusUtilsTest {

	private static ILazyDataset lazyDataset;
	private static String inputPath;
	private static String detector = "Rapid2D";
	private static int totalFrames = 120;
	private static int dim = 2;
	private static int[] image = new int[] {512, 512};
	private static int[] frames = new int[] {1, totalFrames, image[0], image[1]};
	
	private static String format = "0;1,3,15-30,55";
	
	@BeforeClass
	public static void initLazyNcdProcessing() throws Exception {

		String testFileFolder = TestUtils.getGDALargeTestFilesLocation();
		if( testFileFolder == null){
			Assert.fail("TestUtils.getGDALargeTestFilesLocation() returned null - test aborted");
		}

		inputPath = testFileFolder + "/NCDReductionTest/i22-24139.nxs";
		
		HDF5File tmpfile = new HDF5Loader(inputPath).loadTree();
		HDF5Group node = (HDF5Group) tmpfile.findNodeLink("/entry1/Rapid2D").getDestination();
		lazyDataset = node.getDataset("data").getDataset();
	}
	
	@Test
	public void testSliceInputDim0() throws HDF5Exception {
		testSliceInputData(0, 1, 0);
	}

	@Test
	public void testSliceInputDim1() throws HDF5Exception {
		testSliceInputData(1, totalFrames, 0);
	}

	@Test
	public void testSliceInputFrames() throws HDF5Exception {
		testSliceInputData(1, 42, 88);
	}

	public void testSliceInputData(int sliceDim, int sliceBatch, int frameStart) throws HDF5Exception {
		int[] start = new int[] {0, 0, 0, 0};
		start[sliceDim] = frameStart;
		
		int[] stop = Arrays.copyOf(frames, frames.length);
		Arrays.fill(stop, 0, sliceDim + 1, 1);
		stop[sliceDim] = Math.min(frameStart + sliceBatch, frames[sliceDim]);
		
		int[] step = new int[] {1, 1, 1, 1};
		IDataset data = lazyDataset.getSlice(start, stop, step);
		
	    DataSliceIdentifiers dr_id = readDataId(inputPath, detector, "data", null)[0];
	    long[] frames_long = (long[]) ConvertUtils.convert(frames, long[].class);
	    SliceSettings drSlice = new SliceSettings(frames_long, sliceDim, sliceBatch);
	    drSlice.setStart(start);
		Dataset result = NcdNexusUtils.sliceInputData(drSlice, dr_id);
		
		for (int idx = 0; idx < data.getShape()[0]; idx++)
			for (int frame = 0; frame < data.getShape()[1]; frame++) {
				start = new int[] { idx, frame, 0, 0 };
				stop = new int[] { idx + 1, frame + 1, image[0], image[1] };
				Dataset testResult = result.getSlice(start, stop, null);
				Dataset testData = (Dataset) data.getSlice(start, stop, null);
				float valResult = testResult.max().floatValue();
				float valData = testData.max().floatValue();
				float acc = (float) Math.max(1e-6 * Math.abs(Math.sqrt(valResult * valResult + valData * valData)),
						1e-10);
				assertArrayEquals(String.format("Data slicing test for frame (%d, %d) has failed.", idx, frame),
						(float[])testResult.getBuffer(), (float[])testData.getBuffer(), acc);
			}
	}
	
	@Test
	public void testSliceString() throws HDF5Exception {
		
		int[] datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
		ArrayList<int[]> list = NcdDataUtils.createSliceList(format, datDimMake);
		for (int i = 0; i < datDimMake.length; i++)
			datDimMake[i] = list.get(i).length;
		
	    DataSliceIdentifiers dr_id = readDataId(inputPath, detector, "data", null)[0];
	    long[] frames_long = (long[]) ConvertUtils.convert(frames, long[].class);
	    
	    SliceSettings drSlice = new SliceSettings(frames_long, 0, 1);
		int[] start = new int[] {0, 0, 0, 0};
	    drSlice.setStart(start);
		Dataset data = NcdNexusUtils.sliceInputData(drSlice, dr_id);
		Dataset result = sliceInputData(dim, frames, format, dr_id);
		
		MultidimensionalCounter resultCounter = new MultidimensionalCounter(result.getShape());
		Iterator resIter = resultCounter.iterator();
		while (resIter.hasNext()) {
			resIter.next();
			int[] gridFrame = Arrays.copyOf(resIter.getCounts(), frames.length);
			for (int i = 0; i < datDimMake.length; i++)
				gridFrame[i] = list.get(i)[gridFrame[i]];
			float valResult = result.getFloat(resIter.getCounts());
			float valData = data.getFloat(gridFrame);
			double acc = Math.max(1e-6 * Math.abs(Math.sqrt(valResult * valResult + valData * valData)), 1e-10);

			assertEquals(String.format("Data slicing test for pixel %s has failed.", Arrays.toString(resIter.getCounts())),
								valData, valResult, acc);
		}
	}
	
	
	public static DataSliceIdentifiers[] readDataId(String dataFile, String detector, String dataset, String errors) throws HDF5Exception {
		int file_handle = H5.H5Fopen(dataFile, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		int entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		//int instrument_group_id = H5.H5Gopen(entry_group_id, "instrument", HDF5Constants.H5P_DEFAULT);
		//int detector_group_id = H5.H5Gopen(instrument_group_id, detector, HDF5Constants.H5P_DEFAULT);
		int detector_group_id = H5.H5Gopen(entry_group_id, detector, HDF5Constants.H5P_DEFAULT);
		int input_data_id = H5.H5Dopen(detector_group_id, dataset, HDF5Constants.H5P_DEFAULT);
		int input_errors_id = -1;
		if (errors != null) {
			input_errors_id = H5.H5Dopen(detector_group_id, errors, HDF5Constants.H5P_DEFAULT);
		}
		
		DataSliceIdentifiers ids = new DataSliceIdentifiers();
		ids.setIDs(detector_group_id, input_data_id);
		DataSliceIdentifiers errors_ids = null;
		if (errors != null) {
			errors_ids = new DataSliceIdentifiers();
			errors_ids.setIDs(detector_group_id, input_errors_id);
		}
		return new DataSliceIdentifiers[] {ids, errors_ids};
	}
	
	public static Dataset sliceInputData(int dim, int[] frames, String format, DataSliceIdentifiers ids) throws HDF5Exception {
		int[] datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
		int[] imageSize = Arrays.copyOfRange(frames, frames.length - dim, frames.length);
		ArrayList<int[]> list = NcdDataUtils.createSliceList(format, datDimMake);
		for (int i = 0; i < datDimMake.length; i++)
			datDimMake[i] = list.get(i).length;
		int[] framesTotal = ArrayUtils.addAll(datDimMake, imageSize);

		
		long[] block = new long[frames.length];
		block = Arrays.copyOf((long[]) ConvertUtils.convert(frames, long[].class), block.length);
		Arrays.fill(block, 0, block.length - dim, 1);
		int[] block_int = (int[]) ConvertUtils.convert(block, int[].class);
		
		long[] count = new long[frames.length];
		Arrays.fill(count, 1);
		
		int dtype = HDF5Loader.getDtype(ids.dataclass_id, ids.datasize_id);
		Dataset data = DatasetFactory.zeros(block_int, dtype);
		Dataset result = null;
		
		MultidimensionalCounter bgFrameCounter = new MultidimensionalCounter(datDimMake);
		Iterator iter = bgFrameCounter.iterator();
		while (iter.hasNext()) {
			iter.next();
			long[] bgFrame = (long[]) ConvertUtils.convert(iter.getCounts(), long[].class);
			long[] gridFrame = new long[datDimMake.length];
			for (int i = 0; i < datDimMake.length; i++)
				gridFrame[i] = list.get(i)[(int) bgFrame[i]];
			
				long[] start = new long[frames.length];
				start = Arrays.copyOf(gridFrame, frames.length);
				
				int memspace_id = H5.H5Screate_simple(block.length, block, null);
				H5.H5Sselect_hyperslab(ids.dataspace_id, HDF5Constants.H5S_SELECT_SET,
						start, block, count, block);
				H5.H5Dread(ids.dataset_id, ids.datatype_id, memspace_id, ids.dataspace_id,
						HDF5Constants.H5P_DEFAULT, data.getBuffer());
				if (result == null) {
					result = data.clone();
				} else {
					result = DatasetUtils.append(result, data, block.length - dim - 1);
				}
		}
		
		if (result != null) {
			result.setShape(framesTotal);
		}
		return result;
	}
}
