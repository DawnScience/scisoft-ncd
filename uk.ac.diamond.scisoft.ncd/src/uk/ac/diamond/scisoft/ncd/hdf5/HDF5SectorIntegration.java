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

	private AbstractDataset[] areaData;
	private DataSliceIdentifiers azimuthalIds, azimuthalErrorsIds;
	
	private SectorROI roi;
	private Double gradient, intercept, cameraLength;
	
	private boolean calculateRadial = true;
	private boolean calculateAzimuthal = true;
	private boolean fast = true;

	public double getCameraLength() {
		return cameraLength.doubleValue();
	}

	public void setCameraLength(double cameraLength) {
		this.cameraLength = new Double(cameraLength);
	}

	public double[] getCalibrationData() {
		if (gradient != null && intercept != null) {
			return new double[] {gradient.doubleValue(), intercept.doubleValue()};
		}
		return null;
	}

	public void setCalibrationData(double gradient, double intercept) {
		this.gradient = new Double(gradient);
		this.intercept =  new Double(intercept);
	}

	public void setAzimuthalIDs(DataSliceIdentifiers input_id, DataSliceIdentifiers input_error_id) {
		azimuthalIds = new DataSliceIdentifiers(input_id);
		azimuthalErrorsIds = new DataSliceIdentifiers(input_error_id);
	}
	
	@SuppressWarnings("hiding")
	private AbstractDataset mask;
	
	public HDF5SectorIntegration(String name, String key) {
		super(name, key);
		azimuthalIds = new DataSliceIdentifiers();
		azimuthalErrorsIds = new DataSliceIdentifiers();
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

	public void setAreaData(AbstractDataset... area) {
		this.areaData = new AbstractDataset[2];
		this.areaData[0] = area[0];
		this.areaData[1] = area[1];
	}

	public void setCalculateRadial(boolean calculateRadial) {
		this.calculateRadial = calculateRadial;
	}

	public void setCalculateAzimuthal(boolean calculateAzimuthal) {
		this.calculateAzimuthal = calculateAzimuthal;
	}

	public void setFast(boolean fast) {
		this.fast = fast;
	}

	public AbstractDataset[] writeout(int dim, ILock lock) {
		if (roi == null) {
			return null;
		}

		AbstractDataset maskUsed = mask;

		roi.setClippingCompensation(true);

		try {
			AbstractDataset myazdata = null, myazerrors = null;
			AbstractDataset myraddata = null, myraderrors = null;

			SectorIntegration sec = new SectorIntegration();
			sec.setROI(roi);
			sec.setAreaData(areaData);
			sec.setCalculateRadial(calculateRadial);
			sec.setCalculateAzimuthal(calculateAzimuthal);
			sec.setFast(fast);
			int[] dataShape = data.getShape();
			
			data = flattenGridData(data, dim);
			data.setError(flattenGridData(data.getError(), dim));
			
			roi.setAverageArea(false);
			AbstractDataset[] mydata = sec.process(data, data.getShape()[0], maskUsed);
			AbstractDataset[] myerrors = sec.process(data.getError(), data.getShape()[0], maskUsed);
			if (calculateAzimuthal) {
				myazdata = DatasetUtils.cast(mydata[0], AbstractDataset.FLOAT32);
				myazerrors = DatasetUtils.cast(myerrors[0], AbstractDataset.FLOAT64);
			}
			if (calculateRadial) {
				myraddata =  DatasetUtils.cast(mydata[1], AbstractDataset.FLOAT32);
				myraderrors =  DatasetUtils.cast(myerrors[1], AbstractDataset.FLOAT64);
			}
			try {
				lock.acquire();
				if (calculateAzimuthal) {
					writeResults(azimuthalIds, myazdata, dataShape, dim);
					writeResults(azimuthalErrorsIds, myazerrors, dataShape, dim);
				}
				if(calculateRadial) {
					writeResults(ids, myraddata, dataShape, dim);
					writeResults(errIds, myraderrors, dataShape, dim);
				}
			} catch (Exception e) {
				throw e;
			} finally {
				lock.release();
			}
			
			int resLength =  dataShape.length - dim + 1;
			if (calculateAzimuthal && myazdata != null) {
				int[] resAzShape = Arrays.copyOf(dataShape, resLength);
				resAzShape[resLength - 1] = myazdata.getShape()[myazdata.getRank() - 1];
				myazdata = myazdata.reshape(resAzShape);
				if (myazerrors != null) {
					myazerrors = myazerrors.reshape(resAzShape);
				}
				myazdata.setError(myazerrors);
			}
			if (calculateRadial && myraddata != null) {
				int[] resRadShape = Arrays.copyOf(dataShape, resLength);
				resRadShape[resLength - 1] = myraddata.getShape()[myraddata.getRank() - 1];
				myraddata = myraddata.reshape(resRadShape);
				if (myraderrors != null) {
					myraderrors = myraderrors.reshape(resRadShape);
				}
				myraddata.setError(myraderrors);
			}
			
			return new AbstractDataset[] {myazdata, myraddata};
			
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
