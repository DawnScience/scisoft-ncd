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
import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.TestUtils;
import uk.ac.diamond.scisoft.analysis.dataset.Dataset;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;
import uk.ac.diamond.scisoft.ncd.rcp.utils.NcdNexusUtilsTest;
import uk.ac.diamond.scisoft.ncd.reduction.LazyAverage;
import uk.ac.diamond.scisoft.ncd.reduction.LazyBackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.reduction.LazyDetectorResponse;
import uk.ac.diamond.scisoft.ncd.reduction.LazyInvariant;
import uk.ac.diamond.scisoft.ncd.reduction.LazyNormalisation;
import uk.ac.diamond.scisoft.ncd.reduction.LazySectorIntegration;

public class NcdLazyDataReductionTest {

	private static final String NXEntryClassName = "NXentry";
	private static final String NXInstrumentClassName = "NXinstrument";
	private static final String NXDataClassName = "NXdata";
	private static final String NXDetectorClassName = "NXdetector";
	private static String testScratchDirectoryName;
	private static String filename, bgFile, drFile, secFile;
	private static String testDatasetName = "testInput"; 
	private static String testNormName = "testNorm"; 
	
	private ILock lock = Job.getJobManager().newLock();
	
	private static Dataset data;
	private static long [] shape = new long[] {5, 3, 91, 32, 64};
	private static long [] normShape = new long[] {shape[0], shape[1], shape[2], 1};
	private static long [] invShape = new long[] {shape[0], shape[1], shape[2]};
	private static long [] imageShape = new long[] {shape[3], shape[4]};
	private static long [] bgShape = new long[] {4, 13, imageShape[0], imageShape[1]};
	private static float scale = 1.0f; 
	private static float scaleBg = 0.46246f;
	private static float absScale = 100.0f;
	private static int dim = 2;
	private static int points = 1;
	
	@BeforeClass
	public static void writeTestNexusFile() throws Exception {
		
		
		testScratchDirectoryName = TestUtils.generateDirectorynameFromClassname(NcdLazyDataReductionTest.class.getCanonicalName());
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
		
		for (long n: imageShape) points *= n;
		
		{
			filename = testScratchDirectoryName + "ncd_sda_test.nxs"; 

			int nxsFile = H5.H5Fcreate(filename, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			int entry_id = NcdNexusUtils.makegroup(nxsFile, "entry1", NXEntryClassName);
			NcdNexusUtils.makegroup(entry_id, "results", NXInstrumentClassName);
			int datagroup_id = NcdNexusUtils.makegroup(entry_id, testDatasetName, NXDataClassName);
			int normgroup_id = NcdNexusUtils.makegroup(entry_id, testNormName, NXDataClassName);
			int data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, shape, true, "counts");
			int errors_id = NcdNexusUtils.makedata(datagroup_id, "errors", HDF5Constants.H5T_NATIVE_DOUBLE, shape, true, "counts");
			int norm_id = NcdNexusUtils.makedata(normgroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, normShape, true, "counts");

			for (int m = 0; m < shape[0]; m++)
			  for (int n = 0; n < shape[1]; n++) {
				for (int frames = 0; frames < shape[2]; frames++) {
					float[] norm = new float[] {scale*(n+1)};
					float[] data = new float[points];
					double[] errors = new double[points];
					for (int i = 0; i < imageShape[0]; i++) {
						for (int j = 0; j < imageShape[1]; j++) {
							int idx = (int) (i*imageShape[1] + j); 
							float val = n*shape[2] + frames + i*imageShape[1] + j;
							data[idx] = val;
							errors[idx] = Math.sqrt(val);
						}
					}
					{
						long[] start = new long[] { m, n, frames, 0, 0 };
						long[] count = new long[] { 1, 1, 1, 1, 1 };
						long[] block = new long[] { 1, 1, 1, imageShape[0], imageShape[1] };
						int filespace_id = H5.H5Dget_space(data_id);
						int type_id = H5.H5Dget_type(data_id);
						int memspace_id = H5.H5Screate_simple(dim, imageShape, null);
						H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
						H5.H5Dwrite(data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, data);
						H5.H5Sclose(memspace_id);
						H5.H5Sclose(filespace_id);
						H5.H5Tclose(type_id);
						
						filespace_id = H5.H5Dget_space(errors_id);
						type_id = H5.H5Dget_type(errors_id);
						memspace_id = H5.H5Screate_simple(dim, imageShape, null);
						H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
						H5.H5Dwrite(errors_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, errors);
						H5.H5Sclose(memspace_id);
						H5.H5Sclose(filespace_id);
						H5.H5Tclose(type_id);
					}
					{
						long[] start = new long[] { m, n, frames, 0 };
						long[] count = new long[] { 1, 1, 1, 1 };
						long[] block = new long[] { 1, 1, 1, 1 };
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
			H5.H5Dclose(errors_id);
			H5.H5Gclose(datagroup_id);
			H5.H5Dclose(norm_id);
			H5.H5Gclose(normgroup_id);
			H5.H5Gclose(entry_id);
			H5.H5Fclose(nxsFile);
		}
		

		{
			bgFile = testScratchDirectoryName + "bgfile_ncd_sda_test.nxs"; 

			int nxsFile = H5.H5Fcreate(bgFile, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			int entry_id = NcdNexusUtils.makegroup(nxsFile, "entry1", NXEntryClassName);
			int datagroup_id = NcdNexusUtils.makegroup(entry_id, testDatasetName, NXDataClassName);
			int data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, bgShape, true, "counts");
			int errors_id = NcdNexusUtils.makedata(datagroup_id, "errors", HDF5Constants.H5T_NATIVE_DOUBLE, bgShape, true, "counts");

			for (int k = 0; k < bgShape[0]; k++) {
				for (int frames = 0; frames < bgShape[1]; frames++) {
					float[] data = new float[points];
					double[] errors = new double[points];
					for (int i = 0; i < imageShape[0]; i++) {
						for (int j = 0; j < imageShape[1]; j++) {
							int idx = (int) (i * imageShape[1] + j);
							data[idx] = frames + i * imageShape[1] + j;
							errors[idx] = Math.sqrt(data[idx]);
						}
					}
				{
					long[] start = new long[] { k, frames, 0, 0 };
					long[] count = new long[] { 1, 1, 1, 1 };
					long[] block = new long[] { 1, 1, imageShape[0], imageShape[1] };
					int filespace_id = H5.H5Dget_space(data_id);
					int type_id = H5.H5Dget_type(data_id);
					int memspace_id = H5.H5Screate_simple(dim, imageShape, null);
					H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
					H5.H5Dwrite(data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, data);
					H5.H5Sclose(memspace_id);
					H5.H5Sclose(filespace_id);
					H5.H5Tclose(type_id);
					
					filespace_id = H5.H5Dget_space(errors_id);
					type_id = H5.H5Dget_type(errors_id);
					memspace_id = H5.H5Screate_simple(dim, imageShape, null);
					H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
					H5.H5Dwrite(errors_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, errors);
					H5.H5Sclose(memspace_id);
					H5.H5Sclose(filespace_id);
					H5.H5Tclose(type_id);
				}
				}
			}
			H5.H5Dclose(data_id);
			H5.H5Dclose(errors_id);
			H5.H5Gclose(datagroup_id);
			H5.H5Gclose(entry_id);
			H5.H5Fclose(nxsFile);
		}
		
		{
			drFile = testScratchDirectoryName + "drfile_ncd_sda_test.nxs"; 
        
			int nxsFile = H5.H5Fcreate(drFile, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			int entry_id = NcdNexusUtils.makegroup(nxsFile, "entry1", NXEntryClassName);
			int processing_group_id = NcdNexusUtils.makegroup(entry_id, "instrument", NXInstrumentClassName);
			int datagroup_id = NcdNexusUtils.makegroup(processing_group_id, testDatasetName, NXDetectorClassName);
			int data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, imageShape, true, "counts");

			float[] data = new float[points];
			for (int i = 0; i < imageShape[0]; i++) {
				for (int j = 0; j < imageShape[1]; j++) {
					int idx = (int) (i*imageShape[1] + j); 
					float val = (float) Math.log(1.0 + idx);
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
			int entry_id = NcdNexusUtils.makegroup(nxsFile, "entry1", NXEntryClassName);
			int datagroup_id = NcdNexusUtils.makegroup(entry_id, testDatasetName, NXDataClassName);
			int data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, shape, true, "counts");

			for (int m = 0; m < shape[0]; m++) {
			  for (int n = 0; n < shape[1]; n++) {
				for (int frames = 0; frames < shape[2]; frames++) {
					float[] data = new float[points];
					for (int i = 0; i < imageShape[0]; i++) {
						for (int j = 0; j < imageShape[1]; j++) {
							int idx = (int) (i*imageShape[1] + j); 
							float val = n*shape[2] + frames + (float)Math.sqrt((i*i+j*j)/(1.0f*imageShape[1]*imageShape[1]));
							data[idx] = val;
						}
					}
					{
						long[] start = new long[] { m, n, frames, 0, 0 };
						long[] count = new long[] { 1, 1, 1, 1, 1 };
						long[] block = new long[] { 1, 1, 1, imageShape[0], imageShape[1] };
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
			}
			H5.H5Dclose(data_id);
			H5.H5Gclose(datagroup_id);
			H5.H5Gclose(entry_id);
			H5.H5Fclose(nxsFile);
		}
		
	    DataSliceIdentifiers[] ids = NcdNexusUtilsTest.readDataId(filename, testDatasetName, "data", "errors");
	    DataSliceIdentifiers data_id = ids[0];
	    DataSliceIdentifiers errors_id = ids[1];
	    SliceSettings dataSlice = new SliceSettings(shape, 0, (int) shape[0]);
	    int[] start = new int[] {0, 0, 0, 0, 0};
	    dataSlice.setStart(start);
		data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
		Dataset error = NcdNexusUtils.sliceInputData(dataSlice, errors_id);
		data.setError(error);
	}
	
	@Test
	public void testLazyNormalisation() throws HDF5Exception {
		
		LazyNormalisation lazyNormalisation = new LazyNormalisation();
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);

		lazyNormalisation.setAbsScaling((double) absScale);
		lazyNormalisation.setNormChannel(0);
		lazyNormalisation.setCalibration(testNormName);

		SliceSettings slice = new SliceSettings(shape, 0, (int) shape[0]);
		slice.setStart(new int[] {0, 0, 0, 0, 0});
		lazyNormalisation.configure(dim, shape, entry_id, processing_group_id);
		Dataset outData = lazyNormalisation.execute(dim, data, slice, lock);
		Dataset outErrors = outData.getError();
		
		for (int h = 0; h < shape[0]; h++) {
		  for (int g = 0; g < shape[1]; g++) {
			for (int k = 0; k < shape[2]; k++) {
				for (int i = 0; i < imageShape[0]; i++) {
					for (int j = 0; j < imageShape[1]; j++) {
						float value = outData.getFloat(h, g, k, i, j);
						double error = outErrors.getDouble(h, g, k, i, j);
						float expected = absScale*(g*shape[2] + k + i*imageShape[1] + j) / (scale*(g+1));
						double expectederror = absScale*(Math.sqrt((g*shape[2] + k + i*imageShape[1] + j)) / (scale*(g+1)));

						assertEquals(String.format("Test normalisation frame for (%d, %d, %d, %d, %d)", h, g, k, i, j), expected, value, 1e-6*expected);
						assertEquals(String.format("Test normalisation frame error for (%d, %d, %d, %d, %d)", h, g, k, i, j), expectederror, error, 1e-6*expectederror);
					}
				}
			}
		  }
		}
	}
	
	@Test
	public void testLazyBackgroundSubtraction() throws HDF5Exception {
		
		LazyBackgroundSubtraction lazyBackgroundSubtraction = new LazyBackgroundSubtraction();
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
			
		DataSliceIdentifiers[] ids = NcdNexusUtilsTest.readDataId(bgFile, testDatasetName, "data", "errors");
		DataSliceIdentifiers bgIds = ids[0];
		H5.H5Sget_simple_extent_dims(bgIds.dataspace_id, bgShape, null);
		int[] bgShape_int = (int[]) ConvertUtils.convert(bgShape, int[].class);
		lazyBackgroundSubtraction.setBgScale((double) scaleBg);

		SliceSettings bgSliceParams = new SliceSettings(bgShape, 0, bgShape_int[0]);
		int[] start = new int[] { 0, 0, 0, 0 };
		bgSliceParams.setStart(start);
		Dataset bgData = NcdNexusUtils.sliceInputData(bgSliceParams, bgIds);
		int bgFrames = bgShape_int[0] * bgShape_int[1];
		bgData = bgData.sum(0).sum(0).idivide(bgFrames);
		
		DataSliceIdentifiers bgErrorIds = ids[1];
		Dataset bgError = NcdNexusUtils.sliceInputData(bgSliceParams, bgErrorIds);
		bgError = bgError.ipower(2).sum(0).sum(0).idivide(bgFrames);
		bgData.setErrorBuffer(bgError);

		lazyBackgroundSubtraction.setBgFile(bgFile);
		lazyBackgroundSubtraction.setBgDetector(testDatasetName);
		lazyBackgroundSubtraction.setBgScale((double) scaleBg);
		
		SliceSettings slice = new SliceSettings(shape, 0, (int) shape[0]);
		slice.setStart(new int[] {0, 0, 0, 0, 0});
		lazyBackgroundSubtraction.configure(dim, shape, processing_group_id);
		Dataset outData = lazyBackgroundSubtraction.execute(dim, data, bgData, slice, lock);
		Dataset outErrors = outData.getError();
			
		for (int h = 0; h < shape[0]; h++)
		  for (int g = 0; g < shape[1]; g++)
			for (int k = 0; k < shape[2]; k++) {
				for (int i = 0; i < imageShape[0]; i++)
					for (int j = 0; j < imageShape[1]; j++) {
						float value = outData.getFloat(h, g, k, i, j);
						double error = outErrors.getDouble(h, g, k, i, j);
						float expected = g*shape[2] + k + (1.0f - scaleBg)*(i*imageShape[1] + j) - scaleBg*(bgShape_int[1] - 1)/2.0f;
						double expectederr = Math.sqrt(g*shape[2] + k + (1.0 + scaleBg*scaleBg)*(i*imageShape[1] + j) + scaleBg*scaleBg*(bgShape_int[1] - 1.0)/2.0);

						assertEquals(String.format("Test background subtraction frame for (%d, %d, %d, %d, %d)", h, g, k, i, j), expected, value, 1e-4*Math.abs(expected));
						assertEquals(String.format("Test background subtraction frame error for (%d, %d, %d, %d, %d)", h, g, k, i, j), expectederr, error, 1e-4*Math.abs(expectederr));
					}
			}
	}
	
	@Test
	public void testLazyDetectorResponse() throws HDF5Exception {
		
		LazyDetectorResponse lazyDetectorResponse = new LazyDetectorResponse(drFile, testDatasetName);
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
		
		lazyDetectorResponse.setDrFile(drFile);
		
		SliceSettings slice = new SliceSettings(shape, 0, (int) shape[0]);
		slice.setStart(new int[] {0, 0, 0, 0, 0});
		lazyDetectorResponse.configure(dim, shape, entry_id, processing_group_id);
		Dataset outData = lazyDetectorResponse.execute(dim, data, slice, lock);
		Dataset outErrors = outData.getError(); 
		
		for (int h = 0; h < shape[0]; h++)
 		  for (int g = 0; g < shape[1]; g++)
			for (int k = 0; k < shape[2]; k++) {
				for (int i = 0; i < imageShape[0]; i++)
					for (int j = 0; j < imageShape[1]; j++) {
						float value = outData.getFloat(h, g, k, i, j);
						double error = outErrors.getDouble(h, g, k, i, j);
						float expected = (float) ((g*shape[2] + k + i*imageShape[1] + j)*Math.log(1.0 + i*imageShape[1] + j));
						double expectederror =  Math.sqrt((g*shape[2] + k + i*imageShape[1] + j))*Math.log(1.0 + i*imageShape[1] + j);

						assertEquals(String.format("Test detector response for (%d, %d, %d, %d, %d)", h, g, k, i, j), expected, value, 1e-6*expected);
						assertEquals(String.format("Test detector response error for (%d, %d, %d, %d, %d)", h, g, k, i, j), expectederror, error, 1e-6*expectederror);
					}
			}
	}
	
	@Test
	public void testLazyInvariant() throws HDF5Exception {

		LazyInvariant lazyInvariant = new LazyInvariant();
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
    
		SliceSettings slice = new SliceSettings(invShape, 0, (int) invShape[0]);
		slice.setStart(new int[] {0, 0, 0});
		lazyInvariant.configure(dim, shape, entry_id, processing_group_id);
		Dataset outData = lazyInvariant.execute(dim, data, slice, lock);
		Dataset outErrors = outData.getError();
		for (int h = 0; h < invShape[0]; h++) {
		  for (int g = 0; g < invShape[1]; g++) {
			for (int k = 0; k < invShape[2]; k++) {
				float value = outData.getFloat(h, g, k);
				double error = outErrors.getDouble(h, g, k);
				float expected = 0.0f;
				double expectederror = 0.0;
				for (int i = 0; i < imageShape[0]; i++) {
					for (int j = 0; j < imageShape[1]; j++) { 
						expected += g*shape[2] + k + i*imageShape[1] + j;
						expectederror += g*shape[2] + k + i*imageShape[1] + j;
					}
				}
				expectederror = Math.sqrt(expectederror);
				assertEquals(String.format("Test invariant result for (%d, %d, %d)", h, g, k), expected, value, 1e-6*expected);
				assertEquals(String.format("Test invariant error result for (%d, %d, %d)", h, g, k), expectederror, error, 1e-6*expectederror);
			}
		  }
		}
	}
	
	@Test
	public void testLazySectorIntegration() throws Exception {
		
		LazySectorIntegration lazySectorIntegration = new LazySectorIntegration();
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
		SectorROI intSector = new SectorROI(0, 0, 0, imageShape[1], 0, 90);

		intSector.setClippingCompensation(true);
		lazySectorIntegration.setIntSector(intSector);

		DataSliceIdentifiers input_ids = new DataSliceIdentifiers();
		long[] lstart = new long[] { 0, 0, 0, 0, 0 };
		long[] count = new long[] { 1, 1, 1, 1, 1 };
		input_ids.setSlice(lstart, shape, count, shape);

		lazySectorIntegration.setCalculateRadial(true);
		lazySectorIntegration.setCalculateAzimuthal(true);
		lazySectorIntegration.setFast(false);
		lazySectorIntegration.configure(dim, shape, processing_group_id);
		
		SliceSettings slice = new SliceSettings(shape, 0, (int) shape[0]);
		slice.setStart(new int[] {0, 0, 0, 0, 0});
		Dataset[] outDataset = lazySectorIntegration.execute(dim, data, slice, lock);
		Dataset[] outErrors = new Dataset[] {outDataset[0].getError(), outDataset[1].getError()};
			
		intSector.setAverageArea(true);
		for (int h = 0; h < shape[0]; h++)
		  for (int g = 0; g < shape[1]; g++)
			for (int k = 0; k < shape[2]; k++) {
				int[] startImage = new int[] {h, g, k, 0, 0};
				int[] stopImage = new int[] {h + 1, g + 1, k + 1, (int) imageShape[0], (int) imageShape[1]};
				Dataset image = data.getSlice(startImage, stopImage, null).squeeze();
				Dataset errimage = ((DoubleDataset) data.getErrorBuffer()).getSlice(startImage, stopImage, null).squeeze();
				image.setErrorBuffer(errimage);
				Dataset[] intResult = ROIProfile.sector(image, null, intSector, true, true, false, null, null, true);
				for (int i = 0; i < outDataset[1].getShape()[3]; i++) {
						float value = outDataset[1].getFloat(h, g, k, i);
						double error = outErrors[1].getDouble(h, g, k, i);
						float expected = intResult[0].getFloat(i);
						double expectederror = intResult[0].getError(i);
						assertEquals(String.format("Test radial sector integration profile for frame (%d, %d, %d, %d)", h, g, k, i), expected, value, 1e-6*expected);
						assertEquals(String.format("Test radial sector integration profile error for frame (%d, %d, %d, %d)", h, g, k, i), expectederror, error, 1e-6*expectederror);
				}
				for (int i = 0; i < outDataset[0].getShape()[3]; i++) {
					float value = outDataset[0].getFloat(h, g, k, i);
					double error = outErrors[0].getDouble(h, g, k, i);
					float expected = intResult[1].getFloat(i);
					double expectederror = intResult[1].getError(i);
					assertEquals(String.format("Test azimuthal sector integration profile for frame (%d, %d, %d, %d)", h, g, k, i), expected, value, 1e-6*expected);
					assertEquals(String.format("Test azimuthal sector integration profile error for frame (%d, %d, %d, %d)", h, g, k, i), expectederror, error, 1e-6*expectederror);
				}
			}
	}
	
	@Test
	public void testLazyAverage() throws Exception {
		
		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
		
	    DataSliceIdentifiers[] ids = NcdNexusUtilsTest.readDataId(filename, testDatasetName, "data", "errors");
	    DataSliceIdentifiers input_ids = ids[0];
		long[] lstart = new long[] { 0, 0, 0, 0, 0 };
		long[] count = new long[] { 1, 1, 1, 1, 1 };
		input_ids.setSlice(lstart, shape, count, shape);
		
	    DataSliceIdentifiers input_errors_ids = ids[1];
		input_errors_ids.setSlice(lstart, shape, count, shape);
		
		LazyAverage lazyAverage = new LazyAverage();
		lazyAverage.setAverageIndices(new int[] {1,3});
		lazyAverage.configure(dim, (int[]) ConvertUtils.convert(shape, int[].class), processing_group_id, 100);
		lazyAverage.execute(input_ids, input_errors_ids);
		
	    long[] shapeRes = new long[] {1, shape[1], 1, imageShape[0], imageShape[1]}; 
		SliceSettings resultsSlice = new SliceSettings(shapeRes, 0, (int) shapeRes[0]);
	    int[] start = new int[] {0, 0, 0, 0, 0};
	    resultsSlice.setStart(start);
		Dataset outDataset = NcdNexusUtils.sliceInputData(resultsSlice, input_ids);
		Dataset outErrors = NcdNexusUtils.sliceInputData(resultsSlice, input_errors_ids);
		Dataset dataErrors = data.getErrorBuffer(); 
				
		for (int k = 0; k < shape[1]; k++) {
		  for (int i = 0; i < imageShape[0]; i++) {
			for (int j = 0; j < imageShape[1]; j++) {
				start = new int[] {0, k, 0, i, j};
				int[] stop = new int[] {(int) shape[0], k + 1, (int) shape[2], i + 1 , j + 1};
				Dataset dataSlice = data.getSlice(start, stop, null);
				Dataset errorsSlice = dataErrors.getSlice(start, stop, null);
				double value = outDataset.getDouble(0, k, 0, i, j);
				double errors = outErrors.getDouble(0, k, 0, i, j);
				double expected = (Double) dataSlice.sum() / (shape[0] * shape[2]);
				double expectederrors = Math.sqrt((Double) errorsSlice.sum()) / (shape[0] * shape[2]);

				// This check fails for higher accuracy settings
				assertEquals(String.format("Test average frame for (%d, %d, %d)", k, i, j), expected, value, 1e-6*expected);
				assertEquals(String.format("Test average frame errors for (%d, %d, %d)", k, i, j), expectederrors, errors, 1e-6*expectederrors);
			}
		  }
		}
	}
	
	@AfterClass
	public static void removeTmpFiles() throws Exception {
		//Clear scratch directory 
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
	}
}
