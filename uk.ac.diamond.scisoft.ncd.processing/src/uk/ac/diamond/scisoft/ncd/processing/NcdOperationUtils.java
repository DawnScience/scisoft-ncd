/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.ncd.processing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.metadata.AxesMetadata;
import org.eclipse.dawnsci.analysis.dataset.impl.AbstractDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.ncd.core.data.plots.GuinierPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.stats.ClusterOutlierRemoval;
import uk.ac.diamond.scisoft.ncd.core.data.stats.FilterData;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStats;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStatsParameters;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsStatsData;

public class NcdOperationUtils {
	/**
	 * Add a dimension of 1 to account for slicing of original data in the Processing pipeline
	 * @param set
	 * @return
	 */
	public static int[] addDimension(int[] set) {
		int[] dataShape = new int[set.length + 1];
		dataShape[0] = 1;
		int index = 1;
		for (int dimension: set) {
			dataShape[index++] = dimension;
		}
		return dataShape;
	}
	
	/**
	 * Check existing paths list, then /entry/result/data in case this is a file from created from the Processing pipeline.
	 * @param fileToRead
	 * @return
	 * @throws Exception
	 */
	public static ILazyDataset getDataset(String fileToRead, List<String> dataPathsToTry) throws Exception {
		dataPathsToTry.add("/entry/result/data");
		ILazyDataset toReturn = null;
		IDataHolder holder = LoaderFactory.getData(fileToRead);
		for (String location : dataPathsToTry) {
			toReturn = holder.getLazyDataset(location);
			if (toReturn != null) {
				break;
			}
		}
		return toReturn;
	}
	

	public static Object[] getGuinierPlotParameters(IDataset slice) throws Exception {
		GuinierPlotData guinier = new GuinierPlotData();
		
		@SuppressWarnings("unused")
		IDataset dataSlice = slice.getSliceView();
		ILazyDataset axisSlice;
		try {
			axisSlice = slice.getMetadata(AxesMetadata.class).get(0).getAxes()[0];
			if (axisSlice == null) {
				throw new Exception("No axes found");
			}
		} catch (Exception e) {
			throw new Exception("problem while getting axis metadata", e);
		}
		Object[] params = guinier.getGuinierPlotParameters(slice, (IDataset)axisSlice);
		return params;
	}
	
	public static Dataset getSaxsAnalysisStats(Dataset data, SaxsAnalysisStatsParameters statType) {
		SaxsAnalysisStats selectedSaxsStat = statType.getSelectionAlgorithm();
		SaxsStatsData statsData = selectedSaxsStat.getSaxsAnalysisStatsObject();
		if (statsData instanceof FilterData) {
			((FilterData)statsData).setConfigenceInterval(statType.getSaxsFilteringCI());
		}
		if (statsData instanceof ClusterOutlierRemoval) {
			((ClusterOutlierRemoval)statsData).setDbSCANClustererEpsilon(statType.getDbSCANClustererEpsilon());
			((ClusterOutlierRemoval)statsData).setDbSCANClustererMinPoints(statType.getDbSCANClustererMinPoints());
		}

		statsData.setReferenceData(data);
		Dataset mydata = statsData.getStatsData();
		return mydata;
	}
	
	/**
	 * Provide an easy way to get the errorBuffer because only error may be defined
	 * @param data
	 * @return
	 */
	public static Dataset getErrorBuffer(Dataset data) {
		if (data.getErrorBuffer() == null) {
			Dataset error = data.getError() == null ? data.clone() : data.getError();
			return error.ipower(2);
		}
		return data.getErrorBuffer();
	}
	
	public static Dataset getBackgroundSlice(SliceFromSeriesMetadata ssm, IDataset slice, ILazyDataset background) throws Exception {
		Dataset bgSlice;

		//if the background image is the same shape as the sliced image, then do simple subtraction on the background
		if (Arrays.equals(AbstractDataset.squeezeShape(slice.getShape(), false), AbstractDataset.squeezeShape(background.getShape(), false))) {
			bgSlice = (Dataset) background.getSlice();
		}
		else {
			//if number of images between background and parent dataset are the same, subtract each BG from corresponding data slice
			int backgroundImages = getNumberOfImages(background, ssm, slice);
			int sampleImages = getNumberOfSliceImages(ssm);
			if (backgroundImages == sampleImages) {
				bgSlice = (Dataset) ssm.getMatchingSlice(background);
			}
			else {
				throw new IllegalArgumentException("Background data not compatible with subtraction from data - consider averaging the background data before background subtraction");
			}
		}
		//data slice must not be larger than BG data slice! we have not done enough reduction on slices in this case!
		if (slice.getShape().length > bgSlice.getShape().length) {
			throw new Exception("Slice should not have bigger dimensionality than the background data");
		}

		Dataset backgroundErrors = NcdOperationUtils.getErrorBuffer(bgSlice.getSlice());
		if (backgroundErrors == null) {
			backgroundErrors = bgSlice.getSlice();
		}
		bgSlice.setErrorBuffer(backgroundErrors);
		return bgSlice;
	}
	
	private static int getNumberOfImages(ILazyDataset backgroundToProcess2, SliceFromSeriesMetadata ssm, IDataset slice) {
		//find location of data dimensions in origin, see if we have them in background
		ILazyDataset origin = ssm.getParent();
		List<Integer>backgroundDataDims = new ArrayList<Integer>();
		List<Integer> dimensionList = new ArrayList<Integer>();
		if (origin.getRank()== backgroundToProcess2.getRank()) {
			for (int dataDim : ssm.getDataDimensions()) {
				dimensionList.add(origin.getShape()[dataDim]);
			}
		}
		else {
			for (int i = 0; i < slice.getShape().length; ++i) {
				dimensionList.add(slice.getShape()[i]);
			}
		}
		
		for (int i = 0; i < backgroundToProcess2.getShape().length; ++i) {
			if (dimensionList.contains(backgroundToProcess2.getShape()[i])) {
				backgroundDataDims.add(i);
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

	private static int getNumberOfSliceImages(SliceFromSeriesMetadata ssm) {
		int totalSize = 1;
		for (int i = 0; i < ssm.getSubSampledShape().length; ++i) {
			boolean isADataDimension = false;
			for (int j=0; j < ssm.getDataDimensions().length; ++j) {
				if (ssm.getDataDimensions()[j] == i) {
					isADataDimension = true;
					break;
				}
			}
			if (!isADataDimension) {
				totalSize *= ssm.getSubSampledShape()[i];
			}
		}
		return totalSize;
	}

}