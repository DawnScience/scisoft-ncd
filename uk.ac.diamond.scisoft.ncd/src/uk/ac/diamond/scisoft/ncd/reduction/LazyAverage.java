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

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeNode;

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
import uk.ac.diamond.scisoft.ncd.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5Average;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class LazyAverage extends LazyDataReduction {

	public static String name = "Average";
	private String sas_type = DetectorTypes.REDUCTION_DETECTOR;
	private int[] averageIndices;
	
	public LazyAverage(String activeDataset, int[] frames, int frameBatch, NexusFile nxsFile) {
		super(activeDataset, frames, frameBatch, nxsFile);
		averageIndices = null;
	}
	
	@Override
	public void execute(INexusTree tmpNXdata, int dim, IProgressMonitor monitor) throws Exception {
		
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
			
			INexusTree tmpData = executeRecursiveAxis(tmpNXdata, 0, dim, start, stop, monitor);
			
			AbstractDataset outDataset = Nexus.createDataset(NcdDataUtils.getData(tmpData, name, "data", NexusExtractor.SDSClassName), false);
			outDataset.idivide(totalFrames);
			
			NexusTreeNode nxOut = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
			NcdDataUtils.addData(nxOut, name, "data", outDataset.getShape(), outDataset.getDtype(), outDataset.getBuffer(), "counts", 1);
			if (qaxis != null) {
				NexusGroupData qData = NcdDataUtils.getData(tmpData, name, "q", NexusExtractor.SDSClassName);
				NcdDataUtils.addAxis(nxOut, name, "q", qData, frames.length, 1, qaxisUnit, false);
			}
			
			//TODO: Should inherit this node from Average class output 
			INexusTree detTree = nxOut.getChildNode(name, NexusExtractor.NXDetectorClassName);
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
			
			NcdNexusUtils.writeNcdData(nxsFile, nxOut.getChildNode(name, NexusExtractor.NXDetectorClassName), true, false, null, datDimPrefix, newGridFrame, datDimMake, dim);
			nxsFile.flush();
		}
		activeDataset = name;
	}
	
	
	private INexusTree executeRecursiveFrames(INexusTree tmpNXdata, int dim, int[] gridFrame, int[] start, int[] stop, IProgressMonitor monitor) throws Exception {
		String recursiveKey = "tmpAverage";
		String recursiveOut = "Average";
		INexusTree recursiveData = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
		
		int sliceStart = start[gridDim-1-dim];
		int sliceStop = stop[gridDim-1-dim];
		
		int sliceSize = Math.max(frameBatch, (sliceStop - sliceStart) / frameBatch);
		int tmpFrames = (sliceStop - sliceStart) / sliceSize;
		if ((sliceStart + tmpFrames*sliceSize) < sliceStop) 
			tmpFrames++;
		
		int[] inputDim = NcdDataUtils.getData(tmpNXdata, activeDataset, "data", NexusExtractor.SDSClassName).dimensions;
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
				tmpDataset = Nexus.createDataset(NcdDataUtils.getData(recursiveData, recursiveOut, "data", NexusExtractor.SDSClassName), false);
			}
			else {
				recursiveData = NcdDataUtils.selectNAxisFrames(activeDataset, null, tmpNXdata, dim + 1, recStart, recStop);
				HDF5Average reductionStep = new HDF5Average(recursiveOut, activeDataset);
				reductionStep.setqAxis(qaxis, qaxisUnit);
				reductionStep.writeout(sliceFinal - i, recursiveData);
				tmpDataset = Nexus.createDataset(NcdDataUtils.getData(recursiveData, recursiveOut, "data", NexusExtractor.SDSClassName), false);
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
		
		NexusTreeNode nxOut = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
		
		if (totalDataset != null) {
			int[] outShape;
			int outType;
			Serializable outBuffer;
			NexusGroupData qData = null;
			
			if (tmpFrames > 1) {
				NexusTreeNode nxdata = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
				NcdDataUtils.addData(nxdata, recursiveKey, "data", totalDataset.getShape(), totalDataset.getDtype(), totalDataset.getBuffer(), "counts", 1);
				HDF5Average reductionStep = new HDF5Average(recursiveOut, recursiveKey);
				reductionStep.setqAxis(qaxis, qaxisUnit);
				reductionStep.writeout(tmpFrames, nxdata);
				
				AbstractDataset outDataset = Nexus.createDataset(NcdDataUtils.getData(nxdata, recursiveOut, "data", NexusExtractor.SDSClassName), false);
				outDataset.imultiply(tmpFrames);
			
				outShape = outDataset.getShape();
				outType = outDataset.getDtype();
				outBuffer = outDataset.getBuffer();
				if (qaxis != null)
					qData = NcdDataUtils.getData(nxdata, recursiveOut, "q", NexusExtractor.SDSClassName);
			}
			else {
				int[] fullShape = totalDataset.getShape();
				outShape = Arrays.copyOfRange(fullShape, fullShape.length-dim, fullShape.length);
				outType = totalDataset.getDtype();
				outBuffer = totalDataset.getBuffer();
				if (qaxis != null)
					qData = NcdDataUtils.getData(recursiveData, recursiveOut, "q", NexusExtractor.SDSClassName);
			}
			
			NcdDataUtils.addData(nxOut, recursiveOut, "data", outShape, outType, outBuffer, "counts", 1);
			if (qData != null) {
				NcdDataUtils.addAxis(nxOut, recursiveOut, "q", qData, outShape.length, 1, qaxisUnit, false);
			}
		}
		return nxOut;
	}
	
	private INexusTree executeRecursiveAxis(INexusTree tmpNXdata, int idxAxis, int dim, int[] start, int[] stop, IProgressMonitor monitor) throws Exception {
		String recursiveKey = "tmpAverage";
		String recursiveOut = "Average";
		int aveIndex = averageIndices[idxAxis] - 1;
		int sliceStart = start[aveIndex];
		int sliceStop = stop[aveIndex];
		
		int sliceSize = Math.max(1, (sliceStop - sliceStart) / frameBatch);
		int tmpFrames = (sliceStop - sliceStart) / sliceSize;
		if ((sliceStart + tmpFrames*sliceSize) < sliceStop) 
			tmpFrames++;
		
		int[] inputDim = NcdDataUtils.getData(tmpNXdata, activeDataset, "data", NexusExtractor.SDSClassName).dimensions;
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
		
			INexusTree recursiveData = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
			AbstractDataset tmpDataset;
			
			// Keep slicing data in the grid axis until single frames. Then switch slicing to the next grid axis.
			// When grid axes are exhausted switch to frame averaging method.
			if (idxAxis < averageIndices.length - 1) {
				if (sliceSize > 1) {
					recursiveData = executeRecursiveAxis(tmpNXdata, idxAxis, dim, recStart, recStop, monitor);
					tmpDataset = Nexus.createDataset(NcdDataUtils.getData(recursiveData, recursiveOut, "data", NexusExtractor.SDSClassName), false);
				}
				else {
					recursiveData = executeRecursiveAxis(tmpNXdata, idxAxis + 1, dim, recStart, recStop, monitor);
					tmpDataset = Nexus.createDataset(NcdDataUtils.getData(recursiveData, recursiveOut, "data", NexusExtractor.SDSClassName), false);
				}
			}
			else {
				int[] gridFrame = Arrays.copyOf(start, frames.length - dim - 1);
				recursiveData = executeRecursiveFrames(tmpNXdata, dim, gridFrame, recStart, recStop, monitor);
				tmpDataset = Nexus.createDataset(NcdDataUtils.getData(recursiveData, recursiveOut, "data", NexusExtractor.SDSClassName), false);
			}
			tmpDataset.setShape(frameDim);
			if (totalDataset == null) {
				totalDataset = tmpDataset.clone();
				if (qaxis != null)
					qData = NcdDataUtils.getData(recursiveData, recursiveOut, "q", NexusExtractor.SDSClassName);
			}
			else 
				totalDataset = DatasetUtils.append(totalDataset, tmpDataset, 0);
		}
		
		NexusTreeNode nxOut = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
		
		if (totalDataset != null) {
			int[] outShape;
			int outType;
			Serializable outBuffer;
			
			if (tmpFrames > 1) {
				NexusTreeNode nxdata = new NexusTreeNode("", NexusExtractor.NXInstrumentClassName, null);
				
				NexusTreeNode tmpDet = new NexusTreeNode(recursiveKey, NexusExtractor.NXDetectorClassName, null);
				tmpDet.setIsPointDependent(true);
				NcdDataUtils.addData(tmpDet, "data", totalDataset.getShape(), totalDataset.getDtype(), totalDataset.getBuffer(), "counts", 1);
				nxdata.addChildNode(tmpDet);
				
				HDF5Average reductionStep = new HDF5Average(recursiveOut, recursiveKey);
				reductionStep.setqAxis(qaxis, qaxisUnit);
				reductionStep.writeout(tmpFrames, nxdata);
				
				AbstractDataset outDataset = Nexus.createDataset(NcdDataUtils.getData(nxdata, recursiveOut, "data", NexusExtractor.SDSClassName), false);
				outDataset.imultiply(tmpFrames);
			
				outShape = outDataset.getShape();
				outType = outDataset.getDtype();
				outBuffer = outDataset.getBuffer();
				if (qaxis != null)
					qData = NcdDataUtils.getData(nxdata,recursiveOut, "q", NexusExtractor.SDSClassName);
			}
			else {
				int[] fullShape = totalDataset.getShape();
				outShape = Arrays.copyOfRange(fullShape, fullShape.length-dim, fullShape.length);
				outType = totalDataset.getDtype();
				outBuffer = totalDataset.getBuffer();
			}
			
			NcdDataUtils.addData(nxOut, recursiveOut, "data", outShape, outType, outBuffer, "counts", 1);
			if (qData != null) {
				NcdDataUtils.addAxis(nxOut, recursiveOut, "q", qData, outShape.length, 1, qaxisUnit, false);
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
