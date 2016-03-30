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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.hdf5.HDF5Utils;

import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.core.ErrorCode;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;

/**
 * Actor for standardising input dataset
 * 
 * @author Irakli Sikharulidze
 */
public class NcdStandardiseForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	public Parameter detectorResponseParam;

	public NcdStandardiseForkJoinTransformer(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);

	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		try {
			task = new StandardiseTask();
		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}

	private class StandardiseTask extends RecursiveAction {

		@Override
		protected void compute() {

			int[] grid = (int[]) ConvertUtils
					.convert(Arrays.copyOf(frames, frames.length - dimension), int[].class);
			
			long dataspace_id = -1;
			long datatype_id = -1;
			int dataclass = -1;
			int datasize = -1;
			long memspace_id = -1;
			
			long errorspace_id = -1;
			long errortype_id = -1;
			int errorclass = -1;
			int errorsize = -1;
			long errormemspace_id = -1;
			
			for (int idx = 0; idx < frames[frames.length - 1]; idx++) {
				
				Dataset data, errors;
				try {
					if (monitor.isCanceled()) {
						throw new OperationCanceledException(getName() + " stage has been cancelled.");
					}
					
					long[] start = new long[frames.length];
					long[] count = new long[frames.length];
					long[] block = new long[frames.length];
					
					start = (long[]) ConvertUtils.convert(
							ArrayUtils.addAll(Arrays.copyOf(new int[] {}, grid.length),
								new int[] {idx}), 
							long[].class);
					block = Arrays.copyOf(frames, frames.length);
					Arrays.fill(block, frames.length-dimension, frames.length, 1L);
					Arrays.fill(count, 1L);
					
					dataspace_id = H5.H5Dget_space(inputDataID);
					datatype_id = H5.H5Dget_type(inputDataID);
					dataclass = H5.H5Tget_class(datatype_id);
					datasize = (int) H5.H5Tget_size(datatype_id);
					memspace_id = H5.H5Screate_simple(block.length, block, null);
					
					lock.lock();
					int select_id = H5.H5Sselect_hyperslab(
							dataspace_id,
							HDF5Constants.H5S_SELECT_SET,
							start, block, count, block);
					if (select_id < 0) {
						throw new HDF5Exception("H5 select hyperslab error: can't allocate memory to read data");
					}
					
					int dtype = HDF5Utils.getDtype(dataclass, datasize);
					data = DatasetFactory.zeros(grid, dtype);
					if ((dataspace_id > 0) && (memspace_id > 0)) {
						int read_id = H5.H5Dread(
								inputDataID,
								datatype_id,
								memspace_id,
								dataspace_id,
								HDF5Constants.H5P_DEFAULT,
								data.getBuffer());
						if (read_id < 0) {
							throw new HDF5Exception("H5 data read error: can't read input dataset");
						}
					}
					
					errorspace_id = H5.H5Dget_space(inputErrorsID);
					errortype_id = H5.H5Dget_type(inputErrorsID);
					errorclass = H5.H5Tget_class(errortype_id);
					errorsize = (int) H5.H5Tget_size(errortype_id);
					errormemspace_id = H5.H5Screate_simple(block.length, block, null);
					
					select_id = H5.H5Sselect_hyperslab(
							errorspace_id,
							HDF5Constants.H5S_SELECT_SET,
							start, block, count, block);
					if (select_id < 0) {
						throw new HDF5Exception("H5 select hyperslab error: can't allocate memory to read data");
					}
					
					int errdtype = HDF5Utils.getDtype(errorclass, errorsize);
					errors = DatasetFactory.zeros(grid, errdtype);
					if ((errorspace_id > 0) && (errormemspace_id > 0)) {
						int read_id = H5.H5Dread(
								inputErrorsID,
								errortype_id,
								errormemspace_id,
								errorspace_id,
								HDF5Constants.H5P_DEFAULT,
								errors.getBuffer());
						if (read_id < 0) {
							throw new HDF5Exception("H5 data read error: can't read input dataset");
						}
					}
				} catch (HDF5LibraryException e) {
					task.completeExceptionally(e);
					return;
				} catch (HDF5Exception e) {
					task.completeExceptionally(e);
					return;
				} catch (OperationCanceledException e) {
					task.completeExceptionally(e);
					return;
				} finally {
					List<Long> identifiers = new ArrayList<Long>(Arrays.asList(
							dataspace_id,
							datatype_id,
							memspace_id,
							errorspace_id,
							errortype_id,
							errormemspace_id));
	
					try {
						NcdNexusUtils.closeH5idList(identifiers);
					} catch (HDF5LibraryException e) {
						getLogger().info("Error closing NeXus handle identifier", e);
					}
					if (lock != null && lock.isHeldByCurrentThread()) {
						lock.unlock();
					}
				}
				
				data = DatasetUtils.cast(data, Dataset.FLOAT32);
				double mean = (Double) data.mean(true);
				double std = (Double) data.stdDeviation();
				data.isubtract(mean).idivide(std);
				errors.idivide(std);
					
				try {
					if (monitor.isCanceled()) {
						throw new OperationCanceledException(getName() + " stage has been cancelled.");
					}
					
					long[] start = new long[grid.length + 1];
					long[] count = new long[grid.length + 1];
					long[] block = new long[grid.length + 1];
					
					start = (long[]) ConvertUtils.convert(
							ArrayUtils.addAll(Arrays.copyOf(new int[] {}, grid.length),
								new int[] {idx}), 
							long[].class);
					block = Arrays.copyOf(frames, grid.length + 1);
					block[grid.length] = 1;
					Arrays.fill(count, 1L);
					
					lock.lock();
					
					dataspace_id = H5.H5Dget_space(resultDataID);
					datatype_id = H5.H5Dget_type(resultDataID);
					memspace_id = H5.H5Screate_simple(block.length, block, null);
					int select_id = H5.H5Sselect_hyperslab(
							dataspace_id,
							HDF5Constants.H5S_SELECT_SET,
							start, block, count, block);
					if (select_id < 0) {
						throw new HDF5Exception("Failed to allocate space fro writing standardised intensity data");
					}
					int write_id = H5.H5Dwrite(
							resultDataID,
							datatype_id,
							memspace_id,
							dataspace_id,
							HDF5Constants.H5P_DEFAULT,
							data.getBuffer());
					if (write_id < 0) {
						throw new HDF5Exception("Failed to write standardised intensity data into the results file");
					}
					
					errorspace_id = H5.H5Dget_space(resultErrorsID);
					errortype_id = H5.H5Dget_type(resultErrorsID);
					errormemspace_id = H5.H5Screate_simple(block.length, block, null);
					select_id = H5.H5Sselect_hyperslab(
							errorspace_id,
							HDF5Constants.H5S_SELECT_SET,
							start, block, count, block);
					if (select_id < 0) {
						throw new HDF5Exception("Failed to allocate space fro writing standardised intensity errors");
					}
					write_id = H5.H5Dwrite(
							resultErrorsID,
							errortype_id,
							errormemspace_id,
							errorspace_id,
							HDF5Constants.H5P_DEFAULT,
							errors.getBuffer());
					if (write_id < 0) {
						throw new HDF5Exception("Failed to write standardised intensity errors into the results file");
					}
				} catch (HDF5LibraryException e) {
					task.completeExceptionally(e);
					return;
				} catch (HDF5Exception e) {
					task.completeExceptionally(e);
					return;
				} catch (OperationCanceledException e) {
					task.completeExceptionally(e);
					return;
				} finally {
					List<Long> identifiers = new ArrayList<Long>(Arrays.asList(
							dataspace_id,
							datatype_id,
							memspace_id,
							errorspace_id,
							errortype_id,
							errormemspace_id));
					try {
						NcdNexusUtils.closeH5idList(identifiers);
					} catch (HDF5LibraryException e) {
						getLogger().info("Error closing NeXus handle identifier", e);
					}
					if (lock != null && lock.isHeldByCurrentThread()) {
						lock.unlock();
					}
				}
			}
		}
	}
}
