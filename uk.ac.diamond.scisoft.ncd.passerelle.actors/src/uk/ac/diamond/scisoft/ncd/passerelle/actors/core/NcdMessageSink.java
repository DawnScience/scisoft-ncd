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

import org.dawb.hdf5.Nexus;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import ptolemy.data.StringToken;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.Sink;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;

public class NcdMessageSink extends Sink {

	public StringParameter detectorParam;

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
					int entryGroupID = content.getEntryGroupID();
					int processingGroupID = content.getProcessingGroupID();
					int resultGroupID = content.getInputGroupID();
					int resultDataID = content.getInputDataID();
					int resultErrorsID = content.getInputErrorsID();
					int outputGroupID = -1;
					try {
						String detector = ((StringToken) detectorParam.getToken()).stringValue();
						if (detector != null) {
							outputGroupID = NcdNexusUtils.makegroup(entryGroupID, detector + "_result", Nexus.DATA);
							H5.H5Lcopy(resultGroupID, "./data", outputGroupID, "./data", HDF5Constants.H5P_DEFAULT,
									HDF5Constants.H5P_DEFAULT);
							if (resultErrorsID != -1) {
								H5.H5Lcopy(resultGroupID, "./errors", outputGroupID, "./errors",
										HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
							}
						}
					} catch (HDF5Exception e) {
						throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, "Error creating processing results group", this, e);
					} catch (IllegalActionException e) {
						throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, "Error reading detector name parameter", this, e);
					}
				    
					List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(
							outputGroupID,
							resultDataID,
							resultErrorsID,
							resultGroupID,
							processingGroupID,
							entryGroupID));
					try {
						NcdNexusUtils.closeH5idList(identifiers);
					} catch (HDF5LibraryException e) {
						getLogger().info("Trying to close invalid Nexus handle");
					}
				}
			} catch (MessageException e) {
				throw new ProcessingException(ErrorCode.MSG_DELIVERY_FAILURE, "Error processing msg", this, message, e);
			}
		}
	}
}
