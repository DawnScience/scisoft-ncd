/*
 * Copyright (c) 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.dawnsci.conversion.converters.CustomNCDConverter.SAS_FORMAT;
import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.FileType;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;

public class ExportNcd1dModel extends AbstractOperationModel {
	@OperationModelField(hint="Enter the path to output directory", file = FileType.EXISTING_FOLDER, label = "Select Output Directory:")
	private String outputDirectoryPath = "";
	public String getOutputDirectoryPath() {
		return outputDirectoryPath;
	}
	public void setOutputDirectoryPath(String outputDirectoryPath) {
		this.outputDirectoryPath = outputDirectoryPath;
	}

	@OperationModelField(hint="Select the export type", label="Export Format")
	private SAS_FORMAT exportFormat = SAS_FORMAT.ASCII;
	
	public SAS_FORMAT getExportFormat() {
		return exportFormat;
	}
	public void setExportFormat(SAS_FORMAT exportFormat) {
		this.exportFormat = exportFormat;
	}

}
