/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.processing.operations.utils.ProcessingUtils;
import uk.ac.diamond.scisoft.ncd.core.Normalisation;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

public class NormalisationOperation<T extends NormalisationModel> extends AbstractOperation<NormalisationModel, OperationData> {

	public static final String ENTRY1_SAMPLE_THICKNESS = "/entry1/sample/thickness";
	public static final String ENTRY1_IT_DATA = "/entry1/It/data";
	public static final String ENTRY1_DETECTOR_SCALING_FACTOR = "/entry1/detector/scaling_factor";
	private final static Logger logger = LoggerFactory.getLogger(NormalisationOperation.class);

	Double thickness = null;
	Double absScale = null;
	boolean userWarned = false;
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.Normalisation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ANY;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.SAME;
	}
	
	@Override
	public void init() {
		if (!model.isThicknessFromFileIsDefault()) {
			if (model.getThickness() > 0) {
				thickness = model.getThickness();
			}
			else if (model.getThickness() == 0.0) {
				logger.info("The sample thickness cannot be 0 - will be ignored");
			}
			else { //should not be negative or NaN
				logger.info("Unexpected value for sample thickness - " + model.getThickness() + " so it will be ignored");
			}
		}
		
		if (!model.isUseScaleValueFromOriginalFile()) {
			absScale = model.getAbsScale();
		}
	}
	@Override
	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		Normalisation norm = new Normalisation();
		norm.setCalibChannel(model.getCalibChannel());
		if (model.isUseScaleValueFromOriginalFile()) {
			absScale = getAbsScale(slice);
		}
		if (absScale.isNaN() || absScale <= 0) {
			throw new OperationException(this, "Absolute scale value must be a number and positive");
		}
		
		if (model.isThicknessFromFileIsDefault()) {
			//use value from dataset if > 0
			String dataFile = getSliceSeriesMetadata(slice).getSourceInfo().getFilePath();
			IDataset thicknessDataset = ProcessingUtils.getLazyDataset(this, dataFile, ENTRY1_SAMPLE_THICKNESS).getSlice();
			
			thickness = thicknessDataset.getDouble();
			
			if (Double.isNaN(thickness)) {
				if (userWarned == false) {
					logger.info("Sample thickness has a value of NaN (it may not have been set during data collection) - it will be ignored");
				}
			}
			else if (thickness <= 0) {
				if (userWarned == false) {
					logger.info("Sample thickness is not a positive value - it will be ignored");
				}
			}
		}

		if (thickness == null || thickness.isNaN() || thickness <= 0) {
			if (userWarned == false) {
				logger.error("Thickness is invalid, skipping absolute scaling calculation");
				userWarned = true;
			}
		}
		else {
			norm.setNormvalue(absScale / thickness);
		}
		Dataset s = DatasetUtils.convertToDataset(slice);
		Dataset errors = NcdOperationUtils.getErrorBuffer(s);
		Dataset data = s.getSliceView();
		
		IDataset calibration;
		String calibDataFile;
		if (model.isUseCurrentDataForCalibration()) {
			calibDataFile = getSliceSeriesMetadata(slice).getSourceInfo().getFilePath();
		}
		else {
			calibDataFile = model.getFilePath();
		}

		if (calibDataFile == null || calibDataFile.isEmpty()) {
			throw new OperationException(this, new Exception("Calibration data file must be defined"));
		}

		String calibDataPath;
		if (model.isUseDefaultPathForCalibration()) {
			calibDataPath = ENTRY1_IT_DATA;
		}
		else {
			if (model.getCalibDataPath() != null || model.getCalibDataPath().isEmpty()) {
				calibDataPath = model.getCalibDataPath();
			}
			else {
				throw new IllegalArgumentException("Calibration default path not used, but no data path defined");
			}
		}
		calibration = ProcessingUtils.getLazyDataset(this, calibDataFile, calibDataPath).getSlice();
		SliceFromSeriesMetadata ssm = getSliceSeriesMetadata(slice);
		
		Dataset calibrationSlice = DatasetUtils.convertToDataset(ssm.getMatchingSlice(calibration));
		
		if (errors == null) {
			errors = DatasetUtils.cast(data.clone(), Dataset.FLOAT64);
		}
		// now set up normalization
		//check dimension
		data.resize(NcdOperationUtils.addDimension(data.getShape()));
		//then resize calibration data if necessary
		while (data.getRank() > calibrationSlice.getRank()) {
			calibrationSlice.resize(NcdOperationUtils.addDimension(calibrationSlice.getShape()));
		}
		
		Object[] normData = null;
		try {
			normData = norm.process(data.getBuffer(), errors.getBuffer(),
					calibrationSlice.getBuffer(), 1, data.getShape(), calibrationSlice.getShape());
		} catch (Exception e) {
			throw new OperationException(this, "Exception during normalisation - are the correct dataset and channel number being used for normalisation? " + e.getMessage());
		}
		OperationData toReturn = new OperationData();
		float[] mydata = (float[]) normData[0];
		double[] myerrors = (double[]) normData[1];

		Dataset myres = new FloatDataset(mydata, slice.getShape());
		myres.setErrorBuffer(new DoubleDataset(myerrors, slice.getShape()));
		copyMetadata(slice, myres);
		toReturn.setData(myres);
		return toReturn;
		
	}

	private double getAbsScale(IDataset slice) {
		String originalFile = getSliceSeriesMetadata(slice).getSourceInfo().getFilePath();
		IDataset d = ProcessingUtils.getLazyDataset(this, originalFile, ENTRY1_DETECTOR_SCALING_FACTOR).getSlice();
		return d.getDouble();
	}
}
