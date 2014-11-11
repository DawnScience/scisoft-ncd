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
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.AbstractDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IntegerDataset;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.ncd.core.DetectorResponse;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

public class NcdDetectorResponseOperation extends AbstractOperation<NcdDetectorResponseModel, OperationData> {
	
	public IDataset response;

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.NcdDetectorResponse";
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
		DetectorResponse response = new DetectorResponse();
		try {
			IDataset loadedSet = LoaderFactory.getDataSet(model.getFilePath(), "/entry1/instrument/detector/data", null).squeeze();
			response.setResponse((Dataset)loadedSet.getSlice());
		} catch (Exception e) {
			throw new OperationException(this, e);
		}

		IntegerDataset data = (IntegerDataset) slice.squeeze();
		data.resize(NcdOperationUtils.addDimension(data.getShape())); //expand slice to include another dimension - expect data to be n+1 dimensions, response n dimensions

		FloatDataset errors;
		if (slice.getError() != null) {
			errors = (FloatDataset) slice.getError();
		}
		else {
			errors = (FloatDataset) data.cast(AbstractDataset.FLOAT32);
		}
		int[] flatShape = data.getShape();

		Object[] detData = response.process(data.getData(), errors.getData(), flatShape[0], flatShape);
		OperationData toReturn = new OperationData();
		float[] mydata = (float[]) detData[0];
		double[] myerrors = (double[]) detData[1];

		Dataset myres = new FloatDataset(mydata, slice.getShape());
		myres.setErrorBuffer(new DoubleDataset(myerrors, slice.getShape()));
		toReturn.setData(myres);
		return toReturn;
	}

}