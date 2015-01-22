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
	@OperationModelField(min=0,hint="Absolute scaling value",max=1e10,label = "Absolute scale value" )
	private double absScale = 1;

	@OperationModelField(hint="Calibration channel location in scan data",label = "Calibration channel number" )
	private int calibChannel;
	
	@OperationModelField(dataset="filePath", hint="Location of calibration data in Nexus file", label = "Calibration data location" )
	private String calibDataPath = "";

	@OperationModelField(hint="If true, calibration data will be extracted from the default location, /entry1/It/data. If false, data will be from the calibration data location defined here", label = "Calibration data path is default path (It)")
	private boolean useDefaultPathForCalibration = true;

	@OperationModelField(hint="File containing calibration data",file = FileType.EXISTING_FILE, label = "Calibration file" )
	private String filePath = "";

	@OperationModelField(hint="If true, calibration data will be extracted from the current data file. If false, data will be from the calibration file defined here", label = "Calibration file is current data file")
	private boolean useCurrentDataForCalibration = true;

	@OperationModelField(min=0, hint="Thickness (mm)", label = "Thickness of sample" )
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

	public boolean isUseDefaultPathForCalibration() {
		return useDefaultPathForCalibration;
	}

	public void setUseDefaultPathForCalibration(boolean useDefaultPathForCalibration) {
		this.useDefaultPathForCalibration = useDefaultPathForCalibration;
	}

	public boolean isUseCurrentDataForCalibration() {
		return useCurrentDataForCalibration;
	}

	public void setUseCurrentDataForCalibration(boolean useCurrentDataForCalibration) {
		this.useCurrentDataForCalibration = useCurrentDataForCalibration;
	}

	public double getAbsScale() {
		return absScale;
	}

	public void setAbsScale(double absScale) {
		this.absScale = absScale;
	}

	public int getCalibChannel() {
		return calibChannel;
	}

	public void setCalibChannel(int calibChannel) {
		this.calibChannel = calibChannel;
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