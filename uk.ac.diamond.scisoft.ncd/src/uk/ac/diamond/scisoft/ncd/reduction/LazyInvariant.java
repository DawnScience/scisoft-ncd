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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.dawnsci.hdf.object.Nexus;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;
import uk.ac.diamond.scisoft.ncd.core.Invariant;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

public class LazyInvariant extends LazyDataReduction {

	public static String name = "Invariant";
	
	private long inv_group_id, inv_data_id, inv_errors_id;

	public long[] invFrames;
	
	public void configure(int dim, long[] frames, long entry_group_id, long processing_group_id) throws HDF5Exception {
	    inv_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyInvariant.name, Nexus.DETECT);
	    long type = HDF5Constants.H5T_NATIVE_FLOAT;
		invFrames = Arrays.copyOf(frames, frames.length - dim);
		inv_data_id = NcdNexusUtils.makedata(inv_group_id, "data", type, invFrames, true, "counts");
	    type = HDF5Constants.H5T_NATIVE_DOUBLE;
		inv_errors_id = NcdNexusUtils.makedata(inv_group_id, "errors", type, invFrames, true, "counts");
		
		writeNcdMetadata(inv_group_id);
	}
	
	public Dataset execute(int dim, Dataset data, SliceSettings sliceData, ILock lock) throws HDF5Exception {
		
			Invariant inv = new Invariant();
			
			int[] dataShape = Arrays.copyOf(data.getShape(), data.getRank() - dim);
			data = flattenGridData(data, dim);
			Dataset errors = data.getErrorBuffer();
			
			Object[] myobj = inv.process(data.getBuffer(), errors.getBuffer(), data.getShape());
			float[] mydata = (float[]) myobj[0];
			double[] myerrors = (double[]) myobj[1];
			
			Dataset myres = DatasetFactory.createFromObject(mydata, dataShape);
			myres.setErrorBuffer(DatasetFactory.createFromObject(myerrors, dataShape));
			
			try {
				lock.acquire();
				
				long[] frames = sliceData.getFrames();
				long[] start_pos = (long[]) ConvertUtils.convert(sliceData.getStart(), long[].class);
				int sliceDim = sliceData.getSliceDim();
				int sliceSize = sliceData.getSliceSize();

				long[] start = Arrays.copyOf(start_pos, frames.length);

				long[] block = Arrays.copyOf(frames, frames.length);
				Arrays.fill(block, 0, sliceData.getSliceDim(), 1);
				block[sliceDim] = Math.min(frames[sliceDim] - start_pos[sliceDim], sliceSize);

				long[] count = new long[frames.length];
				Arrays.fill(count, 1);

				long filespace_id = H5.H5Dget_space(inv_data_id);
				long type_id = H5.H5Dget_type(inv_data_id);
				long memspace_id = H5.H5Screate_simple(block.length, block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count,	block);
				H5.H5Dwrite(inv_data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, mydata);
				
				filespace_id = H5.H5Dget_space(inv_errors_id);
				type_id = H5.H5Dget_type(inv_errors_id);
				memspace_id = H5.H5Screate_simple(block.length, block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
				H5.H5Dwrite(inv_errors_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, myres.getErrors().getBuffer());
			} finally {
				lock.release();
			}
			
			return myres;
	}
	
	
	public void complete() throws HDF5LibraryException {
		List<Long> identifiers = new ArrayList<Long>(Arrays.asList(inv_data_id,
				inv_errors_id,
				inv_group_id));
		
		NcdNexusUtils.closeH5idList(identifiers);
	}	
}
