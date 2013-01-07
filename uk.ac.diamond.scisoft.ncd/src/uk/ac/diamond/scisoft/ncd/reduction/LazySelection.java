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

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.apache.commons.math3.util.MultidimensionalCounter.Iterator;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class LazySelection extends LazyDataReduction {

	public static String name = "Selection";
	private String format;
	private int[] frames;
	
	public LazySelection(int[] frames) {
		this.frames = Arrays.copyOf(frames, frames.length);
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public DataSliceIdentifiers execute(int dim, DataSliceIdentifiers bgIds, int output_group_id) throws HDF5Exception {
		
		int[] datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
		int[] imageSize = Arrays.copyOfRange(frames, frames.length - dim, frames.length);
		ArrayList<int[]> list = NcdDataUtils.createSliceList(format, datDimMake);
		for (int i = 0; i < datDimMake.length; i++) {
			datDimMake[i] = list.get(i).length;
		}
		long[] bgFramesTotal = (long[]) ConvertUtils.convert(ArrayUtils.addAll(datDimMake, imageSize), long[].class);

		
		long[] block = new long[frames.length];
		block = Arrays.copyOf((long[]) ConvertUtils.convert(frames, long[].class), block.length);
		Arrays.fill(block, 0, block.length - dim, 1);
		int[] block_int = (int[]) ConvertUtils.convert(block, int[].class);
		
		long[] count = new long[frames.length];
		Arrays.fill(count, 1);
		
		int dtype = HDF5Loader.getDtype(bgIds.dataclass_id, bgIds.datasize_id);
		AbstractDataset data = AbstractDataset.zeros(block_int, dtype);
		int output_data_id = NcdNexusUtils.makedata(output_group_id, "data", bgIds.datatype_id, frames.length, bgFramesTotal, true, "counts");
		int output_dataspace_id = H5.H5Dget_space(output_data_id);
		
		MultidimensionalCounter bgFrameCounter = new MultidimensionalCounter(datDimMake);
		Iterator iter = bgFrameCounter.iterator();
		while (iter.hasNext()) {
			iter.next();
			long[] bgFrame = (long[]) ConvertUtils.convert(iter.getCounts(), long[].class);
			long[] gridFrame = new long[datDimMake.length];
			for (int i = 0; i < datDimMake.length; i++) {
				gridFrame[i] = list.get(i)[(int) bgFrame[i]];
			}
			
			long[] start = new long[frames.length];
			start = Arrays.copyOf(gridFrame, frames.length);
			long[] writePosition = new long[frames.length];
			writePosition = Arrays.copyOf(bgFrame, frames.length);

			int memspace_id = H5.H5Screate_simple(block.length, block, null);
			H5.H5Sselect_hyperslab(bgIds.dataspace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
			H5.H5Dread(bgIds.dataset_id, bgIds.datatype_id, memspace_id, bgIds.dataspace_id, HDF5Constants.H5P_DEFAULT,
					data.getBuffer());

			H5.H5Sselect_hyperslab(output_dataspace_id, HDF5Constants.H5S_SELECT_SET, writePosition, block, count,
					block);
			H5.H5Dwrite(output_data_id, bgIds.datatype_id, memspace_id, output_dataspace_id, HDF5Constants.H5P_DEFAULT,
					data.getBuffer());
		}
		
		DataSliceIdentifiers outputIds = new DataSliceIdentifiers();
		outputIds.setIDs(output_group_id, output_data_id);
		return outputIds;
	}
}
