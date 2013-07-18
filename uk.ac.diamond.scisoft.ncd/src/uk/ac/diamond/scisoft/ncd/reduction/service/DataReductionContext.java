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
 * Class holds data (from xml file or source providers) which is
 * required to run the data reduction algorithm. 
 * 
 * This data happens to be quite a lot.
 */
class DataReductionContext implements IDataReductionContext {

	private int workAmount;
	
	private BooleanDataset    mask;
	private SectorROI         sector;
	private LazyNcdProcessing processing;
	private LazyNcdProcessing bgProcessing;
	private NcdReductionFlags flags;
	private NcdDetectors      ncdDetectors;
	
	private boolean enableMask;
	
	private NcdDetectorSettings    detWaxsInfo, detSaxsInfo, scalerData;
	private CalibrationResultsBean calibrationResults;
	private SliceInput             dataSliceInput, bgSliceInput, gridAverageSlice;
	
	private String bgPath, drFile, workingDir, calibrationName, waxsDetectorName, saxsDetectorName;
	
	private Double absScaling, sampleThickness, bgScaling, energy;
	
	/**
	 * This is the actual name generated with the background is written to.
	 */
	private String bgName;
	
	@Override
	public boolean isEnableNormalisation() {
		return flags.isEnableNormalisation();
	}

	@Override
	public void setEnableNormalisation(boolean enableNormalisation) {
		flags.setEnableNormalisation(enableNormalisation);
	}

	@Override
	public boolean isEnableBackground() {
		return flags.isEnableBackground();
	}

	@Override
	public void setEnableBackground(boolean enableBackground) {
		flags.setEnableBackground(enableBackground);
	}

	@Override
	public boolean isEnableDetectorResponse() {
		return flags.isEnableDetectorResponse();
	}

	@Override
	public void setEnableDetectorResponse(boolean enableDetectorResponse) {
		flags.setEnableDetectorResponse(enableDetectorResponse);
	}

	@Override
	public boolean isEnableSector() {
		return flags.isEnableSector();
	}

	@Override
	public void setEnableSector(boolean enableSector) {
		flags.setEnableSector(enableSector);
	}

	@Override
	public boolean isEnableInvariant() {
		return flags.isEnableInvariant();
	}

	@Override
	public void setEnableInvariant(boolean enableInvariant) {
		flags.setEnableInvariant(enableInvariant);
	}

	@Override
	public boolean isEnableAverage() {
		return flags.isEnableAverage();
	}

	@Override
	public void setEnableAverage(boolean enableAverage) {
		flags.setEnableAverage(enableAverage);
	}

	@Override
	public boolean isEnableWaxs() {
		return flags.isEnableWaxs();
	}

	@Override
	public void setEnableWaxs(boolean enableWaxs) {
		flags.setEnableWaxs(enableWaxs);
	}

	@Override
	public boolean isEnableSaxs() {
		return flags.isEnableWaxs();
	}

	@Override
	public void setEnableSaxs(boolean enableSaxs) {
		flags.setEnableSaxs(enableSaxs);
	}

	@Override
	public boolean isEnableRadial() {
		return flags.isEnableRadial();
	}

	@Override
	public void setEnableRadial(boolean enableRadial) {
		flags.setEnableRadial(enableRadial);
	}

	@Override
	public boolean isEnableAzimuthal() {
		return flags.isEnableAzimuthal();
	}

	@Override
	public void setEnableAzimuthal(boolean enableAzimuthal) {
		flags.setEnableAzimuthal(enableAzimuthal);
	}

	@Override
	public boolean isEnableFastIntegration() {
		return flags.isEnableFastintegration();
	}

	@Override
	public void setEnableFastIntegration(boolean enableFastIntegration) {
		flags.setEnableFastintegration(enableFastIntegration);
	}

	@Override
	public boolean isEnableMask() {
		return enableMask;
	}

	@Override
	public void setEnableMask(boolean enableMask) {
		this.enableMask = enableMask;
	}

	@Override
	public NcdDetectorSettings getDetWaxsInfo() {
		return detWaxsInfo;
	}

	@Override
	public void setDetWaxsInfo(NcdDetectorSettings detWaxsInfo) {
		this.detWaxsInfo = detWaxsInfo;
	}

	@Override
	public NcdDetectorSettings getDetSaxsInfo() {
		return detSaxsInfo;
	}

	@Override
	public void setDetSaxsInfo(NcdDetectorSettings detSaxsInfo) {
		this.detSaxsInfo = detSaxsInfo;
	}

	@Override
	public NcdDetectorSettings getScalerData() {
		return scalerData;
	}

	@Override
	public void setScalerData(NcdDetectorSettings scalerData) {
		this.scalerData = scalerData;
	}

	@Override
	public CalibrationResultsBean getCalibrationResults() {
		return calibrationResults;
	}

	@Override
	public void setCalibrationResults(CalibrationResultsBean calibrationResults) {
		this.calibrationResults = calibrationResults;
	}

	@Override
	public SliceInput getDataSliceInput() {
		return dataSliceInput;
	}

	@Override
	public void setDataSliceInput(SliceInput dataSliceInput) {
		this.dataSliceInput = dataSliceInput;
	}

	@Override
	public SliceInput getBgSliceInput() {
		return bgSliceInput;
	}

	@Override
	public void setBgSliceInput(SliceInput bgSliceInput) {
		this.bgSliceInput = bgSliceInput;
	}

	@Override
	public SliceInput getGridAverageSlice() {
		return gridAverageSlice;
	}

	@Override
	public void setGridAverageSlice(SliceInput gridAverageSlice) {
		this.gridAverageSlice = gridAverageSlice;
	}

	@Override
	public String getBgPath() {
		return bgPath;
	}

	@Override
	public void setBgPath(String bgPath) {
		this.bgPath = bgPath;
	}

	@Override
	public String getDrFile() {
		return drFile;
	}

	@Override
	public void setDrFile(String drFile) {
		this.drFile = drFile;
	}

	@Override
	public String getWorkingDir() {
		return workingDir;
	}

	@Override
	public void setWorkingDir(String workingDir) {
		this.workingDir = workingDir;
	}

	@Override
	public Double getAbsScaling() {
		return absScaling;
	}

	@Override
	public void setAbsScaling(Double absScaling) {
		this.absScaling = absScaling;
	}

	@Override
	public Double getSampleThickness() {
		return sampleThickness;
	}

	@Override
	public void setSampleThickness(Double thickness) {
		this.sampleThickness = thickness;
	}

	@Override
	public Double getBgScaling() {
		return bgScaling;
	}

	@Override
	public void setBgScaling(Double bgScaling) {
		this.bgScaling = bgScaling;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((absScaling == null) ? 0 : absScaling.hashCode());
		result = prime * result + ((bgName == null) ? 0 : bgName.hashCode());
		result = prime * result + ((bgPath == null) ? 0 : bgPath.hashCode());
		result = prime * result + ((bgProcessing == null) ? 0 : bgProcessing.hashCode());
		result = prime * result + ((bgScaling == null) ? 0 : bgScaling.hashCode());
		result = prime * result + ((bgSliceInput == null) ? 0 : bgSliceInput.hashCode());
		result = prime * result + ((calibrationName == null) ? 0 : calibrationName.hashCode());
		result = prime * result + ((calibrationResults == null) ? 0 : calibrationResults.hashCode());
		result = prime * result + ((dataSliceInput == null) ? 0 : dataSliceInput.hashCode());
		result = prime * result + ((detSaxsInfo == null) ? 0 : detSaxsInfo.hashCode());
		result = prime * result + ((detWaxsInfo == null) ? 0 : detWaxsInfo.hashCode());
		result = prime * result + ((drFile == null) ? 0 : drFile.hashCode());
		result = prime * result + (enableMask ? 1231 : 1237);
		result = prime * result + ((energy == null) ? 0 : energy.hashCode());
		result = prime * result + ((flags == null) ? 0 : flags.hashCode());
		result = prime * result + ((gridAverageSlice == null) ? 0 : gridAverageSlice.hashCode());
		result = prime * result + ((mask == null) ? 0 : mask.hashCode());
		result = prime * result + ((ncdDetectors == null) ? 0 : ncdDetectors.hashCode());
		result = prime * result + ((processing == null) ? 0 : processing.hashCode());
		result = prime * result + ((sampleThickness == null) ? 0 : sampleThickness.hashCode());
		result = prime * result + ((saxsDetectorName == null) ? 0 : saxsDetectorName.hashCode());
		result = prime * result + ((scalerData == null) ? 0 : scalerData.hashCode());
		result = prime * result + ((sector == null) ? 0 : sector.hashCode());
		result = prime * result + ((waxsDetectorName == null) ? 0 : waxsDetectorName.hashCode());
		result = prime * result + workAmount;
		result = prime * result + ((workingDir == null) ? 0 : workingDir.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DataReductionContext other = (DataReductionContext) obj;
		if (absScaling == null) {
			if (other.absScaling != null)
				return false;
		} else if (!absScaling.equals(other.absScaling))
			return false;
		if (bgName == null) {
			if (other.bgName != null)
				return false;
		} else if (!bgName.equals(other.bgName))
			return false;
		if (bgPath == null) {
			if (other.bgPath != null)
				return false;
		} else if (!bgPath.equals(other.bgPath))
			return false;
		if (bgProcessing == null) {
			if (other.bgProcessing != null)
				return false;
		} else if (!bgProcessing.equals(other.bgProcessing))
			return false;
		if (bgScaling == null) {
			if (other.bgScaling != null)
				return false;
		} else if (!bgScaling.equals(other.bgScaling))
			return false;
		if (bgSliceInput == null) {
			if (other.bgSliceInput != null)
				return false;
		} else if (!bgSliceInput.equals(other.bgSliceInput))
			return false;
		if (calibrationName == null) {
			if (other.calibrationName != null)
				return false;
		} else if (!calibrationName.equals(other.calibrationName))
			return false;
		if (calibrationResults == null) {
			if (other.calibrationResults != null)
				return false;
		} else if (!calibrationResults.equals(other.calibrationResults))
			return false;
		if (dataSliceInput == null) {
			if (other.dataSliceInput != null)
				return false;
		} else if (!dataSliceInput.equals(other.dataSliceInput))
			return false;
		if (detSaxsInfo == null) {
			if (other.detSaxsInfo != null)
				return false;
		} else if (!detSaxsInfo.equals(other.detSaxsInfo))
			return false;
		if (detWaxsInfo == null) {
			if (other.detWaxsInfo != null)
				return false;
		} else if (!detWaxsInfo.equals(other.detWaxsInfo))
			return false;
		if (drFile == null) {
			if (other.drFile != null)
				return false;
		} else if (!drFile.equals(other.drFile))
			return false;
		if (enableMask != other.enableMask)
			return false;
		if (energy == null) {
			if (other.energy != null)
				return false;
		} else if (!energy.equals(other.energy))
			return false;
		if (flags == null) {
			if (other.flags != null)
				return false;
		} else if (!flags.equals(other.flags))
			return false;
		if (gridAverageSlice == null) {
			if (other.gridAverageSlice != null)
				return false;
		} else if (!gridAverageSlice.equals(other.gridAverageSlice))
			return false;
		if (mask == null) {
			if (other.mask != null)
				return false;
		} else if (!mask.equals(other.mask))
			return false;
		if (ncdDetectors == null) {
			if (other.ncdDetectors != null)
				return false;
		} else if (!ncdDetectors.equals(other.ncdDetectors))
			return false;
		if (processing == null) {
			if (other.processing != null)
				return false;
		} else if (!processing.equals(other.processing))
			return false;
		if (sampleThickness == null) {
			if (other.sampleThickness != null)
				return false;
		} else if (!sampleThickness.equals(other.sampleThickness))
			return false;
		if (saxsDetectorName == null) {
			if (other.saxsDetectorName != null)
				return false;
		} else if (!saxsDetectorName.equals(other.saxsDetectorName))
			return false;
		if (scalerData == null) {
			if (other.scalerData != null)
				return false;
		} else if (!scalerData.equals(other.scalerData))
			return false;
		if (sector == null) {
			if (other.sector != null)
				return false;
		} else if (!sector.equals(other.sector))
			return false;
		if (waxsDetectorName == null) {
			if (other.waxsDetectorName != null)
				return false;
		} else if (!waxsDetectorName.equals(other.waxsDetectorName))
			return false;
		if (workAmount != other.workAmount)
			return false;
		if (workingDir == null) {
			if (other.workingDir != null)
				return false;
		} else if (!workingDir.equals(other.workingDir))
			return false;
		return true;
	}

	@Override
	public BooleanDataset getMask() {
		return mask;
	}

	@Override
	public void setMask(BooleanDataset mask) {
		this.mask = mask;
	}

	@Override
	public SectorROI getSector() {
		return sector;
	}

	@Override
	public void setSector(SectorROI sector) {
		this.sector = sector;
	}

	@Override
	public Double getEnergy() {
		return energy;
	}

	@Override
	public void setEnergy(Double energy) {
		this.energy = energy;
	}

	@Override
	public String getCalibrationName() {
		return calibrationName;
	}

	@Override
	public void setCalibrationName(String calibrationName) {
		this.calibrationName = calibrationName;
	}

	@Override
	public String getWaxsDetectorName() {
		return waxsDetectorName;
	}

	@Override
	public void setWaxsDetectorName(String waxsDetectorName) {
		this.waxsDetectorName = waxsDetectorName;
	}

	@Override
	public String getSaxsDetectorName() {
		return saxsDetectorName;
	}

	@Override
	public void setSaxsDetectorName(String saxsDetectorName) {
		this.saxsDetectorName = saxsDetectorName;
	}

	@Override
	public LazyNcdProcessing getProcessing() {
		return processing;
	}

	@Override
	public void setProcessing(LazyNcdProcessing processing) {
		this.processing = processing;
	}

	@Override
	public LazyNcdProcessing getBgProcessing() {
		return bgProcessing;
	}

	@Override
	public void setBgProcessing(LazyNcdProcessing bgProcessing) {
		this.bgProcessing = bgProcessing;
	}

	@Override
	public NcdReductionFlags getFlags() {
		return flags;
	}

	@Override
	public void setFlags(NcdReductionFlags flags) {
		this.flags = flags;
	}

	@Override
	public NcdDetectors getNcdDetectors() {
		return ncdDetectors;
	}

	@Override
	public void setNcdDetectors(NcdDetectors ncdDetectors) {
		this.ncdDetectors = ncdDetectors;
	}

	@Override
	public int getWorkAmount() {
		return workAmount;
	}

	@Override
	public void setWorkAmount(int workAmount) {
		this.workAmount = workAmount;
	}

	@Override
	public String getBgName() {
		return bgName;
	}

	@Override
	public void setBgName(String bgName) {
		this.bgName = bgName;
	}

}
