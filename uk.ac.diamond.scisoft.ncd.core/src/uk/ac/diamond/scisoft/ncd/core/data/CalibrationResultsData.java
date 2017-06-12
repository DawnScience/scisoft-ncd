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

import tec.units.ri.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.Unit;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;

class CalibrationResultsData<V extends ScatteringVector<V>, D extends ScatteringVectorOverDistance<D>>
		implements Serializable {
	private ArrayList<CalibrationPeak> peakList;
	private Quantity<V> intercept;
	private Quantity<D> gradient;
	private Quantity<Length> meanCameraLength;
	private Unit<Length> unit;
	
	public CalibrationResultsData(Quantity<D> gradient, Quantity<V> intercept, Unit<Length> unit) {
		super();
		this.gradient= Quantities.getQuantity(gradient.getValue(), gradient.getUnit());
		this.intercept= Quantities.getQuantity(intercept.getValue(), intercept.getUnit());
		this.unit = unit;
	}

	CalibrationResultsData(Quantity<D> gradient, Quantity<V> intercept, List<CalibrationPeak> peaks, Quantity<Length> meanCameraLength, Unit<Length> unit) {
		super();
		this.gradient= Quantities.getQuantity(gradient.getValue(), gradient.getUnit());
		this.intercept= Quantities.getQuantity(intercept.getValue(), intercept.getUnit());
		
		if (peaks != null) {
			this.peakList = new ArrayList<CalibrationPeak>();
			for(CalibrationPeak p : peaks)
				peakList.add(p);
		} else 
			this.peakList = null;
		
		if (meanCameraLength != null)
			this.meanCameraLength = Quantities.getQuantity(meanCameraLength.getValue(), meanCameraLength.getUnit());
		else 
			this.meanCameraLength = null;
		
		this.unit = unit;
	}
	
	public Quantity<V> getIntercept() {
		return intercept;
	}

	public Quantity<D> getGradient() {
		return gradient;
	}

	public ArrayList<CalibrationPeak> getPeakList() {
		return peakList;
	}

	public Quantity<Length> getMeanCameraLength() {
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