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

import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
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

	public NcdMessageSink(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);
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
					List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(
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
