/*
 * Copyright 2013 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.core.data.plots;

import javax.measure.quantity.Dimensionless;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.SimplePointChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.util.Pair;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.data.stats.AndersonDarlingNormalityTest;

public class GuinierPlotData extends SaxsPlotData {

	private int cmaesLambda = 500;
	private int cmaesMaxIterations = 100000;
	private int cmaesCheckFeasableCount = 100;
	
	private AndersonDarlingNormalityTest test;
	
	private class GuinierLineFitFunction implements MultivariateFunction {

		private Dataset guinierData;
		private Dataset guinierAxis;
		private SimpleRegression regression;
		
		private static final int MIN_POINTS = 50;

		public GuinierLineFitFunction(Dataset guinierData, Dataset guinierAxis) {
			super();
			this.guinierData = guinierData;
			this.guinierAxis = guinierAxis;
			regression = new SimpleRegression();
		}

		@Override
		public double value(double[] pos) {
			int idxMin = DatasetUtils.findIndexGreaterThanOrEqualTo(guinierAxis, pos[0]);
			int idxMax = DatasetUtils.findIndexGreaterThanOrEqualTo(guinierAxis, pos[1]);
			if ((idxMax - idxMin) < MIN_POINTS) {
				return -Double.MAX_VALUE;
			}
			
			regression.clear();
			for (int i = idxMin; i < idxMax; i++) {
				double dataVal = guinierData.getDouble(i);
				double axisVal = guinierAxis.getDouble(i);
				regression.addData(axisVal, dataVal);
			}

			regression.regress();
			double r = regression.getR();
			double slope = regression.getSlope();
			double intercept = regression.getIntercept();
			
			// Test residual values for normality
			//Dataset testData = DatasetFactory.zeros(FloatDataset.class, idxMax - idxMin);
			//for (int i = idxMin; i < idxMax; i++) {
			//	double dataVal = guinierData.getDouble(i);
			//	double axisVal = guinierAxis.getDouble(i);
			//	double calc = regression.predict(axisVal);
			//	testData.set(dataVal - calc, i - idxMin);
			//}
			//Dataset testErrors = guinierData.getError().getSlice(new Slice(idxMin, idxMax));
			//boolean accept = test.acceptNullHypothesis(testData, testErrors);
			//if (!accept) {
			//	return -Double.MAX_VALUE;
			//}
					
			double I0 = Math.exp(intercept);
			double Rg = Math.sqrt(-3.0*slope);
			if (Rg * pos[1] > 1.5  && Rg * pos[1] < 0.5) {
				return -Double.MAX_VALUE;
			}
			//System.out.println("	Slope : " + Double.toString(slope) + "	Intercept : " + Double.toString(intercept));
			//System.out.println("	I(0) : " + Double.toString(I0) + "	Rg : " + Double.toString(Rg));
			//String msg = StringUtils.join(new String[] {
			//		"Slice",
			//		ArrayUtils.toString(pos),
			//		ArrayUtils.toString(new int[] {idxMin, idxMax}),
			//		"R", Double.toString(r)
			//		},
			//		" : ");
			//System.out.println(msg);
			if (Double.isNaN(slope) || Double.isNaN(intercept) || Double.isNaN(r)) {
				return -Double.MAX_VALUE;
			}
			return -Math.log(1.0 + r);
		}
		
	}

	public GuinierPlotData() {
		super();
		Pair<String, String> axesNames = SaxsAnalysisPlotType.GUINIER_PLOT.getAxisNames();
		groupName = SaxsAnalysisPlotType.GUINIER_PLOT.getGroupName();
		variableName = axesNames.getFirst();
		dataName = axesNames.getSecond();
		test = new AndersonDarlingNormalityTest("5%");
	}
	
	@Override
	public double getDataValue(int idx, IDataset axis, IDataset data) {
		return Math.log(data.getDouble(idx));
	}
	
	@Override
	public double getAxisValue(int idx, IDataset axis) {
		return Math.pow(axis.getDouble(idx), 2);
	}

	@Override
	public double getDataError(int idx, IDataset axis, IDataset data) {
		if (data.hasErrors()) {
			double val = data.getDouble(idx);
			double err = data.getError(idx);
			return err / val;
		}
		return Double.NaN;
	}

	@Override
	public double getAxisError(int idx, IDataset axis) {
		if (axis.hasErrors()) {
			double val = axis.getDouble(idx);
			double err = axis.getError(idx);
			return 2.0 * val * err;
		}
		return Double.NaN;
	}
	
	public Object[] getGuinierPlotParameters(IDataset data, IDataset axis) {
		Dataset guinierData = getSaxsPlotDataset(data, axis);
		Dataset guinierAxis = getSaxsPlotAxis(axis);
/*		int sliceSize = data.getSize() / 10;
		int[] step = new int[] {sliceSize};
		IndexIterator dataSliceIter = guinierData.getSliceIterator(null, null, null);
		IndexIterator axisSliceIter = guinierAxis.getSliceIterator(null, null, null);
		// Iterate over data slices
		int[] minPosition = new int[] {-1};
		double minError = Double.MAX_VALUE;
		double slope = Double.NaN;
		double intercept = Double.NaN;
		Map<DoublePoint, Double> clusterInputMap = new HashMap<DoublePoint, Double>();
		while (dataSliceIter.hasNext() && axisSliceIter.hasNext()) {
			SimpleRegression regression = new SimpleRegression();
			int[] pos = dataSliceIter.getPos();
			// Iterate over data values for linear regression
			IndexIterator dataIter = new SliceIterator(
					guinierData.getShape(),
					guinierData.getSize(),
					pos, step);
			pos = axisSliceIter.getPos();
			IndexIterator axisIter = new SliceIterator(
					guinierAxis.getShape(),
					guinierAxis.getSize(),
					pos, step);
			int points = 0;
			while (dataIter.hasNext() && axisIter.hasNext()) {
				double dataVal = guinierData.getDouble(dataIter.getPos());
				double axisVal = guinierAxis.getDouble(axisIter.getPos());
				regression.addData(axisVal, dataVal);
				points++;
			}
			if (points == sliceSize) {
				regression.regress();
				double err = regression.getMeanSquareError();
				if (err < minError) {
					minError = err;
					minPosition = Arrays.copyOf(pos, pos.length);
					slope = regression.getSlope();
					intercept = regression.getIntercept();
					double I0 = Math.exp(intercept);
					double Rg = Math.sqrt(-3.0*slope);
					System.out.println("    Min Pos : " + Arrays.toString(minPosition));
					System.out.println("	Min Error : " + Double.toString(minError));
					System.out.println("	Slope : " + Double.toString(slope) + "	Intercept : " + Double.toString(intercept));
					System.out.println("	I(0) : " + Double.toString(I0) + "	Rg : " + Double.toString(Rg));
					clusterInputMap.put(new DoublePoint(minPosition), minError);
				}
			} else {
				break;
			}
		}
		
		DBSCANClusterer<DoublePoint> clusterer = new DBSCANClusterer<DoublePoint>(5, 5);
		List<Cluster<DoublePoint>> clusterResults = clusterer.cluster(clusterInputMap.keySet());

		// output the clusters
		for (int i = 0; i < clusterResults.size(); i++) {
		    System.out.println("Cluster " + i);
			double[] minPoint = null;
			double minVal = Double.MAX_VALUE;
		    for (DoublePoint point : clusterResults.get(i).getPoints()) {
		        System.out.println(Arrays.toString(point.getPoint()));
		        Double val = clusterInputMap.get(point);
		        if (val < minVal) {
		        	minVal = val;
		        	minPoint = Arrays.copyOf(point.getPoint(), point.getPoint().length);
		        }
		        minVal = (val < minVal ? val : minVal);
		    }
	        System.out.println("Min cluster point : " + Arrays.toString(minPoint));
	        System.out.println("Min cluster value : " + Double.toString(minVal));
		    System.out.println();
		}
		
*/		ConvergenceChecker<PointValuePair> cmaesChecker = new SimplePointChecker<PointValuePair>(1e-6, 1e-8);
		RandomDataGenerator rnd = new RandomDataGenerator();
		CMAESOptimizer optimizer = new CMAESOptimizer(
				cmaesMaxIterations,
				0.0,
				true,
				0,
				cmaesCheckFeasableCount,
				rnd.getRandomGenerator(),
				false,
				cmaesChecker);
		GuinierLineFitFunction function = new GuinierLineFitFunction(guinierData, guinierAxis);

		Amount<Dimensionless> I0 = Amount.valueOf(Double.NaN, Double.NaN, Dimensionless.UNIT);
		Amount<Dimensionless> Rg = Amount.valueOf(Double.NaN, Double.NaN, Dimensionless.UNIT);
		double[] qvals = new double[] {Double.NaN, Double.NaN};
		
		double q0 = guinierAxis.getDouble(0);
		double qMin = guinierAxis.getDouble(1);
		double qMax = guinierAxis.getDouble(guinierAxis.getSize() - 1);
		double[] startPosition= new double[] { guinierAxis.getDouble(0), guinierAxis.getDouble(GuinierLineFitFunction.MIN_POINTS) };
		double[] cmaesInputSigma = new double[] { (qMin - q0) * 0.1, qMax * 0.1 };
		try {
			final PointValuePair res = optimizer.optimize(new MaxEval(cmaesMaxIterations),
					new ObjectiveFunction(function),
					GoalType.MAXIMIZE,
					new CMAESOptimizer.PopulationSize(cmaesLambda),
					new CMAESOptimizer.Sigma(cmaesInputSigma),
					new SimpleBounds(new double[] { q0, q0 }, new double[] { qMin, qMax }),
					new InitialGuess(startPosition));
			
			qvals = res.getPoint();
			function.value(qvals);
			I0 = getI0(function.regression);
			Rg = getRg(function.regression);
			
			System.out.println("Final Result");
			String msg = StringUtils.join(new String[] {
					"	I(0) ",
					I0.toString(),
					"	Rg ",
					Rg.toString()},
					" : ");
			System.out.println(msg);
			msg = StringUtils.join(new String[] {
					"Slice",
					ArrayUtils.toString(res.getPoint()),
					"R", Double.toString(function.regression.getR())
					},
					" : ");
			System.out.println(msg);
			
/*			// Run Monte-Carlo simulation to generate error estimates Rg values 
			//double finalR = function.regression.getR();
			int maxSample = 10000;
			int minSample = 10;
			int totalSample = 100000;
			int counter = 0;
			int totalCounter = 0;
			GuinierLineFitFunction mcFunction = new GuinierLineFitFunction(guinierData, guinierAxis);
			DescriptiveStatistics statsR = new DescriptiveStatistics();
			List<Pair<Double, Amount<Dimensionless>>> listI0 = new ArrayList<Pair<Double,Amount<Dimensionless>>>();
			List<Pair<Double, Amount<Dimensionless>>> listRg = new ArrayList<Pair<Double,Amount<Dimensionless>>>();
			while ((counter < maxSample && totalCounter < totalSample)
					|| (counter < minSample && totalCounter >= totalSample)) {
				double q1 = rnd.nextUniform(q0, qMin);
				double q2 = rnd.nextUniform(q0, qMax);
				if (!(q2 > q1)) {
					continue;
				}
				totalCounter++;
 				mcFunction.value(new double[] {q1, q2});
 				double tmpR = Math.abs(mcFunction.regression.getR());
 				//boolean equalsR = Precision.equalsWithRelativeTolerance(tmpR, finalR, 0.1); 
 				if (!(Double.isNaN(tmpR) || Double.isInfinite(tmpR))) {
 					statsR.addValue(tmpR);
 					Amount<Dimensionless> tmpI0 = getI0(mcFunction.regression);
 					Amount<Dimensionless> tmpRg = getRg(mcFunction.regression);
 	 				if (Double.isNaN(tmpI0.getEstimatedValue()) || Double.isInfinite(tmpI0.getEstimatedValue()) ||
 	 						Double.isNaN(tmpRg.getEstimatedValue()) || Double.isInfinite(tmpRg.getEstimatedValue())) {
 	 					continue;
 	 				}
 					listI0.add(new Pair<Double, Amount<Dimensionless>>(tmpR, tmpI0));
 					listRg.add(new Pair<Double, Amount<Dimensionless>>(tmpR, tmpRg));
 					counter++;
 				}
			}
			
			double threshold = statsR.getPercentile(90);
			//double threshold = 0.95*statsR.getMax();
			SummaryStatistics statsI0 = new SummaryStatistics();
			SummaryStatistics statsRg = new SummaryStatistics();
			for (Pair<Double, Amount<Dimensionless>> tmpVal : listRg) {
				if (tmpVal.getFirst() > threshold) {
 					statsRg.addValue(tmpVal.getSecond().getEstimatedValue());
				}
			}
			for (Pair<Double, Amount<Dimensionless>> tmpVal : listI0) {
				if (tmpVal.getFirst() > threshold) {
 					statsI0.addValue(tmpVal.getSecond().getEstimatedValue());
				}
			}
			
			double meanI0 = statsI0.getMean();
			double stdI0 = statsI0.getStandardDeviation();
			I0 = Amount.valueOf(meanI0, stdI0, Dimensionless.UNIT);
			
			double meanRg = statsRg.getMean();
			double stdRg = statsRg.getStandardDeviation();
			Rg = Amount.valueOf(meanRg, stdRg, Dimensionless.UNIT);
			
			String msg = StringUtils.join(new String[] {
					"Monte-Carlo Rg", Rg.toString()
					},
					" : ");
			System.out.println(msg);
*/			
		} catch (MaxCountExceededException e) {
			System.out.println("Maximum counts exceeded");
			return null;
		}
		return new Object[] {I0, Rg, qvals[0], qvals[1]};
	}

	private Amount<Dimensionless> getI0(SimpleRegression regression) {
		Amount<Dimensionless> I0 = Amount.valueOf(regression.getIntercept(), regression.getInterceptStdErr(), Dimensionless.UNIT);
		return I0.copy();
	}

	private Amount<Dimensionless> getRg(SimpleRegression regression) {
		Amount<Dimensionless> slope = Amount.valueOf(regression.getSlope(), regression.getSlopeStdErr(), Dimensionless.UNIT);
		Amount<Dimensionless> Rg = slope.times(-3.0).sqrt().to(Dimensionless.UNIT);
		return Rg.copy();
	}
	
	public Dataset getFitData(SimpleRegression regression, IDataset axis) {
		Dataset guinierAxis = getSaxsPlotAxis(axis);
		Dataset result = DatasetFactory.zeros(guinierAxis.getShape(), Dataset.FLOAT32);
		for (int i = 0; i < guinierAxis.getSize(); i++) {
			result.set(regression.predict(guinierAxis.getDouble(i)), i);
		}
		
		return result;
	}
	
	
}
