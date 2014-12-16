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

public class NcdAveragingModel extends AbstractOperationModel {
	@OperationModelField(hint="Use Rg-based filtering to remove bad frames before averaging", label = "Use Rg filtering" )
	private boolean useFiltering = false;

	public boolean isUseFiltering() {
		return useFiltering;
	}

	public void setUseFiltering(boolean useFiltering) {
		this.useFiltering = useFiltering;
	}

}
