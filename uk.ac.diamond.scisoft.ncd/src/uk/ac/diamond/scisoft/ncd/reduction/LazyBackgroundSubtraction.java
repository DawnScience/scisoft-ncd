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

package uk.ac.diamond.scisoft.ncd.reduction;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusExtractorException;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeBuilder;
import gda.data.nexus.tree.NexusTreeNode;

import org.eclipse.core.runtime.IProgressMonitor;
import org.nexusformat.NexusException;
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5BackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5Normalisation;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class LazyBackgroundSubtraction extends LazyDataReduction {

	private String bgFile;
	private String bgRoot;
	private Double bgScaling;
	private Double absScaling;

	private Integer bgFirstFrame;
	private Integer bgLastFrame;

	public static String name = "BackgroundSubtraction";

	public void setBgFile(String bgFile) {
		this.bgFile = bgFile;
	}

	public void setBgRoot(String bgRoot) {
		this.bgRoot = bgRoot;
	}

	public void setBgFirstFrame(Integer bgFirstFrame) {
		this.bgFirstFrame = bgFirstFrame;
	}

	public void setBgLastFrame(Integer bgLastFrame) {
		this.bgLastFrame = bgLastFrame;
	}

	private void checkBgFirstFrame(int bgDim) {
		if (bgFirstFrame == null) {
			bgFirstFrame = 0;
			return;
		}
		if ((bgFirstFrame < 0) || (bgFirstFrame > bgDim - 1))
			bgFirstFrame = 0;
	}

	private void checkBgLastFrame(int bgDim) {
		if (bgLastFrame == null) {
			bgLastFrame = bgDim - 1;
			return;
		}

		if ((bgLastFrame < 0) || (bgLastFrame > bgDim - 1))
			bgLastFrame = bgDim - 1;
	}

	public LazyBackgroundSubtraction(String activeDataset, int[] frames, int frameBatch, NexusFile nxsFile) {
		super(activeDataset, frames, frameBatch, nxsFile);
	}

	private AbstractDataset createBackgroundSubtractionInput(String detector, int dim, int[] startGrid, int[] stopGrid) throws NexusException, NexusExtractorException, Exception {

		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(bgFile, NcdDataUtils.getDetectorSelection(detector, calibration));
		NexusGroupData bgGroupData = detectorTree.getNode(bgRoot).getNode(detector).getNode("data").getData();
		int[] bgDims = bgGroupData.dimensions;
		int bgFramesIdx = bgDims.length - dim - 1;
		int[] start = Arrays.copyOfRange(startGrid, startGrid.length-bgDims.length, startGrid.length);
		int[] stop = Arrays.copyOfRange(stopGrid, stopGrid.length-bgDims.length, stopGrid.length);

		// Check if grid point position is within the background data range
		for (int i = 0; i < bgFramesIdx; i++) {
			if (start[i] >= bgDims[i]) {
				start[i] %= bgDims[i];
				stop[i] = start[i] + 1;		// We are processing one grid point at a time
			}
		}

		checkBgFirstFrame(bgDims[bgFramesIdx]);
		checkBgLastFrame(bgDims[bgFramesIdx]);

		int totalFrames = stop[bgFramesIdx] - start[bgFramesIdx];
		int bgFrames = bgLastFrame - bgFirstFrame + 1;
		NexusTreeNode tmpBgData = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);

		start[bgFramesIdx] = (startGrid[startGrid.length - dim - 1] - firstFrame) % bgFrames + bgFirstFrame;
		stop[bgFramesIdx] = Math.min(start[bgFramesIdx] + totalFrames, bgLastFrame + 1);
		int firstSlice = stop[bgFramesIdx] - start[bgFramesIdx];
		INexusTree sliceBgData = NcdDataUtils.selectNAxisFrames(detector, calibration, detectorTree.getNode(bgRoot), dim + 1, start, stop);
		AbstractDataset data = Nexus.createDataset(NcdDataUtils.getData(sliceBgData, detector, "data", NexusExtractor.SDSClassName), false);
		AbstractDataset norm = null;
		if (calibration != null)
			norm = Nexus.createDataset(NcdDataUtils.getData(sliceBgData, calibration, "data", NexusExtractor.SDSClassName), false);

		int i = 0;
		while (i < (totalFrames - firstSlice) / bgFrames) {
			start[bgFramesIdx] = bgFirstFrame;
			stop[bgFramesIdx] = bgLastFrame + 1;
			sliceBgData = NcdDataUtils.selectNAxisFrames(detector, calibration, detectorTree.getNode(bgRoot), dim + 1, start, stop);
			data = DatasetUtils.append(data, Nexus.createDataset(NcdDataUtils.getData(sliceBgData, detector, "data", NexusExtractor.SDSClassName), false), 0);
			if (norm != null)
				norm = DatasetUtils.append(norm, Nexus.createDataset(NcdDataUtils.getData(sliceBgData, calibration, "data", NexusExtractor.SDSClassName), false), 0);
			i++;
		}

		int remFrames = totalFrames - firstSlice - i*bgFrames; 
		if (remFrames > 0) {
			start[bgFramesIdx] = bgFirstFrame;
			stop[bgFramesIdx] = remFrames + bgFirstFrame;
			if (stop[bgFramesIdx] > start[bgFramesIdx]) {
				sliceBgData = NcdDataUtils.selectNAxisFrames(detector, calibration, detectorTree.getNode(bgRoot), dim + 1, start, stop);
				data = DatasetUtils.append(data, Nexus.createDataset(NcdDataUtils.getData(sliceBgData, detector, "data", NexusExtractor.SDSClassName), false), 0);
				if (norm != null)
					norm = DatasetUtils.append(norm, Nexus.createDataset(NcdDataUtils.getData(sliceBgData, calibration, "data", NexusExtractor.SDSClassName), false), 0);
			}
		}
		NcdDataUtils.addData(tmpBgData, detector, "data", new NexusGroupData(data.getShape(), data.getDtype(), data.getBuffer()), "counts", 1);
		if (norm != null)
			NcdDataUtils.addData(tmpBgData, calibration, "data", new NexusGroupData(norm.getShape(), norm.getDtype(), norm.getBuffer()), "counts", 1);

		if (calibration != null) {
			String nrDatasetName = "bgNormalisation";
			HDF5Normalisation reductionStep = new HDF5Normalisation(nrDatasetName, detector);
			reductionStep.setCalibName(calibration);
			reductionStep.setCalibChannel(normChannel);
			if (absScaling != null)
				reductionStep.setNormvalue(absScaling);

			reductionStep.writeout(totalFrames, tmpBgData);

			return Nexus.createDataset(NcdDataUtils.getData(tmpBgData, nrDatasetName, "data", NexusExtractor.SDSClassName), false);
		}

		return Nexus.createDataset(NcdDataUtils.getData(tmpBgData, detector, "data", NexusExtractor.SDSClassName), false);
	}

	@Override
	public void execute(INexusTree tmpNXdata, int dim, IProgressMonitor monitor) throws Exception {

		HDF5BackgroundSubtraction reductionStep = new HDF5BackgroundSubtraction(name, activeDataset);

		int[] datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
		datDimMake[datDimMake.length-1] = lastFrame - firstFrame + 1;
		int gridIdx = NcdDataUtils.gridPoints(frames, dim);
		for (int n = 0; n < gridIdx; n++) {
			if (monitor.isCanceled()) {
				throw new CancellationException("Data reduction cancelled");
			}			
			int[] gridFrame = NcdDataUtils.convertFlatIndex(n, frames, dim+1);

			for (int i = firstFrame; i <= lastFrame; i += frameBatch) {
				int currentBatch = Math.min(i + frameBatch, lastFrame + 1) - i;
				int[] start = new int[gridDim];
				int[] stop = new int[gridDim];
				NcdDataUtils.selectGridRange(frames, gridFrame, i, currentBatch, start, stop);
				INexusTree tmpData = NcdDataUtils.selectNAxisFrames(activeDataset, null, tmpNXdata, dim + 1, start, stop);

				if (dim==1)
					reductionStep.setqAxis(qaxis, qaxisUnit);

				AbstractDataset bgDataSet = createBackgroundSubtractionInput(detector, dim, start, stop);

				reductionStep.setBackground(bgDataSet);
				if (bgScaling != null) bgDataSet.imultiply(bgScaling);
				reductionStep.writeout(currentBatch, tmpData);

				int[] datDimStartPrefix = Arrays.copyOf(start, start.length-dim);
				datDimStartPrefix[datDimStartPrefix.length-1] -= firstFrame;
				int[] datDimPrefix = new int[gridFrame.length];
				Arrays.fill(datDimPrefix, 1);

				if (dim == 1 && qaxis != null) {
					NexusGroupData qData = NcdDataUtils.getData(tmpData, name, "q", NexusExtractor.SDSClassName);
					NcdDataUtils.addAxis(tmpData, name, "q", qData, frames.length, 1, qaxisUnit, false);
				}

				if (n==0 && i==firstFrame) {
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(tmpData, name), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, dim);
				}
				else {
					nxsFile.opengroup(name, NexusExtractor.NXDetectorClassName);
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(tmpData, name).getNode("data"), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, dim);
					nxsFile.closegroup();
				}
				nxsFile.flush();
			}
		}
		activeDataset = name;
	}

	public void setBgScale(Double bgScaling) {

		this.bgScaling = bgScaling;
	}

	public void setAbsScaling(Double absScaling) {

		this.absScaling = absScaling;

	}

}
