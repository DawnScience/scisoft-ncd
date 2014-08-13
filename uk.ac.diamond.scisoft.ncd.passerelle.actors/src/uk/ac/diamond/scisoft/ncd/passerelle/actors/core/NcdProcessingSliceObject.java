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

package uk.ac.diamond.scisoft.ncd.passerelle.actors.core;

import java.util.concurrent.locks.ReentrantLock;

import uk.ac.diamond.scisoft.analysis.dataset.Dataset;
import uk.ac.diamond.scisoft.analysis.dataset.Dataset;
import uk.ac.diamond.scisoft.analysis.message.DataMessageComponent;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;

public class NcdProcessingSliceObject extends DataMessageComponent {

	private static final long serialVersionUID = -7243460468886418789L;
	
	private static final String DATA = "data";
	private static final String AXIS = "axis";
	private static final String SLICE = "slice";
	private static final String LOCK = "lock";

	public NcdProcessingSliceObject(Dataset data, Dataset axis, SliceSettings sliceData,
			ReentrantLock lock) {
		super();

		setData(data);
		setAxis(axis);
		setSliceData(sliceData);
		setLock(lock);
	}

	public Dataset getData() {
		Object obj = getList(DATA);
		if (obj instanceof Dataset) {
			return (Dataset) obj;
		}
		return null;
	}

	public void setData(Dataset data) {
		addList(DATA, data);
	}

	public Dataset getAxis() {
		Object obj = getList(AXIS);
		if (obj instanceof Dataset) {
			return (Dataset) obj;
		}
		return null;
	}

	public void setAxis(Dataset axis) {
		addList(AXIS, axis);
	}

	public SliceSettings getSliceData() {
		Object obj = getUserObject(SLICE);
		if (obj instanceof SliceSettings) {
			return (SliceSettings) obj;
		}
		return null;
	}

	public void setSliceData(SliceSettings sliceData) {
		addUserObject(SLICE, sliceData);
	}

	public ReentrantLock getLock() {
		Object obj = getUserObject(LOCK);
		if (obj instanceof ReentrantLock) {
			return (ReentrantLock) obj;
		}
		return null;
	}

	public void setLock(ReentrantLock lock) {
		addUserObject(LOCK, lock);
	}

}
