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

package uk.ac.diamond.scisoft.ncd.rcp.views;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.SimplePointChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.Well19937a;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;

public class NCDAbsoluteCalibration {
	
	private PolynomialFunction calibrationPolynomial;
	
	private AbstractDataset absQ, absI;
	private AbstractDataset dataQ, dataI;
	private UnivariateFunction absInterpolate;
	
	private int cmaesLambda = 5;
	private int cmaesMaxIterations = 10000000;
	private int cmaesCheckFeasableCount = 10;
	private ConvergenceChecker<PointValuePair> cmaesChecker;
	
	private double qMin, qMax;
	
	public NCDAbsoluteCalibration() {
		
		cmaesChecker = new SimplePointChecker<PointValuePair>(1e-6, 1e-8);
	}

	public PolynomialFunction getCalibrationPolynomial() {
		return calibrationPolynomial;
	}

	private class ResidualMultivariateFunction implements MultivariateFunction {
		
		private double calcError(AbstractDataset q, AbstractDataset data) {
			double res = 0.0;
			int values = 0;
			for (int i = 0; i < q.getSize(); i++) {
				double qVal = q.getDouble(i);
				if (qVal < qMin || qVal > qMax) {
					continue;
				}
				double fVal = data.getDouble(i);
				res += Math.pow(fVal - absInterpolate.value(qVal), 2);
				values++;
			}
			res = -Math.log(res / values);
			return res;
		}
		
		@Override
		public double value(double[] p) {
			PolynomialFunction scaler = new PolynomialFunction(p);
			
			ArrayList<Double> tmpData = new ArrayList<Double>();
			for (int i = 0; i < dataI.getSize(); i++) {
				double newDataI = scaler.value(dataI.getDouble(i));
				tmpData.add(newDataI);
			}
			
			AbstractDataset tmpDataset = new DoubleDataset(ArrayUtils.toPrimitive(tmpData.toArray(new Double[0])), tmpData.size());
			try {
				return calcError(dataQ, tmpDataset);
			} catch (Exception e) {
				return -Double.MAX_VALUE;
			}
		}
	}
	
	public void setAbsoluteData(AbstractDataset absQ, AbstractDataset absI) {
		this.absQ = absQ;
		this.absI = absI;
		
		UnivariateInterpolator interpolator = new SplineInterpolator();
		absInterpolate = interpolator.interpolate((double[])absQ.getBuffer(),(double[])absI.getBuffer());
	}
	
	public void setData(AbstractDataset dataQ, AbstractDataset dataI) {
		this.dataQ = dataQ;
		this.dataI = dataI;
	}

	
	private double[] calibrate(MultivariateFunction residual, double[] initP) {
		double[] cmaesInputSigma = new double[2];
		Arrays.fill(cmaesInputSigma, 1e1);
		CMAESOptimizer optimizer = new CMAESOptimizer(cmaesMaxIterations,
				0.0,
				true,
				0,
				cmaesCheckFeasableCount,
				new Well19937a(),
				false,
				cmaesChecker);
		final PointValuePair fit = optimizer.optimize(new MaxEval(cmaesMaxIterations),
				new ObjectiveFunction(residual),
				GoalType.MAXIMIZE,
				new CMAESOptimizer.PopulationSize(cmaesLambda),
				new CMAESOptimizer.Sigma(cmaesInputSigma),
				SimpleBounds.unbounded(2),
				new InitialGuess(initP));
		return fit.getPoint();
	}
	
	public AbstractDataset calibrate() {
		qMin = Math.max(absQ.min().doubleValue(), dataQ.min().doubleValue());
		qMax = Math.min(absQ.max().doubleValue(), dataQ.max().doubleValue());
		
		int dataQStart = DatasetUtils.findIndexGreaterThanOrEqualTo(dataQ, qMin);
		int dataQStop = DatasetUtils.findIndexGreaterThanOrEqualTo(dataQ, qMax);
		int absQStart = DatasetUtils.findIndexGreaterThanOrEqualTo(absQ, qMin);
		int absQStop = DatasetUtils.findIndexGreaterThanOrEqualTo(absQ, qMax);
		
		double abs1 = absI.getDouble(absQStart); 
		double abs2 = absI.getDouble(absQStop); 
		double dat1 = dataI.getDouble(dataQStart); 
		double dat2 = dataI.getDouble(dataQStop); 
		double a = (abs1 - abs2) / (dat1 - dat2);
		double b = (dat1*abs2 - dat2*abs1) / (dat1 - dat2);
		
		double[] fitInitP = new double[] {b, a};
		ResidualMultivariateFunction residual = new ResidualMultivariateFunction();
		double[] initP = calibrate(residual, fitInitP);
		
		calibrationPolynomial = new PolynomialFunction(initP);
		
		System.out.println("NCD Absolute Instensity Calibration Function");
		System.out.println(calibrationPolynomial.toString());
		residual.value(calibrationPolynomial.getCoefficients());
		
		return calibratedData(dataI);
	}
	
	public AbstractDataset calibratedData(AbstractDataset data) {
		final int size = data.getSize();
		double[] tmpData = new double[size];
		for (int i = 0; i < size; i++)
			tmpData[i] = calibrationPolynomial.value(data.getDouble(i));
		return new DoubleDataset(tmpData, new int[] {size});
	}
}
