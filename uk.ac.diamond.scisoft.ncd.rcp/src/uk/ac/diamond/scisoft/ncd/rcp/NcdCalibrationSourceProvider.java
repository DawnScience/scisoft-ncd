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

package uk.ac.diamond.scisoft.ncd.rcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.measure.quantity.Length;
import javax.measure.unit.Unit;

import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISources;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.fitting.functions.AFunction;
import uk.ac.diamond.scisoft.ncd.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.data.NcdDetectorSettings;

public class NcdCalibrationSourceProvider extends AbstractSourceProvider {

	public final static String CALIBRATION_STATE = "uk.ac.diamond.scisoft.ncd.rcp.calibrationBean";
	public final static String NCDDETECTORS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.ncdDetectors";
	
	private CalibrationResultsBean calibrationResults; 
	private HashMap<String, NcdDetectorSettings> ncdDetectors;

	public NcdCalibrationSourceProvider() {
		calibrationResults = new CalibrationResultsBean();
		ncdDetectors = new HashMap<String, NcdDetectorSettings>();
	}

	@Override
	public void dispose() {
		calibrationResults.clearAllData();
		ncdDetectors.clear();
	}

	@Override
	public Map<String, Object> getCurrentState() {
		Map<String, Object> currentState = new HashMap<String, Object>();
		currentState.put(CALIBRATION_STATE, calibrationResults);
		currentState.put(NCDDETECTORS_STATE, ncdDetectors);
		return currentState;
	}

	@Override
	public String[] getProvidedSourceNames() {
		return new String[] {CALIBRATION_STATE,
                			NCDDETECTORS_STATE};
	}

	public CalibrationResultsBean getCalibrationResults() {
		return calibrationResults;
	}

	public void putCalibrationResult(CalibrationResultsBean crb) {
		for (String experiment : crb.keySet()) {
			AFunction calibrationFunction = crb.getFunction(experiment);
			List<CalibrationPeak> peaks = crb.getPeakList(experiment);
			Amount<Length> meanCameraLength = crb.getMeanCameraLength(experiment);
			Unit<Length> unit = crb.getUnit(experiment);
			calibrationResults.putCalibrationResult(experiment, calibrationFunction, peaks, meanCameraLength, unit);
		}
		
		fireSourceChanged(ISources.WORKBENCH, CALIBRATION_STATE, crb);
	}
	
	public AFunction getFunction(String experiment) {
		return calibrationResults.getFunction(experiment);
	}
	
	public Amount<Length> getMeanCameraLength(String experiment) {
		return calibrationResults.getMeanCameraLength(experiment);
	}
	
	public ArrayList<CalibrationPeak> getPeakList(String experiment) {
		return calibrationResults.getPeakList(experiment);
	}
	
	public Unit<Length> getUnit(String experiment) {
		return calibrationResults.getUnit(experiment);
	}

	public void addNcdDetector(NcdDetectorSettings ncdDetector) {
		ncdDetectors.put(ncdDetector.getName(), new NcdDetectorSettings(ncdDetector));
	}

	public void updateNcdDetectors() {
		fireSourceChanged(ISources.WORKBENCH, NCDDETECTORS_STATE, ncdDetectors);
	}

	public void clearNcdDetectors() {
		ncdDetectors.clear();
	}

	public HashMap<String, NcdDetectorSettings> getNcdDetectors() {
		return ncdDetectors;
	}
}
