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

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;
import javax.measure.unit.UnitFormat;

import org.apache.commons.beanutils.ConvertUtils;
import org.dawb.passerelle.common.parameter.roi.ROIParameter;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.hdf.object.Nexus;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.PositionIterator;
import org.jscience.physics.amount.Amount;

import ptolemy.data.BooleanToken;
import ptolemy.data.ObjectToken;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.ncd.core.SectorIntegration;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
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

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;

/**
 * Actor for performing sector integration of scattering data
 * 
 * @author Irakli Sikharulidze
 */
public class NcdSectorIntegrationForkJoinTransformer extends NcdAbstractDataForkJoinTransformer {

	private static final long serialVersionUID = 9161664703395096017L;
	
	private SectorROI intSector;
	private Dataset[] areaData;
	private Amount<ScatteringVectorOverDistance> gradient;
	private Amount<ScatteringVector> intercept;
	private Amount<Length> cameraLength;
	private Amount<Energy> energy;
	private Amount<Length> pxSize;
	private Unit<ScatteringVector> axisUnit;
	private Dataset mask;

	private boolean doRadial = false;
	private boolean doAzimuthal = false;
	private boolean doFast = false;

	public Parameter gradientParam, interceptParam, cameraLengthParam, energyParam;
	public Parameter maskParam, doRadialParam, doAzimuthalParam, doFastParam;
	public Parameter axisUnitParam, pxSizeParam;
	public ROIParameter sectorROIParam;
	
	private long azimuthalDataID, azimuthalErrorsID;
	private long azimuthalAxisID;

	public Port portAzimuthal;

	public NcdSectorIntegrationForkJoinTransformer(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);

		sectorROIParam = new ROIParameter(this, "sectorROIParam");
		maskParam = new Parameter(this, "maskParam", new ObjectToken());
		doRadialParam = new Parameter(this, "doRadialParam", new BooleanToken(true));
		doAzimuthalParam = new Parameter(this, "doAzimuthalParam", new BooleanToken(true));
		doFastParam = new Parameter(this, "doFastParam", new BooleanToken(true));

		gradientParam = new Parameter(this, "gradientParam", new ObjectToken());
		interceptParam = new Parameter(this, "interceptParam", new ObjectToken());
		cameraLengthParam = new Parameter(this, "cameraLengthParam", new ObjectToken());
		energyParam = new Parameter(this, "energyParam", new ObjectToken());
		axisUnitParam = new Parameter(this, "axisUnitParam", new ObjectToken());
		pxSizeParam = new Parameter(this, "pxSizeParam", new ObjectToken());
		
		portAzimuthal = PortFactory.getInstance().createOutputPort(this, "Azimuthal");
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

			Amount<?> amount = readAmountObject(gradientParam, ScatteringVectorOverDistance.UNIT);
			if (amount != null) {
				gradient = 	amount.to(amount.getUnit().asType(ScatteringVectorOverDistance.class));
			}
			amount = readAmountObject(interceptParam, ScatteringVector.UNIT);
			if (amount != null) {
				intercept = amount.to(amount.getUnit().asType(ScatteringVector.class));
			}
			amount = readAmountObject(cameraLengthParam, Length.UNIT);
			if (amount != null) {
				cameraLength = amount.to(amount.getUnit().asType(Length.class));
			}
			amount = readAmountObject(energyParam, Energy.UNIT);
			if (amount != null) {
				energy = amount.to(amount.getUnit().asType(Energy.class));
			}
			amount = readAmountObject(pxSizeParam, Length.UNIT);
			if (amount != null) {
				pxSize = amount.to(amount.getUnit().asType(Length.class));
			}
			
			Object obj = ((ObjectToken) axisUnitParam.getToken()).getValue();
			if (obj != null) {
				if (obj instanceof Unit<?>) {
					Unit<?> unit = (Unit<?>) obj;
					if (unit.isCompatible(ScatteringVector.UNIT)) {
						axisUnit = unit.asType(ScatteringVector.class);
					}
				} else {
					String msg = axisUnitParam.getName() + ": Invalid input parameter";
					throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, msg, this, null);
				}
			}

			doRadial = ((BooleanToken) doRadialParam.getToken()).booleanValue();
			doAzimuthal = ((BooleanToken) doAzimuthalParam.getToken()).booleanValue();
			doFast = ((BooleanToken) doFastParam.getToken()).booleanValue();
			

			task = new SectorIntegrationTask(true, null);

		} catch (Exception e) {
			throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, "Error initializing my actor",
					this, e);
		}
	}

	private Amount<?> readAmountObject(final Parameter parameter, final Unit<?> parameterUnit)
			throws InitializationException, IllegalActionException {
		Object obj = ((ObjectToken) parameter.getToken()).getValue();
		if (obj == null) {
			return null;
		}
		if (obj instanceof Amount<?>) {
			Amount<?> amount = (Amount<?>) obj;
			Unit<?> unit = amount.getUnit();
			if (unit.isCompatible(parameterUnit)) {
				return amount;
			}
		}
		String msg = parameter.getName() + ": Invalid input parameter";
		throw new InitializationException(ErrorCode.ACTOR_INITIALISATION_ERROR, msg, this, null);
	}

	@Override
	protected void configureActorParameters() throws HDF5Exception {
		super.configureActorParameters();
		
		if (doAzimuthal) {
			long[] azFrames = getAzimuthalDataShape();
			long typeFloat = HDF5Constants.H5T_NATIVE_FLOAT;
			long typeDouble = HDF5Constants.H5T_NATIVE_DOUBLE;
			azimuthalDataID = NcdNexusUtils.makedata(resultGroupID, "azimuth", typeFloat, azFrames, false, "counts");
			azimuthalErrorsID = NcdNexusUtils.makedata(resultGroupID, "azimuth_errors", typeDouble, azFrames, false,
					"counts");
		}
	}

	@Override
	protected long[] getResultDataShape() {
		int[] areaShape = (int[]) ConvertUtils.convert(
				Arrays.copyOfRange(frames, frames.length - dimension, frames.length), int[].class);
		areaData = ROIProfile.area(areaShape, Dataset.FLOAT32, mask, intSector, doRadial, doAzimuthal, doFast);
		
		int areaDataRank = areaData[0].getRank();
		int[] areaDataShape = areaData[0].getShape();
		int secRank = frames.length - dimension + 1;
		long[] secFrames = Arrays.copyOf(frames, secRank);
		secFrames[secRank - 1] = areaDataShape[areaDataRank - 1];
		return secFrames;
	}

	@Override
	protected int getResultDimension() {
		return 1;
	}
	
	private long[] getAzimuthalDataShape() {
		int areaDataRank = areaData[1].getRank();
		int[] areaDataShape = areaData[1].getShape();
		int azRank = frames.length - dimension + 1;
		long[] azFrames = Arrays.copyOf(frames, azRank);
		azFrames[azRank - 1] = areaDataShape[areaDataRank - 1];
		return azFrames;
	}

	private Dataset calculateAzimuthalAxisDataset() {
		
		final Dataset xi;
		
		long[] azShape = getAzimuthalDataShape(); 
		int azSize = (int) azShape[azShape.length - 1];
		if (intSector.getSymmetry() != SectorROI.FULL) {
			xi = DatasetFactory.createLinearSpace(intSector.getAngleDegrees(0), intSector.getAngleDegrees(1), azSize, Dataset.FLOAT64);
		} else {
			xi = DatasetFactory.createLinearSpace(intSector.getAngleDegrees(0), intSector.getAngleDegrees(0) + 360., azSize, Dataset.FLOAT64);
		}
		xi.setName("degrees");
		
		return xi;
	}

	private Dataset calculateQaxisDataset() {
		
		Dataset qaxis = null;
		Dataset qaxisErr = null;

		long[] secFrames = getResultDataShape();
		int numPoints = (int) secFrames[secFrames.length - 1];
		if (gradient != null &&	intercept != null && pxSize != null &&	axisUnit != null) {
			qaxis = DatasetFactory.zeros(new int[] { numPoints }, Dataset.FLOAT32);
			qaxisErr = DatasetFactory.zeros(new int[] { numPoints }, Dataset.FLOAT32);
			double d2bs = intSector.getRadii()[0];
			for (int i = 0; i < numPoints; i++) {
				Amount<ScatteringVector> amountQaxis = gradient.times(i + d2bs).times(pxSize).plus(intercept)
						.to(axisUnit);
				qaxis.set(amountQaxis.getEstimatedValue(), i);
				qaxisErr.set(amountQaxis.getAbsoluteError(), i);
			}
			qaxis.setErrors(qaxisErr);
			return qaxis;
		}
		qaxis = DatasetUtils.cast(DatasetUtils.indices(numPoints).squeeze(), Dataset.FLOAT32);
		return qaxis;
	}
	
	private class SectorIntegrationTask extends RecursiveAction {

		private static final long serialVersionUID = 4409344480477216724L;
		
		private boolean forkTask;
		private int[] pos;

		public SectorIntegrationTask(boolean forkTask, int[] pos) {
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
				while (itr.hasNext()) {
					SectorIntegrationTask task = new SectorIntegrationTask(false, itr.getPos());
					// Processing frames sequentially to avoid potential OutOfMemory problems
					// as present sector integration algorithm already uses multiple fork/join threads
					task.compute();
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
					inputData.setErrors(inputErrors);
				} else {
					// Use counting statistics if no input error estimates are available 
					DoubleDataset inputErrorsBuffer = inputData.copy(DoubleDataset.class);
					inputData.setErrorBuffer(inputErrorsBuffer);
				}
				lock.unlock();


				Dataset myazdata = null, myazerrors = null;
				Dataset myraddata = null, myraderrors = null;

				SectorIntegration sec = new SectorIntegration();
				sec.setROI(intSector);
				sec.setAreaData(areaData);
				sec.setCalculateRadial(doRadial);
				sec.setCalculateAzimuthal(doAzimuthal);
				sec.setFast(doFast);
				
				// We need this shape parameter to restore all relevant dimensions
				// in the integrated sector results dataset shape
				int[] dataShape = inputData.getShape();

				Dataset data = NcdDataUtils.flattenGridData(inputData, dimension);

				Dataset[] mydata = sec.process(data, data.getShape()[0], mask);
				int resLength = dataShape.length - dimension + 1;
				if (doAzimuthal) {
					myazdata = DatasetUtils.cast(mydata[0], Dataset.FLOAT32);
					if (myazdata != null) {
						if (myazdata.hasErrors()) {
							myazerrors = mydata[0].getErrorBuffer();
						}

						int[] resAzShape = Arrays.copyOf(dataShape, resLength);
						resAzShape[resLength - 1] = myazdata.getShape()[myazdata.getRank() - 1];
						myazdata = myazdata.reshape(resAzShape);

						if (myazerrors != null) {
							myazerrors = myazerrors.reshape(resAzShape);
							myazdata.setErrorBuffer(myazerrors);
						}
					}
				}
				if (doRadial) {
					myraddata = DatasetUtils.cast(mydata[1], Dataset.FLOAT32);
					if (myraddata != null) {
						if (myraddata.hasErrors()) {
							myraderrors = mydata[1].getErrorBuffer();
						}
						int[] resRadShape = Arrays.copyOf(dataShape, resLength);
						resRadShape[resLength - 1] = myraddata.getShape()[myraddata.getRank() - 1];
						myraddata = myraddata.reshape(resRadShape);
						if (myraderrors != null) {
							myraderrors = myraderrors.reshape(resRadShape);
							myraddata.setErrorBuffer(myraderrors);
						}
					}
				}
				lock.lock();
				if (doAzimuthal && myazdata != null) {
					DataSliceIdentifiers azimuth_id = new DataSliceIdentifiers();
					azimuth_id.setIDs(resultGroupID, azimuthalDataID);
					azimuth_id.setSlice(currentSliceParams);
					DataSliceIdentifiers err_azimuth_id = new DataSliceIdentifiers();
					err_azimuth_id.setIDs(resultGroupID, azimuthalErrorsID);
					err_azimuth_id.setSlice(currentSliceParams);
					
					writeResults(azimuth_id, myazdata, dataShape, dimension);
					if (myazdata.hasErrors()) {
						writeResults(err_azimuth_id, myazdata.getErrors(), dataShape, dimension);
					}
				}
				if (doRadial && myraddata != null) {
					DataSliceIdentifiers sector_id = new DataSliceIdentifiers();
					sector_id.setIDs(resultGroupID, resultDataID);
					sector_id.setSlice(currentSliceParams);
					DataSliceIdentifiers err_sector_id = new DataSliceIdentifiers();
					err_sector_id.setIDs(resultGroupID, resultErrorsID);
					err_sector_id.setSlice(currentSliceParams);
					
					writeResults(sector_id, myraddata, dataShape, dimension);
					if (myraddata.hasErrors()) {
						writeResults(err_sector_id, myraddata.getErrors(), dataShape, dimension);
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

	@Override
	protected void writeAdditionalPorts(ManagedMessage receivedMsg, ProcessResponse response) throws MessageException {
		if (doAzimuthal) {
			ManagedMessage outputMsg = createMessageFromCauses(receivedMsg);
			NcdProcessingObject obj = new NcdProcessingObject(
					1,
					entryGroupID,
					processingGroupID,
					resultGroupID,
					azimuthalDataID,
					azimuthalErrorsID,
					azimuthalAxisID,
					-1,
					lock,
					monitor);
			outputMsg.setBodyContent(obj, "application/octet-stream");
			response.addOutputMessage(portAzimuthal, outputMsg);
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

		long filespaceID = H5.H5Dget_space(dataIDs.dataset_id);
		long typeID = H5.H5Dget_type(dataIDs.dataset_id);
		long memspaceID = H5.H5Screate_simple(resRank, resBlock, null);
		int selectID = H5.H5Sselect_hyperslab(filespaceID, HDF5Constants.H5S_SELECT_SET, resStart, resBlock, resCount, resBlock);
		if (selectID < 0) {
			throw new HDF5Exception("Error allocating space for writing sector integration results");
		}
		int writeID = H5.H5Dwrite(dataIDs.dataset_id, typeID, memspaceID, filespaceID, HDF5Constants.H5P_DEFAULT, data.getBuffer());
		if (writeID < 0) {
			throw new HDF5Exception("Error writing sector integration results");
		}
		H5.H5Sclose(filespaceID);
		H5.H5Sclose(memspaceID);
		H5.H5Tclose(typeID);
	}
	
	@Override
	protected void writeAxisData() throws HDF5Exception {
		Dataset qaxis = calculateQaxisDataset();
		long[] qaxisShape = (long[]) ConvertUtils.convert(qaxis.getShape(), long[].class);
		
		UnitFormat unitFormat = UnitFormat.getUCUMInstance();
		String units = "a.u.";
		String axisName = "indecies"; 
		if (axisUnit != null) {
			units = unitFormat.format(axisUnit);
			axisName = "q";
		}
		int[] qaxisRank = new int[] { qaxis.getRank() };
		resultAxisDataID = NcdNexusUtils.makeaxis(resultGroupID, axisName, HDF5Constants.H5T_NATIVE_FLOAT, qaxisShape, qaxisRank,
				1, units);

		long filespace_id = H5.H5Dget_space(resultAxisDataID);
		long type_id = H5.H5Dget_type(resultAxisDataID);
		long memspace_id = H5.H5Screate_simple(qaxis.getRank(), qaxisShape, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(resultAxisDataID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, qaxis.getBuffer());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type_id);
		
		if (qaxis.hasErrors()) {
			long[] qaxisShapeError = (long[]) ConvertUtils.convert(qaxis.getShape(), long[].class);
			resultAxisErrorsID = NcdNexusUtils.makedata(resultGroupID, axisName + "_errors", HDF5Constants.H5T_NATIVE_DOUBLE, qaxisShapeError,
				false, units);
		
			filespace_id = H5.H5Dget_space(resultAxisErrorsID);
			type_id = H5.H5Dget_type(resultAxisErrorsID);
			memspace_id = H5.H5Screate_simple(qaxis.getRank(), qaxisShapeError, null);
			H5.H5Sselect_all(filespace_id);
			H5.H5Dwrite(resultAxisErrorsID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, qaxis.getErrors().getBuffer());
		
			H5.H5Sclose(filespace_id);
			H5.H5Sclose(memspace_id);
			H5.H5Tclose(type_id);
		}
		
		if (doAzimuthal) {
			writeAzimuthalAxisData();
		}		
	}
	
	private void writeAzimuthalAxisData() throws HDF5Exception {
		Dataset azAxis = calculateAzimuthalAxisDataset();
		long[] azAxisShape = (long[]) ConvertUtils.convert(azAxis.getShape(), long[].class);
		
		String units = azAxis.getName();
		String axisName = "direction"; 
		int[] azAxisRank = new int[] { azAxis.getRank() };
		azimuthalAxisID = NcdNexusUtils.makeaxis(resultGroupID, axisName, HDF5Constants.H5T_NATIVE_DOUBLE, azAxisShape, azAxisRank,
				1, units);

		long filespace_id = H5.H5Dget_space(azimuthalAxisID);
		long type_id = H5.H5Dget_type(azimuthalAxisID);
		long memspace_id = H5.H5Screate_simple(azAxis.getRank(), azAxisShape, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(azimuthalAxisID, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, azAxis.getBuffer());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type_id);		
	}
	
	@Override
	protected void writeNcdMetadata() throws HDF5LibraryException, NullPointerException, HDF5Exception {
		super.writeNcdMetadata();

		if (intSector != null) {
			writeBeamCenterMetadata(resultGroupID);
			writeIntegrationAnglesMetadata(resultGroupID);
			writeIntegrationRadiiMetadata(resultGroupID);
			writeIntegrationSymmetryMetadata(resultGroupID);
		}
		if (cameraLength != null) {
			writeCameraLengthMetadata(resultGroupID);
		}
		if (energy != null) {
			writeEnergyMetadata(resultGroupID);
		}
		if (mask != null) {
			writeMaskMetadata(resultGroupID);
		}
		if (gradient != null && intercept != null) {
			writeQcalibrationMetadata(resultGroupID);
		}
	}

	private void writeBeamCenterMetadata(long datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long beamcenter_id = NcdNexusUtils.makedata(datagroup_id, "beam centre", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {2}, false, "pixels");
		long filespace_id = H5.H5Dget_space(beamcenter_id);
		long type = H5.H5Dget_type(beamcenter_id);
		long memspace_id = H5.H5Screate_simple(1, new long[] {2}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(beamcenter_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, intSector.getPoint());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(beamcenter_id);
	}
	
	private void writeCameraLengthMetadata(long datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long cameralength_id = NcdNexusUtils.makegroup(datagroup_id, "camera length", Nexus.DATA);
		long cameralength_data_id = NcdNexusUtils.makedata(cameralength_id, "data", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {1}, false, "mm");
		long cameralength_error_id = NcdNexusUtils.makedata(cameralength_id, "errors", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {1}, false, "mm");
		long filespace_id = H5.H5Dget_space(cameralength_data_id);
		long type = H5.H5Dget_type(cameralength_data_id);
		long memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(cameralength_data_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, new double[] {cameraLength.to(SI.MILLIMETRE).getEstimatedValue()});
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(cameralength_data_id);
		
		filespace_id = H5.H5Dget_space(cameralength_error_id);
		type = H5.H5Dget_type(cameralength_error_id);
		memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(cameralength_error_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, new double[] {cameraLength.to(SI.MILLIMETRE).getAbsoluteError()});
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(cameralength_error_id);
		H5.H5Gclose(cameralength_id);
	}
	
	private void writeEnergyMetadata(long datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long energy_id = NcdNexusUtils.makedata(datagroup_id, "energy", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {1}, false, "keV");
		long filespace_id = H5.H5Dget_space(energy_id);
		long type = H5.H5Dget_type(energy_id);
		long memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(energy_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, new double[] {energy.doubleValue(SI.KILO(NonSI.ELECTRON_VOLT))});
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(energy_id);
	}
	
	private void writeIntegrationAnglesMetadata(long datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long angles_id = NcdNexusUtils.makedata(datagroup_id, "integration angles", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {2}, false, "Deg");
		long filespace_id = H5.H5Dget_space(angles_id);
		long type = H5.H5Dget_type(angles_id);
		long memspace_id = H5.H5Screate_simple(1, new long[] {2}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(angles_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, intSector.getAnglesDegrees());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(angles_id);
	}
	
	private void writeIntegrationRadiiMetadata(long datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long radii_id = NcdNexusUtils.makedata(datagroup_id, "integration radii", HDF5Constants.H5T_NATIVE_DOUBLE, new long[] {2}, false, "pixels");
		long filespace_id = H5.H5Dget_space(radii_id);
		long type = H5.H5Dget_type(radii_id);
		long memspace_id = H5.H5Screate_simple(1, new long[] {2}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(radii_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, intSector.getRadii());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(radii_id);
	}
	
	private void writeIntegrationSymmetryMetadata(long datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long symmetry_type = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		String sym = intSector.getSymmetryText();
		H5.H5Tset_size(symmetry_type, sym.length());
		long symmetry_id = NcdNexusUtils.makedata(datagroup_id, "integration symmetry", symmetry_type, new long[] {1});
		long filespace_id = H5.H5Dget_space(symmetry_id);
		long memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(symmetry_id, symmetry_type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, sym.getBytes());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(symmetry_type);
		H5.H5Dclose(symmetry_id);
	}
	
	private void writeMaskMetadata(long datagroup_id) throws HDF5LibraryException, NullPointerException, HDF5Exception {
		long[] maskShape = (long []) ConvertUtils.convert(mask.getShape(), long[].class);
		long mask_id = NcdNexusUtils.makedata(datagroup_id, "mask", HDF5Constants.H5T_NATIVE_INT8, maskShape, false, "pixels");
		long filespace_id = H5.H5Dget_space(mask_id);
		long type = H5.H5Dget_type(mask_id);
		long memspace_id = H5.H5Screate_simple(mask.getRank(), maskShape, null);
		H5.H5Sselect_all(filespace_id);
		H5.H5Dwrite(mask_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, (DatasetUtils.cast(mask, Dataset.INT8)).getBuffer());
		
		H5.H5Sclose(filespace_id);
		H5.H5Sclose(memspace_id);
		H5.H5Tclose(type);
		H5.H5Dclose(mask_id);
	}
	
	private void writeQcalibrationMetadata(long datagroup_id) throws HDF5LibraryException, NullPointerException,	HDF5Exception {

		long qcalibration_id = NcdNexusUtils.makegroup(datagroup_id, "qaxis calibration", Nexus.DATA);

		List<Object[]> data = new ArrayList<Object[]>();
		data.add(new Object[] { "gradient", gradient.getEstimatedValue(), gradient.getUnit() });
		data.add(new Object[] { "gradient_errors", gradient.getAbsoluteError(), gradient.getUnit() });
		data.add(new Object[] { "intercept", intercept.getEstimatedValue(), intercept.getUnit() });
		data.add(new Object[] { "intercept_errors", intercept.getAbsoluteError(), intercept.getUnit() });

		for (Object[] element : data) {
			String name = (String) element[0];
			double[] value = new double[] { (Double) element[1] };
			Unit<?> unit = (Unit<?>) element[2];

			long data_id = NcdNexusUtils.makedata(qcalibration_id, name, HDF5Constants.H5T_NATIVE_DOUBLE,
					new long[] { 1 });
			long filespace_id = H5.H5Dget_space(data_id);
			long type = H5.H5Dget_type(data_id);
			long memspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
			H5.H5Sselect_all(filespace_id);

			H5.H5Dwrite(data_id, type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, value);

			// add unit attribute
			{
				UnitFormat unitFormat = UnitFormat.getUCUMInstance();
				String unitString = unitFormat.format(unit);
				long attrspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
				long attrtype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
				H5.H5Tset_size(attrtype_id, unitString.length());

				long attr_id = H5.H5Acreate(data_id, "units", attrtype_id, attrspace_id, HDF5Constants.H5P_DEFAULT,
						HDF5Constants.H5P_DEFAULT);
				if (attr_id < 0) {
					throw new HDF5Exception("H5 putattr write error: can't create attribute");
				}
				int write_id = H5.H5Awrite(attr_id, attrtype_id, unitString.getBytes());
				if (write_id < 0) {
					throw new HDF5Exception("H5 makegroup attribute write error: can't create signal attribute");
				}
				H5.H5Aclose(attr_id);
				H5.H5Sclose(attrspace_id);
				H5.H5Tclose(attrtype_id);
			}

			H5.H5Sclose(filespace_id);
			H5.H5Sclose(memspace_id);
			H5.H5Tclose(type);
			H5.H5Dclose(data_id);
		}
		H5.H5Gclose(qcalibration_id);
	}

	@Override
	protected void doWrapUp() throws TerminationException {
		if (doAzimuthal) {
			try {
				List<Long> identifiers = new ArrayList<Long>(Arrays.asList(
						azimuthalDataID,
						azimuthalErrorsID,
						azimuthalAxisID));

				NcdNexusUtils.closeH5idList(identifiers);
			} catch (HDF5LibraryException e) {
				getLogger().info("Error closing NeXus handle identifier", e);
			}
		}
		super.doWrapUp();
	}
	
}
