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

import uk.ac.diamond.scisoft.analysis.fitting.functions.AFunction;

public class CalibrationResultsBean implements Serializable {
	
	private HashMap<String, CalibrationResultsData> results;

	public CalibrationResultsBean() {
		results = new HashMap<String, CalibrationResultsData>();
	}
	
	public CalibrationResultsBean(String experiment, AFunction calibrationFunction, List<CalibrationPeak> peaks, double meanCameraLength, String unit) {
		
		results = new HashMap<String, CalibrationResultsData>();
		CalibrationResultsData newData = new CalibrationResultsData(calibrationFunction, peaks, meanCameraLength, unit); 
		results.put(experiment, newData);
	}
	
	public void putCalibrationResult(String experiment, AFunction calibrationFunction, String unit) {
		CalibrationResultsData newData = new CalibrationResultsData(calibrationFunction, unit); 
		results.put(experiment, newData);
	}
	
	public void putCalibrationResult(String experiment, AFunction calibrationFunction, List<CalibrationPeak> peaks, Double meanCameraLength, String unit) {
		CalibrationResultsData newData; 
		if (peaks != null && meanCameraLength != null)
			newData = new CalibrationResultsData(calibrationFunction, peaks, meanCameraLength, unit);
		else 
			newData = new CalibrationResultsData(calibrationFunction, unit);
		
		results.put(experiment, newData);
	}
	
	public AFunction getFunction(String experiment) {
		return results.get(experiment).getFuction();
	}

	public ArrayList<CalibrationPeak> getPeakList(String experiment) {
		return results.get(experiment).getPeakList();
	}

	public Double getMeanCameraLength(String experiment) {
		return results.get(experiment).getMeanCameraLength();
	}
	
	public String getUnit(String experiment) {
		return results.get(experiment).getUnit();
	}
	
	public boolean containsKey(String experiment) {
		return results.containsKey(experiment);
	}
	
	public void clearData(String experiment) {
		results.remove(experiment);
	}
	
	public Set<String> keySet() {
		return results.keySet();
	}
	
	public void clearAllData() {
		results.clear();
	}
	
}
