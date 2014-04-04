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
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.SimplePointChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.Well19937a;

import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.PositionIterator;
import uk.ac.diamond.scisoft.ncd.core.Invariant;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.data.plots.PorodPlotData;
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

	private AbstractDataset inputAxis;
	private long[] axisShape;

	private int cmaesLambda = 5;
	private int cmaesMaxIterations = 10000;
	private int cmaesCheckFeasableCount = 10;
	private ConvergenceChecker<PointValuePair> cmaesChecker = new SimplePointChecker<PointValuePair>(1e-4, 1e-4);

	private int rgDataID, porodDataID, porodErrorsID;
	
	public NcdInvariantForkJoinTransformer(CompositeEntity container, String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);

		dataName = "Invariant";
	}

	@Override
	protected void doInitialize() throws InitializationException {
		super.doInitialize();
		task = new InvariantTask(true, null);
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
	
	private class PorodLineFitFunction implements MultivariateFunction {

		private IDataset porodData;
		private IDataset porodAxis;
		
		public PorodLineFitFunction(IDataset data, IDataset axis) {
			super();
			this.porodData = data;
			this.porodAxis = axis;
		}

		@Override
		public double value(double[] params) {
			double solvation = params[0];
			double correlation = params[1];
			double exponent = params[2];

			double rms = 0.0;
			for (int i = 0; i < porodAxis.getSize(); i++) {
				double dataVal = porodData.getDouble(i);
				double axisVal = porodAxis.getDouble(i);
				double func = solvation / (1.0 + Math.pow(axisVal * correlation,  exponent));
				rms += Math.pow(dataVal - func, 2);
			}

			String msg = StringUtils.join(new String[] {
					"	solvation ",
					Double.toString(solvation),
					"	correlation ",
					Double.toString(correlation),
					"	exponent ",
					Double.toString(exponent)
					},
					" : ");
			//System.out.println(msg);
			msg = StringUtils.join(new String[] {
					"RMS",
					Double.toString(-Math.log(rms))
					},
					" : ");
			//System.out.println(msg);
			return -Math.log(rms);
		}
		
	}

	@Override
	protected void configureActorParameters() throws HDF5Exception {
		super.configureActorParameters();
		
		if (inputAxisDataID != -1) {
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

			int type = HDF5Constants.H5T_NATIVE_FLOAT;
			rgDataID = NcdNexusUtils.makedata(resultGroupID, "fit", type, frames, false, "N/A");
			porodDataID = NcdNexusUtils.makedata(resultGroupID, "porod_fit", type, frames, false, "N/A");
			type = HDF5Constants.H5T_NATIVE_DOUBLE;
			porodErrorsID = NcdNexusUtils.makedata(resultGroupID, "porod_fit_errors", type, frames, false, "N/A");
		}
	}
	
	public double[] getPorodPlotParameters(IDataset data, IDataset axis) {
		CMAESOptimizer optimizer = new CMAESOptimizer(
				cmaesMaxIterations,
				0.0,
				true,
				0,
				cmaesCheckFeasableCount,
				new Well19937a(),
				false,
				cmaesChecker);
		PorodLineFitFunction function = new PorodLineFitFunction(data, axis);

		int dataSize = axis.getSize();
		double qMax = axis.getDouble(dataSize - 1);
		double i0 = Math.abs(data.getDouble(0)); 
		double[] startPosition = new double[] { i0, 1.0 / qMax, 4.0 };
		double[] cmaesInputSigma = new double[] { 0.1 * i0 , 0.1 / qMax, 0.1};
		double[] lb = new double[] { 0.0, 0.0, 0.0};
		double[] ub = new double[] { Double.MAX_VALUE, Double.MAX_VALUE, 10.0};
		try {
			final PointValuePair res = optimizer.optimize(new MaxEval(cmaesMaxIterations),
					new ObjectiveFunction(function),
					GoalType.MAXIMIZE,
					new CMAESOptimizer.PopulationSize(cmaesLambda),
					new CMAESOptimizer.Sigma(cmaesInputSigma),
					new SimpleBounds(lb, ub),
					new InitialGuess(startPosition));

			double[] params = res.getPoint();
			double solvation = params[0];
			double correlation = params[1];
			double exponent = params[2];
			
			double rms = Math.exp(-function.value(params));
			
			System.out.println();
			System.out.println("Final Result");
			String msg = StringUtils.join(new String[] {
					"	solvation ",
					Double.toString(solvation),
					"	correlation ",
					Double.toString(correlation),
					"	exponent ",
					Double.toString(exponent)
					},
					" : ");
			System.out.println(msg);
			msg = StringUtils.join(new String[] {
					"RMS",
					Double.toString(rms)
					},
					" : ");
			System.out.println(msg);
			System.out.println();
			return params;
			
		} catch (MaxCountExceededException e) {
			getLogger().info("Maximum iteration count reached.");
		}
		return null;
	}
	
	public AbstractDataset getFitData(double[] params, IDataset axis) {
		double solvation = params[0];
		double correlation = params[1];
		double exponent = params[2];
		
		AbstractDataset result = AbstractDataset.zeros(axis.getShape(), AbstractDataset.FLOAT32);
		for (int i = 0; i < axis.getSize(); i++) {
			double axisVal = axis.getDouble(i);
			double func = solvation / (1.0 + Math.pow(axisVal * correlation,  exponent));
			result.set(func, i);
		}
		
		return result;
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
				
				if (inputAxis != null) {
					AbstractDataset axis = inputAxis.clone().squeeze();
					double[] params = getPorodPlotParameters(data.squeeze(), axis);
					int[] rgDataShape = Arrays.copyOf(dataShape, dataShape.length + 1);
					rgDataShape[rgDataShape.length - 1] = data.getSize();
					
					DataSliceIdentifiers rgDataIDs = new DataSliceIdentifiers();
					rgDataIDs.setIDs(resultGroupID, rgDataID);
					rgDataIDs.setSlice(sliceData);
					AbstractDataset tmpDataset = getFitData(params, axis);
					writeResults(rgDataIDs, tmpDataset, rgDataShape, 1);
					
					PorodPlotData plotData = (PorodPlotData) SaxsAnalysisPlotType.POROD_PLOT.getSaxsPlotDataObject();
					plotData.getPorodPlotParameters(data, axis);
					AbstractDataset tmpPorodDataset = plotData.getFitData(axis);
					
					DataSliceIdentifiers porodDataIDs = new DataSliceIdentifiers();
					porodDataIDs.setIDs(resultGroupID, porodDataID);
					porodDataIDs.setSlice(sliceData);
					writeResults(porodDataIDs, tmpPorodDataset, rgDataShape, 1);
					
					DataSliceIdentifiers porodErrorsIDs = new DataSliceIdentifiers();
					porodErrorsIDs.setIDs(resultGroupID, porodErrorsID);
					porodErrorsIDs.setSlice(sliceData);
					writeResults(porodErrorsIDs, tmpPorodDataset.getError(), rgDataShape, 1);
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
	protected void writeAxisData() throws HDF5Exception {
		if (inputAxis == null) {
			resultAxisDataID = -1;
			resultAxisErrorsID = -1;
		}
	}
	
}
