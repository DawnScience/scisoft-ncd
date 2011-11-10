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

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeNode;
import gda.device.detector.NXDetectorData;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.ncd.rcp.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.rcp.utils.NcdNexusUtils;
import uk.ac.gda.server.ncd.detectorsystem.NcdDetectorSystem;
import uk.ac.gda.server.ncd.subdetector.Average;

public class LazyAverage extends LazyDataReduction {

	public static String name = "Average";
	private String sas_type = NcdDetectorSystem.REDUCTION_DETECTOR;
	private int[] averageIndices;
	
	public LazyAverage(String activeDataset, int[] frames, int frameBatch, NexusFile nxsFile) {
		super(activeDataset, frames, frameBatch, nxsFile);
		averageIndices = null;
	}
	
	@Override
	public void execute(NXDetectorData tmpNXdata, int dim, IProgressMonitor monitor) throws Exception {
		
		int[] outputDims = new int[frames.length];
		if (averageIndices == null)
			averageIndices = new int[] {frames.length - dim};
		
		for (int i = 0; i < frames.length; i++)
			if (!ArrayUtils.contains(averageIndices, i+1))
				outputDims[i] = frames[i];
			else
				outputDims[i] = 1;
		
		int[] start = new int[frames.length];
		int[] stop = Arrays.copyOf(frames, gridDim);
		Arrays.fill(start, 0);
		start[frames.length - dim - 1] = firstFrame;
		stop[frames.length - dim - 1] = lastFrame + 1;
		
		int gridIdx = NcdDataUtils.gridPoints(outputDims, dim);
		for (int n = 0; n < gridIdx; n++) {
			int[] gridFrame = NcdDataUtils.convertFlatIndex(n, outputDims, dim+1);
			
			int totalFrames = 1;
			for (int i = 0; i < frames.length - dim; i++)
				if (!ArrayUtils.contains(averageIndices, i+1)) {
					start[i] = gridFrame[i];
					stop[i] = gridFrame[i] + 1;
				}
				else totalFrames *= stop[i] - start[i];
			
			NXDetectorData tmpData = executeRecursiveAxis(tmpNXdata, 0, dim, start, stop, monitor);
			
			AbstractDataset outDataset = Nexus.createDataset(tmpData.getData(name, "data", NexusExtractor.SDSClassName), false);
			outDataset.idivide(totalFrames);
			
			NXDetectorData nxOut = new NXDetectorData();
			nxOut.addData(name, "data", outDataset.getShape(), outDataset.getDtype(), outDataset.getBuffer(), "counts", 1);
			if (qaxis != null) {
				NexusGroupData qData = tmpData.getData(name, "q", NexusExtractor.SDSClassName);
				nxOut.addAxis(name, "q", qData, frames.length, 1, "nm^{-1}", false);
			}
			
			//TODO: Should inherit this node from Average class output 
			INexusTree detTree = nxOut.getDetTree(name);
			NexusTreeNode sas_node = new NexusTreeNode("sas_type", NexusExtractor.SDSClassName, null, new NexusGroupData(sas_type));
			sas_node.setIsPointDependent(false);
			detTree.addChildNode(sas_node);
			
			int[] datDimPrefix = new int[gridFrame.length+1];
			Arrays.fill(datDimPrefix, 1);
			
			int[] datDimMake = Arrays.copyOfRange(outputDims, 0, outputDims.length-dim);
			datDimMake[datDimMake.length-1] = 1;
			
			// Add extra frame dimension removed by averaging
			int[] newGridFrame = Arrays.copyOfRange(gridFrame, 0, gridFrame.length+1);
			newGridFrame[gridFrame.length] = 0;
			
			NcdNexusUtils.writeNcdData(nxsFile, nxOut.getDetTree(name), true, false, null, datDimPrefix, newGridFrame, datDimMake, dim);
			nxsFile.flush();
		}
		activeDataset = name;
	}
	
	
	private NXDetectorData executeRecursiveFrames(NXDetectorData tmpNXdata, int dim, int[] gridFrame, int[] start, int[] stop, IProgressMonitor monitor) throws Exception {
		String recursiveKey = "tmpAverage";
		String recursiveOut = "Average";
		NXDetectorData recursiveData = new NXDetectorData();
		
		int sliceStart = start[gridDim-1-dim];
		int sliceStop = stop[gridDim-1-dim];
		
		int sliceSize = Math.max(frameBatch, (sliceStop - sliceStart) / frameBatch);
		int tmpFrames = (sliceStop - sliceStart) / sliceSize;
		if ((sliceStart + tmpFrames*sliceSize) < sliceStop) 
			tmpFrames++;
		
		int[] inputDim = tmpNXdata.getData(activeDataset, "data", NexusExtractor.SDSClassName).dimensions;
		int [] frameDim = new int[dim+1];
		frameDim[0] = 1;
		for (int i = dim; i > 0; i--)
			frameDim[i] = inputDim[i - dim + inputDim.length - 1];
		
		AbstractDataset totalDataset = null;
		for (int i = sliceStart; i < sliceStop; i += sliceSize) {
			int sliceFinal = Math.min(i + sliceSize, sliceStop);
			int[] recStart = new int[gridDim];
			int[] recStop = new int[gridDim];
			NcdDataUtils.selectGridRange(frames, gridFrame, i, sliceFinal - i, recStart, recStop);
		
			AbstractDataset tmpDataset;
			
			// Keep slicing data until slice size is small enough to fit into memory to be averaged.
			if (sliceSize > frameBatch) {
				recursiveData = executeRecursiveFrames(tmpNXdata, dim, gridFrame, recStart, recStop, monitor);
				tmpDataset = Nexus.createDataset(recursiveData.getData(recursiveOut, "data", NexusExtractor.SDSClassName), false);
			}
			else {
				recursiveData = NcdDataUtils.selectNAxisFrames(activeDataset, null, tmpNXdata, dim + 1, recStart, recStop);
				Average reductionStep = new Average(recursiveOut, activeDataset);
				reductionStep.setqAxis(qaxis);
				reductionStep.writeout(sliceFinal - i, recursiveData);
				tmpDataset = Nexus.createDataset(recursiveData.getData(recursiveOut, "data", NexusExtractor.SDSClassName), false);
				tmpDataset.imultiply(sliceFinal - i);
				
				if (monitor.isCanceled()) {
					throw new CancellationException("Data reduction cancelled");
				}			
			}
			tmpDataset.setShape(frameDim);
			if (totalDataset == null)
				totalDataset = tmpDataset.clone();
			else 
				totalDataset = DatasetUtils.append(totalDataset, tmpDataset, 0);
		}
		
		NXDetectorData nxOut = new NXDetectorData();
		
		if (totalDataset != null) {
			int[] outShape;
			int outType;
			Serializable outBuffer;
			NexusGroupData qData = null;
			
			if (tmpFrames > 1) {
				NXDetectorData nxdata = new NXDetectorData();
				nxdata.addData(recursiveKey, "data", totalDataset.getShape(), totalDataset.getDtype(), totalDataset.getBuffer(), "counts", 1);
				Average reductionStep = new Average(recursiveOut, recursiveKey);
				reductionStep.setqAxis(qaxis);
				reductionStep.writeout(tmpFrames, nxdata);
				
				AbstractDataset outDataset = Nexus.createDataset(nxdata.getData(recursiveOut, "data", NexusExtractor.SDSClassName), false);
				outDataset.imultiply(tmpFrames);
			
				outShape = outDataset.getShape();
				outType = outDataset.getDtype();
				outBuffer = outDataset.getBuffer();
				if (qaxis != null)
					qData = nxdata.getData(recursiveOut, "q", NexusExtractor.SDSClassName);
			}
			else {
				int[] fullShape = totalDataset.getShape();
				outShape = Arrays.copyOfRange(fullShape, fullShape.length-dim, fullShape.length);
				outType = totalDataset.getDtype();
				outBuffer = totalDataset.getBuffer();
				if (qaxis != null)
					qData = recursiveData.getData(recursiveOut, "q", NexusExtractor.SDSClassName);
			}
			
			nxOut.addData(recursiveOut, "data", outShape, outType, outBuffer, "counts", 1);
			if (qData != null) {
				nxOut.addAxis(recursiveOut, "q", qData, outShape.length, 1, "nm^{-1}", false);
			}
		}
		return nxOut;
	}
	
	private NXDetectorData executeRecursiveAxis(NXDetectorData tmpNXdata, int idxAxis, int dim, int[] start, int[] stop, IProgressMonitor monitor) throws Exception {
		String recursiveKey = "tmpAverage";
		String recursiveOut = "Average";
		int aveIndex = averageIndices[idxAxis] - 1;
		int sliceStart = start[aveIndex];
		int sliceStop = stop[aveIndex];
		
		int sliceSize = Math.max(1, (sliceStop - sliceStart) / frameBatch);
		int tmpFrames = (sliceStop - sliceStart) / sliceSize;
		if ((sliceStart + tmpFrames*sliceSize) < sliceStop) 
			tmpFrames++;
		
		int[] inputDim = tmpNXdata.getData(activeDataset, "data", NexusExtractor.SDSClassName).dimensions;
		int [] frameDim = new int[dim+1];
		frameDim[0] = 1;
		for (int i = dim; i > 0; i--)
			frameDim[i] = inputDim[i - dim + inputDim.length - 1];
		
		AbstractDataset totalDataset = null;
		NexusGroupData qData = null;
		for (int i = sliceStart; i < sliceStop; i += sliceSize) {
			int sliceFinal = Math.min(i + sliceSize, sliceStop);
			int[] recStart = Arrays.copyOf(start, start.length);
			int[] recStop = Arrays.copyOf(stop, stop.length);
			recStart[aveIndex] = i;
			recStop[aveIndex] = sliceFinal;
		
			NXDetectorData recursiveData = new NXDetectorData();
			AbstractDataset tmpDataset;
			
			// Keep slicing data in the grid axis until single frames. Then switch slicing to the next grid axis.
			// When grid axes are exhausted switch to frame averaging method.
			if (idxAxis < averageIndices.length - 1) {
				if (sliceSize > 1) {
					recursiveData = executeRecursiveAxis(tmpNXdata, idxAxis, dim, recStart, recStop, monitor);
					tmpDataset = Nexus.createDataset(recursiveData.getData(recursiveOut, "data", NexusExtractor.SDSClassName), false);
				}
				else {
					recursiveData = executeRecursiveAxis(tmpNXdata, idxAxis + 1, dim, recStart, recStop, monitor);
					tmpDataset = Nexus.createDataset(recursiveData.getData(recursiveOut, "data", NexusExtractor.SDSClassName), false);
				}
			}
			else {
				int[] gridFrame = Arrays.copyOf(start, frames.length - dim - 1);
				recursiveData = executeRecursiveFrames(tmpNXdata, dim, gridFrame, recStart, recStop, monitor);
				tmpDataset = Nexus.createDataset(recursiveData.getData(recursiveOut, "data", NexusExtractor.SDSClassName), false);
			}
			tmpDataset.setShape(frameDim);
			if (totalDataset == null) {
				totalDataset = tmpDataset.clone();
				if (qaxis != null)
					qData = recursiveData.getData(recursiveOut, "q", NexusExtractor.SDSClassName);
			}
			else 
				totalDataset = DatasetUtils.append(totalDataset, tmpDataset, 0);
		}
		
		NXDetectorData nxOut = new NXDetectorData();
		
		if (totalDataset != null) {
			int[] outShape;
			int outType;
			Serializable outBuffer;
			
			if (tmpFrames > 1) {
				NXDetectorData nxdata = new NXDetectorData();
				nxdata.addData(recursiveKey, "data", totalDataset.getShape(), totalDataset.getDtype(), totalDataset.getBuffer(), "counts", 1);
				Average reductionStep = new Average(recursiveOut, recursiveKey);
				reductionStep.setqAxis(qaxis);
				reductionStep.writeout(tmpFrames, nxdata);
				
				AbstractDataset outDataset = Nexus.createDataset(nxdata.getData(recursiveOut, "data", NexusExtractor.SDSClassName), false);
				outDataset.imultiply(tmpFrames);
			
				outShape = outDataset.getShape();
				outType = outDataset.getDtype();
				outBuffer = outDataset.getBuffer();
				if (qaxis != null)
					qData = nxdata.getData(recursiveOut, "q", NexusExtractor.SDSClassName);
			}
			else {
				int[] fullShape = totalDataset.getShape();
				outShape = Arrays.copyOfRange(fullShape, fullShape.length-dim, fullShape.length);
				outType = totalDataset.getDtype();
				outBuffer = totalDataset.getBuffer();
			}
			
			nxOut.addData(recursiveOut, "data", outShape, outType, outBuffer, "counts", 1);
			if (qData != null) {
				nxOut.addAxis(recursiveOut, "q", qData, outShape.length, 1, "nm^{-1}", false);
			}
		}
		return nxOut;
	}
	
	public void setAverageIndices(int[] averageIndices, int dim) {
		ArrayList<Integer> res = new ArrayList<Integer>();
		
		// Skip any index that does not correspond to the grid axis
		for (int idx : averageIndices)
			if (idx < frames.length - dim)
				res.add(idx);
		res.add(frames.length - dim);
		
		this.averageIndices = ArrayUtils.toPrimitive(res.toArray(new Integer[] {}));
		Arrays.sort(this.averageIndices);
	}

}
