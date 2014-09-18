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
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.PositionIterator;

import ptolemy.data.ObjectToken;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.DetectorResponse;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.core.ErrorCode;

/**
 * Actor for correcting input data for detector response
 * 
 * @author Irakli Sikharulidze
 */
public class NcdDetectorResponseForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = 5818690296954190264L;

	public Parameter detectorResponseParam;

	private Dataset drData;

	public NcdDetectorResponseForkJoinTransformer(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);

		detectorResponseParam = new Parameter(this, "detectorResponseParam", new ObjectToken());
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		try {

			Object obj = ((ObjectToken) detectorResponseParam.getToken()).getValue();
			if (obj != null) {
				if (obj instanceof Dataset) {
					drData = (Dataset) obj;
				} else {
					throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR,
							"Invalid detector response dataset", this, null);
				}
			}

			task = new DetectorResponseTask(true, null);

		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}

	private class DetectorResponseTask extends RecursiveAction {

		private static final long serialVersionUID = -8663334134891578246L;
		
		private boolean forkTask;
		private int[] pos;

		public DetectorResponseTask(boolean forkTask, int[] pos) {
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
				List<DetectorResponseTask> taskArray = new ArrayList<DetectorResponseTask>();
				if (itr.hasNext()) {
					DetectorResponseTask firstTask = new DetectorResponseTask(false, itr.getPos());
					while (itr.hasNext()) {
						DetectorResponseTask task = new DetectorResponseTask(false, itr.getPos());
						task.fork();
						taskArray.add(task);
					}

					firstTask.compute();
					Iterator<DetectorResponseTask> taskItr = taskArray.iterator();
					while (taskItr.hasNext()) {
						taskItr.next().join();
					}
				}
				return;
			}

			int filespaceID = -1;
			int typeID = -1;
			int memspaceID = -1;
			try {
				if (monitor.isCanceled()) {
					throw new OperationCanceledException(getName() + " stage has been cancelled.");
				}
				
				SliceSettings sliceData = new SliceSettings(frames, frames.length - dimension - 1, 1);
				int[] startPos = Arrays.copyOf(pos, frames.length);
				sliceData.setStart(startPos);

				DataSliceIdentifiers tmp_ids = new DataSliceIdentifiers();
				tmp_ids.setIDs(inputGroupID, inputDataID);

				lock.lock();
				Dataset inputData = NcdNexusUtils.sliceInputData(sliceData, tmp_ids);
				if (hasErrors) {
					DataSliceIdentifiers tmp_errors_ids = new DataSliceIdentifiers();
					tmp_errors_ids.setIDs(inputGroupID, inputErrorsID);
					Dataset inputErrors = NcdNexusUtils.sliceInputData(sliceData, tmp_errors_ids);
					inputData.setError(inputErrors);
				} else {
					// Use counting statistics if no input error estimates are available
					DoubleDataset inputErrorsBuffer = new DoubleDataset(inputData);
					inputData.setErrorBuffer(inputErrorsBuffer);
				}
				lock.unlock();

				DetectorResponse dr = new DetectorResponse();
				int[] dataShape = inputData.getShape();

				Dataset data = NcdDataUtils.flattenGridData(inputData, dimension);
				Dataset errors = data.getErrorBuffer();
				Dataset response = drData.squeeze();
				dr.setResponse(response);

				if (data.getRank() != response.getRank() + 1) {
					throw new IllegalArgumentException("response of wrong dimensionality");
				}

				int[] flatShape = data.getShape();
				Object[] myobj = dr.process(data.getBuffer(), errors.getBuffer(), flatShape[0], flatShape);
				float[] mydata = (float[]) myobj[0];
				double[] myerrors = (double[]) myobj[1];

				Dataset myres = new FloatDataset(mydata, dataShape);
				myres.setErrorBuffer(new DoubleDataset(myerrors, dataShape));

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
				int filespace_id = H5.H5Dget_space(resultDataID);
				int type_id = H5.H5Dget_type(resultDataID);
				int memspace_id = H5.H5Screate_simple(block.length, block, null);
				int selectID = H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count,
						block);
				if (selectID < 0) {
					throw new HDF5Exception("Failed to allocate space fro writing DetectorResponse data");
				}
				int writeID = H5.H5Dwrite(resultDataID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT,
						mydata);
				if (writeID < 0) {
					throw new HDF5Exception("Failed to write DetectorResponse data into the results file");
				}

				filespace_id = H5.H5Dget_space(resultErrorsID);
				type_id = H5.H5Dget_type(resultErrorsID);
				memspace_id = H5.H5Screate_simple(block.length, block, null);
				selectID = H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count,
						block);
				if (selectID < 0) {
					throw new HDF5Exception("Failed to allocate space fro writing DetectorResponse error data");
				}
				writeID = H5.H5Dwrite(resultErrorsID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT,
						myres.getError().getBuffer());
				if (writeID < 0) {
					throw new HDF5Exception("Failed to write DetectorResponse error data into the results file");
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
					NcdNexusUtils.closeH5idList(new ArrayList<Integer>(Arrays.asList(memspaceID, typeID, filespaceID)));
				} catch (HDF5LibraryException e) {
					task.completeExceptionally(e);
				}
			}
		}
	}
}
