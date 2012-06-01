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

import javax.measure.quantity.Length;
import javax.measure.unit.SI;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.core.runtime.jobs.ILock;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5SectorIntegration;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class LazySectorIntegration extends LazyDataReduction {

	private SectorROI intSector;
	private AbstractDataset[] areaData;
	private AbstractDataset mask;
	private Double gradient, intercept;
	Amount<Length> cameraLength;
	
	private boolean calculateRadial = true;
	private boolean calculateAzimuthal = true;
	private boolean fast = true;

	public static String name = "SectorIntegration";

	public void setIntSector(SectorROI intSector) {
		this.intSector = intSector;
	}

	public void setMask(AbstractDataset mask) {
		this.mask = mask;
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
	
	public double[] getCalibrationData() {
		if (gradient != null && intercept != null)
			return new double[] {gradient.doubleValue(), intercept.doubleValue()};
		return null;
	}

	public void setCalibrationData(Double gradient, Double intercept) {
		if (gradient != null)
			this.gradient = new Double(gradient);
		if (intercept != null)
			this.intercept =  new Double(intercept);
	}

	public void setCameraLength(Amount<Length> cameraLength) {
		if (cameraLength != null)
			this.cameraLength = cameraLength;
	}

	public AbstractDataset[] execute(int dim, AbstractDataset data, DataSliceIdentifiers sector_id, DataSliceIdentifiers azimuth_id, ILock lock) {
		HDF5SectorIntegration reductionStep = new HDF5SectorIntegration("sector", "data");
		reductionStep.parentdata = data;
		reductionStep.setROI(intSector);
		if (mask != null) 
			reductionStep.setMask(mask);
		reductionStep.setIDs(sector_id);
		reductionStep.setAzimuthalIDs(azimuth_id);
		reductionStep.setAreaData(areaData);
		reductionStep.setCalculateRadial(calculateRadial);
		reductionStep.setCalculateAzimuthal(calculateAzimuthal);
		reductionStep.setFast(fast);
		
		return reductionStep.writeout(dim, lock);
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
		if (cameraLength != null)
			writeCameraLengthMetadata(datagroup_id);
		if (mask != null)
			writeMaskMetadata(datagroup_id);
		if (gradient != null && intercept != null)
			writeQcalibrationMetadata(datagroup_id);
	}

	private void writeBeamCenterMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		int beamcenter_id = NcdNexusUtils.makedata(datagroup_id, "beam center", HDF5Constants.H5T_NATIVE_DOUBLE, 1, new long[] {2}, false, "pixels");
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
		int cameralength_id = NcdNexusUtils.makedata(datagroup_id, "camera length", HDF5Constants.H5T_NATIVE_DOUBLE, 1, new long[] {1}, false, "mm");
		int filespace_id = H5.H5Dget_space(cameralength_id);
		int type = H5.H5Dget_type(cameralength_id);
		int memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(cameralength_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, new double[] {cameraLength.doubleValue(SI.MILLIMETER)});
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(cameralength_id);
	}
	
	private void writeIntegrationAnglesMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		int angles_id = NcdNexusUtils.makedata(datagroup_id, "integration angles", HDF5Constants.H5T_NATIVE_DOUBLE, 1, new long[] {2}, false, "Deg");
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
		int radii_id = NcdNexusUtils.makedata(datagroup_id, "integration radii", HDF5Constants.H5T_NATIVE_DOUBLE, 1, new long[] {2}, false, "pixels");
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
		int symmetry_id = NcdNexusUtils.makedata(datagroup_id, "integration symmetry", symmetry_type, 1, new long[] {1});
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
		int mask_id = NcdNexusUtils.makedata(datagroup_id, "mask", HDF5Constants.H5T_NATIVE_INT8, mask.getRank(), maskShape, false, "pixels");
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
	
	private void writeQcalibrationMetadata(int datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		int qcalibration_id = NcdNexusUtils.makedata(datagroup_id, "qaxis calibration", HDF5Constants.H5T_NATIVE_DOUBLE, 1, new long[] {2});
		int filespace_id = H5.H5Dget_space(qcalibration_id);
		int type = H5.H5Dget_type(qcalibration_id);
		int memspace_id = H5.H5Screate_simple(1, new long[] {2}, null);
		H5.H5Sselect_all(filespace_id);
		double[] qCalibration = new double[] {gradient.doubleValue(), intercept.doubleValue()};
		H5.H5Dwrite(qcalibration_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, qCalibration);
		
		// add unit attribute
		{
			String unitString = qaxisUnit.toString();
			int attrspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
			int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attrtype_id, unitString.length());
			
			int attr_id = H5.H5Acreate(qcalibration_id, "unit", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0)
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			
			int write_id = H5.H5Awrite(attr_id, attrtype_id, unitString .getBytes());
			if (write_id < 0)
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			
			H5.H5Aclose(attr_id);
			H5.H5Sclose(attrspace_id);
			H5.H5Tclose(attrtype_id);
		}
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(qcalibration_id);
	}
}
