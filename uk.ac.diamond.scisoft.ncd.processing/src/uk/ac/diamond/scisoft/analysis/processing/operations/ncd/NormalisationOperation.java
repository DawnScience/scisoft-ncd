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

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.ncd.core.Normalisation;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

public class NormalisationOperation<T extends NormalisationModel> extends AbstractOperation<NormalisationModel, OperationData> {

	private final static Logger logger = LoggerFactory.getLogger(NormalisationOperation.class);

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
	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		Normalisation norm = new Normalisation();
		norm.setCalibChannel(model.getCalibChannel());
		double absScale = model.getAbsScale();
		if (model.isUseThisThickness()) {
			if (model.getThickness() > 0) {
				absScale /= model.getThickness();
			}
			else if (model.getThickness() == 0.0) {
				logger.info("The sample thickness cannot be 0 - will be ignored");
			}
			else { //should not be negative or NaN
				logger.info("Unexpected value for sample thickness - " + model.getThickness() + " so it will be ignored");
			}
		}
		else {
			//use value from dataset if > 0
			double thickness;
			try {

				String dataFile = getSliceSeriesMetadata(slice).getSourceInfo().getFilePath();
				IDataset thicknessDataset = LoaderFactory.getDataSet(dataFile, "/entry1/sample/thickness", null);
				if (thicknessDataset == null) {
					throw new IllegalArgumentException("File does not contain the required thickness information");
				}
				thickness = thicknessDataset.getDouble();
			} catch (Exception e) {
				throw new OperationException(this, e);
			}
			if (thickness > 0) {
				absScale /= thickness;
			}
			else if (Double.isNaN(thickness)) {
				logger.info("Sample thickness has a value of NaN (it may not have been set during data collection) - it will be ignored");
			}
			else {
				logger.info("Sample thickness is not a positive value - it will be ignored");
			}
		}

		norm.setNormvalue(absScale);
		Dataset errors = (Dataset) slice.getError();
		Dataset data = (Dataset) slice.getSliceView();
		
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
			calibDataPath = "/entry1/It/data";
		}
		else {
			if (model.getCalibDataPath() != null || model.getCalibDataPath().isEmpty()) {
				calibDataPath = model.getCalibDataPath();
			}
			else {
				throw new IllegalArgumentException("Calibration default path not used, but no data path defined");
			}
		}
		try {
			calibration = LoaderFactory.getDataSet(calibDataFile, calibDataPath, null);
			if (calibration == null) {
				throw new Exception("Dataset not found: " + calibDataPath);
			}
		} catch (Exception e) {
			throw new OperationException(this, e);
		}
		SliceFromSeriesMetadata ssm = getSliceSeriesMetadata(slice);
		Dataset calibrationSlice = (Dataset) calibration.getSlice(ssm.getSliceInfo().getInputSliceWithoutDataDimensions().convertToSlice());
		
		if (errors == null) {
			errors = DatasetUtils.cast(data.clone(), Dataset.FLOAT64);
		}
		// now set up normalization
		//check dimension
		data.resize(NcdOperationUtils.addDimension(data.getShape()));
		Object[] normData = norm.process(data.getBuffer(), errors.getBuffer(),
				calibrationSlice.getBuffer(), 1, data.getShape(), calibrationSlice.getShape());
		OperationData toReturn = new OperationData();
		float[] mydata = (float[]) normData[0];
		double[] myerrors = (double[]) normData[1];

		Dataset myres = new FloatDataset(mydata, slice.getShape());
		myres.setError(new DoubleDataset(myerrors, slice.getShape()));
		copyMetadata(slice, myres);
		toReturn.setData(myres);
		return toReturn;
		
	}
}
