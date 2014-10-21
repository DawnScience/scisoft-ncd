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

public class NormalisationModel extends AbstractOperationModel {
	@OperationModelField(hint="Normalisation scaling value",label = "Normalisation value" )
	private double normValue;

	@OperationModelField(hint="Calibration channel location in scan data",label = "Calibration channel number" )
	private int calibChannel;
	
	public double getNormValue() {
		return normValue;
	}

	public void setNormValue(double normValue) {
		this.normValue = normValue;
	}

	public int getCalibChannel() {
		return calibChannel;
	}

	public void setCalibChannel(int calibChannel) {
		this.calibChannel = calibChannel;
	}


}