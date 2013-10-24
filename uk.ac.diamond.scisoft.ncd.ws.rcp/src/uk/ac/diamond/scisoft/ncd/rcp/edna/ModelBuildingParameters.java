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

package uk.ac.diamond.scisoft.ncd.rcp.edna;

import org.eclipse.ui.IMemento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.edna.views.NcdModelBuilderParametersMementoStrings;
import uk.ac.diamond.scisoft.ncd.rcp.edna.views.NcdModelBuilderParametersView;
import uk.ac.diamond.scisoft.ws.rcp.Activator;

public class ModelBuildingParameters {
	protected static final Logger logger = LoggerFactory.getLogger(ModelBuildingParameters.class);
			
	private String workingDirectory;
	private String dataFilename;
	private String pathToQ;
	private String pathToData;

	private int numberOfFrames;

	private boolean isMainUnitAngstrom = true;
	private double qMinAngstrom;
	private double qMaxAngstrom;
	private int firstPoint;
	private int lastPoint;

	private int numberOfThreads;

	private boolean gnomOnly; // if false, GNOM and DAMMIF are both run

	private double startDistanceAngstrom;
	private double endDistanceAngstrom;
	private int numberOfSearch;
	private double tolerance;

	private String symmetry;
	private boolean dammifFastMode;
	private boolean xAxisIsLog;
	
	public ModelBuildingParameters() {
	}

	public String getDataFilename() {
		return dataFilename;
	}
	public String getWorkingDirectory() {
		return workingDirectory;
	}

	public void setWorkingDirectory(String workingDirectory) {
		this.workingDirectory = workingDirectory;
	}

	public void setDataFilename(String dataFilename) {
		this.dataFilename = dataFilename;
	}
	public String getPathToQ() {
		return pathToQ;
	}
	public void setPathToQ(String pathToQ) {
		this.pathToQ = pathToQ;
	}
	public String getPathToData() {
		return pathToData;
	}
	public void setPathToData(String pathToData) {
		this.pathToData = pathToData;
	}
	public int getNumberOfFrames() {
		return numberOfFrames;
	}
	public void setNumberOfFrames(int numberOfFrames) {
		this.numberOfFrames = numberOfFrames;
	}
	public boolean isMainUnitAngstrom() {
		return isMainUnitAngstrom;
	}

	public void setMainUnitAngstrom(boolean isMainUnitAngstrom) {
		this.isMainUnitAngstrom = isMainUnitAngstrom;
	}

	public double getqMinAngstrom() {
		return qMinAngstrom;
	}
	public void setqMinAngstrom(double qMinAngstrom) {
		this.qMinAngstrom = qMinAngstrom;
	}
	public double getqMaxAngstrom() {
		return qMaxAngstrom;
	}
	public void setqMaxAngstrom(double qMaxAngstrom) {
		this.qMaxAngstrom = qMaxAngstrom;
	}
	
	public int getFirstPoint() {
		return firstPoint;
	}

	public void setFirstPoint(int firstPoint) {
		this.firstPoint = firstPoint;
	}

	public int getLastPoint() {
		return lastPoint;
	}

	public void setLastPoint(int lastPoint) {
		this.lastPoint = lastPoint;
	}

	public int getNumberOfThreads() {
		return numberOfThreads;
	}
	public void setNumberOfThreads(int numberOfThreads) {
		this.numberOfThreads = numberOfThreads;
	}
	public boolean isGnomOnly() {
		return gnomOnly;
	}
	public void setGnomOnly(boolean gnomOnly) {
		this.gnomOnly = gnomOnly;
	}
	public double getStartDistanceAngstrom() {
		return startDistanceAngstrom;
	}
	public void setStartDistanceAngstrom(double startDistanceAngstrom) {
		this.startDistanceAngstrom = startDistanceAngstrom;
	}
	public double getEndDistanceAngstrom() {
		return endDistanceAngstrom;
	}
	public void setEndDistanceAngstrom(double endDistanceAngstrom) {
		this.endDistanceAngstrom = endDistanceAngstrom;
	}

	public int getNumberOfSearch() {
		return numberOfSearch;
	}
	public void setNumberOfSearch(int numberOfSearch) {
		this.numberOfSearch = numberOfSearch;
	}
	public double getTolerance() {
		return tolerance;
	}
	public void setTolerance(double tolerance) {
		this.tolerance = tolerance;
	}
	public String getSymmetry() {
		return symmetry;
	}
	public void setSymmetry(String symmetry) {
		this.symmetry = symmetry;
	}
	public boolean isDammifFastMode() {
		return dammifFastMode;
	}
	public void setDammifFastMode(boolean dammifFastMode) {
		this.dammifFastMode = dammifFastMode;
	}
	
	public boolean isxAxisIsLog() {
		return xAxisIsLog;
	}

	public void setxAxisIsLog(boolean xAxisIsLog) {
		this.xAxisIsLog = xAxisIsLog;
	}

	@Override
	public String toString() {
		// return parameters for use by the EDNA plugin directly
		String commandLineParameters = "\"" + workingDirectory + "\" --data \"" + dataFilename + "\" " + 
				" --rMaxStart " + startDistanceAngstrom + " --rMaxStop " + endDistanceAngstrom +
				" --rMaxIntervals " + numberOfSearch + " --rMaxAbsTol " + tolerance + " --columns " + numberOfFrames + " --threads " + numberOfThreads +
				" --qmin " + qMinAngstrom + " --qmax " + qMaxAngstrom;
		if (!gnomOnly) {
			commandLineParameters += " --symmetry " + symmetry + " --mode " + (dammifFastMode ? "fast" : "slow");
		}
		else {
			commandLineParameters += " --onlyGnom";
		}
		if (dataFilename.endsWith(NcdModelBuilderParametersView.DATA_TYPES[1])) { //nxs format
			commandLineParameters += " --nxsQ \"" + pathToQ + "\" --nxsData \"" + pathToData + "\" ";
		}
		return commandLineParameters;
	}
	
	public boolean isValid() {
		if (!invalidMessage().isEmpty()) {
			return false;
		}
		return true;
	}
	
	public String invalidMessage() {
		String returnMessage = "";
		//TODO in the future, check file for validity of qMin, qMax, pathToQ, pathToData, but for now, basic checks will be fine
		if (workingDirectory.isEmpty()) {
			returnMessage += "Working directory must not be null" + System.lineSeparator();
		}

		if (qMinAngstrom >= qMaxAngstrom) {
			returnMessage += "qMax must be larger than qMin" + System.lineSeparator();
		}

		if (dataFilename.endsWith(NcdModelBuilderParametersView.DATA_TYPES[1])) { //nxs file
			if (pathToQ.isEmpty()) {
				returnMessage += "pathToQ must not be empty" + System.lineSeparator();
			}
	
			if (pathToData.isEmpty()) {
				returnMessage += "pathToData must not be empty" + System.lineSeparator();
			}
		}

		if (startDistanceAngstrom >= endDistanceAngstrom) {
			returnMessage += "Minimum distance must be less than maximum distance";
		}

		return returnMessage;
	}

	public void storeMementoParameters(IMemento memento) {
		if (memento != null) {
			memento.putString(NcdModelBuilderParametersMementoStrings.BIOSAXS_DATA_FILE, dataFilename);
			memento.putString(NcdModelBuilderParametersMementoStrings.BIOSAXS_WORKING_DIRECTORY, workingDirectory);
			memento.putString(NcdModelBuilderParametersMementoStrings.BIOSAXS_PATH_TO_Q, pathToQ);
			memento.putString(NcdModelBuilderParametersMementoStrings.BIOSAXS_PATH_TO_DATA, pathToData);
			memento.putInteger(NcdModelBuilderParametersMementoStrings.BIOSAXS_NUMBER_OF_FRAMES, numberOfFrames);
			memento.putBoolean(NcdModelBuilderParametersMementoStrings.BIOSAXS_IS_MAIN_UNIT_ANGSTROM, isMainUnitAngstrom);
			memento.putFloat(NcdModelBuilderParametersMementoStrings.BIOSAXS_Q_MIN, (float) qMinAngstrom);
			memento.putFloat(NcdModelBuilderParametersMementoStrings.BIOSAXS_Q_MAX, (float) qMaxAngstrom);
			memento.putInteger(NcdModelBuilderParametersMementoStrings.BIOSAXS_FIRST_POINT, firstPoint);
			memento.putInteger(NcdModelBuilderParametersMementoStrings.BIOSAXS_LAST_POINT, lastPoint);
			memento.putInteger(NcdModelBuilderParametersMementoStrings.BIOSAXS_NUMBER_THREADS, numberOfThreads);
			memento.putBoolean(NcdModelBuilderParametersMementoStrings.BIOSAXS_GNOM_ONLY, gnomOnly);
			memento.putFloat(NcdModelBuilderParametersMementoStrings.BIOSAXS_GNOM_MIN_DISTANCE, (float) startDistanceAngstrom);
			memento.putFloat(NcdModelBuilderParametersMementoStrings.BIOSAXS_GNOM_MAX_DISTANCE, (float) endDistanceAngstrom);
			memento.putInteger(NcdModelBuilderParametersMementoStrings.BIOSAXS_GNOM_NUMBER_SEARCH, numberOfSearch);
			memento.putFloat(NcdModelBuilderParametersMementoStrings.BIOSAXS_GNOM_TOLERANCE, (float) tolerance);
			memento.putString(NcdModelBuilderParametersMementoStrings.BIOSAXS_DAMMIF_SYMMETRY, symmetry);
			memento.putBoolean(NcdModelBuilderParametersMementoStrings.BIOSAXS_DAMMIF_FAST, dammifFastMode);
			memento.putBoolean(NcdModelBuilderParametersMementoStrings.BIOSAXS_ALLOW_USER_TO_CHANGE_PATHS, Activator.getDefault().getPreferenceStore().
					getBoolean(NcdPreferences.NCD_ALLOWSELECTIONOFPATHS));
			memento.putBoolean(NcdModelBuilderParametersMementoStrings.BIOSAXS_IS_LOG_LOG_PLOT, xAxisIsLog);
		}
	}
	
	public void loadMementoParameters(IMemento memento) {
		if (memento != null) {
			try {
				dataFilename = memento.getString(NcdModelBuilderParametersMementoStrings.BIOSAXS_DATA_FILE);
				workingDirectory = memento.getString(NcdModelBuilderParametersMementoStrings.BIOSAXS_WORKING_DIRECTORY);
				pathToQ  = memento.getString(NcdModelBuilderParametersMementoStrings.BIOSAXS_PATH_TO_Q);
				pathToData = memento.getString(NcdModelBuilderParametersMementoStrings.BIOSAXS_PATH_TO_DATA);
				numberOfFrames = memento.getInteger(NcdModelBuilderParametersMementoStrings.BIOSAXS_NUMBER_OF_FRAMES);
				isMainUnitAngstrom = memento.getBoolean(NcdModelBuilderParametersMementoStrings.BIOSAXS_IS_MAIN_UNIT_ANGSTROM);
				qMinAngstrom = memento.getFloat(NcdModelBuilderParametersMementoStrings.BIOSAXS_Q_MIN);
				qMaxAngstrom = memento.getFloat(NcdModelBuilderParametersMementoStrings.BIOSAXS_Q_MAX);
				firstPoint = memento.getInteger(NcdModelBuilderParametersMementoStrings.BIOSAXS_FIRST_POINT);
				lastPoint = memento.getInteger(NcdModelBuilderParametersMementoStrings.BIOSAXS_LAST_POINT);
				numberOfThreads = memento.getInteger(NcdModelBuilderParametersMementoStrings.BIOSAXS_NUMBER_THREADS);
				gnomOnly = memento.getBoolean(NcdModelBuilderParametersMementoStrings.BIOSAXS_GNOM_ONLY);
				startDistanceAngstrom = memento.getFloat(NcdModelBuilderParametersMementoStrings.BIOSAXS_GNOM_MIN_DISTANCE);
				endDistanceAngstrom = memento.getFloat(NcdModelBuilderParametersMementoStrings.BIOSAXS_GNOM_MAX_DISTANCE);
				numberOfSearch = memento.getInteger(NcdModelBuilderParametersMementoStrings.BIOSAXS_GNOM_NUMBER_SEARCH);
				tolerance = memento.getFloat(NcdModelBuilderParametersMementoStrings.BIOSAXS_GNOM_TOLERANCE);
				symmetry = memento.getString(NcdModelBuilderParametersMementoStrings.BIOSAXS_DAMMIF_SYMMETRY);
				dammifFastMode = memento.getBoolean(NcdModelBuilderParametersMementoStrings.BIOSAXS_DAMMIF_FAST);
				Activator.getDefault().getPreferenceStore().setValue(NcdPreferences.NCD_ALLOWSELECTIONOFPATHS,
						memento.getBoolean(NcdModelBuilderParametersMementoStrings.BIOSAXS_ALLOW_USER_TO_CHANGE_PATHS));
				xAxisIsLog = memento.getBoolean(NcdModelBuilderParametersMementoStrings.BIOSAXS_IS_LOG_LOG_PLOT);
			}
			catch (Exception e) {
				logger.error("problem while restoring state", e);
			}
		}
	}
}
