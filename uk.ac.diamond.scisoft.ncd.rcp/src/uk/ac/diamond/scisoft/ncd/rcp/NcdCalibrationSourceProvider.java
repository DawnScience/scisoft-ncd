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
import org.eclipse.ui.ISources;

import uk.ac.diamond.scisoft.analysis.fitting.functions.AFunction;
import uk.ac.diamond.scisoft.ncd.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.data.NcdDetectorSettings;

public class NcdCalibrationSourceProvider extends AbstractSourceProvider {

	public final static String CALIBRATION_STATE = "uk.ac.diamond.scisoft.ncd.rcp.calibrationBean";
	public final static String NCDDETECTORS_STATE = "uk.ac.diamond.scisoft.ncd.rcp.ncdDetectors";
	
	private CalibrationResultsBean calibrationResults; 
	private HashMap<String, NcdDetectorSettings> ncdDetectors;

	public NcdCalibrationSourceProvider() {
		calibrationResults = new CalibrationResultsBean();
		ncdDetectors = new HashMap<String, NcdDetectorSettings>();
	}

	@Override
	public void dispose() {
		calibrationResults.clearAllData();
		ncdDetectors.clear();
	}

	@Override
	public Map<String, Object> getCurrentState() {
		Map<String, Object> currentState = new HashMap<String, Object>();
		currentState.put(CALIBRATION_STATE, calibrationResults);
		currentState.put(NCDDETECTORS_STATE, ncdDetectors);
		return currentState;
	}

	@Override
	public String[] getProvidedSourceNames() {
		return new String[] {CALIBRATION_STATE,
                			NCDDETECTORS_STATE};
	}

	public CalibrationResultsBean getCalibrationResults() {
		return calibrationResults;
	}

	public void putCalibrationResult(CalibrationResultsBean crb) {
		for (String experiment : crb.keySet()) {
			AFunction calibrationFunction = crb.getFunction(experiment);
			List<CalibrationPeak> peaks = crb.getPeakList(experiment);
			Double meanCameraLength = crb.getMeanCameraLength(experiment);
			String unit = crb.getUnit(experiment);
			calibrationResults.putCalibrationResult(experiment, calibrationFunction, peaks, meanCameraLength, unit);
		}
		
		fireSourceChanged(ISources.WORKBENCH, CALIBRATION_STATE, crb);
	}
	
	public AFunction getFunction(String experiment) {
		return calibrationResults.getFunction(experiment);
	}
	
	public Double getMeanCameraLength(String experiment) {
		return calibrationResults.getMeanCameraLength(experiment);
	}
	
	public ArrayList<CalibrationPeak> getPeakList(String experiment) {
		return calibrationResults.getPeakList(experiment);
	}
	
	public String getUnit(String experiment) {
		return calibrationResults.getUnit(experiment);
	}

	public void addNcdDetector(NcdDetectorSettings ncdDetector) {
		ncdDetectors.put(ncdDetector.getName(), new NcdDetectorSettings(ncdDetector));
		fireSourceChanged(ISources.WORKBENCH, NCDDETECTORS_STATE, ncdDetectors);
	}

	public HashMap<String, NcdDetectorSettings> getNcdDetectors() {
		return ncdDetectors;
	}
}
