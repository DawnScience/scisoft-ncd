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

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;

import ptolemy.data.DoubleToken;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Dataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;
import uk.ac.diamond.scisoft.analysis.dataset.PositionIterator;
import uk.ac.diamond.scisoft.ncd.core.BackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdProcessingObject;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.actor.v5.ProcessRequest;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.core.Port;
import com.isencia.passerelle.core.PortFactory;
import com.isencia.passerelle.core.PortMode;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;

/**
 * Actor for subtracting background from input data
 * 
 * @author Irakli Sikharulidze
 */
public class NcdBackgroundSubtractionForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = 2085171025175844254L;

	public Port bgInput;

	private Double bgScaling;
	public Parameter bgScalingParam;

	private int bgDetectorGroupID, bgDataID, bgErrorsID;

	private long[] bgFrames;

	private boolean hasBgErrors;

	public NcdBackgroundSubtractionForkJoinTransformer(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);

		bgInput = PortFactory.getInstance().createInputPort(this, "bgInput", PortMode.PULL, NcdProcessingObject.class);
		
		dataName = "BackgroundSubtraction";

		bgScalingParam = new Parameter(this, "bgScalingParam", new DoubleToken(Double.NaN));
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		try {

			task = new BackgroundSubtractionTask(true, null);

			bgScaling = ((DoubleToken) bgScalingParam.getToken()).doubleValue();

		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}

	@Override
	protected void readAdditionalPorts(ProcessRequest request) throws MessageException {
		ManagedMessage receivedMsg = request.getMessage(bgInput);

		NcdProcessingObject receivedObject = (NcdProcessingObject) receivedMsg.getBodyContent();
		bgDetectorGroupID = receivedObject.getInputGroupID();
		bgDataID = receivedObject.getInputDataID();
		bgErrorsID = receivedObject.getInputErrorsID();
		hasBgErrors = false;
		if (bgErrorsID > 0) {
			try {
				final int type = H5.H5Iget_type(bgErrorsID);
				if (type != HDF5Constants.H5I_BADID) {
					hasBgErrors = true;
				}
			} catch (HDF5LibraryException e) {
				getLogger().info("Background error values dataset wasn't found");
			}
		}
	}

	@Override
	protected void configureActorParameters() throws HDF5Exception {
		super.configureActorParameters();
		
		int bgDataSpaceID = H5.H5Dget_space(bgDataID);
		int rank = H5.H5Sget_simple_extent_ndims(bgDataSpaceID);
		bgFrames = new long[rank];
		H5.H5Sget_simple_extent_dims(bgDataSpaceID, bgFrames, null);
		NcdNexusUtils.closeH5id(bgDataSpaceID);
		
		String[] name = new String[] {""};
		long nameSize = H5.H5Iget_name(bgDataID, name, 1L) + 1;
		H5.H5Iget_name(bgDataID, name, nameSize);
		String bgDatasetName = name[0];
		
		name = new String[] {""};
		nameSize = H5.H5Iget_name(bgErrorsID, name, 1L) + 1;
		H5.H5Iget_name(bgErrorsID, name, nameSize);
		String bgErrorsName = name[0];
		
		String bgFileName = H5.H5Fget_name(bgDataID);
		
		// Store background filename used in data reduction
		int strType = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		H5.H5Tset_size(strType, bgFileName.length());
		int metadataID = NcdNexusUtils.makedata(resultGroupID, "background_filename", strType, new long[] {1});
		
		int filespaceID = H5.H5Dget_space(metadataID);
		int memspaceID = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespaceID);
		H5.H5Dwrite(metadataID, strType, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, bgFileName.getBytes());
		
		H5.H5Sclose(filespaceID);
		H5.H5Sclose(memspaceID);
		H5.H5Tclose(strType);
		H5.H5Dclose(metadataID);
		
		// Make link to the background dataset and store background filename
		H5.H5Lcreate_external(bgFileName, bgDatasetName, resultGroupID, "background", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		H5.H5Lcreate_external(bgFileName, bgErrorsName, resultGroupID, "background_errors", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
	}

	private class BackgroundSubtractionTask extends RecursiveAction {

		private static final long serialVersionUID = -4104769967692484959L;
		
		private boolean forkTask;
		private int[] pos;

		public BackgroundSubtractionTask(boolean forkTask, int[] pos) {
			super();

			this.forkTask = forkTask;
			if (pos != null) {
				this.pos = Arrays.copyOf(pos, pos.length);
			}
		}
		
		@Override
		protected void compute() {

			if (forkTask) {
				int[] grid = (int[]) ConvertUtils
						.convert(Arrays.copyOf(frames, frames.length - dimension), int[].class);
				PositionIterator itr = new PositionIterator(grid);
				List<BackgroundSubtractionTask> taskArray = new ArrayList<BackgroundSubtractionTask>();
				if (itr.hasNext()) {
					BackgroundSubtractionTask firstTask = new BackgroundSubtractionTask(false, itr.getPos());
					while (itr.hasNext()) {
						BackgroundSubtractionTask task = new BackgroundSubtractionTask(false, itr.getPos());
						task.fork();
						taskArray.add(task);
					}

					firstTask.compute();
					Iterator<BackgroundSubtractionTask> taskItr = taskArray.iterator();
					while (taskItr.hasNext()) {
						taskItr.next().join();
					}
				}
				return;
			}

			int filespaceID = -1;
			int typeID = -1;
			int memspaceID = -1;
			int selectID = -1;
			int writeID = -1;

			try {
				SliceSettings sliceData = new SliceSettings(frames, frames.length - dimension - 1, 1);
				int[] startPos = Arrays.copyOf(pos, frames.length);
				sliceData.setStart(startPos);

				DataSliceIdentifiers tmp_ids = new DataSliceIdentifiers();
				tmp_ids.setIDs(inputGroupID, inputDataID);

				lock.lock();
				AbstractDataset inputData = NcdNexusUtils.sliceInputData(sliceData, tmp_ids);
				if (hasErrors) {
					DataSliceIdentifiers tmp_errors_ids = new DataSliceIdentifiers();
					tmp_errors_ids.setIDs(inputGroupID, inputErrorsID);
					AbstractDataset inputErrors = NcdNexusUtils.sliceInputData(sliceData, tmp_errors_ids);
					inputData.setError(inputErrors);
				} else {
					// Use counting statistics if no input error estimates are available
					DoubleDataset inputErrorsBuffer = new DoubleDataset(inputData);
					inputData.setErrorBuffer(inputErrorsBuffer);
				}
				lock.unlock();

				SliceSettings bgSliceData = new SliceSettings(bgFrames, bgFrames.length - dimension - 1, 1);
				
				// Account for mismatch in rank of input and background data
				int[] bgPos = new int[bgFrames.length - dimension];
				for (int i = bgPos.length - 1; i >= 0; i--) {
					int j = i + pos.length - bgPos.length;
					if (j < 0) {
						bgPos[i] = 0;
					} else {
						bgPos[i] = (bgFrames[i] > 1 ? pos[j] : 0);
					}
				}
				
				int[] bgStartPos = Arrays.copyOf(bgPos, bgFrames.length);
				bgSliceData.setStart(bgStartPos);
				
				DataSliceIdentifiers bgIDs = new DataSliceIdentifiers();
				bgIDs.setIDs(bgDetectorGroupID, bgDataID);
				
				lock.lock();
				AbstractDataset bgData = NcdNexusUtils.sliceInputData(bgSliceData, bgIDs);
				if (hasBgErrors) {
					DataSliceIdentifiers bgErrorsIDs = new DataSliceIdentifiers();
					bgErrorsIDs.setIDs(bgDetectorGroupID, bgErrorsID);
					AbstractDataset bgErrors = NcdNexusUtils.sliceInputData(bgSliceData, bgErrorsIDs);
					bgData.setError(bgErrors);
				} else {
					// Use counting statistics if no input error estimates are available
					DoubleDataset bgErrors = (DoubleDataset) DatasetUtils.cast(bgData.clone(), Dataset.FLOAT64);
					bgData.setErrorBuffer(bgErrors);
				}
				lock.unlock();
				
				if (bgScaling != null && !bgScaling.isNaN()) {
					bgData.imultiply(bgScaling);
					AbstractDataset bgErrors = ((AbstractDataset) bgData.getErrorBuffer()).clone();
					bgErrors.imultiply(bgScaling * bgScaling);
					bgData.setErrorBuffer(bgErrors);
				}

				AbstractDataset data = NcdDataUtils.flattenGridData(inputData, dimension);
				AbstractDataset errors = NcdDataUtils.flattenGridData((AbstractDataset) inputData.getErrorBuffer(),
						dimension);

				AbstractDataset background = bgData.squeeze();

				BackgroundSubtraction bs = new BackgroundSubtraction();
				bs.setBackground(background);

				int[] flatShape = data.getShape();
				Object[] myobj = bs.process(data.getBuffer(), errors.getBuffer(), flatShape);
				float[] mydata = (float[]) myobj[0];
				double[] myerror = (double[]) myobj[1];

				AbstractDataset myres = new FloatDataset(mydata, inputData.getShape());
				myres.setErrorBuffer(myerror);


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
					throw new HDF5Exception("Failed to allocate space for writing BackgroundSubtraction data");
				}
				writeID = H5.H5Dwrite(resultDataID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT,
						myres.getBuffer());
				if (writeID < 0) {
					throw new HDF5Exception("Failed to write BackgroundSubtraction data into the results file");
				}

				NcdNexusUtils.closeH5idList(new ArrayList<Integer>(Arrays.asList(memspaceID, typeID, filespaceID)));
				
				filespaceID = H5.H5Dget_space(resultErrorsID);
				typeID = H5.H5Dget_type(resultErrorsID);
				memspaceID = H5.H5Screate_simple(block.length, block, null);
				selectID = H5
						.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
				if (selectID < 0) {
					throw new HDF5Exception("Failed to allocate space for writing BackgroundSubtraction error data");
				}
				writeID = H5.H5Dwrite(resultErrorsID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT,
						myres.getError().getBuffer());
				if (writeID < 0) {
					throw new HDF5Exception("Failed to write BackgroundSubtraction error data into the results file");
				}

			} catch (HDF5LibraryException e) {
				throw new RuntimeException(e);
			} catch (HDF5Exception e) {
				throw new RuntimeException(e);
			} finally {
				if (lock != null && lock.isHeldByCurrentThread()) {
					lock.unlock();
				}
				try {
					NcdNexusUtils.closeH5idList(new ArrayList<Integer>(Arrays.asList(memspaceID, typeID, filespaceID)));
				} catch (HDF5LibraryException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}
}
