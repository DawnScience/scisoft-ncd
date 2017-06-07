/*
 * Copyright 2012, 2017 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.core.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.measure.quantity.Length;
import javax.measure.Unit;

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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((gradient == null) ? 0 : gradient.hashCode());
		result = prime * result + ((intercept == null) ? 0 : intercept.hashCode());
		result = prime * result + ((meanCameraLength == null) ? 0 : meanCameraLength.hashCode());
		result = prime * result + ((peakList == null) ? 0 : peakList.hashCode());
		result = prime * result + ((unit == null) ? 0 : unit.hashCode());
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
		CalibrationResultsData other = (CalibrationResultsData) obj;
		if (gradient == null) {
			if (other.gradient != null)
				return false;
		} else if (!gradient.equals(other.gradient))
			return false;
		if (intercept == null) {
			if (other.intercept != null)
				return false;
		} else if (!intercept.equals(other.intercept))
			return false;
		if (meanCameraLength == null) {
			if (other.meanCameraLength != null)
				return false;
		} else if (!meanCameraLength.equals(other.meanCameraLength))
			return false;
		if (peakList == null) {
			if (other.peakList != null)
				return false;
		} else if (!peakList.equals(other.peakList))
			return false;
		if (unit == null) {
			if (other.unit != null)
				return false;
		} else if (!unit.equals(other.unit))
			return false;
		return true;
	}
}