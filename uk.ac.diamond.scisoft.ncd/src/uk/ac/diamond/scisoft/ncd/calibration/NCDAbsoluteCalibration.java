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

package uk.ac.diamond.scisoft.ncd.calibration;

import java.util.List;

import javax.measure.unit.Unit;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;

public class NCDAbsoluteCalibration {
	
	private double absScale;
	
	private AbstractDataset dataI, absI, calibratedI;
	private AbstractDataset dataQ, absQ;
	private UnivariateFunction absInterpolate;
	
	private double qMin, qMax;
	
	public NCDAbsoluteCalibration() {
	}

	public double getAbsoluteScale() {
		return absScale;
	}

	public void setAbsoluteData(List<Amount<ScatteringVector>> lstAbsQ, AbstractDataset absI, Unit<ScatteringVector> unit) {
		absQ = new DoubleDataset(lstAbsQ.size());
		for (int idx = 0; idx < lstAbsQ.size(); idx++) {
			Amount<ScatteringVector> vec = lstAbsQ.get(idx);
			absQ.set(vec.doubleValue(unit), idx);
			
		}
		this.absI = absI.clone();
		
		UnivariateInterpolator interpolator = new SplineInterpolator();
		absInterpolate = interpolator.interpolate((double[])absQ.getBuffer(),(double[])absI.getBuffer());
	}
	
	public void setData(List<Amount<ScatteringVector>> lstDataQ, AbstractDataset dataI, AbstractDataset emptyI, Unit<ScatteringVector> unit) {
		dataQ = new DoubleDataset(lstDataQ.size());
		for (int idx = 0; idx < lstDataQ.size(); idx++) {
			Amount<ScatteringVector> vec = lstDataQ.get(idx);
			dataQ.set(vec.doubleValue(unit), idx);
			
		}
		this.dataI = dataI.clone();
		this.dataI.isubtract(emptyI);
	}

	
	public void calibrate() {
		qMin = Math.max(absQ.min().doubleValue(), dataQ.min().doubleValue());
		qMax = Math.min(absQ.max().doubleValue(), dataQ.max().doubleValue());
		if (!(qMin < qMax)) {
			throw new IllegalArgumentException("No calibration data found for the selected scattering vector range");
		}
		
		int dataQStart = Math.min(dataQ.getSize() - 1, DatasetUtils.findIndexGreaterThanOrEqualTo(dataQ, qMin));
		int dataQStop = Math.min(dataQ.getSize() - 1, DatasetUtils.findIndexGreaterThanOrEqualTo(dataQ, qMax));
		
		SummaryStatistics stats = new SummaryStatistics();
		for (int i = dataQStart; i <= dataQStop; i++) {
			double qval = dataQ.getDouble(i);
			stats.addValue(absInterpolate.value(qval) / dataI.getDouble(i));
		}

		absScale = stats.getMean();
		
		String msg = StringUtils.join(new String[] {
				"scale", Double.toString(absScale)
				},
				" : ");
		System.out.println(msg);
		
		System.out.println("NCD Absolute Instensity Scaler");
		System.out.println(Double.toString(absScale));
		
		calibratedData(dataI);
	}
	
	public void calibratedData(AbstractDataset data) {
		calibratedI = data.clone().imultiply(absScale);
	}
	
	public AbstractDataset getCalibratedI() {
		return calibratedI;
	}
	
	public AbstractDataset getAbsQ() {
		return absQ;
	}
	
	public AbstractDataset getAbsI() {
		return absI;
	}
	
	public AbstractDataset getDataQ() {
		return dataQ;
	}
}
