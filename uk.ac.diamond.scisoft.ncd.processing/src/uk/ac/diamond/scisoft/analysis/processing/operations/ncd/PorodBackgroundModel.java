/*-
 * Copyright 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;

public class PorodBackgroundModel extends AbstractOperationModel {

	@OperationModelField(hint = "Subtract the background B parameter from the output data", label = "Subtract background?")
	private boolean subtractBackground = true;
	
	public boolean isSubtractBackground() {
		return subtractBackground;
	}
	
	public void setSubtractBackground(boolean subBak) {
		firePropertyChange("subtractBackground", this.subtractBackground, this.subtractBackground = subBak);
	}
}
