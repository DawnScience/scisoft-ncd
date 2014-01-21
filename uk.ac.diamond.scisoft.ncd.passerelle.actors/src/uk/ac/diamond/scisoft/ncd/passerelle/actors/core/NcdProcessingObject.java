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

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;

public class NcdProcessingObject {
	
	private AbstractDataset data;
	private AbstractDataset axis;
	private SliceSettings sliceData;
	private ReentrantLock lock;
	
	private int entryGroupID, processingGroupID;
	private int inputGroupID, inputDataID, inputErrorsID;
	
	public NcdProcessingObject(AbstractDataset data, AbstractDataset axis, SliceSettings sliceData, ReentrantLock lock) {
		this.data = data;
		this.axis = axis;
		this.sliceData = sliceData;
		this.lock = lock;
	}

	public NcdProcessingObject(int entryGroupID, int processingGroupID, int inputGroupID, int inputDataID, int inputErrorsID, ReentrantLock lock) {
		super();
		this.entryGroupID = entryGroupID;
		this.processingGroupID = processingGroupID;
		this.inputGroupID = inputGroupID;
		this.inputDataID = inputDataID;
		this.inputErrorsID = inputErrorsID;
		this.lock = lock;
	}

	public AbstractDataset getData() {
		return data;
	}
	
	public void setData(AbstractDataset data) {
		this.data = data;
	}
	
	public AbstractDataset getAxis() {
		return axis;
	}

	public void setAxis(AbstractDataset axis) {
		this.axis = axis;
	}

	public SliceSettings getSliceData() {
		return sliceData;
	}
	
	public void setSliceData(SliceSettings sliceData) {
		this.sliceData = sliceData;
	}
	
	public ReentrantLock getLock() {
		return lock;
	}
	
	public void setLock(ReentrantLock lock) {
		this.lock = lock;
	}

	public int getEntryGroupID() {
		return entryGroupID;
	}

	public int getProcessingGroupID() {
		return processingGroupID;
	}

	public int getInputGroupID() {
		return inputGroupID;
	}

	public int getInputDataID() {
		return inputDataID;
	}

	public int getInputErrorsID() {
		return inputErrorsID;
	}
	
}
