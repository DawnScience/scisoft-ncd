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

import javax.measure.quantity.Dimensionless;

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.processing.model.EmptyModel;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

public class GuinierOperation extends AbstractOperation<EmptyModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.Guinier";
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
		Object[] params;
		try {
			params = NcdOperationUtils.getGuinierPlotParameters(slice);
		} catch (Exception e) {
			throw new OperationException(this, "Guinier calculation threw an exception" + e);
		}

		@SuppressWarnings("unchecked")
		Dataset i0 = DatasetFactory.createFromObject(new double[]{((Amount<Dimensionless>)params[0]).getEstimatedValue()});
		i0.setName("I0");
		i0.squeeze();
		@SuppressWarnings("unchecked")
		Dataset rG = DatasetFactory.createFromObject(new double[]{((Amount<Dimensionless>)params[1]).getEstimatedValue()});
		rG.setName("Rg");
		rG.squeeze();
		Dataset rGLow = DatasetFactory.createFromObject(new double[]{(double) params[2]}, new int[]{1});
		rGLow.setName("RgLow");
		rGLow.squeeze();
		Dataset rGUpper = DatasetFactory.createFromObject(new double[]{(double) params[3]}, new int[]{1});
		rGUpper.setName("RgUpper");
		rGUpper.squeeze();
		return new OperationData(slice, new Serializable[]{i0, rG, rGLow, rGUpper});
	}

}
