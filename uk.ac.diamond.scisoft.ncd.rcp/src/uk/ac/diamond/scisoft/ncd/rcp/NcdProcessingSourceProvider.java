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
	public final static String WAXS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableWaxsDataReduction";
	public final static String SAXS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableSaxsDataReduction";
	public final static String MASK_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableMask";
	
	static boolean enableAverage = false;
	static boolean enableBackground = false;
	static boolean enableDetectorResponse = false;
	static boolean enableInvariant = false;
	static boolean enableNormalisation = false;
	static boolean enableSector = false;
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
