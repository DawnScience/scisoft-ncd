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

package uk.ac.diamond.scisoft.ncd.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.measure.quantity.Length;
import javax.measure.unit.Unit;

import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.fitting.functions.AFunction;

class CalibrationResultsData implements Serializable {
	private AFunction fuction;
	private ArrayList<CalibrationPeak> peakList;
	private Amount<Length> meanCameraLength;
	private Unit<Length> unit;
	
	public CalibrationResultsData(AFunction calibrationFunction, Unit<Length> unit) {
		super();
		this.fuction= calibrationFunction;
		this.unit = unit;
	}

	CalibrationResultsData(AFunction calibrationFunction, List<CalibrationPeak> peaks, Amount<Length> meanCameraLength, Unit<Length> unit) {
		super();
		this.fuction= calibrationFunction;
		
		if (peaks != null) {
			this.peakList = new ArrayList<CalibrationPeak>();
			for(CalibrationPeak p : peaks)
				peakList.add(p);
		} else 
			this.peakList = null;
		
		if (meanCameraLength != null)
			this.meanCameraLength = meanCameraLength.copy();
		else 
			this.meanCameraLength = null;
		
		this.unit = unit;
	}
	
	public AFunction getFuction() {
		return fuction;
	}

	public ArrayList<CalibrationPeak> getPeakList() {
		return peakList;
	}

	public Amount<Length> getMeanCameraLength() {
		return meanCameraLength;
	}
	
	public Unit<Length> getUnit() {
		return unit;
	}
}