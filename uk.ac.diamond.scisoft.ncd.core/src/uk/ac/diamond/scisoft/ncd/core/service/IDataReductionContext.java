/*
 * Copyright 2013 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.core.service;

import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.core.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.core.data.SliceInput;

/**
 * Contains information to run data reduction algorithm from anywhere.
 * 
 * Interface used to hide any details of implementation from outside world. 
 * Currently there are none but since eclipse tools give extaction of interface
 * for free, we only expose an interface to the world in the service.
 */
public interface IDataReductionContext {
	
	public String getResultsFile();
	
	public void setResultsFile(String path);
	
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

	public boolean isEnableLogLogPlot();
	
	public void setEnableLogLogPlot(boolean enableLogLogPlot);

	public boolean isEnableGuinierPlot();

	public void setEnableGuinierPlot(boolean enableGuinierPlot);

	public boolean isEnablePorodPlot();

	public void setEnablePorodPlot(boolean enablePorodPlot);

	public boolean isEnableKratkyPlot();

	public void setEnableKratkyPlot(boolean enableKratkyPlot);

	public boolean isEnableZimmPlot();

	public void setEnableZimmPlot(boolean enableZimmPlot);

	public boolean isEnableDebyeBuechePlot();

	public void setEnableDebyeBuechePlot(boolean enableDebyeBuechePlot);
	
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

	public IDataReductionProcess getProcessing();

	public void setProcessing(IDataReductionProcess processing);

	public IDataReductionProcess getBgProcessing();

	public void setBgProcessing(IDataReductionProcess bgProcessing);

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
