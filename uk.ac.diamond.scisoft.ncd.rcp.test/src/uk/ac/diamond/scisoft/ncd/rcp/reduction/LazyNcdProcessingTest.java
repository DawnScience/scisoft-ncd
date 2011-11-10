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

package uk.ac.diamond.scisoft.ncd.rcp.reduction;

import static org.junit.Assert.assertEquals;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusExtractorException;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeBuilder;
import gda.device.detector.NXDetectorData;
import gda.util.TestUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import junit.framework.Assert;

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
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.rcp.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.rcp.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.rcp.utils.NcdDataUtils;
import uk.ac.gda.server.ncd.data.CalibrationResultsBean;

public class LazyNcdProcessingTest {

	static LazyNcdProcessing testClass;
	private static String bgFile;
	private static Double bgScaling = 0.1;
	private static Double absScaling = 3.5;
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
	private static String calibration = "Scalers";
	private static Double pxSaxs = 0.1;
	private static int dim = 2;
	private static String filename;
	private static String testScratchDirectoryName;
	private static Integer firstFrame = 60;
	private static Integer lastFrame = 70;
	private static Integer bgFirstFrame = 1;
	private static Integer bgLastFrame = 7;
	//private static String frameSelection = "0;60-70";
	private static String bgFrameSelection = "0;1-7";

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

		testClass.execute(detector, dim, filename, new NullProgressMonitor());
	}

	@Test
	public void checkNormalisation() throws NexusException, NexusExtractorException, Exception {

		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(detector, calibration));
		NXDetectorData tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/instrument"));

		int[] startData = new int[] {0, firstFrame, 0, 0};
		int[] stopData = new int[] {1, lastFrame +1, 512, 512};
		NXDetectorData tmpData = NcdDataUtils.selectNAxisFrames(detector, calibration, tmpNXdata, dim + 1, startData, stopData);
		AbstractDataset data = Nexus.createDataset(tmpData.getData(detector, "data", NexusExtractor.SDSClassName), false);
		AbstractDataset norm = Nexus.createDataset(tmpData.getData(calibration, "data", NexusExtractor.SDSClassName), false);

		int[] start = new int[] {0, 0, 0, 0};
		int[] stop = new int[] {1, lastFrame - firstFrame + 1, 512, 512};
		detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyNormalisation.name, calibration));
		tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));
		NXDetectorData tmpResult = NcdDataUtils.selectNAxisFrames(LazyNormalisation.name, null, tmpNXdata, dim + 1, start, stop);
		AbstractDataset result = Nexus.createDataset(tmpResult.getData(LazyNormalisation.name, "data", NexusExtractor.SDSClassName), false);

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
			float valNorm = norm.getFloat(new int[] {frame, normChannel}); 
			for (int i = 0; i < 512; i++)
				for (int j = 0; j < 512; j++) {
					float valResult = result.getFloat(new int[] {frame, i, j});
					float valData = data.getFloat(new int[] {frame, i, j});
					float testData = (float) (valResult*valNorm/absScaling);

					assertEquals(String.format("Test normalisation for pixel (%d, %d, %d)", frame, i, j), testData, valData, 1e-6*valData);
				}
		}
	}

	@Test
	public void checkBackgroundSubtraction() throws NexusException, NexusExtractorException, Exception {

		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyNormalisation.name, null));
		NXDetectorData tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));

		int[] start = new int[] {0, 0, 0, 0};
		int[] stop = new int[] {1, lastFrame - firstFrame + 1, 512, 512};
		NXDetectorData tmpData = NcdDataUtils.selectNAxisFrames(LazyNormalisation.name, null, tmpNXdata, dim + 1, start, stop);
		AbstractDataset data = Nexus.createDataset(tmpData.getData(LazyNormalisation.name, "data", NexusExtractor.SDSClassName), false);

		detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyBackgroundSubtraction.name, null));
		tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));
		NXDetectorData tmpResult = NcdDataUtils.selectNAxisFrames(LazyBackgroundSubtraction.name, null, tmpNXdata, dim + 1, start, stop);
		AbstractDataset result = Nexus.createDataset(tmpResult.getData(LazyBackgroundSubtraction.name, "data", NexusExtractor.SDSClassName), false);

		detectorTree = NexusTreeBuilder.getNexusTree(bgFile, NcdDataUtils.getDetectorSelection(detector, calibration));
		tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/instrument"));

		int[] startData = new int[] {0, bgFirstFrame, 0, 0};
		int[] stopData = new int[] {1, bgLastFrame + 1, 512, 512};
		tmpData = NcdDataUtils.selectNAxisFrames(detector, calibration, tmpNXdata, dim + 1, startData, stopData);
		AbstractDataset bg = Nexus.createDataset(tmpData.getData(detector, "data", NexusExtractor.SDSClassName), false);
		AbstractDataset norm = Nexus.createDataset(tmpData.getData(calibration, "data", NexusExtractor.SDSClassName), false);

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
	}
	
	@Test
	public void checkDetectorResponse() throws NexusException, NexusExtractorException, Exception {

		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyBackgroundSubtraction.name, null));
		NXDetectorData tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));

		int[] start = new int[] {0, 0, 0, 0};
		int[] stop = new int[] {1, lastFrame - firstFrame + 1, 512, 512};
		NXDetectorData tmpData = NcdDataUtils.selectNAxisFrames(LazyBackgroundSubtraction.name, null, tmpNXdata, dim + 1, start, stop);
		AbstractDataset data = Nexus.createDataset(tmpData.getData(LazyBackgroundSubtraction.name, "data", NexusExtractor.SDSClassName), false);

		detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyDetectorResponse.name, null));
		tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));
		NXDetectorData tmpResult = NcdDataUtils.selectNAxisFrames(LazyDetectorResponse.name, null, tmpNXdata, dim + 1, start, stop);
		AbstractDataset result = Nexus.createDataset(tmpResult.getData(LazyDetectorResponse.name, "data", NexusExtractor.SDSClassName), false);

		detectorTree = NexusTreeBuilder.getNexusTree(drFile, NcdDataUtils.getDetectorSelection(detector, null));
		tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/instrument"));

		int[] startData = new int[] {0, 0, 0, 0};
		int[] stopData = new int[] {1, 1, 512, 512};
		tmpData = NcdDataUtils.selectNAxisFrames(detector, null, tmpNXdata, dim, startData, stopData);
		AbstractDataset dr = Nexus.createDataset(tmpData.getData(detector, "data", NexusExtractor.SDSClassName), false);

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
			for (int i = 0; i < 512; i++)
				for (int j = 0; j < 512; j++) {
					float valResult = result.getFloat(new int[] {frame, i, j});
					float valData = data.getFloat(new int[] {frame, i, j});
					float valDr = dr.getFloat(new int[] {i, j}); 
					double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);
					
					assertEquals(String.format("Test detector response for pixel (%d, %d, %d)", frame, i, j), valResult, valData*valDr, acc);
				}
		}
	}
	
	@Test
	public void checkInvariant() throws NexusException, NexusExtractorException, Exception {

		int[] start = new int[] {0, 0, 0, 0};
		int[] stop = new int[] {1, lastFrame - firstFrame + 1, 512, 512};
			INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyDetectorResponse.name, null));
			NXDetectorData tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));

			NXDetectorData tmpData = NcdDataUtils.selectNAxisFrames(LazyDetectorResponse.name, null, tmpNXdata, dim + 1, start, stop);
			AbstractDataset data = Nexus.createDataset(tmpData.getData(LazyDetectorResponse.name, "data", NexusExtractor.SDSClassName), false);

			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyInvariant.name, null));
			tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));
			int[] startData = new int[] {0, 0};
			int[] stopData = new int[] {1, lastFrame - firstFrame + 1};
			NXDetectorData tmpResult = NcdDataUtils.selectNAxisFrames(LazyInvariant.name, null, tmpNXdata, 1, startData, stopData);
			AbstractDataset result = Nexus.createDataset(tmpResult.getData(LazyInvariant.name, "data", NexusExtractor.SDSClassName), false);

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
	
	@Test
	public void checkSectorIntegration() throws NexusException, NexusExtractorException, Exception {

		for (int frame = 0; frame <= lastFrame - firstFrame; frame++) {
			int[] start = new int[] {0, frame, 0, 0};
			int[] stop = new int[] {1, frame + 1, 512, 512};
			INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyDetectorResponse.name, null));
			NXDetectorData tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));

			NXDetectorData tmpData = NcdDataUtils.selectNAxisFrames(LazyDetectorResponse.name, null, tmpNXdata, dim, start, stop);
			AbstractDataset data = Nexus.createDataset(tmpData.getData(LazyDetectorResponse.name, "data", NexusExtractor.SDSClassName), false);

			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazySectorIntegration.name, null));
			tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));
			int[] startData = new int[] {0, frame, 0};
			int[] stopData = new int[] {1, frame + 1, intPoints};
			NXDetectorData tmpResult = NcdDataUtils.selectNAxisFrames(LazySectorIntegration.name, null, tmpNXdata, dim-1, startData, stopData);
			AbstractDataset result = Nexus.createDataset(tmpResult.getData(LazySectorIntegration.name, "data", NexusExtractor.SDSClassName), false);

			AbstractDataset[] intResult = ROIProfile.sector(data, null, intSector);

			for (int j = 0; j < intPoints; j++) {
				float valResult = result.getFloat(new int[] {j});
				float valData = intResult[0].getFloat(new int[] {j});
				double acc = Math.max(1e-6*Math.abs(Math.sqrt(valResult*valResult + valData*valData)), 1e-10);

				assertEquals(String.format("Test sector integration for index (%d, %d)", frame, j), valResult, valData, acc);
			}
		}
	}
	
	@Test
	public void checkAverage() throws NexusException, NexusExtractorException, Exception {

		int[] start = new int[] {0, 0, 0};
		int[] stop = new int[] {1, lastFrame - firstFrame + 1, intPoints};
			INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazySectorIntegration.name, null));
			NXDetectorData tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));

			NXDetectorData tmpData = NcdDataUtils.selectNAxisFrames(LazySectorIntegration.name, null, tmpNXdata, dim, start, stop);
			AbstractDataset data = Nexus.createDataset(tmpData.getData(LazySectorIntegration.name, "data", NexusExtractor.SDSClassName), false);

			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(LazyAverage.name, null));
			tmpNXdata = new NXDetectorData(detectorTree.getNode("entry1/Rapid2D_processing"));
			int[] startData = new int[] {0, 0, 0};
			int[] stopData = new int[] {1, 1, intPoints};
			NXDetectorData tmpResult = NcdDataUtils.selectNAxisFrames(LazyAverage.name, null, tmpNXdata, dim-1, startData, stopData);
			AbstractDataset result = Nexus.createDataset(tmpResult.getData(LazyAverage.name, "data", NexusExtractor.SDSClassName), false);

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
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
	}
}
