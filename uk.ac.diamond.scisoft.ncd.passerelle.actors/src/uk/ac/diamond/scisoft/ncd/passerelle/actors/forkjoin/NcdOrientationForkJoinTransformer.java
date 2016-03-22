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
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.PositionIterator;
import org.eclipse.dawnsci.hdf.object.Nexus;

import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.DegreeOfOrientation;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.actor.TerminationException;

/**
 * Actor for calculating degree of orientation from azimuthal profile data
 * 
 * @author Irakli Sikharulidze
 */
public class NcdOrientationForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = 2754886305339583550L;
	
	private Dataset inputAxis;
	private long[] axisShape;
	
	private long angleDataID;
	private long mapDataID;
	
	public NcdOrientationForkJoinTransformer(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);

	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		task = new OrientationTask(true, null);
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
		long inputDataSpaceID = H5.H5Dget_space(inputDataID);
		int rank = H5.H5Sget_simple_extent_ndims(inputDataSpaceID);
		frames = new long[rank];
		H5.H5Sget_simple_extent_dims(inputDataSpaceID, frames, null);
		NcdNexusUtils.closeH5id(inputDataSpaceID);
		
		hasErrors = false;
		long[] resultFrames = getResultDataShape();
		resultGroupID = NcdNexusUtils.makegroup(processingGroupID, getName(), Nexus.DETECT);
		long type = getResultDataType();
		resultDataID = NcdNexusUtils.makedata(resultGroupID, "data", type, resultFrames, true, "dimensionless");
		resultErrorsID = -1;
		
		angleDataID = NcdNexusUtils.makedata(resultGroupID, "orientation", type, resultFrames, true, "degree");
		long[] mapFrames = ArrayUtils.addAll(resultFrames, new long[] {2});
		mapDataID = NcdNexusUtils.makedata(resultGroupID, "vector_map", type, mapFrames, true, "dimensionless");
		
		if (inputAxisDataID != -1) {
			long spaceID = H5.H5Dget_space(inputAxisDataID);
			int axisRank = H5.H5Sget_simple_extent_ndims(spaceID);
			axisShape = new long[axisRank];
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
		}
	}
	
	private class OrientationTask extends RecursiveAction {

		private static final long serialVersionUID = -6513288951179902341L;
		
		private boolean forkTask;
		private int[] pos;

		public OrientationTask(boolean forkTask, int[] pos) {
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
				List<OrientationTask> taskArray = new ArrayList<OrientationTask>();
				if (itr.hasNext()) {
					OrientationTask firstTask = new OrientationTask(false, itr.getPos());
					while (itr.hasNext()) {
						OrientationTask task = new OrientationTask(false, itr.getPos());
						task.fork();
						taskArray.add(task);
					}

					firstTask.compute();
					Iterator<OrientationTask> taskItr = taskArray.iterator();
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
				lock.unlock();

				Dataset data = NcdDataUtils.flattenGridData(inputData, dimension);
				
				Object[] myobj;
				Dataset axis = null;
				
				axis = inputAxis.clone().squeeze();
				DegreeOfOrientation degree = new DegreeOfOrientation();
				myobj = degree.process(data.getBuffer(), axis.getBuffer(), data.getShape());
				
				float[] mydata = (float[]) myobj[0];
				float[] myangle = (float[]) myobj[1];
				float[] myvector = (float[]) myobj[2];

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
				
				{
					filespaceID = H5.H5Dget_space(resultDataID);
					typeID = H5.H5Dget_type(resultDataID);
					memspaceID = H5.H5Screate_simple(block.length, block, null);
					selectID = H5
							.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
					if (selectID < 0) {
						throw new HDF5Exception("Failed to allocate space fro writing Degree of Orientation data");
					}
					writeID = H5.H5Dwrite(resultDataID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, mydata);
					if (writeID < 0) {
						throw new HDF5Exception("Failed to write Degree of Orientation data into the results file");
					}
					H5.H5Sclose(filespaceID);
					H5.H5Sclose(memspaceID);
					H5.H5Tclose(typeID);
				}
				
				{
					filespaceID = H5.H5Dget_space(angleDataID);
					typeID = H5.H5Dget_type(angleDataID);
					memspaceID = H5.H5Screate_simple(block.length, block, null);
					selectID = H5
							.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
					if (selectID < 0) {
						throw new HDF5Exception("Failed to allocate space fro writing Degree of Orientation angle data");
					}
					writeID = H5.H5Dwrite(angleDataID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, myangle);
					if (writeID < 0) {
						throw new HDF5Exception("Failed to write Degree of Orientation angle data into the results file");
					}
					H5.H5Sclose(filespaceID);
					H5.H5Sclose(memspaceID);
					H5.H5Tclose(typeID);
				}
				
				{
					start = ArrayUtils.addAll(start, new long[] {0});
					block = ArrayUtils.addAll(block, new long[] {2});
					count = ArrayUtils.addAll(count, new long[] {1});
					filespaceID = H5.H5Dget_space(mapDataID);
					typeID = H5.H5Dget_type(mapDataID);
					memspaceID = H5.H5Screate_simple(block.length, block, null);
					selectID = H5
							.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, start, block, count, block);
					if (selectID < 0) {
						throw new HDF5Exception("Failed to allocate space for writing Degree of Orientation vector data");
					}
					writeID = H5.H5Dwrite(mapDataID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, myvector);
					if (writeID < 0) {
						throw new HDF5Exception("Failed to write Degree of Orientation vector data into the results file");
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
					NcdNexusUtils.closeH5idList(
							new ArrayList<Long>(Arrays.asList(
									memspaceID,
									typeID,
									filespaceID)));
				} catch (HDF5LibraryException e) {
					task.completeExceptionally(e);
				}
			}
		}
	}
	
	@Override
	protected void writeAxisData() throws HDF5Exception {
		if (inputAxis == null) {
			resultAxisDataID = -1;
			resultAxisErrorsID = -1;
		}
	}
	
	@Override
	protected void doWrapUp() throws TerminationException {
		try {
			NcdNexusUtils.closeH5idList(
					new ArrayList<Long>(Arrays.asList(
							angleDataID,
							mapDataID)));
		} catch (HDF5LibraryException e) {
			getLogger().info("Error closing NeXus handle identifier", e);
		}
		super.doWrapUp();
	}
}
