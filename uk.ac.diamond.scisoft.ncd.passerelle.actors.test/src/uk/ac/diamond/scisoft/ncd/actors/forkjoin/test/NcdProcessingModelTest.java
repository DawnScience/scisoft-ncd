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

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.measure.quantity.Length;
import javax.measure.unit.SI;

import org.apache.commons.io.IOUtils;
import org.eclipse.core.runtime.Path;
import org.jscience.physics.amount.Amount;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.TestUtils;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.NcdProcessingModel;
import uk.ac.diamond.scisoft.ncd.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.preferences.NcdReductionFlags;

public class NcdProcessingModelTest {

	private static NcdProcessingModel testClass, testbgClass;
	private static Double bgScaling = 0.1;
	private static Double absScaling = 1.0;
	private static int normChannel = 1;
	private static CalibrationResultsBean crb = null;
	private static String drFile;
	//private static boolean enableMask = false;
	private static NcdReductionFlags flags;
	private static SectorROI intSector;
	private static int intPoints, azPoints;
	//private static BooleanDataset mask;
	private static NcdDetectors ncdDetectors;

	private static String detector = "Rapid2D";
	private static String detectorOut = "Rapid2D_processing";
	private static String detectorBg = "Rapid2D_result";
	private static String calibration = "Scalers";
	private static Amount<Length> pxSaxs = Amount.valueOf(0.1, SI.MILLIMETER);
	private static int dim = 2;
	private static String filename, bgFilename;
	private static String testScratchDirectoryName;
	private static Integer firstFrame = 60;
	private static Integer lastFrame = 70;
	private static String frameSelection = "0;60-70";
	private static Integer bgFirstFrame = 5;
	private static Integer bgLastFrame = 5;
	private static String bgFrameSelection = "0;" + bgFirstFrame.toString() + "-" + bgLastFrame.toString();
	
	private static AbstractDataset dr;
	
	private static long[] frames = new long[] {1, 120, 512, 512};
	private static long[] drFrames = new long[] {1, 1, 512, 512};
	private static long[] framesCal = new long[] {1, 120, 9};
	private static long[] framesResult = new long[] {1, lastFrame - firstFrame + 1, 512, 512};
	private static long[] framesSec, framesSecAz, framesAve, framesBg, framesInv;
	
	@Before
	public void setUp() throws Exception {

		testScratchDirectoryName = TestUtils.generateDirectorynameFromClassname(NcdProcessingModelTest.class.getCanonicalName());
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
		filename = testScratchDirectoryName + "ncd_processing_test.nxs"; 
		bgFilename = testScratchDirectoryName + "ncd_bg_test.nxs"; 

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

		ncdDetectors = new NcdDetectors();
		ncdDetectors.setDetectorSaxs(detector);
		ncdDetectors.setDetectorWaxs(null);
		ncdDetectors.setPxSaxs(pxSaxs);
		ncdDetectors.setPxWaxs(null);

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
		testClass.setBgDetector(detectorBg);
		testClass.setDrFile(drFile);
		testClass.setAbsScaling(absScaling);
		testClass.setBgScaling(bgScaling);
		testClass.setFirstFrame(firstFrame);
		testClass.setLastFrame(lastFrame);
		testClass.setCalibration(calibration);
		testClass.setNormChannel(normChannel);
		testClass.setCrb(crb);
		//testClass.setEnableMask(enableMask);
		testClass.setFlags(flags);
		testClass.setIntSector(intSector);
		//testClass.setMask(mask);
		//testClass.setNcdDetectors(ncdDetectors);

		testbgClass = new NcdProcessingModel();
		testbgClass.setDrFile(drFile);
		testbgClass.setAbsScaling(absScaling);
		testbgClass.setFirstFrame(bgFirstFrame);
		testbgClass.setLastFrame(bgLastFrame);
		testbgClass.setFrameSelection(bgFrameSelection);
		testbgClass.setCalibration(calibration);
		testbgClass.setNormChannel(normChannel);
		testbgClass.setCrb(crb);
		//testbgClass.setEnableMask(enableMask);
		
		flags.setEnableBackground(false);
		flags.setEnableInvariant(false);
		testbgClass.setFlags(flags);
		
		testbgClass.setIntSector(intSector);
		//testbgClass.setMask(mask);
		//testbgClass.setNcdDetectors(ncdDetectors);
		
	    //DataSliceIdentifiers dr_id = NcdNexusUtilsTest.readDataId(drFile, detector, "data", null)[0];
	    //SliceSettings drSlice = new SliceSettings(drFrames, 1, 1);
	    //int[] start = new int[] {0, 0, 0, 0};
	    //drSlice.setStart(start);
		//dr = NcdNexusUtils.sliceInputData(drSlice, dr_id);
		
		testbgClass.execute(detector, dim, bgFilename);
		testClass.execute(detector, dim, filename);
	}

	@After
	public void tearDown() throws Exception {
		//Clear scratch directory 
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
	}

	@Test
	public void testExcecute() {
		fail("Not yet implemented");
	}

}
