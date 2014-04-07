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
import org.eclipse.swt.SWT;

import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.PositionIterator;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.data.plots.PorodPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.SaxsPlotData;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.core.ErrorCode;
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

	private AbstractDataset inputAxis;
	private String inputAxisUnit;
	private long[] axisShape;
	
	private int q4AxisDataID = -1;
	private int q4AxisErrorsID = -1;
	
	public NcdSaxsPlotDataForkJoinTransformer(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);

		dataName = "SaxsPlot";

		plotTypeParam = new StringChoiceParameter(this, "plotTypeParam", SAXS_PLOT_TYPES, SWT.SINGLE);
	}

	@Override
	protected void doInitialize() throws InitializationException {
		
		super.doInitialize();

		try {
			String[] selectedObj = plotTypeParam.getValue();
			if (selectedObj != null && selectedObj.length == 1) {
				SaxsAnalysisPlotType selectedSaxsPlot = SaxsAnalysisPlotType.forName(selectedObj[0]);
				plotData = selectedSaxsPlot.getSaxsPlotDataObject();
				dataName = plotData.getGroupName();
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
		
		int spaceID = H5.H5Dget_space(inputAxisDataID);
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
			AbstractDataset inputAxisErrors = NcdNexusUtils.sliceInputData(axisSliceParams, axisErrorsIDs);
			inputAxis.setError(inputAxisErrors);
		}
		
		int attrID = H5.H5Aopen(inputAxisDataID, "units", HDF5Constants.H5P_DEFAULT);
		int typeID = H5.H5Aget_type(attrID);
		int size = H5.H5Tget_size(typeID);
		byte[] link = new byte[size];
		int readID = H5.H5Aread(attrID, typeID, link);
		if (readID > 0) {
			inputAxisUnit = new String(link);
		}
		H5.H5Tclose(typeID);
		H5.H5Aclose(attrID);
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
				}
				return;
			}

			SliceSettings currentSliceParams = new SliceSettings(frames, frames.length - dimension - 1, 1);
			int[] startPos = Arrays.copyOf(pos, frames.length);
			currentSliceParams.setStart(startPos);

			try {
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
				AbstractDataset inputData = NcdNexusUtils.sliceInputData(currentSliceParams, tmp_ids);
				if (hasErrors) {
					DataSliceIdentifiers tmp_errors_ids = new DataSliceIdentifiers();
					tmp_errors_ids.setIDs(inputGroupID, inputErrorsID);
					tmp_errors_ids.setSlice(currentSliceParams);
					AbstractDataset inputErrors = NcdNexusUtils.sliceInputData(currentSliceParams, tmp_errors_ids);
					inputData.setError(inputErrors);
				} else {
					// Use counting statistics if no input error estimates are available 
					DoubleDataset inputErrorsBuffer = new DoubleDataset(inputData);
					inputData.setErrorBuffer(inputErrorsBuffer);
				}
				lock.unlock();


				AbstractDataset saxsPlotData = null, saxsPlotErrors = null;

				// We need this shape parameter to restore all relevant dimensions
				// in the integrated sector results dataset shape
				int[] dataShape = inputData.getShape();

				AbstractDataset data = NcdDataUtils.flattenGridData(inputData, dimension);
				if (inputData.hasErrors()) {
					AbstractDataset errors = NcdDataUtils.flattenGridData((AbstractDataset) inputData.getErrorBuffer(),
							dimension);
					data.setErrorBuffer(errors);
				}
				
				AbstractDataset axis = inputAxis.clone();
				AbstractDataset mydata = plotData.getSaxsPlotDataset(data.squeeze(), axis.squeeze());
				int resLength = dataShape.length - dimension + 1;
					saxsPlotData = DatasetUtils.cast(mydata, AbstractDataset.FLOAT32);
					if (saxsPlotData != null) {
						if (saxsPlotData.hasErrors()) {
							saxsPlotErrors = DatasetUtils.cast((AbstractDataset) mydata.getErrorBuffer(),
									AbstractDataset.FLOAT64);
						}
						int[] resRadShape = Arrays.copyOf(dataShape, resLength);
						resRadShape[resLength - 1] = saxsPlotData.getShape()[saxsPlotData.getRank() - 1];
						saxsPlotData = saxsPlotData.reshape(resRadShape);
						if (saxsPlotErrors != null) {
							saxsPlotErrors = saxsPlotErrors.reshape(resRadShape);
							saxsPlotData.setErrorBuffer(saxsPlotErrors);
						}
					}
				lock.lock();
				if (saxsPlotData != null) {
					writeResults(dataIDs, saxsPlotData, dataShape, dimension);
					if (saxsPlotData.hasErrors()) {
						writeResults(errorIDs, saxsPlotData.getError(), dataShape, dimension);
					}
				}
			} catch (HDF5LibraryException e) {
				throw new RuntimeException(e);
			} catch (HDF5Exception e) {
				throw new RuntimeException(e);
			} finally {
				if (lock != null && lock.isHeldByCurrentThread()) {
					lock.unlock();
				}
			}
		}
	}

	private void writeResults(DataSliceIdentifiers dataIDs, AbstractDataset data, int[] dataShape, int dim)
			throws HDF5Exception {

		int resRank = dataShape.length - dim + 1;
		int integralLength = data.getShape()[data.getRank() - 1];

		long[] resStart = Arrays.copyOf(dataIDs.start, resRank);
		long[] resCount = Arrays.copyOf(dataIDs.count, resRank);
		long[] resBlock = Arrays.copyOf(dataIDs.block, resRank);
		resBlock[resRank - 1] = integralLength;

		int filespaceID = H5.H5Dget_space(dataIDs.dataset_id);
		int typeID = H5.H5Dget_type(dataIDs.dataset_id);
		int memspaceID = H5.H5Screate_simple(resRank, resBlock, null);
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
	protected void writeAxisData() throws HDF5LibraryException, HDF5Exception {
		
		AbstractDataset axisNew = plotData.getSaxsPlotAxis(inputAxis);
		
		resultAxisDataID = NcdNexusUtils.makeaxis(resultGroupID, "variable", HDF5Constants.H5T_NATIVE_FLOAT, axisShape,
				new int[] { axisNew.getRank() }, 1, inputAxisUnit);

		int filespace_id = H5.H5Dget_space(resultAxisDataID);
		int type_id = H5.H5Dget_type(resultAxisDataID);
		int memspace_id = H5.H5Screate_simple(axisNew.getRank(), axisShape, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(resultAxisDataID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, axisNew.getBuffer());
		
		// add long_name attribute
		{
			String variableName = plotData.getVariableName();
			int attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attrtype_id, variableName.getBytes().length);
			
			int attr_id = H5.H5Acreate(resultAxisDataID, "long_name", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
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
			AbstractDataset q4Axis = axisNew.clone().ipower(4);
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
				int attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
				int attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
				H5.H5Tset_size(attrtype_id, variableName.getBytes().length);
				
				int attr_id = H5.H5Acreate(q4AxisDataID, "long_name", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
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

	private void setq4AxisErrors(AbstractDataset axisNew, AbstractDataset q4Axis) {
		
		AbstractDataset errors = AbstractDataset.zeros(q4Axis, AbstractDataset.FLOAT64);
		
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

}
