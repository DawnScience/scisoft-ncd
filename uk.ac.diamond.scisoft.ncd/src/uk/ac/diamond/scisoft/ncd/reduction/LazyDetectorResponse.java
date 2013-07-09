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

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.core.runtime.jobs.ILock;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5DetectorResponse;

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
	
	public LazyDetectorResponse(String drFile, String detector) {
		if(drFile != null) {
			this.drFile = drFile;
		}
		this.detector = detector;
	}
	
	public AbstractDataset createDetectorResponseInput() throws HDF5Exception {
		
		int fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
		H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_WEAK);
		int nxsfile_handle = H5.H5Fopen(drFile, HDF5Constants.H5F_ACC_RDONLY, fapl);
		H5.H5Pclose(fapl);
		
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
		
		if ((input_data_id >= 0) && (input_dataspace_id >= 0) && (memspace_id >= 0)) {
			H5.H5Dread(input_data_id, input_datatype_id, memspace_id, input_dataspace_id, HDF5Constants.H5P_DEFAULT,
					drData.getBuffer());
		}
		
		H5.H5Sclose(memspace_id);
		H5.H5Sclose(input_dataspace_id);
		H5.H5Tclose(input_datatype_id);
		H5.H5Dclose(input_data_id);
		H5.H5Gclose(detector_group_id);
		H5.H5Gclose(instrument_group_id);
		H5.H5Gclose(entry_group_id);
		H5.H5Fclose(nxsfile_handle);
		
		return drData;
	}
	
	public AbstractDataset execute(int dim, AbstractDataset data, DataSliceIdentifiers det_id, DataSliceIdentifiers det_errors_id, ILock lock) {
		HDF5DetectorResponse reductionStep = new HDF5DetectorResponse("det", "data");
		reductionStep.setData(data);
		reductionStep.setResponse(drData);
		reductionStep.setIDs(det_id, det_errors_id);
		
		return reductionStep.writeout(dim, lock);
	}
}
