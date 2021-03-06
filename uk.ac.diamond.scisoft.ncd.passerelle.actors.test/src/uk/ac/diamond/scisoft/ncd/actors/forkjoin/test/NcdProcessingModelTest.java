/*
 * Copyright 2014 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.actors.forkjoin.test;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.io.IOUtils;
import org.dawb.passerelle.common.PersistenceServiceHolder;
import org.dawnsci.persistence.PersistenceServiceCreator;
import org.dawnsci.plotting.tools.preference.detector.DiffractionDetector;
import org.eclipse.core.runtime.Path;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.hdf5.HDF5Utils;
import org.eclipse.january.dataset.BooleanDataset;
import org.eclipse.january.dataset.Dataset;
import org.jscience.physics.amount.Amount;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import uk.ac.diamond.scisoft.analysis.IOTestUtils;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.data.plots.SaxsPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStatsParameters;
import uk.ac.diamond.scisoft.ncd.core.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.NcdProcessingModel;

public class NcdProcessingModelTest {

	private static final String LazyDetectorResponseName = "DetectorResponse";
	private static final String LazySectorIntegrationName = "SectorIntegration";
	private static final String LazyNormalisationName = "Normalisation";
	private static final String LazyBackgroundSubtractionName = "BackgroundSubtraction";
	private static final String LazyInvariantName = "Invariant";
	private static final String LazyAverageName = "Average";
	
	private static NcdProcessingModel testClass, testbgClass;
	private static Double bgScaling = 0.1;
	private static Double absScaling = 1.0;
	private static int normChannel = 1;
	private static String drFile;
	private static boolean enableMask = false;
	private static NcdReductionFlags flags;
	private static SectorROI intSector;
	private static int intPoints, azPoints;
	private static BooleanDataset mask;

	private static String detector = "Rapid2D";
	private static String detectorOut = "Rapid2D_processing";
	private static String detectorBg = "Rapid2D_result";
	private static String calibration = "Scalers";
	private static Amount<Length> pxSaxs = Amount.valueOf(0.1, SI.MILLIMETER);
	private static String filename, bgFilename;
	private static String testScratchDirectoryName;
	private static Integer firstFrame = 60;
	private static Integer lastFrame = 70;
	//private static String frameSelection = "0;60-70";
	private static Integer bgFirstFrame = 5;
	private static Integer bgLastFrame = 5;
	private static String bgFrameSelection = "0;" + bgFirstFrame.toString() + "-" + bgLastFrame.toString();
	
	private static Dataset dr;
	
	private static long[] frames = new long[] {1, 120, 512, 512};
	private static long[] drFrames = new long[] {1, 1, 512, 512};
	private static long[] framesCal = new long[] {1, 120, 9};
	private static long[] framesResult = new long[] {1, lastFrame - firstFrame + 1, 512, 512};
	private static long[] framesSec, framesSecAz, framesAve, framesBg, framesInv;
	
	private static Unit<ScatteringVector> axisUnit = NonSI.ANGSTROM.inverse().asType(ScatteringVector.class);
	private static Amount<ScatteringVectorOverDistance> gradient = Amount.valueOf(0.1,
			axisUnit.divide(SI.MILLIMETER).asType(ScatteringVectorOverDistance.class));
	private static Amount<ScatteringVector> intercept = Amount.valueOf(0.2, axisUnit);
	private static Amount<Length> meanCameraLength = Amount.valueOf(3.5, SI.METER);

	private static CalibrationResultsBean crb = new CalibrationResultsBean(detector,
			gradient,
			intercept,
			null,
			meanCameraLength,
			axisUnit.inverse().asType(Length.class));
	
	private static SaxsAnalysisStatsParameters saxsAnalysisStatParams = new SaxsAnalysisStatsParameters();
	
	@BeforeClass
	public static void setUp() throws Exception {

		// This is required for ROIParameter class to work		
		PersistenceServiceHolder.getInstance().setPersistenceService(PersistenceServiceCreator.createPersistenceService());		
		
		testScratchDirectoryName = IOTestUtils.generateDirectorynameFromClassname(NcdProcessingModelTest.class.getCanonicalName());
		IOTestUtils.makeScratchDirectory(testScratchDirectoryName);
		filename = testScratchDirectoryName + "ncd_processing_test.nxs"; 
		bgFilename = testScratchDirectoryName + "ncd_bg_test.nxs"; 

		String testFileFolder = IOTestUtils.getGDALargeTestFilesLocation();

		Path bgPath = new Path(testFileFolder + "NCDReductionTest/i22-24132.nxs");
		Path drPath = new Path(testFileFolder + "NCDReductionTest/i22-24125.nxs");
		Path inputPath = new Path(testFileFolder + "NCDReductionTest/i22-24139.nxs");

		FileInputStream inFile = new FileInputStream(inputPath.toOSString());
		FileOutputStream outFile = new FileOutputStream(filename);

		IOUtils.copy(inFile, outFile);

		inFile = new FileInputStream(bgPath.toOSString());
		outFile = new FileOutputStream(bgFilename);

		IOUtils.copy(inFile, outFile);
		
		drFile = drPath.toOSString();

		flags = new NcdReductionFlags();
		flags.setEnableNormalisation(true);
		flags.setEnableBackground(true);
		flags.setEnableDetectorResponse(true);
		flags.setEnableSector(true);
		flags.setEnableRadial(true);
		flags.setEnableAzimuthal(true);
		flags.setEnableFastintegration(false);
		flags.setEnableInvariant(true);
		flags.setEnableAverage(true);
		flags.setEnableSaxs(true);
		flags.setEnableWaxs(false);
		flags.setEnableLogLogPlot(true);
		flags.setEnableGuinierPlot(true);
		flags.setEnablePorodPlot(true);
		flags.setEnableKratkyPlot(true);
		flags.setEnableZimmPlot(true);
		flags.setEnableDebyeBuechePlot(true);

		DiffractionDetector ncdDetector = new DiffractionDetector();
		ncdDetector.setDetectorName(detector);
		ncdDetector.setxPixelSize(pxSaxs);
		ncdDetector.setyPixelSize(pxSaxs);

		intSector = new SectorROI(262.0, 11.0, 20.0, 500.0,  Math.toRadians(60.0), Math.toRadians(120.0));
		intPoints = intSector.getIntRadius(1) - intSector.getIntRadius(0);
		azPoints = (int) Math.ceil((intSector.getAngle(1) - intSector.getAngle(0)) * intSector.getRadius(1));
		framesSec = new long[] {1, lastFrame - firstFrame + 1, intPoints};
		framesSecAz = new long[] {1, lastFrame - firstFrame + 1, azPoints};
		framesInv = new long[] {1,  lastFrame - firstFrame + 1};
		framesAve = new long[] {1, 1, intPoints};
		framesBg = new long[] {1, 1, intPoints};

		testClass = new NcdProcessingModel();
		testClass.setBgFile(bgFilename);
		testClass.setDrFile(drFile);
		testClass.setAbsScaling(absScaling);
		testClass.setBgDetector(detectorBg);
		testClass.setBgScaling(bgScaling);
		testClass.setFirstFrame(firstFrame);
		testClass.setLastFrame(lastFrame);
		testClass.setCalibration(calibration);
		testClass.setNormChannel(normChannel);
		testClass.setCrb(crb);
		testClass.setEnableMask(enableMask);
		testClass.setFlags(flags);
		testClass.setIntSector(intSector);
		testClass.setMask(mask);
		testClass.setNcdDetector(ncdDetector);
		testClass.setSaxsAnalysisStatsParameters(saxsAnalysisStatParams);

		testbgClass = new NcdProcessingModel();
		testbgClass.setDrFile(drFile);
		testbgClass.setAbsScaling(absScaling);
		testbgClass.setFirstFrame(bgFirstFrame);
		testbgClass.setLastFrame(bgLastFrame);
		testbgClass.setFrameSelection(bgFrameSelection);
		testbgClass.setCalibration(calibration);
		testbgClass.setNormChannel(normChannel);
		testbgClass.setCrb(crb);
		testbgClass.setEnableMask(enableMask);
		testbgClass.setSaxsAnalysisStatsParameters(saxsAnalysisStatParams);
		
		flags.setEnableBackground(false);
		flags.setEnableInvariant(false);
		testbgClass.setFlags(flags);
		
		testbgClass.setIntSector(intSector);
		testbgClass.setMask(mask);
		testbgClass.setNcdDetector(ncdDetector);
		
	    DataSliceIdentifiers dr_id = readDataId(drFile, detector, "data", null)[0];
	    SliceSettings drSlice = new SliceSettings(drFrames, 1, 1);
	    int[] start = new int[] {0, 0, 0, 0};
	    drSlice.setStart(start);
		dr = NcdNexusUtils.sliceInputData(drSlice, dr_id);
		
		testbgClass.execute(bgFilename, null);
		testClass.execute(filename, null);
	}

	@Test
	public void checkDetectorResponse() throws HDF5Exception {

	    DataSliceIdentifiers data_id = readDataId(filename, detector, "data", null)[0];
	    SliceSettings dataSlice = new SliceSettings(frames, 1, lastFrame - firstFrame + 1);
	    int[] start = new int[] {0, firstFrame, 0, 0};
	    dataSlice.setStart(start);
		Dataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
		
	    DataSliceIdentifiers[] array_id = readResultsIds(filename, detectorOut, LazyDetectorResponseName);
	    DataSliceIdentifiers result_id = array_id[0];
	    DataSliceIdentifiers result_error_id = array_id[1];
	    SliceSettings resultSlice = new SliceSettings(framesResult, 1, lastFrame - firstFrame + 1);
	    start = new int[] {0, 0, 0, 0};
	    resultSlice.setStart(start);
		Dataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);
		Dataset resultErrors = NcdNexusUtils.sliceInputData(resultSlice, result_error_id);

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
			for (int i = 0; i < 512; i++) {
				for (int j = 0; j < 512; j++) {
					float valResult = result.getFloat(0, frame, i, j);
					double valResultErrors = resultErrors.getDouble(0, frame, i, j);
					float valData = data.getFloat(0, frame, i, j);
					double valInputErrors = Math.sqrt(valData);
					float valDr = dr.getFloat(0, 0, i, j); 
					double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);
					double accerr = Math.max(1e-6*Math.abs(Math.sqrt(valResultErrors*valResultErrors + valInputErrors*valInputErrors)), 1e-10);
					
					assertEquals(String.format("Test detector response for pixel (%d, %d, %d)", frame, i, j), valData*valDr, valResult, acc);
					assertEquals(String.format("Test detector response errors for pixel (%d, %d, %d)", frame, i, j), valInputErrors*valDr, valResultErrors, accerr);
				}
			}
		}
	}
	
	@Test
	public void checkSectorIntegration() throws HDF5Exception {

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
		    DataSliceIdentifiers[] ids = readResultsIds(filename, detectorOut, LazyDetectorResponseName);
		    DataSliceIdentifiers data_id = ids[0];
		    DataSliceIdentifiers input_errors_id = ids[1];
		    SliceSettings dataSlice = new SliceSettings(framesResult, 1, 1);
		    int[] start = new int[] {0, frame, 0, 0};
		    dataSlice.setStart(start);
			Dataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id).squeeze();
			Dataset dataErrors = NcdNexusUtils.sliceInputData(dataSlice, input_errors_id).squeeze();
			data.setErrors(dataErrors);
			
			intSector.setAverageArea(true);
			Dataset[] intResult = ROIProfile.sector(data, null, intSector, true, true, false, null, null, true);

		    DataSliceIdentifiers[] array_id = readResultsIds(filename, detectorOut, LazySectorIntegrationName);
		    DataSliceIdentifiers result_id = array_id[0];
		    DataSliceIdentifiers result_error_id = array_id[1];
		    SliceSettings resultSlice = new SliceSettings(framesSec, 1, 1);
		    start = new int[] {0, frame, 0};
		    resultSlice.setStart(start);
			Dataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);
			Dataset resultError = NcdNexusUtils.sliceInputData(resultSlice, result_error_id);

			for (int j = 0; j < intPoints; j++) {
				float  valResult      = result.getFloat(0, 0, j);
				double valResultError = resultError.getDouble(0, 0, j);
				float  valData        = intResult[0].getFloat(j);
				double valDataError   = intResult[0].getError(j);
				double acc    = Math.max(1e-6 * Math.abs(Math.sqrt(valResult * valResult + valData * valData)), 1e-10);
				double accerr = Math.max(1e-6 * Math.abs(Math.sqrt(valResultError * valResultError + valDataError * valDataError)),	1e-10);

				assertEquals(String.format("Test radial sector integration for index (%d, %d)", frame, j), valResult, valData, acc);
				assertEquals(String.format("Test radial sector integration error for index (%d, %d)", frame, j), valResultError, valDataError, accerr);
			}
			
		    array_id = readResultsIds(filename, detectorOut, LazySectorIntegrationName, "azimuth", "azimuth_errors");
		    result_id = array_id[0];
		    result_error_id = array_id[1];
		    resultSlice = new SliceSettings(framesSecAz, 1, 1);
		    start = new int[] {0, frame, 0};
		    resultSlice.setStart(start);
			result = NcdNexusUtils.sliceInputData(resultSlice, result_id);
			resultError = NcdNexusUtils.sliceInputData(resultSlice, result_error_id);

			for (int j = 0; j < intPoints; j++) {
				float  valResult      = result.getFloat(0, 0, j);
				double valResultError = resultError.getDouble(0, 0, j);
				float  valData        = intResult[1].getFloat(j);
				double valDataError   = intResult[1].getError(j);
				double acc    = Math.max(1e-6 * Math.abs(Math.sqrt(valResult * valResult + valData * valData)), 1e-10);
				double accerr = Math.max(1e-6 * Math.abs(Math.sqrt(valResultError * valResultError + valDataError * valDataError)),	1e-10);

				assertEquals(String.format("Test azimuthal sector integration for index (%d, %d)", frame, j), valResult, valData, acc);
				assertEquals(String.format("Test azimuthal sector integration error for index (%d, %d)", frame, j), valResultError, valDataError, accerr);
			}
		}
	}
	
	@Test
	public void checkNormalisation() throws HDF5Exception {

	    DataSliceIdentifiers[] ids = readResultsIds(filename, detectorOut, LazySectorIntegrationName);
	    DataSliceIdentifiers data_id = ids[0];
	    DataSliceIdentifiers input_errors_id = ids[1];
	    SliceSettings dataSlice = new SliceSettings(framesSec, 1, lastFrame - firstFrame + 1);
	    int[] start = new int[] {0, 0, 0};
	    dataSlice.setStart(start);
		Dataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
		Dataset dataErrors = NcdNexusUtils.sliceInputData(dataSlice, input_errors_id);
	    
	    DataSliceIdentifiers norm_id = readDataId(filename, calibration, "data", null)[0];
	    SliceSettings normSlice = new SliceSettings(framesCal, 1, lastFrame - firstFrame + 1);
	    normSlice.setStart(start);
		Dataset norm = NcdNexusUtils.sliceInputData(normSlice, norm_id);
		
	    DataSliceIdentifiers[] array_id = readResultsIds(filename, detectorOut, LazyNormalisationName);
	    DataSliceIdentifiers result_id = array_id[0];
	    DataSliceIdentifiers result_error_id = array_id[1];
	    SliceSettings resultSlice = new SliceSettings(framesSec, 1, lastFrame - firstFrame + 1);
	    resultSlice.setStart(start);
		Dataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);
		Dataset resultError = NcdNexusUtils.sliceInputData(resultSlice, result_error_id);

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
			float valNorm = norm.getFloat(0, frame, normChannel); 
			for (int i = 0; i < intPoints; i++) {
				float valResult = result.getFloat(0, frame, i);
				double valResultError = resultError.getDouble(0, frame, i);
				float valData = data.getFloat(0, frame, i);
				double valDataError = dataErrors.getDouble(0, frame, i);
				float testResult = (float) (valData * absScaling / valNorm);
				double testError = valDataError * absScaling / valNorm;

				assertEquals(String.format("Test normalisation for pixel (%d, %d)", frame, i), testResult, valResult, 1e-6*valResult);
				assertEquals(String.format("Test normalisation erros for pixel (%d, %d)", frame, i), testError, valResultError, 1e-6*valResultError);
			}
		}
	}

	@Test
	public void checkBackgroundSubtraction() throws HDF5Exception {

	    DataSliceIdentifiers[] ids = readResultsIds(filename, detectorOut, LazyNormalisationName);
	    DataSliceIdentifiers data_id = ids[0];
	    DataSliceIdentifiers input_errors_id = ids[1];
	    SliceSettings dataSlice = new SliceSettings(framesSec, 1, lastFrame - firstFrame + 1);
	    int[] start = new int[] {0, 0, 0};
	    dataSlice.setStart(start);
		Dataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
		Dataset dataErrors = NcdNexusUtils.sliceInputData(dataSlice, input_errors_id);
	    
	    DataSliceIdentifiers[] array_id = readResultsIds(filename, detectorOut, LazyBackgroundSubtractionName);
	    DataSliceIdentifiers result_id = array_id[0];
	    DataSliceIdentifiers result_error_id = array_id[1];
	    SliceSettings resultSlice = new SliceSettings(framesSec, 1, lastFrame - firstFrame + 1);
	    resultSlice.setStart(start);
		Dataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);
		Dataset resultError = NcdNexusUtils.sliceInputData(resultSlice, result_error_id);

	    DataSliceIdentifiers[] bg_ids = readDataId(bgFilename, detectorBg, "data", "errors");
	    DataSliceIdentifiers bg_data_id = bg_ids[0];
	    DataSliceIdentifiers bg_error_id = bg_ids[1];
	    SliceSettings bgSlice = new SliceSettings(framesBg, 1, 1);
	    start = new int[] {0, 0, 0};
	    bgSlice.setStart(start);
		Dataset bgData = NcdNexusUtils.sliceInputData(bgSlice, bg_data_id);
		Dataset bgErrors = NcdNexusUtils.sliceInputData(bgSlice, bg_error_id);

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
			for (int i = 0; i < intPoints; i++) {
				float valResult = result.getFloat(0, frame, i);
				double valResultError = resultError.getDouble(0, frame, i);
				float valData = data.getFloat(0, frame, i);
				double valDataError = dataErrors.getDouble(0, frame, i);
				float valBg = bgData.getFloat(0, 0, i);
				double valBgError = bgErrors.getDouble(0, 0, i);
				float testResult = (float) (valData - bgScaling*valBg);
				double testResultError = Math.sqrt(valDataError*valDataError + bgScaling*bgScaling*valBgError*valBgError);
				double acc = Math.max(1e-6*Math.abs(Math.sqrt(testResult*testResult + valResult*valResult)), 1e-10);
				double accerr = Math.max(1e-6*Math.abs(Math.sqrt(testResultError*testResultError + valResultError*valResultError)), 1e-10);

				assertEquals(String.format("Test background subtraction for pixel (%d, %d)", frame, i), testResult, valResult, acc);
				assertEquals(String.format("Test background subtraction error for pixel (%d, %d)", frame, i), testResultError, valResultError, accerr);
			}
		}
	}
	
	@Ignore("This test needs an acurate q-axis data following changes implementing true SAXS invariant.")
	@Test
	public void checkInvariant() throws HDF5Exception {
	    DataSliceIdentifiers[] ids = readResultsIds(filename, detectorOut, LazyBackgroundSubtractionName);
	    DataSliceIdentifiers data_id = ids[0];
	    DataSliceIdentifiers input_errors_id = ids[1];
	    SliceSettings dataSlice = new SliceSettings(framesSec, 1, lastFrame - firstFrame + 1);
	    int[] start = new int[] {0, 0, 0};
	    dataSlice.setStart(start);
		Dataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
		Dataset dataErrors = NcdNexusUtils.sliceInputData(dataSlice, input_errors_id);
		dataErrors.ipower(2);
	    
	    DataSliceIdentifiers[] array_id = readResultsIds(filename, detectorOut, LazyInvariantName);
	    DataSliceIdentifiers result_id = array_id[0];
	    DataSliceIdentifiers result_error_id = array_id[1];
	    SliceSettings resultSlice = new SliceSettings(framesInv, 1, lastFrame - firstFrame + 1);
	    start = new int[] {0, 0};
	    resultSlice.setStart(start);
		Dataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);
		Dataset resultErrors = NcdNexusUtils.sliceInputData(resultSlice, result_error_id);

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
			float valResult = result.getFloat(0, frame);
			double valResultError = resultErrors.getDouble(0, frame);
			float valData = 0.0f;
			double valDataError = 0.0;
			for (int i = 0; i < intPoints; i++) {
				valData += data.getFloat(0, frame, i);
				valDataError += dataErrors.getDouble(0, frame, i);
			}
			valDataError = Math.sqrt(valDataError);
			double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);
			double accerr = Math.max(1e-6*Math.abs(Math.sqrt(valResultError*valResultError + valDataError*valDataError)), 1e-10);
			assertEquals(String.format("Test invariant for frame %d", frame), valResult, valData, acc);
			assertEquals(String.format("Test invariant error for frame %d", frame), valResultError, valDataError, accerr);
		}
	}
	
	@Test
	public void checkAverage() throws HDF5Exception {
	    DataSliceIdentifiers[] ids = readResultsIds(filename, detectorOut, LazyBackgroundSubtractionName);
	    DataSliceIdentifiers data_id = ids[0];
	    DataSliceIdentifiers input_errors_id = ids[1];
	    SliceSettings dataSlice = new SliceSettings(framesSec, 1, (int) framesSec[1]);
	    int[] start = new int[] {0, 0, 0};
	    dataSlice.setStart(start);
		Dataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
		Dataset dataErrors = NcdNexusUtils.sliceInputData(dataSlice, input_errors_id);
		dataErrors.ipower(2);
	    
	    DataSliceIdentifiers[] array_id = readResultsIds(filename, detectorOut, LazyAverageName);
	    DataSliceIdentifiers result_id = array_id[0];
	    DataSliceIdentifiers result_error_id = array_id[1];
	    SliceSettings resultSlice = new SliceSettings(framesAve, 1, 1);
	    start = new int[] {0, 0, 0};
	    resultSlice.setStart(start);
		Dataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id);
		Dataset resultErrors = NcdNexusUtils.sliceInputData(resultSlice, result_error_id);

		for (int i = 0; i < intPoints; i++) {
			float valResult = result.getFloat(0, 0, i);
			double valResultError = resultErrors.getDouble(0, 0, i);
			float valData = 0.0f;
			double valDataError = 0.0;
			for (int frame = 0; frame < framesSec[1]; frame++) {
				valData += data.getFloat(0, frame, i);
				valDataError += dataErrors.getFloat(0, frame, i);
			}
			valData /= framesSec[1];
			valDataError = Math.sqrt(valDataError) / framesSec[1];
			double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);
			double accerr = Math.max(1e-6*Math.abs(Math.sqrt(valResultError*valResultError + valDataError*valDataError)), 1e-10);
			assertEquals(String.format("Test average for index %d", i), valResult, valData, acc);
			assertEquals(String.format("Test average error for index %d", i), valResultError, valDataError, accerr);
		}
	}
	
	@Test
	public void checkLogLogPlot() throws HDF5Exception {
		checkSaxsPlot(SaxsAnalysisPlotType.LOGLOG_PLOT);
	}
	
	@Test
	public void checkGuinierPlot() throws HDF5Exception {
		checkSaxsPlot(SaxsAnalysisPlotType.GUINIER_PLOT);
	}
	
	@Test
	public void checkPorodPlot() throws HDF5Exception {
		checkSaxsPlot(SaxsAnalysisPlotType.POROD_PLOT);
	}
	
	@Test
	public void checkKratkyPlot() throws HDF5Exception {
		checkSaxsPlot(SaxsAnalysisPlotType.KRATKY_PLOT);
	}
	
	@Test
	public void checkZimmPlot() throws HDF5Exception {
		checkSaxsPlot(SaxsAnalysisPlotType.ZIMM_PLOT);
	}
	
	@Test
	public void checkDebyeBuechePlot() throws HDF5Exception {
		checkSaxsPlot(SaxsAnalysisPlotType.DEBYE_BUECHE_PLOT);
	}
	
	private void checkSaxsPlot(SaxsAnalysisPlotType plotType) throws HDF5Exception {

		SaxsPlotData plotObject = plotType.getSaxsPlotDataObject();
	    DataSliceIdentifiers[] ids = readResultsIds(filename, detectorOut, LazyAverageName);
	    DataSliceIdentifiers data_id = ids[0];
	    DataSliceIdentifiers input_errors_id = ids[1];
	    SliceSettings dataSlice = new SliceSettings(framesAve, 1, 1);
		Dataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id).squeeze();
		Dataset dataErrors = NcdNexusUtils.sliceInputData(dataSlice, input_errors_id).squeeze();
		data.setErrors(dataErrors);
	    
	    DataSliceIdentifiers[] array_id = readResultsIds(filename, detectorOut, plotType.getGroupName());
	    DataSliceIdentifiers result_id = array_id[0];
	    DataSliceIdentifiers result_error_id = array_id[1];
	    SliceSettings resultSlice = new SliceSettings(framesAve, 1, 1);
		Dataset result = NcdNexusUtils.sliceInputData(resultSlice, result_id).squeeze();
		Dataset resultErrors = NcdNexusUtils.sliceInputData(resultSlice, result_error_id).squeeze();

		long axis_id = H5.H5Dopen(data_id.datagroup_id, "q", HDF5Constants.H5P_DEFAULT);
		long axis_errors_id = H5.H5Dopen(data_id.datagroup_id, "q_errors", HDF5Constants.H5P_DEFAULT);
		DataSliceIdentifiers axisIDs = new DataSliceIdentifiers();
		axisIDs.setIDs(data_id.datagroup_id, axis_id);
		DataSliceIdentifiers axisErrorIDs = new DataSliceIdentifiers();
		axisErrorIDs.setIDs(data_id.datagroup_id, axis_errors_id);
		
	    SliceSettings axisSlice = new SliceSettings(new long[] {framesAve[framesAve.length - 1]}, 0, (int) framesAve[framesAve.length - 1]);
		Dataset axis = NcdNexusUtils.sliceInputData(axisSlice, axisIDs);
		Dataset axisErrors = NcdNexusUtils.sliceInputData(axisSlice, axisErrorIDs);
		axis.setErrors(axisErrors);
		
		for (int i = 0; i < intPoints; i++) {
			double valResult = result.getDouble(i);
			double valResultError = resultErrors.getDouble(i);
			double valData = plotObject.getDataValue(i, axis, data);
			double valDataError = plotObject.getDataError(i, axis, data);
			double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);
			double accerr = Math.max(1e-6*Math.abs(Math.sqrt(valResultError*valResultError + valDataError*valDataError)), 1e-10);
			assertEquals(String.format("Test %s SAXS Plot data for index %d", plotType.getName(), i), valResult, valData, acc);
			assertEquals(String.format("Test %s SAXS Plot error for index %d", plotType.getName(), i), valResultError, valDataError, accerr);
		}
	}
	
	@AfterClass
	public static void tearDown() throws Exception {
		//Clear scratch directory 
		IOTestUtils.makeScratchDirectory(testScratchDirectoryName);
	}
	
	private static DataSliceIdentifiers[] readDataId(String dataFile, String detector, String dataset, String errors) throws HDF5Exception {
		long file_handle = HDF5Utils.H5Fopen(dataFile, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		long entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		long detector_group_id = H5.H5Gopen(entry_group_id, detector, HDF5Constants.H5P_DEFAULT);
		long input_data_id = H5.H5Dopen(detector_group_id, dataset, HDF5Constants.H5P_DEFAULT);
		long input_errors_id = -1;
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
	
	private static DataSliceIdentifiers[] readResultsIds(String dataFile, String detector, String result) throws HDF5Exception {
		long file_handle = HDF5Utils.H5Fopen(dataFile, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		long entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		long instrument_group_id = H5.H5Gopen(entry_group_id, detector, HDF5Constants.H5P_DEFAULT);
		long detector_group_id = H5.H5Gopen(instrument_group_id, result, HDF5Constants.H5P_DEFAULT);
		long input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		long input_errors_id = H5.H5Dopen(detector_group_id, "errors", HDF5Constants.H5P_DEFAULT);
		
		DataSliceIdentifiers ids = new DataSliceIdentifiers();
		ids.setIDs(detector_group_id, input_data_id);
		DataSliceIdentifiers errors_ids = new DataSliceIdentifiers();
		errors_ids.setIDs(detector_group_id, input_errors_id);
		return 	readResultsIds(dataFile, detector, result, null, null);
	}
	
	private static DataSliceIdentifiers[] readResultsIds(String dataFile, String detector, String result, String data, String errors) throws HDF5Exception {
		long file_handle = HDF5Utils.H5Fopen(dataFile, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		long entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		long instrument_group_id = H5.H5Gopen(entry_group_id, detector, HDF5Constants.H5P_DEFAULT);
		long detector_group_id = H5.H5Gopen(instrument_group_id, result, HDF5Constants.H5P_DEFAULT);
		long input_data_id = H5.H5Dopen(detector_group_id, (data == null ? "data" : data), HDF5Constants.H5P_DEFAULT);
		long input_errors_id = H5.H5Dopen(detector_group_id, (errors == null ? "errors" : errors), HDF5Constants.H5P_DEFAULT);
		
		DataSliceIdentifiers ids = new DataSliceIdentifiers();
		ids.setIDs(detector_group_id, input_data_id);
		DataSliceIdentifiers errors_ids = new DataSliceIdentifiers();
		errors_ids.setIDs(detector_group_id, input_errors_id);
		return new DataSliceIdentifiers[] {ids, errors_ids};
	}

}
