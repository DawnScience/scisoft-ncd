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

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;

import ptolemy.data.ObjectToken;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.roi.PointROI;
import uk.ac.diamond.scisoft.analysis.roi.PointROIList;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.core.ErrorCode;

/**
 * Actor for calculating standardised intensity values for selected pixels
 * 
 * @author Irakli Sikharulidze
 */
public class NcdImageStatsForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	public Parameter maskParam;

	private PointROIList points;
	private int numSamples = 1000;
	
	private AbstractDataset mask;

	public NcdImageStatsForkJoinTransformer(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);

		dataName = "ImageStats";

		maskParam = new Parameter(this, "maskParam", new ObjectToken());
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		
		try {
			Object objMask = ((ObjectToken) maskParam.getToken()).getValue();
			if (objMask != null) {
				if (objMask instanceof AbstractDataset) {
					mask = (AbstractDataset) objMask;
				} else {
					throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Invalid mask parameter",
							this, null);
				}
			}
			
			points = new PointROIList();
			task = new ImageStatsTask();
			
		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}
	
	private void generatePointROIList() {
		int[] imageShape = (int[]) ConvertUtils
				.convert(Arrays.copyOfRange(frames, frames.length - dimension, frames.length), int[].class);
		
		UniformIntegerDistribution randX = new UniformIntegerDistribution(0, imageShape[1]);
		UniformIntegerDistribution randY = new UniformIntegerDistribution(0, imageShape[0]);
		
		while (points.size() < numSamples) {
			int[] point = new int[] {randY.sample(), randX.sample()};
			PointROI pointROI = new PointROI(point);
			if (mask.getBoolean(point)) {
				points.append(pointROI);
			}
		}
	}

	@Override
	protected long[] getResultDataShape() {
		long[] resultFrames = Arrays.copyOfRange(frames, 0, frames.length - dimension + 1);
		resultFrames[resultFrames.length - 1] = numSamples;
		//while (ArrayUtils.contains(frameRange, 1L)) {
		//	frameRange = ArrayUtils.removeElement(frameRange, 1L);
		//}
		//long[] resultFrames = ArrayUtils.addAll(new long[] {numSamples}, frameRange);
		return resultFrames;
	}

	@Override
	protected int getResultDimension() {
		return 1;
	}
	
	private class ImageStatsTask extends RecursiveAction {

		@Override
		protected void compute() {
			
			generatePointROIList();
			
			int[] grid = (int[]) ConvertUtils
					.convert(Arrays.copyOf(frames, frames.length - dimension), int[].class);
			for (int idx = 0; idx < points.size(); idx++) {
				PointROI point = points.get(idx);

				AbstractDataset data;
				long[] start = new long[frames.length];
				long[] count = new long[frames.length];
				long[] block = new long[frames.length];
				
				start = (long[]) ConvertUtils.convert(
						ArrayUtils.addAll(Arrays.copyOf(new int[] {}, grid.length),
							point.getIntPoint()), 
						long[].class);
				block = Arrays.copyOf(frames, frames.length - dimension);
				Arrays.fill(block, frames.length-dimension, frames.length, 1L);
				Arrays.fill(count, 1L);
				
				int dataspace_id = -1;
				int datatype_id = -1;
				int dataclass_id = -1;
				int datasize_id = -1;
				int memspace_id = -1;
				
				try {
					dataspace_id = H5.H5Dget_space(inputDataID);
					datatype_id = H5.H5Dget_type(inputDataID);
					dataclass_id = H5.H5Tget_class(datatype_id);
					datasize_id = H5.H5Tget_size(datatype_id);
					memspace_id = H5.H5Screate_simple(block.length, block, null);
					
					lock.lock();
					int select_id = H5.H5Sselect_hyperslab(
							dataspace_id,
							HDF5Constants.H5S_SELECT_SET,
							start, block, count, block);
					if (select_id < 0) {
						throw new HDF5Exception("H5 select hyperslab error: can't allocate memory to read data");
					}
					
					int dtype = HDF5Loader.getDtype(dataclass_id, datasize_id);
					data = AbstractDataset.zeros(grid, dtype);
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
				} catch (HDF5LibraryException e) {
					throw new RuntimeException(e);
				} catch (HDF5Exception e) {
					throw new RuntimeException(e);
				} finally {
					List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(
							dataclass_id,
							datasize_id,
							dataspace_id,
							datatype_id,
							memspace_id));

					try {
						NcdNexusUtils.closeH5idList(identifiers);
					} catch (HDF5LibraryException e) {
						getLogger().info("Error closing NeXus handle identifier", e);
					}
					if (lock != null && lock.isHeldByCurrentThread()) {
						lock.unlock();
					}
				}
							
				double mean = (Double) data.mean(true);
				double std = (Double) data.stdDeviation();
				data.isubtract(mean).idivide(std);
					
				try {
					lock.lock();
					
					dataspace_id = H5.H5Dget_space(resultDataID);
					datatype_id = H5.H5Dget_type(resultDataID);
					memspace_id = H5.H5Screate_simple(block.length, block, null);
					int select_id = H5.H5Sselect_hyperslab(
							dataspace_id,
							HDF5Constants.H5S_SELECT_SET,
							start, block, count, block);
					if (select_id < 0) {
						throw new HDF5Exception("Failed to allocate space fro writing ImageStats data");
					}
					int write_id = H5.H5Dwrite(
							resultDataID,
							datatype_id,
							memspace_id,
							dataspace_id,
							HDF5Constants.H5P_DEFAULT,
							data.getBuffer());
					if (write_id < 0) {
						throw new HDF5Exception("Failed to write ImageStats data into the results file");
					}
				} catch (HDF5LibraryException e) {
					throw new RuntimeException(e);
				} catch (HDF5Exception e) {
					throw new RuntimeException(e);
				} finally {
					List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(
							datasize_id,
							dataspace_id,
							datatype_id,
							memspace_id));
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
