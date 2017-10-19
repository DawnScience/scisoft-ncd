/*
 * Copyright (c) 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.AggregateDataset;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.Slice;

import uk.ac.diamond.scisoft.ncd.core.BackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

/**
 * Run the NCD background subtraction using the current data file for background data.
 * @author rbv51579
 *
 */
public class NcdBackgroundSubtractionFromDataOperation extends AbstractOperation<NcdBackgroundSubtractionFromDataModel, OperationData> {
	
	public ILazyDataset background;
	
	public ILazyDataset backgroundToProcess;
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.NcdBackgroundSubtractionFromData";
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
		String fileToRead = getSliceSeriesMetadata(slice).getSourceInfo().getFilePath();

		try {
			background = NcdOperationUtils.getDataset(this, fileToRead, new ArrayList<String>(){{add(getDataPath(slice));}});
			if (background == null) {
				throw new Exception("No background dataset found");
			}
			
			backgroundToProcess = getImageSelection(slice);
			
		} catch (Exception e1) {
			throw new OperationException(this, e1);
		}
		
		SliceFromSeriesMetadata ssm = getSliceSeriesMetadata(slice);

		Dataset bgSlice;
		try {
			bgSlice = NcdOperationUtils.getBackgroundSlice(ssm, slice, backgroundToProcess);
		} catch (Exception e1) {
			throw new OperationException(this, e1);
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

	/**
	 * Get images based on a user selection written in Irakli's format - note the latter number is inclusive (for 0-10, 11 images are selected)
	 * @param slice
	 * @return
	 * @throws Exception
	 */
	private ILazyDataset getImageSelection(IDataset slice) throws Exception {
		//append ; to fill out the dimensions for image selection
		String selectionString = model.getImageSelectionString();
		int rank = getInputRank().equals(OperationRank.ONE) ? 1 : 2; 
		int toAdd= background.getShape().length - rank - model.getImageSelectionString().split(";").length;
		if (toAdd>0) {
			selectionString = StringUtils.leftPad(selectionString, toAdd + model.getImageSelectionString().length(), ";");
		}
		int[] reshaped = Arrays.copyOf(background.getShape(), background.getShape().length - slice.getShape().length);
		ArrayList<int[]> sliceList= NcdDataUtils.createSliceList(selectionString, reshaped); //only get image slices, not image data
		ArrayList<int[]> combinations = NcdDataUtils.generateCombinations(sliceList);

		return getByCombinations(background.getSliceView(), combinations);
	}

	/**
	 * Create a dataset whose images match the positions listed in combinations.
	 * @param data
	 * @param combinations
	 * @return
	 * @throws Exception
	 */
	private ILazyDataset getByCombinations(ILazyDataset data, ArrayList<int[]> combinations) throws Exception {
		ILazyDataset[] toReturn = new ILazyDataset[combinations.size()];
		int i=0;
		for (int[] combo : combinations) {
			Slice[] sliceList = new Slice[combo.length];
			for (int i1=0; i1<combo.length; ++i1){
				sliceList[i1] = new Slice(combo[i1], combo[i1]+1);
			}
			toReturn[i++] = data.getSliceView(sliceList);
		}
		AggregateDataset agg = new AggregateDataset(false, toReturn);
		return agg;
	}
}
