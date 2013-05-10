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

package uk.ac.diamond.scisoft.ncd.rcp;

import java.util.HashMap;

import javax.measure.quantity.Energy;

import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISources;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.data.SliceInput;

public class NcdProcessingSourceProvider extends AbstractSourceProvider {
	
	public final static String AVERAGE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableAverage";
	public final static String BACKGROUD_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableBackgroundSubtraction";
	public final static String RESPONSE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableDetectorResponse";
	public final static String INVARIANT_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableInvariant";
	public final static String NORMALISATION_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableNormalisation";
	public final static String SECTOR_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableSectorIntegration";
	public final static String RADIAL_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableRadialIntegration";
	public final static String AZIMUTH_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableAzimuthalIntegration";
	public final static String FASTINT_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableFastIntegration";
	public final static String WAXS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableWaxsDataReduction";
	public final static String SAXS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableSaxsDataReduction";
	public final static String MASK_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableMask";
	
	public final static String SCALER_STATE = "uk.ac.diamond.scisoft.ncd.rcp.scalerDetector";
	public final static String SAXSDETECTOR_STATE = "uk.ac.diamond.scisoft.ncd.rcp.saxsDetector";
	public final static String WAXSDETECTOR_STATE = "uk.ac.diamond.scisoft.ncd.rcp.waxsDetector";
	
	public final static String ENERGY_STATE = "uk.ac.diamond.scisoft.ncd.rcp.energy";
	public final static String NORMCHANNEL_STATE = "uk.ac.diamond.scisoft.ncd.rcp.normChannel";
	
	public final static String DATASLICE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.dataSlice";
	public final static String BKGSLICE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.bkgSlice";
	public final static String GRIDAVERAGE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.gridAverage";
	
	public final static String BKGSCALING_STATE = "uk.ac.diamond.scisoft.ncd.rcp.bgScale";
	public final static String ABSSCALING_STATE = "uk.ac.diamond.scisoft.ncd.rcp.absScale";
	public final static String ABSOFFSET_STATE = "uk.ac.diamond.scisoft.ncd.rcp.absOffset";
	public static final String SAMPLETHICKNESS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.sampleThickness";
	
	public final static String BKGFILE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.bkgFile";
	public final static String DRFILE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.drFile";
	public final static String WORKINGDIR_STATE = "uk.ac.diamond.scisoft.ncd.rcp.workingDir";

	public final static String OPEN_NCD_WIZARD = "uk.ac.diamond.scisoft.ncd.rcp.openNcdDataReductionWizard";
	
	private boolean enableAverage = false;
	private boolean enableBackground = false;
	private boolean enableDetectorResponse = false;
	private boolean enableInvariant = false;
	private boolean enableNormalisation = false;
	private boolean enableSector = false;
	private boolean enableRadial = false;
	private boolean enableAzimuthal = false;
	private boolean enableFastIntegration = false;
	private boolean enableWaxs = false;
	private boolean enableSaxs = false;
	private boolean enableMask = false;
	
	private String scaler, saxsDetector, waxsDetector;
	private String bgFile, drFile, workingDir;
	private SliceInput dataSlice, bkgSlice, gridAverage;
	
	private Integer normChannel;
	private Double bgScaling, absScaling, absOffset, sampleThickness;
	private Amount<Energy> energy;

	public NcdProcessingSourceProvider() {
	}

	@Override
	public void dispose() {
	}

	@Override
	public HashMap<String, Object> getCurrentState() {
		HashMap<String, Object> currentState = new HashMap<String, Object>();
		currentState.put(AVERAGE_STATE, enableAverage);
		currentState.put(BACKGROUD_STATE, enableBackground);
		currentState.put(RESPONSE_STATE, enableDetectorResponse);
		currentState.put(INVARIANT_STATE, enableInvariant);
		currentState.put(NORMALISATION_STATE, enableNormalisation);
		currentState.put(SECTOR_STATE, enableSector);
		currentState.put(RADIAL_STATE, enableRadial);
		currentState.put(AZIMUTH_STATE, enableAzimuthal);
		currentState.put(FASTINT_STATE, enableFastIntegration);
		currentState.put(WAXS_STATE, enableWaxs);
		currentState.put(SAXS_STATE, enableSaxs);
		currentState.put(MASK_STATE, enableMask);
		currentState.put(SCALER_STATE, scaler);
		currentState.put(SAXSDETECTOR_STATE, saxsDetector);
		currentState.put(WAXSDETECTOR_STATE, waxsDetector);
		currentState.put(ENERGY_STATE, energy);
		currentState.put(NORMCHANNEL_STATE, normChannel);
		currentState.put(DATASLICE_STATE, dataSlice);
		currentState.put(BKGSLICE_STATE, bkgSlice);
		currentState.put(BKGFILE_STATE, bgFile);
		currentState.put(DRFILE_STATE, drFile);
		currentState.put(GRIDAVERAGE_STATE, gridAverage);
		currentState.put(BKGSCALING_STATE, bgScaling);
		currentState.put(ABSSCALING_STATE, absScaling);
		currentState.put(ABSOFFSET_STATE, absOffset);
		currentState.put(SAMPLETHICKNESS_STATE, sampleThickness);
		currentState.put(WORKINGDIR_STATE, workingDir);
		
		return currentState;
	}

	@Override
	public String[] getProvidedSourceNames() {
		
		return new String[] {AVERAGE_STATE,
		                     BACKGROUD_STATE,
		                     RESPONSE_STATE,
		                     INVARIANT_STATE,
		                     NORMALISATION_STATE,
		                     SECTOR_STATE,
		                     RADIAL_STATE,
		                     AZIMUTH_STATE,
		                     FASTINT_STATE,
		                     WAXS_STATE,
		                     SAXS_STATE,
		                     MASK_STATE,
		                     SCALER_STATE,
		                     SAXSDETECTOR_STATE,
		                     WAXSDETECTOR_STATE,
		                     ENERGY_STATE,
		                     NORMCHANNEL_STATE,
		                     DATASLICE_STATE,
		                     BKGSLICE_STATE,
		                     BKGFILE_STATE,
		                     DRFILE_STATE,
		                     GRIDAVERAGE_STATE,
		                     BKGSCALING_STATE,
		                     ABSSCALING_STATE,
		                     ABSOFFSET_STATE,
		                     SAMPLETHICKNESS_STATE,
		                     WORKINGDIR_STATE};
	}

	public void setEnableAverage(boolean enableAverage) {
		this.enableAverage = enableAverage;
		fireSourceChanged(ISources.WORKBENCH, AVERAGE_STATE, enableAverage);
	}

	public void setEnableBackground(boolean enableBackground) {
		this.enableBackground = enableBackground;
		fireSourceChanged(ISources.WORKBENCH, BACKGROUD_STATE, enableBackground);
	}

	public void setEnableDetectorResponse(boolean enableDetectorResponse) {
		this.enableDetectorResponse = enableDetectorResponse;
		fireSourceChanged(ISources.WORKBENCH, RESPONSE_STATE, enableDetectorResponse);
	}

	public void setEnableInvariant(boolean enableInvariant) {
		this.enableInvariant = enableInvariant;
		fireSourceChanged(ISources.WORKBENCH, INVARIANT_STATE, enableInvariant);
	}

	public void setEnableNormalisation(boolean enableNormalisation) {
		this.enableNormalisation = enableNormalisation;
		fireSourceChanged(ISources.WORKBENCH, NORMALISATION_STATE, enableNormalisation);
	}

	public void setEnableSector(boolean enableSector) {
		this.enableSector = enableSector;
		fireSourceChanged(ISources.WORKBENCH, SECTOR_STATE, enableSector);
	}

	public void setEnableRadial(boolean enableRadial) {
		this.enableRadial = enableRadial;
		fireSourceChanged(ISources.WORKBENCH, RADIAL_STATE, enableRadial);
	}

	public void setEnableAzimuthal(boolean enableAzimuthal) {
		this.enableAzimuthal = enableAzimuthal;
		fireSourceChanged(ISources.WORKBENCH, AZIMUTH_STATE, enableAzimuthal);
	}

	public void setEnableFastIntegration(boolean enableFastIntegration) {
		this.enableFastIntegration = enableFastIntegration;
		fireSourceChanged(ISources.WORKBENCH, FASTINT_STATE, enableFastIntegration);
	}

	public void setEnableWaxs(boolean enableWaxs) {
		this.enableWaxs = enableWaxs;
		fireSourceChanged(ISources.WORKBENCH, WAXS_STATE, enableWaxs);
	}

	public void setEnableSaxs(boolean enableSaxs) {
		this.enableSaxs = enableSaxs;
		fireSourceChanged(ISources.WORKBENCH, SAXS_STATE, enableSaxs);
	}

	public void setEnableMask(boolean enableMask) {
		this.enableMask = enableMask;
		fireSourceChanged(ISources.WORKBENCH, MASK_STATE, enableMask);
	}

	public void setScaler(String scaler) {
		this.scaler = (scaler != null) ? new String(scaler) : null;
		fireSourceChanged(ISources.WORKBENCH, SCALER_STATE, this.scaler);
	}
	
	public void setSaxsDetector(String saxsDetector) {
		this.saxsDetector = (saxsDetector != null) ? new String(saxsDetector) : null;
		fireSourceChanged(ISources.WORKBENCH, SAXSDETECTOR_STATE, this.saxsDetector);
	}
	
	public void setWaxsDetector(String waxsDetector) {
		this.waxsDetector = (waxsDetector != null) ? new String(waxsDetector) : null;
		fireSourceChanged(ISources.WORKBENCH, WAXSDETECTOR_STATE, this.waxsDetector);
	}
	
	public void setEnergy(Amount<Energy> energy) {
		this.energy = (energy != null) ? energy.copy() : null;
		fireSourceChanged(ISources.WORKBENCH, ENERGY_STATE, this.energy);
	}

	public void setNormChannel(Integer normChannel) {
		this.normChannel = (normChannel != null) ? new Integer(normChannel) : null;
		fireSourceChanged(ISources.WORKBENCH, NORMCHANNEL_STATE, this.normChannel);
	}

	public void setDataSlice(SliceInput dataSlice) {
		this.dataSlice = (dataSlice != null) ? new SliceInput(dataSlice) : null;
		fireSourceChanged(ISources.WORKBENCH, DATASLICE_STATE, this.dataSlice);
	}

	public void setBkgSlice(SliceInput bkgSlice) {
		this.bkgSlice = (bkgSlice != null) ? new SliceInput(bkgSlice) : null;
		fireSourceChanged(ISources.WORKBENCH, BKGSLICE_STATE, this.bkgSlice);
	}

	public void setBgFile(String bgFile) {
		this.bgFile = (bgFile != null) ? new String(bgFile) : null;
		fireSourceChanged(ISources.WORKBENCH, BKGFILE_STATE, this.bgFile);
	}

	public void setDrFile(String drFile) {
		this.drFile = (drFile != null) ? new String(drFile) : null;
		fireSourceChanged(ISources.WORKBENCH, DRFILE_STATE, this.drFile);
	}

	public void setGrigAverage(SliceInput gridAverage) {
		this.gridAverage = (gridAverage != null) ? new SliceInput(gridAverage.getAdvancedSlice()) : null;
		fireSourceChanged(ISources.WORKBENCH, GRIDAVERAGE_STATE, this.gridAverage);
	}

	public void setBgScaling(Double bgScaling) {
		this.bgScaling = (bgScaling != null) ? new Double(bgScaling) : null;
		fireSourceChanged(ISources.WORKBENCH, BKGSCALING_STATE, this.bgScaling);
	}

	public void setSampleThickness(Double sampleThickness) {
		this.sampleThickness = (sampleThickness != null) ? new Double(sampleThickness) : null;
		fireSourceChanged(ISources.WORKBENCH, SAMPLETHICKNESS_STATE, this.sampleThickness);
	}

	public void setAbsScaling(Double absScaling, boolean notify) {
		this.absScaling = (absScaling != null) ? new Double(absScaling) : null;
		if (notify) {
			fireSourceChanged(ISources.WORKBENCH, ABSSCALING_STATE, this.absScaling);
		}
	}

	public void setAbsOffset(Double absOffset) {
		this.absOffset = (absOffset != null) ? new Double(absOffset) : null;
		fireSourceChanged(ISources.WORKBENCH, ABSOFFSET_STATE, this.absOffset);
	}

	public void setWorkingDir(String workingDir) {
		this.workingDir = (workingDir != null) ? new String(workingDir): null;
		fireSourceChanged(ISources.WORKBENCH, WORKINGDIR_STATE, this.workingDir);
	}

	public boolean isEnableAverage() {
		return enableAverage;
	}

	public boolean isEnableBackground() {
		return enableBackground;
	}

	public boolean isEnableDetectorResponse() {
		return enableDetectorResponse;
	}

	public boolean isEnableInvariant() {
		return enableInvariant;
	}

	public boolean isEnableNormalisation() {
		return enableNormalisation;
	}

	public boolean isEnableSector() {
		return enableSector;
	}

	public boolean isEnableRadial() {
		return enableRadial;
	}

	public boolean isEnableAzimuthal() {
		return enableAzimuthal;
	}

	public boolean isEnableFastIntegration() {
		return enableFastIntegration;
	}

	public boolean isEnableWaxs() {
		return enableWaxs;
	}

	public boolean isEnableSaxs() {
		return enableSaxs;
	}

	public boolean isEnableMask() {
		return enableMask;
	}
	
	public String getScaler() {
		return scaler;
	}
	
	public String getSaxsDetector() {
		return saxsDetector;
	}
	
	public String getWaxsDetector() {
		return waxsDetector;
	}

	public SliceInput getDataSlice() {
		return dataSlice;
	}

	public SliceInput getBkgSlice() {
		return bkgSlice;
	}

	public String getBgFile() {
		return bgFile;
	}

	public String getDrFile() {
		return drFile;
	}

	public String getWorkingDir() {
		return workingDir;
	}

	public SliceInput getGridAverage() {
		return gridAverage;
	}

	public Integer getNormChannel() {
		return normChannel;
	}

	public Double getBgScaling() {
		return bgScaling;
	}

	public Double getAbsScaling() {
		return absScaling;
	}

	public Double getAbsOffset() {
		return absOffset;
	}

	public Double getSampleThickness() {
		return sampleThickness;
	}

	public Amount<Energy> getEnergy() {
		return energy;
	}
}
