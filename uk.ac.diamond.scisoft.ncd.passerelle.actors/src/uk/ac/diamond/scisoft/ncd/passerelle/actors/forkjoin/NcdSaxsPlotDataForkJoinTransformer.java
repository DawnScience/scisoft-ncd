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

import javax.measure.quantity.Dimensionless;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IndexIterator;
import org.eclipse.dawnsci.analysis.dataset.impl.PositionIterator;
import org.eclipse.swt.SWT;
import org.jscience.physics.amount.Amount;

import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.data.plots.GuinierPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.LogLogPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.PorodPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.SaxsPlotData;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdProcessingObject;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.actor.TerminationException;
import com.isencia.passerelle.actor.v5.ProcessResponse;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.core.Port;
import com.isencia.passerelle.core.PortFactory;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;
import com.isencia.passerelle.util.ptolemy.StringChoiceParameter;

/**
 * Actor for calculating standard SAXS plot data
 * 
 * @author Irakli Sikharulidze
 */
public class NcdSaxsPlotDataForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = -6724571888549033948L;

	private SaxsPlotData plotData;

	private static List<String> SAXS_PLOT_TYPES;
	static {
		SaxsAnalysisPlotType[] saxsPlotTypes = SaxsAnalysisPlotType.values();
		SAXS_PLOT_TYPES = new ArrayList<String>(saxsPlotTypes.length);
		for (SaxsAnalysisPlotType plotType : saxsPlotTypes) {
			SAXS_PLOT_TYPES.add(plotType.getName());
		}
	}
	
	public StringChoiceParameter plotTypeParam;

	private Dataset inputAxis;
	private String inputAxisUnit;
	private long[] axisShape;

	private long rgDataID = -1;
	private long rgErrorsID = -1;
	private long rgRangeDataID = -1;
	private long I0DataID = -1;
	private long I0ErrorsID = -1;

	private long guinierFitDataID = -1;
	private long loglogFitDataID = -1;
	
	private long q4AxisDataID = -1;
	private long q4AxisErrorsID = -1;

	public Port portRg, portRgRange, portI0;
	
	public NcdSaxsPlotDataForkJoinTransformer(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);

		plotTypeParam = new StringChoiceParameter(this, "plotTypeParam", SAXS_PLOT_TYPES, SWT.SINGLE);
		
		portRg = PortFactory.getInstance().createOutputPort(this, "Rg");
		portRgRange = PortFactory.getInstance().createOutputPort(this, "Rg_range");
		portI0 = PortFactory.getInstance().createOutputPort(this, "I0");
	}

	@Override
	protected void doInitialize() throws InitializationException {
		
		super.doInitialize();

		try {
			String[] selectedObj = plotTypeParam.getValue();
			if (selectedObj != null && selectedObj.length == 1) {
				SaxsAnalysisPlotType selectedSaxsPlot = SaxsAnalysisPlotType.forName(selectedObj[0]);
				plotData = selectedSaxsPlot.getSaxsPlotDataObject();
			}
			inputAxisUnit = "N/A";
			task = new SaxsPlotTask(true, null);

		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}

	@Override
	protected void configureActorParameters() throws HDF5Exception {
		super.configureActorParameters();
		
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
		
		long attrID = H5.H5Aopen(inputAxisDataID, "units", HDF5Constants.H5P_DEFAULT);
		long typeID = H5.H5Aget_type(attrID);
		int size = (int) H5.H5Tget_size(typeID);
		byte[] link = new byte[size];
		int readID = H5.H5Aread(attrID, typeID, link);
		if (readID > 0) {
			inputAxisUnit = new String(link);
		}
		H5.H5Tclose(typeID);
		H5.H5Aclose(attrID);
		
		if (plotData instanceof LogLogPlotData) {
			createLogLogFitPlotDataset();
		}
		if (plotData instanceof GuinierPlotData) {
			createGuinierPlotDataset();
		}
	}
	
	private void createGuinierPlotDataset() throws HDF5Exception {
		long[] resultFrames = Arrays.copyOf(frames, frames.length - dimension);
		long[] rgRangeFrames = Arrays.copyOf(frames, frames.length - dimension + 1);
		rgRangeFrames[rgRangeFrames.length - 1] = 2;
		long type = HDF5Constants.H5T_NATIVE_DOUBLE;
		rgDataID = NcdNexusUtils.makedata(resultGroupID, "Rg", type, resultFrames, false, inputAxisUnit);
		rgErrorsID = NcdNexusUtils.makedata(resultGroupID, "Rg_errors", type, resultFrames, false, inputAxisUnit);
		rgRangeDataID = NcdNexusUtils.makedata(resultGroupID, "Rg_range", type, rgRangeFrames, false, inputAxisUnit);
		I0DataID = NcdNexusUtils.makedata(resultGroupID, "I0", type, resultFrames, false, inputAxisUnit);
		I0ErrorsID = NcdNexusUtils.makedata(resultGroupID, "I0_errors", type, resultFrames, false, inputAxisUnit);
		
		long[] resultFitFrames = Arrays.copyOf(frames, frames.length);
		type = HDF5Constants.H5T_NATIVE_FLOAT;
		guinierFitDataID = NcdNexusUtils.makedata(resultGroupID, "fit", type, resultFitFrames, false, inputAxisUnit);
	}

	private void createLogLogFitPlotDataset() throws HDF5Exception {
		long[] resultFrames = Arrays.copyOf(frames, frames.length);
		long type = HDF5Constants.H5T_NATIVE_FLOAT;
		loglogFitDataID = NcdNexusUtils.makedata(resultGroupID, "fit", type, resultFrames, false, inputAxisUnit);
	}

	private class SaxsPlotTask extends RecursiveAction {

		private static final long serialVersionUID = 2182736294600190488L;
		
		private boolean forkTask;
		private int[] pos;

		public SaxsPlotTask(boolean forkTask, int[] pos) {
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
				List<SaxsPlotTask> taskArray = new ArrayList<SaxsPlotTask>();
				if (itr.hasNext()) {
					SaxsPlotTask firstTask = new SaxsPlotTask(false, itr.getPos());
					while (itr.hasNext()) {
						SaxsPlotTask task = new SaxsPlotTask(false, itr.getPos());
						task.fork();
						taskArray.add(task);
					}

					firstTask.compute();
					Iterator<SaxsPlotTask> taskItr = taskArray.iterator();
					while (taskItr.hasNext()) {
						taskItr.next().join();
					}
					// check for errors
					taskItr = taskArray.iterator();
					while (taskItr.hasNext()) {
						SaxsPlotTask tmpTask = taskItr.next(); 
						if (tmpTask.isCompletedAbnormally()) {
							task.completeExceptionally(tmpTask.getException());
						}
					}
				}
				return;
			}

			SliceSettings currentSliceParams = new SliceSettings(frames, frames.length - dimension - 1, 1);
			int[] startPos = Arrays.copyOf(pos, frames.length);
			currentSliceParams.setStart(startPos);

			try {
				if (monitor.isCanceled()) {
					throw new OperationCanceledException(getName() + " stage has been cancelled.");
				}
				DataSliceIdentifiers dataIDs = new DataSliceIdentifiers();
				dataIDs.setIDs(resultGroupID, resultDataID);
				dataIDs.setSlice(currentSliceParams);
				DataSliceIdentifiers errorIDs = new DataSliceIdentifiers();
				errorIDs.setIDs(resultGroupID, resultErrorsID);
				errorIDs.setSlice(currentSliceParams);

				DataSliceIdentifiers tmp_ids = new DataSliceIdentifiers();
				tmp_ids.setIDs(inputGroupID, inputDataID);
				tmp_ids.setSlice(currentSliceParams);

				lock.lock();
				Dataset inputData = NcdNexusUtils.sliceInputData(currentSliceParams, tmp_ids);
				if (hasErrors) {
					DataSliceIdentifiers tmp_errors_ids = new DataSliceIdentifiers();
					tmp_errors_ids.setIDs(inputGroupID, inputErrorsID);
					tmp_errors_ids.setSlice(currentSliceParams);
					Dataset inputErrors = NcdNexusUtils.sliceInputData(currentSliceParams, tmp_errors_ids);
					inputData.setError(inputErrors);
				} else {
					// Use counting statistics if no input error estimates are available 
					DoubleDataset inputErrorsBuffer = new DoubleDataset(inputData);
					inputData.setErrorBuffer(inputErrorsBuffer);
				}
				lock.unlock();


				Dataset saxsPlotData = null, saxsPlotErrors = null;

				// We need this shape parameter to restore all relevant dimensions
				// in the integrated sector results dataset shape
				int[] dataShape = inputData.getShape();

				Dataset data = NcdDataUtils.flattenGridData(inputData, dimension);
				
				Dataset axis = inputAxis.clone();
				Dataset mydata = plotData.getSaxsPlotDataset(data.squeeze(), axis.squeeze());
				int resLength = dataShape.length - dimension + 1;
				saxsPlotData = DatasetUtils.cast(mydata, Dataset.FLOAT32);
				if (saxsPlotData != null) {
					if (saxsPlotData.hasErrors()) {
						saxsPlotErrors = mydata.getErrorBuffer();
					}
					int[] resRadShape = Arrays.copyOf(dataShape, resLength);
					resRadShape[resLength - 1] = saxsPlotData.getShape()[saxsPlotData.getRank() - 1];
					saxsPlotData = saxsPlotData.reshape(resRadShape);
					if (saxsPlotErrors != null) {
						saxsPlotErrors = saxsPlotErrors.reshape(resRadShape);
						saxsPlotData.setErrorBuffer(saxsPlotErrors);
					}
				}
				if (saxsPlotData != null) {
					writeResults(dataIDs, saxsPlotData, dataShape, dimension);
					if (saxsPlotData.hasErrors()) {
						writeResults(errorIDs, saxsPlotData.getError(), dataShape, dimension);
					}

					if (plotData instanceof GuinierPlotData) {
						GuinierPlotData guinierPlotData = (GuinierPlotData) plotData;
						Object[] params = guinierPlotData.getGuinierPlotParameters(data.squeeze(), axis.squeeze());
						if (params != null) {
							Amount<Dimensionless> Rg = (Amount<Dimensionless>) params[1];
							int[] rgDataShape = Arrays.copyOf(dataShape, dataShape.length - dimension);

							DataSliceIdentifiers rgDataIDs = new DataSliceIdentifiers();
							rgDataIDs.setIDs(resultGroupID, rgDataID);
							rgDataIDs.setSlice(currentSliceParams);
							Dataset tmpDataset = new DoubleDataset(new double[] { Rg.getEstimatedValue() },
									new int[] { 1 });
							writeResults(rgDataIDs, tmpDataset, rgDataShape, 1);

							DataSliceIdentifiers rgErrorsIDs = new DataSliceIdentifiers();
							rgErrorsIDs.setIDs(resultGroupID, rgErrorsID);
							rgErrorsIDs.setSlice(currentSliceParams);
							tmpDataset = new DoubleDataset(new double[] { Rg.getAbsoluteError() }, new int[] { 1 });
							writeResults(rgErrorsIDs, tmpDataset, rgDataShape, 1);
							
							double[] rgRange = new double[] {(double) params[2], (double) params[3]};
							int[] rgRangeDataShape = Arrays.copyOf(dataShape, dataShape.length - dimension + 1);
							rgRangeDataShape[rgRangeDataShape.length - 1] = 2;
							DataSliceIdentifiers rgRangeDataIDs = new DataSliceIdentifiers();
							rgRangeDataIDs.setIDs(resultGroupID, rgRangeDataID);
							rgRangeDataIDs.setSlice(currentSliceParams);
							Dataset qRangeDataset = new DoubleDataset(rgRange, new int[] { 2 });
							writeResults(rgRangeDataIDs, qRangeDataset, rgRangeDataShape, 1);

							Amount<Dimensionless> I0 = (Amount<Dimensionless>) params[0];
							DataSliceIdentifiers I0DataIDs = new DataSliceIdentifiers();
							I0DataIDs.setIDs(resultGroupID, I0DataID);
							I0DataIDs.setSlice(currentSliceParams);
							tmpDataset = new DoubleDataset(new double[] { I0.getEstimatedValue() },
									new int[] { 1 });
							writeResults(I0DataIDs, tmpDataset, rgDataShape, 1);

							DataSliceIdentifiers I0ErrorsIDs = new DataSliceIdentifiers();
							I0ErrorsIDs.setIDs(resultGroupID, I0ErrorsID);
							I0ErrorsIDs.setSlice(currentSliceParams);
							tmpDataset = new DoubleDataset(new double[] { I0.getAbsoluteError() }, new int[] { 1 });
							writeResults(I0ErrorsIDs, tmpDataset, rgDataShape, 1);
							
							//rgDataIDs = new DataSliceIdentifiers();
							//rgDataIDs.setIDs(resultGroupID, guinierFitDataID);
							//rgDataIDs.setSlice(currentSliceParams);
							//tmpDataset = guinierPlotData.getFitData(regression, axis);
							//writeResults(rgDataIDs, tmpDataset, dataShape, 1);
						}
					}
					if (plotData instanceof PorodPlotData) {
						PorodPlotData porodPlotData = (PorodPlotData) plotData;
						porodPlotData.getPorodPlotParameters(data.squeeze(), axis.squeeze());
					}
					if (plotData instanceof LogLogPlotData) {
						LogLogPlotData loglogPlotData = (LogLogPlotData) plotData;
						double[] params = loglogPlotData.getPorodPlotParameters(data.squeeze(), axis.squeeze());

						DataSliceIdentifiers rgDataIDs = new DataSliceIdentifiers();
						rgDataIDs.setIDs(resultGroupID, loglogFitDataID);
						rgDataIDs.setSlice(currentSliceParams);
						Dataset tmpDataset = loglogPlotData.getFitData(params, axis);
						writeResults(rgDataIDs, tmpDataset, dataShape, 1);
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

		lock.lock();
		
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
		
		lock.unlock();
	}
	
	@Override
	protected void writeAdditionalPorts(ManagedMessage receivedMsg, ProcessResponse response) throws MessageException {
		if (plotData instanceof GuinierPlotData) {
			ManagedMessage outputMsg = createMessageFromCauses(receivedMsg);
			NcdProcessingObject obj = new NcdProcessingObject(
					1,
					entryGroupID,
					processingGroupID,
					resultGroupID,
					rgDataID,
					rgErrorsID,
					-1,
					-1,
					lock,
					monitor);
			outputMsg.setBodyContent(obj, "application/octet-stream");
			response.addOutputMessage(portRg, outputMsg);
			
			ManagedMessage rgRangeOutputMsg = createMessageFromCauses(receivedMsg);
			NcdProcessingObject rgRangeObj = new NcdProcessingObject(
					1,
					entryGroupID,
					processingGroupID,
					resultGroupID,
					rgRangeDataID,
					-1,
					-1,
					-1,
					lock,
					monitor);
			rgRangeOutputMsg.setBodyContent(rgRangeObj, "application/octet-stream");
			response.addOutputMessage(portRgRange, rgRangeOutputMsg);
			
			ManagedMessage I0OutputMsg = createMessageFromCauses(receivedMsg);
			NcdProcessingObject I0Obj = new NcdProcessingObject(
					1,
					entryGroupID,
					processingGroupID,
					resultGroupID,
					I0DataID,
					I0ErrorsID,
					-1,
					-1,
					lock,
					monitor);
			I0OutputMsg.setBodyContent(I0Obj, "application/octet-stream");
			response.addOutputMessage(portI0, I0OutputMsg);
		}
	}

	@Override
	protected void writeAxisData() throws HDF5LibraryException, HDF5Exception {
		
		Dataset axisNew = plotData.getSaxsPlotAxis(inputAxis);
		
		resultAxisDataID = NcdNexusUtils.makeaxis(resultGroupID, "variable", HDF5Constants.H5T_NATIVE_FLOAT, axisShape,
				new int[] { axisNew.getRank() }, 1, inputAxisUnit);

		long filespace_id = H5.H5Dget_space(resultAxisDataID);
		long type_id = H5.H5Dget_type(resultAxisDataID);
		long memspace_id = H5.H5Screate_simple(axisNew.getRank(), axisShape, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(resultAxisDataID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, axisNew.getBuffer());
		
		// add long_name attribute
		{
			String variableName = plotData.getVariableName();
			long attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			long attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attrtype_id, variableName.getBytes().length);
			
			long attr_id = H5.H5Acreate(resultAxisDataID, "long_name", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			if (attr_id < 0) {
				throw new HDF5Exception("H5 putattr write error: can't create attribute");
			}
			int write_id = H5.H5Awrite(attr_id, attrtype_id, variableName.getBytes());
			if (write_id < 0) {
				throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
			}
			H5.H5Aclose(attr_id);
			H5.H5Sclose(attrspace_id);
			H5.H5Tclose(attrtype_id);
		}
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type_id);
		
		if (axisNew.hasErrors()) {
			resultAxisErrorsID = NcdNexusUtils.makedata(resultGroupID, "variable_errors", HDF5Constants.H5T_NATIVE_DOUBLE, axisShape, false, inputAxisUnit);
		
			filespace_id = H5.H5Dget_space(resultAxisErrorsID);
			type_id = H5.H5Dget_type(resultAxisErrorsID);
			memspace_id = H5.H5Screate_simple(axisNew.getRank(), axisShape, null);
			H5.H5Sselect_all(filespace_id);
			H5.H5Dwrite(resultAxisErrorsID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, axisNew.getError().getBuffer());
		
			H5.H5Sclose(filespace_id);
			H5.H5Sclose(memspace_id);
			H5.H5Tclose(type_id);
		}
		
		// TODO: need to redesign SAXS plot interface to support multiple axis
		if (plotData instanceof PorodPlotData) {
			Dataset q4Axis = axisNew.clone().ipower(4);
			setq4AxisErrors(axisNew, q4Axis);
			
			String variableName = plotData.getVariableName() + "^4";
			q4AxisDataID = NcdNexusUtils.makeaxis(resultGroupID, variableName, HDF5Constants.H5T_NATIVE_FLOAT, axisShape,
					new int[] { axisNew.getRank() }, 0, inputAxisUnit);

			filespace_id = H5.H5Dget_space(q4AxisDataID);
			type_id = H5.H5Dget_type(q4AxisDataID);
			memspace_id = H5.H5Screate_simple(axisNew.getRank(), axisShape, null);
			H5.H5Sselect_all(filespace_id);
			H5.H5Dwrite(q4AxisDataID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, q4Axis.getBuffer());
			
			// add long_name attribute
			{
				long attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
				long attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
				H5.H5Tset_size(attrtype_id, variableName.getBytes().length);
				
				long attr_id = H5.H5Acreate(q4AxisDataID, "long_name", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
						HDF5Constants.H5P_DEFAULT);
				if (attr_id < 0) {
					throw new HDF5Exception("H5 putattr write error: can't create attribute");
				}
				int write_id = H5.H5Awrite(attr_id, attrtype_id, variableName.getBytes());
				if (write_id < 0) {
					throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
				}
				H5.H5Aclose(attr_id);
				H5.H5Sclose(attrspace_id);
				H5.H5Tclose(attrtype_id);
			}
			
			H5.H5Sclose(filespace_id);
			H5.H5Sclose(memspace_id);
			H5.H5Tclose(type_id);
			
			if (axisNew.hasErrors()) {
				q4AxisErrorsID = NcdNexusUtils.makedata(resultGroupID, variableName + "_errors", HDF5Constants.H5T_NATIVE_DOUBLE, axisShape, false, inputAxisUnit);
			
				filespace_id = H5.H5Dget_space(q4AxisErrorsID);
				type_id = H5.H5Dget_type(q4AxisErrorsID);
				memspace_id = H5.H5Screate_simple(axisNew.getRank(), axisShape, null);
				H5.H5Sselect_all(filespace_id);
				H5.H5Dwrite(q4AxisErrorsID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, q4Axis.getError().getBuffer());
			
				H5.H5Sclose(filespace_id);
				H5.H5Sclose(memspace_id);
				H5.H5Tclose(type_id);
			}
		}
		
	}

	private void setq4AxisErrors(Dataset axisNew, Dataset q4Axis) {
		
		Dataset errors = DatasetFactory.zeros(q4Axis, Dataset.FLOAT64);
		
		// Calculate std. deviation for q^4 values
		IndexIterator itr = q4Axis.getIterator(true);
		while (itr.hasNext()) {
			int[] idx = itr.getPos();
			double mu = axisNew.getDouble(idx);
			double s = axisNew.getError(idx);
			
			double s2 = s * s;
			double s4 = s2 * s2;
			double s6 = s4 * s2;
			double s8 = s4 * s4;
			
			double mu2 = mu * mu;
			double mu4 = mu2 * mu2;
			double mu6 = mu4 * mu2;
			
			double val = 14*mu6*s2 + 168*mu4*s4 + 372*mu2*s6 + 96*s8;
			errors.set(val, idx);
		}
		q4Axis.setErrorBuffer(errors);
	}
	
	@Override
	protected void doWrapUp() throws TerminationException {
		if (plotData instanceof GuinierPlotData) {
			try {
				List<Long> identifiers = new ArrayList<Long>(Arrays.asList(
						rgDataID,
						rgErrorsID,
						rgRangeDataID,
						I0DataID,
						I0ErrorsID,
						guinierFitDataID,
						loglogFitDataID));

				NcdNexusUtils.closeH5idList(identifiers);
			} catch (HDF5LibraryException e) {
				getLogger().info("Error closing NeXus handle identifier", e);
			}
		}
		super.doWrapUp();
	}
	
}
