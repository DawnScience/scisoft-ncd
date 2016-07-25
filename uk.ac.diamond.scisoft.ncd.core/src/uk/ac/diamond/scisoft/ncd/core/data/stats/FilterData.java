/*
 * Copyright 2014 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.ncd.core.data.stats;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.dawnsci.analysis.dataset.impl.function.DatasetToDatasetFunction;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.IndexIterator;
import org.eclipse.january.dataset.PositionIterator;
import org.eclipse.january.dataset.Slice;
import org.eclipse.january.dataset.Stats;

public class FilterData extends SaxsStatsData {
	
	private class QuantileFilter implements DatasetToDatasetFunction {
		
		private double  lowQuant = 0.25;
		private double highQuant = 0.75;
		
		public QuantileFilter() {
		}
		
		@Override
		public List<Dataset> value(IDataset... datasets) {
			
			if (datasets.length == 0)
				return null;

			List<Dataset> result = new ArrayList<Dataset>();
			
			for (IDataset idataset : datasets) {
				Dataset dataset = DatasetUtils.convertToDataset(idataset);
				final int dt = dataset.getDType();
				final int is = dataset.getElementsPerItem();
				final int[] ishape = dataset.getShape();
				
				if (ishape.length > 1)
					throw new IllegalArgumentException("Only 1D input datasets are supported");
				
				Dataset filtered = DatasetFactory.zeros(is , ishape, dt);
				
				final PositionIterator iterPos = filtered.getPositionIterator();
				final int[] pos = iterPos.getPos();
				final int size = dataset.getSize();
				final int[] start = new int[1];
				final int[] stop = new int[1];
				final int[] step = new int[] {1};
				List<Double> resList = new ArrayList<Double>(); 
				while (iterPos.hasNext()) {
					int idx = pos[0];
					start[0] = Math.max(idx - filterWindow, 0);
					stop[0] = Math.min(idx + filterWindow + 1, size); // exclusive
					Dataset slice = dataset.getSlice(start, stop, step);
					double lowQ = Stats.quantile(slice, lowQuant);
					double highQ = Stats.quantile(slice, highQuant);
					Double mean = (highQ + lowQ) / 2.0;
					Double dev =  (highQ - lowQ) / 2.0;
					Double val = dataset.getDouble(idx);
					if (Math.abs(val - mean) < devSigma*dev) {
						resList.add(val);
					}
				}
				if (!resList.isEmpty()) {
					filtered = DatasetFactory.createFromList(resList);
					result.add(filtered);
				}
			}
			return result;
		}
	}
	
	private double confidenceInterval;
	private int filterWindow = 10;
	private double devSigma = 10.0;

	public FilterData() {
		super();
		this.confidenceInterval = SaxsAnalysisStatsParameters.SAXS_FILTERING_CI;
	}

	@Override
	public Dataset getStatsData() {
		int datasize = referenceData.getSize();
		Dataset result = DatasetFactory.ones(new int[] { datasize }, Dataset.INT);
		if (referenceData != null) {
			result.imultiply(getStatsData(referenceData));
			result.imultiply(getStatsData(referenceData.getError()));
		}
		return result;
	}
	
	private Dataset getStatsData(Dataset inputData) {
		if (inputData != null) {
			int datasize = inputData.getSize();
			Dataset result = DatasetFactory.ones(new int[] { datasize }, Dataset.INT);
			if (datasize <= SaxsAnalysisStatsParameters.SAXS_FILTERING_MINPOINTS) {
				return result;
			}
			
			// Find data slice that passes normality test
			AndersonDarlingNormalityTest test = new AndersonDarlingNormalityTest("2.5%");
			int lastIdxTrue = 0;
			for (int i = SaxsAnalysisStatsParameters.SAXS_FILTERING_MINPOINTS; i < datasize; i++) {
				Dataset data = inputData.getSlice(new Slice(0, i));
				QuantileFilter outlierFilter = new QuantileFilter();
				List<? extends Dataset> filter = outlierFilter.value(data);
				if (filter.isEmpty()) {
					result.set(false, i);
				} else {
					boolean accept = test.acceptNullHypothesis(filter.get(0));
					if (accept) {
						lastIdxTrue = i;
					}
					result.set(accept, i);
				}
			}
			for (int i = 0; i <= lastIdxTrue; i++) {
				result.set(true, i);
			}
			
			// Find outliers in the selected data slice
			Dataset selectedData = inputData.getSlice(new Slice(0, lastIdxTrue + 1));
			IndexIterator itr = selectedData.getIterator(true);
			final int[] start = new int[1];
			final int[] stop = new int[1];
			final int[] step = new int[] {1};
			final int size = selectedData.getSize();
			while (itr.hasNext()) {
				int[] pos = itr.getPos();
				int idx = pos[0];
				start[0] = Math.max(idx - filterWindow, 0);
				stop[0] = Math.min(idx + filterWindow + 1, size); // exclusive
				Dataset slice = inputData.getSlice(start, stop, step);
				double q14 = Stats.quantile(slice, 0.25);
				double q34 = Stats.quantile(slice, 0.75);
				Double mean = (q34 + q14) / 2.0;
				Double dev =  (q34 - q14) / 2.0;
				Double val = inputData.getDouble(idx);
				if (Double.isInfinite(val) || Double.isNaN(val) || Math.abs(val - mean) > devSigma*dev) {
					result.set(0, pos);
				}
			}
			return result;
		}
		return null;
	}

	public void setConfigenceInterval(double saxsFilteringCI) {
		this.confidenceInterval = saxsFilteringCI;
	}

}
