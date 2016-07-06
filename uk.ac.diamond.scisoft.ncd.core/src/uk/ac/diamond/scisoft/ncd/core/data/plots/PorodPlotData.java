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
import org.apache.commons.math3.random.Well19937a;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.util.Pair;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;

public class PorodPlotData extends SaxsPlotData {

	private int cmaesLambda = 500;
	private int cmaesMaxIterations = 100000;
	private int cmaesCheckFeasableCount = 100;
	private ConvergenceChecker<PointValuePair> cmaesChecker = new SimplePointChecker<PointValuePair>(1e-6, 1e-8);
	
	private class PorodLineFitFunction implements MultivariateFunction {

		private Dataset porodData;
		private Dataset porodAxis;
		private SimpleRegression regression;
		
		private static final int MIN_POINTS = 50;

		public PorodLineFitFunction(Dataset porodData, Dataset porodAxis) {
			super();
			this.porodData = porodData;
			this.porodAxis = porodAxis;
			regression = new SimpleRegression();
		}

		@Override
		public double value(double[] pos) {
			int idxMin = DatasetUtils.findIndexGreaterThanOrEqualTo(porodAxis, pos[0]);
			int idxMax = DatasetUtils.findIndexGreaterThanOrEqualTo(porodAxis, pos[1]);
			if ((idxMax - idxMin) < MIN_POINTS) {
				return -Double.MAX_VALUE;
			}
			
			regression.clear();
			for (int i = idxMin; i < idxMax; i++) {
				double axisVal = porodAxis.getDouble(i);
				double dataVal = porodData.getDouble(i);
				regression.addData(axisVal, dataVal);
			}

			regression.regress();
			double r = regression.getR();
			double slope = regression.getSlope();
			double intercept = regression.getIntercept();
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
			return -Math.log(Math.abs(slope));
		}
		
	}

	public PorodPlotData() {
		super();
		Pair<String, String> axesNames = SaxsAnalysisPlotType.POROD_PLOT.getAxisNames();
		groupName = SaxsAnalysisPlotType.POROD_PLOT.getGroupName();
		variableName = axesNames.getFirst();
		dataName = axesNames.getSecond();
	}
	
	@Override
	public double getDataValue(int idx, IDataset axis, IDataset data) {
		return (Math.pow(axis.getDouble(idx), 4) * data.getDouble(idx));
	}
	
	@Override
	public double getAxisValue(int idx, IDataset axis) {
		return axis.getDouble(idx);
	}
	
	@Override
	public double getDataError(int idx, IDataset axis, IDataset data) {
		if (data.hasErrors() && axis.hasErrors()) {
			double val = data.getDouble(idx);
			double err = data.getError(idx);
			double axval = axis.getDouble(idx);
			double axerr = axis.getError(idx);
			return Math.sqrt(Math.pow(4.0*Math.pow(axval, 3.0)*val*axerr, 2.0) + Math.pow(Math.pow(axval, 4.0)*err, 2.0));
		}
		return Double.NaN;
	}

	@Override
	public double getAxisError(int idx, IDataset axis) {
		if (axis.hasErrors()) {
			return axis.getError(idx);
		}
		return Double.NaN;
	}
	
	public SimpleRegression getPorodPlotParameters(IDataset data, IDataset axis) {
		Dataset porodData = getSaxsPlotDataset(data, axis);
		Dataset porodAxis = getSaxsPlotAxis(axis);
		CMAESOptimizer optimizer = new CMAESOptimizer(
				cmaesMaxIterations,
				0.0,
				true,
				0,
				cmaesCheckFeasableCount,
				new Well19937a(),
				false,
				cmaesChecker);
		PorodLineFitFunction function = new PorodLineFitFunction(porodData, porodAxis);

		int dataSize = porodAxis.getSize();
		double q0 = porodAxis.getDouble(0);
		double qMax = porodAxis.getDouble(dataSize - 1);
		double[] startPosition= new double[] { porodAxis.getDouble(dataSize - PorodLineFitFunction.MIN_POINTS - 1), porodAxis.getDouble(dataSize - 1) };
		double[] cmaesInputSigma = new double[] { qMax * 0.1, qMax * 0.1 };
		try {
			final PointValuePair res = optimizer.optimize(new MaxEval(cmaesMaxIterations),
					new ObjectiveFunction(function),
					GoalType.MAXIMIZE,
					new CMAESOptimizer.PopulationSize(cmaesLambda),
					new CMAESOptimizer.Sigma(cmaesInputSigma),
					new SimpleBounds(new double[] { q0, q0 }, new double[] { qMax, qMax }),
					new InitialGuess(startPosition));
			
			function.value(res.getPoint());
			double slope = function.regression.getSlope();
			Amount<Dimensionless> c4 = getC4(function.regression);
			
			System.out.println("Final Result");
			String msg = StringUtils.join(new String[] {
					"	c4 ",
					c4.toString(),
					"	slope ",
					Double.toString(slope)
					},
					" : ");
			System.out.println(msg);
			msg = StringUtils.join(new String[] {
					"Slice",
					ArrayUtils.toString(res.getPoint()),
					"R", Double.toString(function.regression.getR())
					},
					" : ");
			System.out.println(msg);
		} catch (MaxCountExceededException e) {
			return null;
		}
		return function.regression;
	}
	
	public Amount<Dimensionless> getC4(SimpleRegression regression) {
		Amount<Dimensionless> c4 = Amount.valueOf(regression.getIntercept(), regression.getInterceptStdErr(), Dimensionless.UNIT);
		return c4.copy();
	}

	public Dataset getFitData(Dataset axis, SimpleRegression regression) {
		Dataset porodAxis = getSaxsPlotAxis(axis);
		Dataset result = DatasetFactory.zeros(porodAxis.getShape(), Dataset.FLOAT32);
		Dataset errors = DatasetFactory.zeros(porodAxis.getShape(), Dataset.FLOAT64);
		for (int i = 0; i < porodAxis.getSize(); i++) {
			Amount<Dimensionless> q = Amount.valueOf(axis.getDouble(i), axis.getError(i), Dimensionless.UNIT); 
			Amount<Dimensionless> res = getC4(regression).divide(q.pow(4)).to(Dimensionless.UNIT);
			result.set(res.getEstimatedValue(), i);
			errors.set(res.getAbsoluteError(), i);
		}
		result.setError(errors);
		return result;
	}
	
}
