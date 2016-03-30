/*
 * Copyright 2012 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.core.data;

import java.util.Arrays;

import org.apache.commons.beanutils.ConvertUtils;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.exceptions.HDF5LibraryException;

public class DataSliceIdentifiers {
	
	public long datagroup_id = -1;
	public long dataset_id = -1;
	public long dataspace_id = -1;
	public int dataclass_id = -1;
	public long datatype_id = -1;
	public int datasize_id = -1;
	public long[] start, stride, count, block;
	
	
	public DataSliceIdentifiers() {
		super();
	}
	
	
	public DataSliceIdentifiers(DataSliceIdentifiers ids) {
		super();
		if (ids != null) {
			this.datagroup_id = ids.datagroup_id;
			this.dataset_id = ids.dataset_id;
			this.dataspace_id = ids.dataspace_id;
			this.dataclass_id = ids.dataclass_id;
			this.datatype_id = ids.datatype_id;
			this.datasize_id = ids.datasize_id;
			if (ids.start  != null) {
				this.start  = Arrays.copyOf(ids.start, ids.start.length);
			}
			if (ids.stride != null) {
				this.stride = Arrays.copyOf(ids.stride, ids.stride.length);
			}
			if (ids.count  != null) {
				this.count  = Arrays.copyOf(ids.count, ids.count.length);
			}
			if (ids.block  != null) {
				this.block  = Arrays.copyOf(ids.block, ids.block.length);
			}
		}
	}
	
	
	public void setIDs(long datagroup_id, long dataset_id) throws HDF5LibraryException {
		this.datagroup_id = datagroup_id;
		this.dataset_id = dataset_id;
		if (dataset_id != -1) {
			dataspace_id = H5.H5Dget_space(dataset_id);
			datatype_id = H5.H5Dget_type(dataset_id);
			dataclass_id = H5.H5Tget_class(datatype_id);
			datasize_id = (int) H5.H5Tget_size(datatype_id);
		}
	}
	
	public void setSlice(SliceSettings slice) {
		long[] frames = slice.getFrames();
		long[] start_pos = (long[]) ConvertUtils.convert(slice.getStart(), long[].class);
		int sliceDim = slice.getSliceDim();
		int sliceSize = slice.getSliceSize();
		
		long[] start_data = Arrays.copyOf(start_pos, frames.length);

		long[] block_data = Arrays.copyOf(frames, frames.length);
		Arrays.fill(block_data, 0, slice.getSliceDim(), 1);
		block_data[sliceDim] = Math.min(frames[sliceDim] - start_pos[sliceDim], sliceSize);

		long[] count_data = new long[frames.length];
		Arrays.fill(count_data, 1);

		setSlice(start_data, block_data, count_data, block_data);
	}
	
	public void setSlice (long[] start, long[] stride, long[] count, long[] block) {
		this.start = Arrays.copyOf(start, start.length);
		this.stride = Arrays.copyOf(stride, stride.length);
		this.count = Arrays.copyOf(count, count.length);
		this.block = Arrays.copyOf(block, block.length);
	}
}
