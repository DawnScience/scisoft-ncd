/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.ncd.processing;

import java.util.List;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.metadata.AxesMetadata;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;

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
		for (String location : dataPathsToTry) {
			IDataHolder holder = LoaderFactory.getData(fileToRead);
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
			Dataset error = data.getError();
			return error.ipower(2);
		}
		return data.getErrorBuffer();
	}
}