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
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.hdf5.Nexus;

import uk.ac.diamond.scisoft.ncd.core.Normalisation;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

public class LazyNormalisation extends LazyDataReduction {
	
	protected String calibration;
	private Double absScaling;
	protected int normChannel;
	
	public static String name = "Normalisation";
	
	private int calibration_group_id, input_calibration_id;
	public int norm_group_id, norm_data_id, norm_errors_id;
	private DataSliceIdentifiers calibration_ids;

	// Normalisation data shapes
	private int rankCal;
	private long[] framesCal;
	
	public void setCalibration(String calibration) {
		this.calibration = calibration;
	}

	public void setAbsScaling(Double absScaling) {
		this.absScaling = absScaling;
	}

	public void setNormChannel(int normChannel) {
		this.normChannel = normChannel;
	}

	public void configure(int dim, long[] frames, int entry_group_id, int processing_group_id) throws HDF5Exception {
		calibration_group_id = H5.H5Gopen(entry_group_id, calibration, HDF5Constants.H5P_DEFAULT);
		input_calibration_id = H5.H5Dopen(calibration_group_id, "data", HDF5Constants.H5P_DEFAULT);
		calibration_ids = new DataSliceIdentifiers();
		calibration_ids.setIDs(calibration_group_id, input_calibration_id);
		
		rankCal = H5.H5Sget_simple_extent_ndims(calibration_ids.dataspace_id);
		long[] tmpFramesCal = new long[rankCal];
		H5.H5Sget_simple_extent_dims(calibration_ids.dataspace_id, tmpFramesCal, null);
		
		// This is a workaround to add extra dimensions to the end of scaler data shape
		// to match them with scan data dimensions
		int extraDims = frames.length - dim + 1 - rankCal;
		if (extraDims > 0) {
			rankCal += extraDims;
			for (int dm = 0; dm < extraDims; dm++) {
				tmpFramesCal = ArrayUtils.add(tmpFramesCal, 1);
			}
		}
		framesCal = Arrays.copyOf(tmpFramesCal, rankCal);
		
		for (int i = 0; i < frames.length - dim; i++) {
			if (frames[i] != framesCal[i]) {
				frames[i] = Math.min(frames[i], framesCal[i]);
			}
		}
		
	    norm_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyNormalisation.name, Nexus.DETECT);
	    int type = HDF5Constants.H5T_NATIVE_FLOAT;
		norm_data_id = NcdNexusUtils.makedata(norm_group_id, "data", type, frames, true, "counts");
	    type = HDF5Constants.H5T_NATIVE_DOUBLE;
		norm_errors_id = NcdNexusUtils.makedata(norm_group_id, "errors", type, frames, true, "counts");
		
		setAbsScaling(absScaling);
		setNormChannel(normChannel);
		
		if (qaxis != null) {
			setQaxis(qaxis, qaxisUnit);
			writeQaxisData(frames.length, norm_group_id);
		}
		writeNcdMetadata(norm_group_id);
	}
	
	public Dataset execute(int dim, Dataset data, SliceSettings sliceData, ILock lock) throws HDF5Exception {
			Normalisation nm = new Normalisation();
			nm.setCalibChannel(normChannel);
			if(absScaling != null) {
				nm.setNormvalue(absScaling);
			}
			int[] dataShape = data.getShape();
			
			data = flattenGridData(data, dim);
			Dataset errors = data.getErrorBuffer();
			
			SliceSettings calibrationSliceParams = new SliceSettings(sliceData);
			calibrationSliceParams.setFrames(framesCal);
			Dataset dataCal = NcdNexusUtils.sliceInputData(calibrationSliceParams, calibration_ids);
			Dataset calibngd = flattenGridData(dataCal, 1);
			
			Object[] myobj = nm.process(data.getBuffer(), errors.getBuffer(), calibngd.getBuffer(), data.getShape()[0], data.getShape(), calibngd.getShape());
			float[] mydata = (float[]) myobj[0];
			double[] myerrors = (double[]) myobj[1];
			
			Dataset myres = new FloatDataset(mydata, dataShape);
			myres.setErrorBuffer(new DoubleDataset(myerrors, dataShape));

			int filespace_id = -1;
			int type_id = -1;
			int memspace_id = -1;
			int select_id = -1;
			int write_id = -1;
			
			try{
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

				filespace_id = H5.H5Dget_space(norm_data_id);
				type_id = H5.H5Dget_type(norm_data_id);
				memspace_id = H5.H5Screate_simple(block.length, block, null);
				
				select_id = H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET,
						start, block, count, block);
				if (select_id < 0) {
					throw new HDF5Exception("Failed to allocate space fro writing Normalisation data");
				}
				
				write_id = H5.H5Dwrite(norm_data_id, type_id, memspace_id, filespace_id,
						HDF5Constants.H5P_DEFAULT, mydata);
				if (write_id < 0) {
					throw new HDF5Exception("Failed to write Normalisation data into the results file");
				}
				
				NcdNexusUtils.closeH5idList(new ArrayList<Integer>(Arrays.asList(memspace_id, type_id, filespace_id)));
				
				filespace_id = H5.H5Dget_space(norm_errors_id);
				type_id = H5.H5Dget_type(norm_errors_id);
				memspace_id = H5.H5Screate_simple(block.length, block, null);
				select_id = H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET,
						start, block, count, block);
				if (select_id < 0) {
					throw new HDF5Exception("Failed to allocate space for writing Normalisation error data");
				}
				write_id = H5.H5Dwrite(norm_errors_id, type_id, memspace_id, filespace_id,
						HDF5Constants.H5P_DEFAULT, myres.getError().getBuffer());
				if (write_id < 0) {
					throw new HDF5Exception("Failed to write Normalisation error data into the results file");
				}
				
			} finally {
				lock.release();
				NcdNexusUtils.closeH5idList(new ArrayList<Integer>(Arrays.asList(memspace_id, type_id, filespace_id)));
			}
			
			return myres;
	}
}
