/*-
 * Copyright © 2011 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd.reduction.service;

import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.data.SliceInput;
import uk.ac.diamond.scisoft.ncd.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.reduction.LazyNcdProcessing;

/**
 * Contains information to run data reduction algorithm from anywhere.
 * 
 * Interface used to hide any details of implementation from outside world. 
 * Currently there are none but since eclipse tools give extaction of interface
 * for free, we only expose an interface to the world in the service.
 */
public interface IDataReductionContext {
	
	public boolean isEnableNormalisation();

	public void setEnableNormalisation(boolean enableNormalisation);

	public boolean isEnableBackground();

	public void setEnableBackground(boolean enableBackground);

	public boolean isEnableDetectorResponse();

	public void setEnableDetectorResponse(boolean enableDetectorResponse);

	public boolean isEnableSector();

	public void setEnableSector(boolean enableSector);

	public boolean isEnableInvariant();

	public void setEnableInvariant(boolean enableInvariant);

	public boolean isEnableAverage();

	public void setEnableAverage(boolean enableAverage);

	public boolean isEnableWaxs();

	public void setEnableWaxs(boolean enableWax);

	public boolean isEnableSaxs();

	public void setEnableSaxs(boolean enableSaxs);

	public boolean isEnableRadial();

	public void setEnableRadial(boolean enableRadial);

	public boolean isEnableAzimuthal();

	public void setEnableAzimuthal(boolean enableAzimuthal);

	public boolean isEnableFastIntegration();

	public void setEnableFastIntegration(boolean enableFastIntegration);

	public boolean isEnableMask();

	public void setEnableMask(boolean enableMask);

	public NcdDetectorSettings getDetWaxsInfo();

	public void setDetWaxsInfo(NcdDetectorSettings detWaxsInfo);

	public NcdDetectorSettings getDetSaxsInfo();

	public void setDetSaxsInfo(NcdDetectorSettings detSaxsInfo);

	public NcdDetectorSettings getScalerData();

	public void setScalerData(NcdDetectorSettings scalerData);

	public CalibrationResultsBean getCalibrationResults();

	public void setCalibrationResults(CalibrationResultsBean calibrationResults);

	public SliceInput getDataSliceInput();

	public void setDataSliceInput(SliceInput dataSliceInput);

	public SliceInput getBgSliceInput();

	public void setBgSliceInput(SliceInput bgSliceInput);

	public SliceInput getGridAverageSlice();

	public void setGridAverageSlice(SliceInput gridAverageSlice);

	public String getBgPath();

	public void setBgPath(String bgPath);

	public String getDrFile();

	public void setDrFile(String drFile);

	public String getWorkingDir();

	public void setWorkingDir(String workingDir);

	public Double getAbsScaling();

	public void setAbsScaling(Double absScaling);

	public Double getSampleThickness();

	public void setSampleThickness(Double thickness);

	public Double getBgScaling();

	public void setBgScaling(Double bgScaling);
	
	public BooleanDataset getMask();

	public void setMask(BooleanDataset mask);

	public SectorROI getSector();

	public void setSector(SectorROI sector);

	/**
	 * The energy in keV
	 * @return energy
	 */
	Double getEnergy();

	/**
	 * The energy in keV
	 */
	void setEnergy(Double energy);

	String getCalibrationName();

	void setCalibrationName(String calibrationName);

	String getWaxsDetectorName();

	void setWaxsDetectorName(String waxsDetectorName);

	String getSaxsDetectorName();

	void setSaxsDetectorName(String saxsDetectorName);

	public LazyNcdProcessing getProcessing();

	public void setProcessing(LazyNcdProcessing processing);

	public LazyNcdProcessing getBgProcessing();

	public void setBgProcessing(LazyNcdProcessing bgProcessing);

	public NcdReductionFlags getFlags();

	public void setFlags(NcdReductionFlags flags);

	public NcdDetectors getNcdDetectors();

	public void setNcdDetectors(NcdDetectors ncdDetectors);

	/**
	 * The amount of increments per file processed. So the total time is
	 * the number of files multiplied by the work amount.
	 * @return work amount
	 */
	int getWorkAmount();

	/**
	 * 
	 * @param workAmount
	 */
	void setWorkAmount(int workAmount);

	/**
	 * This is the actual name generated with the background is written to.
	 */
	String getBgName();

	/**
	 * This is the actual name generated with the background is written to.
	 */
	void setBgName(String bgName);

}
