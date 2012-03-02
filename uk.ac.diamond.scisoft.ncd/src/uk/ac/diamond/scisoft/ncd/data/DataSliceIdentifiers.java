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

package uk.ac.diamond.scisoft.ncd.data;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

public class DataSliceIdentifiers {
	
	public int dataset_id, dataspace_id, dataclass_id, datatype_id, datasize_id;
	public long[] start, stride, count, block;
	
	
	public DataSliceIdentifiers() {
		super();
	}
	
	
	public DataSliceIdentifiers(DataSliceIdentifiers ids) {
		super();
		this.dataset_id = ids.dataset_id;
		this.dataspace_id = ids.dataspace_id;
		this.dataclass_id = ids.dataclass_id;
		this.datatype_id = ids.datatype_id;
		this.datasize_id = ids.datasize_id;
		this.start = ids.start;
		this.stride = ids.stride;
		this.count = ids.count;
		this.block = ids.block;
	}
	
	
	public DataSliceIdentifiers(int dataset_id, long[] start, long[] stride, long[] count, long[] block) {
		super();
		this.dataset_id = dataset_id;
		this.start = start;
		this.stride = stride;
		this.count = count;
		this.block = block;
	}
	
	public void setIDs(int dataset_id) throws HDF5LibraryException {
		this.dataset_id = dataset_id;
		dataspace_id = H5.H5Dget_space(dataset_id);
		datatype_id = H5.H5Dget_type(dataset_id);
		dataclass_id = H5.H5Tget_class(datatype_id);
		datasize_id = H5.H5Tget_size(datatype_id);
	}
	
	public void setSlice (long[] start, long[] stride, long[] count, long[] block) {
		this.start = start;
		this.stride = stride;
		this.count = count;
		this.block = block;
	}
}
