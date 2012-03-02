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

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.nexusformat.NexusException;
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
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
	private AbstractDataset drData;
	
	public static String name = "DetectorResponse";

	public String getDrFile() {
		return this.drFile;
	}

	public void setDrFile(String drFile) {
		this.drFile = drFile;
	}

	public AbstractDataset getDrData() {
		return this.drData;
	}
	
	public void setDrData(AbstractDataset drData) {
		this.drData = drData.clone();
	}
	
	public LazyDetectorResponse(String activeDataset, int[] frames, int frameBatch, NexusFile nxsFile) {
		super(activeDataset, frames, frameBatch, nxsFile);
	}

	public LazyDetectorResponse(String drFile, String detector) {
		if(drFile != null)
			this.drFile = new String(drFile);
		this.detector = new String(detector);
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
					reductionStep.setqAxis(qaxis, qaxisUnit);
	
				reductionStep.setResponse(responseDataSet);
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

	public AbstractDataset createDetectorResponseInput() throws HDF5Exception {
		
		int nxsfile_handle = H5.H5Fopen(drFile, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		
		int entry_group_id = H5.H5Gopen(nxsfile_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		int instrument_group_id = H5.H5Gopen(entry_group_id, "instrument", HDF5Constants.H5P_DEFAULT);
		int detector_group_id = H5.H5Gopen(instrument_group_id, detector, HDF5Constants.H5P_DEFAULT);
		
		int input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		int input_dataspace_id = H5.H5Dget_space(input_data_id);
		int input_datatype_id = H5.H5Dget_type(input_data_id);
		int input_dataclass_id = H5.H5Tget_class(input_datatype_id);
		int input_datasize_id = H5.H5Tget_size(input_datatype_id);
		
		int rank = H5.H5Sget_simple_extent_ndims(input_dataspace_id);
		int dtype = HDF5Loader.getDtype(input_dataclass_id, input_datasize_id);
		
		long[] frames = new long[rank];
		H5.H5Sget_simple_extent_dims(input_dataspace_id, frames, null);
		int memspace_id = H5.H5Screate_simple(rank, frames, null);
		
		int[] frames_int = (int[]) ConvertUtils.convert(frames, int[].class);
		drData = AbstractDataset.zeros(frames_int, dtype);
		
		if ((input_data_id >= 0) && (input_dataspace_id >= 0) && (memspace_id >= 0))
			H5.H5Dread(input_data_id, input_datatype_id, memspace_id, input_dataspace_id, HDF5Constants.H5P_DEFAULT,
					drData.getBuffer());
		
		return drData;
	}
	
	public AbstractDataset execute(int dim, AbstractDataset data, DataSliceIdentifiers det_id) {
		HDF5DetectorResponse reductionStep = new HDF5DetectorResponse("det", "data");
		reductionStep.setResponse(drData);
		reductionStep.parentngd = data;
		reductionStep.setIDs(det_id);
		
		return reductionStep.writeout(dim);
	}
}
