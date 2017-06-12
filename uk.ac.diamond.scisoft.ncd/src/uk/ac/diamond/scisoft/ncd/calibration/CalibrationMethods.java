/*
 * Copyright 2012, 2017 Diamond Light Source Ltd.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Angle;
import javax.measure.quantity.Length;
import javax.measure.spi.ServiceProvider;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.eclipse.dawnsci.analysis.api.fitting.functions.IPeak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uncommons.maths.combinatorics.CombinationGenerator;

import si.uom.SI;
import tec.units.ri.quantity.Quantities;
import tec.units.ri.unit.MetricPrefix;
import tec.units.ri.unit.Units;
import si.uom.NonSI;

import uk.ac.diamond.scisoft.analysis.crystallography.CalibrantSpacing;
import uk.ac.diamond.scisoft.analysis.crystallography.HKL;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationPeak;

public class CalibrationMethods <V extends ScatteringVector<V>, D extends ScatteringVectorOverDistance<D>> {
	
	private static final Logger logger = LoggerFactory.getLogger(CalibrationMethods.class);
	
	private List<IPeak> peaks;
	private CalibrantSpacing spacing;
	private Quantity<Length> wavelength;
	private Quantity<Length> pixelSize;
	private Unit<Length> unit;
    private Unit<D> unitGradient; 
    private Unit<V> unitIntercept; 
	
    private Quantity<Length> meanCameraLength;

    public Quantity<D> gradient; 
    public Quantity<V> intercept;
    
    private List<CalibrationPeak> indexedPeakList;
    
	private SimpleRegression regression;

	private static final Unit<Length> MILLIMETRE = MetricPrefix.MILLI(Units.METRE);
	private static final Unit<Length> LENGTH_UNIT = ServiceProvider.current().getQuantityFactory(Length.class).getSystemUnit();

	public CalibrationMethods(List<IPeak> peaks, CalibrantSpacing calibrantSpacing, Quantity<Length> wavelength,
			Quantity<Length> pixelSize, Unit<Length> unit) {
		
		this.peaks = peaks;
		this.spacing = calibrantSpacing;
		this.wavelength = wavelength;
		this.pixelSize = pixelSize;
		this.unit = unit;
		this.unitGradient = unit.inverse().divide(MILLIMETRE).asType(ScatteringVectorOverDistance.class);
		this.unitIntercept = unit.inverse().asType(ScatteringVector.class);
	}
	

	public List<CalibrationPeak> getIndexedPeakList() {
		return indexedPeakList;
	}

	public Quantity<D> getGradient() {
		return gradient;
	}


	public Quantity<V> getIntercept() {
		return intercept;
	}


	public Quantity<Length> getMeanCameraLength() {
		return meanCameraLength;
	}

	/**
	 *   Calculates the bragg angle for a given reflection at a given wavelength.
	 *   The order of the bragg scattering is also calculated
	 */
	private LinkedHashMap<HKL, Quantity<Angle>> twoThetaAngles() {
		LinkedHashMap<HKL, Quantity<Angle>> twoTheta = new LinkedHashMap<HKL, Quantity<Angle>>();
		for (HKL idx : spacing.getHKLs()) {
			Quantity<Length> d = idx.getD();
			double x = wavelength.to(unit).getValue().doubleValue() / (2.0 * d.to(unit).getValue().doubleValue());
			if (x > 1)
				continue; // can't scatter beyond pi/2
			twoTheta.put(idx, Quantities.getQuantity(2.0 * Math.asin(x), SI.RADIAN));
	    }
	    return twoTheta;
	}
	   
	private LinkedHashMap<IPeak, HKL> indexPeaks(LinkedHashMap<HKL, Quantity<Angle>> twoTheta) {
		LinkedHashMap<IPeak, HKL> indexedPeaks = new LinkedHashMap<IPeak, HKL>(peaks.size());
		CombinationGenerator<HKL> combinations = new CombinationGenerator<HKL>(twoTheta.keySet(), peaks.size());
		Double minVar = Double.MAX_VALUE;
		for (List<HKL> comb : combinations) {
			ArrayList<Double> distance = new ArrayList<Double>();
			LinkedHashMap<IPeak, HKL> tmpResult = new LinkedHashMap<IPeak, HKL>();
			for (int i = 0; i < comb.size(); i++) {
				IPeak peak = peaks.get(i); 
				HKL tmpHKL = comb.get(i); 
	            distance.add(peak.getPosition() / Math.tan(twoTheta.get(tmpHKL).to(SI.RADIAN).getValue().doubleValue()));
	            tmpResult.put(peak, tmpHKL);
			}
			double var = fitFunctionToData(tmpResult, false);
			if (var > minVar)
				continue;
			indexedPeaks = tmpResult;
			minVar = var;
		}
		
		indexedPeakList = new ArrayList<CalibrationPeak>();
		for (Entry<IPeak, HKL> peak : indexedPeaks.entrySet()) {
			double position = peak.getKey().getPosition();
			HKL idx = peak.getValue();
			Quantity<Angle> angle = twoTheta.get(idx);
			indexedPeakList.add(new CalibrationPeak(position, angle, idx));
		}
		
		return indexedPeaks;
	}
	
	private double fitFunctionToData(LinkedHashMap<IPeak, HKL> peaks, boolean hasIntercept) {
		regression = new SimpleRegression(hasIntercept);
		if (hasIntercept) {
			regression.addData(0.0, 0.0);
		}
		for (Entry<IPeak, HKL> peak : peaks.entrySet()) {
			double position = peak.getKey().getPosition();
	        double qVal = 2.0 * Math.PI / peak.getValue().getD().to(unit).getValue().doubleValue();
   			regression.addData(position, qVal);
		}
   		regression.regress();
   		
		gradient = Quantity.valueOf(regression.getSlope(), regression.getSlopeStdErr(), unit.inverse()).divide(pixelSize)
				.to(unit.inverse().divide(pixelSize.getUnit()).asType(ScatteringVectorOverDistance.class));
		intercept = Quantity.valueOf(regression.getIntercept(), regression.getInterceptStdErr(), unit.inverse()).to(
				unit.inverse().asType(ScatteringVector.class));
   		//fitResult = new double [] {regression.getIntercept(), regression.getSlope()/pixelSize.doubleValue(SI.MILLIMETRE)};
	    logger.info("Gradient: {},  Intercept: {}", gradient.to(unitGradient).getValue().doubleValue(), intercept.to(unitIntercept).getValue().doubleValue());
   		return regression.getSumSquaredErrors();
	}
	
	@SuppressWarnings("unused")
	private Quantity<Length> estimateCameraLength(LinkedHashMap<IPeak, HKL> indexedPeaks) {
	    ArrayList<Double> cameraLen = new ArrayList<Double>();
		CombinationGenerator<Entry<IPeak,HKL>> combinations = new CombinationGenerator<Entry<IPeak,HKL>>(indexedPeaks.entrySet(), 2);
		for (List<Entry<IPeak, HKL>> comb : combinations) {
			Entry<IPeak, HKL> peak1 = comb.get(0);
			Entry<IPeak, HKL> peak2 = comb.get(1);
			//double q1 = regression.predict(peak1.getPosition());
			//double q2 = regression.predict(peak2.getPosition());
			Quantity<V> q1 = Constants.two_π.divide(peak1.getValue().getD()).to(ScatteringVector.UNIT);
			Quantity<V> q2 = Constants.two_π.divide(peak2.getValue().getD()).to(ScatteringVector.UNIT);
			Quantity<Length> dist = pixelSize.multiply(peak2.getKey().getPosition() - peak1.getKey().getPosition())
					.times(Constants.two_π).divide(wavelength.multiply(q2.subtract(q1))).to(LENGTH_UNIT);
			cameraLen.add(dist.to(MILLIMETRE).getValue().doubleValue());
		    //logger.info("Camera length from " + indexedPeaks.get(peak2).toString() + " and " + indexedPeaks.get(peak1).toString() + "is {} mm", dist);
		}
		double[] cameraLenArray = ArrayUtils.toPrimitive(cameraLen.toArray(new Double[] {}));
	    double mcl = StatUtils.mean(cameraLenArray);
	    double std = Math.sqrt(StatUtils.variance(cameraLenArray));
	    meanCameraLength = Quantity.valueOf(mcl, std, MILLIMETRE);
	    
	    logger.info("Camera length: {}", meanCameraLength.to(Units.METRE));
   	    return meanCameraLength;
	}
	
	private Quantity<Length> estimateCameraLengthSingle(LinkedHashMap<IPeak, HKL> indexedPeaks) {
	    ArrayList<Double> cameraLen = new ArrayList<Double>();
		for (Entry<IPeak, HKL> peak : indexedPeaks.entrySet()) {
			double peakPos = peak.getKey().getPosition();
			Quantity<V> q = Quantity.valueOf(regression.predict(peakPos), unit.inverse())
					.to(ScatteringVector.UNIT);
			Quantity<Length> dist = pixelSize.multiply(peakPos).times(Constants.two_π).divide(wavelength.multiply(q))
					.to(LENGTH_UNIT);
			cameraLen.add(dist.to(MILLIMETRE).getValue().doubleValue());
		}
		double[] cameraLenArray = ArrayUtils.toPrimitive(cameraLen.toArray(new Double[] {}));
	    double mcl = StatUtils.mean(cameraLenArray);
	    double std = Math.sqrt(StatUtils.variance(cameraLenArray));
	    meanCameraLength = Quantity.valueOf(mcl, std, MILLIMETRE);
	    
	    logger.info("Camera length: {}", meanCameraLength.to(Units.METRE));
   	    return meanCameraLength;
	}
	
	public double performCalibration(boolean intercept) {
		LinkedHashMap<HKL, Quantity<Angle>> twoTheta = twoThetaAngles();
		LinkedHashMap<IPeak, HKL> indexedPeaks = indexPeaks(twoTheta);
		double error = fitFunctionToData(indexedPeaks, intercept);
		estimateCameraLengthSingle(indexedPeaks);
		//estimateCameraLength(indexedPeaks);
		return error;
	}
}
