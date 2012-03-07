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

import gda.util.TestUtils;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.math.util.MultidimensionalCounter;
import org.apache.commons.math.util.MultidimensionalCounter.Iterator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Group;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class NcdNexusUtilsTest {

	private static ILazyDataset lazyDataset;
	private static String inputPath;
	private static String detector = "Rapid2D";
	private static int totalFrames = 120;
	private static int dim = 2;
	private static int[] frames = new int[] {1, totalFrames, 512, 512};
	
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
		
	    DataSliceIdentifiers dr_id = NcdNexusUtils.readDataId(inputPath, detector);
	    long[] frames_long = (long[]) ConvertUtils.convert(frames, long[].class);
	    SliceSettings drSlice = new SliceSettings(frames_long, sliceDim, sliceBatch);
	    drSlice.setStart(start);
		AbstractDataset result = NcdNexusUtils.sliceInputData(drSlice, dr_id);
		
		for (int idx = 0; idx < data.getShape()[0]; idx++)
			for (int frame = 0; frame < data.getShape()[1]; frame++)
				for (int i = 0; i < 512; i++)
					for (int j = 0; j < 512; j++) {
						float valResult = result.getFloat(new int[] { idx, frame, i, j });
						float valData = data.getFloat(new int[] { idx, frame, i, j });
						double acc = Math.max(1e-6 * Math.abs(Math.sqrt(valResult * valResult + valData * valData)),
								1e-10);

						assertEquals(String.format("Data slicing test for pixel (%d, %d, %d) has failed.", frame, i, j),
								valData, valResult, acc);
					}
	}
	
	@Test
	public void testSliceString() throws HDF5Exception {
		
		int[] datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
		ArrayList<int[]> list = NcdDataUtils.createSliceList(format, datDimMake);
		for (int i = 0; i < datDimMake.length; i++)
			datDimMake[i] = list.get(i).length;
		
	    DataSliceIdentifiers dr_id = NcdNexusUtils.readDataId(inputPath, detector);
	    long[] frames_long = (long[]) ConvertUtils.convert(frames, long[].class);
	    
	    SliceSettings drSlice = new SliceSettings(frames_long, 0, 1);
		int[] start = new int[] {0, 0, 0, 0};
	    drSlice.setStart(start);
		AbstractDataset data = NcdNexusUtils.sliceInputData(drSlice, dr_id);
		AbstractDataset result = NcdNexusUtils.sliceInputData(dim, frames, format, dr_id);
		
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
}
