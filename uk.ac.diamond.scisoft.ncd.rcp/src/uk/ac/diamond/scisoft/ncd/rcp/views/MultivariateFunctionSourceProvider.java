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

package uk.ac.diamond.scisoft.ncd.rcp.views;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISources;

import uk.ac.diamond.scisoft.analysis.fitting.functions.IPeak;

public class MultivariateFunctionSourceProvider extends AbstractSourceProvider {

	public static final String SECTORREFINEMENT_STATE = "uk.ac.diamond.scisoft.ncd.rcp.beamxy";
	public static final String PEAKREFINEMENT_STATE = "uk.ac.diamond.scisoft.ncd.rcp.peaks";
	
	private double[] beamxy; 
	private List<IPeak>  peaks;

	public MultivariateFunctionSourceProvider() {
		beamxy = new double[2];
		peaks = new ArrayList<IPeak>();
	}

	@Override
	public void dispose() {
	}

	@Override
	public Map<String, Object> getCurrentState() {
		Map<String, Object> currentState = new HashMap<String, Object>();
		currentState.put(SECTORREFINEMENT_STATE, beamxy);
		currentState.put(PEAKREFINEMENT_STATE, peaks);
		return currentState;
	}

	@Override
	public String[] getProvidedSourceNames() {
		return new String[] {SECTORREFINEMENT_STATE,
				PEAKREFINEMENT_STATE};
	}

	public double[] getBeamPosition() {
		return beamxy;
	}

	public void putBeamPosition(double[] beamxy) {
		this.beamxy = Arrays.copyOf(beamxy, beamxy.length);
		fireSourceChanged(ISources.WORKBENCH, SECTORREFINEMENT_STATE, beamxy);
	}
	
	public List<IPeak> getPeaks() {
		return peaks;
	}

	public void putPeaks(List<IPeak> peaks) {
		this.peaks = new ArrayList<IPeak>(peaks);
		Collections.copy(this.peaks, peaks);
		fireSourceChanged(ISources.WORKBENCH, PEAKREFINEMENT_STATE, peaks);
	}
	
}