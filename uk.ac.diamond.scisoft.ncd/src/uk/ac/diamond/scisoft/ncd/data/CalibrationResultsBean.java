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
import javax.xml.bind.annotation.XmlAnyAttribute;

import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;

public class CalibrationResultsBean implements Serializable {
	
	@XmlAnyAttribute
	private HashMap<String, CalibrationResultsData> results;

	public CalibrationResultsBean() {
		results = new HashMap<String, CalibrationResultsData>();
	}
	
	public CalibrationResultsBean(String detector, Amount<ScatteringVectorOverDistance> gradient, Amount<ScatteringVector> intercept, List<CalibrationPeak> peaks, Amount<Length> meanCameraLength, Unit<Length> unit) {
		results = new HashMap<String, CalibrationResultsData>();
		putCalibrationResult(detector, gradient, intercept, peaks, meanCameraLength, unit); 
	}
	
	public void putCalibrationResult(String detector, Amount<ScatteringVectorOverDistance> gradient, Amount<ScatteringVector> intercept, List<CalibrationPeak> peaks, Amount<Length> meanCameraLength, Unit<Length> unit) {
		CalibrationResultsData newData = new CalibrationResultsData(gradient, intercept, peaks, meanCameraLength, unit);	
		results.put(detector, newData);
	}
	
	public ArrayList<CalibrationPeak> getPeakList(String detector) {
		if (results.containsKey(detector)) {
			return results.get(detector).getPeakList();
		}
		return null;
	}

	public Amount<ScatteringVectorOverDistance> getGradient(String detector) {
		if (results.containsKey(detector)) {
			return results.get(detector).getGradient();
		}
		return null;
	}
	
	public Amount<ScatteringVector> getIntercept(String detector) {
		if (results.containsKey(detector)) {
			return results.get(detector).getIntercept();
		}
		return null;
	}
	
	public Amount<Length> getMeanCameraLength(String detector) {
		if (results.containsKey(detector)) {
			return results.get(detector).getMeanCameraLength();
		}
		return null;
	}
	
	public Unit<Length> getUnit(String detector) {
		if (results.containsKey(detector)) {
			return results.get(detector).getUnit();
		}
		return null;
	}
	
	public boolean containsKey(String detector) {
		return results.containsKey(detector);
	}
	
	public void clearData(String detector) {
		if (results.containsKey(detector)) {
			results.remove(detector);
		}
	}
	
	public Set<String> keySet() {
		return results.keySet();
	}
	
	public void clearAllData() {
		results.clear();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((results == null) ? 0 : results.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CalibrationResultsBean other = (CalibrationResultsBean) obj;
		if (results == null) {
			if (other.results != null)
				return false;
		} else if (!results.equals(other.results))
			return false;
		return true;
	}	
}
