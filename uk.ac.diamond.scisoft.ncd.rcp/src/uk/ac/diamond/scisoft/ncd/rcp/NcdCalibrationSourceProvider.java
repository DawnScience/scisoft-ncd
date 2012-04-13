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

package uk.ac.diamond.scisoft.ncd.rcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ui.AbstractSourceProvider;

import uk.ac.diamond.scisoft.analysis.fitting.functions.AFunction;
import uk.ac.diamond.scisoft.ncd.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;

public class NcdCalibrationSourceProvider extends AbstractSourceProvider {

	public final static String CALIBRATION_STATE = "uk.ac.diamond.scisoft.ncd.rcp.calibrationBean";
	
	static CalibrationResultsBean calibrationResults; 

	public NcdCalibrationSourceProvider() {
		calibrationResults = new CalibrationResultsBean();
	}

	@Override
	public void dispose() {

	}

	@Override
	public Map<String, CalibrationResultsBean> getCurrentState() {
		Map<String, CalibrationResultsBean> currentState = new HashMap<String, CalibrationResultsBean>();
		currentState.put(CALIBRATION_STATE, calibrationResults);
		return currentState;
	}

	@Override
	public String[] getProvidedSourceNames() {
		return new String[] {CALIBRATION_STATE};
	}

	public void putCalibrationResult(CalibrationResultsBean crb) {
		for (String experiment : crb.keySet()) {
			AFunction calibrationFunction = crb.getFuction(experiment);
			List<CalibrationPeak> peaks = crb.getPeakList(experiment);
			double meanCameraLength = crb.getMeanCameraLength(experiment);
			String unit = crb.getUnit(experiment);
			calibrationResults.putCalibrationResult(experiment, calibrationFunction, peaks, meanCameraLength, unit);
		}
	}
	
	public static AFunction getFunction(String experiment) {
		return calibrationResults.getFuction(experiment);
	}
	
	public static double getMeanCameraLength(String experiment) {
		return calibrationResults.getMeanCameraLength(experiment);
	}
	
	public static ArrayList<CalibrationPeak> getPeakList(String experiment) {
		return calibrationResults.getPeakList(experiment);
	}
	
	public static String getUnit(String experiment) {
		return calibrationResults.getUnit(experiment);
	}

}
