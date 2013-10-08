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

package uk.ac.diamond.scisoft.ncd.data.plots;

import org.apache.commons.math3.util.Pair;

import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.ncd.utils.SaxsAnalysisPlots;

public class PorodPlotData extends SaxsPlotData {

	public PorodPlotData() {
		super();
		Pair<String, String> axesNames = SaxsAnalysisPlots.getSaxsPlotAxes(SaxsAnalysisPlots.POROD_PLOT);
		groupName = "porod";
		variableName = axesNames.getFirst();
		dataName = axesNames.getSecond();
	}
	
	@Override
	protected double getDataValue(int idx, IDataset axis, IDataset data) {
		return (Math.pow(axis.getDouble(idx), 4) * data.getDouble(idx));
	}
	
	@Override
	protected double getAxisValue(int idx, IDataset axis) {
		return axis.getDouble(idx);
	}
}
