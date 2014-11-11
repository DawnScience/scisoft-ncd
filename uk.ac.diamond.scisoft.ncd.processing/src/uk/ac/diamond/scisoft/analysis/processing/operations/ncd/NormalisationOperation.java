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
import org.eclipse.dawnsci.analysis.api.dataset.Slice;
import org.eclipse.dawnsci.analysis.api.metadata.OriginMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.ncd.core.Normalisation;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

public class NormalisationOperation extends AbstractOperation<NormalisationModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.Normalisation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ONE;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}
	
	@Override
	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		Normalisation norm = new Normalisation();
		norm.setCalibChannel(model.getCalibChannel());
		double normValue = model.getNormValue();
		if (model.isUseThisThickness() && model.getThickness() != 0) {
			normValue /= model.getThickness();
		}
		else if (!model.getOriginalDataFilePath().isEmpty()){
			//use value from dataset if > 0
			double thickness;
			try {
				thickness = LoaderFactory.getDataSet(model.getOriginalDataFilePath(), "/entry1/sample/thickness", null).getDouble();
			} catch (Exception e) {
				throw new OperationException(this, e);
			}
			if (thickness > 0) {
				normValue /= thickness;
			}
		}

		norm.setNormvalue(normValue);
		Dataset errors = (Dataset) slice.getError();
		Dataset data = (Dataset) slice.getSlice();
		
		IDataset calibration;
		try {
			calibration = LoaderFactory.getDataSet(model.getFilePath(), model.getCalibDataPath(), null);
			if (calibration == null) {
				throw new Exception("Dataset not found: " + model.getCalibDataPath());
			}
		} catch (Exception e) {
			throw new OperationException(this, e);
		}
		OriginMetadata origin = getOriginMetadata(slice);
		Slice[] newInitialSlice = getSmallerSlice(origin.getInitialSlice());
		Slice[] newCurrentSlice = getSmallerSlice(origin.getCurrentSlice());
		Dataset calibrationSlice = (Dataset) calibration.getSliceView(newInitialSlice).getSlice(newCurrentSlice);
		
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
		toReturn.setData(myres);
		return toReturn;
		
	}
	
	private Slice[] getSmallerSlice(Slice[] modifiedSlice) {
		Slice[] newSlice = new Slice[modifiedSlice.length-1];
		int index = 0;
		for (Slice slice: modifiedSlice) {
			newSlice[index++] = slice;
			if (index == modifiedSlice.length - 1) {
				break;
			}
		}
		return newSlice;
	}
}