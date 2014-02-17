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

import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;
import uk.ac.diamond.scisoft.analysis.dataset.PositionIterator;
import uk.ac.diamond.scisoft.ncd.core.Invariant;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.InitializationException;

/**
 * Actor for calculating invariant value for input data
 * 
 * @author Irakli Sikharulidze
 */
public class NcdInvariantForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = 5836823100154893276L;

	public NcdInvariantForkJoinTransformer(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);

		dataName = "Invariant";
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		task = new InvariantTask(true, null);
	}

	@Override
	protected long[] getResultDataShape() {
		return Arrays.copyOf(frames, frames.length - dimension);
	}

	@Override
	protected int getResultDimension() {
		return 1;
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

				Invariant inv = new Invariant();

				int[] dataShape = Arrays.copyOf(inputData.getShape(), inputData.getRank() - dimension);
				AbstractDataset data = NcdDataUtils.flattenGridData(inputData, dimension);
				AbstractDataset errors = NcdDataUtils.flattenGridData((AbstractDataset) data.getErrorBuffer(),
						dimension);

				Object[] myobj = inv.process(data.getBuffer(), errors.getBuffer(), data.getShape());
				float[] mydata = (float[]) myobj[0];
				double[] myerrors = (double[]) myobj[1];

				AbstractDataset myres = new FloatDataset(mydata, dataShape);
				myres.setErrorBuffer(new DoubleDataset(myerrors, dataShape));

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