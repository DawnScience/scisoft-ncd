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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.integration.BaseAbstractUnivariateIntegrator;
import org.apache.commons.math3.analysis.integration.IterativeLegendreGaussIntegrator;
import org.apache.commons.math3.analysis.integration.UnivariateIntegrator;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.util.MathUtils;

public class DegreeOfOrientation {
	
	private static final int INTEGRATION_POINTS = 1000000;

	public Object[] process(Serializable buffer, Serializable axis, final int[] dimensions) {
		
		double[] parentaxis = (double[]) ConvertUtils.convert(axis, double[].class);
		float[] parentdata = (float[]) ConvertUtils.convert(buffer, float[].class);
		
		int size = dimensions[dimensions.length - 1];
		double[] myaxis = new double[size];
		double[] mydata = new double[size];
		double[] cos2data = new double[size];
		double[] sin2data = new double[size];
		double[] sincosdata = new double[size];
		
		for (int i = 0; i < parentaxis.length; i++) {
			myaxis[i] = Math.toRadians(parentaxis[i]);
			mydata[i] = parentdata[i];
			float cos2alpha = (float) Math.cos(2.0*myaxis[i]);
			float sin2alpha = (float) Math.sin(2.0*myaxis[i]);
			cos2data[i] = (1.0f + cos2alpha) * parentdata[i] / 2.0;
			sin2data[i] = (1.0f - cos2alpha) * parentdata[i] / 2.0;
			sincosdata[i] = sin2alpha * parentdata[i] / 2.0;
		}
		
		UnivariateInterpolator interpolator = new SplineInterpolator();
		UnivariateFunction function = interpolator.interpolate(myaxis, mydata);
		UnivariateFunction cos2Function = interpolator.interpolate(myaxis, cos2data);
		UnivariateFunction sin2Function = interpolator.interpolate(myaxis, sin2data);
		UnivariateFunction sincosFunction = interpolator.interpolate(myaxis, sincosdata);
		
		UnivariateIntegrator integrator = new IterativeLegendreGaussIntegrator(15,
				BaseAbstractUnivariateIntegrator.DEFAULT_RELATIVE_ACCURACY,
				BaseAbstractUnivariateIntegrator.DEFAULT_ABSOLUTE_ACCURACY);

		try {
			float cos2mean = (float) integrator.integrate(INTEGRATION_POINTS, cos2Function, myaxis[0], myaxis[myaxis.length - 1]);
			float sin2mean = (float) integrator.integrate(INTEGRATION_POINTS, sin2Function, myaxis[0], myaxis[myaxis.length - 1]);
			float sincosmean = (float) integrator.integrate(INTEGRATION_POINTS, sincosFunction, myaxis[0], myaxis[myaxis.length - 1]);
			float norm = (float) integrator.integrate(INTEGRATION_POINTS, function, myaxis[0], myaxis[myaxis.length - 1]);
			
			cos2mean /= norm;
			sin2mean /= norm;
			sincosmean /= norm;
			
			float result =  (float) Math.sqrt(Math.pow(cos2mean-sin2mean, 2) - 4.0*sincosmean*sincosmean);
			double angle = MathUtils.normalizeAngle(Math.atan2(2.0*sincosmean, cos2mean-sin2mean) / 2.0, Math.PI);
			
			return new Object[] { new float[] { result }, new double[] { Math.toDegrees(angle) } };
		} catch (TooManyEvaluationsException e) {
			return new Object[] { new float[] { Float.NaN }, new double[] { Double.NaN } };
		} catch (MaxCountExceededException e) {
			return new Object[] { new float[] { Float.NaN }, new double[] { Double.NaN } };
		}
	}
	
}

