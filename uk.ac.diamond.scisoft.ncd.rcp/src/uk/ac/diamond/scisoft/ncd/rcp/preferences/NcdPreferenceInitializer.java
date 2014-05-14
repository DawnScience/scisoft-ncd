/*
 * Copyright 2012 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.rcp.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStatsParameters;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;

public class NcdPreferenceInitializer extends AbstractPreferenceInitializer {
	
	private final boolean runModal = true;
	private final Integer cmaesLambda = 5;
	private final Integer cmaesInputSigma = 3;
	private final Integer cmaesMaxIterations = 1000;
	private final Integer cmaesChecker = 4;
	
	public NcdPreferenceInitializer() {
		super();
	}

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		
		store.setDefault(NcdPreferences.NCD_REDUCTION_MODAL, runModal);
		
		store.setDefault(NcdPreferences.CMAESlambda, cmaesLambda);
		store.setDefault(NcdPreferences.CMAESsigma, cmaesInputSigma);
		store.setDefault(NcdPreferences.CMAESmaxiteration, cmaesMaxIterations);
		store.setDefault(NcdPreferences.CMAESchecker, cmaesChecker);
		
		store.setDefault(NcdPreferences.SAXS_SELECTION_ALGORITHM,
				SaxsAnalysisStatsParameters.DEFAULT_SELECTION_METHOD.getName());
		store.setDefault(NcdPreferences.DBSCANClusterer_EPSILON,
				Double.toString(SaxsAnalysisStatsParameters.DBSCAN_CLUSTERER_EPSILON));
		store.setDefault(NcdPreferences.DBSCANClusterer_MINPOINTS,
				Double.toString(SaxsAnalysisStatsParameters.DBSCAN_CLUSTERER_MINPOINTS));
		store.setDefault(NcdPreferences.SAXS_FILTERING_CI,
				Double.toString(SaxsAnalysisStatsParameters.SAXS_FILTERING_CI));
	}
}
