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
import java.util.Map;

import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISources;

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
	
	static boolean enableAverage = false;
	static boolean enableBackground = false;
	static boolean enableDetectorResponse = false;
	static boolean enableInvariant = false;
	static boolean enableNormalisation = false;
	static boolean enableSector = false;
	static boolean enableRadial = false;
	static boolean enableAzimuthal = false;
	static boolean enableFastIntegration = false;
	static boolean enableWaxs = false;
	static boolean enableSaxs = false;
	static boolean enableMask = false;


	public NcdProcessingSourceProvider() {

	}

	@Override
	public void dispose() {

	}

	@Override
	public Map<String, Boolean> getCurrentState() {
		Map<String, Boolean> currentState = new HashMap<String, Boolean>(1);
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
		                     MASK_STATE};
	}

	public void setEnableAverage(boolean enableAverage) {
		NcdProcessingSourceProvider.enableAverage = enableAverage;
		fireSourceChanged(ISources.WORKBENCH, AVERAGE_STATE, enableAverage);
	}

	public void setEnableBackground(boolean enableBackground) {
		NcdProcessingSourceProvider.enableBackground = enableBackground;
		fireSourceChanged(ISources.WORKBENCH, BACKGROUD_STATE, enableBackground);
	}

	public void setEnableDetectorResponse(boolean enableDetectorResponse) {
		NcdProcessingSourceProvider.enableDetectorResponse = enableDetectorResponse;
		fireSourceChanged(ISources.WORKBENCH, RESPONSE_STATE, enableDetectorResponse);
	}

	public void setEnableInvariant(boolean enableInvariant) {
		NcdProcessingSourceProvider.enableInvariant = enableInvariant;
		fireSourceChanged(ISources.WORKBENCH, INVARIANT_STATE, enableInvariant);
	}

	public void setEnableNormalisation(boolean enableNormalisation) {
		NcdProcessingSourceProvider.enableNormalisation = enableNormalisation;
		fireSourceChanged(ISources.WORKBENCH, NORMALISATION_STATE, enableNormalisation);
	}

	public void setEnableSector(boolean enableSector) {
		NcdProcessingSourceProvider.enableSector = enableSector;
		fireSourceChanged(ISources.WORKBENCH, SECTOR_STATE, enableSector);
	}

	public void setEnableRadial(boolean enableRadial) {
		NcdProcessingSourceProvider.enableRadial = enableRadial;
		fireSourceChanged(ISources.WORKBENCH, RADIAL_STATE, enableRadial);
	}

	public void setEnableAzimuthal(boolean enableAzimuthal) {
		NcdProcessingSourceProvider.enableAzimuthal = enableAzimuthal;
		fireSourceChanged(ISources.WORKBENCH, AZIMUTH_STATE, enableAzimuthal);
	}

	public void setEnableFastIntegration(boolean enableFastIntegration) {
		NcdProcessingSourceProvider.enableFastIntegration = enableFastIntegration;
		fireSourceChanged(ISources.WORKBENCH, FASTINT_STATE, enableFastIntegration);
	}

	public void setEnableWaxs(boolean enableWaxs) {
		NcdProcessingSourceProvider.enableWaxs = enableWaxs;
		fireSourceChanged(ISources.WORKBENCH, WAXS_STATE, enableWaxs);
	}

	public void setEnableSaxs(boolean enableSaxs) {
		NcdProcessingSourceProvider.enableSaxs = enableSaxs;
		fireSourceChanged(ISources.WORKBENCH, SAXS_STATE, enableSaxs);
	}

	public void setEnableMask(boolean enableMask) {
		NcdProcessingSourceProvider.enableMask = enableMask;
		fireSourceChanged(ISources.WORKBENCH, MASK_STATE, enableMask);
	}

	public static boolean isEnableAverage() {
		return enableAverage;
	}

	public static boolean isEnableBackground() {
		return enableBackground;
	}

	public static boolean isEnableDetectorResponse() {
		return enableDetectorResponse;
	}

	public static boolean isEnableInvariant() {
		return enableInvariant;
	}

	public static boolean isEnableNormalisation() {
		return enableNormalisation;
	}

	public static boolean isEnableSector() {
		return enableSector;
	}

	public static boolean isEnableRadial() {
		return enableRadial;
	}

	public static boolean isEnableAzimuthal() {
		return enableAzimuthal;
	}

	public static boolean isEnableFastIntegration() {
		return enableFastIntegration;
	}

	public static boolean isEnableWaxs() {
		return enableWaxs;
	}

	public static boolean isEnableSaxs() {
		return enableSaxs;
	}

	public static boolean isEnableMask() {
		return enableMask;
	}
}
