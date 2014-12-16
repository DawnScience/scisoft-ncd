/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;

public class NcdInvariantModel extends AbstractOperationModel {

	@OperationModelField(hint="Calculate SAXS invariant - q axis must be available\nIf false, a general invariant will be calculated.", label = "Calculate SAXS Invariant" )
	private boolean calculateSaxsInvariant = true;

	public boolean isCalculateSaxsInvariant() {
		return calculateSaxsInvariant;
	}

	public void setCalculateSaxsInvariant(boolean calculateSaxsInvariant) {
		this.calculateSaxsInvariant = calculateSaxsInvariant;
	}

}
