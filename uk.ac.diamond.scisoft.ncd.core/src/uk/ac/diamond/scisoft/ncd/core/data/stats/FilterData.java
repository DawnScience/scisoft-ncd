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

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;

public class FilterData extends SaxsStatsData {

	@Override
	public AbstractDataset getStatsData() {
		if (referenceData != null) {
			int[] shape = referenceData.getShape();
			double mean = (Double) referenceData.mean(true);
			double dev = (Double) referenceData.stdDeviation();
			dev *= 1.0;
			
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

}
