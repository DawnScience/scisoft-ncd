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
import org.eclipse.dawnsci.analysis.api.metadata.OriginMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.ncd.core.BackgroundSubtraction;

/**
 * Run the NCD background subtraction code.
 * @author rbv51579
 *
 */
public abstract class AbstractNcdBackgroundSubtractionOperation<T extends NcdBackgroundSubtractionModel> extends AbstractOperation<NcdBackgroundSubtractionModel, OperationData> {
	
	public IDataset background;
	
	public abstract String getDataPath();
	
	@Override
	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		
		try {
			background = LoaderFactory.getDataSet(model.getFilePath(), getDataPath(), null);
			Dataset backgroundErrors = ((Dataset) background).getErrorBuffer();
			if (backgroundErrors == null) {
				backgroundErrors = (Dataset) background.getSlice();
			}
			((Dataset)background).setErrorBuffer(backgroundErrors);
		} catch (Exception e1) {
			throw new OperationException(this, e1);
		}
		
		if (model.getBgScale() != 0 && model.getBgScale() != Double.NaN) {
			double bgScaling = model.getBgScale();
			((FloatDataset)background).imultiply(bgScaling);
			DoubleDataset bgErrors = (DoubleDataset) ((Dataset) background).getErrorBuffer();
			bgErrors.imultiply(bgScaling * bgScaling);
			((Dataset)background).setErrorBuffer(bgErrors);
		}
		
		BackgroundSubtraction bgSubtraction = new BackgroundSubtraction();
		//compare data and BG sizes, if same size, find the correct background slice to pair with the data
		try {
			OriginMetadata origin = getOriginMetadata(slice);
			ILazyDataset originParent = origin.getParent();
			if (!(Arrays.equals(originParent.getShape(), background.getShape()))) {
				throw new Exception("Data and background shapes must match");
			}
			Dataset bgSlice = (Dataset)background.getSliceView(origin.getInitialSlice()).getSlice(origin.getCurrentSlice());
			bgSubtraction.setBackground(bgSlice);
		} catch (Exception e) {
			throw new OperationException(this, e);
		}

		Dataset error = (Dataset) slice.getError();
		if (error == null) {
			error = (Dataset) slice.getSlice();
		}
		Dataset data = (Dataset)slice.getSlice();
		data.setError(error);

		Object[] bgDataAndError = bgSubtraction.process(data.cast(Dataset.FLOAT32).getBuffer(), error.cast(Dataset.FLOAT64).getBuffer(), data.getShape());
		float[] bgData = (float[])bgDataAndError[0];
		double[] bgError = (double[])bgDataAndError[1];
		Dataset bgDataset = new FloatDataset(bgData, data.getShape());
		Dataset bgErrorDataset = new DoubleDataset(bgError, data.getShape());
		bgDataset.setError(bgErrorDataset);

		OperationData toReturn = new OperationData();
		copyMetadata(slice, bgDataset);
		toReturn.setData(bgDataset);
		return toReturn;
	}

}
