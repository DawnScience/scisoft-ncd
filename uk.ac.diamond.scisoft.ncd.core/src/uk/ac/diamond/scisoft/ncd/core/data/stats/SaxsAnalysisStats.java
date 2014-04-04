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

public enum SaxsAnalysisStats {

	DATA_FILTER("Data Filter");
		
	private final String name;

	SaxsAnalysisStats(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public static SaxsAnalysisStats forName(String pt) {
		for (SaxsAnalysisStats statsType : values()) {
			if (statsType.getName().equals(pt)) return statsType;
		}
		return null;
	}
	
	public SaxsStatsData getSaxsAnalysisStatsObject() {
    	SaxsStatsData statsData = null;
		if (this.equals(SaxsAnalysisStats.DATA_FILTER)) {
			statsData = new FilterData();
		}
		return statsData;
	}

}
