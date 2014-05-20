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

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Erf;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.Slice;

public class FilterData extends SaxsStatsData {
	
	private double confidenceInterval;

	public FilterData() {
		super();
		this.confidenceInterval = SaxsAnalysisStatsParameters.SAXS_FILTERING_CI;
	}

	@Override
	public AbstractDataset getStatsData() {
		int datasize = referenceData.getSize();
		AbstractDataset result = AbstractDataset.ones(new int[] { datasize }, AbstractDataset.INT);
		AbstractDataset errors = referenceData.getError();
		
		if (referenceData != null) {
			// Find data slice that passes normality test
			int lastIdxTrue = 0;
			for (int i = SaxsAnalysisStatsParameters.SAXS_FILTERING_MINPOINTS; i < datasize; i++) {
				AbstractDataset data = referenceData.getSlice(new Slice(0, i));
				AbstractDataset dataErrors = errors.getSlice(new Slice(0, i));
				
				boolean test = testAndersonDarling(data, dataErrors);
				
				if (test) {
					lastIdxTrue = i;
				}
				result.set(test, i);
			}
			
			for (int i = 0; i <= lastIdxTrue; i++) {
				result.set(true, i);
			}
			
			// Find outliers in the selected data slice
			AbstractDataset selectedData = referenceData.getSlice(new Slice(0, lastIdxTrue + 1));
			AbstractDataset selectedErrors = errors.getSlice(new Slice(0, lastIdxTrue + 1));
			double mean = (Double) selectedData.mean(true);
			double dev = Math.max((Double) selectedData.stdDeviation(),
					((Double) selectedErrors.mean(true)));
			if (confidenceInterval > 1 || confidenceInterval <= 0) {
				confidenceInterval = SaxsAnalysisStatsParameters.SAXS_FILTERING_CI;
			}
			dev *= Math.sqrt(2.0) * Erf.erfInv(2.0 * confidenceInterval - 1);

			IndexIterator itr = result.getIterator(true);
			while (itr.hasNext()) {
				int[] pos = itr.getPos();
				double val = referenceData.getDouble(pos);
				if (Double.isInfinite(val) || Double.isNaN(val) || (Math.abs(val - mean) > dev)) {
					result.set(0, pos);
				}
			}
			return result;
		}
		return null;
	}

	private boolean testAndersonDarling(AbstractDataset data, AbstractDataset errors) {
		AbstractDataset sortedData = data.clone().sort(null);
		double mean = (Double) data.mean(true);
		int size = data.getSize();
		double std = Math.max((Double) errors.mean(true), (Double) data.stdDeviation());
		
		// Critical values  for following significance levels:
		//  15%    10%    5%    2.5%     1%
		// 0.576, 0.656, 0.787, 0.918, 1.092
		double criticalValue = 0.918;
		double thres = criticalValue / (1.0 + 4.0 / size - 25.0 / size / size);
		
		NormalDistribution norm = new NormalDistribution(mean, std);
		double sum = 0.0;
		for (int i = 0; i < size; i++) { 
			double val1 = sortedData.getDouble(i);
			double val2 = sortedData.getDouble(size - 1 - i);
			double cdf1 = norm.cumulativeProbability(val1);
			double cdf2 = norm.cumulativeProbability(val2);
			sum += (2 * i + 1) * (Math.log(cdf1) + Math.log(1.0 - cdf2));
		}
		double A2 = -size - sum / size;
		return (A2 < 0 ? true : (Math.sqrt(A2) < thres));
	}

	public void setConfigenceInterval(double saxsFilteringCI) {
		this.confidenceInterval = saxsFilteringCI;
	}

}
