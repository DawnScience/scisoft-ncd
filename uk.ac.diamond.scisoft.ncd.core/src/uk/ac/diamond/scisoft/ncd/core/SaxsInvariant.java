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

package uk.ac.diamond.scisoft.ncd.core;

import java.io.Serializable;

import javax.measure.quantity.Dimensionless;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.BaseAbstractUnivariateIntegrator;
import org.apache.commons.math3.analysis.integration.IterativeLegendreGaussIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.data.plots.PorodPlotData;

public class SaxsInvariant {
	
	private static final int INTEGRATION_POINTS = 1000000;

	public Object[] process(Serializable buffer, Serializable errors, Serializable axis, final int[] dimensions) {
		
		double[] parentaxis = (double[]) ConvertUtils.convert(axis, double[].class);
		float[] parentdata = (float[]) ConvertUtils.convert(buffer, float[].class);
		double[] parenterrors = (double[]) ConvertUtils.convert(errors, double[].class);
		
		int shift = (parentaxis[0] > 0 ? 1 : 0);
		int size = dimensions[dimensions.length - 1] + shift;
		double[] myaxis = new double[size];
		double[] mydata = new double[size];
		double[] myerrors = new double[size];
		
		if (shift > 0) {
			myaxis[0] = 0.0;
			mydata[0] = 0.0;
			myerrors[0] = 0.0;
		}
		
		for (int i = 0; i < parentaxis.length; i++) {
			myaxis[i + shift] = parentaxis[i];
			mydata[i + shift] = parentdata[i] * parentaxis[i] * parentaxis[i];
			myerrors[i + shift] = parenterrors[i] * Math.pow(parentaxis[i], 4);
		}
		
		UnivariateInterpolator interpolator = new SplineInterpolator();
		UnivariateFunction function = interpolator.interpolate(myaxis, mydata);
		
		UnivariateIntegrator integrator = new IterativeLegendreGaussIntegrator(15,
				BaseAbstractUnivariateIntegrator.DEFAULT_RELATIVE_ACCURACY,
				BaseAbstractUnivariateIntegrator.DEFAULT_ABSOLUTE_ACCURACY);

		try {
			float result = (float) integrator.integrate(INTEGRATION_POINTS, function, 0.0, myaxis[myaxis.length - 1]);

			IDataset data = DatasetFactory.createFromObject(parentdata, dimensions);
			IDataset qaxis = DatasetFactory.createFromObject(parentaxis, dimensions);
			PorodPlotData porodPlotData = (PorodPlotData) SaxsAnalysisPlotType.POROD_PLOT.getSaxsPlotDataObject();
			SimpleRegression regression = porodPlotData.getPorodPlotParameters(data.squeeze(), qaxis.squeeze());
			Amount<Dimensionless> c4 = porodPlotData.getC4(regression);

			result += (float) (c4.getEstimatedValue() / myaxis[myaxis.length - 1]);
			
			double error = 0.0;
			for (int i = 0; i < myaxis.length; i++) {
				int idx1 = Math.max(0, i - 1);
				int idx2 = Math.min(myaxis.length - 1, i + 1);
				error += Math.pow((myaxis[idx2] - myaxis[idx1]), 2) * myerrors[i] / 4.0;
			}
			error += Math.pow(c4.getAbsoluteError() / myaxis[myaxis.length - 1], 2);
			
			return new Object[] { new float[] { result }, new double[] {error} };
		} catch (TooManyEvaluationsException e) {
			return new Object[] { new float[] { Float.NaN }, new double[] { Double.NaN } };
		} catch (MaxCountExceededException e) {
			return new Object[] { new float[] { Float.NaN }, new double[] { Double.NaN } };
		}
	}
	
}

