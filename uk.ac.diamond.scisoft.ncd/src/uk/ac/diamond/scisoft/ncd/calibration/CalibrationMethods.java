/*
 * Copyright 2012 Diamond Light Source Ltd.
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

import javax.measure.quantity.Length;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uncommons.maths.combinatorics.CombinationGenerator;

import uk.ac.diamond.scisoft.analysis.crystallography.HKL;
import uk.ac.diamond.scisoft.analysis.fitting.functions.IPeak;
import uk.ac.diamond.scisoft.ncd.data.CalibrationPeak;

public class CalibrationMethods {
	
	private static final Logger logger = LoggerFactory.getLogger(CalibrationMethods.class);
	
	private ArrayList<IPeak> peaks;
	private LinkedHashMap<HKL, Amount<Length>> spacing;
	private double wavelength;
	private double pixelSize;
	private Unit<Length> unit;
	
    Amount<Length> meanCameraLength;

    private double[] fitResult;
    private List<CalibrationPeak> indexedPeakList;
    
	SimpleRegression regression;

	public CalibrationMethods(ArrayList<IPeak> peaks, LinkedHashMap<HKL, Amount<Length>> spacing, double wavelength,
			double pixelSize, Unit<Length> unit) {
		
		this.peaks = peaks;
		this.spacing = spacing;
		this.wavelength = wavelength;
		this.pixelSize = pixelSize;
		this.unit = unit;
		
		this.fitResult = new double[2];
	}
	

	public List<CalibrationPeak> getIndexedPeakList() {
		return indexedPeakList;
	}


	public double[] getFitResult() {
		return fitResult;
	}


	public Amount<Length> getMeanCameraLength() {
		return meanCameraLength;
	}

	/**
	 *   Calculates the bragg angle for a given reflection at a given wavelength.
	 *   The order of the bragg scattering is also calculated
	 */
	private LinkedHashMap<HKL, Double> twoThetaAngles() {
		LinkedHashMap<HKL, Double> twoTheta = new LinkedHashMap<HKL, Double>();
	    for ( Entry<HKL, Amount<Length>> val : spacing.entrySet()) {
			HKL idx = val.getKey();
	    	double d = val.getValue().doubleValue(unit);
            double x = (wavelength / (2 * d));
            if (x > 1) continue; // can't scatter beyond pi/2 as beyond resolution limit
            twoTheta.put(idx, 2.0 * Math.asin(x));
	    }
	    return twoTheta;
	}
	   
	private LinkedHashMap<IPeak, HKL> indexPeaks(LinkedHashMap<HKL, Double> twoTheta) {
		LinkedHashMap<IPeak, HKL> indexedPeaks = new LinkedHashMap<IPeak, HKL>(peaks.size());
		CombinationGenerator<HKL> combinations = new CombinationGenerator<HKL>(twoTheta.keySet(), peaks.size());
		Double minVar = Double.MAX_VALUE;
		for (List<HKL> comb : combinations) {
			ArrayList<Double> distance = new ArrayList<Double>();
			LinkedHashMap<IPeak, HKL> tmpResult = new LinkedHashMap<IPeak, HKL>();
			for (int i = 0; i < comb.size(); i++) {
				IPeak peak = peaks.get(i); 
				HKL tmpHKL = comb.get(i); 
	            distance.add(peak.getPosition() / Math.tan(twoTheta.get(tmpHKL)));
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
			Double angle = twoTheta.get(idx);
			indexedPeakList.add(new CalibrationPeak(position, angle, spacing.get(idx), idx.getIndices()));
		}
		
		return indexedPeaks;
	}
	
	private double fitFunctionToData(LinkedHashMap<IPeak, HKL> peaks, boolean intercept) {
		regression = new SimpleRegression(intercept);
		if (intercept)
			regression.addData(0.0, 0.0);
		for (Entry<IPeak, HKL> peak : peaks.entrySet()) {
			double position = peak.getKey().getPosition();
	        double qVal = 2.0 * Math.PI / spacing.get(peak.getValue()).doubleValue(unit);
   			regression.addData(position, qVal);
		}
   		regression.regress();
   		fitResult = new double [] {regression.getIntercept(), regression.getSlope()/pixelSize};
   		return regression.getSumSquaredErrors();
	}
	
	@SuppressWarnings("unused")
	private Amount<Length> estimateCameraLength(LinkedHashMap<IPeak, HKL> indexedPeaks) {
	    ArrayList<Double> cameraLen = new ArrayList<Double>();
		CombinationGenerator<Entry<IPeak,HKL>> combinations = new CombinationGenerator<Entry<IPeak,HKL>>(indexedPeaks.entrySet(), 2);
		for (List<Entry<IPeak, HKL>> comb : combinations) {
			Entry<IPeak, HKL> peak1 = comb.get(0);
			Entry<IPeak, HKL> peak2 = comb.get(1);
			//double q1 = regression.predict(peak1.getPosition());
			//double q2 = regression.predict(peak2.getPosition());
	        double q1 = 2.0 * Math.PI / spacing.get(peak1.getValue()).doubleValue(unit);
	        double q2 = 2.0 * Math.PI / spacing.get(peak2.getValue()).doubleValue(unit);
			double dist = (peak2.getKey().getPosition() - peak1.getKey().getPosition()) * pixelSize * 2.0 * Math.PI / ((q2 - q1) * wavelength);
			cameraLen.add(dist);
		    //logger.info("Camera length from " + indexedPeaks.get(peak2).toString() + " and " + indexedPeaks.get(peak1).toString() + "is {} mm", dist);
		}
		double[] cameraLenArray = ArrayUtils.toPrimitive(cameraLen.toArray(new Double[] {}));
	    double mcl = StatUtils.mean(cameraLenArray);
	    double std = Math.sqrt(StatUtils.variance(cameraLenArray));
	    meanCameraLength = Amount.valueOf(mcl, std, SI.MILLIMETER);
	    
	    logger.info("Camera length: {}", meanCameraLength.to(SI.METER));
   	    return meanCameraLength;
	}
	
	private Amount<Length> estimateCameraLengthSingle(LinkedHashMap<IPeak, HKL> indexedPeaks) {
	    ArrayList<Double> cameraLen = new ArrayList<Double>();
		for (Entry<IPeak, HKL> peak : indexedPeaks.entrySet()) {
			double peakPos = peak.getKey().getPosition();
			double q = regression.predict(peakPos);
			double dist = (peakPos * pixelSize * 2.0 * Math.PI / (q * wavelength));
			cameraLen.add(dist);
		}
		double[] cameraLenArray = ArrayUtils.toPrimitive(cameraLen.toArray(new Double[] {}));
	    double mcl = StatUtils.mean(cameraLenArray);
	    double std = Math.sqrt(StatUtils.variance(cameraLenArray));
	    meanCameraLength = Amount.valueOf(mcl, std, SI.MILLIMETER);
	    
	    logger.info("Camera length: {}", meanCameraLength.to(SI.METER));
   	    return meanCameraLength;
	}
	
	public double performCalibration(boolean intercept) {
		LinkedHashMap<HKL, Double> twoTheta = twoThetaAngles();
		LinkedHashMap<IPeak, HKL> indexedPeaks = indexPeaks(twoTheta);
		double error = fitFunctionToData(indexedPeaks, intercept);
		estimateCameraLengthSingle(indexedPeaks);
		//estimateCameraLength(indexedPeaks);
		return error;
	}
}
