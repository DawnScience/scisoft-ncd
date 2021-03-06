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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.util.Pair;
import org.dawb.passerelle.common.parameter.roi.ROIParameter;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PointROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PointROIList;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.hdf5.HDF5Utils;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;

import ptolemy.data.ObjectToken;
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
 * Actor for calculating standardised intensity values for selected pixels
 * 
 * @author Irakli Sikharulidze
 */
public class NcdImageStatsForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = -6657567371116278560L;

	public Parameter maskParam;
	public ROIParameter sectorROIParam;
	
	private SectorROI intSector;

	private PointROIList points;
	private int numSamples = 100;
	private int numBins = 5; 
	private List<Set<Pair<Integer, Integer>>> resBins;
	
	private Map<Pair<Integer, Integer>, Double> radiiMap;
	private Percentile percentile;
	private double[] percentiles;
	private EuclideanDistance distance;
	
	private Dataset mask;

	

	public NcdImageStatsForkJoinTransformer(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);

		maskParam = new Parameter(this, "maskParam", new ObjectToken());
		sectorROIParam = new ROIParameter(this, "sectorROIParam");
		
		radiiMap = new HashMap<>(numSamples);
		percentile = new Percentile();
		percentiles = new double[numBins + 1];
		distance = new EuclideanDistance();
		resBins = new ArrayList<Set<Pair<Integer, Integer>>>(numBins);
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		
		try {
			IROI objSector = sectorROIParam.getRoi();
			if (objSector instanceof SectorROI) {
				intSector = ((SectorROI) objSector).copy();
				intSector.setClippingCompensation(true);
				intSector.setAverageArea(false);
			} else {
				throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR,
						"Invalid sector region parameter", this, null);
			}
			
			Object objMask = ((ObjectToken) maskParam.getToken()).getValue();
			if (objMask != null) {
				if (objMask instanceof Dataset) {
					mask = (Dataset) objMask;
				} else {
					throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Invalid mask parameter",
							this, null);
				}
			}
			
			points = new PointROIList();
			task = new ImageStatsTask();
			
		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing ImageStats actor",
					this, e);
		}
	}
	
	private void generatePointROIList() {
		int[] imageShape = (int[]) ConvertUtils
				.convert(Arrays.copyOfRange(frames, frames.length - dimension, frames.length), int[].class);
		
		UniformIntegerDistribution randX = new UniformIntegerDistribution(0, imageShape[1] - 1);
		UniformIntegerDistribution randY = new UniformIntegerDistribution(0, imageShape[0] - 1);
		
		while (points.size() < numSamples) {
			int[] point = new int[] {randY.sample(), randX.sample()};
			PointROI pointROI = new PointROI(point);
			if (intSector == null || intSector.containsPoint(point[1], point[0])) {
				if (mask == null || mask.getBoolean(point)) {
					points.append(pointROI);
					double radius = distance.compute(intSector.getPoint(), new double[] {point[0], point[1]});
					radiiMap.put(new Pair<Integer, Integer>(point[1], point[0]), radius);
				}
			}
		}
		
		// Calculate resolution bins 
		double[] sortedRadii = ArrayUtils.toPrimitive(radiiMap.values().toArray(new Double[] {}));
		Arrays.sort(sortedRadii);
		
		percentile.setData(sortedRadii);
		percentiles[0] = 0;
		percentiles[numBins] = Double.MAX_VALUE;
		for (int i = 1; i < numBins; i++) {
			double p = i * 100.0 / numBins;
			percentiles[i] = percentile.evaluate(p);			
		}
		
		// Subdivide points into resolution bins
		for (int bin = 0; bin < numBins; bin++) {
			HashSet<Pair<Integer, Integer>> pointSet = new HashSet<Pair<Integer, Integer>>();
			for (Entry<Pair<Integer, Integer>,Double> element : radiiMap.entrySet()) {
				double radius = element.getValue();
				if (radius > percentiles[bin] && radius < percentiles[bin + 1]) {
					pointSet.add(element.getKey());
					radiiMap.remove(element);
				}
			}
			resBins.add(pointSet);
		}
	}

	@Override
	protected long[] getResultDataShape() {
		long[] resultFrames = Arrays.copyOfRange(frames, 0, frames.length - dimension + 1);
		resultFrames[resultFrames.length - 1] = numBins;
		return resultFrames;
	}

	@Override
	protected int getResultDimension() {
		return 1;
	}
	
	private class ImageStatsTask extends RecursiveAction {

		private static final long serialVersionUID = 2962438408345745461L;

		@Override
		protected void compute() {
			
			generatePointROIList();
			
			int[] grid = (int[]) ConvertUtils
					.convert(Arrays.copyOf(frames, frames.length - dimension), int[].class);
			
			long dataspace_id = -1;
			long datatype_id = -1;
			int dataclass = -1;
			int datasize = -1;
			long memspace_id = -1;
			
			for (int idx = 0; idx < numBins; idx++) {
				
				Dataset binData = DatasetFactory.zeros(grid, Dataset.FLOAT32);
				
				for (Pair<Integer, Integer> point : resBins.get(idx)) {
				
					Dataset data;
					try {
						if (monitor.isCanceled()) {
							throw new OperationCanceledException(getName() + " stage has been cancelled.");
						}
						
						long[] start = new long[frames.length];
						long[] count = new long[frames.length];
						long[] block = new long[frames.length];
						
						start = (long[]) ConvertUtils.convert(
								ArrayUtils.addAll(Arrays.copyOf(new int[] {}, grid.length),
									new int[] {point.getSecond(), point.getFirst()}), 
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
						
						int dtype = HDF5Utils.getDType(dataclass, datasize);
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
					
					data = DatasetUtils.cast(data, Dataset.FLOAT32);
					binData.iadd(data);
				}
				double mean = (Double) binData.mean(true);
				double std = (Double) binData.stdDeviation();
				binData.isubtract(mean).idivide(std);
					
				try {
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
						throw new HDF5Exception("Failed to allocate space fro writing ImageStats data");
					}
					int write_id = H5.H5Dwrite(
							resultDataID,
							datatype_id,
							memspace_id,
							dataspace_id,
							HDF5Constants.H5P_DEFAULT,
							binData.getBuffer());
					if (write_id < 0) {
						throw new HDF5Exception("Failed to write ImageStats data into the results file");
					}
				} catch (HDF5LibraryException e) {
					task.completeExceptionally(e);
				} catch (HDF5Exception e) {
					task.completeExceptionally(e);
				} finally {
					List<Long> identifiers = new ArrayList<Long>(Arrays.asList(
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
