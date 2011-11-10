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

import java.util.Arrays;
import java.util.concurrent.CancellationException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.nexusformat.NexusException;
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.ncd.rcp.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.rcp.utils.NcdNexusUtils;
import uk.ac.gda.server.ncd.subdetector.DetectorResponse;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusExtractorException;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeBuilder;
import gda.device.detector.NXDetectorData;

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
		NXDetectorData tmpdrData = NcdDataUtils.selectNAxisFrames(detector, null, new NXDetectorData(detectorTree.getNode("entry1/instrument")), dim, start, drDims);
		
		return Nexus.createDataset(tmpdrData.getData(detector, "data", NexusExtractor.SDSClassName), false);
	}

	@Override
	public void execute(NXDetectorData tmpNXdata, int dim, IProgressMonitor monitor) throws Exception {
		
		DetectorResponse reductionStep = new DetectorResponse(name, activeDataset);
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
				NXDetectorData tmpData = NcdDataUtils.selectNAxisFrames(activeDataset, null, tmpNXdata, dim + 1, start, stop);
	
				if (dim == 1)
					reductionStep.setqAxis(qaxis);
	
				reductionStep.setResponse(responseDataSet);
				reductionStep.writeout(currentBatch, tmpData);
	
				int[] datDimStartPrefix = Arrays.copyOf(start, start.length-dim);
				datDimStartPrefix[datDimStartPrefix.length-1] -= firstFrame;
				int[] datDimPrefix = new int[gridFrame.length];
				Arrays.fill(datDimPrefix, 1);
				
				if (dim == 1 && qaxis != null) {
					NexusGroupData qData = tmpData.getData(name, "q", NexusExtractor.SDSClassName);
					tmpData.addAxis(name, "q", qData, frames.length, 1, "nm^{-1}", false);
				}
				
				if (n==0 && i==firstFrame) {
					NcdNexusUtils.writeNcdData(nxsFile, tmpData.getDetTree(name), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, dim);
				}
				else {
					nxsFile.opengroup(name, NexusExtractor.NXDetectorClassName);
					NcdNexusUtils.writeNcdData(nxsFile, tmpData.getDetTree(name).getNode("data"), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, dim);
					nxsFile.closegroup();
				}
				nxsFile.flush();
			}
		}
		activeDataset = name;
	}

}
