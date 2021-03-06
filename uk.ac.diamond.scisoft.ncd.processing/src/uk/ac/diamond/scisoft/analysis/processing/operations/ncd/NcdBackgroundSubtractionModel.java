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
import org.eclipse.dawnsci.analysis.api.processing.model.FileType;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;

public class NcdBackgroundSubtractionModel extends AbstractOperationModel {

	@OperationModelField(hint="File containing background data",file = FileType.EXISTING_FILE, label = "Background file" )
	private String filePath = "";

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		firePropertyChange("filePath", this.filePath, this.filePath = filePath);
	}

	@OperationModelField(hint="Value to scale the background data", label = "Background Scale" )
	private double bgScale = 1.0;

	public double getBgScale() {
		return bgScale;
	}

	public void setBgScale(double bgScale) {
		this.bgScale = bgScale;
	}
}
