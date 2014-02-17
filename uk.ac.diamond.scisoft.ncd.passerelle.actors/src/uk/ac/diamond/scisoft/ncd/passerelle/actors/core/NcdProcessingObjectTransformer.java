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
import java.util.concurrent.locks.ReentrantLock;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.apache.commons.math3.util.MultidimensionalCounter.Iterator;
import org.dawb.passerelle.common.message.DataMessageComponent;

import ptolemy.data.IntToken;
import ptolemy.data.StringToken;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
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

/**
 * Actor for selecting subset of input data
 * 
 * @author Irakli Sikharulidze
 */
public class NcdProcessingObjectTransformer extends Actor {

	private static final long serialVersionUID = -3361012226565001162L;
	
	private String format, datasetName;
	private int dimension;
	public StringParameter datasetNameParam, formatParam;
	private Parameter dimensionParam;

	private int[] selectedShape;
	private ArrayList<int[]> indexList;

	public Port input;
	public Port output;

	private int inputGroupID;
	private int inputDataID;
	private int inputErrorsID;

	private ReentrantLock lock;

	private long[] frames;
	private boolean hasErrors;

	public NcdProcessingObjectTransformer(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);

		input = PortFactory.getInstance().createInputPort(this, "input", NcdProcessingObject.class);
		output = PortFactory.getInstance().createOutputPort(this, "result");
		
		dimensionParam = new Parameter(this, "dimensionParam", new IntToken(-1));
		formatParam = new StringParameter(this, "formatParam");
		datasetNameParam = new StringParameter(this, "datasetNameParam");
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		try {

			format = ((StringToken) formatParam.getToken()).stringValue();
			datasetName = ((StringToken) datasetNameParam.getToken()).stringValue();
			dimension = ((IntToken) dimensionParam.getToken()).intValue();
			
		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}

	private int[] getResultDataShape() {
		selectedShape = (int[]) ConvertUtils.convert(Arrays.copyOf(frames, frames.length - dimension), int[].class);
		indexList = NcdDataUtils.createSliceList(format, (int[]) ConvertUtils.convert(selectedShape, int[].class));
		for (int i = 0; i < selectedShape.length; i++) {
			selectedShape[i] = indexList.get(i).length;
		}
		int[] imageSize = (int[]) ConvertUtils.convert(Arrays.copyOfRange(frames, frames.length - dimension, frames.length), int[].class);
		int[] resultDataShape = ArrayUtils.addAll(selectedShape, imageSize);
		return resultDataShape;
	}

	@Override
	protected void process(ActorContext ctxt, ProcessRequest request, ProcessResponse response)
			throws ProcessingException {

		ManagedMessage receivedMsg = request.getMessage(input);

		try {
			NcdProcessingObject receivedObject = (NcdProcessingObject) receivedMsg.getBodyContent();
			inputGroupID = receivedObject.getInputGroupID();
			inputDataID = receivedObject.getInputDataID();
			inputErrorsID = receivedObject.getInputErrorsID();
			lock = receivedObject.getLock();

			lock.lock();

			configureActorParameters();
			int[] resultShape = getResultDataShape();
			
			MultidimensionalCounter frameCounter = new MultidimensionalCounter(selectedShape);
			Iterator iter = frameCounter.iterator();
			AbstractDataset data = AbstractDataset.zeros(resultShape, AbstractDataset.FLOAT32);
			data.setName(datasetName);
			AbstractDataset errors = null;
			if (hasErrors) {
				errors = AbstractDataset.zeros(resultShape, AbstractDataset.FLOAT64);
			}
			while (iter.hasNext()) {
				iter.next();
				int[] frame = iter.getCounts();
				int[] gridFrame = new int[resultShape.length];
				Arrays.fill(gridFrame, 0);
				for (int i = 0; i < selectedShape.length; i++) {
					gridFrame[i] = indexList.get(i)[frame[i]];
				}

				SliceSettings sliceData = new SliceSettings(frames, frames.length - dimension - 1, 1);
				sliceData.setStart(gridFrame);
				
				DataSliceIdentifiers ids = new DataSliceIdentifiers();
				ids.setIDs(inputGroupID, inputDataID);
				AbstractDataset value = NcdNexusUtils.sliceInputData(sliceData, ids);
				int[] start = Arrays.copyOf(frame, resultShape.length);
				int[] stop = Arrays.copyOf(resultShape, resultShape.length);
				for (int i = 0; i < selectedShape.length; i++) {
					stop[i] = start[i] + 1;
				}

				data.setSlice(value, start, stop, null);
				if (hasErrors && errors != null) {
					DataSliceIdentifiers errors_ids = new DataSliceIdentifiers();
					errors_ids.setIDs(inputGroupID, inputErrorsID);
					AbstractDataset error = NcdNexusUtils.sliceInputData(sliceData, errors_ids);
					errors.setSlice(error, start, stop, null);
				}
			}
			if (hasErrors && errors != null) {
				data.setErrorBuffer(errors);
			}

			ManagedMessage outputMsg = createMessageFromCauses(receivedMsg);
			DataMessageComponent obj = new DataMessageComponent();
			obj.addList(datasetName, data);
			outputMsg.setBodyContent(obj, "application/octet-stream");
			response.addOutputMessage(output, outputMsg);
		} catch (MessageException e) {
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e);
		} catch (HDF5Exception e) {
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e);
		} finally {
			if (lock != null && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	private void configureActorParameters() throws HDF5Exception {
		int inputDataSpaceID = H5.H5Dget_space(inputDataID);
		int rank = H5.H5Sget_simple_extent_ndims(inputDataSpaceID);
		frames = new long[rank];
		H5.H5Sget_simple_extent_dims(inputDataSpaceID, frames, null);
		NcdNexusUtils.closeH5id(inputDataSpaceID);
		if (datasetName == null || datasetName.isEmpty()) {
			String[] name = new String[] {""};
			final long nameSize = H5.H5Iget_name(inputDataID, name, 1L) + 1;
			H5.H5Iget_name(inputDataID, name, nameSize);
			String[] nameTree = name[0].split("/");
			datasetName = nameTree[nameTree.length -1];
		}
		
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
	}

}
