/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.measure.quantity.Dimensionless;

import org.apache.commons.lang.math.NumberUtils;
import org.eclipse.dawnsci.analysis.api.dataset.DatasetException;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.IExportOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.AggregateDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStats;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStatsParameters;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

public class NcdAveragingOperation extends AbstractOperation<NcdAveragingModel, OperationData> implements IExportOperation {

	private Dataset[] sliceData;
	private int counter;

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.NcdAveraging";
	}

	@Override
	public void init() {
		sliceData = null;
		counter = 0;
	}

	@SuppressWarnings("unchecked")
	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		
		SliceFromSeriesMetadata ssm = getSliceSeriesMetadata(input);
		
		Dataset d = DatasetUtils.cast(input,Dataset.FLOAT64);
		
		if (sliceData == null) {
			sliceData = new Dataset[ssm.getTotalSlices()];
		}
		
		//first accumulate all data
		sliceData[counter] = d;
		counter++;
		
		if (counter == ssm.getTotalSlices()) {

			Serializable[] filterData = null;
			if (model.isUseFiltering()) {
				//calculate Rg from GuinierPlotData - same as in GuinierOperation
				double[] rG = new double[counter];
				double[] rGError = new double[counter];
				for (int i=0; i < counter; ++i) {
					try {
						Object[] guinierParams = NcdOperationUtils.getGuinierPlotParameters(sliceData[i]);
						Amount<Dimensionless> rGAmount = (Amount<Dimensionless>)guinierParams[1];
						rG[i] = rGAmount.getEstimatedValue();
						rGError[i] = rGAmount.getAbsoluteError();
					} catch (Exception e) {
						throw new OperationException(this, "Exception during Guinier calculation" + e);
					}
					
				}
				//filter using method from NcdSaxsDataStatsForkJoinTransformer - this part from NcdDataReductionTransformer
				String saxsSelectionAlgorithm = "Data Filter"; //TODO these parameters are from preferences in Irakli's GUI
				String strDBSCANClustererEps = "0.1";
				int dbSCANClustererMinPoints = 0;
				String strSaxsFilteringCI = "0.999";

				SaxsAnalysisStatsParameters saxsAnalysisStatParams = new SaxsAnalysisStatsParameters();
				saxsAnalysisStatParams.setSelectionAlgorithm(SaxsAnalysisStats.forName(saxsSelectionAlgorithm));
				if (NumberUtils.isNumber(strDBSCANClustererEps)) {
					saxsAnalysisStatParams.setDbSCANClustererEpsilon(Double.valueOf(strDBSCANClustererEps));
				}
				saxsAnalysisStatParams.setDbSCANClustererMinPoints(dbSCANClustererMinPoints);
				if (NumberUtils.isNumber(strDBSCANClustererEps)) {
					saxsAnalysisStatParams.setSaxsFilteringCI(Double.valueOf(strSaxsFilteringCI));
				}
				
				DoubleDataset rgDataset = new DoubleDataset(rG);
				rgDataset.setName("Rg");
				DoubleDataset rgErrorDataset = new DoubleDataset(rGError);
				rgDataset.setError(rgErrorDataset);
				Dataset removalFilter = NcdOperationUtils.getSaxsAnalysisStats(rgDataset, saxsAnalysisStatParams); //remove frame[i] if true
				removalFilter.setName("Removal filter");
				
				List<Dataset> filteredDataset = new ArrayList<Dataset>();
				for (int i=0; i < counter; ++i) {
					if (removalFilter.getBoolean(i) == false) {
						filteredDataset.add(sliceData[i]);
					}
				}
				sliceData = new Dataset[filteredDataset.size()];
				sliceData = filteredDataset.toArray(sliceData);
				filterData = new Serializable[]{rgDataset, removalFilter};
			}
			
			//after filtering (if done), do the averaging
			AggregateDataset aggregate = new AggregateDataset(true, sliceData);
			int numImages = sliceData.length;
			Dataset[] errorData = new Dataset[numImages];
			boolean hasError = false;
			for (int i=0; i < numImages; ++i) {
				Dataset slice = sliceData[i];
				errorData[i] = NcdOperationUtils.getErrorBuffer(slice);
				if (errorData[i] != null) {
					hasError = true;
				}
			}

			Dataset errorSum = null;
			if (hasError) {
				AggregateDataset aggregateErrors = new AggregateDataset(true, errorData);
				try {
					errorSum = aggregateErrors.getSlice().sum(false, 0);
				} catch (DatasetException e) {
					throw new OperationException(this, e);
				}
			}

			Dataset out;
			try {
				out = aggregate.getSlice().mean(false, 0);
			} catch (DatasetException e) {
				throw new OperationException(this, e);
			}
			copyMetadata(input, out);
			SliceFromSeriesMetadata outsmm = ssm.clone();
			for (int i = 0; i < ssm.getParent().getRank(); i++) {
				
				if (!outsmm.isDataDimension(i)) outsmm.reducedDimensionToSingular(i);
				
			}
			out.setMetadata(outsmm);
			if (hasError) {
				out.setError(errorSum.ipower(0.5).idivide(numImages));
			}
			sliceData = null;
			counter = 0;
			if (model.isUseFiltering()) {
				return new OperationData(out, filterData);
			}
			return new OperationData(out);
		}
		
		return null;
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ONE;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}

}
