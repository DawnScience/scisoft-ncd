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

package uk.ac.diamond.scisoft.ncd.passerelle.actors;

import java.util.ArrayList;
import java.util.Arrays;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.hdf5.Nexus;

import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.StringToken;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.Normalisation;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdProcessingSliceObject;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.v5.ActorContext;
import com.isencia.passerelle.actor.v5.ProcessRequest;
import com.isencia.passerelle.actor.v5.ProcessResponse;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;

/**
 * Actor for normalising scattering data using scaler values
 * 
 * 
 * @author Irakli Sikharulidze
 * 
 */
public class NcdNormalisationTransformer extends NcdAbstractDataTransformer {

	private static final long serialVersionUID = 5494752725472250946L;

	private String calibration;
	private Double absScaling;
	private int normChannel;

	// Normalisation data shapes
	private long[] framesCal;

	public StringParameter calibrationParam;
	public Parameter absScalingParam, normChannelParam;

	public static final String dataName = "Normalisation";

	private long calibrationGroupID, inputCalibrationID;
	private long normGroupID, normDataID, normErrorsID;

	private DataSliceIdentifiers calibrationIDs;

	public NcdNormalisationTransformer(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);

		calibrationParam = new StringParameter(this, "calibrationParam");
		absScalingParam = new Parameter(this, "absScalingParam", new DoubleToken(0.0));
		normChannelParam = new Parameter(this, "normChannelParam", new IntToken(-1));
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		try {

			calibration = ((StringToken) calibrationParam.getToken()).stringValue();

			calibrationGroupID = H5.H5Gopen(entryGroupID, calibration, HDF5Constants.H5P_DEFAULT);
			inputCalibrationID = H5.H5Dopen(calibrationGroupID, "data", HDF5Constants.H5P_DEFAULT);
			calibrationIDs = new DataSliceIdentifiers();
			calibrationIDs.setIDs(calibrationGroupID, inputCalibrationID);

			int rankCal = H5.H5Sget_simple_extent_ndims(calibrationIDs.dataspace_id);
			long[] tmpFramesCal = new long[rankCal];
			H5.H5Sget_simple_extent_dims(calibrationIDs.dataspace_id, tmpFramesCal, null);

			// This is a workaround to add extra dimensions to the end of scaler
			// data shape
			// to match them with scan data dimensions
			int extraDims = frames.length - dimension + 1 - rankCal;
			if (extraDims > 0) {
				rankCal += extraDims;
				for (int dm = 0; dm < extraDims; dm++) {
					tmpFramesCal = ArrayUtils.add(tmpFramesCal, 1);
				}
			}
			framesCal = Arrays.copyOf(tmpFramesCal, rankCal);

			for (int i = 0; i < frames.length - dimension; i++) {
				if (frames[i] != framesCal[i]) {
					frames[i] = Math.min(frames[i], framesCal[i]);
				}
			}

			normGroupID = NcdNexusUtils.makegroup(processingGroupID, dataName, Nexus.DETECT);
			long type = HDF5Constants.H5T_NATIVE_FLOAT;
			normDataID = NcdNexusUtils.makedata(normGroupID, "data", type, frames, true, "counts");
			type = HDF5Constants.H5T_NATIVE_DOUBLE;
			normErrorsID = NcdNexusUtils.makedata(normGroupID, "errors", type, frames, true, "counts");

			absScaling = ((DoubleToken) absScalingParam.getToken()).doubleValue();
			normChannel = ((IntToken) normChannelParam.getToken()).intValue();

			// TODO: add axis support
			// if (qaxis != null) {
			// setQaxis(qaxis, qaxisUnit);
			// writeQaxisData(frames.length, normGroupID);
			// }
			// writeNcdMetadata(normGroupID);
		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
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

		NcdProcessingSliceObject receivedObject;

		long filespaceID = -1;
		long typeID = -1;
		long memspaceID = -1;
		try {
			receivedObject = (NcdProcessingSliceObject) receivedMsg.getBodyContent();
			lock = receivedObject.getLock();
			SliceSettings sliceData = receivedObject.getSliceData();
			Dataset data = receivedObject.getData();
			Dataset qaxis = receivedObject.getAxis();

			Normalisation nm = new Normalisation();
			nm.setCalibChannel(normChannel);
			if (absScaling != null) {
				nm.setNormvalue(absScaling);
			}
			int[] dataShape = data.getShape();

			data = NcdDataUtils.flattenGridData(data, dimension);
			Dataset errors = data.getErrorBuffer();

			SliceSettings calibrationSliceParams = new SliceSettings(sliceData);
			calibrationSliceParams.setFrames(framesCal);
			Dataset dataCal = NcdNexusUtils.sliceInputData(calibrationSliceParams, calibrationIDs);
			Dataset calibngd = NcdDataUtils.flattenGridData(dataCal, 1);

			Object[] myobj = nm.process(data.getBuffer(), errors.getBuffer(), calibngd.getBuffer(), data.getShape()[0],
					data.getShape(), calibngd.getShape());

			float[] mydata = (float[]) myobj[0];
			double[] myerrors = (double[]) myobj[1];

			Dataset myres = new FloatDataset(mydata, dataShape);
			myres.setErrorBuffer(new DoubleDataset(myerrors, dataShape));

			int selectID = -1;
			int writeID = -1;

			lock.lock();

			long[] frames = sliceData.getFrames();
			long[] start_pos = (long[]) ConvertUtils.convert(sliceData.getStart(), long[].class);
			int sliceDim = sliceData.getSliceDim();
			int sliceSize = sliceData.getSliceSize();

			long[] start = Arrays.copyOf(start_pos, frames.length);

			long[] block = Arrays.copyOf(frames, frames.length);
			Arrays.fill(block, 0, sliceData.getSliceDim(), 1);
			block[sliceDim] = Math.min(frames[sliceDim] - start_pos[sliceDim], sliceSize);

			long[] count = new long[frames.length];
			Arrays.fill(count, 1);

			filespaceID = H5.H5Dget_space(normDataID);
			typeID = H5.H5Dget_type(normDataID);
			memspaceID = H5.H5Screate_simple(block.length, block, null);

			selectID = H5.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
			if (selectID < 0) {
				throw new HDF5Exception("Failed to allocate space fro writing Normalisation data");
			}

			writeID = H5.H5Dwrite(normDataID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, mydata);
			if (writeID < 0) {
				throw new HDF5Exception("Failed to write Normalisation data into the results file");
			}

			NcdNexusUtils.closeH5idList(new ArrayList<Long>(Arrays.asList(memspaceID, typeID, filespaceID)));

			filespaceID = H5.H5Dget_space(normErrorsID);
			typeID = H5.H5Dget_type(normErrorsID);
			memspaceID = H5.H5Screate_simple(block.length, block, null);
			selectID = H5.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
			if (selectID < 0) {
				throw new HDF5Exception("Failed to allocate space for writing Normalisation error data");
			}
			writeID = H5.H5Dwrite(normErrorsID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, myres
					.getError().getBuffer());
			if (writeID < 0) {
				throw new HDF5Exception("Failed to write Normalisation error data into the results file");
			}

			ManagedMessage outputMsg = createMessageFromCauses(receivedMsg);
			NcdProcessingSliceObject obj = new NcdProcessingSliceObject(myres, qaxis, sliceData, lock);
			outputMsg.setBodyContent(obj, "application/octet-stream");
			response.addOutputMessage(output, outputMsg);
		} catch (MessageException e) {
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e.getCause());
		} catch (HDF5LibraryException e) {
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e.getCause());
		} catch (HDF5Exception e) {
			throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e.getCause());
		} finally {
			if (lock != null && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
			try {
				NcdNexusUtils.closeH5idList(new ArrayList<Long>(Arrays.asList(memspaceID, typeID, filespaceID)));
			} catch (HDF5LibraryException e) {
				throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, e.getMessage(), this, e.getCause());
			}
		}
	}

}
