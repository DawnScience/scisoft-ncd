/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.ArrayList;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.AbstractDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IntegerDataset;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;

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
			@SuppressWarnings("serial")
			Dataset loadedSet = DatasetUtils.sliceAndConvertLazyDataset(NcdOperationUtils.getDataset(this, model.getFilePath(),
					new ArrayList<String>() {{add("/entry1/instrument/detector/data");}}));
			if (loadedSet == null) {
				throw new Exception("No detector response dataset found");
			}
			response.setResponse(loadedSet.squeeze().getSlice());
		} catch (Exception e) {
			throw new OperationException(this, e);
		}

		IntegerDataset data = (IntegerDataset) slice.getSliceView().squeeze();
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

		Dataset myres = DatasetFactory.createFromObject(mydata, slice.getShape());
		myres.setErrorBuffer(DatasetFactory.createFromObject(myerrors, slice.getShape()));
		copyMetadata(slice, myres);
		toReturn.setData(myres);
		return toReturn;
	}

}
