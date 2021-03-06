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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RecursiveAction;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.dawnsci.hdf5.HDF5Utils;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.PositionIterator;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.actor.TerminationException;
import com.isencia.passerelle.core.ErrorCode;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;
import hdf.hdf5lib.structs.H5L_info_t;
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

/**
 * Actor for normalising scattering data using scaler values
 * 
 * @author Irakli Sikharulidze
 */
public class NcdNormalisationForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = 5494752725472250946L;

	private String calibration;
	private Double absScaling;
	private int normChannel;

	// Normalisation data shapes
	private long[] framesCal;

	public StringParameter calibrationParam;
	public Parameter absScalingParam, normChannelParam;

	private long linkFileID = -1;
	private long calibrationGroupID, inputCalibrationID;

	private DataSliceIdentifiers calibrationIDs;

	public NcdNormalisationForkJoinTransformer(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);

		calibrationParam = new StringParameter(this, "calibrationParam");
		absScalingParam = new Parameter(this, "absScalingParam", new DoubleToken(Double.NaN));
		normChannelParam = new Parameter(this, "normChannelParam", new IntToken(-1));
		
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		try {

			calibration = ((StringToken) calibrationParam.getToken()).stringValue();

			absScaling = ((DoubleToken) absScalingParam.getToken()).doubleValue();
			normChannel = ((IntToken) normChannelParam.getToken()).intValue();
			
			task = new NormalisationTask(true, null);

		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}

	@Override
	protected void configureActorParameters() throws HDF5Exception {
		super.configureActorParameters();
		
		calibrationGroupID = H5.H5Gopen(entryGroupID, calibration, HDF5Constants.H5P_DEFAULT);
		H5L_info_t linkInfo = H5.H5Lget_info(calibrationGroupID, "data", HDF5Constants.H5P_DEFAULT);
		if (linkInfo.type == HDF5Constants.H5L_TYPE_EXTERNAL) {
			String[] buff = new String[(int) linkInfo.address_val_size];
			H5.H5Lget_value(calibrationGroupID, "data", buff, HDF5Constants.H5P_DEFAULT);
			if (buff[0] != null && buff[1] != null) {
				String linkData = buff[0];
				String linkFilename = buff[1];
				linkFileID = HDF5Utils.H5Fopen(linkFilename, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
				inputCalibrationID = H5.H5Dopen(linkFileID, linkData, HDF5Constants.H5P_DEFAULT);
			} else {
				throw new HDF5Exception("Invalid external link data for Normalisation dataset.");
			}
		} else {
			inputCalibrationID = H5.H5Dopen(calibrationGroupID, "data", HDF5Constants.H5P_DEFAULT);
		}
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
	}
	
	private class NormalisationTask extends RecursiveAction {

		private static final long serialVersionUID = 5266823189518140464L;
		
		private boolean forkTask;
		private int[] pos;

		public NormalisationTask(boolean forkTask, int[] pos) {
			super();

			this.forkTask = forkTask;
			if (pos != null) {
				this.pos = Arrays.copyOf(pos, pos.length);
			}
		}

		@Override
		protected void compute() {

			if (forkTask) {
				int[] grid = (int[]) ConvertUtils.convert(Arrays.copyOf(frames, frames.length-dimension), int[].class);
				PositionIterator itr = new PositionIterator(grid);
				List<NormalisationTask> taskArray = new ArrayList<NormalisationTask>();
				if (itr.hasNext()) {
					NormalisationTask firstTask = new NormalisationTask(false, itr.getPos());
					while (itr.hasNext()) {
						NormalisationTask task = new NormalisationTask(false, itr.getPos());
						task.fork();
						taskArray.add(task);
					}

					firstTask.compute();
					Iterator<NormalisationTask> taskItr = taskArray.iterator();
					while (taskItr.hasNext()) {
						taskItr.next().join();
					}
				}
				return;
			}

			long filespaceID = -1;
			long typeID = -1;
			long memspaceID = -1;
			try {
				if (monitor.isCanceled()) {
					throw new OperationCanceledException(getName() + " stage has been cancelled.");
				}
				
				SliceSettings sliceData = new SliceSettings(frames, frames.length - dimension - 1, 1);
				int[] startPos = Arrays.copyOf(pos, frames.length);
				sliceData.setStart(startPos);
				
				SliceSettings calibrationSliceParams = new SliceSettings(sliceData);
				calibrationSliceParams.setFrames(framesCal);
				
				DataSliceIdentifiers tmp_ids = new DataSliceIdentifiers();
				tmp_ids.setIDs(inputGroupID, inputDataID);

				lock.lock();
				Dataset inputData = NcdNexusUtils.sliceInputData(sliceData, tmp_ids);
				Dataset dataCal = NcdNexusUtils.sliceInputData(calibrationSliceParams, calibrationIDs);
				if (hasErrors) {
					DataSliceIdentifiers tmp_errors_ids = new DataSliceIdentifiers();
					tmp_errors_ids.setIDs(inputGroupID, inputErrorsID);
					Dataset inputErrors = NcdNexusUtils.sliceInputData(sliceData, tmp_errors_ids);
					inputData.setErrors(inputErrors);
				} else {
					// Use counting statistics if no input error estimates are available 
					DoubleDataset inputErrorsBuffer = inputData.copy(DoubleDataset.class);
					inputData.setErrorBuffer(inputErrorsBuffer);
				}
				lock.unlock();

				
				Normalisation nm = new Normalisation();
				nm.setCalibChannel(normChannel);
				if (absScaling != null && !absScaling.isNaN()) {
					nm.setNormvalue(absScaling);
				}
				int[] dataShape = inputData.getShape();

				Dataset data = NcdDataUtils.flattenGridData(inputData, dimension);
				// We need to get variance values for further calculations
				Dataset errors = inputData.getErrorBuffer();
				Dataset calibngd = NcdDataUtils.flattenGridData(dataCal, 1);

				Object[] myobj = nm.process(data.getBuffer(), errors.getBuffer(), calibngd.getBuffer(),
						data.getShape()[0], data.getShape(), calibngd.getShape());

				float[] mydata = (float[]) myobj[0];
				double[] myerrors = (double[]) myobj[1];

				Dataset myres = DatasetFactory.createFromObject(mydata, dataShape);
				myres.setErrorBuffer(DatasetFactory.createFromObject(myerrors, dataShape));

				int selectID = -1;
				int writeID = -1;

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

				lock.lock();
				filespaceID = H5.H5Dget_space(resultDataID);
				typeID = H5.H5Dget_type(resultDataID);
				memspaceID = H5.H5Screate_simple(block.length, block, null);

				selectID = H5
						.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
				if (selectID < 0) {
					throw new HDF5Exception("Failed to allocate space fro writing Normalisation data");
				}

				writeID = H5.H5Dwrite(resultDataID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, mydata);
				if (writeID < 0) {
					throw new HDF5Exception("Failed to write Normalisation data into the results file");
				}

				NcdNexusUtils.closeH5idList(new ArrayList<Long>(Arrays.asList(memspaceID, typeID, filespaceID)));

				filespaceID = H5.H5Dget_space(resultErrorsID);
				typeID = H5.H5Dget_type(resultErrorsID);
				memspaceID = H5.H5Screate_simple(block.length, block, null);
				selectID = H5
						.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
				if (selectID < 0) {
					throw new HDF5Exception("Failed to allocate space for writing Normalisation error data");
				}
				writeID = H5.H5Dwrite(resultErrorsID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, myres
						.getErrors().getBuffer());
				if (writeID < 0) {
					throw new HDF5Exception("Failed to write Normalisation error data into the results file");
				}

			} catch (HDF5LibraryException e) {
				task.completeExceptionally(e);
			} catch (HDF5Exception e) {
				task.completeExceptionally(e);
			} catch (OperationCanceledException e) {
				task.completeExceptionally(e);
			} finally {
				if (lock != null && lock.isHeldByCurrentThread()) {
					lock.unlock();
				}
				try {
					NcdNexusUtils.closeH5idList(new ArrayList<Long>(Arrays.asList(memspaceID, typeID, filespaceID)));
				} catch (HDF5LibraryException e) {
					task.completeExceptionally(e);
				}
			}
		}
	}
	
	@Override
	protected void doWrapUp() throws TerminationException {
		try {
			List<Long> identifiers = new ArrayList<Long>(Arrays.asList(
					inputCalibrationID,
					calibrationGroupID,
					linkFileID));

			NcdNexusUtils.closeH5idList(identifiers);
		} catch (HDF5LibraryException e) {
			getLogger().info("Error closing NeXus handle identifier", e);
		}
		super.doWrapUp();
	}
	
}
