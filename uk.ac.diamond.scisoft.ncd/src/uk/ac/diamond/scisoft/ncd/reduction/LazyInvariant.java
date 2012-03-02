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
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5Invariant;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.tree.INexusTree;

public class LazyInvariant extends LazyDataReduction {

	public static String name = "Invariant";
	
	public LazyInvariant(String activeDataset, int[] frames, int frameBatch, NexusFile nxsFile) {
		super(activeDataset, frames, frameBatch, nxsFile);
	}

	@Override
	public void execute(INexusTree tmpNXdata, int dim, IProgressMonitor monitor) throws Exception {
		HDF5Invariant reductionStep = new HDF5Invariant(name, activeDataset);

		int[] datDimMake = new int[frames.length - dim];
		datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
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
	
				reductionStep.writeout(currentBatch, tmpData);
	
				int[] datDimStartPrefix = Arrays.copyOf(start, start.length-dim);
				datDimStartPrefix[datDimStartPrefix.length-1] -= firstFrame;
				int[] datDimPrefix = new int[gridFrame.length];
				Arrays.fill(datDimPrefix, 1);
				
				if (n==0 && i==firstFrame) {
					NcdNexusUtils.writeNcdData(nxsFile, tmpData.getChildNode(name, NexusExtractor.NXDetectorClassName), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, 0);
				}
				else {
					nxsFile.opengroup(name, NexusExtractor.NXDetectorClassName);
					NcdNexusUtils.writeNcdData(nxsFile, tmpData.getChildNode(name, NexusExtractor.NXDetectorClassName).getNode("data"), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, 0);
					nxsFile.closegroup();
				}
				nxsFile.flush();
			}
		}
	}

	public AbstractDataset execute(int dim, AbstractDataset data, DataSliceIdentifiers inv_id) {
		HDF5Invariant reductionStep = new HDF5Invariant("inv", "data");
		reductionStep.parentngd = data;
		reductionStep.setIDs(inv_id);
		
		return reductionStep.writeout(dim);
	}
	
	
}
