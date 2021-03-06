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

package uk.ac.diamond.scisoft.ncd.core.rcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.measure.quantity.Length;
import javax.measure.unit.Unit;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISources;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.data.xml.CalibrationResultsXmlAdapter;

@XmlAccessorType(XmlAccessType.FIELD)

public class NcdCalibrationSourceProvider extends AbstractSourceProvider {

	public final static String CALIBRATION_STATE = "uk.ac.diamond.scisoft.ncd.rcp.calibrationBean";
	public final static String NCDDETECTORS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.ncdDetectors";
	
	@XmlJavaTypeAdapter(CalibrationResultsXmlAdapter.class)
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
			Amount<ScatteringVectorOverDistance> gradient = crb.getGradient(experiment);
			Amount<ScatteringVector> intercept = crb.getIntercept(experiment);
			List<CalibrationPeak> peaks = crb.getPeakList(experiment);
			Amount<Length> meanCameraLength = crb.getMeanCameraLength(experiment);
			Unit<Length> unit = crb.getUnit(experiment);
			calibrationResults.putCalibrationResult(experiment, gradient, intercept, peaks, meanCameraLength, unit);
		}
		
		fireSourceChanged(ISources.WORKBENCH, CALIBRATION_STATE, crb);
	}
	
	public Amount<ScatteringVectorOverDistance> getGradient(String experiment) {
		return calibrationResults.getGradient(experiment);
	}

	public Amount<ScatteringVector> getIntercept(String experiment) {
		return calibrationResults.getIntercept(experiment);
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
	
	public void setAll(NcdCalibrationSourceProvider sourceProvider) {
		
		Map<String, Object> sourceState = sourceProvider.getCurrentState();
		
		ncdDetectors       = (HashMap<String, NcdDetectorSettings>) sourceState.get(NCDDETECTORS_STATE);
		updateNcdDetectors();
		
		calibrationResults = (CalibrationResultsBean) sourceState.get(CALIBRATION_STATE);
		fireSourceChanged(ISources.WORKBENCH, CALIBRATION_STATE, calibrationResults);
		
	}
}
