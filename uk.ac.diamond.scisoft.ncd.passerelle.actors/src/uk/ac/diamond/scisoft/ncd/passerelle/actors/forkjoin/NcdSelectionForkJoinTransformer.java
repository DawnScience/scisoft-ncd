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
import java.util.concurrent.RecursiveAction;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.util.MultidimensionalCounter;
import org.apache.commons.math3.util.MultidimensionalCounter.Iterator;

import ptolemy.data.StringToken;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.core.ErrorCode;

/**
 * Actor for selecting subset of input data
 * 
 * @author Irakli Sikharulidze
 */
public class NcdSelectionForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = -5655747807538747688L;

	private String format;

	public StringParameter formatParam;

	private long[] selectedShape;
	private ArrayList<int[]> indexList;

	public NcdSelectionForkJoinTransformer(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);

		dataName = "Selection";

		formatParam = new StringParameter(this, "formatParam");
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		try {

			format = ((StringToken) formatParam.getToken()).stringValue();

			task = new SelectionTask();
			
		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}

	@Override
	protected int getResultDataType() throws HDF5LibraryException {
		if (inputDataID > 0) {
			try {
				int typeID = H5.H5Dget_type(inputDataID);
				return H5.H5Tcopy(typeID);
			} catch (HDF5LibraryException e) {
				getLogger().info("Setting default results dataset type", e);
			}
		}
		return HDF5Constants.H5T_NATIVE_FLOAT;
	}
	
	@Override
	protected int getResultErrorsType() throws HDF5LibraryException {
		if (inputErrorsID > 0) {
			try {
				int typeID = H5.H5Dget_type(inputErrorsID);
				return H5.H5Tcopy(typeID);
			} catch (HDF5LibraryException e) {
				getLogger().info("Setting default result errors dataset type", e);
			}
		}
		return HDF5Constants.H5T_NATIVE_DOUBLE;
	}
	
	@Override
	protected long[] getResultDataShape() {
		selectedShape = Arrays.copyOf(frames, frames.length - dimension);
		indexList = NcdDataUtils.createSliceList(format, (int[]) ConvertUtils.convert(selectedShape, int[].class));
		for (int i = 0; i < selectedShape.length; i++) {
			selectedShape[i] = indexList.get(i).length;
		}
		long[] imageSize = Arrays.copyOfRange(frames, frames.length - dimension, frames.length);
		long[] resultDataShape = ArrayUtils.addAll(selectedShape, imageSize);
		return resultDataShape;
	}

	private class SelectionTask extends RecursiveAction {

		private static final long serialVersionUID = 2174001369762740761L;

		@Override
		protected void compute() {

			long[] block = new long[frames.length];
			block = Arrays.copyOf((long[]) ConvertUtils.convert(frames, long[].class), block.length);
			Arrays.fill(block, 0, block.length - dimension, 1);

			long[] count = new long[frames.length];
			Arrays.fill(count, 1);

			MultidimensionalCounter frameCounter = new MultidimensionalCounter((int[]) ConvertUtils.convert(
					selectedShape, int[].class));
			Iterator iter = frameCounter.iterator();
			int filespaceID = -1;
			int typeID = -1;
			int memspaceID = -1;
			int selectID = -1;
			int writeID = -1;

			while (iter.hasNext()) {
				iter.next();
				long[] frame = (long[]) ConvertUtils.convert(iter.getCounts(), long[].class);
				long[] gridFrame = new long[selectedShape.length];
				for (int i = 0; i < gridFrame.length; i++) {
					gridFrame[i] = indexList.get(i)[(int) frame[i]];
				}

				long[] start = new long[frames.length];
				start = Arrays.copyOf(gridFrame, frames.length);
				long[] writePosition = new long[frames.length];
				writePosition = Arrays.copyOf(frame, frames.length);

				SliceSettings sliceData = new SliceSettings(frames, frames.length - dimension - 1, 1);
				int[] startPos = (int[]) ConvertUtils.convert(Arrays.copyOf(start, frames.length), int[].class);
				sliceData.setStart(startPos);
				try {
					lock.lock();
					DataSliceIdentifiers ids = new DataSliceIdentifiers();
					ids.setIDs(inputGroupID, inputDataID);
					DataSliceIdentifiers error_ids = new DataSliceIdentifiers();
					error_ids.setIDs(inputGroupID, inputErrorsID);

					AbstractDataset data = NcdNexusUtils.sliceInputData(sliceData, ids);
					if (hasErrors) {
						DataSliceIdentifiers tmp_errors_ids = new DataSliceIdentifiers();
						tmp_errors_ids.setIDs(inputGroupID, inputErrorsID);
						AbstractDataset inputErrors = NcdNexusUtils.sliceInputData(sliceData, tmp_errors_ids);
						data.setError(inputErrors);
					} else {
						// Use counting statistics if no input error estimates are available
						DoubleDataset inputErrorsBuffer = new DoubleDataset(data);
						data.setErrorBuffer(inputErrorsBuffer);
					}

					filespaceID = H5.H5Dget_space(resultDataID);
					typeID = H5.H5Dget_type(resultDataID);
					memspaceID = H5.H5Screate_simple(block.length, block, null);

					selectID = H5.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, writePosition, block,
							count, block);
					if (selectID < 0) {
						throw new HDF5Exception("Failed to allocate space fro reading selected data");
					}
					writeID = H5.H5Dwrite(resultDataID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT,
							data.getBuffer());
					if (writeID < 0) {
						throw new HDF5Exception("Failed to write selected data into the results file");
					}

					NcdNexusUtils.closeH5idList(new ArrayList<Integer>(Arrays.asList(memspaceID, typeID, filespaceID)));

					filespaceID = H5.H5Dget_space(resultErrorsID);
					typeID = H5.H5Dget_type(resultErrorsID);
					memspaceID = H5.H5Screate_simple(block.length, block, null);
					selectID = H5.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, writePosition, block,
							count, block);
					if (selectID < 0) {
						throw new HDF5Exception("Failed to allocate space fro reading selected error data");
					}
					writeID = H5.H5Dwrite(resultErrorsID, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT,
							data.getError().getBuffer());
					if (writeID < 0) {
						throw new HDF5Exception("Failed to write selected error data into the results file");
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
						NcdNexusUtils.closeH5idList(new ArrayList<Integer>(Arrays.asList(memspaceID, typeID,
								filespaceID)));
					} catch (HDF5LibraryException e) {
						throw new RuntimeException(e);
					}
				}
			}
		}
	}

}
