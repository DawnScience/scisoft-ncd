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

package uk.ac.diamond.scisoft.ncd.core.rcp;

import java.util.HashMap;
import java.util.Map;

import javax.measure.quantity.Energy;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISources;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.core.data.SliceInput;
import uk.ac.diamond.scisoft.ncd.data.xml.EnergyXmlAdapter;

@XmlAccessorType(XmlAccessType.FIELD)

public class NcdProcessingSourceProvider extends AbstractSourceProvider {
	
	public static final String AVERAGE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableAverage";
	public static final String BACKGROUD_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableBackgroundSubtraction";
	public static final String RESPONSE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableDetectorResponse";
	public static final String INVARIANT_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableInvariant";
	public static final String NORMALISATION_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableNormalisation";
	public static final String SECTOR_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableSectorIntegration";
	public static final String RADIAL_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableRadialIntegration";
	public static final String AZIMUTH_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableAzimuthalIntegration";
	public static final String FASTINT_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableFastIntegration";
	public static final String WAXS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableWaxsDataReduction";
	public static final String SAXS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableSaxsDataReduction";
	public static final String MASK_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableMask";
	public static final String MASKFILE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.maskFile";
	                    
	public static final String SCALER_STATE = "uk.ac.diamond.scisoft.ncd.rcp.scalerDetector";
	public static final String SAXSDETECTOR_STATE = "uk.ac.diamond.scisoft.ncd.rcp.saxsDetector";
	public static final String WAXSDETECTOR_STATE = "uk.ac.diamond.scisoft.ncd.rcp.waxsDetector";
	                    
	public static final String ENERGY_STATE = "uk.ac.diamond.scisoft.ncd.rcp.energy";
	public static final String NORMCHANNEL_STATE = "uk.ac.diamond.scisoft.ncd.rcp.normChannel";
	                    
	public static final String DATASLICE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.dataSlice";
	public static final String BKGSLICE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.bkgSlice";
	public static final String GRIDAVERAGE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.gridAverage";
	                    
	public static final String BKGSCALING_STATE = "uk.ac.diamond.scisoft.ncd.rcp.bgScale";
	public static final String ABSSCALING_STATE = "uk.ac.diamond.scisoft.ncd.rcp.absScale";
	public static final String ABSSCALING_STDDEV_STATE = "uk.ac.diamond.scisoft.ncd.rcp.absScaleStdDev";
	public static final String SAMPLETHICKNESS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.sampleThickness";
	public static final String USEFORMSAMPLETHICKNESS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.useFormSampleThickness";
	
	public static final String BKGFILE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.bkgFile";
	public static final String DRFILE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.drFile";
	public static final String WORKINGDIR_STATE = "uk.ac.diamond.scisoft.ncd.rcp.workingDir";
	
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
	private boolean enableMask = true;
	
	private String scaler, saxsDetector, waxsDetector;
	private String maskFile, bgFile, drFile, workingDir;
	private SliceInput dataSlice, bkgSlice, gridAverage;
	
	private Double bgScaling, absScaling, absScalingStdDev, sampleThickness;
	private boolean useFormSampleThickness;

    @XmlElement
    @XmlJavaTypeAdapter(EnergyXmlAdapter.class)
	private Amount<Energy> energy;

	public NcdProcessingSourceProvider() {
	}

	@Override
	public void dispose() {
	}

	@Override
	public Map<String, Object> getCurrentState() {
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
		currentState.put(DATASLICE_STATE, dataSlice);
		currentState.put(BKGSLICE_STATE, bkgSlice);
		currentState.put(MASKFILE_STATE, maskFile);
		currentState.put(BKGFILE_STATE, bgFile);
		currentState.put(DRFILE_STATE, drFile);
		currentState.put(GRIDAVERAGE_STATE, gridAverage);
		currentState.put(BKGSCALING_STATE, bgScaling);
		currentState.put(ABSSCALING_STATE, absScaling);
		currentState.put(ABSSCALING_STDDEV_STATE, absScalingStdDev);
		currentState.put(SAMPLETHICKNESS_STATE, sampleThickness);
		currentState.put(USEFORMSAMPLETHICKNESS_STATE, useFormSampleThickness);
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
		                     MASKFILE_STATE,
		                     BKGFILE_STATE,
		                     DRFILE_STATE,
		                     GRIDAVERAGE_STATE,
		                     BKGSCALING_STATE,
		                     ABSSCALING_STATE,
		                     ABSSCALING_STDDEV_STATE,
		                     SAMPLETHICKNESS_STATE,
		                     USEFORMSAMPLETHICKNESS_STATE,
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
		this.scaler = scaler;
		fireSourceChanged(ISources.WORKBENCH, SCALER_STATE, this.scaler);
	}
	
	public void setSaxsDetector(String saxsDetector) {
		this.saxsDetector = saxsDetector;
		fireSourceChanged(ISources.WORKBENCH, SAXSDETECTOR_STATE, this.saxsDetector);
	}
	
	public void setWaxsDetector(String waxsDetector) {
		this.waxsDetector = waxsDetector;
		fireSourceChanged(ISources.WORKBENCH, WAXSDETECTOR_STATE, this.waxsDetector);
	}
	
	public void setEnergy(Amount<Energy> energy) {
		this.energy = (energy != null) ? energy.copy() : null;
		fireSourceChanged(ISources.WORKBENCH, ENERGY_STATE, this.energy);
	}

	public void setDataSlice(SliceInput dataSlice) {
		this.dataSlice = (dataSlice != null) ? new SliceInput(dataSlice) : null;
		fireSourceChanged(ISources.WORKBENCH, DATASLICE_STATE, this.dataSlice);
	}

	public void setBkgSlice(SliceInput bkgSlice) {
		this.bkgSlice = (bkgSlice != null) ? new SliceInput(bkgSlice) : null;
		fireSourceChanged(ISources.WORKBENCH, BKGSLICE_STATE, this.bkgSlice);
	}

	public void setMaskFile(String maskFile) {
		this.maskFile = maskFile;
		fireSourceChanged(ISources.WORKBENCH, MASKFILE_STATE, this.maskFile);
	}

	public void setBgFile(String bgFile) {
		this.bgFile = bgFile;
		fireSourceChanged(ISources.WORKBENCH, BKGFILE_STATE, this.bgFile);
	}

	public void setDrFile(String drFile) {
		this.drFile = drFile;
		fireSourceChanged(ISources.WORKBENCH, DRFILE_STATE, this.drFile);
	}

	public void setGrigAverage(SliceInput gridAverage, boolean notify) {
		this.gridAverage = (gridAverage != null) ? new SliceInput(gridAverage.getAdvancedSlice()) : null;
		if (notify) {
			fireSourceChanged(ISources.WORKBENCH, GRIDAVERAGE_STATE, this.gridAverage);
		}
	}

	public void setBgScaling(Double bgScaling, boolean notify) {
		this.bgScaling = (bgScaling != null) ? new Double(bgScaling) : null;
		if (notify) {
			fireSourceChanged(ISources.WORKBENCH, BKGSCALING_STATE, this.bgScaling);
		}
	}

	public void setSampleThickness(Double sampleThickness, boolean notify) {
		this.sampleThickness = (sampleThickness != null) ? new Double(sampleThickness) : null;
		if (notify) {
			fireSourceChanged(ISources.WORKBENCH, SAMPLETHICKNESS_STATE, this.sampleThickness);
		}
	}

	public void setUseFormSampleThickness(boolean useFormSampleThickness, boolean notify) {
		this.useFormSampleThickness = useFormSampleThickness;
		if (notify) {
			fireSourceChanged(ISources.WORKBENCH, USEFORMSAMPLETHICKNESS_STATE, this.useFormSampleThickness);
		}
	}

	public void setAbsScaling(Double absScaling, boolean notify) {
		this.absScaling = (absScaling != null) ? new Double(absScaling) : null;
		if (notify) {
			fireSourceChanged(ISources.WORKBENCH, ABSSCALING_STATE, this.absScaling);
		}
	}

	public void setAbsScalingStdDev(Double absScalingStdDev, boolean notify) {
		this.absScalingStdDev = (absScalingStdDev != null) ? new Double(absScalingStdDev) : null;
		if (notify) {
			fireSourceChanged(ISources.WORKBENCH, ABSSCALING_STDDEV_STATE, this.absScalingStdDev);
		}
	}

	public void setWorkingDir(String workingDir) {
		this.workingDir = workingDir;
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

	public String getMaskFile() {
		return maskFile;
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

	public Double getBgScaling() {
		return bgScaling;
	}

	public Double getAbsScaling() {
		return absScaling;
	}

	public Double getAbsScalingStdDev() {
		return absScalingStdDev;
	}

	public Double getSampleThickness() {
		return sampleThickness;
	}
	
	public boolean isUseFormSampleThickness() {
		return useFormSampleThickness;
	}
	
	public Amount<Energy> getEnergy() {
		return energy;
	}
	
	public void setAll(NcdProcessingSourceProvider sourceProvider) {
		
		Map<String, Object> sourceState = sourceProvider.getCurrentState();
		
		enableAverage          = (Boolean) sourceState.get(AVERAGE_STATE);      
		enableBackground       = (Boolean) sourceState.get(BACKGROUD_STATE);     
		enableDetectorResponse = (Boolean) sourceState.get(RESPONSE_STATE);      
		enableInvariant        = (Boolean) sourceState.get(INVARIANT_STATE);     
		enableNormalisation    = (Boolean) sourceState.get(NORMALISATION_STATE);
		enableSector           = (Boolean) sourceState.get(SECTOR_STATE);               
		enableRadial           = (Boolean) sourceState.get(RADIAL_STATE);               
		enableAzimuthal        = (Boolean) sourceState.get(AZIMUTH_STATE);              
		enableFastIntegration  = (Boolean) sourceState.get(FASTINT_STATE);              
		enableWaxs             = (Boolean) sourceState.get(WAXS_STATE);                 
		enableSaxs             = (Boolean) sourceState.get(SAXS_STATE);                 
		enableMask             = (Boolean) sourceState.get(MASK_STATE);                 
		scaler                 = (String) sourceState.get(SCALER_STATE);               
		saxsDetector           = (String) sourceState.get(SAXSDETECTOR_STATE);         
		waxsDetector           = (String) sourceState.get(WAXSDETECTOR_STATE);         
		energy                 = (Amount<Energy>) sourceState.get(ENERGY_STATE);               
		dataSlice              = (SliceInput) sourceState.get(DATASLICE_STATE);            
		bkgSlice               = (SliceInput) sourceState.get(BKGSLICE_STATE);             
		maskFile               = (String) sourceState.get(MASKFILE_STATE);
		bgFile                 = (String) sourceState.get(BKGFILE_STATE);              
		drFile                 = (String) sourceState.get(DRFILE_STATE);               
		gridAverage            = (SliceInput) sourceState.get(GRIDAVERAGE_STATE);          
		bgScaling              = (Double) sourceState.get(BKGSCALING_STATE);           
		absScaling             = (Double) sourceState.get(ABSSCALING_STATE);           
		absScalingStdDev       = (Double) sourceState.get(ABSSCALING_STDDEV_STATE);
		sampleThickness        = (Double) sourceState.get(SAMPLETHICKNESS_STATE);      
		useFormSampleThickness = (Boolean) sourceState.get(USEFORMSAMPLETHICKNESS_STATE);
		workingDir             = (String) sourceState.get(WORKINGDIR_STATE);           
		
		fireSourceChanged(ISources.WORKBENCH, getCurrentState());
	}
}
