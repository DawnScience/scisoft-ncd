/*
 * Copyright 2013, 2017 Diamond Light Source Ltd.
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

import javax.measure.Quantity;
import javax.measure.Unit;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;

public class NCDAbsoluteCalibration <V extends ScatteringVector<V>>{
	
	private double absScale;
	private double absScaleStdDev;
	
	private Dataset dataI, absI, calibratedI;
	private Dataset dataQ, absQ;
	private UnivariateFunction absInterpolate;
	
	private double qMin, qMax;
	
	public NCDAbsoluteCalibration() {
	}

	public double getAbsoluteScale() {
		return absScale;
	}

	public double getAbsScaleStdDev() {
		return absScaleStdDev;
	}

	public void setAbsoluteData(List<Quantity<V>> lstAbsQ, Dataset absI, Unit<V> unit) {
		absQ = DatasetFactory.zeros(DoubleDataset.class, lstAbsQ.size());
		for (int idx = 0; idx < lstAbsQ.size(); idx++) {
			Quantity<V> vec = lstAbsQ.get(idx);
			absQ.set(vec.to(unit).getValue().doubleValue(), idx);
			
		}
		this.absI = absI.clone();
		
		UnivariateInterpolator interpolator = new SplineInterpolator();
		absInterpolate = interpolator.interpolate((double[])absQ.getBuffer(),(double[])absI.getBuffer());
	}
	
	public void setData(List<Quantity<V>> lstDataQ, Dataset dataI, Dataset emptyI, Unit<V> unit) {
		dataQ = DatasetFactory.zeros(DoubleDataset.class, lstDataQ.size());
		for (int idx = 0; idx < lstDataQ.size(); idx++) {
			Quantity<V> vec = lstDataQ.get(idx);
			dataQ.set(vec.to(unit).getValue().doubleValue(), idx);
			
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
			if (dataI.getDouble(i) != 0) {
				stats.addValue(absInterpolate.value(qval) / dataI.getDouble(i));
			}
			else {
				System.out.println("throwing out data point at q = " + qval + " because dataI is zero, resulting in an infinite ratio");
			}
		}

		absScale = stats.getMean();
		absScaleStdDev = stats.getStandardDeviation();
		
		String msg = StringUtils.join(new String[] {
				"scale", Double.toString(absScale)
				},
				" : ");
		System.out.println(msg);
		
		System.out.println("NCD Absolute Instensity Scaler");
		System.out.println(Double.toString(absScale));
		System.out.println("standard deviation:" + Double.toString(absScaleStdDev));
		
		calibratedData(dataI);
	}
	
	public void calibratedData(Dataset data) {
		calibratedI = data.clone().imultiply(absScale);
	}
	
	public Dataset getCalibratedI() {
		return calibratedI;
	}
	
	public Dataset getAbsQ() {
		return absQ;
	}
	
	public Dataset getAbsI() {
		return absI;
	}
	
	public Dataset getDataQ() {
		return dataQ;
	}
}
