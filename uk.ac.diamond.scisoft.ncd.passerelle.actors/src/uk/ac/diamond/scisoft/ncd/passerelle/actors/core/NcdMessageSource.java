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

import org.dawb.hdf5.Nexus;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import ptolemy.data.ObjectToken;
import ptolemy.data.StringToken;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.Source;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;

public class NcdMessageSource extends Source {


	private boolean messageSent;
	private ReentrantLock lock;
	private String filename;
	private String detector;
	
	public Parameter lockParam;
	public StringParameter filenameParam, detectorParam;

	public NcdMessageSource(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);
		messageSent = false;
		
		filenameParam = new StringParameter(this, "filenameParam");
		detectorParam = new StringParameter(this, "detectorParam");
		lockParam = new Parameter(this, "lockParam");
	}

	@Override
	protected ManagedMessage getMessage() throws ProcessingException {
		ManagedMessage dataMsg = null;
		if (messageSent) {
			return null;
		}
		try {
			filename = ((StringToken) filenameParam.getToken()).stringValue();
			detector = ((StringToken) detectorParam.getToken()).stringValue();
			
			Object obj = ((ObjectToken) lockParam.getToken()).getValue();
			if (obj instanceof ReentrantLock) {
				lock = (ReentrantLock) obj;
			}
			
			int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
			int entryGroupID = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
			int detectorGroupID = H5.H5Gopen(entryGroupID, detector, HDF5Constants.H5P_DEFAULT);
			int inputDataID = H5.H5Dopen(detectorGroupID, "data", HDF5Constants.H5P_DEFAULT);
			int inputErrorsID = -1;
			try {
				inputErrorsID = H5.H5Dopen(detectorGroupID, "errors", HDF5Constants.H5P_DEFAULT);
			} catch (HDF5LibraryException e) {
				getLogger().info("Input dataset with error estimates wasn't found");
			}
			int processingGroupID = NcdNexusUtils.makegroup(entryGroupID, detector + "_processing", Nexus.INST);

			NcdProcessingObject msg = new NcdProcessingObject(entryGroupID, processingGroupID, detectorGroupID, inputDataID, inputErrorsID, lock);

			dataMsg = createMessage(msg, "application/octet-stream");
		} catch (IllegalActionException e) {
			messageSent = false;
			throw new ProcessingException(ErrorCode.MSG_CONSTRUCTION_ERROR, "Error creating msg", this, e);
		} catch (HDF5LibraryException e) {
			messageSent = false;
			throw new ProcessingException(ErrorCode.MSG_CONSTRUCTION_ERROR, "Error creating msg", this, e);
		} catch (HDF5Exception e) {
			messageSent = false;
			throw new ProcessingException(ErrorCode.MSG_CONSTRUCTION_ERROR, "Error creating msg", this, e);
		} catch (MessageException e) {
			messageSent = false;
			throw new ProcessingException(ErrorCode.MSG_CONSTRUCTION_ERROR, "Error creating msg", this, e);
		} finally {
			
		}
		messageSent = true;
		return dataMsg;
	}

}
