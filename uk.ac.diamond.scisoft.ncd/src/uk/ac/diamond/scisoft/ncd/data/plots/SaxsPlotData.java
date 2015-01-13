/*
 * Copyright 2013 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.data.plots;

import java.util.Arrays;

import javax.measure.unit.UnitFormat;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.IErrorDataset;
import org.eclipse.dawnsci.analysis.api.dataset.SliceND;
import org.eclipse.dawnsci.analysis.dataset.impl.AbstractDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.IndexIterator;
import org.eclipse.dawnsci.analysis.dataset.impl.SliceIterator;
import org.eclipse.dawnsci.hdf5.Nexus;

import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.data.plots.ISaxsPlotData;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;
import uk.ac.diamond.scisoft.ncd.reduction.LazyDataReduction;

public abstract class SaxsPlotData extends LazyDataReduction implements ISaxsPlotData {

	protected String groupName, dataName, variableName;
	
	public void execute(int entry_group_id, DataSliceIdentifiers input_ids, DataSliceIdentifiers input_errors_ids) throws HDF5Exception {
		
		long[] frames = NcdNexusUtils.getIdsDatasetShape(input_ids.dataspace_id);
		int[] frames_int = (int[]) ConvertUtils.convert(frames, int[].class);
		
	    int group_id = NcdNexusUtils.makegroup(entry_group_id, detector + "_" + groupName, Nexus.DATA);
	    int type = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_FLOAT);
	    int data_id = NcdNexusUtils.makedata(group_id, "data", type, frames, true, "a.u.");
		H5.H5Tclose(type);
	    type = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_DOUBLE);
	    int errors_id = NcdNexusUtils.makedata(group_id, "errors", type, frames, true, "a.u.");
		H5.H5Tclose(type);
		
    	SliceSettings sliceSettings = new SliceSettings(frames, frames.length - 2, 1);
		int[] step = new int[frames_int.length];
		Arrays.fill(step, 1);
		step[step.length - 1] = frames_int[frames_int.length - 1];
		SliceND slice = new SliceND(frames_int, null, frames_int, step);
		IndexIterator iter = new SliceIterator(frames_int, AbstractDataset.calcSize(frames_int), slice);
		int[] pos = iter.getPos();
		while (iter.hasNext()) {
			sliceSettings.setStart(pos);
			Dataset data_slice = NcdNexusUtils.sliceInputData(sliceSettings, input_ids).squeeze();
			Dataset errors_slice = NcdNexusUtils.sliceInputData(sliceSettings, input_errors_ids).squeeze();
			data_slice.setError(errors_slice);
			Dataset tmpFrame = getSaxsPlotDataset(data_slice, qaxis);
			
			int filespace_id = H5.H5Dget_space(data_id);
			int type_id = H5.H5Dget_type(data_id);
			long[] ave_start = (long[]) ConvertUtils.convert(slice, long[].class);
			long[] ave_step = (long[]) ConvertUtils.convert(step, long[].class);
			long[] ave_count_data = new long[frames.length];
			Arrays.fill(ave_count_data, 1);
			int memspace_id = H5.H5Screate_simple(ave_step.length, ave_step, null);
			
			H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, ave_start, ave_step, ave_count_data,
					ave_step);
			H5.H5Dwrite(data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT,
					tmpFrame.getBuffer());
			
			H5.H5Sclose(filespace_id);
			H5.H5Sclose(memspace_id);
			H5.H5Tclose(type_id);
			
			if (tmpFrame.hasErrors()) {
				filespace_id = H5.H5Dget_space(errors_id);
				type_id = H5.H5Dget_type(errors_id);
				memspace_id = H5.H5Screate_simple(ave_step.length, ave_step, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, ave_start, ave_step, ave_count_data,
						ave_step);
				H5.H5Dwrite(errors_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT,
						tmpFrame.getError().getBuffer());
			
				H5.H5Sclose(filespace_id);
				H5.H5Sclose(memspace_id);
				H5.H5Tclose(type_id);
			}
			
		}
		
		// add long_name attribute
		{
			int attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attrtype_id, dataName.getBytes().length);
			
			int attr_id = H5.H5Acreate(data_id, "long_name", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, dataName.getBytes());
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
			H5.H5Aclose(attr_id);
			H5.H5Sclose(attrspace_id);
			H5.H5Tclose(attrtype_id);
		}
		
		writeAxisData(group_id);
		
		H5.H5Dclose(data_id);
		H5.H5Dclose(errors_id);
		H5.H5Gclose(group_id);
	}
	
	private void writeAxisData(int group_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long[] axisShape = (long[]) ConvertUtils.convert(qaxis.getShape(), long[].class);
		
		Dataset qaxisNew = getSaxsPlotAxis(qaxis);
		
		UnitFormat unitFormat = UnitFormat.getUCUMInstance();
		String units = unitFormat.format(qaxisUnit); 
		int qaxis_id = NcdNexusUtils.makeaxis(group_id, "variable", HDF5Constants.H5T_NATIVE_FLOAT, axisShape,
				new int[] { qaxisNew.getRank() }, 1, units);

		int filespace_id = H5.H5Dget_space(qaxis_id);
		int type_id = H5.H5Dget_type(qaxis_id);
		int memspace_id = H5.H5Screate_simple(qaxisNew.getRank(), axisShape, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(qaxis_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, qaxisNew.getBuffer());
		
		// add long_name attribute
		{
			int attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attrtype_id, variableName.getBytes().length);
			
			int attr_id = H5.H5Acreate(qaxis_id, "long_name", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, variableName.getBytes());
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
			H5.H5Aclose(attr_id);
			H5.H5Sclose(attrspace_id);
			H5.H5Tclose(attrtype_id);
		}
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type_id);
		H5.H5Dclose(qaxis_id);
		
		if (qaxisNew.hasErrors()) {
			int qaxis_error_id = NcdNexusUtils.makedata(group_id, "variable_errors", HDF5Constants.H5T_NATIVE_DOUBLE, axisShape, false, units);
		
			filespace_id = H5.H5Dget_space(qaxis_error_id);
			type_id = H5.H5Dget_type(qaxis_error_id);
			memspace_id = H5.H5Screate_simple(qaxisNew.getRank(), axisShape, null);
			H5.H5Sselect_all(filespace_id);
			H5.H5Dwrite(qaxis_error_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, qaxisNew.getError().getBuffer());
		
			H5.H5Sclose(filespace_id);
			H5.H5Sclose(memspace_id);
			H5.H5Tclose(type_id);
			H5.H5Dclose(qaxis_error_id);
		}
		
	}
	
	@Override
	public Dataset getSaxsPlotDataset(IDataset data, IDataset axis) {
		Dataset tmpData = DatasetFactory.zeros(data.getShape(), Dataset.FLOAT32);
		boolean hasErrors = false;
		Dataset tmpErrors = null;
		if (data instanceof IErrorDataset && ((IErrorDataset) data).hasErrors()) {
			tmpErrors = DatasetFactory.zeros(data.getShape(), Dataset.FLOAT32);
			hasErrors = true;
		}
		IndexIterator itr = tmpData.getIterator();
		while (itr.hasNext()) {
			int idx = itr.index;
			double val = getDataValue(idx, axis, data);
			tmpData.set(val, idx);
			if (hasErrors && tmpErrors != null) {
				double err = getDataError(idx, axis, data);
				tmpErrors.set(err, idx);
			}
		}
		if (tmpErrors != null) {
			tmpData.setError(tmpErrors);
		}
		return tmpData;
	}

	@Override
	public Dataset getSaxsPlotAxis(IDataset axis) {
		Dataset tmpAxis = DatasetFactory.zeros(axis.getShape(), Dataset.FLOAT32);
		boolean hasErrors = false;
		Dataset tmpAxisErrors = null;
		if (axis instanceof IErrorDataset && ((IErrorDataset) axis).hasErrors()) {
			tmpAxisErrors = DatasetFactory.zeros(axis.getShape(), Dataset.FLOAT32);
			hasErrors = true;
		}
		IndexIterator itr = tmpAxis.getIterator();
		while (itr.hasNext()) {
			int idx = itr.index;
			double val = getAxisValue(idx, axis);
			tmpAxis.set(val, idx);
			if (hasErrors && tmpAxisErrors != null) {
				double err = getAxisError(idx, axis);
				tmpAxisErrors.set(err, idx);
			}
		}
		if (tmpAxisErrors != null) {
			tmpAxis.setError(tmpAxisErrors);
		}
		return tmpAxis;
	}
}
