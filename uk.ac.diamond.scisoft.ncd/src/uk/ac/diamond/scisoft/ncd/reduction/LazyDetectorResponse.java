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

import org.eclipse.core.runtime.IProgressMonitor;
import org.nexusformat.NexusException;
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5DetectorResponse;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusExtractorException;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeBuilder;

public class LazyDetectorResponse extends LazyDataReduction {

	private String drFile;
	
	public static String name = "DetectorResponse";

	public void setDrFile(String drFile) {
		this.drFile = drFile;
	}

	public LazyDetectorResponse(String activeDataset, int[] frames, int frameBatch, NexusFile nxsFile) {
		super(activeDataset, frames, frameBatch, nxsFile);
	}

	private AbstractDataset createDetectorResponseInput(String detector, int dim) throws NexusException, NexusExtractorException, Exception {


		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(drFile, NcdDataUtils.getDetectorSelection(detector, null));
		int[] drDims = detectorTree.getNode("entry1/instrument").getNode(detector).getNode("data").getData().dimensions;
		int[] start = new int[drDims.length];
		Arrays.fill(start, 0);
		INexusTree tmpdrData = NcdDataUtils.selectNAxisFrames(detector, null, detectorTree.getNode("entry1/instrument"), dim, start, drDims);
		
		return Nexus.createDataset(NcdDataUtils.getData(tmpdrData, detector, "data", NexusExtractor.SDSClassName), false);
	}

	@Override
	public void execute(INexusTree tmpNXdata, int dim, IProgressMonitor monitor) throws Exception {
		
		HDF5DetectorResponse reductionStep = new HDF5DetectorResponse(name, activeDataset);
		AbstractDataset responseDataSet = createDetectorResponseInput(detector, dim);

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
	
				if (dim == 1)
					reductionStep.setqAxis(qaxis);
	
				reductionStep.setResponse(responseDataSet);
				reductionStep.writeout(currentBatch, tmpData);
	
				int[] datDimStartPrefix = Arrays.copyOf(start, start.length-dim);
				datDimStartPrefix[datDimStartPrefix.length-1] -= firstFrame;
				int[] datDimPrefix = new int[gridFrame.length];
				Arrays.fill(datDimPrefix, 1);
				
				if (dim == 1 && qaxis != null) {
					NexusGroupData qData = NcdDataUtils.getData(tmpData, name, "q", NexusExtractor.SDSClassName);
					NcdDataUtils.addAxis(tmpData, name, "q", qData, frames.length, 1, "nm^{-1}", false);
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

}
