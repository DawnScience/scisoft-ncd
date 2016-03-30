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

import java.util.Arrays;
import java.util.concurrent.RecursiveAction;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.dawnsci.analysis.api.dataset.SliceND;
import org.eclipse.dawnsci.analysis.api.message.DataMessageComponent;
import org.eclipse.dawnsci.analysis.dataset.impl.AbstractDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.IndexIterator;
import org.eclipse.dawnsci.analysis.dataset.impl.SliceIterator;

import ptolemy.data.StringToken;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
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

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;

/**
 * Actor for averaging scattering data
 * 
 * @author Irakli Sikharulidze
 */
public class NcdAverageForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = -8213944227944915689L;

	public StringParameter gridAverageParam;

	private String gridAverage;
	private int[] averageIndices;
	private long[] framesAve;

	private int sliceDim, sliceSize;

	public Port selectionInput;
	private Dataset selectionData;

	public NcdAverageForkJoinTransformer(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);

		selectionInput = PortFactory.getInstance().createInputPort(this, "selectionInput", PortMode.PULL, DataMessageComponent.class);
		
		gridAverageParam = new StringParameter(this, "gridAverageParam");
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		try {
			
			gridAverage = ((StringToken) gridAverageParam.getToken()).stringValue();
			
			task = new AverageTask();

		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}

	@Override
	protected void readAdditionalPorts(ProcessRequest request) throws MessageException {
		ManagedMessage receivedMsg = request.getMessage(selectionInput);

		DataMessageComponent receivedObject = (DataMessageComponent) receivedMsg.getBodyContent();
		if (receivedObject.getList().keySet().contains("selection")) {
			Object obj = receivedObject.getList("selection");
			if (obj instanceof Dataset) {
				selectionData = (Dataset) obj;
				return;
			}
		}
		throw new MessageException(ErrorCode.ACTOR_EXECUTION_FATAL, "Invalid selection dataset", receivedMsg, null);
	}

	@Override
	protected void configureActorParameters() throws HDF5Exception {
		super.configureActorParameters();
		
		sliceDim = 0;
		sliceSize = (int) frames[0];
		int frameBatch = 1;

		// We will slice only 2D data. 1D data is loaded into memory completely
		if (averageIndices.length > 0 || dimension == 2) {
			// Find dimension that needs to be sliced
			int dimCounter = 1;
			for (int idx = (frames.length - 1 - dimension); idx >= 0; idx--) {
				if (ArrayUtils.contains(averageIndices, idx + 1)) {
					sliceDim = idx;
					sliceSize = (int) frames[idx];
					dimCounter *= frames[idx];
					if (dimCounter >= frameBatch) {
						sliceSize = (int) (frameBatch * frames[idx] / dimCounter);
						break;
					}
				}
			}
		}
		
		int[] gridShape = (int[]) ConvertUtils.convert(Arrays.copyOf(frames, frames.length - dimension), int[].class);
		if (!Arrays.equals(selectionData.getShape(), gridShape)) {
			selectionData = DatasetFactory.ones(gridShape, Dataset.INT);
		}
	}
	
	@Override
	protected long[] getResultDataShape() {
		// Calculate shape of the averaged dataset based on the dimensions selected for averaging
		averageIndices = new int[] {frames.length - dimension};
		if (gridAverage != null && !gridAverage.equals("")) {
			averageIndices = NcdDataUtils.createGridAxesList(gridAverage, frames.length - dimension + 1);
		}
		
		framesAve = Arrays.copyOf(frames, frames.length);
		for (int idx : averageIndices) {
			framesAve[idx - 1] = 1;
		}
		
		return framesAve;
	}
	
	private class AverageTask extends RecursiveAction {

		private static final long serialVersionUID = 4257320691174860254L;

		@Override
		protected void compute() {
			
			int[] framesAve_int = (int[]) ConvertUtils.convert(getResultDataShape(), int[].class);
			// Loop over dimensions that aren't averaged
			int[] iter_array = Arrays.copyOf(framesAve_int, framesAve_int.length);
			int[] step = Arrays.copyOf(framesAve_int, framesAve_int.length);
			Arrays.fill(step, 0, framesAve_int.length - dimension, 1);
			SliceND slice = new SliceND(iter_array, null, iter_array, step);
			IndexIterator iter = new SliceIterator(iter_array, AbstractDataset.calcSize(iter_array), slice);
			
			// This loop iterates over the output averaged dataset image by image
			while (iter.hasNext()) {
				
				int[] currentFrame = iter.getPos();
				int[] data_stop = Arrays.copyOf(currentFrame, currentFrame.length);
				long[] data_iter_array = Arrays.copyOf(frames, frames.length);
				Arrays.fill(data_iter_array, 0, frames.length - dimension, 1);
				for (int i = 0; i < currentFrame.length; i++) {
					if (i < currentFrame.length - dimension) {
						data_stop[i]++;
					} else {
						data_stop[i] = (int) frames[i];
					}
				}
				int[] data_start = Arrays.copyOf(currentFrame, currentFrame.length);
				int[] data_step = Arrays.copyOf(step, currentFrame.length);
				Arrays.fill(data_step, 0, currentFrame.length - dimension, 1);
				for (int idx : averageIndices) {
					int i = idx - 1;
					data_start[i] = 0;
					data_stop[i] = (int) frames[i];
					data_iter_array[i] = frames[i];
					if (i > sliceDim) {
						data_step[i] = (int) frames[i];
					} else {
						if (i == sliceDim) {
							data_step[i] = sliceSize;
						}
					}
				}

				slice = new SliceND(data_stop, data_start, data_stop, data_step);
				IndexIterator data_iter = new SliceIterator(data_stop, AbstractDataset.calcSize(data_stop), slice);
				
				int[] aveShape = Arrays.copyOfRange(framesAve_int, framesAve_int.length - dimension, framesAve_int.length);
				Dataset ave_frame = DatasetFactory.zeros(aveShape, Dataset.FLOAT32);
				Dataset ave_errors_frame = DatasetFactory.zeros(aveShape, Dataset.FLOAT64);
				try {
					DataSliceIdentifiers input_ids = new DataSliceIdentifiers();
					input_ids.setIDs(inputGroupID, inputDataID);
					DataSliceIdentifiers input_errors_ids = new DataSliceIdentifiers();
					input_errors_ids.setIDs(inputGroupID, inputErrorsID);
					
					// This loop iterates over chunks of data that need to be averaged for the current output image
					int totalFrames = 0;
			    	SliceSettings sliceSettings = new SliceSettings(data_iter_array, sliceDim, sliceSize);
					while (data_iter.hasNext()) {
						if (monitor.isCanceled()) {
							throw new OperationCanceledException(getName() + " stage has been cancelled.");
						}
						
						int[] tmpPos = data_iter.getPos();
						
						int[] gridPos = Arrays.copyOf(tmpPos, tmpPos.length - dimension);
						if (!selectionData.getBoolean(gridPos)) {
							continue;
						}
						sliceSettings.setStart(tmpPos);
						Dataset data_slice = NcdNexusUtils.sliceInputData(sliceSettings, input_ids);
						Dataset errors_slice;
						if (hasErrors) {
							errors_slice = NcdNexusUtils.sliceInputData(sliceSettings, input_errors_ids);
							errors_slice.ipower(2);
						} else {
							errors_slice = data_slice.clone();
						}
						int data_slice_rank = data_slice.getRank();
						
						int totalFramesBatch = 1;
						for (int idx = (data_slice_rank - dimension - 1); idx >= sliceDim; idx--) {
							if (ArrayUtils.contains(averageIndices, idx + 1)) {
								totalFramesBatch *= data_slice.getShape()[idx];
								data_slice = data_slice.sum(idx);
								errors_slice = errors_slice.sum(idx);
							}
						}
						totalFrames += totalFramesBatch;
						ave_frame = ave_frame.iadd(data_slice);
						ave_errors_frame = ave_errors_frame.iadd(errors_slice);
					}
					
					ave_frame =  ave_frame.idivide(totalFrames);
					ave_errors_frame =  ave_errors_frame.ipower(0.5).idivide(totalFrames);
					
					long filespace_id = H5.H5Dget_space(resultDataID);
					long type_id = H5.H5Dget_type(resultDataID);
					long[] ave_start = (long[]) ConvertUtils.convert(currentFrame, long[].class);
					long[] ave_step = (long[]) ConvertUtils.convert(step, long[].class);
					long[] ave_count_data = new long[frames.length];
					Arrays.fill(ave_count_data, 1);
					long memspace_id = H5.H5Screate_simple(ave_step.length, ave_step, null);
					
					H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, ave_start, ave_step, ave_count_data,
							ave_step);
					H5.H5Dwrite(resultDataID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT,
							ave_frame.getBuffer());
					
					H5.H5Sclose(filespace_id);
					H5.H5Sclose(memspace_id);
					H5.H5Tclose(type_id);
					
					filespace_id = H5.H5Dget_space(resultErrorsID);
					type_id = H5.H5Dget_type(resultErrorsID);
					memspace_id = H5.H5Screate_simple(ave_step.length, ave_step, null);
					
					H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, ave_start, ave_step, ave_count_data,
							ave_step);
					H5.H5Dwrite(resultErrorsID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT,
							ave_errors_frame.getBuffer());
					
					H5.H5Sclose(filespace_id);
					H5.H5Sclose(memspace_id);
					H5.H5Tclose(type_id);
				} catch (HDF5LibraryException e) {
					task.completeExceptionally(e);
				} catch (HDF5Exception e) {
					task.completeExceptionally(e);
				} catch (OperationCanceledException e) {
					task.completeExceptionally(e);
				}
			}
		}
	}

}
