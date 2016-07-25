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
import org.apache.commons.math3.util.Pair;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;

import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;

public class LogLogPlotData extends SaxsPlotData {

	private int cmaesLambda = 5;
	private int cmaesMaxIterations = 10000;
	private int cmaesCheckFeasableCount = 10;
	private ConvergenceChecker<PointValuePair> cmaesChecker = new SimplePointChecker<PointValuePair>(1e-4, 1e-4);

	private class PorodLineFitFunction implements MultivariateFunction {

		private IDataset porodData;
		private IDataset porodAxis;
		
		public PorodLineFitFunction(IDataset data, IDataset axis) {
			super();
			this.porodData = data;
			this.porodAxis = axis;
		}

		@Override
		public double value(double[] params) {
			double solvation = params[0];
			double correlation = params[1];
			double exponent = params[2];

			double rms = 0.0;
			for (int i = 0; i < porodAxis.getSize(); i++) {
				double dataVal = porodData.getDouble(i);
				double axisVal = Math.pow(10.0, porodAxis.getDouble(i));
				double func = Math.log10(solvation / (1.0 + Math.pow(axisVal * correlation,  exponent)));
				if (Double.isInfinite(func) || Double.isNaN(func)) {
					return -Double.MAX_VALUE;
				}
				if (!Double.isNaN(dataVal)) {
					rms += Math.pow(dataVal - func, 2);
				}
			}

			String msg = StringUtils.join(new String[] {
					"	solvation ",
					Double.toString(solvation),
					"	correlation ",
					Double.toString(correlation),
					"	exponent ",
					Double.toString(exponent)
					},
					" : ");
			//System.out.println(msg);
			msg = StringUtils.join(new String[] {
					"RMS",
					Double.toString(-Math.log(rms))
					},
					" : ");
			//System.out.println(msg);
			return -Math.log(rms);
		}
		
	}

	public LogLogPlotData() {
		super();
		Pair<String, String> axesNames = SaxsAnalysisPlotType.LOGLOG_PLOT.getAxisNames();
		groupName = SaxsAnalysisPlotType.LOGLOG_PLOT.getGroupName();
		variableName = axesNames.getFirst();
		dataName = axesNames.getSecond();
	}
	
	@Override
	public double getDataValue(int idx, IDataset axis, IDataset data) {
		return Math.log10(data.getDouble(idx));
	}
	
	@Override
	public double getAxisValue(int idx, IDataset axis) {
		return Math.log10(axis.getDouble(idx));
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
			return err / val;
		}
		return Double.NaN;
	}
	
	public double[] getPorodPlotParameters(IDataset data, IDataset axis) {
		Dataset loglogData = getSaxsPlotDataset(data, axis);
		Dataset loglogAxis = getSaxsPlotAxis(axis);
		CMAESOptimizer optimizer = new CMAESOptimizer(
				cmaesMaxIterations,
				0.0,
				true,
				0,
				cmaesCheckFeasableCount,
				new Well19937a(),
				false,
				cmaesChecker);
		PorodLineFitFunction function = new PorodLineFitFunction(loglogData, loglogAxis);

		int dataSize = loglogAxis.getSize();
		double qMax = Math.pow(10.0, loglogAxis.getDouble(dataSize - 1));
		double i0 = Math.pow(10.0, loglogData.getDouble(0));
		double[] startPosition = new double[] { i0, 1.0 / qMax, 4.0 };
		double[] cmaesInputSigma = new double[] { 0.1 * i0 , 0.1 / qMax, 0.1 };
		double[] lb = new double[] { 0.0, 0.0, 0.0 };
		double[] ub = new double[] { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
		double[] params = new double[] { Double.NaN, Double.NaN, Double.NaN };
		try {
			final PointValuePair res = optimizer.optimize(new MaxEval(cmaesMaxIterations),
					new ObjectiveFunction(function),
					GoalType.MAXIMIZE,
					new CMAESOptimizer.PopulationSize(cmaesLambda),
					new CMAESOptimizer.Sigma(cmaesInputSigma),
					new SimpleBounds(lb, ub),
					new InitialGuess(startPosition));

			params = res.getPoint();
			double solvation = params[0];
			double correlation = params[1];
			double exponent = params[2];
			
			double rms = Math.exp(-function.value(params));
			
			System.out.println();
			System.out.println("Final Result");
			String msg = StringUtils.join(new String[] {
					"	solvation ",
					Double.toString(solvation),
					"	correlation ",
					Double.toString(correlation),
					"	exponent ",
					Double.toString(exponent)
					},
					" : ");
			System.out.println(msg);
			msg = StringUtils.join(new String[] {
					"RMS",
					Double.toString(rms)
					},
					" : ");
			System.out.println(msg);
			System.out.println();
			
		} catch (MaxCountExceededException e) {
			params = new double[] { Double.NaN, Double.NaN, Double.NaN };
		}
		return params;
	}
	
	public Dataset getFitData(double[] params, IDataset axis) {
		double solvation = params[0];
		double correlation = params[1];
		double exponent = params[2];
		
		Dataset loglogAxis = getSaxsPlotAxis(axis);
		Dataset result = DatasetFactory.zeros(loglogAxis.getShape(), Dataset.FLOAT32);
		for (int i = 0; i < loglogAxis.getSize(); i++) {
			double axisVal = Math.pow(10.0, loglogAxis.getDouble(i));
			double func = solvation / (1.0 + Math.pow(axisVal * correlation,  exponent));
			result.set(Math.log10(func), i);
		}
		
		return result;
	}
	
}
