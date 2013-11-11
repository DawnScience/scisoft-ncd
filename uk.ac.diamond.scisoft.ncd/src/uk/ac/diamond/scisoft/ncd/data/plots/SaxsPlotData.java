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

import org.apache.commons.beanutils.ConvertUtils;
import org.dawb.hdf5.Nexus;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.SliceIterator;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.reduction.LazyDataReduction;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public abstract class SaxsPlotData extends LazyDataReduction {

	protected String groupName, dataName, variableName;
	private int group_id, data_id;
	
	public void execute(int entry_group_id, DataSliceIdentifiers input_ids) throws HDF5Exception {
		
		long[] frames = NcdNexusUtils.getIdsDatasetShape(input_ids);
		int[] frames_int = (int[]) ConvertUtils.convert(frames, int[].class);
		
	    group_id = NcdNexusUtils.makegroup(entry_group_id, detector + "_" + groupName, Nexus.DATA);
	    int type = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_FLOAT);
	    data_id = NcdNexusUtils.makedata(group_id, "data", type, frames.length, frames, true, "a.u.");
		H5.H5Tclose(type);
		
    	SliceSettings sliceSettings = new SliceSettings(frames, frames.length - 2, 1);
		int[] start = new int[frames_int.length];
		int[] step = new int[frames_int.length];
		Arrays.fill(start, 0);
		Arrays.fill(step, 1);
		step[step.length - 1] = frames_int[frames_int.length - 1];
		int[] newShape = AbstractDataset.checkSlice(frames_int, start, frames_int, start, frames_int, step);
		IndexIterator iter = new SliceIterator(frames_int, AbstractDataset.calcSize(frames_int), start, step, newShape);
		while (iter.hasNext()) {
			int[] slice = iter.getPos();
			sliceSettings.setStart(slice);
			AbstractDataset data_slice = NcdNexusUtils.sliceInputData(sliceSettings, input_ids).squeeze();
			
			AbstractDataset tmpFrame = AbstractDataset.zeros(data_slice.getShape(), AbstractDataset.FLOAT32);
			IndexIterator itr = data_slice.getIterator();
			while (itr.hasNext()) {
				int idx = itr.index;
				double val = getDataValue(idx, qaxis, data_slice);
				tmpFrame.set(val, idx);
			}
			
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
		
		writeAxisData();
		
		H5.H5Dclose(data_id);
		H5.H5Gclose(group_id);
	}
	
	private void writeAxisData() throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long[] axisShape = (long[]) ConvertUtils.convert(qaxis.getShape(), long[].class);
		
		AbstractDataset qaxisNew = AbstractDataset.zeros(qaxis.getShape(), AbstractDataset.FLOAT32);
		IndexIterator itr = qaxis.getIterator();
		while (itr.hasNext()) {
			int idx = itr.index;
			double val = getAxisValue(idx, qaxis);
			qaxisNew.set(val, idx);
		}
		
		UnitFormat unitFormat = UnitFormat.getUCUMInstance();
		String units = unitFormat.format(qaxisUnit); 
		int qaxis_id = NcdNexusUtils.makeaxis(group_id, "variable", HDF5Constants.H5T_NATIVE_FLOAT, axisShape.length, axisShape,
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
		
	}
	
	abstract protected double getDataValue(int idx, IDataset axis, IDataset data);
	
	abstract protected double getAxisValue(int idx, IDataset axis);
	
}
