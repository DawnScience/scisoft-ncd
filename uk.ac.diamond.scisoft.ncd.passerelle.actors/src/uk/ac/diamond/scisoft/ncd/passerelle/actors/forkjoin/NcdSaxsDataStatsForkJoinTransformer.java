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
import java.util.List;
import java.util.concurrent.RecursiveAction;

import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;

import ptolemy.data.ObjectToken;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.data.stats.ClusterOutlierRemoval;
import uk.ac.diamond.scisoft.ncd.core.data.stats.FilterData;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStats;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStatsParameters;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsStatsData;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.actor.TerminationException;
import com.isencia.passerelle.core.ErrorCode;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;

/**
 * Actor for calculating processed SAXS data statistics
 * 
 * @author Irakli Sikharulidze
 */
public class NcdSaxsDataStatsForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static List<String> SAXS_STAT_TYPES;
	static {
		SaxsAnalysisStats[] saxsStatTypes = SaxsAnalysisStats.values();
		SAXS_STAT_TYPES = new ArrayList<String>(saxsStatTypes.length);
		for (SaxsAnalysisStats statType : saxsStatTypes) {
			SAXS_STAT_TYPES.add(statType.getName());
		}
	}
	
	public Parameter statTypeParam;
	private SaxsAnalysisStatsParameters statType;
	
	public NcdSaxsDataStatsForkJoinTransformer(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);

		statTypeParam = new Parameter(this, "statTypeParam");
	}

	@Override
	protected void doInitialize() throws InitializationException {
		
		super.doInitialize();

		try {
			Object token = statTypeParam.getToken();
			if (token instanceof ObjectToken) {
				Object obj = ((ObjectToken) token).getValue();
				if (obj instanceof SaxsAnalysisStatsParameters) {
					statType = (SaxsAnalysisStatsParameters) obj;
				}
			}
			
			task = new SaxsPlotTask();

		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}

	@Override
	protected void configureActorParameters() throws HDF5Exception {
		long inputDataSpaceID = H5.H5Dget_space(inputDataID);
		int rank = H5.H5Sget_simple_extent_ndims(inputDataSpaceID);
		frames = new long[rank];
		H5.H5Sget_simple_extent_dims(inputDataSpaceID, frames, null);
		NcdNexusUtils.closeH5id(inputDataSpaceID);
		
		hasErrors = false;
		if (inputErrorsID > 0) {
			try {
				final long type = H5.H5Iget_type(inputErrorsID);
				if (type != HDF5Constants.H5I_BADID) {
					hasErrors = true;
				}
			} catch (HDF5LibraryException e) {
				getLogger().info("Input dataset with error values wasn't found");
			}
		}
		long[] resultFrames = Arrays.copyOf(frames, frames.length);
		long type = HDF5Constants.H5T_NATIVE_INT;
		resultGroupID = inputGroupID;
		resultDataID = NcdNexusUtils.makedata(resultGroupID, "outliers", type, resultFrames, true, "counts");
		resultErrorsID = -1;
	}

	private class SaxsPlotTask extends RecursiveAction {

		@Override
		protected void compute() {

			SliceSettings currentSliceParams = new SliceSettings(frames, 0, (int) frames[0]);
			int[] startPos = Arrays.copyOf(new int[] {}, frames.length);
			currentSliceParams.setStart(startPos);

			try {
				DataSliceIdentifiers dataIDs = new DataSliceIdentifiers();
				dataIDs.setIDs(resultGroupID, resultDataID);
				dataIDs.setSlice(currentSliceParams);

				DataSliceIdentifiers tmp_ids = new DataSliceIdentifiers();
				tmp_ids.setIDs(inputGroupID, inputDataID);
				tmp_ids.setSlice(currentSliceParams);

				if (monitor.isCanceled()) {
					throw new OperationCanceledException(getName() + " stage has been cancelled.");
				}
				
				lock.lock();
				Dataset inputData = NcdNexusUtils.sliceInputData(currentSliceParams, tmp_ids);
				if (hasErrors) {
					DataSliceIdentifiers tmp_errors_ids = new DataSliceIdentifiers();
					tmp_errors_ids.setIDs(inputGroupID, inputErrorsID);
					tmp_errors_ids.setSlice(currentSliceParams);
					Dataset inputErrors = NcdNexusUtils.sliceInputData(currentSliceParams, tmp_errors_ids);
					inputData.setErrors(inputErrors);
				}
				lock.unlock();


				Dataset saxsStatsData = null;

				// We need this shape parameter to restore all relevant dimensions
				// in the integrated sector results dataset shape
				int[] dataShape = inputData.getShape();

				Dataset data = NcdDataUtils.flattenGridData(inputData, 1);
				
				SaxsAnalysisStats selectedSaxsStat = statType.getSelectionAlgorithm();
				SaxsStatsData statsData = selectedSaxsStat.getSaxsAnalysisStatsObject();
				if (statsData instanceof FilterData) {
					((FilterData)statsData).setConfigenceInterval(statType.getSaxsFilteringCI());
				}
				if (statsData instanceof ClusterOutlierRemoval) {
					((ClusterOutlierRemoval)statsData).setDbSCANClustererEpsilon(statType.getDbSCANClustererEpsilon());
					((ClusterOutlierRemoval)statsData).setDbSCANClustererMinPoints(statType.getDbSCANClustererMinPoints());
				}

				statsData.setReferenceData(data);
				Dataset mydata = statsData.getStatsData();
				
				saxsStatsData = DatasetUtils.cast(mydata, Dataset.INT);
				if (saxsStatsData != null) {
					saxsStatsData = saxsStatsData.reshape(dataShape);
				}
				if (saxsStatsData != null) {
					writeResults(dataIDs, saxsStatsData, dataShape);
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

	private void writeResults(DataSliceIdentifiers dataIDs, Dataset data, int[] dataShape)
			throws HDF5Exception {

		int resRank = dataShape.length;
		long[] resStart = Arrays.copyOf(dataIDs.start, resRank);
		long[] resCount = Arrays.copyOf(dataIDs.count, resRank);
		long[] resBlock = Arrays.copyOf(dataIDs.block, resRank);

		lock.lock();
		
		long filespaceID = H5.H5Dget_space(dataIDs.dataset_id);
		long typeID = H5.H5Dget_type(dataIDs.dataset_id);
		long memspaceID = H5.H5Screate_simple(resRank, resBlock, null);
		int selectID = H5.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, resStart, resBlock, resCount, resBlock);
		if (selectID < 0) {
			throw new HDF5Exception("Error allocating space for writing SAXS stats data");
		}
		int writeID = H5.H5Dwrite(dataIDs.dataset_id, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, data.getBuffer());
		if (writeID < 0) {
			throw new HDF5Exception("Error writing SAXS stats data");
		}
		H5.H5Sclose(filespaceID);
		H5.H5Sclose(memspaceID);
		H5.H5Tclose(typeID);
		
		lock.unlock();
	}
	
	@Override
	protected void writeAxisData() throws HDF5LibraryException, HDF5Exception {
	}
	
	@Override
	protected void writeNcdMetadata() throws HDF5LibraryException, HDF5Exception {
	}
	
	@Override
	protected void doWrapUp() throws TerminationException {
		try {
			NcdNexusUtils.closeH5id(resultDataID);
		} catch (HDF5LibraryException e) {
			getLogger().info("Error closing NeXus handle identifier", e);
		}
	}

}
