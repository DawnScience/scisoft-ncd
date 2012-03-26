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

import java.util.Arrays;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.IntegerDataset;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class LazyAverage extends LazyDataReduction {

	public static String name = "Average";
	private int[] averageIndices;
	
	public int[] getAverageIndices() {
		return averageIndices;
	}

	public void setAverageIndices(int[] averageIndices) {
		this.averageIndices = averageIndices;
	}

	public void execute(int dim, int[] frames_int, int processing_group_id, int frameBatch, DataSliceIdentifiers input_ids) throws NullPointerException, HDF5Exception {
		
		long[] frames = (long[]) ConvertUtils.convert(frames_int, long[].class);
		
		// Calculate shape of the averaged dataset based on the dimensions selected for averaging
		long[] framesAve = Arrays.copyOf(frames, frames.length);
		for (int idx : averageIndices)
			framesAve[idx - 1] = 1;
		
		int[] framesAve_int = (int[]) ConvertUtils.convert(framesAve, int[].class);
		
	    int ave_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyAverage.name, "NXdetector");
	    int type = HDF5Constants.H5T_NATIVE_FLOAT;
		int ave_data_id = NcdNexusUtils.makedata(ave_group_id, "data", type, framesAve.length, framesAve, true, "counts");
		
		// Loop over dimensions that aren't averaged
		int[] iter_array = Arrays.copyOf(framesAve_int, framesAve_int.length);
		int[] start = new int[iter_array.length];
		int[] step = Arrays.copyOf(framesAve_int, framesAve_int.length);
		Arrays.fill(start, 0);
		Arrays.fill(step, 0, framesAve_int.length - dim, 1);
		IntegerDataset idx_dataset = new IntegerDataset(iter_array);
		IndexIterator iter = idx_dataset.getSliceIterator(start, iter_array, step);
		
		int sliceDim = 0;
		int sliceSize = frames_int[0];

		// We will slice only 2D data. 1D data is loaded into memory completely
		if (averageIndices.length > 0 || dim == 2) {
			// Find dimension that needs to be sliced
			int dimCounter = 1;
			for (int idx = (frames.length - 1 - dim); idx >= 0; idx--) {
				if (ArrayUtils.contains(averageIndices, idx + 1)) {
					dimCounter *= frames[idx];
					if (dimCounter >= frameBatch) {
						sliceDim = idx;
						sliceSize = frameBatch * frames_int[idx] / dimCounter;
						break;
					}
				}
			}
		}
		
		// This look iterates over the output averaged dataset image by image
		while (iter.hasNext()) {
			
			int[] currentFrame = iter.getPos();
			int[] data_stop = Arrays.copyOf(currentFrame, currentFrame.length);
			long[] data_iter_array = Arrays.copyOf(frames, frames.length);
			Arrays.fill(data_iter_array, 0, frames.length - dim, 1);
			for (int i = 0; i < currentFrame.length; i++)
				if (i < currentFrame.length - dim)
					data_stop[i]++;
				else
					data_stop[i] = frames_int[i];
			
			int[] data_start = Arrays.copyOf(currentFrame, currentFrame.length);
			int[] data_step = Arrays.copyOf(step, currentFrame.length);
			Arrays.fill(data_step, 0, currentFrame.length - dim, 1);
			for (int idx : averageIndices) {
				int i = idx - 1;
				data_start[i] = 0;
				data_stop[i] = frames_int[i];
				data_iter_array[i] = frames_int[i];
				if (i > sliceDim)
					data_step[i] = frames_int[i];
				else if (i == sliceDim)
					data_step[i] = sliceSize;
			}
			
			IntegerDataset data_idx_dataset = new IntegerDataset(data_stop);
			IndexIterator data_iter = data_idx_dataset.getSliceIterator(data_start, data_stop, data_step);
			
			int[] aveShape = Arrays.copyOfRange(framesAve_int, framesAve_int.length - dim, framesAve_int.length);
			AbstractDataset ave_frame = AbstractDataset.zeros(aveShape, AbstractDataset.FLOAT32);
			
			// This loop iterates over chunks of data that need to be averaged for the current output image
			int totalFrames = 0;
	    	SliceSettings sliceSettings = new SliceSettings(data_iter_array, sliceDim, sliceSize);
			while (data_iter.hasNext()) {
				sliceSettings.setStart(data_iter.getPos());
				AbstractDataset data_slice = NcdNexusUtils.sliceInputData(sliceSettings, input_ids);
				int data_slice_rank = data_slice.getRank();
				
				int totalFramesBatch = 1;
				for (int idx = (data_slice_rank - dim - 1); idx >= sliceDim; idx--)
					if (ArrayUtils.contains(averageIndices, idx + 1)) {
						totalFramesBatch *= data_slice.getShape()[idx];
						data_slice = data_slice.sum(idx);
					}
				totalFrames += totalFramesBatch;
				ave_frame = ave_frame.iadd(data_slice);
			}
			ave_frame =  ave_frame.idivide(totalFrames);
			
			int filespace_id = H5.H5Dget_space(ave_data_id);
			int type_id = H5.H5Dget_type(ave_data_id);
			long[] ave_start = (long[]) ConvertUtils.convert(currentFrame, long[].class);
			long[] ave_step = (long[]) ConvertUtils.convert(step, long[].class);
			long[] ave_count_data = new long[frames.length];
			Arrays.fill(ave_count_data, 1);
			
			int memspace_id = H5.H5Screate_simple(ave_step.length, ave_step, null);
			H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET,
					ave_start, ave_step, ave_count_data, ave_step);
			H5.H5Dwrite(ave_data_id, type_id, memspace_id, filespace_id,
					HDF5Constants.H5P_DEFAULT, ave_frame.getBuffer());
		}
		
		input_ids.setIDs(ave_group_id, ave_data_id);
	}

}
