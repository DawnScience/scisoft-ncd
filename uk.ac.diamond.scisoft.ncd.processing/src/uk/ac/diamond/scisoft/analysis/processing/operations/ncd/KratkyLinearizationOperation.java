/*-
 * Copyright 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.processing.PlotAdditionalData;
import org.eclipse.dawnsci.analysis.api.processing.model.EmptyModel;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetException;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.Maths;
import org.eclipse.january.metadata.internal.AxesMetadataImpl;

import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;
import uk.ac.diamond.scisoft.ncd.processing.TParameterMetadata;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils.KratkyParameters;

@PlotAdditionalData(onInput = false, dataName = "Kratky fit")
public class KratkyLinearizationOperation extends
		AbstractOperation<EmptyModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.ncd.processing.KratkyLinearizationOperation";
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
		// Get the Kratky parameters; limit of the fit, gradient and intercept
		KratkyParameters kPax;
		try {
			kPax = NcdOperationUtils.fitKratkyLine(DatasetUtils.convertToDataset(input));
		} catch (DatasetException e) {
			throw new OperationException(this, e);
		}
		System.err.println("Kratky parameters: qmin = " + kPax.qMin + ", gradient = " + kPax.gradient + ", intercept = " + kPax.intercept);

		// Create an additional output dataset of the linear fit
		Dataset fitQ = DatasetFactory.createRange(DoubleDataset.class, Double.MIN_NORMAL, kPax.qMin, kPax.qMin/20);
		Dataset kratkyCurve = Maths.divide(Maths.add(Maths.multiply(kPax.gradient, fitQ), kPax.intercept), Maths.square(fitQ));
		AxesMetadataImpl kratkyAxes = new AxesMetadataImpl(1);
		kratkyAxes.addAxis(0, fitQ);
		kratkyCurve.addMetadata(kratkyAxes);
		kratkyCurve.setName("Kratky fit");

		TParameterMetadata tparam = input.getFirstMetadata(TParameterMetadata.class);
		if (tparam == null)
			tparam = new TParameterMetadata();

			
		tparam.setqKratkyMin(kPax.qMin);
		tparam.setKratkyIntegral(kPax.qMin * (kPax.intercept + kPax.gradient * kPax.qMin));
		
		// Don't add metadata to a Dataset that already has it
		if (input.getFirstMetadata(TParameterMetadata.class) == null)
			input.addMetadata(tparam);
			
		
		return new OperationData(input, kratkyCurve);
	}

}
