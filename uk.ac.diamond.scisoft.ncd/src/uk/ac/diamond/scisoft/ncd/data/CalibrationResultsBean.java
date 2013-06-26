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

package uk.ac.diamond.scisoft.ncd.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.measure.quantity.Length;
import javax.measure.unit.Unit;

import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;

public class CalibrationResultsBean implements Serializable {
	
	private HashMap<String, CalibrationResultsData> results;

	public CalibrationResultsBean() {
		results = new HashMap<String, CalibrationResultsData>();
	}
	
	public CalibrationResultsBean(String experiment, Amount<ScatteringVectorOverDistance> gradient, Amount<ScatteringVector> intercept, List<CalibrationPeak> peaks, Amount<Length> meanCameraLength, Unit<Length> unit) {
		results = new HashMap<String, CalibrationResultsData>();
		putCalibrationResult(experiment, gradient, intercept, peaks, meanCameraLength, unit); 
	}
	
	public void putCalibrationResult(String experiment, Amount<ScatteringVectorOverDistance> gradient, Amount<ScatteringVector> intercept, List<CalibrationPeak> peaks, Amount<Length> meanCameraLength, Unit<Length> unit) {
		CalibrationResultsData newData = new CalibrationResultsData(gradient, intercept, peaks, meanCameraLength, unit);	
		results.put(experiment, newData);
	}
	
	public ArrayList<CalibrationPeak> getPeakList(String experiment) {
		if (results.containsKey(experiment))
			return results.get(experiment).getPeakList();
		return null;
	}

	public Amount<ScatteringVectorOverDistance> getGradient(String experiment) {
		if (results.containsKey(experiment))
			return results.get(experiment).getGradient();
		return null;
	}
	
	public Amount<ScatteringVector> getIntercept(String experiment) {
		if (results.containsKey(experiment))
			return results.get(experiment).getIntercept();
		return null;
	}
	
	public Amount<Length> getMeanCameraLength(String experiment) {
		if (results.containsKey(experiment))
			return results.get(experiment).getMeanCameraLength();
		return null;
	}
	
	public Unit<Length> getUnit(String experiment) {
		if (results.containsKey(experiment))
			return results.get(experiment).getUnit();
		return null;
	}
	
	public boolean containsKey(String experiment) {
		return results.containsKey(experiment);
	}
	
	public void clearData(String experiment) {
		if (results.containsKey(experiment))
			results.remove(experiment);
	}
	
	public Set<String> keySet() {
		return results.keySet();
	}
	
	public void clearAllData() {
		results.clear();
	}
	
}
