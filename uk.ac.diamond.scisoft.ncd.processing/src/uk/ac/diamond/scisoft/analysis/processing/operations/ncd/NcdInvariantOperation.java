/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

 package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.metadata.AxesMetadata;

import uk.ac.diamond.scisoft.ncd.core.Invariant;
import uk.ac.diamond.scisoft.ncd.core.SaxsInvariant;
import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.data.plots.PorodPlotData;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

public class NcdInvariantOperation extends AbstractOperation<NcdInvariantModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.Invariant";
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
		Object[] myobj;
		Dataset axis = null;
		
		Dataset data = DatasetUtils.convertToDataset(slice);
		Dataset errors = NcdOperationUtils.getErrorBuffer(data);
		int[] dataShape = NcdOperationUtils.addDimension(data.getShape());
		
		Dataset inputAxis = null;
		try {
			List<AxesMetadata> axes = slice.getMetadata(AxesMetadata.class);
			if (axes != null) {
				inputAxis = DatasetUtils.sliceAndConvertLazyDataset(axes.get(0).getAxes()[0]); //assume q is first axis
			}
		} catch (Exception e) {
			throw new OperationException(this, e);
		}
		
		if (inputAxis == null && model.isCalculateSaxsInvariant()) {
			throw new OperationException(this, new Exception("q axis must be defined to calculate SAXS invariant"));
		}
		else if (inputAxis == null) {
			System.out.println("q axis does not need to be defined for non-SAXS invariant calculation");
		}
		else {
			axis = inputAxis.clone().squeeze();
		}

		if (model.isCalculateSaxsInvariant()) {
			SaxsInvariant inv = new SaxsInvariant();
			myobj = inv.process(data.getBuffer(), errors.getBuffer(), axis.getBuffer(), data.getShape());
		} else {
			Invariant inv = new Invariant();
			myobj = inv.process(data.getBuffer(), errors.getBuffer(), dataShape);
		}
		
		float[] mydata = (float[]) myobj[0];
		double[] myerrors = (double[]) myobj[1];

		Dataset myres = DatasetFactory.createFromObject(mydata, 1);
		myres.setErrorBuffer(DatasetFactory.createFromObject(myerrors, 1));
		myres.setName("Invariant");

		Dataset porodDataset = null;
		if (axis != null) {
			int[] rgDataShape = Arrays.copyOf(dataShape, dataShape.length + 1);
			rgDataShape[rgDataShape.length - 1] = data.getSize();
			
			PorodPlotData plotData = (PorodPlotData) SaxsAnalysisPlotType.POROD_PLOT.getSaxsPlotDataObject();
			SimpleRegression regression = plotData.getPorodPlotParameters(data.squeeze(), axis);
			if (regression != null) {
				porodDataset = plotData.getFitData(axis, regression);
				porodDataset.setName("Porod dataset");
				
			}
		}

		if (porodDataset != null) {
			return new OperationData(slice, new Serializable[]{myres, porodDataset});
		}
		return new OperationData(slice, new Serializable[]{myres});
	}
}
