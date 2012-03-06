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

import java.lang.reflect.Array;
import java.util.Arrays;

import gda.util.TestUtils;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
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
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class NcdNexusUtilsTest {

	private static ILazyDataset lazyDataset;
	private static String inputPath;
	private static String detector = "Rapid2D";
	private static int totalFrames = 120;
	private static int[] frames = new int[] {1, totalFrames, 512, 512};
	
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
}
