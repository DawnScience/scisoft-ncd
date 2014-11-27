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
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.metadata.OriginMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.slice.SliceFromSeriesMetadata;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IntegerDataset;

import uk.ac.diamond.scisoft.ncd.core.BackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

/**
 * Run the NCD background subtraction code.
 * @author rbv51579
 *
 */
public abstract class AbstractNcdBackgroundSubtractionOperation<T extends NcdBackgroundSubtractionModel> extends AbstractOperation<NcdBackgroundSubtractionModel, OperationData> {
	
	public ILazyDataset background;
	
	public IDataset backgroundToProcess;
	
	public abstract String getDataPath();
	
	@SuppressWarnings("serial")
	@Override
	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		String fileToRead = "";
		if (model.isUseCurrentFileForBackground()) {
			fileToRead = getSliceSeriesMetadata(slice).getSourceInfo().getFilePath();
		}
		else {
			fileToRead = model.getFilePath();
		}
		try {
			background = NcdOperationUtils.getDataset(fileToRead, new ArrayList<String>(){{add(getDataPath());}});
			if (background == null) {
				throw new Exception("No background dataset found");
			}
			
			//append ; to fill out the dimensions for image selection
			String selectionString = model.getImageSelectionString();
			int toAdd= background.getShape().length - 1 - model.getImageSelectionString().split(";").length;
			if (toAdd>0) {
				selectionString = StringUtils.leftPad(selectionString, toAdd + model.getImageSelectionString().length(), ";");
			}
			List<int[]> sliceList= NcdDataUtils.createSliceList(selectionString, background.getShape());
			List<IntegerDataset> sliceDatasetList = new ArrayList<IntegerDataset>();
			for (int[] slice1 : sliceList) {
				sliceDatasetList.add(new IntegerDataset(slice1, slice1.length));
			}
			Object[] sets = new ILazyDataset[sliceDatasetList.size()];
			int index = 0;
			for (IntegerDataset sliceDataset : sliceDatasetList) {
				if (sliceDataset.getSize() != background.getShape()[index]) {
					sets[index++] = sliceDataset;
				}
				else {
					index++;
				}
			}
			backgroundToProcess = ((Dataset)background.getSlice()).getByIndexes(sets);
		} catch (Exception e1) {
			throw new OperationException(this, e1);
		}
		
		//compare data and BG sizes, if same size, find the correct background slice to pair with the data
		Dataset bgSlice;
		try {
			SliceFromSeriesMetadata ssm = getSliceSeriesMetadata(slice);
			ILazyDataset originParent = ssm.getSourceInfo().getParent();
			if (!(Arrays.equals(originParent.getShape(), background.getShape()))) {
				throw new Exception("Data and background shapes must match");
			}
			bgSlice = (Dataset)background.getSliceView(ssm.getSliceInfo().getViewSlice()).getSlice(ssm.getSliceInfo().getCurrentSlice());
			
			Dataset backgroundErrors = bgSlice.getSlice().getErrorBuffer();
			if (backgroundErrors == null) {
				backgroundErrors = bgSlice.getSlice();
			}
			bgSlice.setErrorBuffer(backgroundErrors);

		} catch (Exception e) {
			throw new OperationException(this, e);
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
