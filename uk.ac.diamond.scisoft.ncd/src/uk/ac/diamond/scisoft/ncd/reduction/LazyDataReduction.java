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

import javax.measure.unit.Unit;
import javax.measure.unit.UnitFormat;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.ncd.core.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

public abstract class LazyDataReduction {

	protected Dataset qaxis;
	protected Unit<ScatteringVector> qaxisUnit;
	protected String detector;
	protected Dataset mask;

	public LazyDataReduction() {
	}
	
	public void setDetector(String detector) {
		this.detector = detector;
	}

	public void setQaxis(Dataset qaxis, Unit<ScatteringVector> unit) {
		this.qaxis = qaxis;
		this.qaxisUnit = unit;
	}

	public void setMask(Dataset mask) {
		this.mask = mask;
	}

	public void writeQaxisData(int dim, long datagroup_id) throws HDF5Exception {
		long[] qaxisShape = (long[]) ConvertUtils.convert(qaxis.getShape(), long[].class);
		
		UnitFormat unitFormat = UnitFormat.getUCUMInstance();
		String units = unitFormat.format(qaxisUnit); 
		long qaxis_id = NcdNexusUtils.makeaxis(datagroup_id, "q", HDF5Constants.H5T_NATIVE_FLOAT, qaxisShape, new int[] { dim },
				1, units);

		long filespace_id = H5.H5Dget_space(qaxis_id);
		long type_id = H5.H5Dget_type(qaxis_id);
		long memspace_id = H5.H5Screate_simple(qaxis.getRank(), qaxisShape, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(qaxis_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, qaxis.getBuffer());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type_id);
		H5.H5Dclose(qaxis_id);
		
		if (qaxis.hasErrors()) {
			long[] qaxisShapeError = (long[]) ConvertUtils.convert(qaxis.getShape(), long[].class);
			long qaxis_error_id = NcdNexusUtils.makedata(datagroup_id, "q_errors", HDF5Constants.H5T_NATIVE_DOUBLE, qaxisShapeError,
				false, units);
		
			filespace_id = H5.H5Dget_space(qaxis_error_id);
			type_id = H5.H5Dget_type(qaxis_error_id);
			memspace_id = H5.H5Screate_simple(qaxis.getRank(), qaxisShapeError, null);
			H5.H5Sselect_all(filespace_id);
			H5.H5Dwrite(qaxis_error_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, qaxis.getError().getBuffer());
		
			H5.H5Sclose(filespace_id);
			H5.H5Sclose(memspace_id);
			H5.H5Tclose(type_id);
			H5.H5Dclose(qaxis_error_id);
		}
		
	}
	
	public void writeNcdMetadata(long datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		String detType = DetectorTypes.REDUCTION_DETECTOR;
		long type = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		H5.H5Tset_size(type, detType.length());
		long metadata_id = NcdNexusUtils.makedata(datagroup_id, "sas_type", type, new long[] {1});
		
		long filespace_id = H5.H5Dget_space(metadata_id);
		long memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(metadata_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, detType.getBytes());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(metadata_id);
	}
	
	protected Dataset flattenGridData(Dataset data, int dimension) {
		
		int dataRank = data.getRank();
		int[] dataShape = data.getShape();
		if (dataRank > (dimension + 1)) {
			int[] frameArray = Arrays.copyOf(dataShape, dataRank - dimension);
			int totalFrames = 1;
			for (int val : frameArray) {
				totalFrames *= val;
			}
			int[] newShape = Arrays.copyOfRange(dataShape, dataRank - dimension - 1, dataRank);
			newShape[0] = totalFrames;
			return data.reshape(newShape);
		}
		return data;
	}
}
