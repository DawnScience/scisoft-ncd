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

package uk.ac.diamond.scisoft.ncd.hdf5;

import java.util.Arrays;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

import org.eclipse.core.runtime.jobs.ILock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.ncd.Invariant;

/**
 * calculates the total intensity in each frame
 */
public class HDF5Invariant extends HDF5ReductionDetector {

	private static final Logger logger = LoggerFactory.getLogger(HDF5Invariant.class);

	public HDF5Invariant(String name, String key) {
		super(name, key);
	}
	
	@Override
	public void setqAxis(IDataset qAxis, String unit) {
		// Ignore qAxis setting for Invariant subdetector
		this.qAxis = null;
		this.qAxisUnit = null;
	}

	@Override
	public AbstractDataset getqAxis() {
		return null;
	}

	public AbstractDataset[] writeout(int dim, ILock lock) {
		try {
			if (data == null) return null;
			Invariant inv = new Invariant();
			
			int[] dataShape = Arrays.copyOf(data.getShape(), data.getRank() - dim);
			data = flattenGridData(data, dim);
			
			Object[] myobj = inv.process(data.getBuffer(), error.getBuffer(), data.getShape());
			float[] mydata = (float[]) myobj[0];
			float[] myerrors = (float[]) myobj[1];
			try {
				lock.acquire();
				
				int filespace_id = H5.H5Dget_space(ids.dataset_id);
				int type_id = H5.H5Dget_type(ids.dataset_id);
				int memspace_id = H5.H5Screate_simple(ids.block.length, ids.block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, ids.start, ids.stride, ids.count,
						ids.block);
				H5.H5Dwrite(ids.dataset_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, mydata);
				
				filespace_id = H5.H5Dget_space(errIds.dataset_id);
				type_id = H5.H5Dget_type(errIds.dataset_id);
				memspace_id = H5.H5Screate_simple(errIds.block.length, errIds.block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, errIds.start, errIds.stride, errIds.count,
						errIds.block);
				H5.H5Dwrite(errIds.dataset_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, myerrors);
			} catch (Exception e) {
				throw e;
			} finally {
				lock.release();
			}
			
			return new AbstractDataset[] {new FloatDataset(mydata, dataShape), new FloatDataset(myerrors, dataShape)};
			
		} catch (Exception e) {
			logger.error("exception caugth reducing data", e);
		}
		
		return null;
	}
	
}
