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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.distribution.NormalDistribution;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;

public class AndersonDarlingNormalityTest {
	
	private static Map<String, Double> criticalValuesMap = new HashMap<String, Double>(5);
	
	// Critical values for following significance levels for Normal distribution:
	//  15%    10%    5%    2.5%     1%
	// 0.576, 0.656, 0.787, 0.918, 1.092
	static {
		criticalValuesMap.put("1%",   1.092);
		criticalValuesMap.put("2.5%", 0.918);
		criticalValuesMap.put("5%",   0.787);
		criticalValuesMap.put("10%",  0.656);
		criticalValuesMap.put("15%",  0.576);
	}
	
	private double criticalValue;
	
	/**
	 * Construct Anderson-Darling tests for normality
	 * 
	 * @param significanceLevelKey
	 *            key identifying supported significance level
	 * @throws IllegalArgumentException
	 *             if significanceLevelKey does not match any of the available values
	 */
	public AndersonDarlingNormalityTest(String significanceLevelKey) {
		if (!criticalValuesMap.containsKey(significanceLevelKey)) {
			throw new IllegalArgumentException("Unsupported significance level provided");
		}
		this.criticalValue = criticalValuesMap.get(significanceLevelKey);
	}

	/**
	 * Set of keys indicating supported significance levels
	 * 
	 * @return key set of supported significance levels
	 */
	public static Set<String> avaliableSignificanceLevels() {
		return criticalValuesMap.keySet();
	}
	
	public boolean acceptNullHypothesis(AbstractDataset data, AbstractDataset errors) {
		AbstractDataset sortedData = data.clone().sort(null);
		double mean = (Double) data.mean(true);
		int size = data.getSize();
		double std = Math.max((Double) errors.mean(true), (Double) data.stdDeviation());
		
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

}
