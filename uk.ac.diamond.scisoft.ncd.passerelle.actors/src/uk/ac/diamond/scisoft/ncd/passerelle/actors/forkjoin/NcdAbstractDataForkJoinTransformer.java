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

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.locks.ReentrantLock;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.dawb.hdf5.Nexus;

import ptolemy.data.BooleanToken;
import ptolemy.data.IntToken;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdProcessingObject;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.v5.Actor;
import com.isencia.passerelle.actor.v5.ActorContext;
import com.isencia.passerelle.actor.v5.ProcessRequest;
import com.isencia.passerelle.actor.v5.ProcessResponse;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.core.Port;
import com.isencia.passerelle.core.PortFactory;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;

public abstract class NcdAbstractDataForkJoinTransformer extends Actor {

	private static final long serialVersionUID = -289682801810608304L;
	
	protected static final ForkJoinPool forkJoinPool = new ForkJoinPool();

	public Port input;
	public Port output;
	
	public String dataName;
	
	protected ReentrantLock lock;
	
	public Parameter isEnabled;
	public Parameter dimensionParam;
	
	protected boolean enabled, hasErrors;
	
	protected int dimension;
	protected long[] frames;
	protected int entryGroupID, processingGroupID;
	protected int inputGroupID, inputDataID, inputErrorsID;
	protected int resultGroupID, resultDataID, resultErrorsID;

	protected RecursiveAction task;
	
	public NcdAbstractDataForkJoinTransformer(CompositeEntity container, String name) throws IllegalActionException,
			NameDuplicationException {
		super(container, name);

		input = PortFactory.getInstance().createInputPort(this, "input", NcdProcessingObject.class);
		output = PortFactory.getInstance().createOutputPort(this, "result");

		isEnabled = new Parameter(this, "enable", new BooleanToken(false));

		dimensionParam = new Parameter(this, "dimensionParam", new IntToken(-1));
	}
	
	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		
		try {
			enabled = ((BooleanToken) isEnabled.getToken()).booleanValue();
			if (!enabled) {
				return;
			}
			
			dimension = ((IntToken) dimensionParam.getToken()).intValue();
			
		} catch (IllegalActionException e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing NCD actor",
					this, e);
		}
	}
	
	@Override
	protected void process(ActorContext ctxt, ProcessRequest request, ProcessResponse response)
			throws ProcessingException {

		ManagedMessage receivedMsg = request.getMessage(input);
		if (!enabled) {
			response.addOutputMessage(output, receivedMsg);
			return;
		}

		try {
			NcdProcessingObject receivedObject = (NcdProcessingObject) receivedMsg.getBodyContent();
			entryGroupID = receivedObject.getEntryGroupID();
			processingGroupID = receivedObject.getProcessingGroupID();
			inputGroupID = receivedObject.getInputGroupID();
			inputDataID = receivedObject.getInputDataID();
			inputErrorsID = receivedObject.getInputErrorsID();
			lock = receivedObject.getLock();
			
			readAdditionalPorts(request);
			
			lock.lock();
			configureActorParameters();
			lock.unlock();

			forkJoinPool.invoke(task);
			writeNcdMetadata(resultGroupID);
			
			ManagedMessage outputMsg = createMessageFromCauses(receivedMsg);
			NcdProcessingObject obj = new NcdProcessingObject(entryGroupID, processingGroupID, resultGroupID, resultDataID, resultErrorsID, lock);
			outputMsg.setBodyContent(obj, "application/octet-stream");
			response.addOutputMessage(output, outputMsg);
		} catch (MessageException e) {
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e.getCause());
		} catch (HDF5Exception e) {
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e.getCause());
		}
	}
	
	@SuppressWarnings("unused")
	protected void readAdditionalPorts(ProcessRequest request) throws MessageException {
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
		resultGroupID = NcdNexusUtils.makegroup(processingGroupID, dataName, Nexus.DETECT);
		int type = HDF5Constants.H5T_NATIVE_FLOAT;
		resultDataID = NcdNexusUtils.makedata(resultGroupID, "data", type, resultFrames, true, "counts");
		type = HDF5Constants.H5T_NATIVE_DOUBLE;
		resultErrorsID = NcdNexusUtils.makedata(resultGroupID, "errors", type, resultFrames, true, "counts");
		
	}

	protected long[] getResultDataShape() {
		return Arrays.copyOf(frames, frames.length);
	}
	
	public void writeNcdMetadata(int datagroupID) throws HDF5LibraryException, HDF5Exception {
		String detType = DetectorTypes.REDUCTION_DETECTOR;
		int typeID = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		H5.H5Tset_size(typeID, detType.length());
		int metadataID = NcdNexusUtils.makedata(datagroupID, "sas_type", typeID, new long[] {1});
		
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
