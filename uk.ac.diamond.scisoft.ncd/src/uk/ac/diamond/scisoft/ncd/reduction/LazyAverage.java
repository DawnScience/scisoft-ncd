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
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.dawnsci.hdf.object.Nexus;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IndexIterator;
import org.eclipse.january.dataset.ShapeUtils;
import org.eclipse.january.dataset.SliceIterator;
import org.eclipse.january.dataset.SliceND;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

public class LazyAverage extends LazyDataReduction {

	public static final String name = "Average";
	
	private int[] averageIndices;
	private long ave_group_id, ave_data_id, ave_errors_id;
	
	private IProgressMonitor monitor = new NullProgressMonitor();

	private int sliceDim;
	private int sliceSize;

	private int dim;
	private long[] frames;
	private int[] frames_int;
	private long[] framesAve;
	private int[] framesAve_int;

	
	public int[] getAverageIndices() {
		return averageIndices;
	}

	public void setAverageIndices(int[] averageIndices) {
		this.averageIndices = Arrays.copyOf(averageIndices, averageIndices.length);
	}

	public void setMonitor(IProgressMonitor monitor) {
		this.monitor = monitor;
	}

	public void configure(int dimension, int[] inputFrames, long processing_group_id, int frameBatch) throws HDF5Exception {
		
		dim = dimension;
		frames_int = inputFrames;
		frames = (long[]) ConvertUtils.convert(frames_int, long[].class);
		
		// Calculate shape of the averaged dataset based on the dimensions selected for averaging
		framesAve = Arrays.copyOf(frames, frames.length);
		for (int idx : averageIndices) {
			framesAve[idx - 1] = 1;
		}
		
		framesAve_int = (int[]) ConvertUtils.convert(framesAve, int[].class);
		
		ave_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyAverage.name, Nexus.DETECT);
		long type = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_FLOAT);
		ave_data_id = NcdNexusUtils.makedata(ave_group_id, "data", type, framesAve, true, "counts");
		ave_errors_id = NcdNexusUtils.makedata(ave_group_id, "errors", type, framesAve, true, "counts");
		H5.H5Tclose(type);
		
		sliceDim = 0;
		sliceSize = frames_int[0];

		// We will slice only 2D data. 1D data is loaded into memory completely
		if (averageIndices.length > 0 && dim == 2) {
			// Find dimension that needs to be sliced
			int dimCounter = 1;
			for (int idx = (frames.length - 1 - dim); idx >= 0; idx--) {
				if (ArrayUtils.contains(averageIndices, idx + 1)) {
					sliceDim = idx;
					sliceSize = frames_int[idx];
					dimCounter *= frames[idx];
					if (dimCounter >= frameBatch) {
						sliceSize = frameBatch * frames_int[idx] / dimCounter;
						break;
					}
				}
			}
		}
	}
	
	public void execute(DataSliceIdentifiers input_ids, DataSliceIdentifiers input_errors_ids) throws HDF5Exception {
		
		// Loop over dimensions that aren't averaged
		int[] iter_array = Arrays.copyOf(framesAve_int, framesAve_int.length);
		int[] step = Arrays.copyOf(framesAve_int, framesAve_int.length);
		Arrays.fill(step, 0, framesAve_int.length - dim, 1);
		SliceND slice = new SliceND(iter_array, null, iter_array, step);
		IndexIterator iter = new SliceIterator(iter_array, ShapeUtils.calcSize(iter_array), slice);
		
		// This loop iterates over the output averaged dataset image by image
		while (iter.hasNext()) {
			
			if (monitor.isCanceled()) {
				return;
			}
			
			int[] currentFrame = iter.getPos();
			int[] data_stop = Arrays.copyOf(currentFrame, currentFrame.length);
			long[] data_iter_array = Arrays.copyOf(frames, frames.length);
			Arrays.fill(data_iter_array, 0, frames.length - dim, 1);
			for (int i = 0; i < currentFrame.length; i++) {
				if (i < currentFrame.length - dim) {
					data_stop[i]++;
				} else {
					data_stop[i] = frames_int[i];
				}
			}
			int[] data_start = Arrays.copyOf(currentFrame, currentFrame.length);
			int[] data_step = Arrays.copyOf(step, currentFrame.length);
			Arrays.fill(data_step, 0, currentFrame.length - dim, 1);
			for (int idx : averageIndices) {
				int i = idx - 1;
				data_start[i] = 0;
				data_stop[i] = frames_int[i];
				data_iter_array[i] = frames_int[i];
				if (i > sliceDim) {
					data_step[i] = frames_int[i];
				} else {
					if (i == sliceDim) {
						data_step[i] = sliceSize;
					}
				}
			}
			
			slice = new SliceND(data_stop, data_start, data_stop, data_step);
			IndexIterator data_iter = new SliceIterator(data_stop, ShapeUtils.calcSize(data_stop), slice);
			
			int[] aveShape = Arrays.copyOfRange(framesAve_int, sliceDim + 1, framesAve_int.length);
			Dataset ave_frame = DatasetFactory.zeros(aveShape, Dataset.FLOAT32);
			Dataset ave_errors_frame = DatasetFactory.zeros(aveShape, Dataset.FLOAT32);
			
			// This loop iterates over chunks of data that need to be averaged for the current output image
			int totalFrames = 0;
			SliceSettings sliceSettings = new SliceSettings(data_iter_array, sliceDim, sliceSize);
			while (data_iter.hasNext()) {
				
				if (monitor.isCanceled()) {
					return;
				}
				
				sliceSettings.setStart(data_iter.getPos());
				Dataset data_slice = NcdNexusUtils.sliceInputData(sliceSettings, input_ids);
				Dataset errors_slice;
				if (input_errors_ids.dataset_id != -1) {
					errors_slice = NcdNexusUtils.sliceInputData(sliceSettings, input_errors_ids);
					errors_slice.ipower(2);
				} else {
					errors_slice = data_slice.clone();
				}
				int data_slice_rank = data_slice.getRank();
				
				if (monitor.isCanceled()) {
					return;
				}
				
				int totalFramesBatch = 1;
				for (int idx = (data_slice_rank - dim - 1); idx >= sliceDim; idx--) {
					if (ArrayUtils.contains(averageIndices, idx + 1)) {
						totalFramesBatch *= data_slice.getShape()[idx];
						data_slice = data_slice.sum(idx);
						errors_slice = errors_slice.sum(idx);
					}
				}
				totalFrames += totalFramesBatch;
				ave_frame = ave_frame.iadd(data_slice);
				ave_errors_frame = ave_errors_frame.iadd(errors_slice);
			}
			
			if (monitor.isCanceled()) {
				return;
			}
			
			ave_frame =  ave_frame.idivide(totalFrames);
			ave_errors_frame =  ave_errors_frame.ipower(0.5).idivide(totalFrames);
			
			if (monitor.isCanceled()) {
				return;
			}
			
			long filespace_id = H5.H5Dget_space(ave_data_id);
			long type_id = H5.H5Dget_type(ave_data_id);
			long[] ave_start = (long[]) ConvertUtils.convert(currentFrame, long[].class);
			long[] ave_step = (long[]) ConvertUtils.convert(step, long[].class);
			long[] ave_count_data = new long[frames.length];
			Arrays.fill(ave_count_data, 1);
			long memspace_id = H5.H5Screate_simple(ave_step.length, ave_step, null);
			
			H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, ave_start, ave_step, ave_count_data,
					ave_step);
			H5.H5Dwrite(ave_data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT,
					ave_frame.getBuffer());
			
			H5.H5Sclose(filespace_id);
			H5.H5Sclose(memspace_id);
			H5.H5Tclose(type_id);
			
			filespace_id = H5.H5Dget_space(ave_errors_id);
			type_id = H5.H5Dget_type(ave_errors_id);
			memspace_id = H5.H5Screate_simple(ave_step.length, ave_step, null);
			
			H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, ave_start, ave_step, ave_count_data,
					ave_step);
			H5.H5Dwrite(ave_errors_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT,
					ave_errors_frame.getBuffer());
			
			H5.H5Sclose(filespace_id);
			H5.H5Sclose(memspace_id);
			H5.H5Tclose(type_id);
			
			monitor.worked(1);
		}
		
		input_ids.setIDs(ave_group_id, ave_data_id);
		input_errors_ids.setIDs(ave_group_id, ave_errors_id);
	}

	public void complete() throws HDF5LibraryException {
		List<Long> identifiers = new ArrayList<Long>(Arrays.asList(ave_data_id,
				ave_errors_id,
				ave_group_id));
		
		NcdNexusUtils.closeH5idList(identifiers);
	}
}
