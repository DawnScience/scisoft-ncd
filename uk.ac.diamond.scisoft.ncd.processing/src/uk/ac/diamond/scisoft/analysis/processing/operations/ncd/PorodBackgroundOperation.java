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
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.DatasetException;
import org.eclipse.january.IMonitor;
import org.eclipse.january.MetadataException;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.Maths;
import org.eclipse.january.metadata.AxesMetadata;
import org.eclipse.january.metadata.MetadataFactory;

import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;
import uk.ac.diamond.scisoft.ncd.processing.TParameterMetadata;

/**
 * An Operation to determine the Porod constant of a SAXS trace as a function of q.
 * <p>
 * The determined results are output to stderr, and the output data is I*, I-B 
 * @author Timothy Spain, timothy.spain@diamond.ac.uk
 *
 */
@PlotAdditionalData(onInput = false, dataName = "Porod fit")
public class PorodBackgroundOperation extends
		AbstractOperation<PorodBackgroundModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.ncd.processing.PorodBackgroundOperation";
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
		NcdOperationUtils.PorodParameters params;
		try {
			params = NcdOperationUtils.fitPorodConstant(dataInput);
		} catch (DatasetException e) {
			throw new OperationException(this, e);
		}
		Dataset fitQ = DatasetFactory.createRange(DoubleDataset.class, params.qMin, params.qMax, (params.qMax-params.qMin)/20);
		Dataset porodCurve = Maths.add(params.gradient, Maths.divide(params.porodConstant, Maths.square(Maths.square(fitQ))));
		AxesMetadata porodAxes;
		try {
			porodAxes = MetadataFactory.createMetadata(AxesMetadata.class, 1);
		} catch (MetadataException e) {
			throw new OperationException(this, e);
		}
		porodAxes.addAxis(0, fitQ);
		porodCurve.addMetadata(porodAxes);
		porodCurve.setName("Porod fit");
		
		System.err.println("Porod data: region = [" + params.qMin + ", " + params.qMax + "], B = " + params.gradient + ", constant = " + params.porodConstant);
		
		TParameterMetadata tparam = input.getFirstMetadata(TParameterMetadata.class);
		if (tparam == null)
			tparam = new TParameterMetadata();
		
		tparam.setqPorodMin(params.qMin);
		tparam.setPorodConstant(params.porodConstant);
		
		Dataset baselined = dataInput.copy(DoubleDataset.class);
		if (model.isSubtractBackground()) {
			baselined.isubtract(params.gradient);
			porodCurve.isubtract(params.gradient);
		}
		
		baselined.addMetadata(tparam);
		
		return new OperationData(baselined, porodCurve);
	}

}
