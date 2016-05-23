/*-
 * Copyright 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.io.Serializable;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.Slice;
import org.eclipse.dawnsci.analysis.api.metadata.AxesMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.processing.model.EmptyModel;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;

import uk.ac.diamond.scisoft.ncd.processing.TParameterMetadata;

public class TParameterOperation extends
		AbstractOperation<EmptyModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.ncd.TParameterOperation";
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
	protected OperationData process(IDataset input, IMonitor monitor)
			throws OperationException {

		Dataset dataInput = DatasetUtils.convertToDataset(input);
		
		TParameterMetadata tP;
		// I need TP
		tP = input.getFirstMetadata(TParameterMetadata.class);
		if (tP == null)
			throw new OperationException(this, "T parameter metadata not found. Please run the Porod Background and Kratky linearisation Operations.");
		// Validate the values in the metadata
		if (tP.getqPorodMin() < tP.getqKratkyMin())
			throw new OperationException(this, "Kratky (low q) region overlaps Porod (high q) region. q_k = " + tP.getqKratkyMin() + ", q_p = " + tP.getqPorodMin());
		if (tP.getqKratkyMin() < 0.0)
			throw new OperationException(this, "Minimum momentum transfer has a negative value.");

		// High-q and low-q fitted integrals 
		double jPorod = tP.getPorodConstant() / tP.getqPorodMin();
		double jKratky = tP.getKratkyIntegral();
		// Experimental integral
		double jExp;
		Dataset q = DatasetUtils.convertToDataset(input.getFirstMetadata(AxesMetadata.class).getAxis(0)[0].getSlice());
		
		// First index greater than the limit
		int iPorod = DatasetUtils.findIndexGreaterThan(q, tP.getqPorodMin());
		// Last index less than the limit
		int iKratky = DatasetUtils.findIndexGreaterThan(q, tP.getqKratkyMin());

		Slice expSlice = new Slice(iKratky, iPorod);
		Dataset dataSlice = DatasetUtils.convertToDataset(input.getSlice(expSlice));
		Dataset qSlice = q.getSlice(expSlice);

		
		// Integration
		// Is rectangle rule good for you?
		Dataset integrand = Maths.multiply(Maths.square(qSlice), dataSlice);
		Dataset indices = DoubleDataset.createRange(0.0, (double) iPorod - iKratky, 1.0);
		Dataset dq = Maths.derivative(indices, qSlice, 1);
		jExp = (double) Maths.multiply(integrand, dq).sum();
		// Add any bits between the pieces of the integral
		jExp = (q.getDouble(iKratky) - tP.getqKratkyMin()) * dataInput.getDouble(iKratky) + 
				jExp + 
				(q.getDouble(iPorod) - tP.getqKratkyMin()) * ((iKratky != dataInput.getSize() - 1) ? dataInput.getDouble(iKratky+1) : dataInput.getDouble(iKratky));
		double j = jKratky + jExp + jPorod;
		
		double t = 4/(Math.PI * tP.getPorodConstant()) * j;
		System.out.println("T = " + t);

		DoubleDataset tset = new DoubleDataset(new double[] {t}, new int[] {1});
		tset.setName("Crystallite thickness");
		tset.squeeze();
		
		// Sanitize the input of the calculation metadata
		input.clearMetadata(TParameterMetadata.class);
		return new OperationData(input, new Serializable[] {tset});
	}

}
