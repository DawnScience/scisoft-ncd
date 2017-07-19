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

package uk.ac.diamond.scisoft.ncd.core.service;

import javax.measure.quantity.Energy;

import org.dawnsci.plotting.tools.preference.detector.DiffractionDetector;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.january.dataset.BooleanDataset;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStatsParameters;
import uk.ac.diamond.scisoft.ncd.core.preferences.NcdReductionFlags;

public interface IDataReductionProcess {

	void setNcdDetector(DiffractionDetector detectorWaxs);

	void execute(String bgFilename, IProgressMonitor monitor) throws Exception;

	void setBgFile(String bgFilename);

	void setBgDetector(String string);

	void setFlags(NcdReductionFlags flags);

	void setFirstFrame(Integer bgFirstFrame);
	
	void setLastFrame(Integer lastFrame);

	void setFrameSelection(String frameSelection);

	void setMask(BooleanDataset booleanDataset);

	void setEnableMask(boolean enableMask);

	void setIntSector(SectorROI sector);

	void setCrb(CalibrationResultsBean crb);

	void setEnergy(Amount<Energy> energy);

	void setAbsScaling(Double absScaling);

	void setBgScaling(Double bgScaling);

	void setDrFile(String drFile);

	void setGridAverageSelection(String gridAverage);

	void setCalibration(String calibrationName);

	void setNormChannel(int normChannel);

	void setSaxsAnalysisStatsParameters(SaxsAnalysisStatsParameters saxsAnalysisStatsParameters);
	
}
