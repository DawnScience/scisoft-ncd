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

package uk.ac.diamond.scisoft.ncd.rcp.reduction;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import gda.data.nexus.extractor.NexusExtractor;
import gda.util.TestUtils;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.reduction.LazyAverage;
import uk.ac.diamond.scisoft.ncd.reduction.LazyBackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.reduction.LazyDetectorResponse;
import uk.ac.diamond.scisoft.ncd.reduction.LazyInvariant;
import uk.ac.diamond.scisoft.ncd.reduction.LazyNormalisation;
import uk.ac.diamond.scisoft.ncd.reduction.LazySectorIntegration;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class NcdLazyDataReductionTest {

	private static String testScratchDirectoryName;
	private static String filename, bgFile, drFile, secFile;
	private static String testDatasetName = "testInput"; 
	private static String testNormName = "testNorm"; 
	
	private static AbstractDataset data;
	static long [] shape = new long[] {3, 91, 128, 64};
	static long [] normShape = new long[] {shape[0], shape[1], 1};
	static long [] invShape = new long[] {shape[0], shape[1]};
	static long [] imageShape = new long[] {shape[2], shape[3]};
	static int bgFrames = (int) (shape[1] / 9);
	static long [] bgShape = new long[] {bgFrames, imageShape[0], imageShape[1]};
	private static float scale = 1.0f; 
	private static float scaleBg = 0.46246f;
	private static float absScale = 100.0f;
	static int dim = 2;
	static int points = 1;
	
	@BeforeClass
	public static void writeTestNexusFile() throws Exception {
		
		
		testScratchDirectoryName = TestUtils.generateDirectorynameFromClassname(NcdLazyDataReductionTest.class.getCanonicalName());
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
		
		for (long n: imageShape) points *= n;
		
		{
			filename = testScratchDirectoryName + "ncd_sda_test.nxs"; 

			int nxsFile = H5.H5Fcreate(filename, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			int entry_id = NcdNexusUtils.makegroup(nxsFile, "entry1", NexusExtractor.NXEntryClassName);
			NcdNexusUtils.makegroup(entry_id, "results", NexusExtractor.NXInstrumentClassName);
			int datagroup_id = NcdNexusUtils.makegroup(entry_id, testDatasetName, NexusExtractor.NXDataClassName);
			int normgroup_id = NcdNexusUtils.makegroup(entry_id, testNormName, NexusExtractor.NXDataClassName);
			int data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, shape.length, shape, true, "counts");
			int norm_id = NcdNexusUtils.makedata(normgroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, normShape.length, normShape, true, "counts");

			for (int n = 0; n < shape[0]; n++) {
				for (int frames = 0; frames < shape[1]; frames++) {
					float[] norm = new float[] {scale*(n+1)};
					float[] data = new float[points];
					for (int i = 0; i < shape[2]; i++) {
						for (int j = 0; j < shape[3]; j++) {
							int idx = (int) (i*shape[3] + j); 
							float val = n*shape[1] + frames + i*shape[3] + j;
							data[idx] = val;
						}
					}
					{
						long[] start = new long[] { n, frames, 0, 0 };
						long[] count = new long[] { 1, 1, 1, 1 };
						long[] block = new long[] { 1, 1, shape[2], shape[3] };
						int filespace_id = H5.H5Dget_space(data_id);
						int type_id = H5.H5Dget_type(data_id);
						int memspace_id = H5.H5Screate_simple(dim, imageShape, null);
						H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
						H5.H5Dwrite(data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, data);
						H5.H5Sclose(memspace_id);
						H5.H5Sclose(filespace_id);
						H5.H5Tclose(type_id);
					}
					{
						long[] start = new long[] { n, frames, 0 };
						long[] count = new long[] { 1, 1, 1 };
						long[] block = new long[] { 1, 1, 1 };
						int filespace_id = H5.H5Dget_space(norm_id);
						int type_id = H5.H5Dget_type(norm_id);
						int memspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
						H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
						H5.H5Dwrite(norm_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, norm);
						H5.H5Sclose(memspace_id);
						H5.H5Sclose(filespace_id);
						H5.H5Tclose(type_id);
					}
				}
			}
			H5.H5Dclose(data_id);
			H5.H5Gclose(datagroup_id);
			H5.H5Dclose(norm_id);
			H5.H5Gclose(normgroup_id);
			H5.H5Gclose(entry_id);
			H5.H5Fclose(nxsFile);
		}
		

		{
			bgFile = testScratchDirectoryName + "bgfile_ncd_sda_test.nxs"; 

			int nxsFile = H5.H5Fcreate(bgFile, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			int entry_id = NcdNexusUtils.makegroup(nxsFile, "entry1", NexusExtractor.NXEntryClassName);
			int datagroup_id = NcdNexusUtils.makegroup(entry_id, testDatasetName, NexusExtractor.NXDataClassName);
			int data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, bgShape.length, bgShape, true, "counts");

			for (int frames = 0; frames < bgFrames; frames++) {
				float[] data = new float[points];
				for (int i = 0; i < shape[2]; i++) {
					for (int j = 0; j < shape[3]; j++) {
						int idx = (int) (i*shape[3] + j);
						data[idx] = i*shape[3] + j;
					}
				}
				{
					long[] start = new long[] { frames, 0, 0 };
					long[] count = new long[] { 1, 1, 1 };
					long[] block = new long[] { 1, bgShape[1], bgShape[2] };
					int filespace_id = H5.H5Dget_space(data_id);
					int type_id = H5.H5Dget_type(data_id);
					int memspace_id = H5.H5Screate_simple(dim, imageShape, null);
					H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
					H5.H5Dwrite(data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, data);
					H5.H5Sclose(memspace_id);
					H5.H5Sclose(filespace_id);
					H5.H5Tclose(type_id);
				}
			}
			H5.H5Dclose(data_id);
			H5.H5Gclose(datagroup_id);
			H5.H5Gclose(entry_id);
			H5.H5Fclose(nxsFile);
		}
		
		{
			drFile = testScratchDirectoryName + "drfile_ncd_sda_test.nxs"; 
        
			int nxsFile = H5.H5Fcreate(drFile, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			int entry_id = NcdNexusUtils.makegroup(nxsFile, "entry1", NexusExtractor.NXEntryClassName);
			int processing_group_id = NcdNexusUtils.makegroup(entry_id, "instrument", NexusExtractor.NXInstrumentClassName);
			int datagroup_id = NcdNexusUtils.makegroup(processing_group_id, testDatasetName, NexusExtractor.NXDetectorClassName);
			int data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, imageShape.length, imageShape, true, "counts");

			float[] data = new float[points];
			for (int i = 0; i < shape[2]; i++) {
				for (int j = 0; j < shape[3]; j++) {
					int idx = (int) (i*shape[3] + j); 
					float val = scaleBg;
					data[idx] = val;
				}
			}

			int filespace_id = H5.H5Dget_space(data_id);
			int type_id = H5.H5Dget_type(data_id);
			int memspace_id = H5.H5Screate_simple(dim, imageShape, null);
			H5.H5Sselect_all(filespace_id);
			H5.H5Dwrite(data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, data);
			H5.H5Sclose(memspace_id);
			H5.H5Sclose(filespace_id);
			H5.H5Tclose(type_id);

			H5.H5Dclose(data_id);
			H5.H5Gclose(datagroup_id);
			H5.H5Gclose(processing_group_id);
			H5.H5Gclose(entry_id);
			H5.H5Fclose(nxsFile);
		}
		
		{
			secFile = testScratchDirectoryName + "secfile_ncd_sda_test.nxs"; 
			
			int nxsFile = H5.H5Fcreate(secFile, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			int entry_id = NcdNexusUtils.makegroup(nxsFile, "entry1", NexusExtractor.NXEntryClassName);
			int datagroup_id = NcdNexusUtils.makegroup(entry_id, testDatasetName, NexusExtractor.NXDataClassName);
			int data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, shape.length, shape, true, "counts");

			for (int n = 0; n < shape[0]; n++) {
				for (int frames = 0; frames < shape[1]; frames++) {
					float[] data = new float[points];
					for (int i = 0; i < shape[2]; i++) {
						for (int j = 0; j < shape[3]; j++) {
							int idx = (int) (i*shape[3] + j); 
							float val = n*shape[1] + frames + (float)Math.sqrt((i*i+j*j)/(1.0f*shape[3]*shape[3]));
							data[idx] = val;
						}
					}
					{
						long[] start = new long[] { n, frames, 0, 0 };
						long[] count = new long[] { 1, 1, 1, 1 };
						long[] block = new long[] { 1, 1, shape[2], shape[3] };
						int filespace_id = H5.H5Dget_space(data_id);
						int type_id = H5.H5Dget_type(data_id);
						int memspace_id = H5.H5Screate_simple(dim, imageShape, null);
						H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
						H5.H5Dwrite(data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, data);
						H5.H5Sclose(memspace_id);
						H5.H5Sclose(filespace_id);
						H5.H5Tclose(type_id);
					}
				}
			}
			H5.H5Dclose(data_id);
			H5.H5Gclose(datagroup_id);
			H5.H5Gclose(entry_id);
			H5.H5Fclose(nxsFile);
		}
		
	    DataSliceIdentifiers data_id = NcdNexusUtils.readDataId(filename, testDatasetName);
	    SliceSettings dataSlice = new SliceSettings(shape, 0, (int) shape[0]);
	    int[] start = new int[] {0, 0, 0, 0};
	    dataSlice.setStart(start);
		data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
		
	}
	
	@Test
	public void testLazyNormalisation() throws HDF5Exception {
		
		LazyNormalisation lazyNormalisation = new LazyNormalisation();
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
		int norm_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyNormalisation.name, "NXdetector");
		int type = HDF5Constants.H5T_NATIVE_FLOAT;
		int norm_data_id = NcdNexusUtils.makedata(norm_group_id, "data", type, shape.length, shape, true, "counts");

		DataSliceIdentifiers calibration_ids = NcdNexusUtils.readDataId(filename, testNormName);

		int rankCal = H5.H5Sget_simple_extent_ndims(calibration_ids.dataspace_id);
		long[] framesCal = new long[rankCal];
		H5.H5Sget_simple_extent_dims(calibration_ids.dataspace_id, framesCal, null);

		lazyNormalisation.setAbsScaling((double) absScale);
		lazyNormalisation.setNormChannel(0);

		SliceSettings calibrationSliceParams = new SliceSettings(framesCal, 0, (int) framesCal[0]);
	    int[] start = new int[] {0, 0, 0};
	    calibrationSliceParams.setStart(start);
		AbstractDataset dataCal = NcdNexusUtils.sliceInputData(calibrationSliceParams, calibration_ids);

		DataSliceIdentifiers input_ids = new DataSliceIdentifiers();
		input_ids.setIDs(norm_group_id, norm_data_id);
	    long[] lstart = new long[] {0, 0, 0, 0};
		long[] count = new long[] {1, 1, 1, 1};
		input_ids.setSlice(lstart, shape, count, shape);
		AbstractDataset outDataset = lazyNormalisation.execute(dim, data, dataCal, input_ids);
		
		for (int g = 0; g < shape[0]; g++)
			for (int k = 0; k < shape[1]; k++) {
				for (int i = 0; i < shape[2]; i++)
					for (int j = 0; j < shape[3]; j++) {
						float value = outDataset.getFloat(new int[] {g, k, i, j});
						float expected = absScale*(g*shape[1] + k + i*shape[3] + j) / (scale*(g+1));

						assertEquals(String.format("Test normalisation frame for (%d, %d, %d, %d)", g, k, i, j), expected, value, 1e-6*expected);
					}
			}
	}
	
	//@Test
	public void testLazyBackgroundSubtraction() throws HDF5Exception {
		
		LazyBackgroundSubtraction lazyBackgroundSubtraction = new LazyBackgroundSubtraction();
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
		int bg_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyBackgroundSubtraction.name, "NXdetector");
		int type = HDF5Constants.H5T_NATIVE_FLOAT;
		int bg_data_id = NcdNexusUtils.makedata(bg_group_id, "data", type, shape.length, shape, true, "counts");
			
		DataSliceIdentifiers bgIds = NcdNexusUtils.readDataId(bgFile, testDatasetName);
		H5.H5Sget_simple_extent_dims(bgIds.dataspace_id, bgShape, null);
		int[] bgShape_int = (int[]) ConvertUtils.convert(bgShape, int[].class);
		lazyBackgroundSubtraction.setBgScale((double) scaleBg);

		SliceSettings bgSliceParams = new SliceSettings(bgShape, 0, bgShape_int[0]);
		int[] start = new int[] { 0, 0, 0 };
		bgSliceParams.setStart(start);
		AbstractDataset bgData = NcdNexusUtils.sliceInputData(bgSliceParams, bgIds);

		DataSliceIdentifiers input_ids = new DataSliceIdentifiers();
		input_ids.setIDs(bg_group_id, bg_data_id);
		long[] lstart = new long[] { 0, 0, 0, 0 };
		long[] count = new long[] { 1, 1, 1, 1 };
		input_ids.setSlice(lstart, shape, count, shape);
		input_ids.setIDs(bg_group_id, bg_data_id);
		AbstractDataset outDataset = lazyBackgroundSubtraction.execute(dim, data, bgData, input_ids);
			
		for (int g = 0; g < shape[0]; g++)
			for (int k = 0; k < shape[1]; k++) {
				for (int i = 0; i < shape[2]; i++)
					for (int j = 0; j < shape[3]; j++) {
						float value = outDataset.getFloat(new int[] {g, k, i, j});
						float expected = g*shape[1] + k + (1.0f - scaleBg)*(i*shape[3] + j);

						assertEquals(String.format("Test background subtraction frame for (%d, %d, %d, %d)", g, k, i, j), expected, value, 1e-6*expected);
					}
			}
	}
	
	@Test
	public void testLazyDetectorResponse() throws HDF5Exception {
		
		LazyDetectorResponse lazyDetectorResponse = new LazyDetectorResponse(drFile, testDatasetName);
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
	    int dr_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyDetectorResponse.name, "NXdetector");
		int type = HDF5Constants.H5T_NATIVE_FLOAT;
		int dr_data_id = NcdNexusUtils.makedata(dr_group_id, "data", type, shape.length, shape, true, "counts");
			
		lazyDetectorResponse.createDetectorResponseInput();
		
		DataSliceIdentifiers input_ids = new DataSliceIdentifiers();
		input_ids.setIDs(dr_group_id, dr_data_id);
		long[] lstart = new long[] { 0, 0, 0, 0 };
		long[] count = new long[] { 1, 1, 1, 1 };
		input_ids.setSlice(lstart, shape, count, shape);
		AbstractDataset outDataset = lazyDetectorResponse.execute(dim, data, input_ids);
		
		for (int g = 0; g < shape[0]; g++)
			for (int k = 0; k < shape[1]; k++) {
				for (int i = 0; i < shape[2]; i++)
					for (int j = 0; j < shape[3]; j++) {
						float value = outDataset.getFloat(new int[] {g, k, i, j});
						float expected = (g*shape[1] + k + i*shape[3] + j)*scaleBg;

						assertEquals(String.format("Test detector response frame for (%d, %d, %d, %d)", g, k, i, j), expected, value, 1e-6*expected);
					}
			}
	}
	
	@Test
	public void testLazyInvariant() throws HDF5Exception {

		LazyInvariant lazyInvariant = new LazyInvariant();
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
	    int inv_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyInvariant.name, "NXdetector");
		int type = HDF5Constants.H5T_NATIVE_FLOAT;
		int inv_data_id = NcdNexusUtils.makedata(inv_group_id, "data", type, invShape.length, invShape, true, "counts");
		
		DataSliceIdentifiers inv_id = new DataSliceIdentifiers();
		inv_id.setIDs(inv_group_id, inv_data_id);
		long[] lstart = new long[] { 0, 0 };
		long[] count = new long[] { 1, 1 };
		inv_id.setSlice(lstart, invShape, count, invShape);
    
		AbstractDataset outDataset = lazyInvariant.execute(dim, data, inv_id);
		for (int g = 0; g < invShape[0]; g++)
			for (int k = 0; k < invShape[1]; k++) {
				float value = outDataset.getFloat(new int[] {g, k});
				float expected = 0.0f;
				for (int i = 0; i < shape[2]; i++)
					for (int j = 0; j < shape[3]; j++) 
						expected += g*shape[1] + k + i*shape[3] + j;
				assertEquals(String.format("Test invariant result for (%d, %d)", g, k), expected, value, 1e-6*expected);
			}
	}
	
	@Test
	public void testLazySectorIntegration() throws Exception {
		
		LazySectorIntegration lazySectorIntegration = new LazySectorIntegration();
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
		int sec_group_id = NcdNexusUtils.makegroup(processing_group_id, LazySectorIntegration.name, "NXdetector");
		int type = HDF5Constants.H5T_NATIVE_FLOAT;
		SectorROI intSector = new SectorROI(0, 0, 0, shape[3], 0, 90);
		int[] intRadii = intSector.getIntRadii();
		double[] radii = intSector.getRadii();
		double dpp = intSector.getDpp();
		long[] secFrames = Arrays.copyOf(shape, shape.length - dim + 1);
		secFrames[secFrames.length - 1] = intRadii[1] - intRadii[0] + 1;
		int sec_data_id = NcdNexusUtils.makedata(sec_group_id, "data", type, secFrames.length, secFrames, true,
				"counts");

		double[] angles = intSector.getAngles();
		long[] azFrames = Arrays.copyOf(secFrames, secFrames.length);
		azFrames[azFrames.length - 1] = (int) Math.ceil((angles[1] - angles[0]) * radii[1] * dpp);
		int az_data_id = NcdNexusUtils.makedata(sec_group_id, "azimuth", type, azFrames.length, azFrames, false,
				"counts");

		lazySectorIntegration.setIntSector(intSector);

		DataSliceIdentifiers input_ids = new DataSliceIdentifiers();
		long[] lstart = new long[] { 0, 0, 0, 0 };
		long[] count = new long[] { 1, 1, 1, 1 };
		input_ids.setSlice(lstart, shape, count, shape);

		DataSliceIdentifiers sector_id = new DataSliceIdentifiers(input_ids);
		sector_id.setIDs(sec_group_id, sec_data_id);
		DataSliceIdentifiers azimuth_id = new DataSliceIdentifiers(input_ids);
		azimuth_id.setIDs(sec_group_id, az_data_id);

		AbstractDataset[] outDataset = lazySectorIntegration.execute(dim, data, sector_id, azimuth_id);
			
		for (int g = 0; g < shape[0]; g++)
			for (int k = 0; k < shape[1]; k++) {
				int[] startImage = new int[] {g, k, 0, 0};
				int[] stopImage = new int[] {g + 1, k + 1, (int) shape[2], (int) shape[3]};
				AbstractDataset image = data.getSlice(startImage, stopImage, null);
				AbstractDataset[] intResult = ROIProfile.sector(image.squeeze(), null, intSector);
				for (int i = 0; i < outDataset[1].getShape()[2]; i++) {
						float value = outDataset[1].getFloat(new int[] {g, k, i});
						float expected = intResult[0].getFloat(new int[] {i});
						assertEquals(String.format("Test radial sector integration profile for frame (%d, %d, %d)", g, k, i), expected, value, 1e-6*expected);
				}
				for (int i = 0; i < outDataset[0].getShape()[2]; i++) {
					float value = outDataset[0].getFloat(new int[] {g, k, i});
					float expected = intResult[1].getFloat(new int[] {i});
					assertEquals(String.format("Test azimuthal sector integration profile for frame (%d, %d, %d)", g, k, i), expected, value, 1e-6*expected);
				}
			}
	}
	
	@Test
	public void testLazyAverage() throws Exception {
		
		LazyAverage lazyAverage = new LazyAverage();
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
		
	    DataSliceIdentifiers input_ids = NcdNexusUtils.readDataId(filename, testDatasetName);
		long[] lstart = new long[] { 0, 0, 0, 0 };
		long[] count = new long[] { 1, 1, 1, 1 };
		input_ids.setSlice(lstart, shape, count, shape);
		
		lazyAverage.setAverageIndices(new int[] {1,2});
		lazyAverage.execute(dim, (int[]) ConvertUtils.convert(shape, int[].class), processing_group_id, 1000, input_ids);
		
	    long[] shapeRes = new long[] {1, 1, shape[2], shape[3]}; 
		SliceSettings resultsSlice = new SliceSettings(shapeRes, 0, (int) shapeRes[0]);
	    int[] start = new int[] {0, 0, 0, 0};
	    resultsSlice.setStart(start);
		AbstractDataset outDataset = NcdNexusUtils.sliceInputData(resultsSlice, input_ids);
		
		for (int i = 0; i < shape[2]; i++)
			for (int j = 0; j < shape[3]; j++) {
				float value = outDataset.getFloat(new int[] {0, 0, i, j});
				float expected = ((shape[0]-1)*shape[1]/2 + (shape[1] - 1)/2.0f + i*shape[3] + j);

				// This check fails for higher accuracy settings
				assertEquals(String.format("Test average frame for (%d, %d)", i, j), expected, value, 1e-6*expected);
			}
	}
	
	@AfterClass
	public static void removeTmpFiles() throws Exception {
		//Clear scratch directory 
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
	}
}
