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

import org.dawb.hdf5.Nexus;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import ptolemy.data.StringToken;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.Sink;
import com.isencia.passerelle.actor.TerminationException;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;

public class NcdMessageSink extends Sink {

	public StringParameter detectorParam;
	private int entryGroupID;
	private int inputDataID;
	private int inputErrorsID;
	private int inputAxisDataID;
	private int inputAxisErrorsID;
	private int outputGroupID;

	public NcdMessageSink(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);
		
		detectorParam = new StringParameter(this, "detectorParam");
	}

	@Override
	protected void sendMessage(ManagedMessage message) throws ProcessingException {

		if (message != null) {
			try {
				Object obj = message.getBodyContent();
				if (obj instanceof NcdProcessingObject) {
					NcdProcessingObject content = (NcdProcessingObject) obj;
					entryGroupID = content.getEntryGroupID();
					inputDataID = content.getInputDataID();
					inputErrorsID = content.getInputErrorsID();
					inputAxisDataID = content.getInputAxisDataID();
					inputAxisErrorsID = content.getInputAxisErrorsID();
					outputGroupID = -1;
					try {
						String detector = ((StringToken) detectorParam.getToken()).stringValue();
						if (detector != null) {
							outputGroupID = NcdNexusUtils.makegroup(entryGroupID, detector + "_result", Nexus.DATA);
							if (inputDataID > 0) {
								NcdNexusUtils.makelink(inputDataID, outputGroupID);
							}
							if (inputErrorsID  > 0) {
								NcdNexusUtils.makelink(inputErrorsID, outputGroupID);
							}
							if (inputAxisDataID > 0) {
								NcdNexusUtils.makelink(inputAxisDataID, outputGroupID);
							}
							if (inputAxisErrorsID  > 0) {
								NcdNexusUtils.makelink(inputAxisErrorsID, outputGroupID);
							}
						}
					} catch (HDF5Exception e) {
						throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, "Error creating processing results group", this, e);
					} catch (IllegalActionException e) {
						throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, "Error reading detector name parameter", this, e);
					}
				}
			} catch (MessageException e) {
				throw new ProcessingException(ErrorCode.MSG_DELIVERY_FAILURE, "Error processing msg", this, message, e);
			}
		}
	}
	
	@Override
	protected void doWrapUp() throws TerminationException {
		try {
			NcdNexusUtils.closeH5id(outputGroupID);
		} catch (HDF5LibraryException e) {
			getLogger().info("Trying to close invalid NeXus identifier", e);
		}
	}
	
}
