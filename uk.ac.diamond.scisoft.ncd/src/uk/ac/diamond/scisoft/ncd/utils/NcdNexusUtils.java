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

package uk.ac.diamond.scisoft.ncd.utils;

import java.util.ArrayList;
import java.util.Arrays;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.apache.commons.math3.util.MultidimensionalCounter.Iterator;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;

public class NcdNexusUtils {

	public static int makegroup(int parent_id, String name, String nxclass) throws HDF5Exception {
		
		if (parent_id < 0) {
			throw new HDF5Exception("Illegal parent group id");
		}
		int open_group_id = -1;
		int dataspace_id = -1;
		int datatype_id = -1;
		int attribute_id = -1;
		
		open_group_id = H5.H5Gcreate(parent_id, name, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT);
		if (open_group_id < 0) {
			throw new HDF5Exception("H5 makegroup error: can't create a group");
		}
		byte[] nxdata = nxclass.getBytes();
		dataspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
		datatype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		H5.H5Tset_size(datatype_id, nxdata.length);
		
		attribute_id = H5.H5Acreate(open_group_id, "NX_class", datatype_id, dataspace_id, HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT);
		if (attribute_id < 0) {
			throw new HDF5Exception("H5 putattr write error: can't create attribute");
		}
		int write_id = H5.H5Awrite(attribute_id, datatype_id, nxdata);
		if (write_id < 0) {
			throw new HDF5Exception("H5 makegroup attribute write error: can't create NXclass attribute");
		}
		H5.H5Aclose(attribute_id);
		H5.H5Sclose(dataspace_id);
		H5.H5Tclose(datatype_id);
		
		return open_group_id;
	}
	
	public static int makedata(int parent_id, String name, int type, int rank, long[] dim) throws HDF5Exception {
		if (parent_id < 0) {
			throw new HDF5Exception("Illegal parent group id");
		}
		int dataspace_id = H5.H5Screate_simple(rank, dim, null);
		if (dataspace_id < 0) {
			throw new HDF5Exception("H5 makedata error: failed to allocate space for dataset");
		}
		int dcpl_id = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);
		//if (dcpl_id >= 0)
		//	H5.H5Pset_chunk(dcpl_id, rank, dim);

		int dataset_id = H5.H5Dcreate(parent_id, name, type, dataspace_id, HDF5Constants.H5P_DEFAULT, dcpl_id,
				HDF5Constants.H5P_DEFAULT);
		if (dataset_id < 0) {
			throw new HDF5Exception("H5 makedata error: failed to create dataset");
		}
		H5.H5Sclose(dataspace_id);
		H5.H5Pclose(dcpl_id);
		
		return dataset_id;
	}
		
	public static int makedata(int parent_id, String name, int type, int rank, long[] dim, boolean signal, String units) throws HDF5Exception {
		if (parent_id < 0) {
			throw new HDF5Exception("Illegal parent group id");
		}
		int dataset_id = makedata(parent_id, name, type, rank, dim);
		if (dataset_id < 0) {
			throw new HDF5Exception("H5 makedata error: failed to create dataset");
		}
		// add signal attribute
		{
			int attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_INT32);
			
			int attr_id = H5.H5Acreate(dataset_id, "signal", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, signal ? new int[] {1} : new int[] {0});
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
			H5.H5Aclose(attr_id);
			H5.H5Sclose(attrspace_id);
			H5.H5Tclose(attrtype_id);
		}
		
		// add units attribute
		{
			int attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attrtype_id, units.length());
			
			int attr_id = H5.H5Acreate(dataset_id, "units", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, units.getBytes());
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
			H5.H5Aclose(attr_id);
			H5.H5Sclose(attrspace_id);
			H5.H5Tclose(attrtype_id);
		}
		return dataset_id;
	}
	
	public static int makeaxis(int parent_id, String name, int type, int rank, long[] dim, int[] axis, int primary, String units) throws HDF5Exception {
		if (parent_id < 0) {
			throw new HDF5Exception("Illegal parent group id");
		}
		int dataspace_id = H5.H5Screate_simple(rank, dim, null);
		if (dataspace_id < 0) {
			throw new HDF5Exception("H5 makedata error: failed to allocate space for dataset");
		}
		int dcpl_id = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);
		//if (dcpl_id >= 0)
		//	H5.H5Pset_chunk(dcpl_id, rank, dim);

		int dataset_id = H5.H5Dcreate(parent_id, name, type, dataspace_id, HDF5Constants.H5P_DEFAULT, dcpl_id,
				HDF5Constants.H5P_DEFAULT);
		if (dataset_id < 0) {
			throw new HDF5Exception("H5 makedata error: failed to create dataset");
		}
		H5.H5Sclose(dataspace_id);
		H5.H5Pclose(dcpl_id);
		
		// add axis attribute
		{
			int attrspace_id = H5.H5Screate_simple(1, new long[] { axis.length }, null);
			int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_INT32);
			
			int attr_id = H5.H5Acreate(dataset_id, "axis", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, axis);
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
			H5.H5Aclose(attr_id);
			H5.H5Sclose(attrspace_id);
			H5.H5Tclose(attrtype_id);
		}
		
		// add primary attribute
		{
			int attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_INT32);
			
			int attr_id = H5.H5Acreate(dataset_id, "primary", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, new int[] {primary});
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
			H5.H5Aclose(attr_id);
			H5.H5Sclose(attrspace_id);
			H5.H5Tclose(attrtype_id);
		}
		
		// add units attribute
		{
			int attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attrtype_id, units.length());
			
			int attr_id = H5.H5Acreate(dataset_id, "units", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, units.getBytes());
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
			H5.H5Aclose(attr_id);
			H5.H5Sclose(attrspace_id);
			H5.H5Tclose(attrtype_id);
		}
		return dataset_id;
	}
	
	public static DataSliceIdentifiers[] readDataId(String dataFile, String detector, String dataset, String errors) throws HDF5Exception {
		int file_handle = H5.H5Fopen(dataFile, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		int entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		//int instrument_group_id = H5.H5Gopen(entry_group_id, "instrument", HDF5Constants.H5P_DEFAULT);
		//int detector_group_id = H5.H5Gopen(instrument_group_id, detector, HDF5Constants.H5P_DEFAULT);
		int detector_group_id = H5.H5Gopen(entry_group_id, detector, HDF5Constants.H5P_DEFAULT);
		int input_data_id = H5.H5Dopen(detector_group_id, dataset, HDF5Constants.H5P_DEFAULT);
		int input_errors_id = -1;
		if (errors != null) {
			input_errors_id = H5.H5Dopen(detector_group_id, errors, HDF5Constants.H5P_DEFAULT);
		}
		
		DataSliceIdentifiers ids = new DataSliceIdentifiers();
		ids.setIDs(detector_group_id, input_data_id);
		DataSliceIdentifiers errors_ids = null;
		if (errors != null) {
			errors_ids = new DataSliceIdentifiers();
			errors_ids.setIDs(detector_group_id, input_errors_id);
		}
		return new DataSliceIdentifiers[] {ids, errors_ids};
	}
	
	public static AbstractDataset sliceInputData(SliceSettings sliceData, DataSliceIdentifiers ids) throws HDF5Exception {
		long[] frames = sliceData.getFrames();
		long[] start_pos = (long[]) ConvertUtils.convert(sliceData.getStart(), long[].class);
		int sliceDim = sliceData.getSliceDim();
		int sliceSize = sliceData.getSliceSize();
		
		long[] start_data = Arrays.copyOf(start_pos, frames.length);

		long[] block_data = Arrays.copyOf(frames, frames.length);
		Arrays.fill(block_data, 0, sliceData.getSliceDim(), 1);
		block_data[sliceDim] = Math.min(frames[sliceDim] - start_pos[sliceDim], sliceSize);

		long[] count_data = new long[frames.length];
		Arrays.fill(count_data, 1);

		ids.setSlice(start_data, block_data, count_data, block_data);

		H5.H5Sselect_hyperslab(ids.dataspace_id, HDF5Constants.H5S_SELECT_SET, ids.start, ids.stride, ids.count,
				ids.block);
		int rank = block_data.length;
		int dtype = HDF5Loader.getDtype(ids.dataclass_id, ids.datasize_id);
		int[] block_data_int = (int[]) ConvertUtils.convert(ids.block, int[].class);
		AbstractDataset data = AbstractDataset.zeros(block_data_int, dtype);
		int memspace_id = H5.H5Screate_simple(rank, ids.block, null);
		// Read the data using the previously defined hyperslab.
		if ((ids.dataset_id >= 0) && (ids.dataspace_id >= 0) && (memspace_id >= 0)) {
			H5.H5Dread(ids.dataset_id, ids.datatype_id, memspace_id, ids.dataspace_id, HDF5Constants.H5P_DEFAULT,
					data.getBuffer());
		}
		return data;
	}
	
	public static AbstractDataset sliceInputData(int dim, int[] frames, String format, DataSliceIdentifiers ids) throws HDF5Exception {
		int[] datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
		int[] imageSize = Arrays.copyOfRange(frames, frames.length - dim, frames.length);
		ArrayList<int[]> list = NcdDataUtils.createSliceList(format, datDimMake);
		for (int i = 0; i < datDimMake.length; i++)
			datDimMake[i] = list.get(i).length;
		int[] framesTotal = ArrayUtils.addAll(datDimMake, imageSize);

		
		long[] block = new long[frames.length];
		block = Arrays.copyOf((long[]) ConvertUtils.convert(frames, long[].class), block.length);
		Arrays.fill(block, 0, block.length - dim, 1);
		int[] block_int = (int[]) ConvertUtils.convert(block, int[].class);
		
		long[] count = new long[frames.length];
		Arrays.fill(count, 1);
		
		int dtype = HDF5Loader.getDtype(ids.dataclass_id, ids.datasize_id);
		AbstractDataset data = AbstractDataset.zeros(block_int, dtype);
		AbstractDataset result = null;
		
		MultidimensionalCounter bgFrameCounter = new MultidimensionalCounter(datDimMake);
		Iterator iter = bgFrameCounter.iterator();
		while (iter.hasNext()) {
			iter.next();
			long[] bgFrame = (long[]) ConvertUtils.convert(iter.getCounts(), long[].class);
			long[] gridFrame = new long[datDimMake.length];
			for (int i = 0; i < datDimMake.length; i++)
				gridFrame[i] = list.get(i)[(int) bgFrame[i]];
			
				long[] start = new long[frames.length];
				start = Arrays.copyOf(gridFrame, frames.length);
				
				int memspace_id = H5.H5Screate_simple(block.length, block, null);
				H5.H5Sselect_hyperslab(ids.dataspace_id, HDF5Constants.H5S_SELECT_SET,
						start, block, count, block);
				H5.H5Dread(ids.dataset_id, ids.datatype_id, memspace_id, ids.dataspace_id,
						HDF5Constants.H5P_DEFAULT, data.getBuffer());
				if (result == null) {
					result = data.clone();
				} else {
					result = DatasetUtils.append(result, data, block.length - dim - 1);
				}
		}
		
		if (result != null) {
			result.setShape(framesTotal);
		}
		return result;
	}
	
}
