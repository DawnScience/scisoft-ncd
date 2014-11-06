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

public class NormalisationModel extends AbstractOperationModel {
	@OperationModelField(hint="Normalisation scaling value",max=1e10,label = "Normalisation value" )
	private double normValue = 1;

	@OperationModelField(hint="Calibration channel location in scan data",label = "Calibration channel number" )
	private int calibChannel;
	
	@OperationModelField(hint="Location of calibration data in Nexus file", label = "Calibration data location" )
	private String calibDataPath = "";

	@OperationModelField(hint="File containing calibration data",file = FileType.EXISTING_FILE, label = "Calibration file" )
	private String filePath = "";

	@OperationModelField(hint="File containing thickness data (original data file)",file = FileType.EXISTING_FILE, label = "Thickness file" )
	private String originalDataFilePath = "";

	@OperationModelField(hint="Thickness (mm)", label = "Thickness of sample" )
	private double thickness = 0;

	@OperationModelField(hint="Use Thickness Value in This Form", label = "Use This Thickness" )
	private boolean useThisThickness = false;

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		firePropertyChange("filePath", this.filePath, this.filePath = filePath);
	}

	public String getCalibDataPath() {
		return calibDataPath;
	}

	public void setCalibDataPath(String calibDataPath) {
		firePropertyChange("calibDataPath", this.calibDataPath, this.calibDataPath = calibDataPath);
	}

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

	public void setOriginalDataFilePath(String originalDataFilePath) {
		this.originalDataFilePath = originalDataFilePath;
	}

	public String getOriginalDataFilePath() {
		return originalDataFilePath;
	}

	public double getThickness() {
		return thickness;
	}

	public void setThickness(double thickness) {
		this.thickness = thickness;
	}

	public boolean isUseThisThickness() {
		return useThisThickness;
	}

	public void setUseThisThickness(boolean useThisThickness) {
		this.useThisThickness = useThisThickness;
	}

}