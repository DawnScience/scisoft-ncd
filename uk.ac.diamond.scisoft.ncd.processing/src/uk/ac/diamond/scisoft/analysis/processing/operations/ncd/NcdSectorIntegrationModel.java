/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.FileType;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;

import uk.ac.diamond.scisoft.analysis.processing.operations.IntegrationModel;

public class NcdSectorIntegrationModel extends IntegrationModel {

	public enum IntegrationOperationName {
		azimuthal, radial;
	}

	@OperationModelField(hint="The path to the a NeXus file containing a ROI.\nYou can click and drag a file into this field.", file = FileType.EXISTING_FILE, label = "Region of Interest File")
	private String filePath = "";

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		firePropertyChange("filePath", this.filePath, this.filePath = filePath);
	}
	
	@OperationModelField(hint="The path to XML file containing the calibration data.", file = FileType.EXISTING_FILE, label = "Calibration XML File")	
	private String calibrationPath = "";

	public String getCalibrationPath() {
		return calibrationPath;
	}

	public void setCalibrationPath(String calibrationPath) {
		firePropertyChange("calibrationPath", this.calibrationPath, this.calibrationPath = calibrationPath);
	}

	@OperationModelField(label="Integration Type")
	private IntegrationOperationName azimuthalOrRadialIntegration = IntegrationOperationName.azimuthal;

	public IntegrationOperationName getAzimuthalOrRadialIntegration() {
		return azimuthalOrRadialIntegration;
	}

	public void setAzimuthalOrRadialIntegration(
			IntegrationOperationName azimuthalOrRadialIntegration) {
		this.azimuthalOrRadialIntegration = azimuthalOrRadialIntegration;
	}
}
