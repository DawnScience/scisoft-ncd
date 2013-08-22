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

public class ModelBuildingParameters {
	private String workingDirectory;
	private String htmlResultsDirectory;
	private String dataFilename;
	private String pathToQ;
	private String pathToData;

	private int numberOfFrames;

	private double qMinAngstrom; //TODO check units in run-sas-pipeline
	private double qMaxAngstrom;

	private int numberOfThreads;

	private boolean gnomOnly; // if false, GNOM and DAMMIF are both run

	private double startDistanceAngstrom; //TODO check units in run-sas-pipeline
	private double endDistanceAngstrom;
	private int numberOfSearch;
	private double tolerance;

	private String symmetry;
	private boolean dammifFastMode;
	
	public ModelBuildingParameters(String workingDirectory, String htmlResultsDirectory, String dataFilename, String pathToQ, String pathToData, int numberOfFrames,
			double qMinAngstrom, double qMaxAngstrom, int numberOfThreads,
			boolean gnomOnly, double startDistanceAngstrom, double endDistanceAngstrom, int numberOfSearch, double tolerance,
			String symmetry, boolean dammifFastMode) {
		this.workingDirectory = workingDirectory;
		this.htmlResultsDirectory = htmlResultsDirectory;
		this.dataFilename = dataFilename;
		this.pathToQ = pathToQ;
		this.pathToData = pathToData;
		this.numberOfFrames = numberOfFrames;
		this.qMinAngstrom = qMinAngstrom;
		this.qMaxAngstrom = qMaxAngstrom;
		this.numberOfThreads = numberOfThreads;
		this.gnomOnly = gnomOnly;
		this.startDistanceAngstrom = startDistanceAngstrom;
		this.endDistanceAngstrom = endDistanceAngstrom;
		this.numberOfSearch = numberOfSearch;
		this.tolerance = tolerance;
		this.symmetry = symmetry;
		this.dammifFastMode = dammifFastMode;
	}
	
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

	public String getHtmlResultsDirectory() {
		return htmlResultsDirectory;
	}

	public void setHtmlResultsDirectory(String htmlResultsDirectory) {
		this.htmlResultsDirectory = htmlResultsDirectory;
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
	
	@Override
	public String toString() {
		// return parameters for use by the EDNA plugin directly
		String commandLineParameters = "\"" + workingDirectory + "\" \"" + htmlResultsDirectory + "\" --data \"" + dataFilename + "\"  --nxsQ \"" + pathToQ + "\" --nxsData \"" + pathToData + "\" --rMaxStart " + startDistanceAngstrom + " --rMaxStop " + endDistanceAngstrom +
				" --rMaxIntervals " + numberOfSearch + " --rMaxAbsTol " + tolerance + " --columns " + numberOfFrames +
				" --qmin " + qMinAngstrom + " --qmax " + qMaxAngstrom;
		if (!gnomOnly) {
			commandLineParameters += " --symmetry " + symmetry + " --mode " + (dammifFastMode ? "fast" : "slow");
		}
		else {
			commandLineParameters += " --onlyGnom";
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

		if (htmlResultsDirectory.isEmpty()) {
			returnMessage += "HTML results directory must not be empty" + System.lineSeparator();
		}

		if (qMinAngstrom >= qMaxAngstrom) {
			returnMessage += "qMax must be larger than qMin" + System.lineSeparator();
		}

		if (pathToQ.isEmpty()) {
			returnMessage += "pathToQ must not be empty" + System.lineSeparator();
		}

		if (pathToData.isEmpty()) {
			returnMessage += "pathToData must not be empty" + System.lineSeparator();
		}

		if (startDistanceAngstrom >= endDistanceAngstrom) {
			returnMessage += "Minimum distance must be less than maximum distance";
		}

		return returnMessage;
	}
}
