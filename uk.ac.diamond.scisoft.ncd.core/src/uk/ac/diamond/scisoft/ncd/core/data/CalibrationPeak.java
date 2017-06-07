/*
 * Copyright 2011, 2017 Diamond Light Source Ltd.
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

import javax.measure.quantity.Angle;
import javax.measure.quantity.Length;

import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.HKL;

public class CalibrationPeak implements Serializable {
	private double peakPos;
	private Amount<Angle> twoTheta;
	private HKL reflection;

	public CalibrationPeak(double peakPos, Amount<Angle> angle, HKL reflection) {
		super();
		this.peakPos = peakPos;
		this.twoTheta = angle.copy();
		this.reflection = reflection.clone();
	}

	public double getPeakPos() {
		return peakPos;
	}

	public Amount<Angle> getTwoTheta() {
		return twoTheta.copy();
	}

	public Amount<Length> getDSpacing() {
		return reflection.getD();
	}

	public HKL getReflection() {
		return reflection.clone();
	}
	
}
