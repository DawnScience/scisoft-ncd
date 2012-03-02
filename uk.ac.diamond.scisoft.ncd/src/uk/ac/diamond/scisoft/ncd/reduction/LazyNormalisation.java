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
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5Normalisation;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;

public class LazyNormalisation extends LazyDataReduction {
	
	private Double absScaling;
	public static String name = "Normalisation";

	public LazyNormalisation(String activeDataset, int[] frames, int frameBatch, NexusFile nxsFile) {
		super(activeDataset, frames, frameBatch, nxsFile);
	}

	@Override
	public void execute(INexusTree tmpNXdata, int dim, IProgressMonitor monitor) throws Exception {

		HDF5Normalisation reductionStep = new HDF5Normalisation(name, activeDataset);
		
		int[] datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
		datDimMake[datDimMake.length-1] = lastFrame - firstFrame + 1;
		int gridIdx = NcdDataUtils.gridPoints(frames, dim);
		System.out.print("Flat index: " + gridIdx + " Shape : " + Arrays.toString(frames));
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
				INexusTree tmpData = NcdDataUtils.selectNAxisFrames(activeDataset, calibration, tmpNXdata, dim + 1, start, stop);
				
				reductionStep.setCalibName(calibration);
				reductionStep.setCalibChannel(normChannel);
				if (absScaling != null)
					reductionStep.setNormvalue(absScaling);
			
				if (dim == 1)
					reductionStep.setqAxis(qaxis, qaxisUnit);
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
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(tmpData, name).getNode("data"),true, false, null, datDimPrefix,  datDimStartPrefix, datDimMake, dim);
					nxsFile.closegroup();
				}
				nxsFile.flush();
			}
		}
		activeDataset = name;
	}

	public void setAbsScaling(Double absScaling) {
		
		this.absScaling = absScaling;
		
	}

	public AbstractDataset execute(int dim, AbstractDataset data, AbstractDataset dataCal, DataSliceIdentifiers norm_id) {
		HDF5Normalisation reductionStep = new HDF5Normalisation("norm", "data");
		reductionStep.setCalibChannel(normChannel);
		if(absScaling != null)
			reductionStep.setNormvalue(absScaling);
		reductionStep.parentngd = data;
		reductionStep.calibngd = dataCal;
		reductionStep.setIDs(norm_id);
		
		return reductionStep.writeout(dim);
	}
	
	
}
