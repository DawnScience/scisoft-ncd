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
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.eclipse.core.runtime.jobs.ILock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.SectorIntegration;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;

public class HDF5SectorIntegration extends HDF5ReductionDetector {

	private static final Logger logger = LoggerFactory.getLogger(HDF5SectorIntegration.class);

	public AbstractDataset parentdata;
	private DataSliceIdentifiers azimuthalIds;
	
	private SectorROI roi;
	private Double gradient, intercept, cameraLength;

	public double getCameraLength() {
		return cameraLength.doubleValue();
	}

	public void setCameraLength(double cameraLength) {
		this.cameraLength = new Double(cameraLength);
	}

	public double[] getCalibrationData() {
		if (gradient != null && intercept != null)
			return new double[] {gradient.doubleValue(), intercept.doubleValue()};
		return null;
	}

	public void setCalibrationData(double gradient, double intercept) {
		this.gradient = new Double(gradient);
		this.intercept =  new Double(intercept);
	}

	public void setAzimuthalIDs(DataSliceIdentifiers input_id) {
		azimuthalIds = new DataSliceIdentifiers(input_id);
	}
	
	@SuppressWarnings("hiding")
	private AbstractDataset mask;
	
	public HDF5SectorIntegration(String name, String key) {
		super(name, key);
		azimuthalIds = new DataSliceIdentifiers();
	}

	public void setMask(IDataset mask) {
		this.mask = DatasetUtils.convertToAbstractDataset(mask);
	}

	@Override
	public AbstractDataset getMask() {
		return mask;
	}

	public void setROI(SectorROI ds) {
		roi = ds;
	}

	public SectorROI getROI() {
		return roi;
	}

	public AbstractDataset[] writeout(int dim, ILock lock) {
		if (roi == null) {
			return null;
		}

		AbstractDataset maskUsed = mask;

		roi.setClippingCompensation(true);

		try {
			AbstractDataset myazdata = null, myraddata = null;

			SectorIntegration sec = new SectorIntegration();
			sec.setROI(roi);
			int[] dataShape = parentdata.getShape();
			
			parentdata = flattenGridData(parentdata, dim);
			
			AbstractDataset[] mydata = sec.process(parentdata, parentdata.getShape()[0], maskUsed, myazdata, myraddata);
			myazdata = DatasetUtils.cast(mydata[0], AbstractDataset.FLOAT32);
			myraddata =  DatasetUtils.cast(mydata[1], AbstractDataset.FLOAT32);
			try {
				lock.acquire();
				writeResults(azimuthalIds, myazdata, dataShape, dim);
				writeResults(ids, myraddata, dataShape, dim);
			} catch (Exception e) {
				throw e;
			} finally {
				lock.release();
			}
			
			int resLength =  dataShape.length - dim + 1;
			int[] resAzShape = Arrays.copyOf(dataShape, resLength);
			int[] resRadShape = Arrays.copyOf(dataShape, resLength);
			resAzShape[resLength - 1] = myazdata.getShape()[myazdata.getRank() - 1];
			resRadShape[resLength - 1] = myraddata.getShape()[myraddata.getRank() - 1];
			
			return new AbstractDataset[] {myazdata.reshape(resAzShape), myraddata.reshape(resRadShape)};
			
		} catch (Exception e) {
			logger.error("exception caught reducing data", e);
		}
		
		return null;
	}
	
	private void writeResults(DataSliceIdentifiers dataIDs, AbstractDataset data, int[] dataShape, int dim) throws HDF5Exception {
		int filespace_id = H5.H5Dget_space(dataIDs.dataset_id);
		int type_id = H5.H5Dget_type(dataIDs.dataset_id);
		
		int resLength =  dataShape.length - dim + 1;
		int integralLength = data.getShape()[data.getRank() - 1];
		
		long[] res_start = Arrays.copyOf(dataIDs.start, resLength);
		long[] res_count = Arrays.copyOf(dataIDs.count, resLength);
		long[] res_block = Arrays.copyOf(dataIDs.block, resLength);
		res_block[resLength - 1] = integralLength;
		
		int memspace_id = H5.H5Screate_simple(resLength, res_block, null);
		H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET,
				res_start, res_block, res_count, res_block);
		
		H5.H5Dwrite(dataIDs.dataset_id, type_id, memspace_id, filespace_id,
				HDF5Constants.H5P_DEFAULT, data.getBuffer());
	}
}
