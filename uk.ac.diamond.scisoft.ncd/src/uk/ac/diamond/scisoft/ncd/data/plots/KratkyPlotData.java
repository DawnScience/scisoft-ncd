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
import org.eclipse.january.dataset.IDataset;

import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;

public class KratkyPlotData extends SaxsPlotData {

	public KratkyPlotData() {
		super();
		Pair<String, String> axesNames = SaxsAnalysisPlotType.KRATKY_PLOT.getAxisNames();
		groupName = "kratky";
		variableName = axesNames.getFirst();
		dataName = axesNames.getSecond();
	}
	
	@Override
	public double getDataValue(int idx, IDataset axis, IDataset data) {
		return (Math.pow(axis.getDouble(idx), 2) * data.getDouble(idx));
	}
	
	@Override
	public double getAxisValue(int idx, IDataset axis) {
		return axis.getDouble(idx);
	}

	@Override
	public double getDataError(int idx, IDataset axis, IDataset data) {
		if (data.hasErrors() && axis.hasErrors()) {
			double val = data.getDouble(idx);
			double err = data.getError(idx);
			double axval = axis.getDouble(idx);
			double axerr = axis.getError(idx);
			return Math.sqrt(Math.pow(2.0*axval*val*axerr, 2.0) + Math.pow(axval*axval*err, 2.0));
		}
		return Double.NaN;
	}

	@Override
	public double getAxisError(int idx, IDataset axis) {
		if (axis.hasErrors()) {
			return axis.getError(idx);
		}
		return Double.NaN;
	}
}
