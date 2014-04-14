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

public class SaxsAnalysisStatsParameters {
	
	public static final SaxsAnalysisStats DEFAULT_SELECTION_METHOD = SaxsAnalysisStats.IDENTITY_FILTER;
	public static final double DBSCAN_CLUSTERER_EPSILON = 0.1;
	public static final int DBSCAN_CLUSTERER_MINPOINTS = 1;	
	public static final double SAXS_FILTERING_CI = 0.95;
	
	private SaxsAnalysisStats selectionAlgorithm;
	private double dbSCANClustererEpsilon;
	private int dbSCANClustererMinPoints;
	private double saxsFilteringCI;
	
	public SaxsAnalysisStatsParameters() {
		selectionAlgorithm = DEFAULT_SELECTION_METHOD;
		dbSCANClustererEpsilon = DBSCAN_CLUSTERER_EPSILON;
		dbSCANClustererMinPoints = DBSCAN_CLUSTERER_MINPOINTS;
		saxsFilteringCI = SAXS_FILTERING_CI;
	}
	
	public SaxsAnalysisStats getSelectionAlgorithm() {
		return selectionAlgorithm;
	}
	
	public void setSelectionAlgorithm(SaxsAnalysisStats selectionAlgorithm) {
		this.selectionAlgorithm = selectionAlgorithm;
	}
	
	public double getDbSCANClustererEpsilon() {
		return dbSCANClustererEpsilon;
	}
	
	public void setDbSCANClustererEpsilon(double dbSCANClustererEpsilon) {
		this.dbSCANClustererEpsilon = dbSCANClustererEpsilon;
	}
	
	public int getDbSCANClustererMinPoints() {
		return dbSCANClustererMinPoints;
	}
	public void setDbSCANClustererMinPoints(int dbSCANClustererMinPoints) {
		this.dbSCANClustererMinPoints = dbSCANClustererMinPoints;
	}
	
	public double getSaxsFilteringCI() {
		return saxsFilteringCI;
	}
	
	public void setSaxsFilteringCI(double saxsFilteringCI) {
		this.saxsFilteringCI = saxsFilteringCI;
	}
	
}
