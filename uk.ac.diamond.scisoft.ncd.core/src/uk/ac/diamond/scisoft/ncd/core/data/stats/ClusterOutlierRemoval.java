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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.stat.StatUtils;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Dataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetFactory;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;

public class ClusterOutlierRemoval extends SaxsStatsData {

	private double dbSCANClustererEpsilon;
	private int dbSCANClustererMinPoints;
	
	public ClusterOutlierRemoval() {
		super();
		this.dbSCANClustererEpsilon = SaxsAnalysisStatsParameters.DBSCAN_CLUSTERER_EPSILON;
		this.dbSCANClustererMinPoints = SaxsAnalysisStatsParameters.DBSCAN_CLUSTERER_MINPOINTS;
	}

	@Override
	public Dataset getStatsData() {
		if (referenceData != null) {
			int[] shape = referenceData.getShape();
			IndexIterator itr = referenceData.getIterator(true);
			Map<DoublePoint, Integer> clusterInputMap = new HashMap<DoublePoint, Integer>();
			while (itr.hasNext()) {
				int[] pos = itr.getPos();
				int idx = AbstractDataset.getFlat1DIndex(shape, pos);
				double val = referenceData.getDouble(pos); 
				clusterInputMap.put(new DoublePoint(new double[] {val}), idx);
			}
			
			DBSCANClusterer<DoublePoint> clusterer = new DBSCANClusterer<DoublePoint>(dbSCANClustererEpsilon, dbSCANClustererMinPoints);
			List<Cluster<DoublePoint>> clusterResults = clusterer.cluster(clusterInputMap.keySet());
			// output the clusters
			List<Double> dev = new ArrayList<Double>(clusterResults.size());
			for (int i = 0; i < clusterResults.size(); i++) {
			    System.out.println("Cluster " + i);
				List<DoublePoint> points = clusterResults.get(i).getPoints();
				int size = points.size();
				if (size == 1) {
					dev.add(Double.MAX_VALUE);
					continue;
				}
				double[] values = new double[size];
			    for (int j = 0; j < values.length; j++) {
			    	DoublePoint point = points.get(j);
			    	values[j] = point.getPoint()[0];
			    }
			    dev.add(StatUtils.variance(values));
		        System.out.println("Number of points : " + Integer.toString(size));
		        System.out.println("Variance : " + Double.toString(dev.get(i)));
			    System.out.println();
			}
			
			Dataset result = DatasetFactory.zeros(shape, Dataset.INT);
			//int selectIndex = dev.indexOf(Collections.min(dev));
			for (int selectIndex = 0; selectIndex < clusterResults.size(); selectIndex++) {
				Cluster<DoublePoint> selectCluster = clusterResults.get(selectIndex);
				for (DoublePoint point : selectCluster.getPoints()) {
					Integer idx = clusterInputMap.get(point);
					result.set(selectIndex, AbstractDataset.getNDPositionFromShape(idx, shape));
				}
			}
			return result; 
		}
		return null;
	}

	public void setDbSCANClustererEpsilon(double dbSCANClustererEpsilon) {
		this.dbSCANClustererEpsilon = dbSCANClustererEpsilon;
	}

	public void setDbSCANClustererMinPoints(int dbSCANClustererMinPoints) {
		this.dbSCANClustererMinPoints = dbSCANClustererMinPoints;
	}

}
