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

import java.io.StringReader;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusExtractorException;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeBuilder;
import gda.data.nexus.tree.NexusTreeNode;
import gda.data.nexus.tree.NexusTreeNodeSelection;
import gda.util.TestUtils;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.nexusformat.NexusException;
import org.nexusformat.NexusFile;
import org.xml.sax.InputSource;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.reduction.LazyAverage;
import uk.ac.diamond.scisoft.ncd.reduction.LazyBackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.reduction.LazyDetectorResponse;
import uk.ac.diamond.scisoft.ncd.reduction.LazyInvariant;
import uk.ac.diamond.scisoft.ncd.reduction.LazyNormalisation;
import uk.ac.diamond.scisoft.ncd.reduction.LazySectorIntegration;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class NcdLazyDataReductionTest {

	private static String testScratchDirectoryName;
	private static String filename, bgFile, drFile, secFile;
	private static String testDatasetName = "testInput"; 
	private static String testNormName = "testNorm"; 
	private Integer firstFrame = 33;
	private Integer lastFrame = 52;
	private static Integer bgFirstFrame = 1;
	private static Integer bgLastFrame = 7;
	
	static int [] shape = new int[] {3, 91, 128, 64};
	static int [] imageShape = new int[] {shape[2], shape[3]};
	static int bgFrames = shape[1] / 9;
	private static float scale = 10.0f; 
	private static float scaleBg = 0.1f;
	private static float absScale = 100.0f;
	static int dim = 2;
	static int points = 1;
	
	@BeforeClass
	public static void writeTestNexusFile() throws Exception {
		
		
		testScratchDirectoryName = TestUtils.generateDirectorynameFromClassname(NcdLazyDataReductionTest.class.getCanonicalName());
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
		
		for (int n: imageShape) points *= n;
		
		{
			filename = testScratchDirectoryName + "ncd_sda_test.nxs"; 

			NexusFile nxsFile = new NexusFile(filename, NexusFile.NXACC_CREATE5);
			nxsFile.makegroup("instrument", NexusExtractor.NXInstrumentClassName);
			nxsFile.opengroup("instrument", NexusExtractor.NXInstrumentClassName);

			int[] datDimPrefix = new int[] {1, 1};
			for (int n = 0; n < shape[0]; n++) {
				int[] gridFrame = NcdDataUtils.convertFlatIndex(n, shape, 3);

				for (int frames = 0; frames < shape[1]; frames++) {
					float[] norm = new float[] {scale*(n+1)};
					float[] data = new float[points];
					for (int i = 0; i < shape[2]; i++) {
						for (int j = 0; j < shape[3]; j++) {
							int idx = i*shape[3] + j; 
							float val = n*shape[1] + frames + i*shape[3] + j;
							data[idx] = val;
						}
					}
					INexusTree nxdata = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
					NexusGroupData datagroup = new NexusGroupData(imageShape, NexusFile.NX_FLOAT32, data);
					NexusGroupData normgroup = new NexusGroupData(new int[] {1}, NexusFile.NX_FLOAT32, norm);
					datagroup.isDetectorEntryData = true;
					normgroup.isDetectorEntryData = true;
					NcdDataUtils.addData(nxdata, testDatasetName, "data", datagroup, "counts", 1);
					NcdDataUtils.addData(nxdata, testNormName, "data", normgroup, "counts", 1);

					int[] datStartPosition =  new int[] {gridFrame[0], frames};
					int[] datDimMake =  new int[] {shape[0], shape[1]};
					if (n == 0 && frames == 0) {
						NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testDatasetName), true, false, null, datDimPrefix, datStartPosition, datDimMake, 2);
						NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testNormName), true, false, null, datDimPrefix, datStartPosition, datDimMake, 1);
					}
					else {
						nxsFile.opengroup(testDatasetName, NexusExtractor.NXDetectorClassName);
						NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testDatasetName).getNode("data"), true, false, null, datDimPrefix,  datStartPosition, datDimMake, 2);
						nxsFile.closegroup();

						nxsFile.opengroup(testNormName, NexusExtractor.NXDetectorClassName);
						NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testNormName).getNode("data"), true, false, null, datDimPrefix,  datStartPosition, datDimMake, 1);
						nxsFile.closegroup();
					}
					nxsFile.flush();
				}
			}
			nxsFile.closegroup();
			nxsFile.close();
		}
		

		{
			bgFile = testScratchDirectoryName + "bgfile_ncd_sda_test.nxs"; 

			NexusFile nxsFile = new NexusFile(bgFile, NexusFile.NXACC_CREATE5);
			nxsFile.makegroup("entry1", NexusExtractor.NXEntryClassName);
			nxsFile.opengroup("entry1", NexusExtractor.NXEntryClassName);
			nxsFile.makegroup("instrument", NexusExtractor.NXInstrumentClassName);
			nxsFile.opengroup("instrument", NexusExtractor.NXInstrumentClassName);

			int[] datDimPrefix = new int[] {1};

			for (int frames = 0; frames < bgFrames; frames++) {
				float[] data = new float[points];
				float[] norm = new float[] {scale};
				for (int i = 0; i < shape[2]; i++) {
					for (int j = 0; j < shape[3]; j++) {
						int idx = i*shape[3] + j;
						float val = scale*(i*shape[3] + j) / absScale;
						if (frames >= bgFirstFrame && frames <= bgLastFrame)
							data[idx] = val;
						else data[idx] = 0.0f;
					}
				}
				INexusTree nxdata = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
				NexusGroupData datagroup = new NexusGroupData(imageShape, NexusFile.NX_FLOAT32, data);
				NexusGroupData normgroup = new NexusGroupData(new int[] {1}, NexusFile.NX_FLOAT32, norm);
				datagroup.isDetectorEntryData = true;
				normgroup.isDetectorEntryData = true;
				NcdDataUtils.addData(nxdata, testDatasetName, "data", datagroup, "counts", 1);
				NcdDataUtils.addData(nxdata, testNormName, "data", normgroup, "counts", 1);

				int[] datStartPosition =  new int[] {frames};
				int[] datDimMake =  new int[] {bgFrames};
				if (frames == 0) {
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testDatasetName), true, false, null, datDimPrefix, datStartPosition, datDimMake, 2);
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testNormName), true, false, null, datDimPrefix, datStartPosition, datDimMake, 1);
				}
				else {
					nxsFile.opengroup(testDatasetName, NexusExtractor.NXDetectorClassName);
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testDatasetName).getNode("data"), true, false, null, datDimPrefix,  datStartPosition, datDimMake, 2);
					nxsFile.closegroup();

					nxsFile.opengroup(testNormName, NexusExtractor.NXDetectorClassName);
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testNormName).getNode("data"), true, false, null, datDimPrefix,  datStartPosition, datDimMake, 1);
					nxsFile.closegroup();
				}
				nxsFile.flush();
			}

			nxsFile.closegroup();
			nxsFile.closegroup();
			nxsFile.close();
		}
		
		{
			drFile = testScratchDirectoryName + "drfile_ncd_sda_test.nxs"; 
        
			NexusFile nxsFile = new NexusFile(drFile, NexusFile.NXACC_CREATE5);
			nxsFile.makegroup("entry1", NexusExtractor.NXEntryClassName);
			nxsFile.opengroup("entry1", NexusExtractor.NXEntryClassName);
			nxsFile.makegroup("instrument", NexusExtractor.NXInstrumentClassName);
			nxsFile.opengroup("instrument", NexusExtractor.NXInstrumentClassName);
        
			int[] datDimPrefix = new int[] {1};
			float[] data = new float[points];
        
			int frames = 0;
			for (int i = 0; i < shape[2]; i++) {
				for (int j = 0; j < shape[3]; j++) {
					int idx = i*shape[3] + j; 
					float val = scaleBg;
					data[idx] = val;
				}
			}
			INexusTree nxdata = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
			NexusGroupData datagroup = new NexusGroupData(imageShape, NexusFile.NX_FLOAT32, data);
			datagroup.isDetectorEntryData = true;
			NcdDataUtils.addData(nxdata, testDatasetName, "data", datagroup, "counts", 1);
        
			int[] datStartPosition =  new int[] {frames};
			int[] datDimMake =  new int[] {1};
			NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testDatasetName), true, false, null, datDimPrefix, datStartPosition, datDimMake, 2);
			NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testNormName), true, false, null, datDimPrefix, datStartPosition, datDimMake, 1);
			nxsFile.flush();
        
			nxsFile.closegroup();
			nxsFile.closegroup();
			nxsFile.close();
		}
		
		{
			secFile = testScratchDirectoryName + "secfile_ncd_sda_test.nxs"; 
			
			NexusFile nxsFile = new NexusFile(secFile, NexusFile.NXACC_CREATE5);
			nxsFile.makegroup("instrument", NexusExtractor.NXInstrumentClassName);
			nxsFile.opengroup("instrument", NexusExtractor.NXInstrumentClassName);
			
			int[] datDimPrefix = new int[] {1, 1};
			for (int n = 0; n < shape[0]; n++) {
				int[] gridFrame = NcdDataUtils.convertFlatIndex(n, shape, 3);
				
				for (int frames = 0; frames < shape[1]; frames++) {
					float[] data = new float[points];
					for (int i = 0; i < shape[2]; i++) {
						for (int j = 0; j < shape[3]; j++) {
							int idx = i*shape[3] + j; 
							float val = n*shape[1] + frames + (float)Math.sqrt((i*i+j*j)/(1.0f*shape[3]*shape[3]));
							data[idx] = val;
						}
					}
					INexusTree nxdata = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
					NexusGroupData datagroup = new NexusGroupData(imageShape, NexusFile.NX_FLOAT32, data);
					datagroup.isDetectorEntryData = true;
					NcdDataUtils.addData(nxdata, testDatasetName, "data", datagroup, "counts", 1);
					
					int[] datStartPosition =  new int[] {gridFrame[0], frames};
					int[] datDimMake =  new int[] {shape[0], shape[1]};
					if (n == 0 && frames == 0) {
						NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testDatasetName), true, false, null, datDimPrefix, datStartPosition, datDimMake, 2);
					}
					else {
						nxsFile.opengroup(testDatasetName, NexusExtractor.NXDetectorClassName);
						NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(nxdata, testDatasetName).getNode("data"), true, false, null, datDimPrefix,  datStartPosition, datDimMake, 2);
						nxsFile.closegroup();
					}
					nxsFile.flush();
				}
			}
			nxsFile.closegroup();
			nxsFile.close();

		}
	}
	
	@Test
	public void testLazyNormalisation() throws Exception {
		
		NexusFile nxsFile = new NexusFile(filename, NexusFile.NXACC_RDWR);
		LazyNormalisation ncdTestClass = new LazyNormalisation(testDatasetName, shape, 10, nxsFile);
		
		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, getDetectorSelection(testDatasetName, false));
		INexusTree nxdata = detectorTree.getNode("instrument");
		
		nxsFile.opengroup("instrument", NexusExtractor.NXInstrumentClassName);
		ncdTestClass.setDetector(testDatasetName);
		ncdTestClass.setCalibration(testNormName);
		ncdTestClass.setAbsScaling((double) absScale);
		ncdTestClass.setNormChannel(0);
		ncdTestClass.setFirstFrame(firstFrame, dim);
		ncdTestClass.setLastFrame(lastFrame, dim);
		ncdTestClass.execute(nxdata, dim, new NullProgressMonitor());
		nxsFile.closegroup();
		
		detectorTree = NexusTreeBuilder.getNexusTree(filename, getDetectorSelection(LazyNormalisation.name, false));
		INexusTree tmpNXdata = detectorTree.getNode("instrument");
		for (int g = 0; g < shape[0]; g++)
			for (int k = 0; k < (lastFrame - firstFrame + 1); k++) {
				int[] start = new int[] {g, k, 0, 0};
				int[] stop = new int[] {g + 1, k + 1, shape[2], shape[3]};
				INexusTree tmpData = NcdDataUtils.selectNAxisFrames(LazyNormalisation.name, null, tmpNXdata, dim, start, stop);
				AbstractDataset outDataset = Nexus.createDataset(NcdDataUtils.getDetTree(tmpData, LazyNormalisation.name).getChildNode("data", NexusExtractor.SDSClassName).getData(), false);
				for (int i = 0; i < shape[2]; i++)
					for (int j = 0; j < shape[3]; j++) {
						float value = outDataset.getFloat(new int[] {i,j});
						float expected = absScale*(g*shape[1] + k + firstFrame + i*shape[3] + j) / (scale*(g+1));

						assertEquals(String.format("Test normalisation frame for (%d, %d, %d, %d)", g, k, i, j), expected, value, 1e-6*expected);
					}
			}
	}
	
	@Test
	public void testLazyBackgroundSubtraction() throws Exception {
		
		NexusFile nxsFile = new NexusFile(filename, NexusFile.NXACC_RDWR);
		LazyBackgroundSubtraction ncdTestClass = new LazyBackgroundSubtraction(testDatasetName, shape, 10, nxsFile);
		
		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, getDetectorSelection(testDatasetName, false));
		INexusTree nxdata = detectorTree.getNode("instrument");
		
		nxsFile.opengroup("instrument", NexusExtractor.NXInstrumentClassName);
		ncdTestClass.setDetector(testDatasetName);
		ncdTestClass.setBgFile(bgFile);
		ncdTestClass.setBgRoot("entry1/instrument");
		ncdTestClass.setBgScale((double) scaleBg);
		ncdTestClass.setAbsScaling((double) absScale);
		ncdTestClass.setCalibration(testNormName);
		ncdTestClass.setNormChannel(0);
		ncdTestClass.setFirstFrame(firstFrame, dim);
		ncdTestClass.setLastFrame(lastFrame, dim);
		ncdTestClass.setBgFirstFrame(bgFirstFrame);
		ncdTestClass.setBgLastFrame(bgLastFrame);
		ncdTestClass.execute(nxdata, dim, new NullProgressMonitor());
		nxsFile.closegroup();
		
		detectorTree = NexusTreeBuilder.getNexusTree(filename, getDetectorSelection(LazyBackgroundSubtraction.name, false));
		INexusTree tmpNXdata = detectorTree.getNode("instrument");
		for (int g = 0; g < shape[0]; g++)
			for (int k = 0; k < (lastFrame - firstFrame + 1); k++) {
				int[] start = new int[] {g, k, 0, 0};
				int[] stop = new int[] {g + 1, k + 1, shape[2], shape[3]};
				INexusTree tmpData = NcdDataUtils.selectNAxisFrames(LazyBackgroundSubtraction.name, null, tmpNXdata, dim, start, stop);
				AbstractDataset outDataset = Nexus.createDataset(NcdDataUtils.getDetTree(tmpData,LazyBackgroundSubtraction.name).getChildNode("data", NexusExtractor.SDSClassName).getData(), false);
				for (int i = 0; i < shape[2]; i++)
					for (int j = 0; j < shape[3]; j++) {
						float value = outDataset.getFloat(new int[] {i,j});
						float expected = g*shape[1] + k + firstFrame + (1.0f - scaleBg)*(i*shape[3] + j);

						assertEquals(String.format("Test background subtraction frame for (%d, %d, %d, %d)", g, k, i, j), expected, value, 1e-6*expected);
					}
			}
	}
	
	@Test
	public void testLazyDetectorResponse() throws Exception {
		
		NexusFile nxsFile = new NexusFile(filename, NexusFile.NXACC_RDWR);
		LazyDetectorResponse ncdTestClass = new LazyDetectorResponse(testDatasetName, shape, 10, nxsFile);
		
		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, getDetectorSelection(testDatasetName, false));
		INexusTree nxdata = detectorTree.getNode("instrument");
		
		nxsFile.opengroup("instrument", NexusExtractor.NXInstrumentClassName);
		ncdTestClass.setDetector(testDatasetName);
		ncdTestClass.setDrFile(drFile);
		ncdTestClass.setFirstFrame(firstFrame, dim);
		ncdTestClass.setLastFrame(lastFrame, dim);
		ncdTestClass.execute(nxdata, dim, new NullProgressMonitor());
		nxsFile.closegroup();
		
		detectorTree = NexusTreeBuilder.getNexusTree(filename, getDetectorSelection(LazyDetectorResponse.name, false));
		INexusTree tmpNXdata = detectorTree.getNode("instrument");
		for (int g = 0; g < shape[0]; g++)
			for (int k = 0; k < (lastFrame - firstFrame + 1); k++) {
				int[] start = new int[] {g, k, 0, 0};
				int[] stop = new int[] {g + 1, k + 1, shape[2], shape[3]};
				INexusTree tmpData = NcdDataUtils.selectNAxisFrames(LazyDetectorResponse.name, null, tmpNXdata, dim, start, stop);
				AbstractDataset outDataset = Nexus.createDataset(NcdDataUtils.getDetTree(tmpData,LazyDetectorResponse.name).getChildNode("data", NexusExtractor.SDSClassName).getData(), false);
				for (int i = 0; i < shape[2]; i++)
					for (int j = 0; j < shape[3]; j++) {
						float value = outDataset.getFloat(new int[] {i,j});
						float expected = (g*shape[1] + k + firstFrame + i*shape[3] + j)*scaleBg;

						assertEquals(String.format("Test detector response frame for (%d, %d, %d, %d)", g, k, i, j), expected, value, 1e-6*expected);
					}
			}
	}
	
	@Test
	public void testLazyInvariant() throws NexusException, NexusExtractorException, Exception {

		NexusFile nxsFile = new NexusFile(filename, NexusFile.NXACC_RDWR);
		LazyInvariant ncdTestClass = new LazyInvariant(testDatasetName, shape, 10, nxsFile);

		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, getDetectorSelection(testDatasetName, false));
		INexusTree nxdata = detectorTree.getNode("instrument");

		nxsFile.opengroup("instrument", NexusExtractor.NXInstrumentClassName);
		ncdTestClass.setDetector(testDatasetName);
		ncdTestClass.setFirstFrame(firstFrame, dim);
		ncdTestClass.setLastFrame(lastFrame, dim);
		ncdTestClass.execute(nxdata, dim, new NullProgressMonitor());
		nxsFile.closegroup();

		detectorTree = NexusTreeBuilder.getNexusTree(filename, getDetectorSelection(LazyInvariant.name, false));
		INexusTree tmpNXdata = detectorTree.getNode("instrument");

		for (int g = 0; g < shape[0]; g++)
			for (int k = 0; k < (lastFrame - firstFrame + 1); k++) {
				int[] start = new int[] {g, k};
				int[] stop = new int[] {g + 1, k + 1};
				INexusTree tmpData = NcdDataUtils.selectNAxisFrames(LazyInvariant.name, null, tmpNXdata, 1, start, stop);
				AbstractDataset outDataset = Nexus.createDataset(NcdDataUtils.getDetTree(tmpData,LazyInvariant.name).getChildNode("data", NexusExtractor.SDSClassName).getData(), false);
				float value = outDataset.getFloat(new int[] {0});
				float expected = 0.0f;
				for (int i = 0; i < shape[2]; i++)
					for (int j = 0; j < shape[3]; j++) 
						expected += g*shape[1] + k + firstFrame + i*shape[3] + j;
				assertEquals(String.format("Test invariant result for (%d, %d)", g, k), expected, value, 1e-6*expected);
			}
	}
	
	@Test
	public void testLazySectorIntegration() throws Exception {
		
		NexusFile nxsFile = new NexusFile(secFile, NexusFile.NXACC_RDWR);
		LazySectorIntegration ncdTestClass = new LazySectorIntegration(testDatasetName, shape, 10, nxsFile);
		
		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(secFile, getDetectorSelection(testDatasetName, false));
		INexusTree nxdata = detectorTree.getNode("instrument");
		
		nxsFile.opengroup("instrument", NexusExtractor.NXInstrumentClassName);
		ncdTestClass.setDetector(testDatasetName);
		SectorROI intSector = new SectorROI(0, 0, 0, shape[3], 0, 90);
		ncdTestClass.setIntSector(intSector);
		ncdTestClass.setFirstFrame(firstFrame, dim);
		ncdTestClass.setLastFrame(lastFrame, dim);
		ncdTestClass.execute(nxdata, dim, new NullProgressMonitor());
		nxsFile.closegroup();
		
		detectorTree = NexusTreeBuilder.getNexusTree(secFile, getDetectorSelection(LazySectorIntegration.name, false));
		INexusTree tmpNXdata = detectorTree.getNode("instrument");
		for (int g = 0; g < shape[0]; g++)
			for (int k = 0; k < (lastFrame - firstFrame + 1); k++) {
				int[] start = new int[] {g, k, 0};
				int[] stop = new int[] {g + 1, k + 1, shape[3]};
				INexusTree tmpData = NcdDataUtils.selectNAxisFrames(LazySectorIntegration.name, null, tmpNXdata, 1, start, stop);
				AbstractDataset outDataset = Nexus.createDataset(NcdDataUtils.getDetTree(tmpData,LazySectorIntegration.name).getChildNode("data", NexusExtractor.SDSClassName).getData(), false);
				int[] startImage = new int[] {g, firstFrame + k, 0, 0};
				int[] stopImage = new int[] {g + 1, firstFrame + k + 1, shape[2], shape[3]};
				INexusTree tmpInput = NcdDataUtils.selectNAxisFrames(testDatasetName, null, nxdata, 2, startImage, stopImage);
				AbstractDataset[] intResult = ROIProfile.sector(Nexus.createDataset(NcdDataUtils.getDetTree(tmpInput,testDatasetName).getChildNode("data", NexusExtractor.SDSClassName).getData(), false), null, intSector);
				for (int i = 1; i < shape[3]; i++) {
						float value = outDataset.getFloat(new int[] {i});
						float expected = intResult[0].getFloat(new int[] {i});

						assertEquals(String.format("Test sector integration frame for (%d, %d, %d)", g, k, i), expected, value, 1e-6*expected);
				}
			}
	}
	
	@Test
	public void testLazyAverage() throws Exception {
		
		NexusFile nxsFile = new NexusFile(filename, NexusFile.NXACC_RDWR);
		LazyAverage ncdTestClass = new LazyAverage(testDatasetName, shape, 10, nxsFile);
		
		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, getDetectorSelection(testDatasetName, false));
		INexusTree nxdata = detectorTree.getNode("instrument");
		
		nxsFile.opengroup("instrument", NexusExtractor.NXInstrumentClassName);
		ncdTestClass.setFirstFrame(firstFrame, dim);
		ncdTestClass.setLastFrame(lastFrame, dim);
		ncdTestClass.setAverageIndices(new int[] {1,2}, dim);
		ncdTestClass.execute(nxdata, dim, new NullProgressMonitor());
		nxsFile.closegroup();
		
		detectorTree = NexusTreeBuilder.getNexusTree(filename, getDetectorSelection(LazyAverage.name, true));
		INexusTree tmpNXdata = detectorTree.getNode("instrument");
		AbstractDataset outDataset = Nexus.createDataset(NcdDataUtils.getDetTree(tmpNXdata,LazyAverage.name).getChildNode("data", NexusExtractor.SDSClassName).getData(), false);
		for (int i = 0; i < shape[2]; i++)
			for (int j = 0; j < shape[3]; j++) {
				float value = outDataset.getFloat(new int[] {0, 0, i, j});
				float expected = ((shape[0]-1)*shape[1]/2 + (lastFrame - firstFrame)/2.0f + firstFrame + i*shape[3] + j);

				// This check fails for higher accuracy settings
				assertEquals(String.format("Test average frame for (%d, %d)", i, j), expected, value, 1e-6*expected);
			}
	}
	
	private NexusTreeNodeSelection getDetectorSelection(String detName, boolean getData) throws Exception {
		String type;
		if (getData) type = "2";
		else type = "1";
		String xml = "<?xml version='1.0' encoding='UTF-8'?>" +
		"<nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>NXinstrument</nxClass><wanted>1</wanted><dataType>2</dataType>" +
		"<nexusTreeNodeSelection><nxClass>NXdetector</nxClass><name>"+ detName + "</name><wanted>2</wanted><dataType>2</dataType>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>data</name><wanted>2</wanted><dataType>" + type + "</dataType>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>NXdetector</nxClass><name>"+ testNormName + "</name><wanted>2</wanted><dataType>2</dataType>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>data</name><wanted>2</wanted><dataType>" + type + "</dataType>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>";
		return NexusTreeNodeSelection.createFromXML(new InputSource(new StringReader(xml)));
	}
	
	@AfterClass
	public static void removeTmpFiles() throws Exception {
		//Clear scratch directory 
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
	}
}
