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

package uk.ac.diamond.scisoft.ncd.core.rcp;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISources;

public class SaxsPlotsSourceProvider extends AbstractSourceProvider {

	public static final String LOGLOG_STATE       = "uk.ac.diamond.scisoft.ncd.rcp.enableLogLog";
	public static final String GUINIER_STATE      = "uk.ac.diamond.scisoft.ncd.rcp.enableGuinier";
	public static final String POROD_STATE        = "uk.ac.diamond.scisoft.ncd.rcp.enablePorod";
	public static final String KRATKY_STATE       = "uk.ac.diamond.scisoft.ncd.rcp.enableKratky";
	public static final String ZIMM_STATE         = "uk.ac.diamond.scisoft.ncd.rcp.enableZimm";
	public static final String DEBYE_BUECHE_STATE = "uk.ac.diamond.scisoft.ncd.rcp.enableDebyeBueche";
	
	private boolean enableLogLog = false;
	private boolean enableGuinier = false;
	private boolean enablePorod = false;
	private boolean enableKratky = false;
	private boolean enableZimm = false;
	private boolean enableDebyeBueche = false;
	
	@Override
	public void dispose() {
	}

	@Override
	public Map getCurrentState() {
		Map<String, Object> currentState = new HashMap<String, Object>();
		currentState.put(LOGLOG_STATE,       enableLogLog);
		currentState.put(GUINIER_STATE,      enableGuinier);
		currentState.put(POROD_STATE,        enablePorod);
		currentState.put(KRATKY_STATE,       enableKratky);
		currentState.put(ZIMM_STATE,         enableZimm);
		currentState.put(DEBYE_BUECHE_STATE, enableDebyeBueche);
		
		return currentState;
	}

	@Override
	public String[] getProvidedSourceNames() {
		
		return new String[] {LOGLOG_STATE,
                GUINIER_STATE,
                POROD_STATE,
                KRATKY_STATE,
                ZIMM_STATE,
                DEBYE_BUECHE_STATE};
	}

	public boolean isEnableLogLog() {
		return enableLogLog;
	}

	public void setEnableLogLog(boolean enableLogLog) {
		this.enableLogLog = enableLogLog;
		fireSourceChanged(ISources.WORKBENCH, LOGLOG_STATE, enableLogLog);
	}

	public boolean isEnableGuinier() {
		return enableGuinier;
	}

	public void setEnableGuinier(boolean enableGuinier) {
		this.enableGuinier = enableGuinier;
		fireSourceChanged(ISources.WORKBENCH, GUINIER_STATE, enableGuinier);
	}

	public boolean isEnablePorod() {
		return enablePorod;
	}

	public void setEnablePorod(boolean enablePorod) {
		this.enablePorod = enablePorod;
		fireSourceChanged(ISources.WORKBENCH, POROD_STATE, enablePorod);
	}

	public boolean isEnableKratky() {
		return enableKratky;
	}

	public void setEnableKratky(boolean enableKratky) {
		this.enableKratky = enableKratky;
		fireSourceChanged(ISources.WORKBENCH, KRATKY_STATE, enableKratky);
	}

	public boolean isEnableZimm() {
		return enableZimm;
	}

	public void setEnableZimm(boolean enableZimm) {
		this.enableZimm = enableZimm;
		fireSourceChanged(ISources.WORKBENCH, ZIMM_STATE, enableZimm);
	}

	public boolean isEnableDebyeBueche() {
		return enableDebyeBueche;
	}

	public void setEnableDebyeBueche(boolean enableDebyeBueche) {
		this.enableDebyeBueche = enableDebyeBueche;
		fireSourceChanged(ISources.WORKBENCH, DEBYE_BUECHE_STATE, enableDebyeBueche);
	}

	public void setAll(SaxsPlotsSourceProvider sourceProvider) {
		
		Map<String, Object> sourceState = sourceProvider.getCurrentState();
		
		enableLogLog = (Boolean) sourceState.get(LOGLOG_STATE);
		enableGuinier = (Boolean) sourceState.get(GUINIER_STATE);
		enablePorod = (Boolean) sourceState.get(POROD_STATE);
		enableKratky = (Boolean) sourceState.get(KRATKY_STATE);
		enableZimm = (Boolean) sourceState.get(ZIMM_STATE);
		enableDebyeBueche = (Boolean) sourceState.get(DEBYE_BUECHE_STATE);
		
		fireSourceChanged(ISources.WORKBENCH, getCurrentState());
	}
}
