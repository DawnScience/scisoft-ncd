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

import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import uk.ac.diamond.scisoft.ncd.core.DetectorResponse;

public class HDF5DetectorResponse extends HDF5ReductionDetector {

	private static final Logger logger = LoggerFactory.getLogger(HDF5DetectorResponse.class);

	private Dataset response;

	public Dataset getResponse() {
		return response;
	}

	public void setResponse(Dataset response) {
		this.response = response;
	}

	public HDF5DetectorResponse(String name, String key) {
		super(name, key);
	}

	public Dataset writeout(int dim, ILock lock) {
		if (response == null) {
			return null;
		}

		try {
			DetectorResponse dr = new DetectorResponse();
			dr.setResponse(response);
			int[] dataShape = data.getShape();
			
			data = flattenGridData(data, dim);
			Dataset errors = data.getErrorBuffer();
			response = response.squeeze();
			
			if (data.getRank() != response.getRank() + 1) {
				throw new IllegalArgumentException("response of wrong dimensionality");
			}

			int[] flatShape = data.getShape();
			Object[] myobj = dr.process(data.getBuffer(), errors.getBuffer(), flatShape[0], flatShape);
			float[] mydata = (float[]) myobj[0];
			double[] myerrors = (double[]) myobj[1];
			
			Dataset myres = new FloatDataset(mydata, dataShape);
			myres.setErrorBuffer(new DoubleDataset(myerrors, dataShape));
			
			try {
				lock.acquire();
				
				long filespace_id = H5.H5Dget_space(ids.dataset_id);
				long type_id = H5.H5Dget_type(ids.dataset_id);
				long memspace_id = H5.H5Screate_simple(ids.block.length, ids.block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, ids.start, ids.stride, ids.count,
						ids.block);
				H5.H5Dwrite(ids.dataset_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, mydata);
				
				filespace_id = H5.H5Dget_space(errIds.dataset_id);
				type_id = H5.H5Dget_type(errIds.dataset_id);
				memspace_id = H5.H5Screate_simple(errIds.block.length, errIds.block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, errIds.start, errIds.stride, errIds.count,
						errIds.block);
				H5.H5Dwrite(errIds.dataset_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, myres.getError().getBuffer());
			} catch (Exception e) {
				throw e;
			} finally {
				lock.release();
			}

			return myres;
			
		} catch (Exception e) {
			logger.error("exception caugth reducing data", e);
		}
		
		return null;
	}

}
