/*
 * Copyright (c) 2014, 2017 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.processing.io;

import javax.measure.Quantity;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;

public class QAxisCalibration <V extends ScatteringVector<V>, D extends ScatteringVectorOverDistance<D>> {

	public Quantity<D> gradient;
	public Quantity<D> gradient_errors;
	public Quantity<V> intercept;
	public Quantity<V> intercept_errors;
	public Quantity<D> getGradient() {
		return gradient;
	}
	public void setGradient(Quantity<D> gradient) {
		this.gradient = gradient;
	}
	public Quantity<D> getGradientErrors() {
		return gradient_errors;
	}
	public void setGradientErrors(
			Quantity<D> gradient_errors) {
		this.gradient_errors = gradient_errors;
	}
	public Quantity<V> getIntercept() {
		return intercept;
	}
	public void setIntercept(Quantity<V> intercept) {
		this.intercept = intercept;
	}
	public Quantity<V> getInterceptErrors() {
		return intercept_errors;
	}
	public void setInterceptErrors(Quantity<V> intercept_errors) {
		this.intercept_errors = intercept_errors;
	}

}