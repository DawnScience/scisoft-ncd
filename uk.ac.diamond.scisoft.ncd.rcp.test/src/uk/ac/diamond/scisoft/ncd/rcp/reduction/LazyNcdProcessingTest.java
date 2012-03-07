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

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusExtractorException;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeBuilder;
import gda.util.TestUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import junit.framework.Assert;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nexusformat.NexusException;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationResultsBean;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.reduction.LazyAverage;
import uk.ac.diamond.scisoft.ncd.reduction.LazyBackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.reduction.LazyDetectorResponse;
import uk.ac.diamond.scisoft.ncd.reduction.LazyInvariant;
import uk.ac.diamond.scisoft.ncd.reduction.LazyNcdProcessing;
import uk.ac.diamond.scisoft.ncd.reduction.LazyNormalisation;
import uk.ac.diamond.scisoft.ncd.reduction.LazySectorIntegration;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class LazyNcdProcessingTest {

	static LazyNcdProcessing testClass;
	private static String bgFile;
	private static Double bgScaling = 0.1;
	private static Double absScaling = 1.0;
	private static int normChannel = 1;
	private static CalibrationResultsBean crb = null;
	private static String drFile;
	private static boolean enableMask = false;
	private static NcdReductionFlags flags;
	private static SectorROI intSector;
	private static int intPoints;
	private static BooleanDataset mask;
	private static NcdDetectors ncdDetectors;

	private static String detector = "Rapid2D";
	private static String detectorOut = "Rapid2D_processing";
	private static String detectorBg = "Rapid2D_background";
	private static String calibration = "Scalers";
	private static Double pxSaxs = 0.1;
	private static int dim = 2;
	private static String filename;
	private static String testScratchDirectoryName;
	private static Integer firstFrame = 60;
	private static Integer lastFrame = 70;
	private static Integer bgFirstFrame = 5;
	private static Integer bgLastFrame = 5;
	//private static String frameSelection = "0;60-70";
	private static String bgFrameSelection = "0;" + bgFirstFrame.toString() + "-" + bgLastFrame.toString();
	
	private static AbstractDataset dr;
	
	private static long[] frames = new long[] {1, 120, 512, 512};
	private static long[] drFrames = new long[] {1, 1, 512, 512};
	private static long[] bgSelFrames = new long[] {1, bgLastFrame - bgFirstFrame + 1, 512, 512};
	private static long[] framesCal = new long[] {1, 120, 9};
	private static long[] framesResult = frames;  // TODO: Change after data slicing is supported
	//private static long[] framesResult = new long[] {1, lastFrame - firstFrame + 1, 512, 512};
	private static long[] framesSec, framesAve, framesBgSec;
	
	@BeforeClass
	public static void initLazyNcdProcessing() throws Exception {

		testScratchDirectoryName = TestUtils.generateDirectorynameFromClassname(LazyNcdProcessingTest.class.getCanonicalName());
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
		filename = testScratchDirectoryName + "ncd_processing_test.nxs"; 

		String testFileFolder = TestUtils.getGDALargeTestFilesLocation();
		if( testFileFolder == null){
			Assert.fail("TestUtils.getGDALargeTestFilesLocation() returned null - test aborted");
		}

		Path bgPath = new Path(testFileFolder + "/NCDReductionTest/i22-24132.nxs");
		Path drPath = new Path(testFileFolder + "/NCDReductionTest/i22-24125.nxs");
		Path inputPath = new Path(testFileFolder + "/NCDReductionTest/i22-24139.nxs");

		FileInputStream inFile = new FileInputStream(inputPath.toOSString());
		FileOutputStream outFile = new FileOutputStream(filename);

		IOUtils.copy(inFile, outFile);

		bgFile = bgPath.toOSString();
		drFile = drPath.toOSString();

		flags = new NcdReductionFlags();
		flags.setEnableNormalisation(true);
		flags.setEnableBackground(true);
		flags.setEnableDetectorResponse(true);
		flags.setEnableSector(true);
		flags.setEnableInvariant(true);
		flags.setEnableAverage(true);
		flags.setEnableSaxs(true);
		flags.setEnableWaxs(false);

		ncdDetectors = new NcdDetectors();
		ncdDetectors.setDetectorSaxs(detector);
		ncdDetectors.setDetectorWaxs(null);
		ncdDetectors.setPxSaxs(pxSaxs);
		ncdDetectors.setPxWaxs(null);

		intSector = new SectorROI(262.0, 11.0, 20.0, 500.0, 60.0, 120.0);
		intPoints = intSector.getIntRadius(1) - intSector.getIntRadius(0);
		framesSec = new long[] {1, frames[1], intPoints}; //TODO: update when data slicing is supported
		framesAve = new long[] {1, 1, intPoints}; //TODO: update when data slicing is supported
		framesBgSec = new long[] {1, bgSelFrames[1], intPoints}; //TODO: update when data slicing is supported

		testClass = new LazyNcdProcessing();
		testClass.setBgFile(bgFile);
		testClass.setDrFile(drFile);
		testClass.setAbsScaling(absScaling);
		testClass.setBgScaling(bgScaling);
		testClass.setFirstFrame(firstFrame);
		testClass.setLastFrame(lastFrame);
		//testClass.setBgFirstFrame(bgFirstFrame);
		//testClass.setBgLastFrame(bgLastFrame);
		//testClass.setFrameSelection(frameSelection);
		testClass.setBgFrameSelection(bgFrameSelection);
		testClass.setCalibration(calibration);
		testClass.setNormChannel(normChannel);
		testClass.setCrb(crb);
		testClass.setEnableMask(enableMask);
		testClass.setFlags(flags);
		testClass.setIntSector(intSector);
		testClass.setMask(mask);
		testClass.setNcdDetectors(ncdDetectors);
		testClass.setFirstFrame(firstFrame);
		testClass.setLastFrame(lastFrame);

	    DataSliceIdentifiers dr_id = NcdNexusUtils.readDataId(drFile, detector);
	    SliceSettings drSlice = new SliceSettings(drFrames, 1, 1);
	    int[] start = new int[] {0, 0, 0, 0};
	    drSlice.setStart(start);
		dr = NcdNexusUtils.sliceInputData(drSlice, dr_id);
		
		testClass.executeHDF5(detector, dim, filename, new NullProgressMonitor());
	}

	@Test
	public void checkDetectorResponse() throws HDF5Exception {

	    DataSliceIdentifiers data_id = NcdNexusUtils.readDataId(filename, detector);
	    SliceSettings dataSlice = new SliceSettings(frames, 1, lastFrame - firstFrame + 1);
	    int[] start = new int[] {0, firstFrame, 0, 0};
	    dataSlice.setStart(start);
		AbstractDataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
		
	    DataSliceIdentifiers result_id = readResultsId(filename, detectorOut, LazyDetectorResponse.name);
	    SliceSettings resultSlice = new SliceSettings(framesResult, 1, lastFrame - firstFrame + 1);
	    start = new int[] {0, firstFrame, 0, 0};  //TODO: should be starting from 0 one data slicing is supported
	    resultSlice.setStart(start);
		AbstractDataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
			for (int i = 0; i < 512; i++)
				for (int j = 0; j < 512; j++) {
					float valResult = result.getFloat(new int[] {0, frame, i, j});
					float valData = data.getFloat(new int[] {0, frame, i, j});
					float valDr = dr.getFloat(new int[] {0, 0, i, j}); 
					double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);
					
					assertEquals(String.format("Test detector response for pixel (%d, %d, %d)", frame, i, j), valData*valDr, valResult, acc);
				}
		}
	}
	
	@Test
	public void checkSectorIntegration() throws HDF5Exception {

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
		    DataSliceIdentifiers data_id = readResultsId(filename, detectorOut, LazyDetectorResponse.name);
		    SliceSettings dataSlice = new SliceSettings(frames, 1, 1);
		    int[] start = new int[] {0, frame, 0, 0};
		    dataSlice.setStart(start);
			AbstractDataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
			
		    DataSliceIdentifiers result_id = readResultsId(filename, detectorOut, LazySectorIntegration.name);
		    SliceSettings resultSlice = new SliceSettings(framesSec, 1, 1);
		    start = new int[] {0, frame, 0};
		    resultSlice.setStart(start);
			AbstractDataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);

			AbstractDataset[] intResult = ROIProfile.sector(data.squeeze(), null, intSector);

			for (int j = 0; j < intPoints; j++) {
				float valResult = result.getFloat(new int[] {0, 0, j});
				float valData = intResult[0].getFloat(new int[] {j});
				double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);

				assertEquals(String.format("Test sector integration for index (%d, %d)", frame, j), valResult, valData, acc);
			}
		}
	}
	
	@Test
	public void checkNormalisation() throws HDF5Exception {

	    DataSliceIdentifiers data_id = readResultsId(filename, detectorOut, LazySectorIntegration.name);
	    SliceSettings dataSlice = new SliceSettings(framesSec, 1, lastFrame - firstFrame + 1);
	    int[] start = new int[] {0, firstFrame, 0};
	    dataSlice.setStart(start);
		AbstractDataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
	    
	    DataSliceIdentifiers norm_id = NcdNexusUtils.readDataId(filename, calibration);
	    SliceSettings normSlice = new SliceSettings(framesCal, 1, lastFrame - firstFrame + 1);
	    normSlice.setStart(start);
		AbstractDataset norm = NcdNexusUtils.sliceInputData(normSlice, norm_id);
		
	    DataSliceIdentifiers result_id = readResultsId(filename, detectorOut, LazyNormalisation.name);
	    SliceSettings resultSlice = new SliceSettings(framesSec, 1, lastFrame - firstFrame + 1);
	    resultSlice.setStart(start);
		AbstractDataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
			float valNorm = norm.getFloat(new int[] {0, frame, normChannel}); 
			for (int i = 0; i < intPoints; i++) {
				float valResult = result.getFloat(new int[] {0, frame, i});
				float valData = data.getFloat(new int[] {0, frame, i});
				float testResult = (float) (valData * absScaling / valNorm);

				assertEquals(String.format("Test normalisation for pixel (%d, %d)", frame, i), testResult, valResult, 1e-6*valResult);
			}
		}
	}

	@Test
	public void checkBackgroundSubtractionData() throws HDF5Exception {

	    DataSliceIdentifiers bg_id = NcdNexusUtils.readDataId(bgFile, detector);
	    SliceSettings bgSlice = new SliceSettings(frames, 1, bgLastFrame - bgFirstFrame + 1);
	    int[] start = new int[] {0, bgFirstFrame, 0, 0};
	    bgSlice.setStart(start);
		AbstractDataset bgData = NcdNexusUtils.sliceInputData(bgSlice, bg_id);

	    DataSliceIdentifiers bgdr_id = readResultsId(filename, detectorBg, LazyDetectorResponse.name);
	    bgSlice = new SliceSettings(bgSelFrames, 1, bgLastFrame - bgFirstFrame + 1);
	    start = new int[] {0, 0, 0, 0};
	    bgSlice.setStart(start);
		AbstractDataset bgDrData = NcdNexusUtils.sliceInputData(bgSlice, bgdr_id);
		
		for (int frame = 0; frame <= bgLastFrame - bgFirstFrame; frame++) {
			for (int i = 0; i < 512; i++)
				for (int j = 0; j < 512; j++) {
					float valResult = bgDrData.getFloat(new int[] {0, frame, i, j});
					float valData = bgData.getFloat(new int[] {0, frame, i, j});
					float valDr = dr.getFloat(new int[] {0, 0, i, j}); 
					double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);
					
					assertEquals(String.format("Test bakground detector response for pixel (%d, %d, %d)", frame, i, j), valData*valDr, valResult, acc);
				}
		}
		
		for (int frame = 0; frame <= bgLastFrame - bgFirstFrame; frame++) {
		    bg_id = readResultsId(filename, detectorBg, LazyDetectorResponse.name);
		    SliceSettings dataSlice = new SliceSettings(bgSelFrames, 1, 1);
		    start = new int[] {0, frame, 0, 0};
		    dataSlice.setStart(start);
			AbstractDataset data = NcdNexusUtils.sliceInputData(dataSlice, bg_id);
			
		    DataSliceIdentifiers result_id = readResultsId(filename, detectorBg, LazySectorIntegration.name);
		    SliceSettings resultSlice = new SliceSettings(framesBgSec, 1, 1);
		    start = new int[] {0, frame, 0};
		    resultSlice.setStart(start);
			AbstractDataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);

			AbstractDataset[] intResult = ROIProfile.sector(data.squeeze(), null, intSector);

			for (int j = 0; j < intPoints; j++) {
				float valResult = result.getFloat(new int[] {0, 0, j});
				float valData = intResult[0].getFloat(new int[] {j});
				double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);

				assertEquals(String.format("Test background sector integration for index (%d, %d)", frame, j), valResult, valData, acc);
			}
		}
		
	    bg_id = readResultsId(filename, detectorBg, LazySectorIntegration.name);
	    SliceSettings dataSlice = new SliceSettings(framesBgSec, 1, bgLastFrame - bgFirstFrame + 1);
	    start = new int[] {0, 0, 0};
	    dataSlice.setStart(start);
		AbstractDataset data = NcdNexusUtils.sliceInputData(dataSlice, bg_id);
		
	    DataSliceIdentifiers norm_id = NcdNexusUtils.readDataId(bgFile, calibration);
	    SliceSettings normSlice = new SliceSettings(framesCal, 1, bgLastFrame - bgFirstFrame + 1);
	    start = new int[] {0, bgFirstFrame, 0};
	    normSlice.setStart(start);
		AbstractDataset norm = NcdNexusUtils.sliceInputData(normSlice, norm_id);
		
	    DataSliceIdentifiers result_id = readResultsId(filename, detectorBg, LazyNormalisation.name);
	    SliceSettings resultSlice = new SliceSettings(framesBgSec, 1, bgLastFrame - bgFirstFrame + 1);
	    start = new int[] {0, 0, 0};
	    resultSlice.setStart(start);
		AbstractDataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);

		for (int frame = 0; frame <= bgLastFrame - bgFirstFrame; frame++) {
			float valNorm = norm.getFloat(new int[] {0, frame, normChannel}); 
			for (int i = 0; i < intPoints; i++) {
				float valResult = result.getFloat(new int[] {0, frame, i});
				float valData = data.getFloat(new int[] {0, frame, i});
				float testResult = (float) (valData * absScaling / valNorm);

				assertEquals(String.format("Test background normalisation for pixel (%d, %d)", frame, i), testResult, valResult, 1e-6*valResult);
			}
		}
		
	    bg_id = readResultsId(filename, detectorBg, LazyNormalisation.name);
		data = NcdNexusUtils.sliceInputData(dataSlice, bg_id);
		
	    result_id = readResultsId(filename, detectorBg, LazyAverage.name);
	    dataSlice = new SliceSettings(framesAve, 1, 1);
	    dataSlice.setStart(start);
		result = NcdNexusUtils.sliceInputData(dataSlice, result_id);

		for (int i = 0; i < intPoints; i++) {
			float valData = 0.0f;
			for (int frame = 0; frame <= bgLastFrame - bgFirstFrame; frame++) {
				valData += data.getFloat(new int[] { 0, frame, i }) / (bgLastFrame - bgFirstFrame + 1);
			}
			float valResult = result.getFloat(new int[] { 0, 0, i });
			double acc = Math.max(1e-6 * Math.abs(Math.sqrt(valResult * valResult + valData * valData)), 1e-10);

			assertEquals(String.format("Test bakground detector response for pixel (%d)", i),
					valData, valResult, acc);
		}

	}
	
	//@Test
	public void checkBackgroundSubtraction() throws HDF5Exception {

	    DataSliceIdentifiers data_id = readResultsId(filename, detector, LazyNormalisation.name);
	    SliceSettings dataSlice = new SliceSettings(framesSec, 1, lastFrame - firstFrame + 1);
	    int[] start = new int[] {0, firstFrame, 0};
	    dataSlice.setStart(start);
		AbstractDataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
	    
	    DataSliceIdentifiers result_id = readResultsId(filename, detector, LazyBackgroundSubtraction.name);
	    SliceSettings resultSlice = new SliceSettings(framesSec, 1, lastFrame - firstFrame + 1);
	    resultSlice.setStart(start);
		AbstractDataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);

	    DataSliceIdentifiers bg_id = NcdNexusUtils.readDataId(bgFile, detector);
	    SliceSettings bgSlice = new SliceSettings(frames, 1, bgLastFrame - bgFirstFrame + 1);
	    start = new int[] {0, bgFirstFrame, 0};
	    bgSlice.setStart(start);
		AbstractDataset bgData = NcdNexusUtils.sliceInputData(bgSlice, bg_id);

	    DataSliceIdentifiers bgdr_id = readResultsId(filename, detectorBg, LazyDetectorResponse.name);
		AbstractDataset bgDrData = NcdNexusUtils.sliceInputData(bgSlice, bgdr_id);
		
	    DataSliceIdentifiers bgsec_id = readResultsId(filename, detectorBg, LazySectorIntegration.name);
	    bgSlice = new SliceSettings(frames, 1, bgLastFrame - bgFirstFrame + 1);
	    start = new int[] {0, bgFirstFrame, 0};
	    bgSlice.setStart(start);
		AbstractDataset bgSecData = NcdNexusUtils.sliceInputData(bgSlice, bgsec_id);

/*		detectorTree = NexusTreeBuilder.getNexusTree(bgFile, NcdDataUtils.getDetectorSelection(detector, calibration));
		tmpNXdata = detectorTree.getNode("entry1/instrument");

		int[] startData = new int[] {0, bgFirstFrame, 0, 0};
		int[] stopData = new int[] {1, bgLastFrame + 1, 512, 512};
		tmpData = NcdDataUtils.selectNAxisFrames(detector, calibration, tmpNXdata, dim + 1, startData, stopData);
		AbstractDataset bg = Nexus.createDataset(NcdDataUtils.getData(tmpData, detector, "data", NexusExtractor.SDSClassName), false);
		AbstractDataset norm = Nexus.createDataset(NcdDataUtils.getData(tmpData, calibration, "data", NexusExtractor.SDSClassName), false);

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
			for (int i = 0; i < 512; i++)
				for (int j = 0; j < 512; j++) {
					float valResult = result.getFloat(new int[] {frame, i, j});
					float valData = data.getFloat(new int[] {frame, i, j});
					int bgFrame = frame % (bgLastFrame - bgFirstFrame + 1);
					float bgNorm = norm.getFloat(new int[] {bgFrame, normChannel}); 
					float valBg = (float) (bg.getFloat(new int[] {bgFrame, i, j})*absScaling/bgNorm);
					double testData = valResult + bgScaling*valBg;
					double acc = Math.max(1e-6*Math.abs(Math.sqrt(testData*testData + valData*valData)), 1e-10);
					
					assertEquals(String.format("Test background subtraction for pixel (%d, %d, %d)", frame, i, j), testData, valData, acc);
				}
		}
*/	}
	
	//@Test
	public void checkInvariant() throws NexusException, NexusExtractorException, Exception {

		int[] start = new int[] {0, 0, 0, 0};
		int[] stop = new int[] {1, lastFrame - firstFrame + 1, 512, 512};
			INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyDetectorResponse.name, null));
			INexusTree tmpNXdata = detectorTree.getNode("entry1/Rapid2D_processing");

			INexusTree tmpData = NcdDataUtils.selectNAxisFrames(LazyDetectorResponse.name, null, tmpNXdata, dim + 1, start, stop);
			AbstractDataset data = Nexus.createDataset(NcdDataUtils.getData(tmpData, LazyDetectorResponse.name, "data", NexusExtractor.SDSClassName), false);

			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyInvariant.name, null));
			tmpNXdata = detectorTree.getNode("entry1/Rapid2D_processing");
			int[] startData = new int[] {0, 0};
			int[] stopData = new int[] {1, lastFrame - firstFrame + 1};
			INexusTree tmpResult = NcdDataUtils.selectNAxisFrames(LazyInvariant.name, null, tmpNXdata, 1, startData, stopData);
			AbstractDataset result = Nexus.createDataset(NcdDataUtils.getData(tmpResult, LazyInvariant.name, "data", NexusExtractor.SDSClassName), false);

			for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
				float valResult = result.getFloat(new int[] {frame});
				float valData = 0.0f;
				for (int i = 0; i < 512; i++)
					for (int j = 0; j < 512; j++)
						valData += data.getFloat(new int[] {frame, i, j});
				double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);
				
				assertEquals(String.format("Test invariant for frame %d", frame), valResult, valData, acc);
			}
	}
	
	//@Test
	public void checkAverage() throws NexusException, NexusExtractorException, Exception {

		int[] start = new int[] {0, 0, 0};
		int[] stop = new int[] {1, lastFrame - firstFrame + 1, intPoints};
			INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazySectorIntegration.name, null));
			INexusTree tmpNXdata = detectorTree.getNode("entry1/Rapid2D_processing");

			INexusTree tmpData = NcdDataUtils.selectNAxisFrames(LazySectorIntegration.name, null, tmpNXdata, dim, start, stop);
			AbstractDataset data = Nexus.createDataset(NcdDataUtils.getData(tmpData, LazySectorIntegration.name, "data", NexusExtractor.SDSClassName), false);

			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyAverage.name, null));
			tmpNXdata = detectorTree.getNode("entry1/Rapid2D_processing");
			int[] startData = new int[] {0, 0, 0};
			int[] stopData = new int[] {1, 1, intPoints};
			INexusTree tmpResult = NcdDataUtils.selectNAxisFrames(LazyAverage.name, null, tmpNXdata, dim-1, startData, stopData);
			AbstractDataset result = Nexus.createDataset(NcdDataUtils.getData(tmpResult, LazyAverage.name, "data", NexusExtractor.SDSClassName), false);

			for (int j = 0; j < intSector.getIntRadius(1) - intSector.getIntRadius(0); j++) {
				float valData = 0.0f;
				float valResult = result.getFloat(new int[] {j});
				for (int frame = 0; frame <= lastFrame - firstFrame; frame++)
					valData += data.getFloat(new int[] {frame,j});
				valData /= lastFrame - firstFrame + 1;
				double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);
				assertEquals(String.format("Test average for index %d", j), valResult, valData, acc);
			}
	}
	
	@AfterClass
	public static void removeTmpFiles() throws Exception {
		//Clear scratch directory 
		//TestUtils.makeScratchDirectory(testScratchDirectoryName);
	}
	
	private static DataSliceIdentifiers readResultsId(String dataFile, String detector, String result) throws HDF5Exception {
		int file_handle = H5.H5Fopen(dataFile, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		int entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		int instrument_group_id = H5.H5Gopen(entry_group_id, detector, HDF5Constants.H5P_DEFAULT);
		int detector_group_id = H5.H5Gopen(instrument_group_id, result, HDF5Constants.H5P_DEFAULT);
		int input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		
		DataSliceIdentifiers ids = new DataSliceIdentifiers();
		ids.setIDs(detector_group_id, input_data_id);
		return ids;
	}
	
}
