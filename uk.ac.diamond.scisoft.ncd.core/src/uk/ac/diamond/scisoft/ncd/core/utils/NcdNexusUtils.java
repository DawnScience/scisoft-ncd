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

package uk.ac.diamond.scisoft.ncd.core.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.dawnsci.hdf5.HDF5Utils;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;

public final class NcdNexusUtils {

	private NcdNexusUtils() {
	}

	public static long makegroup(long parent_id, String name, String nxclass) throws HDF5Exception {

		if (parent_id < 0) {
			throw new HDF5Exception("Illegal parent group id");
		}
		long open_group_id = -1;
		long dataspace_id = -1;
		long datatype_id = -1;
		long attribute_id = -1;

		try {
			open_group_id = H5.H5Gcreate(parent_id, name, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (open_group_id < 0) {
				throw new HDF5Exception("H5 makegroup error: can't create a group");
			}
			byte[] nxdata = nxclass.getBytes();
			dataspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			datatype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(datatype_id, nxdata.length);

			attribute_id = H5.H5Acreate(open_group_id, "NX_class", datatype_id, dataspace_id,
					HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			if (attribute_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attribute_id, datatype_id, nxdata);
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create NXclass attribute");
			}
		} finally {
			closeH5idList(new ArrayList<Long>(Arrays.asList(attribute_id, dataspace_id, datatype_id)));
		}

		return open_group_id;
	}

	public static long makedata(long parent_id, String name, long type, long[] dim) throws HDF5Exception {
		if (parent_id < 0) {
			throw new HDF5Exception("Illegal parent group id");
		}

		long dcpl_id = -1;
		long dataspace_id = -1;
		long dataset_id = -1;

		try {
			dataspace_id = H5.H5Screate_simple(dim.length, dim, null);
			if (dataspace_id < 0) {
				throw new HDF5Exception("H5 makedata error: failed to allocate space for dataset");
			}
			dcpl_id = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);
			// if (dcpl_id >= 0)
			// H5.H5Pset_chunk(dcpl_id, rank, dim);

			dataset_id = H5.H5Dcreate(parent_id, name, type, dataspace_id, HDF5Constants.H5P_DEFAULT, dcpl_id,
					HDF5Constants.H5P_DEFAULT);
			if (dataset_id < 0) {
				throw new HDF5Exception("H5 makedata error: failed to create dataset");
			}
		} finally {
			try {
				closeH5id(dataspace_id);
			} finally {
				if (dcpl_id > 0) {
					H5.H5Pclose(dcpl_id);
				}
			}
		}
		return dataset_id;
	}

	public static long makedata(long parent_id, String name, long type, long[] dim, boolean signal, String units)
			throws HDF5Exception {
		if (parent_id < 0) {
			throw new HDF5Exception("Illegal parent group id");
		}
		long dataset_id = makedata(parent_id, name, type, dim);
		if (dataset_id < 0) {
			throw new HDF5Exception("H5 makedata error: failed to create dataset");
		}
		// add signal attribute
		long attrspace_id = -1;
		long attrtype_id = -1;
		long attr_id = -1;
		try {
			attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_INT32);

			attr_id = H5.H5Acreate(dataset_id, "signal", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, signal ? new int[] { 1 } : new int[] { 0 });
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
		} finally {
			closeH5idList(new ArrayList<Long>(Arrays.asList(attr_id, attrspace_id, attrtype_id)));
		}

		// add units attribute
		try {
			attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attrtype_id, units.length());

			attr_id = H5.H5Acreate(dataset_id, "units", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, units.getBytes());
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
		} finally {
			closeH5idList(new ArrayList<Long>(Arrays.asList(attr_id, attrspace_id, attrtype_id)));
		}
		return dataset_id;
	}

	public static long makeaxis(long parent_id, String name, long type, long[] dim, int[] axis, int primary, String units)
			throws HDF5Exception {
		if (parent_id < 0) {
			throw new HDF5Exception("Illegal parent group id");
		}
		
		long dataspace_id = -1;
		long dcpl_id = -1;
		long dataset_id = -1;

		try {
			dataspace_id = H5.H5Screate_simple(dim.length, dim, null);
			if (dataspace_id < 0) {
				throw new HDF5Exception("H5 makedata error: failed to allocate space for dataset");
			}
			dcpl_id = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);
			// if (dcpl_id >= 0)
			// H5.H5Pset_chunk(dcpl_id, rank, dim);

			dataset_id = H5.H5Dcreate(parent_id, name, type, dataspace_id, HDF5Constants.H5P_DEFAULT, dcpl_id,
					HDF5Constants.H5P_DEFAULT);
			if (dataset_id < 0) {
				throw new HDF5Exception("H5 makedata error: failed to create dataset");
			}
		} finally {
			try {
				closeH5id(dataspace_id);
			} finally {
				if (dcpl_id > 0) {
					H5.H5Pclose(dcpl_id);
				}
			}
		}

		// add axis attribute
		long attrspace_id = -1;
		long attrtype_id = -1;
		long attr_id = -1;
		try {
			attrspace_id = H5.H5Screate_simple(1, new long[] { axis.length }, null);
			attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_INT32);

			attr_id = H5.H5Acreate(dataset_id, "axis", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, axis);
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
		} finally {
			closeH5idList(new ArrayList<Long>(Arrays.asList(attr_id, attrspace_id, attrtype_id)));
		}

		// add primary attribute
		try {
			attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_INT32);

			attr_id = H5.H5Acreate(dataset_id, "primary", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, new int[] { primary });
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
		} finally {
			closeH5idList(new ArrayList<Long>(Arrays.asList(attr_id, attrspace_id, attrtype_id)));
		}

		// add units attribute
		try {
			attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attrtype_id, units.length());

			attr_id = H5.H5Acreate(dataset_id, "units", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, units.getBytes());
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
		} finally {
			closeH5idList(new ArrayList<Long>(Arrays.asList(attr_id, attrspace_id, attrtype_id)));
		}
		return dataset_id;
	}

	public static void makelink(long inputDataset, long resultGroup) throws HDF5LibraryException {
		if (inputDataset > 0) {
			final long type = H5.H5Iget_type(inputDataset);
			if (type != HDF5Constants.H5I_BADID) {
				String[] name = new String[] {""};
				final long nameSize = H5.H5Iget_name(inputDataset, name, 1L) + 1;
				H5.H5Iget_name(inputDataset, name, nameSize);
				String[] nameTree = name[0].split("/");
				String inputFileName = H5.H5Fget_name(inputDataset); 
				String resultFileName = H5.H5Fget_name(resultGroup);
				if (inputFileName.equals(resultFileName)) {
					H5.H5Lcreate_hard(
							inputDataset,
							"./",
							resultGroup,
							nameTree[nameTree.length -1],
							HDF5Constants.H5P_DEFAULT,
							HDF5Constants.H5P_DEFAULT);
				} else {
					H5.H5Lcreate_external(
							inputFileName,
							name[0],
							resultGroup,
							nameTree[nameTree.length -1],
							HDF5Constants.H5P_DEFAULT,
							HDF5Constants.H5P_DEFAULT);
				}
			}
		}
	}
	
	public static long[] getIdsDatasetShape(long dataspace_id) throws HDF5LibraryException {
		final int ndims = H5.H5Sget_simple_extent_ndims(dataspace_id);
		long[] dims = new long[ndims];
		long[] maxdims = new long[ndims];
		H5.H5Sget_simple_extent_dims(dataspace_id, dims, maxdims);
		return dims;
	}

	public static Dataset sliceInputData(SliceSettings sliceData, DataSliceIdentifiers ids)
			throws HDF5Exception {
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
		
		Dataset data;
		long memspace_id = -1;
		try {
			int select_id = H5.H5Sselect_hyperslab(ids.dataspace_id, HDF5Constants.H5S_SELECT_SET, ids.start,
					ids.stride, ids.count, ids.block);
			if (select_id < 0) {
				throw new HDF5Exception("H5 select hyperslab error: can't allocate memory to read data");
			}
			
			int rank = block_data.length;
			int dtype = HDF5Utils.getDtype(ids.dataclass_id, ids.datasize_id);
			int[] block_data_int = (int[]) ConvertUtils.convert(ids.block, int[].class);
			data = DatasetFactory.zeros(block_data_int, dtype);
			memspace_id = H5.H5Screate_simple(rank, ids.block, null);
			// Read the data using the previously defined hyperslab.
			if ((ids.dataset_id > 0) && (ids.dataspace_id > 0) && (memspace_id > 0)) {
				int read_id = H5.H5Dread(ids.dataset_id, ids.datatype_id, memspace_id, ids.dataspace_id,
						HDF5Constants.H5P_DEFAULT, data.getBuffer());
				if (read_id < 0) {
					throw new HDF5Exception("H5 data read error: can't read input dataset");
				}
			}
		} finally {
			closeH5id(memspace_id);
		}
		return data;
	}

	public static void closeH5id(long id) throws HDF5LibraryException {
		if (id > 0) {
			final long type = H5.H5Iget_type(id);
			if (type != HDF5Constants.H5I_BADID) {
				final int ref = H5.H5Iget_ref(id);
				if (ref > 0) {
					if (type == HDF5Constants.H5I_ATTR) {
						H5.H5Aclose(id);
						return;
					}
					if (type == HDF5Constants.H5I_DATASPACE) {
						H5.H5Sclose(id);
						return;
					}
					if (type == HDF5Constants.H5I_DATATYPE) {
						H5.H5Tclose(id);
						return;
					}
					if (type == HDF5Constants.H5I_DATASET) {
						H5.H5Dclose(id);
						return;
					}
					if (type == HDF5Constants.H5I_GROUP) {
						H5.H5Gclose(id);
						return;
					}
					if (type == HDF5Constants.H5I_FILE) {
						H5.H5Fclose(id);
						return;
					}
				}
			}
		}
	}

	public static void closeH5idList(List<Long> identifiers) throws HDF5LibraryException {

		if (identifiers == null || identifiers.isEmpty()) {
			return;
		}

		Iterator<Long> itr = identifiers.iterator();
		Long id = itr.next();
		try {
			NcdNexusUtils.closeH5id(id);
		} finally {
			itr.remove();
			closeH5idList(identifiers);
		}
	}

}
