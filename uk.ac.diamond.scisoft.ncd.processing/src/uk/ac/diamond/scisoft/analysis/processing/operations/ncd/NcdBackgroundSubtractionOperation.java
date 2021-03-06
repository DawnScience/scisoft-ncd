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

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;

import uk.ac.diamond.scisoft.ncd.core.BackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

/**
 * Run the NCD background subtraction code.
 * @author rbv51579
 *
 */
public class NcdBackgroundSubtractionOperation extends AbstractOperation<NcdBackgroundSubtractionModel, OperationData> {
	
	public ILazyDataset background;
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.NcdBackgroundSubtraction";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ANY;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.SAME;
	}
	
	public String getDataPath(IDataset slice) throws Exception {
		if (slice.getRank() == 2) {
			return "/entry1/instrument/detector/data";
		}
		else if (slice.getRank() == 1) {
			return "/entry/result/data";
		}
		throw new Exception("Invalid rank for data path");
	}
	
	@SuppressWarnings("serial")
	@Override
	public OperationData process(final IDataset slice, IMonitor monitor) throws OperationException {
		if (model.getFilePath() == null || model.getFilePath().isEmpty()) {
			throw new OperationException(this, new Exception("Background file not defined"));
		}
		
		String fileToRead = model.getFilePath();

		try {
			background = NcdOperationUtils.getDataset(this, fileToRead, new ArrayList<String>(){{add(getDataPath(slice));}});
			if (background == null) {
				throw new Exception("No background dataset found");
			}
			
		} catch (Exception e1) {
			throw new OperationException(this, e1);
		}
		
		SliceFromSeriesMetadata ssm = getSliceSeriesMetadata(slice);

		Dataset bgSlice;
		try {
			bgSlice = NcdOperationUtils.getBackgroundSlice(ssm, slice, background);
		} catch (Exception e1) {
			throw new OperationException(this, e1);
		}
		
		if (model.getBgScale() != 0 && model.getBgScale() != Double.NaN) {
			double bgScaling = model.getBgScale();
			bgSlice.imultiply(bgScaling);
			DoubleDataset bgErrors = (DoubleDataset) bgSlice.getErrorBuffer();
			bgErrors.imultiply(bgScaling * bgScaling);
			bgSlice.setErrorBuffer(bgErrors);
		}
		
		BackgroundSubtraction bgSubtraction = new BackgroundSubtraction();
		bgSubtraction.setBackground(bgSlice);

		Dataset data = DatasetUtils.convertToDataset(slice);
		Dataset errorBuffer = data.getErrorBuffer();
		if (errorBuffer == null) {
			Dataset error = data.getErrors();
			if (error == null) {
				errorBuffer = data.getSlice();
			}
			else {
				errorBuffer = error.ipower(2);
			}
		}

		Object[] bgDataAndError = bgSubtraction.process(data.cast(Dataset.FLOAT32).getBuffer(), errorBuffer.cast(Dataset.FLOAT64).getBuffer(), data.getShape());
		float[] bgData = (float[])bgDataAndError[0];
		double[] bgError = (double[])bgDataAndError[1];
		Dataset bgDataset = DatasetFactory.createFromObject(bgData, data.getShape());
		Dataset bgErrorDataset = DatasetFactory.createFromObject(bgError, data.getShape());
		bgDataset.setErrorBuffer(bgErrorDataset);

		OperationData toReturn = new OperationData();
		copyMetadata(slice, bgDataset);
		toReturn.setData(bgDataset);
		return toReturn;
	}

}
