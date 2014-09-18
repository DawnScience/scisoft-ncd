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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;

import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

public class LazySelection extends LazyDataReduction {

	public static String name = "Selection";
	private String format;
	private int[] frames;
	
	private IProgressMonitor monitor = new NullProgressMonitor();
	
	public LazySelection(int[] frames) {
		this.frames = Arrays.copyOf(frames, frames.length);
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public void setMonitor(IProgressMonitor monitor) {
		this.monitor = monitor;
	}

	public DataSliceIdentifiers[] execute(int dim, DataSliceIdentifiers ids, DataSliceIdentifiers error_ids, int output_group_id) throws HDF5Exception {
		
		int[] datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
		int[] imageSize = Arrays.copyOfRange(frames, frames.length - dim, frames.length);
		ArrayList<int[]> list = NcdDataUtils.createSliceList(format, datDimMake);
		for (int i = 0; i < datDimMake.length; i++) {
			datDimMake[i] = list.get(i).length;
		}
		long[] framesTotal = (long[]) ConvertUtils.convert(ArrayUtils.addAll(datDimMake, imageSize), long[].class);

		
		long[] block = new long[frames.length];
		block = Arrays.copyOf((long[]) ConvertUtils.convert(frames, long[].class), block.length);
		Arrays.fill(block, 0, block.length - dim, 1);
		int[] block_int = (int[]) ConvertUtils.convert(block, int[].class);
		
		long[] count = new long[frames.length];
		Arrays.fill(count, 1);
		
		int dtype = HDF5Loader.getDtype(ids.dataclass_id, ids.datasize_id);
		Dataset data = DatasetFactory.zeros(block_int, dtype);
		int output_data_id = NcdNexusUtils.makedata(output_group_id, "data", ids.datatype_id, framesTotal, true, "counts");
		int output_dataspace_id = H5.H5Dget_space(output_data_id);
		
		Dataset errors = DatasetFactory.zeros(block_int, dtype);
		int errors_datatype_id = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_DOUBLE);
		int errors_data_id = NcdNexusUtils.makedata(output_group_id, "errors", errors_datatype_id, framesTotal, true, "counts");
		int errors_dataspace_id = H5.H5Dget_space(errors_data_id);
		
		MultidimensionalCounter frameCounter = new MultidimensionalCounter(datDimMake);
		Iterator iter = frameCounter.iterator();
		while (iter.hasNext()) {
			iter.next();
			long[] frame = (long[]) ConvertUtils.convert(iter.getCounts(), long[].class);
			long[] gridFrame = new long[datDimMake.length];
			for (int i = 0; i < datDimMake.length; i++) {
				gridFrame[i] = list.get(i)[(int) frame[i]];
			}
			
			long[] start = new long[frames.length];
			start = Arrays.copyOf(gridFrame, frames.length);
			long[] writePosition = new long[frames.length];
			writePosition = Arrays.copyOf(frame, frames.length);

			int memspace_id = H5.H5Screate_simple(block.length, block, null);
			H5.H5Sselect_hyperslab(ids.dataspace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
			H5.H5Dread(ids.dataset_id, ids.datatype_id, memspace_id, ids.dataspace_id, HDF5Constants.H5P_DEFAULT,
					data.getBuffer());
			int errors_memspace_id = H5.H5Screate_simple(block.length, block, null);
			if (error_ids.dataset_id >= 0) {
				H5.H5Sselect_hyperslab(error_ids.dataspace_id, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
				H5.H5Dread(error_ids.dataset_id, error_ids.datatype_id, errors_memspace_id, error_ids.dataspace_id, HDF5Constants.H5P_DEFAULT,
						errors.getBuffer());
				data.setError(errors);
			} else {
				Object obj = DatasetUtils.createJavaArray(data);
				Dataset error = DatasetFactory.createFromObject(obj);
				error.ipower(0.5);
				data.setError(error);
			}

			H5.H5Sselect_hyperslab(output_dataspace_id, HDF5Constants.H5S_SELECT_SET, writePosition, block, count,
					block);
			H5.H5Dwrite(output_data_id, ids.datatype_id, memspace_id, output_dataspace_id, HDF5Constants.H5P_DEFAULT,
					data.getBuffer());
			
			H5.H5Sselect_hyperslab(errors_dataspace_id, HDF5Constants.H5S_SELECT_SET, writePosition, block, count,
					block);
			H5.H5Dwrite(errors_data_id, errors_datatype_id, errors_memspace_id, errors_dataspace_id, HDF5Constants.H5P_DEFAULT,
					data.getError().getBuffer());
			
			if (monitor.isCanceled()) {
				return null;
			}
			
			monitor.worked(1);
			
		}
		
		DataSliceIdentifiers outputDataIds = new DataSliceIdentifiers();
		outputDataIds.setIDs(output_group_id, output_data_id);
		DataSliceIdentifiers outputErrorsIds = new DataSliceIdentifiers();
		outputErrorsIds.setIDs(output_group_id, errors_data_id);
		return new DataSliceIdentifiers[] {outputDataIds, outputErrorsIds};
	}
}
