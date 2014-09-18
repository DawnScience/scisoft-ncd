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

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.dawnsci.analysis.api.message.DataMessageComponent;

public class NcdProcessingObject extends DataMessageComponent {

	private static final long serialVersionUID = -3338750892085718124L;
	
	private static final String DIMENSION = "dimension";
	private static final String ENTRY_GROUP_ID = "entryGroupID";
	private static final String PROCESSING_GROUP_ID = "processingGroupID";
	private static final String INPUT_GROUP_ID = "inputGroupID";
	private static final String INPUT_DATA_ID = "inputDataID";
	private static final String INPUT_ERRORS_ID = "inputErrorsID";
	private static final String INPUT_AXIS_DATA_ID = "inputAxisDataID";
	private static final String INPUT_AXIS_ERRORS_ID = "inputAxisErrorsID";
	private static final String LOCK = "lock";
	private static final String MONITOR = "Monitor";

	public NcdProcessingObject(int dimension,
			int entryGroupID, int processingGroupID,	int inputGroupID,
			int inputDataID, int inputErrorsID,
			int inputAxisDataID, int inputAxisErrorsID,
			ReentrantLock lock, IProgressMonitor monitor) {
		super();

		setDataDimension(dimension);
		setEntryGroupID(entryGroupID);
		setProcessingGroupID(processingGroupID);
		setInputGroupID(inputGroupID);
		setInputDataID(inputDataID);
		setInputErrorsID(inputErrorsID);
		setInputAxisDataID(inputAxisDataID);
		setInputAxisErrorsID(inputAxisErrorsID);
		setLock(lock);
		setMonitor(monitor);
	}

	public int getDataDimension() {
		Object obj = getUserObject(DIMENSION);
		if (obj instanceof Integer) {
			return (Integer) obj;
		}
		return -1;
	}

	public void setDataDimension(int dimension) {
		addUserObject(DIMENSION, dimension);
	}

	public int getEntryGroupID() {
		Object obj = getUserObject(ENTRY_GROUP_ID);
		if (obj instanceof Integer) {
			return (Integer) obj;
		}
		return -1;
	}

	public void setEntryGroupID(int entryGroupID) {
		addUserObject(ENTRY_GROUP_ID, entryGroupID);
	}

	public int getProcessingGroupID() {
		Object obj = getUserObject(PROCESSING_GROUP_ID);
		if (obj instanceof Integer) {
			return (Integer) obj;
		}
		return -1;
	}

	public void setProcessingGroupID(int processingGroupID) {
		addUserObject(PROCESSING_GROUP_ID, processingGroupID);
	}

	public int getInputGroupID() {
		Object obj = getUserObject(INPUT_GROUP_ID);
		if (obj instanceof Integer) {
			return (Integer) obj;
		}
		return -1;
	}

	public void setInputGroupID(int inputGroupID) {
		addUserObject(INPUT_GROUP_ID, inputGroupID);
	}

	public int getInputDataID() {
		Object obj = getUserObject(INPUT_DATA_ID);
		if (obj instanceof Integer) {
			return (Integer) obj;
		}
		return -1;
	}

	public void setInputDataID(int inputDataID) {
		addUserObject(INPUT_DATA_ID, inputDataID);
	}

	public int getInputErrorsID() {
		Object obj = getUserObject(INPUT_ERRORS_ID);
		if (obj instanceof Integer) {
			return (Integer) obj;
		}
		return -1;
	}

	public void setInputErrorsID(int inputErrorsID) {
		addUserObject(INPUT_ERRORS_ID, inputErrorsID);
	}

	public int getInputAxisDataID() {
		Object obj = getUserObject(INPUT_AXIS_DATA_ID);
		if (obj instanceof Integer) {
			return (Integer) obj;
		}
		return -1;
	}

	public void setInputAxisDataID(int inputAxisDataID) {
		addUserObject(INPUT_AXIS_DATA_ID, inputAxisDataID);
	}

	public int getInputAxisErrorsID() {
		Object obj = getUserObject(INPUT_AXIS_ERRORS_ID);
		if (obj instanceof Integer) {
			return (Integer) obj;
		}
		return -1;
	}

	public void setInputAxisErrorsID(int inputAxisErrorsID) {
		addUserObject(INPUT_AXIS_ERRORS_ID, inputAxisErrorsID);
	}

	public ReentrantLock getLock() {
		Object obj = getUserObject(LOCK);
		if (obj instanceof ReentrantLock) {
			return (ReentrantLock) obj;
		}
		return null;
	}

	public void setLock(ReentrantLock lock) {
		if (lock != null) {
			addUserObject(LOCK, lock);
		} else {
			addUserObject(LOCK, new ReentrantLock());
		}
	}

	public IProgressMonitor getMonitor() {
		Object obj = getUserObject(MONITOR);
		if (obj instanceof IProgressMonitor) {
			return (IProgressMonitor) obj;
		}
		return null;
	}

	public void setMonitor(IProgressMonitor monitor) {
		if (monitor != null) {
			addUserObject(MONITOR, monitor);
		} else {
			addUserObject(MONITOR, new NullProgressMonitor());
		}
	}

}
