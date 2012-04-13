/*-
 * Copyright © 2011 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd.calibration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.measure.quantity.Length;
import javax.measure.unit.Unit;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uncommons.maths.combinatorics.CombinationGenerator;

import uk.ac.diamond.scisoft.analysis.fitting.functions.APeak;
import uk.ac.diamond.scisoft.ncd.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.data.HKL;

public class CalibrationMethods {
	
	private static final Logger logger = LoggerFactory.getLogger(CalibrationMethods.class);
	
	private ArrayList<APeak> peaks;
	private LinkedHashMap<HKL, Amount<Length>> spacing;
	private double wavelength;
	private double pixelSize;
	private Integer maxIndex;
	private Unit<Length> unit;
	
    private double meanCameraLength, stdCameraLength;
    private double[] fitResult;
    LinkedHashMap<APeak, HKL> indexedPeaks;
    List<CalibrationPeak> indexedPeakList;
    
	SimpleRegression regression;

	public CalibrationMethods(ArrayList<APeak> peaks, LinkedHashMap<HKL, Amount<Length>> spacing, double wavelength,
			double pixelSize, int maxIndex, Unit<Length> unit) {
		
		this.peaks = peaks;
		this.spacing = spacing;
		this.wavelength = wavelength;
		this.pixelSize = pixelSize;
		this.maxIndex = maxIndex;
		this.unit = unit;
		
		this.fitResult = new double[2];
	}
	

	public List<CalibrationPeak> getIndexedPeakList() {
		return indexedPeakList;
	}


	public double[] getFitResult() {
		return fitResult;
	}


	public double getMeanCameraLength() {
		return meanCameraLength;
	}


	public double getStdCameraLength() {
		return stdCameraLength;
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
	        if (idx.getMaxIndex() <= maxIndex) {
	            double x = (wavelength / (2 * d));
	            if (x > 1) continue; // can't scatter beyond pi/2 as beyond resolution limit
	            twoTheta.put(idx, 2.0 * Math.asin(x));
	        }
	    }
	    return twoTheta;
	}
	   
	private void indexPeaks(LinkedHashMap<HKL, Double> twoTheta) {
		CombinationGenerator<HKL> combinations = new CombinationGenerator<HKL>(twoTheta.keySet(), peaks.size());
		Double minVar = Double.MAX_VALUE;
		for (List<HKL> comb : combinations) {
			ArrayList<Double> distance = new ArrayList<Double>();
			LinkedHashMap<APeak, HKL> tmpResult = new LinkedHashMap<APeak, HKL>();
			for (int i = 0; i < comb.size(); i++) {
				APeak peak = peaks.get(i); 
				HKL tmpHKL = comb.get(i); 
	            distance.add(peak.getPosition() / Math.tan(twoTheta.get(tmpHKL)));
	            tmpResult.put(peak, tmpHKL);
			}
			double var = StatUtils.variance(ArrayUtils.toPrimitive(distance.toArray(new Double[] {})));
			if (var > minVar)
				continue;
			indexedPeaks = tmpResult;
			minVar = var;
		}
		
		indexedPeakList = new ArrayList<CalibrationPeak>();
		for (Entry<APeak, HKL> peak : indexedPeaks.entrySet()) {
			double position = peak.getKey().getPosition();
			HKL idx = peak.getValue();
			Double angle = twoTheta.get(idx);
			indexedPeakList.add(new CalibrationPeak(position, angle, spacing.get(idx), idx.getIndeces()));
		}
	}
	
	private void fitFunctionToData(boolean intercept) {
		regression = new SimpleRegression(intercept);
		if (intercept)
			regression.addData(0.0, 0.0);
		for (Entry<APeak, HKL> peak : indexedPeaks.entrySet()) {
			double position = peak.getKey().getPosition();
	        double qVal = 2.0 * Math.PI / spacing.get(peak.getValue()).doubleValue(unit);
   			regression.addData(position, qVal);
		}
   		regression.regress();
   		fitResult = new double [] {regression.getIntercept(), regression.getSlope()};
	}
	
	private double[] estimateCameraLength() {
	    ArrayList<Double> cameraLen = new ArrayList<Double>();
		CombinationGenerator<Entry<APeak,HKL>> combinations = new CombinationGenerator<Entry<APeak,HKL>>(indexedPeaks.entrySet(), 2);
		for (List<Entry<APeak, HKL>> comb : combinations) {
			Entry<APeak, HKL> peak1 = comb.get(0);
			Entry<APeak, HKL> peak2 = comb.get(1);
			//double q1 = regression.predict(peak1.getPosition());
			//double q2 = regression.predict(peak2.getPosition());
	        double q1 = 2.0 * Math.PI / spacing.get(peak1.getValue()).doubleValue(unit);
	        double q2 = 2.0 * Math.PI / spacing.get(peak2.getValue()).doubleValue(unit);
			double dist = (peak2.getKey().getPosition() - peak1.getKey().getPosition()) * pixelSize * 2.0 * Math.PI / ((q2 - q1) * wavelength);
			cameraLen.add(dist);
		    //logger.info("Camera length from " + indexedPeaks.get(peak2).toString() + " and " + indexedPeaks.get(peak1).toString() + "is {} mm", dist);
		}
		double[] cameraLenArray = ArrayUtils.toPrimitive(cameraLen.toArray(new Double[] {}));
	    meanCameraLength = StatUtils.mean(cameraLenArray);
	    stdCameraLength = Math.sqrt(StatUtils.variance(cameraLenArray));
	    
	    logger.info("Camera length: {} +/- {} mm", meanCameraLength, stdCameraLength);
   	    return new double[] {meanCameraLength, stdCameraLength};
	}
	
	private double[] estimateCameraLengthSingle() {
	    ArrayList<Double> cameraLen = new ArrayList<Double>();
		for (Entry<APeak, HKL> peak : indexedPeaks.entrySet()) {
			double peakPos = peak.getKey().getPosition();
			double q = regression.predict(peakPos);
			double dist = (peakPos * pixelSize * 2.0 * Math.PI / (q * wavelength));
			cameraLen.add(dist);
		}
		double[] cameraLenArray = ArrayUtils.toPrimitive(cameraLen.toArray(new Double[] {}));
	    meanCameraLength = StatUtils.mean(cameraLenArray);
	    stdCameraLength = Math.sqrt(StatUtils.variance(cameraLenArray));
	    
	    logger.info("Camera length: {} +/- {} mm", meanCameraLength, stdCameraLength);
   	    return new double[] {meanCameraLength, stdCameraLength};
	}
	
	public double performCalibration(boolean intercept) {
		LinkedHashMap<HKL, Double> twoTheta = twoThetaAngles();
		indexPeaks(twoTheta);
		fitFunctionToData(intercept);
		//estimateCameraLengthSingle();
		estimateCameraLength();
		return Math.sqrt(regression.getSumSquaredErrors());
	}
}
