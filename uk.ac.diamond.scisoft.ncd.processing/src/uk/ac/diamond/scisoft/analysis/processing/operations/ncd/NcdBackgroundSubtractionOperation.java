/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.Arrays;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.dataset.Slice;
import org.eclipse.dawnsci.analysis.api.metadata.OriginMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.ncd.core.BackgroundSubtraction;

public class NcdBackgroundSubtractionOperation extends AbstractOperation<NcdBackgroundSubtractionModel, OperationData> {
	
	public IDataset background;

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.NcdBackgroundSubtractionOperation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.TWO;
	}
	
	@Override
	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		
		try {
			background = LoaderFactory.getDataSet(model.getFilePath(), "/entry1/instrument/detector/data", null);
			background.squeeze();
			Dataset backgroundErrors = (Dataset) background.getError();
			if (backgroundErrors == null) {
				backgroundErrors = (Dataset) background.getSlice();
			}
			background.setError(backgroundErrors);
		} catch (Exception e1) {
			throw new OperationException(this, e1);
		}
		//compare data and BG sizes, if same size, find the correct background slice to pair with the data
		try {
			OriginMetadata origin = slice.getMetadata(OriginMetadata.class).get(0);
			ILazyDataset originParent = origin.getParent().squeeze();
			if (!(Arrays.equals(originParent.getShape(), background.getShape()))) {
				throw new Exception("Data and background shapes must match");
			}

		} catch (Exception e) {
			throw new OperationException(this, e);
		}

		
		Dataset error = (Dataset) slice.getError();
		if (error == null) {
			error = (Dataset) slice.getSlice();
		}
		Dataset data = (Dataset)slice.getSlice();
		data.setError(error);

		BackgroundSubtraction bgSubtraction = new BackgroundSubtraction();
		bgSubtraction.setBackground((Dataset)background.getSlice(new Slice(0,1)).squeeze());

		Object[] bgDataAndError = bgSubtraction.process(data.cast(Dataset.FLOAT32).getBuffer(), error.cast(Dataset.FLOAT64).getBuffer(), data.getShape());
		float[] bgData = (float[])bgDataAndError[0];
		double[] bgError = (double[])bgDataAndError[1];
		Dataset bgDataset = new FloatDataset(bgData, data.getShape());
		Dataset bgErrorDataset = new DoubleDataset(bgError, data.getShape());
		bgDataset.setError(bgErrorDataset);

		OperationData toReturn = new OperationData();
		toReturn.setData(bgDataset);
		return toReturn;
	}

}