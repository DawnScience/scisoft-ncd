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

package uk.ac.diamond.scisoft.ncd.core.data.plots;

import uk.ac.diamond.scisoft.analysis.dataset.Dataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetFactory;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IErrorDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;

public abstract class SaxsPlotData implements ISaxsPlotData {
	
	protected String groupName, dataName, variableName;
	
	public String getGroupName() {
		return groupName;
	}

	public String getDataName() {
		return dataName;
	}

	public String getVariableName() {
		return variableName;
	}

	@Override
	public Dataset getSaxsPlotDataset(IDataset data, IDataset axis) {
		Dataset tmpData = DatasetFactory.zeros(data.getShape(), Dataset.FLOAT32);
		boolean hasErrors = false;
		Dataset tmpErrors = null;
		if (data instanceof IErrorDataset && ((IErrorDataset) data).hasErrors()) {
			tmpErrors = DatasetFactory.zeros(data.getShape(), Dataset.FLOAT32);
			hasErrors = true;
		}
		IndexIterator itr = tmpData.getIterator();
		while (itr.hasNext()) {
			int idx = itr.index;
			double val = getDataValue(idx, axis, data);
			tmpData.set(val, idx);
			if (hasErrors && tmpErrors != null) {
				double err = getDataError(idx, axis, data);
				tmpErrors.set(err, idx);
			}
		}
		if (tmpErrors != null) {
			tmpData.setError(tmpErrors);
		}
		return tmpData;
	}

	@Override
	public Dataset getSaxsPlotAxis(IDataset axis) {
		Dataset tmpAxis = DatasetFactory.zeros(axis.getShape(), Dataset.FLOAT32);
		boolean hasErrors = false;
		Dataset tmpAxisErrors = null;
		if (axis instanceof IErrorDataset && ((IErrorDataset) axis).hasErrors()) {
			tmpAxisErrors = DatasetFactory.zeros(axis.getShape(), Dataset.FLOAT32);
			hasErrors = true;
		}
		IndexIterator itr = tmpAxis.getIterator();
		while (itr.hasNext()) {
			int idx = itr.index;
			double val = getAxisValue(idx, axis);
			tmpAxis.set(val, idx);
			if (hasErrors && tmpAxisErrors != null) {
				double err = getAxisError(idx, axis);
				tmpAxisErrors.set(err, idx);
			}
		}
		if (tmpAxisErrors != null) {
			tmpAxis.setError(tmpAxisErrors);
		}
		return tmpAxis;
	}
}
