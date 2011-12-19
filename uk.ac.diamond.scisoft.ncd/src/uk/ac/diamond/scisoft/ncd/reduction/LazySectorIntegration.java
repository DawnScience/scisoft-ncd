/*
 * Copyright © 2011 Diamond Light Source Ltd.
 * Contact :  ScientificSoftware@diamond.ac.uk
 * 
 * This is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 * 
 * This software is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this software. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd.reduction;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5SectorIntegration;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;

public class LazySectorIntegration extends LazyDataReduction {

	private SectorROI intSector;
	private AbstractDataset mask;
	private Double gradient, intercept, cameraLength;
	
	public static String name = "SectorIntegration";

	public void setIntSector(SectorROI intSector) {
		this.intSector = intSector;
	}

	public void setMask(AbstractDataset mask) {
		this.mask = mask;
	}

	public double[] getCalibrationData() {
		if (gradient != null && intercept != null)
			return new double[] {gradient.doubleValue(), intercept.doubleValue()};
		return null;
	}

	public void setCalibrationData(Double gradient, Double intercept) {
		if (gradient != null)
			this.gradient = new Double(gradient);
		if (intercept != null)
			this.intercept =  new Double(intercept);
	}

	public void setCameraLength(Double cameraLength) {
		if (cameraLength != null)
			this.cameraLength = new Double(cameraLength);
	}

	public LazySectorIntegration(String activeDataset, int[] frames, int frameBatch, NexusFile nxsFile) {
		super(activeDataset, frames, frameBatch, nxsFile);
		mask = null;
	}

	@Override
	public void execute(INexusTree tmpNXdata, int dim, IProgressMonitor monitor) throws Exception {
		HDF5SectorIntegration reductionStep = new HDF5SectorIntegration(name, activeDataset);
		
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
	
				reductionStep.setROI(intSector);
				reductionStep.setqAxis(qaxis);
				if (mask != null)
					reductionStep.setMask(mask.cast(AbstractDataset.INT8));
				if (gradient != null && intercept != null)
					reductionStep.setCalibrationData(gradient.doubleValue(), intercept.doubleValue());
				if (cameraLength != null)
					reductionStep.setCameraLength(cameraLength.doubleValue());
				reductionStep.writeout(currentBatch, tmpData);
	
				if (qaxis != null) {
					NexusGroupData qData = NcdDataUtils.getData(tmpData, name, "q", NexusExtractor.SDSClassName);
					NcdDataUtils.addAxis(tmpData, name, "q", qData, frames.length - 1, 1, "nm^{-1}", false);
				}
				
				int[] datDimStartPrefix = Arrays.copyOf(start, start.length-dim);
				datDimStartPrefix[datDimStartPrefix.length-1] -= firstFrame;
				int[] datDimPrefix = new int[gridFrame.length];
				Arrays.fill(datDimPrefix, 1);
				
				if (n==0 && i==firstFrame) {
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(tmpData, name), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, 1);
				}
				else {
					nxsFile.opengroup(name, NexusExtractor.NXDetectorClassName);
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(tmpData, name).getNode("data"), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, 1);
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(tmpData, name).getNode("azimuth"), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, 1);
					nxsFile.closegroup();
				}
				nxsFile.flush();
			}
		}
		activeDataset = name;
	}


}
