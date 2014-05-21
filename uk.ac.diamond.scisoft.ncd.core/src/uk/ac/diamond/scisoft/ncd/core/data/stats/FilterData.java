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

import org.apache.commons.math3.special.Erf;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.Slice;

public class FilterData extends SaxsStatsData {
	
	private double confidenceInterval;
	private AndersonDarlingNormalityTest test;

	public FilterData() {
		super();
		this.confidenceInterval = SaxsAnalysisStatsParameters.SAXS_FILTERING_CI;
		this.test = new AndersonDarlingNormalityTest("2.5%");
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
				
				boolean accept = test.acceptNullHypothesis(data, dataErrors);
				
				if (accept) {
					lastIdxTrue = i;
				}
				result.set(accept, i);
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

	public void setConfigenceInterval(double saxsFilteringCI) {
		this.confidenceInterval = saxsFilteringCI;
	}

}
