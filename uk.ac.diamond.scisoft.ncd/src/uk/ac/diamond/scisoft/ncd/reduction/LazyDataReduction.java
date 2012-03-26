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
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.ncd.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public abstract class LazyDataReduction {

	protected AbstractDataset qaxis;
	protected String qaxisUnit;
	protected String detector;
	protected String calibration;
	protected int normChannel;

	public LazyDataReduction() {
	}
	
	public void setDetector(String detector) {
		this.detector = detector;
	}

	public void setCalibration(String calibration) {
		this.calibration = calibration;
	}

	public void setNormChannel(int normChannel) {
		this.normChannel = normChannel;
	}

	public void setQaxis(AbstractDataset qaxis, String unit) {
		this.qaxis = qaxis;
		this.qaxisUnit = unit;
	}

	//public abstract AbstractDataset execute(int dim, AbstractDataset data, AbstractDataset dataCal, DataSliceIdentifiers norm_id);
	
	public void writeQaxisData(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long[] qaxisShape = (long[]) ConvertUtils.convert(qaxis.getShape(), long[].class);
		int qaxis_id = NcdNexusUtils.makeaxis(datagroup_id, "q", HDF5Constants.H5T_NATIVE_FLOAT, qaxisShape.length, qaxisShape, new int[] { 1 },
				1, qaxisUnit);

		int filespace_id = H5.H5Dget_space(qaxis_id);
		int type_id = H5.H5Dget_type(qaxis_id);
		int memspace_id = H5.H5Screate_simple(qaxis.getRank(), qaxisShape, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(qaxis_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, qaxis.getBuffer());
	}
	
	public void writeNcdMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		String detType = DetectorTypes.REDUCTION_DETECTOR;
		int type = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		H5.H5Tset_size(type, detType.length());
		int metadata_id = NcdNexusUtils.makedata(datagroup_id, "sas_type", type, 1, new long[] {1});
		
		int filespace_id = H5.H5Dget_space(metadata_id);
		int memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(metadata_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, detType.getBytes());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(metadata_id);
	}
}
