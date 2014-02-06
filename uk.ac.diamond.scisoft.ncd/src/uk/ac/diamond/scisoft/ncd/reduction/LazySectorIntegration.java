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
import java.util.List;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;
import javax.measure.unit.UnitFormat;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.dawb.hdf5.Nexus;
import org.eclipse.core.runtime.jobs.ILock;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.core.SectorIntegration;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

public class LazySectorIntegration extends LazyDataReduction {

	private SectorROI intSector;
	private AbstractDataset[] areaData;
	private Amount<ScatteringVectorOverDistance> gradient;
	private Amount<ScatteringVector> intercept;
	private Amount<Length> cameraLength;
	private Amount<Energy> energy;
	
	private boolean calculateRadial = true;
	private boolean calculateAzimuthal = true;
	private boolean fast = true;
	
	private int sec_group_id, sec_data_id, sec_errors_id, az_data_id, az_errors_id;

	public static String name = "SectorIntegration";

	public void setIntSector(SectorROI intSector) {
		this.intSector = intSector.copy();
		this.intSector.setAverageArea(false);
		this.intSector.setClippingCompensation(true);
	}

	public void setAreaData(AbstractDataset... area) {
		this.areaData = new AbstractDataset[2];
		this.areaData[0] = area[0];
		this.areaData[1] = area[1];
	}

	public void setCalculateRadial(boolean calulcateRadial) {
		this.calculateRadial = calulcateRadial;
	}

	public void setCalculateAzimuthal(boolean calculateAzimuthal) {
		this.calculateAzimuthal = calculateAzimuthal;
	}

	public void setFast(boolean fast) {
		this.fast = fast;
	}
	
	public Object[] getCalibrationData() {
		if (gradient != null && intercept != null) {
			return new Object[] {gradient.copy(), intercept.copy()};
		}
		return null;
	}

	public void setCalibrationData(Amount<ScatteringVectorOverDistance> slope, Amount<ScatteringVector> intercept) {
		if (slope != null) {
			this.gradient = slope.copy();
		}
		if (intercept != null) {
			this.intercept =  intercept.copy();
		}
	}

	public void setCameraLength(Amount<Length> cameraLength) {
		if (cameraLength != null) {
			this.cameraLength = cameraLength.copy();
		}
	}

	public void setEnergy(Amount<Energy> energy) {
		if (energy != null) {
			this.energy = energy.copy();
		}
	}

	public void configure(int dim, long[] frames, int processing_group_id) throws HDF5Exception {
	    sec_group_id = NcdNexusUtils.makegroup(processing_group_id, LazySectorIntegration.name, Nexus.DETECT);
		int typeFloat = HDF5Constants.H5T_NATIVE_FLOAT;
		int typeDouble = HDF5Constants.H5T_NATIVE_DOUBLE;
		int[] intRadii = intSector.getIntRadii();
		double[] radii = intSector.getRadii();
		double dpp = intSector.getDpp();
		int secRank = frames.length - dim + 1;
		long[] secFrames = Arrays.copyOf(frames, secRank);
		secFrames[secRank - 1] = intRadii[1] - intRadii[0] + 1;
		sec_data_id = NcdNexusUtils.makedata(sec_group_id, "data", typeFloat, secFrames, true, "counts");
		sec_errors_id = NcdNexusUtils.makedata(sec_group_id, "errors", typeDouble, secFrames, false, "counts");
		
		double[] angles = intSector.getAngles();
		long[] azFrames = Arrays.copyOf(frames, secRank);
		if (intSector.getSymmetry() == SectorROI.FULL) {
			angles[1] = angles[0] + 2 * Math.PI;
		}
		azFrames[secRank - 1] = (int) Math.ceil((angles[1] - angles[0]) * radii[1] * dpp);
		az_data_id = NcdNexusUtils.makedata(sec_group_id, "azimuth", typeFloat, azFrames, false, "counts");
		az_errors_id = NcdNexusUtils.makedata(sec_group_id, "azimuth_errors", typeDouble, azFrames, false, "counts");
		
		int[] areaShape = (int[]) ConvertUtils.convert(Arrays.copyOfRange(frames, frames.length - dim, frames.length), int[].class); 
		areaData = ROIProfile.area(areaShape, AbstractDataset.FLOAT32, mask, intSector, calculateRadial, calculateAzimuthal, fast);
		
		if (qaxis != null) {
			writeQaxisData(secRank, sec_group_id);
		}
		writeNcdMetadata(sec_group_id);
	}
	
	public AbstractDataset[] execute(int dim, AbstractDataset inputData, SliceSettings currentSliceParams, ILock lock) throws Exception {
		
		DataSliceIdentifiers sector_id = new DataSliceIdentifiers();
		sector_id.setIDs(sec_group_id, sec_data_id);
		sector_id.setSlice(currentSliceParams);
		DataSliceIdentifiers err_sector_id = new DataSliceIdentifiers();
		err_sector_id.setIDs(sec_group_id, sec_errors_id);
		err_sector_id.setSlice(currentSliceParams);
		
		DataSliceIdentifiers azimuth_id = new DataSliceIdentifiers();
		azimuth_id.setIDs(sec_group_id, az_data_id);
		azimuth_id.setSlice(currentSliceParams);
		DataSliceIdentifiers err_azimuth_id = new DataSliceIdentifiers();
		err_azimuth_id.setIDs(sec_group_id, az_errors_id);
		err_azimuth_id.setSlice(currentSliceParams);
		
			AbstractDataset myazdata = null, myazerrors = null;
			AbstractDataset myraddata = null, myraderrors = null;

			SectorIntegration sec = new SectorIntegration();
			sec.setROI(intSector);
			sec.setAreaData(areaData);
			sec.setCalculateRadial(calculateRadial);
			sec.setCalculateAzimuthal(calculateAzimuthal);
			sec.setFast(fast);
			int[] dataShape = inputData.getShape();
			
			AbstractDataset data = flattenGridData(inputData, dim);
			if (inputData.hasErrors()) {
				AbstractDataset errors = flattenGridData((AbstractDataset) inputData.getErrorBuffer(), dim);
				data.setErrorBuffer(errors);
			}
			
			AbstractDataset[] mydata = sec.process(data, data.getShape()[0], mask);
			int resLength =  dataShape.length - dim + 1;
			if (calculateAzimuthal) {
				myazdata = DatasetUtils.cast(mydata[0], AbstractDataset.FLOAT32);
				if (myazdata != null) {
					if (myazdata.hasErrors()) {
						myazerrors = DatasetUtils.cast((AbstractDataset) mydata[0].getErrorBuffer(), AbstractDataset.FLOAT64);
					}
					
					int[] resAzShape = Arrays.copyOf(dataShape, resLength);
					resAzShape[resLength - 1] = myazdata.getShape()[myazdata.getRank() - 1];
					myazdata = myazdata.reshape(resAzShape);
					
					if (myazerrors != null) {
						myazerrors = myazerrors.reshape(resAzShape);
						myazdata.setErrorBuffer(myazerrors);
					}
				}
			}
			if (calculateRadial) {
				myraddata =  DatasetUtils.cast(mydata[1], AbstractDataset.FLOAT32);
				if (myraddata != null) {
					if (myraddata.hasErrors()) {
						myraderrors =  DatasetUtils.cast((AbstractDataset) mydata[1].getErrorBuffer(), AbstractDataset.FLOAT64);
					}
					int[] resRadShape = Arrays.copyOf(dataShape, resLength);
					resRadShape[resLength - 1] = myraddata.getShape()[myraddata.getRank() - 1];
					myraddata = myraddata.reshape(resRadShape);
					if (myraderrors != null) {
						myraderrors = myraderrors.reshape(resRadShape);
						myraddata.setErrorBuffer(myraderrors);
					}
				}
			}
			try {
				lock.acquire();
				if (calculateAzimuthal && myazdata != null) {
					writeResults(azimuth_id, myazdata, dataShape, dim);
					if (myazdata.hasErrors()) {
						writeResults(err_azimuth_id, myazdata.getError(), dataShape, dim);
					}
				}
				if(calculateRadial && myraddata != null) {
					writeResults(sector_id, myraddata, dataShape, dim);
					if (myraddata.hasErrors()) {
						writeResults(err_sector_id, myraddata.getError(), dataShape, dim);
					}
				}
			} catch (Exception e) {
				throw e;
			} finally {
				lock.release();
			}
			
			
			return new AbstractDataset[] {myazdata, myraddata};
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
	
	@Override
	public void writeNcdMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		super.writeNcdMetadata(datagroup_id);

		if (intSector != null) {
			writeBeamCenterMetadata(datagroup_id);
			writeIntegrationAnglesMetadata(datagroup_id);
			writeIntegrationRadiiMetadata(datagroup_id);
			writeIntegrationSymmetryMetadata(datagroup_id);
		}
		if (cameraLength != null) {
			writeCameraLengthMetadata(datagroup_id);
		}
		if (energy != null) {
			writeEnergyMetadata(datagroup_id);
		}
		if (mask != null) {
			writeMaskMetadata(datagroup_id);
		}
		if (gradient != null && intercept != null) {
			writeQcalibrationMetadata(datagroup_id);
		}
	}

	private void writeBeamCenterMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		int beamcenter_id = NcdNexusUtils.makedata(datagroup_id, "beam centre", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {2}, false, "pixels");
		int filespace_id = H5.H5Dget_space(beamcenter_id);
		int type = H5.H5Dget_type(beamcenter_id);
		int memspace_id = H5.H5Screate_simple(1, new long[] {2}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(beamcenter_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, intSector.getPoint());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(beamcenter_id);
	}
	
	private void writeCameraLengthMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		int cameralength_id = NcdNexusUtils.makegroup(datagroup_id, "camera length", Nexus.DATA);
		int cameralength_data_id = NcdNexusUtils.makedata(cameralength_id, "data", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {1}, false, "mm");
		int cameralength_error_id = NcdNexusUtils.makedata(cameralength_id, "errors", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {1}, false, "mm");
		int filespace_id = H5.H5Dget_space(cameralength_data_id);
		int type = H5.H5Dget_type(cameralength_data_id);
		int memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(cameralength_data_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, new double[] {cameraLength.to(SI.MILLIMETRE).getEstimatedValue()});
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(cameralength_data_id);
		
		filespace_id = H5.H5Dget_space(cameralength_error_id);
		type = H5.H5Dget_type(cameralength_error_id);
		memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(cameralength_error_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, new double[] {cameraLength.to(SI.MILLIMETRE).getAbsoluteError()});
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(cameralength_error_id);
		H5.H5Gclose(cameralength_id);
	}
	
	private void writeEnergyMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		int energy_id = NcdNexusUtils.makedata(datagroup_id, "energy", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {1}, false, "keV");
		int filespace_id = H5.H5Dget_space(energy_id);
		int type = H5.H5Dget_type(energy_id);
		int memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(energy_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, new double[] {energy.doubleValue(SI.KILO(NonSI.ELECTRON_VOLT))});
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(energy_id);
	}
	
	private void writeIntegrationAnglesMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		int angles_id = NcdNexusUtils.makedata(datagroup_id, "integration angles", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {2}, false, "Deg");
		int filespace_id = H5.H5Dget_space(angles_id);
		int type = H5.H5Dget_type(angles_id);
		int memspace_id = H5.H5Screate_simple(1, new long[] {2}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(angles_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, intSector.getAnglesDegrees());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(angles_id);
	}
	
	private void writeIntegrationRadiiMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		int radii_id = NcdNexusUtils.makedata(datagroup_id, "integration radii", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {2}, false, "pixels");
		int filespace_id = H5.H5Dget_space(radii_id);
		int type = H5.H5Dget_type(radii_id);
		int memspace_id = H5.H5Screate_simple(1, new long[] {2}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(radii_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, intSector.getRadii());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(radii_id);
	}
	
	private void writeIntegrationSymmetryMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		int symmetry_type = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		String sym = intSector.getSymmetryText();
		H5.H5Tset_size(symmetry_type, sym.length());
		int symmetry_id = NcdNexusUtils.makedata(datagroup_id, "integration symmetry", symmetry_type, new long[] {1});
		int filespace_id = H5.H5Dget_space(symmetry_id);
		int memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(symmetry_id, symmetry_type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, sym.getBytes());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(symmetry_type);
		H5.H5Dclose(symmetry_id);
	}
	
	private void writeMaskMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long[] maskShape = (long []) ConvertUtils.convert(mask.getShape(), long[].class);
		int mask_id = NcdNexusUtils.makedata(datagroup_id, "mask", HDF5Constants.H5T_NATIVE_INT8, maskShape, false, "pixels");
		int filespace_id = H5.H5Dget_space(mask_id);
		int type = H5.H5Dget_type(mask_id);
		int memspace_id = H5.H5Screate_simple(mask.getRank(), maskShape, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(mask_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, (DatasetUtils.cast(mask, AbstractDataset.INT8)).getBuffer());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(mask_id);
	}
	
	private void writeQcalibrationMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException,	HDF5Exception {

		int qcalibration_id = NcdNexusUtils.makegroup(datagroup_id, "qaxis calibration", Nexus.DATA);

		List<Object[]> data = new ArrayList<Object[]>();
		data.add(new Object[] { "gradient", gradient.getEstimatedValue(), gradient.getUnit() });
		data.add(new Object[] { "gradient_errors", gradient.getAbsoluteError(), gradient.getUnit() });
		data.add(new Object[] { "intercept", intercept.getEstimatedValue(), intercept.getUnit() });
		data.add(new Object[] { "intercept_errors", intercept.getAbsoluteError(), intercept.getUnit() });

		for (Object[] element : data) {
			String name = (String) element[0];
			double[] value = new double[] { (Double) element[1] };
			Unit<?> unit = (Unit<?>) element[2];

			int data_id = NcdNexusUtils.makedata(qcalibration_id, name, HDF5Constants.H5T_NATIVE_DOUBLE,
					new long[] { 1 });
			int filespace_id = H5.H5Dget_space(data_id);
			int type = H5.H5Dget_type(data_id);
			int memspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			H5.H5Sselect_all(filespace_id);

			H5.H5Dwrite(data_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, value);

			// add unit attribute
			{
				UnitFormat unitFormat = UnitFormat.getUCUMInstance();
				String unitString = unitFormat.format(unit);
				int attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
				int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
				H5.H5Tset_size(attrtype_id, unitString.length());

				int attr_id = H5.H5Acreate(data_id, "units", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
						HDF5Constants.H5P_DEFAULT);
				if (attr_id < 0) {
					throw new HDF5Exception("H5 putattr write error: can't create attribute");
				}
				int write_id = H5.H5Awrite(attr_id, attrtype_id, unitString.getBytes());
				if (write_id < 0) {
					throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
				}
				H5.H5Aclose(attr_id);
				H5.H5Sclose(attrspace_id);
				H5.H5Tclose(attrtype_id);
			}

			H5.H5Sclose(filespace_id);
			H5.H5Sclose(memspace_id);
			H5.H5Tclose(type);
			H5.H5Dclose(data_id);
		}
		H5.H5Gclose(qcalibration_id);
	}
}
