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
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import uk.ac.diamond.scisoft.ncd.core.Normalisation;

public class HDF5Normalisation extends HDF5ReductionDetector {


	private static final Logger logger = LoggerFactory.getLogger(HDF5Normalisation.class);

	private double normvalue = 1;
	private String calibName;
	private int calibChannel = 1;

	private Dataset calibngd;
	
	public HDF5Normalisation(String name, String key) {
		super(name, key);
	}

	public double getNormvalue() {
		return normvalue;
	}

	public void setCalibrationData(Dataset dataCal) {
		this.calibngd = dataCal;
		
	}
	
	public void setNormvalue(double normvalue) {
		this.normvalue = normvalue;
	}

	public String getCalibName() {
		return calibName;
	}

	public void setCalibName(String calibName) {
		this.calibName = calibName;
	}

	public int getCalibChannel() {
		return calibChannel;
	}

	public void setCalibChannel(int calibChannel) {
		this.calibChannel = calibChannel;
	}

	public Dataset writeout(int dim, ILock lock) {

		try {
			Normalisation nm = new Normalisation();
			nm.setCalibChannel(calibChannel);
			nm.setNormvalue(normvalue);
			int[] dataShape = data.getShape();
			
			data = flattenGridData(data, dim);
			Dataset errors = data.getErrorBuffer();
			calibngd = flattenGridData(calibngd, 1);
			
			Object[] myobj = nm.process(data.getBuffer(), errors.getBuffer(), calibngd.getBuffer(), data.getShape()[0], data.getShape(), calibngd.getShape());
			float[] mydata = (float[]) myobj[0];
			double[] myerrors = (double[]) myobj[1];
			
			Dataset myres = DatasetFactory.createFromObject(mydata, dataShape);
			myres.setErrorBuffer(DatasetFactory.createFromObject(myerrors, dataShape));
			
			try {
				lock.acquire();
				
				long filespace_id = H5.H5Dget_space(ids.dataset_id);
				long type_id = H5.H5Dget_type(ids.dataset_id);
				long memspace_id = H5.H5Screate_simple(ids.block.length, ids.block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET,
						ids.start, ids.stride, ids.count, ids.block);
				H5.H5Dwrite(ids.dataset_id, type_id, memspace_id, filespace_id,
						HDF5Constants.H5P_DEFAULT, mydata);
				
				filespace_id = H5.H5Dget_space(errIds.dataset_id);
				type_id = H5.H5Dget_type(errIds.dataset_id);
				memspace_id = H5.H5Screate_simple(errIds.block.length, errIds.block, null);
				H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET,
						errIds.start, errIds.stride, errIds.count, errIds.block);
				H5.H5Dwrite(errIds.dataset_id, type_id, memspace_id, filespace_id,
						HDF5Constants.H5P_DEFAULT, myres.getErrors().getBuffer());
				
			} catch (Exception e) {
				throw e;
			} finally {
				lock.release();
			}
			
			return myres;
			
		} catch (Exception e) {
			logger.error("exception caught reducing data", e);
		}
		
		return null;
		
	}

}
