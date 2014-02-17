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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.dawb.hdf5.Nexus;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import ptolemy.data.BooleanToken;
import ptolemy.data.IntToken;
import ptolemy.data.ObjectToken;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.Source;
import com.isencia.passerelle.actor.TerminationException;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;

public class NcdMessageSource extends Source {


	private static final long serialVersionUID = 5280820508271420584L;
	
	private boolean messageSent;
	private String filename;
	private String detector;
	private String processing;
	
	public Parameter dimensionParam, lockParam, monitorParam, readOnlyParam;
	public StringParameter filenameParam, detectorParam, processingParam;
	
	private int dimension = -1;
	private int nxsFileID = -1;
	private int entryGroupID = -1;
	private int processingGroupID = -1;
	private int detectorGroupID = -1;
	private int inputDataID = -1;
	private int inputErrorsID = -1;
	//TODO: Add support for reading axis dataset
	private int inputAxisDataID = -1;
	private int inputAxisErrorsID = -1;

	public NcdMessageSource(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);
		messageSent = false;
		
		dimensionParam = new Parameter(this, "dimensionParam", new IntToken(-1));
		filenameParam = new StringParameter(this, "filenameParam");
		detectorParam = new StringParameter(this, "detectorParam");
		processingParam = new StringParameter(this, "processingParam");
		readOnlyParam = new Parameter(this, "readOnlyParam");
		lockParam = new Parameter(this, "lockParam");
		monitorParam = new Parameter(this, "monitorParam");
	}

	@Override
	protected ManagedMessage getMessage() throws ProcessingException {
		ManagedMessage dataMsg = null;
		if (messageSent) {
			return null;
		}
		ReentrantLock lock = null;
		try {
			dimension  = ((IntToken) dimensionParam.getToken()).intValue();
			filename = ((StringToken) filenameParam.getToken()).stringValue();
			detector = ((StringToken) detectorParam.getToken()).stringValue();
			processing = ((StringToken) processingParam.getToken()).stringValue();

			boolean readOnly = false;
			Token token = readOnlyParam.getToken();
			if (token instanceof BooleanToken) {
				readOnly = ((BooleanToken) token).booleanValue();
			}
			
			token = lockParam.getToken();
			if (token instanceof ObjectToken) {
				Object obj = ((ObjectToken) token).getValue();
				if (obj instanceof ReentrantLock) {
					lock = (ReentrantLock) obj;
				}
			}
			if (lock == null) {
				lock = new ReentrantLock();
				getLogger().info("Creating new reentrant lock for working with HDF5 files");
			}

			IProgressMonitor monitor = null;
			token = monitorParam.getToken();
			if (token instanceof ObjectToken) {
				Object obj = ((ObjectToken) token).getValue();
				if (obj instanceof IProgressMonitor) {
					monitor = (IProgressMonitor) obj;
				}
			}
			if (monitor == null) {
				monitor = new NullProgressMonitor();
				getLogger().info("Monitor object was not provided");
			}

			lock.lock();
			nxsFileID = H5.H5Fopen(filename, readOnly ? HDF5Constants.H5F_ACC_RDONLY : HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
			entryGroupID = H5.H5Gopen(nxsFileID, "entry1", HDF5Constants.H5P_DEFAULT);
			detectorGroupID = H5.H5Gopen(entryGroupID, detector, HDF5Constants.H5P_DEFAULT);
			inputDataID = H5.H5Dopen(detectorGroupID, "data", HDF5Constants.H5P_DEFAULT);
			
			if (processing != null && !processing.isEmpty()) {
				boolean hasProcessing = H5.H5Lexists(entryGroupID, processing, HDF5Constants.H5P_DEFAULT);
				if (hasProcessing) {
					processingGroupID = H5.H5Gopen(entryGroupID, processing, HDF5Constants.H5P_DEFAULT);
				} else {
					if (!readOnly) {
						processingGroupID = NcdNexusUtils.makegroup(entryGroupID, processing, Nexus.INST);
					}
				}
			}
			
			boolean hasErrors = H5.H5Lexists(detectorGroupID, "errors", HDF5Constants.H5P_DEFAULT);
			if (hasErrors) {
				inputErrorsID = H5.H5Dopen(detectorGroupID, "errors", HDF5Constants.H5P_DEFAULT);
			} else {
				getLogger().info("Input dataset with error estimates wasn't found");
			}
			
			NcdProcessingObject msg = new NcdProcessingObject(
					dimension,
					entryGroupID,
					processingGroupID,
					detectorGroupID,
					inputDataID,
					inputErrorsID,
					inputAxisDataID,
					inputAxisErrorsID,
					lock,
					monitor);
			dataMsg = createMessage(msg, "application/octet-stream");
		} catch (IllegalActionException e) {
			messageSent = false;
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e);
		} catch (HDF5LibraryException e) {
			messageSent = false;
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e);
		} catch (HDF5Exception e) {
			messageSent = false;
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e);
		} catch (MessageException e) {
			messageSent = false;
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e);
		} finally {
			if (lock != null && lock.isLocked() && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
		messageSent = true;
		return dataMsg;
	}

	@Override
	protected void doWrapUp() throws TerminationException {
		try {
			List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(
					inputDataID,
					inputErrorsID,
					inputAxisDataID,
					inputAxisErrorsID,
					detectorGroupID,
					processingGroupID,
					entryGroupID,
					nxsFileID));

			NcdNexusUtils.closeH5idList(identifiers);
		} catch (HDF5LibraryException e) {
			getLogger().info("Error closing NeXus handle identifier", e);
		}
	}

}