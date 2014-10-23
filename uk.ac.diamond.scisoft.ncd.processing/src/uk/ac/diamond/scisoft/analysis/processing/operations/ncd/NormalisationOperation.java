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
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.dataset.Slice;
import org.eclipse.dawnsci.analysis.api.metadata.IMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;

import uk.ac.diamond.scisoft.ncd.core.Normalisation;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;

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
		return OperationRank.SAME;
	}
	
	@Override
	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		Normalisation norm = new Normalisation();
		norm.setCalibChannel(model.getCalibChannel());
		norm.setNormvalue(model.getNormValue());
		ILazyDataset errors = slice.getError();
		ILazyDataset data = slice.getSlice(new Slice());
		IMetadata metadata = slice.getMetadata();
		
		double[] buffer = new double[data.getSize()];
		IDataset dataslice = data.getSlice(new Slice());
		for (int i=0; i < data.getSize(); ++i) {
			double value = dataslice.getDouble(i);
			buffer[i] = value;
		}
		double[] errorBuffer = new double[errors.getSize()];
		IDataset errorSet = errors.getSlice(new Slice());
		for (int i=0; i< errors.getSize(); ++i) {
			double value = errorSet.getDouble(i);
			errorBuffer[i] = value;
		}
		
		//cbuffer - best to get from original data?
		int[] cbuffer = new int[9];
		int[] cdimensions = new int[]{1,1,9};
		
		// now set up normalization
		//check dimension
		Object[] normData = norm.process(NcdDataUtils.flattenGridData((Dataset) data, 1), NcdDataUtils.flattenGridData((Dataset) errors, 1), cbuffer, slice.getSize(), slice.getShape(), cdimensions);
		OperationData toReturn = new OperationData();
		float[] mydata = (float[]) normData[0];
		double[] myerrors = (double[]) normData[1];

		Dataset myres = new FloatDataset(mydata, slice.getShape());
		myres.setErrorBuffer(new DoubleDataset(myerrors, slice.getShape()));
		toReturn.setData(myres);
		return toReturn;
		
	}
}