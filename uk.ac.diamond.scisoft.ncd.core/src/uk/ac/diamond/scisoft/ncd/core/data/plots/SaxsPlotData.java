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

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
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
	public AbstractDataset getSaxsPlotDataset(IDataset data, IDataset axis) {
		AbstractDataset tmpData = AbstractDataset.zeros(data.getShape(), AbstractDataset.FLOAT32);
		boolean hasErrors = false;
		AbstractDataset tmpErrors = null;
		if (data instanceof IErrorDataset && ((IErrorDataset) data).hasErrors()) {
			tmpErrors = AbstractDataset.zeros(data.getShape(), AbstractDataset.FLOAT32);
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
	public AbstractDataset getSaxsPlotAxis(IDataset axis) {
		AbstractDataset tmpAxis = AbstractDataset.zeros(axis.getShape(), AbstractDataset.FLOAT32);
		boolean hasErrors = false;
		AbstractDataset tmpAxisErrors = null;
		if (axis instanceof IErrorDataset && ((IErrorDataset) axis).hasErrors()) {
			tmpAxisErrors = AbstractDataset.zeros(axis.getShape(), AbstractDataset.FLOAT32);
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
