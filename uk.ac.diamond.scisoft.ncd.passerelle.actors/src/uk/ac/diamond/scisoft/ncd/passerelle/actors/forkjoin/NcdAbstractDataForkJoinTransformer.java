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

package uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.locks.ReentrantLock;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.dawb.hdf5.Nexus;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdProcessingObject;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.TerminationException;
import com.isencia.passerelle.actor.v5.Actor;
import com.isencia.passerelle.actor.v5.ActorContext;
import com.isencia.passerelle.actor.v5.ProcessRequest;
import com.isencia.passerelle.actor.v5.ProcessResponse;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.core.PasserelleException;
import com.isencia.passerelle.core.Port;
import com.isencia.passerelle.core.PortFactory;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;

public abstract class NcdAbstractDataForkJoinTransformer extends Actor {

	private static final long serialVersionUID = -289682801810608304L;
	
	protected static final ForkJoinPool forkJoinPool = new ForkJoinPool();

	public Port input;
	public Port output;
	
	protected ReentrantLock lock;
	protected IProgressMonitor monitor;
	
	public Parameter isEnabled;
	
	protected boolean hasErrors;
	
	protected int dimension;
	protected long[] frames;
	protected int entryGroupID, processingGroupID;
	protected int inputGroupID, inputDataID, inputErrorsID, inputAxisDataID, inputAxisErrorsID;
	protected int resultGroupID, resultDataID, resultErrorsID, resultAxisDataID, resultAxisErrorsID;

	protected RecursiveAction task;
	
	public NcdAbstractDataForkJoinTransformer(CompositeEntity container, String name) throws IllegalActionException,
			NameDuplicationException {
		super(container, name);

		input = PortFactory.getInstance().createInputPort(this, "input", NcdProcessingObject.class);
		output = PortFactory.getInstance().createOutputPort(this, "result");
	}
	
	@Override
	protected void process(ActorContext ctxt, ProcessRequest request, ProcessResponse response)
			throws ProcessingException {

		ManagedMessage receivedMsg = request.getMessage(input);
		
		try {
			NcdProcessingObject receivedObject = (NcdProcessingObject) receivedMsg.getBodyContent();
			dimension = receivedObject.getDataDimension();
			entryGroupID = receivedObject.getEntryGroupID();
			processingGroupID = receivedObject.getProcessingGroupID();
			inputGroupID = receivedObject.getInputGroupID();
			inputDataID = receivedObject.getInputDataID();
			inputErrorsID = receivedObject.getInputErrorsID();
			inputAxisDataID = receivedObject.getInputAxisDataID();
			inputAxisErrorsID = receivedObject.getInputAxisErrorsID();
			lock = receivedObject.getLock();
			monitor = receivedObject.getMonitor();
			
			readAdditionalPorts(request);
			
			lock.lock();
			configureActorParameters();
			lock.unlock();

			if (!monitor.isCanceled()) {
				
				monitor.subTask("Executing task : " + getDisplayName());
				forkJoinPool.invoke(task);
				
				lock.lock();
				writeNcdMetadata();
				lock.unlock();
			}
			
			dimension = getResultDimension();
			
			writeAdditionalPorts(receivedMsg, response);
			
			ManagedMessage outputMsg = createMessageFromCauses(receivedMsg);
			NcdProcessingObject obj = new NcdProcessingObject(
					dimension,
					entryGroupID,
					processingGroupID,
					resultGroupID,
					resultDataID,
					resultErrorsID,
					resultAxisDataID,
					resultAxisErrorsID,
					lock,
					monitor);
			outputMsg.setBodyContent(obj, "application/octet-stream");
			response.addOutputMessage(output, outputMsg);
		} catch (MessageException e) {
			throw new ProcessingException(e.getErrorCode(), e.getMessage(), this, receivedMsg, e);
		} catch (HDF5Exception e) {
			throw new ProcessingException(ErrorCode.ERROR, e.getMessage(), this, receivedMsg, e);
		} finally {
			if (lock != null && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
			if (task != null && task.isCompletedAbnormally()) {
				Throwable e = task.getException();
				while (e.getCause() != null) {
					e = e.getCause();
				}
				ErrorCode severity = ErrorCode.ACTOR_EXECUTION_ERROR;
				if (e instanceof PasserelleException) {
					severity = ((PasserelleException) e).getErrorCode();
				}
				if (e instanceof OperationCanceledException) {
					severity = ErrorCode.INFO;
				}
				throw new ProcessingException(severity, e.getMessage(), this, receivedMsg, e);
			}
		}
	}
	
	@Override
	protected void doWrapUp() throws TerminationException {
		try {
			List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(
					resultDataID,
					resultErrorsID,
					resultAxisDataID,
					resultAxisErrorsID,
					resultGroupID));

			NcdNexusUtils.closeH5idList(identifiers);
		} catch (HDF5LibraryException e) {
			getLogger().info("Error closing NeXus handle identifier", e);
		}
	}

	@SuppressWarnings("unused")
	protected void readAdditionalPorts(ProcessRequest request) throws MessageException {
	}

	@SuppressWarnings("unused")
	protected void writeAdditionalPorts(ManagedMessage receivedMsg, ProcessResponse response) throws MessageException {
	}

	protected void configureActorParameters() throws HDF5Exception {
		int inputDataSpaceID = H5.H5Dget_space(inputDataID);
		int rank = H5.H5Sget_simple_extent_ndims(inputDataSpaceID);
		frames = new long[rank];
		H5.H5Sget_simple_extent_dims(inputDataSpaceID, frames, null);
		NcdNexusUtils.closeH5id(inputDataSpaceID);
		
		hasErrors = false;
		if (inputErrorsID > 0) {
			try {
				final int type = H5.H5Iget_type(inputErrorsID);
				if (type != HDF5Constants.H5I_BADID) {
					hasErrors = true;
				}
			} catch (HDF5LibraryException e) {
				getLogger().info("Input dataset with error values wasn't found");
			}
		}
		long[] resultFrames = getResultDataShape();
		resultGroupID = NcdNexusUtils.makegroup(processingGroupID, getName(), Nexus.DETECT);
		int type = getResultDataType();
		resultDataID = NcdNexusUtils.makedata(resultGroupID, "data", type, resultFrames, true, "counts");
		type = getResultErrorsType();
		resultErrorsID = NcdNexusUtils.makedata(resultGroupID, "errors", type, resultFrames, true, "counts");
	}

	protected int getResultDataType() throws HDF5LibraryException {
		return H5.H5Tcopy(HDF5Constants.H5T_NATIVE_FLOAT);
	}
	
	protected int getResultErrorsType() throws HDF5LibraryException {
		return H5.H5Tcopy(HDF5Constants.H5T_NATIVE_DOUBLE);
	}
	
	protected long[] getResultDataShape() {
		return Arrays.copyOf(frames, frames.length);
	}
	
	protected int getResultDimension() {
		return dimension;
	}
	
	protected void writeAxisData() throws HDF5Exception {
		NcdNexusUtils.makelink(inputAxisDataID, resultGroupID);
		NcdNexusUtils.makelink(inputAxisErrorsID, resultGroupID);
		resultAxisDataID = inputAxisDataID;
		resultAxisErrorsID = inputAxisErrorsID;
	}
	
	protected void writeNcdMetadata() throws HDF5LibraryException, HDF5Exception {
		
		writeAxisData();
		
		String detType = DetectorTypes.REDUCTION_DETECTOR;
		int typeID = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		H5.H5Tset_size(typeID, detType.length());
		int metadataID = NcdNexusUtils.makedata(resultGroupID, "sas_type", typeID, new long[] {1});
		
		int filespaceID = H5.H5Dget_space(metadataID);
		int memspaceID = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespaceID);
		H5.H5Dwrite(metadataID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, detType.getBytes());
		
		H5.H5Sclose(filespaceID);
		H5.H5Sclose(memspaceID);
		H5.H5Tclose(typeID);
		H5.H5Dclose(metadataID);
	}
	
}
