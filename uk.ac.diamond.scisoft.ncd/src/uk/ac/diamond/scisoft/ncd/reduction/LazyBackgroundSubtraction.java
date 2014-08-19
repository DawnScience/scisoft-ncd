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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.dawnsci.hdf5.Nexus;

import uk.ac.diamond.scisoft.analysis.dataset.Dataset;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;
import uk.ac.diamond.scisoft.ncd.core.BackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

public class LazyBackgroundSubtraction extends LazyDataReduction {

	private String bgFile;
	private String bgDetector;
	private Double bgScaling;
	
	public static String name = "BackgroundSubtraction";
	
	public int bg_group_id, bg_data_id, bg_errors_id;
	private int background_file_handle, background_entry_group_id, background_detector_group_id;
	private int background_input_data_id, background_input_errors_id;
	
	// Background data shapes
	public long[] bgFrames;
	public int[] bgFrames_int;
	
	public DataSliceIdentifiers bgIds, bgErrorsIds;

	public void setBgFile(String bgFile) {
		this.bgFile = bgFile;
	}

	public void setBgDetector(String bgDetector) {
		this.bgDetector = bgDetector;
	}

	public void setBgScale(Double bgScaling) {
		this.bgScaling = (bgScaling != null) ? new Double(bgScaling) : null;
	}
	
	public void configure(int dim, long[] frames, int processing_group_id) throws HDF5Exception {
	    bg_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyBackgroundSubtraction.name, Nexus.DETECT);
	    int type = HDF5Constants.H5T_NATIVE_FLOAT;
		bg_data_id = NcdNexusUtils.makedata(bg_group_id, "data", type,
				frames, true, "counts");
	    type = HDF5Constants.H5T_NATIVE_DOUBLE;
		bg_errors_id = NcdNexusUtils.makedata(bg_group_id, "errors", type,
				frames, true, "counts");
		
		int fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
		H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_WEAK);
		background_file_handle = H5.H5Fopen(bgFile, HDF5Constants.H5F_ACC_RDONLY, fapl);
		H5.H5Pclose(fapl);
		background_entry_group_id = H5.H5Gopen(background_file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		background_detector_group_id = H5.H5Gopen(background_entry_group_id, bgDetector, HDF5Constants.H5P_DEFAULT);
		background_input_data_id = H5.H5Dopen(background_detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		background_input_errors_id = H5.H5Dopen(background_detector_group_id, "errors", HDF5Constants.H5P_DEFAULT);
		bgIds = new DataSliceIdentifiers();
		bgIds.setIDs(background_detector_group_id, background_input_data_id);
		bgErrorsIds = new DataSliceIdentifiers();
		bgErrorsIds.setIDs(background_detector_group_id, background_input_errors_id);
	    
	    int bgRank = H5.H5Sget_simple_extent_ndims(bgIds.dataspace_id);
		bgFrames = new long[bgRank];
		H5.H5Sget_simple_extent_dims(bgIds.dataspace_id, bgFrames, null);
		bgFrames_int = (int[]) ConvertUtils.convert(bgFrames, int[].class);
		
		if (qaxis != null) {
			writeQaxisData(bgRank, bg_group_id);
		}
		writeNcdMetadata(bg_group_id);
		
		// Store background filename used in data reduction
		int str_type = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		H5.H5Tset_size(str_type, bgFile.length());
		int metadata_id = NcdNexusUtils.makedata(bg_group_id, "filename", str_type, new long[] {1});
		
		int filespace_id = H5.H5Dget_space(metadata_id);
		int memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(metadata_id, str_type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, bgFile.getBytes());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(str_type);
		H5.H5Dclose(metadata_id);
	}
		
	public void preprocess(int dim, long[] frames, int frameBatch) throws HDF5Exception {
		if (bgFrames != null) {
			if (!Arrays.equals(bgFrames, frames)) {
				ArrayList<Integer> bgAverageIndices = new ArrayList<Integer>();
				int bgRank = bgFrames.length;
				for (int i = (bgRank - dim - 1); i >= 0; i--) {
					int fi = i - bgRank + frames.length;
					if ((bgFrames[i] != 1) && (fi < 0 || (bgFrames[i] != frames[fi]))) {
						bgAverageIndices.add(i + 1);
						bgFrames[i] = 1;
					}
				}
				if (bgAverageIndices.size() > 0) {
					LazyAverage lazyAverage = new LazyAverage();
					lazyAverage.setAverageIndices(ArrayUtils.toPrimitive(bgAverageIndices.toArray(new Integer[] {})));
					lazyAverage.configure(dim, bgFrames_int, bg_group_id, frameBatch);
					lazyAverage.execute(bgIds, bgErrorsIds);
					if (bgIds != null) {
						lazyAverage.writeNcdMetadata(bgIds.datagroup_id);
						if (qaxis != null) {
							lazyAverage.setQaxis(qaxis, qaxisUnit);
							lazyAverage.writeQaxisData(bgFrames.length, bgIds.datagroup_id);
						}
					}

					bgFrames_int = (int[]) ConvertUtils.convert(bgFrames, int[].class);
				}
			}
			
			// Make link to the background dataset and store background filename
			H5.H5Lcreate_external(bgFile, "/entry1/" + bgDetector +  "/data", bg_group_id, "background", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			H5.H5Lcreate_external(bgFile, "/entry1/" + bgDetector +  "/errors", bg_group_id, "background_errors", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		}
	}
	
	public Dataset execute(int dim, Dataset data, Dataset bgData, SliceSettings sliceData, ILock lock) throws HDF5Exception {
		
		if (bgScaling != null) {
			bgData.imultiply(bgScaling);
			if (bgData.hasErrors()) {
				Serializable bgErrorBuffer = bgData.getErrorBuffer();
				if (bgErrorBuffer instanceof Dataset) {
					DoubleDataset bgError = new DoubleDataset((Dataset) bgErrorBuffer);
					bgError.imultiply(bgScaling*bgScaling);
					bgData.setErrorBuffer(bgError);
				}
			} else {
				DoubleDataset bgErrors = new DoubleDataset((double[]) bgData.getBuffer(), bgData.getShape());
				bgErrors.imultiply(bgScaling*bgScaling);
				bgData.setErrorBuffer(bgErrors);
			}
		}
		
			int[] dataShape = data.getShape();

			data = flattenGridData(data, dim);
			Dataset errors = data.getErrorBuffer();
			
			Dataset background = bgData.squeeze();

			BackgroundSubtraction bs = new BackgroundSubtraction();
			bs.setBackground(background);

			int[] flatShape = data.getShape();
			Object[] myobj = bs.process(data.getBuffer(), errors.getBuffer(), flatShape);
			float[] mydata = (float[]) myobj[0];
			double[] myerror = (double[]) myobj[1];
					
			Dataset myres = new FloatDataset(mydata, dataShape);
			myres.setErrorBuffer(myerror);
			
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
				
				int filespace_id = H5.H5Dget_space(bg_data_id);
				int type_id = H5.H5Dget_type(bg_data_id);
				int memspace_id = H5.H5Screate_simple(block.length, block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
				H5.H5Dwrite(bg_data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, myres.getBuffer());
				
				
				int err_filespace_id = H5.H5Dget_space(bg_errors_id);
				int err_type_id = H5.H5Dget_type(bg_errors_id);
				int err_memspace_id = H5.H5Screate_simple(block.length, block, null);
				H5.H5Sselect_hyperslab(err_filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
				H5.H5Dwrite(bg_errors_id, err_type_id, err_memspace_id, err_filespace_id, HDF5Constants.H5P_DEFAULT, myres.getError().getBuffer());
				
			} finally {
				lock.release();
			}

			return myres;
			
	}	
	
	public void complete() throws HDF5LibraryException {
		List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(bg_data_id,
				bg_errors_id,
				bg_group_id,
				background_input_data_id,
				background_input_errors_id,
				background_detector_group_id,
				background_entry_group_id,
				background_file_handle));

		NcdNexusUtils.closeH5idList(identifiers);
	}
}
