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

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.dawb.hdf5.Nexus;
import org.eclipse.core.runtime.jobs.ILock;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.core.DetectorResponse;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

public class LazyDetectorResponse extends LazyDataReduction {

	private String drFile;
	private AbstractDataset drData;
	
	public static String name = "DetectorResponse";

	public int dr_group_id, dr_data_id, dr_errors_id;
	
	public void setDrFile(String drFile) {
		this.drFile = drFile;
	}

	public LazyDetectorResponse(String drFile, String detector) {
		if(drFile != null) {
			this.drFile = drFile;
		}
		this.detector = detector;
	}
	
	public void configure(int dim, long[] frames, int entry_group_id, int processing_group_id) throws HDF5Exception {
		
	    dr_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyDetectorResponse.name, Nexus.DETECT);
	    int type = HDF5Constants.H5T_NATIVE_FLOAT;
		dr_data_id = NcdNexusUtils.makedata(dr_group_id, "data", type, frames, true, "counts");
	    type = HDF5Constants.H5T_NATIVE_DOUBLE;
		dr_errors_id = NcdNexusUtils.makedata(dr_group_id, "errors", type, frames, true, "counts");
		
		int fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
		H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_WEAK);
		int nxsfile_handle = H5.H5Fopen(drFile, HDF5Constants.H5F_ACC_RDONLY, fapl);
		H5.H5Pclose(fapl);
		
		int dr_entry_group_id = H5.H5Gopen(nxsfile_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		int dr_instrument_group_id = H5.H5Gopen(dr_entry_group_id, "instrument", HDF5Constants.H5P_DEFAULT);
		int dr_detector_group_id = H5.H5Gopen(dr_instrument_group_id, detector, HDF5Constants.H5P_DEFAULT);
		
		int input_data_id = H5.H5Dopen(dr_detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		int input_dataspace_id = H5.H5Dget_space(input_data_id);
		int input_datatype_id = H5.H5Dget_type(input_data_id);
		int input_dataclass_id = H5.H5Tget_class(input_datatype_id);
		int input_datasize_id = H5.H5Tget_size(input_datatype_id);
		
		int rank = H5.H5Sget_simple_extent_ndims(input_dataspace_id);
		int dtype = HDF5Loader.getDtype(input_dataclass_id, input_datasize_id);
		
		long[] drFrames = new long[rank];
		H5.H5Sget_simple_extent_dims(input_dataspace_id, drFrames, null);
		int memspace_id = H5.H5Screate_simple(rank, drFrames, null);
		
		int[] drFrames_int = (int[]) ConvertUtils.convert(drFrames, int[].class);
		drData = AbstractDataset.zeros(drFrames_int, dtype);
		
		if ((input_data_id >= 0) && (input_dataspace_id >= 0) && (memspace_id >= 0)) {
			H5.H5Dread(input_data_id, input_datatype_id, memspace_id, input_dataspace_id, HDF5Constants.H5P_DEFAULT,
					drData.getBuffer());
		}
		
		H5.H5Sclose(memspace_id);
		H5.H5Sclose(input_dataspace_id);
		H5.H5Tclose(input_datatype_id);
		H5.H5Dclose(input_data_id);
		H5.H5Gclose(dr_detector_group_id);
		H5.H5Gclose(dr_instrument_group_id);
		H5.H5Gclose(dr_entry_group_id);
		H5.H5Fclose(nxsfile_handle);
		
		writeNcdMetadata(dr_group_id);
	}
	
	public AbstractDataset execute(int dim, AbstractDataset data, SliceSettings sliceData, ILock lock) throws HDF5Exception {
		
			DetectorResponse dr = new DetectorResponse();
			int[] dataShape = data.getShape();
			
			data = flattenGridData(data, dim);
			AbstractDataset errors = flattenGridData((AbstractDataset) data.getErrorBuffer(), dim);
			AbstractDataset response = drData.squeeze();
			dr.setResponse(response);
			
			if (data.getRank() != response.getRank() + 1) {
				throw new IllegalArgumentException("response of wrong dimensionality");
			}

			int[] flatShape = data.getShape();
			Object[] myobj = dr.process(data.getBuffer(), errors.getBuffer(), flatShape[0], flatShape);
			float[] mydata = (float[]) myobj[0];
			double[] myerrors = (double[]) myobj[1];
			
			AbstractDataset myres = new FloatDataset(mydata, dataShape);
			myres.setErrorBuffer(new DoubleDataset(myerrors, dataShape));
			
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

				int filespace_id = H5.H5Dget_space(dr_data_id);
				int type_id = H5.H5Dget_type(dr_data_id);
				int memspace_id = H5.H5Screate_simple(block.length, block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
				H5.H5Dwrite(dr_data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, mydata);
				
				filespace_id = H5.H5Dget_space(dr_errors_id);
				type_id = H5.H5Dget_type(dr_errors_id);
				memspace_id = H5.H5Screate_simple(block.length, block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
				H5.H5Dwrite(dr_errors_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, myres.getError().getBuffer());
			} finally {
				lock.release();
			}

			return myres;
	}
	
	
	public void complete() throws HDF5LibraryException {
		List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(dr_data_id,
				dr_errors_id,
				dr_group_id));

			NcdNexusUtils.closeH5idList(identifiers);
	}
}
