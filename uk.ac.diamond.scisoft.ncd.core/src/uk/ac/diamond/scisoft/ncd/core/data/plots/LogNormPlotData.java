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

package uk.ac.diamond.scisoft.ncd.core.data.plots;

import org.apache.commons.math3.util.Pair;
import org.eclipse.january.dataset.IDataset;

import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;

public class LogNormPlotData extends SaxsPlotData {

	public LogNormPlotData() {
		super();
		Pair<String, String> axesNames = SaxsAnalysisPlotType.LOGNORM_PLOT.getAxisNames();
		groupName = SaxsAnalysisPlotType.LOGNORM_PLOT.getGroupName();
		variableName = axesNames.getFirst();
		dataName = axesNames.getSecond();
	}
	
	@Override
	public double getDataValue(int idx, IDataset axis, IDataset data) {
	        return Math.log10(data.getDouble(idx));
	}
	
	@Override
	public double getAxisValue(int idx, IDataset axis) {
		return axis.getDouble(idx);
	}

	@Override
	public double getDataError(int idx, IDataset axis, IDataset data) {
		if (data.hasErrors()) {
			double val = data.getDouble(idx);
			double err = data.getError(idx);
			return err / val;
		}
		return Double.NaN;
	}

	@Override
	public double getAxisError(int idx, IDataset axis) {
		if (axis.hasErrors()) {
			double val = axis.getDouble(idx);
			double err = axis.getError(idx);
			return err / val;
		}
		return Double.NaN;
	}
}
