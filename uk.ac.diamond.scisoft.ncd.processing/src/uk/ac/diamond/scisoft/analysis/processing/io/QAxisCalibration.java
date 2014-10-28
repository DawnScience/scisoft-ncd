/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.processing.io;

import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;

public class QAxisCalibration {

	public Amount<ScatteringVectorOverDistance> gradient;
	public Amount<ScatteringVectorOverDistance> gradient_errors;
	public Amount<ScatteringVector> intercept;
	public Amount<ScatteringVector> intercept_errors;
	public Amount<ScatteringVectorOverDistance> getGradient() {
		return gradient;
	}
	public void setGradient(Amount<ScatteringVectorOverDistance> gradient) {
		this.gradient = gradient;
	}
	public Amount<ScatteringVectorOverDistance> getGradientErrors() {
		return gradient_errors;
	}
	public void setGradientErrors(
			Amount<ScatteringVectorOverDistance> gradient_errors) {
		this.gradient_errors = gradient_errors;
	}
	public Amount<ScatteringVector> getIntercept() {
		return intercept;
	}
	public void setIntercept(Amount<ScatteringVector> intercept) {
		this.intercept = intercept;
	}
	public Amount<ScatteringVector> getInterceptErrors() {
		return intercept_errors;
	}
	public void setInterceptErrors(Amount<ScatteringVector> intercept_errors) {
		this.intercept_errors = intercept_errors;
	}

}