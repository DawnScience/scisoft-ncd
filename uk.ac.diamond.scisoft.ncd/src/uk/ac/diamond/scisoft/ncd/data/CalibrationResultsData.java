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

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;

class CalibrationResultsData implements Serializable {
	private ArrayList<CalibrationPeak> peakList;
	private Amount<ScatteringVector> intercept;
	private Amount<ScatteringVectorOverDistance> gradient;
	private Amount<Length> meanCameraLength;
	private Unit<Length> unit;
	
	public CalibrationResultsData(Amount<ScatteringVectorOverDistance> gradient, Amount<ScatteringVector> intercept, Unit<Length> unit) {
		super();
		this.gradient= gradient.copy();
		this.intercept= intercept.copy();
		this.unit = unit;
	}

	CalibrationResultsData(Amount<ScatteringVectorOverDistance> gradient, Amount<ScatteringVector> intercept, List<CalibrationPeak> peaks, Amount<Length> meanCameraLength, Unit<Length> unit) {
		super();
		this.gradient= gradient.copy();
		this.intercept= intercept.copy();
		
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
	
	public Amount<ScatteringVector> getIntercept() {
		return intercept;
	}

	public Amount<ScatteringVectorOverDistance> getGradient() {
		return gradient;
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