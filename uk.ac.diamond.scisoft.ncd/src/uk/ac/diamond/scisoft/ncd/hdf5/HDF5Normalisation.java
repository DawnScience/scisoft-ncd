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

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;
import uk.ac.diamond.scisoft.ncd.Normalisation;

public class HDF5Normalisation extends HDF5ReductionDetector {


	private static final Logger logger = LoggerFactory.getLogger(HDF5Normalisation.class);

	private double normvalue = 1;
	private String calibName;
	private int calibChannel = 1;

	public AbstractDataset parentngd;
	public AbstractDataset calibngd;
	
	public HDF5Normalisation(String name, String key) {
		super(name, key);
	}

	public double getNormvalue() {
		return normvalue;
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

	public AbstractDataset writeout(int dim) {

		try {
			Normalisation nm = new Normalisation();
			nm.setCalibChannel(calibChannel);
			nm.setNormvalue(normvalue);
			int[] dataShape = parentngd.getShape();
			
			parentngd = flattenGridData(parentngd, dim);
			calibngd = flattenGridData(calibngd, 1);
			
			float[] mydata = nm.process(parentngd.getBuffer(), calibngd.getBuffer(), parentngd.getShape()[0], parentngd.getShape(), calibngd.getShape());
			
			int filespace_id = H5.H5Dget_space(ids.dataset_id);
			int type_id = H5.H5Dget_type(ids.dataset_id);
			int memspace_id = H5.H5Screate_simple(ids.block.length, ids.block, null);
			H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET,
					ids.start, ids.stride, ids.count, ids.block);
			H5.H5Dwrite(ids.dataset_id, type_id, memspace_id, filespace_id,
					HDF5Constants.H5P_DEFAULT, mydata);
			
			return new FloatDataset(mydata, dataShape);
			
		} catch (Exception e) {
			logger.error("exception caught reducing data", e);
		}
		
		return null;
		
	}
}
