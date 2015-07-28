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
			long entryGroupID, long processingGroupID,	long inputGroupID,
			long inputDataID, long inputErrorsID,
			long inputAxisDataID, long inputAxisErrorsID,
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

	public long getEntryGroupID() {
		Object obj = getUserObject(ENTRY_GROUP_ID);
		if (obj instanceof Long) {
			return (Long) obj;
		}
		return -1;
	}

	public void setEntryGroupID(long entryGroupID) {
		addUserObject(ENTRY_GROUP_ID, entryGroupID);
	}

	public long getProcessingGroupID() {
		Object obj = getUserObject(PROCESSING_GROUP_ID);
		if (obj instanceof Long) {
			return (Long) obj;
		}
		return -1;
	}

	public void setProcessingGroupID(long processingGroupID) {
		addUserObject(PROCESSING_GROUP_ID, processingGroupID);
	}

	public long getInputGroupID() {
		Object obj = getUserObject(INPUT_GROUP_ID);
		if (obj instanceof Long) {
			return (Long) obj;
		}
		return -1;
	}

	public void setInputGroupID(long inputGroupID) {
		addUserObject(INPUT_GROUP_ID, inputGroupID);
	}

	public long getInputDataID() {
		Object obj = getUserObject(INPUT_DATA_ID);
		if (obj instanceof Long) {
			return (Long) obj;
		}
		return -1;
	}

	public void setInputDataID(long inputDataID) {
		addUserObject(INPUT_DATA_ID, inputDataID);
	}

	public long getInputErrorsID() {
		Object obj = getUserObject(INPUT_ERRORS_ID);
		if (obj instanceof Long) {
			return (Long) obj;
		}
		return -1;
	}

	public void setInputErrorsID(long inputErrorsID) {
		addUserObject(INPUT_ERRORS_ID, inputErrorsID);
	}

	public long getInputAxisDataID() {
		Object obj = getUserObject(INPUT_AXIS_DATA_ID);
		if (obj instanceof Long) {
			return (Long) obj;
		}
		return -1;
	}

	public void setInputAxisDataID(long inputAxisDataID) {
		addUserObject(INPUT_AXIS_DATA_ID, inputAxisDataID);
	}

	public long getInputAxisErrorsID() {
		Object obj = getUserObject(INPUT_AXIS_ERRORS_ID);
		if (obj instanceof Long) {
			return (Long) obj;
		}
		return -1;
	}

	public void setInputAxisErrorsID(long inputAxisErrorsID) {
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
