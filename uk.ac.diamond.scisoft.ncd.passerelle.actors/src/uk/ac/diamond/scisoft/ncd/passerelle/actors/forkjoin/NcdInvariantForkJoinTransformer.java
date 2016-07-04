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
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.PositionIterator;

import com.isencia.passerelle.actor.InitializationException;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.Invariant;
import uk.ac.diamond.scisoft.ncd.core.SaxsInvariant;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.data.plots.PorodPlotData;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

/**
 * Actor for calculating invariant value for input data
 * 
 * @author Irakli Sikharulidze
 */
public class NcdInvariantForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = 5836823100154893276L;

	private Dataset inputAxis;
	private long[] axisShape;

	private long porodDataID, porodErrorsID;
	
	public NcdInvariantForkJoinTransformer(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);

	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		task = new InvariantTask(true, null);
		inputAxis = null;
	}

	@Override
	protected long[] getResultDataShape() {
		return Arrays.copyOf(frames, frames.length - dimension);
	}

	@Override
	protected int getResultDimension() {
		return 1;
	}
	
	@Override
	protected void configureActorParameters() throws HDF5Exception {
		super.configureActorParameters();
		
		if (inputAxisDataID != -1) {
			long spaceID = H5.H5Dget_space(inputAxisDataID);
			int rank = H5.H5Sget_simple_extent_ndims(spaceID);
			axisShape = new long[rank];
			H5.H5Sget_simple_extent_dims(spaceID, axisShape, null);
			NcdNexusUtils.closeH5id(spaceID);

			DataSliceIdentifiers axisIDs = new DataSliceIdentifiers();
			axisIDs.setIDs(inputGroupID, inputAxisDataID);
			SliceSettings axisSliceParams = new SliceSettings(axisShape, 0, (int) axisShape[0]);
			axisIDs.setSlice(axisSliceParams);
			inputAxis = NcdNexusUtils.sliceInputData(axisSliceParams, axisIDs);

			if (inputAxisErrorsID > 0) {
				DataSliceIdentifiers axisErrorsIDs = new DataSliceIdentifiers();
				axisErrorsIDs.setIDs(inputGroupID, inputAxisErrorsID);
				axisErrorsIDs.setSlice(axisSliceParams);
				Dataset inputAxisErrors = NcdNexusUtils.sliceInputData(axisSliceParams, axisErrorsIDs);
				inputAxis.setError(inputAxisErrors);
			}

			long type = HDF5Constants.H5T_NATIVE_FLOAT;
			porodDataID = NcdNexusUtils.makedata(resultGroupID, "porod_fit", type, frames, false, "N/A");
			type = HDF5Constants.H5T_NATIVE_DOUBLE;
			porodErrorsID = NcdNexusUtils.makedata(resultGroupID, "porod_fit_errors", type, frames, false, "N/A");
		}
	}
	
	private class InvariantTask extends RecursiveAction {

		/**
		 * 
		 */
		private static final long serialVersionUID = -3706726892055486185L;
		private boolean forkTask;
		private int[] pos;

		public InvariantTask(boolean forkTask, int[] pos) {
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
				List<InvariantTask> taskArray = new ArrayList<InvariantTask>();
				if (itr.hasNext()) {
					InvariantTask firstTask = new InvariantTask(false, itr.getPos());
					while (itr.hasNext()) {
						InvariantTask task = new InvariantTask(false, itr.getPos());
						task.fork();
						taskArray.add(task);
					}

					firstTask.compute();
					Iterator<InvariantTask> taskItr = taskArray.iterator();
					while (taskItr.hasNext()) {
						taskItr.next().join();
					}
				}
				return;
			}

			long filespaceID = -1;
			long typeID = -1;
			long memspaceID = -1;
			long selectID = -1;
			long writeID = -1;
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
					DoubleDataset inputErrorsBuffer = inputData.copy(DoubleDataset.class);
					inputData.setErrorBuffer(inputErrorsBuffer);
				}
				lock.unlock();

				int[] dataShape = Arrays.copyOf(inputData.getShape(), inputData.getRank() - dimension);
				Dataset data = NcdDataUtils.flattenGridData(inputData, dimension);
				Dataset errors = data.getErrorBuffer();
				
				Object[] myobj;
				Dataset axis = null;
				
				if (inputAxis != null) {
					axis = inputAxis.clone().squeeze();
					SaxsInvariant inv = new SaxsInvariant();
					myobj = inv.process(data.getBuffer(), errors.getBuffer(), axis.getBuffer(), data.getShape());
				} else {
					Invariant inv = new Invariant();
					myobj = inv.process(data.getBuffer(), errors.getBuffer(), data.getShape());
				}
				float[] mydata = (float[]) myobj[0];
				double[] myerrors = (double[]) myobj[1];

				Dataset myres = DatasetFactory.createFromObject(mydata, dataShape);
				myres.setErrorBuffer(DatasetFactory.createFromObject(myerrors, dataShape));

				long[] frames = getResultDataShape();
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
					throw new HDF5Exception("Failed to allocate space fro writing Invariant data");
				}
				writeID = H5.H5Dwrite(resultDataID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, mydata);
				if (writeID < 0) {
					throw new HDF5Exception("Failed to write Invariant data into the results file");
				}

				filespaceID = H5.H5Dget_space(resultErrorsID);
				typeID = H5.H5Dget_type(resultErrorsID);
				memspaceID = H5.H5Screate_simple(block.length, block, null);
				selectID = H5
						.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
				if (selectID < 0) {
					throw new HDF5Exception("Failed to allocate space fro writing Invariant error data");
				}
				writeID = H5.H5Dwrite(resultErrorsID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, myres
						.getError().getBuffer());
				if (writeID < 0) {
					throw new HDF5Exception("Failed to write Invariant error data into the results file");
				}
				
				if (axis != null) {
					int[] rgDataShape = Arrays.copyOf(dataShape, dataShape.length + 1);
					rgDataShape[rgDataShape.length - 1] = data.getSize();
					
					PorodPlotData plotData = (PorodPlotData) SaxsAnalysisPlotType.POROD_PLOT.getSaxsPlotDataObject();
					SimpleRegression regression = plotData.getPorodPlotParameters(data.squeeze(), axis);
					if (regression != null) {
						Dataset tmpPorodDataset = plotData.getFitData(axis, regression);
						
						DataSliceIdentifiers porodDataIDs = new DataSliceIdentifiers();
						porodDataIDs.setIDs(resultGroupID, porodDataID);
						porodDataIDs.setSlice(sliceData);
						writeResults(porodDataIDs, tmpPorodDataset, rgDataShape, 1);
						
						DataSliceIdentifiers porodErrorsIDs = new DataSliceIdentifiers();
						porodErrorsIDs.setIDs(resultGroupID, porodErrorsID);
						porodErrorsIDs.setSlice(sliceData);
						writeResults(porodErrorsIDs, tmpPorodDataset.getError(), rgDataShape, 1);
					}
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
	
	private void writeResults(DataSliceIdentifiers dataIDs, Dataset data, int[] dataShape, int dim)
			throws HDF5Exception {

		int resRank = dataShape.length - dim + 1;
		int integralLength = data.getShape()[data.getRank() - 1];

		long[] resStart = Arrays.copyOf(dataIDs.start, resRank);
		long[] resCount = Arrays.copyOf(dataIDs.count, resRank);
		long[] resBlock = Arrays.copyOf(dataIDs.block, resRank);
		resBlock[resRank - 1] = integralLength;

		long filespaceID = H5.H5Dget_space(dataIDs.dataset_id);
		long typeID = H5.H5Dget_type(dataIDs.dataset_id);
		long memspaceID = H5.H5Screate_simple(resRank, resBlock, null);
		int selectID = H5.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, resStart, resBlock, resCount, resBlock);
		if (selectID < 0) {
			throw new HDF5Exception("Error allocating space for writing SAXS plot data");
		}
		int writeID = H5.H5Dwrite(dataIDs.dataset_id, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, data.getBuffer());
		if (writeID < 0) {
			throw new HDF5Exception("Error writing SAXS plot data");
		}
		H5.H5Sclose(filespaceID);
		H5.H5Sclose(memspaceID);
		H5.H5Tclose(typeID);
	}
	
	@Override
	protected void writeAxisData() throws HDF5Exception {
		if (inputAxis == null) {
			resultAxisDataID = -1;
			resultAxisErrorsID = -1;
		}
	}
	
}
