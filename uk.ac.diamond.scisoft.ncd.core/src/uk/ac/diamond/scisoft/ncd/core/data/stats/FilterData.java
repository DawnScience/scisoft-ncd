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

public class FilterData extends SaxsStatsData {
	
	private double confidenceInterval;

	public FilterData() {
		super();
		this.confidenceInterval = SaxsAnalysisStatsParameters.SAXS_FILTERING_CI;
	}

	@Override
	public AbstractDataset getStatsData() {
		if (referenceData != null) {
			int[] shape = referenceData.getShape();
			double mean = (Double) referenceData.mean(true);
			double dev = (Double) referenceData.stdDeviation();
			if (confidenceInterval > 1 || confidenceInterval <= 0) {
				confidenceInterval = SaxsAnalysisStatsParameters.SAXS_FILTERING_CI;
			}
			dev *= Math.sqrt(2.0) * Erf.erfInv(2.0 * confidenceInterval - 1);
			
			AbstractDataset result = AbstractDataset.ones(shape, AbstractDataset.INT); 
			IndexIterator itr = result.getIterator(true);
			while (itr.hasNext()) {
				int[] pos = itr.getPos();
				double val = referenceData.getDouble(pos); 
				if (Math.abs(val - mean) > dev) {
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
