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
import org.eclipse.dawnsci.analysis.api.dataset.Slice;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.slice.SliceFromSeriesMetadata;
import org.eclipse.dawnsci.analysis.dataset.impl.AbstractDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.AggregateDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;

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
	
	public ILazyDataset backgroundToProcess;
	
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
			
			backgroundToProcess = getImageSelection(slice);
			
		} catch (Exception e1) {
			throw new OperationException(this, e1);
		}
		
		Dataset bgSlice;
		try {
			SliceFromSeriesMetadata ssm = getSliceSeriesMetadata(slice);
			
			//if the background image is the same shape as the sliced image, then do simple subtraction on the background
			if (AbstractDataset.squeezeShape(slice.getShape(), false) == AbstractDataset.squeezeShape(backgroundToProcess.getShape(), false)) {
				bgSlice = (Dataset) backgroundToProcess;
			}
			else {
				//if number of images between background and parent dataset are the same, subtract each BG from corresponding data slice
				int backgroundImages = getNumberOfImages(backgroundToProcess, ssm);
				int sampleImages = getNumberOfSliceImages(ssm);
				if (backgroundImages == sampleImages) {
					bgSlice = (Dataset)backgroundToProcess.getSliceView(ssm.getSliceInfo().getViewSlice()).getSlice(ssm.getSliceInfo().getCurrentSlice());
				}
				//if number of BG images is a clean divisor of number of data images, use BG images repeatedly based on mod numBGimages
				else if (sampleImages % backgroundImages == 0) {
					bgSlice = (Dataset)backgroundToProcess.getSliceView().getSlice(new Slice(ssm.getSliceInfo().getSliceNumber() % backgroundImages, ssm.getSliceInfo().getSliceNumber() % backgroundImages + 1));
				}
				else {
					System.out.println("has gotten through everything. what have I missed?"); //TODO average or is this illegal?
					bgSlice = (Dataset)backgroundToProcess.getSliceView(ssm.getSliceInfo().getViewSlice()).getSlice(ssm.getSliceInfo().getCurrentSlice());

					//if background image is the same shape as parent slice (but slice is reduced), then run a process on the background files
					if (slice.getShape().length < bgSlice.getSliceView().squeeze().getShape().length) {
						throw new IllegalArgumentException("Data reduced/BG unreduced background subtraction is currently not supported");
					}
				}
			}
			//data slice must not be larger than BG data slice! we have not done enough reduction on slices in this case!
			if (slice.getShape().length > bgSlice.getShape().length) {
				throw new Exception("Slice should not have bigger dimensionality than the background data");
			}
			
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

	private int getNumberOfImages(ILazyDataset backgroundToProcess2, SliceFromSeriesMetadata ssm) {
		//find location of data dimensions in origin, see if we have them in background
		ILazyDataset origin = ssm.getParent();
		List<Integer>backgroundDataDims = new ArrayList<Integer>();
		for (int dataDim : ssm.getShapeInfo().getDataDimensions()) {
			int dataDimSize = origin.getShape()[dataDim];
			for (int i = 0; i < backgroundToProcess2.getShape().length; ++i) {
				if (backgroundToProcess2.getShape()[i] == dataDimSize) {
					backgroundDataDims.add(i);
				}
			}
		}
		
		int backgroundImages = 1;
		for (int i=0; i < backgroundToProcess2.getShape().length; ++i) {
			int backgroundDim = backgroundToProcess2.getShape()[i];
			if (backgroundDim != 1 && !backgroundDataDims.contains(i)) {
				backgroundImages *= backgroundDim;
			}
		}
		return backgroundImages;
	}

	private int getNumberOfSliceImages(SliceFromSeriesMetadata ssm) {
		int totalSize = 1;
		for (int i = 0; i < ssm.getShapeInfo().getSubSampledShape().length; ++i) {
			boolean isADataDimension = false;
			for (int j=0; j < ssm.getShapeInfo().getDataDimensions().length; ++j) {
				if (ssm.getShapeInfo().getDataDimensions()[j] == i) {
					isADataDimension = true;
					break;
				}
			}
			if (!isADataDimension) {
				totalSize *= ssm.getShapeInfo().getSubSampledShape()[i];
			}
		}
		return totalSize;
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
