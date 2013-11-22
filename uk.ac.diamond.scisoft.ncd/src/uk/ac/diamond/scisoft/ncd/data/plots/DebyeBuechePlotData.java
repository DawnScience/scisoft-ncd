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
import uk.ac.diamond.scisoft.analysis.dataset.IErrorDataset;
import uk.ac.diamond.scisoft.ncd.utils.SaxsAnalysisPlotType;

public class DebyeBuechePlotData extends SaxsPlotData {

	public DebyeBuechePlotData() {
		super();
		Pair<String, String> axesNames = SaxsAnalysisPlotType.DEBYE_BUECHE_PLOT.getAxisNames();
		groupName = "debye_bueche";
		variableName = axesNames.getFirst();
		dataName = axesNames.getSecond();
	}
	
	@Override
	public double getDataValue(int idx, IDataset axis, IDataset data) {
		return Math.pow(data.getDouble(idx), -0.5);
	}
	
	@Override
	public double getAxisValue(int idx, IDataset axis) {
		return Math.pow(axis.getDouble(idx), 2);
	}

	@Override
	public double getDataError(int idx, IDataset axis, IDataset data) {
		if (data instanceof IErrorDataset && ((IErrorDataset) data).hasErrors()) {
			double val = data.getDouble(idx);
			double err = ((IErrorDataset) data).getError().getDouble(idx);
			return Math.pow(val, -1.5) * err / 2.0;
		}
		return Double.NaN;
	}

	@Override
	public double getAxisError(int idx, IDataset axis) {
		if (axis instanceof IErrorDataset && ((IErrorDataset) axis).hasErrors()) {
			double val = axis.getDouble(idx);
			double err = ((IErrorDataset) axis).getError().getDouble(idx);
			return 2.0 * val * err;
		}
		return Double.NaN;
	}
}
