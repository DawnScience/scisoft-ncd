/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.Arrays;
import java.util.List;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.Slice;
import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.eclipse.dawnsci.analysis.api.metadata.MaskMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IntegerDataset;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.metadata.AxesMetadataImpl;
import uk.ac.diamond.scisoft.analysis.processing.io.NexusNcdMetadataReader;
import uk.ac.diamond.scisoft.analysis.processing.io.QAxisCalibration;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.ncd.core.SectorIntegration;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

public class NcdSectorIntegrationOperation extends AbstractOperation<NcdSectorIntegrationModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.NcdSectorIntegration";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}
	
	@Override
	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		NexusNcdMetadataReader reader = new NexusNcdMetadataReader(model.getFilePath());
		IROI roi;
		try {
			roi = reader.getROIDataFromFile();
		} catch (Exception e) {
			throw new OperationException(this, e);
		}

		((SectorROI)roi).setAverageArea(false);
		model.setRegion(roi);
		
		SectorIntegration sec = new SectorIntegration();
		int[] frames = NcdOperationUtils.addDimension(slice.getShape());
		int dimension = 2; //should match input rank
		int[] areaShape = (int[]) ConvertUtils.convert(
				Arrays.copyOfRange(frames, frames.length - dimension, frames.length), int[].class);
		List<MaskMetadata> mask;
		try {
			mask = slice.getMetadata(MaskMetadata.class);
		} catch (Exception e) {
			throw new OperationException(this, e);
		}
		
		Dataset maskDataset = (Dataset) mask.get(0).getMask().getSlice();
		Dataset sliceDataset = (Dataset) slice.getSlice(new Slice());
		
		sliceDataset.resize(NcdOperationUtils.addDimension(sliceDataset.getShape()));
		Dataset newSliceDataset = new IntegerDataset(((IntegerDataset)sliceDataset).getData(), sliceDataset.getShape()); //remove metadata so there won't be any checking
		if (!sliceDataset.hasErrors()) {
			// Use counting statistics if no input error estimates are available 
			DoubleDataset inputErrorsBuffer = new DoubleDataset(newSliceDataset);
			newSliceDataset.setErrorBuffer(inputErrorsBuffer);
		}
		
		Dataset[] areaData = ROIProfile.area(areaShape, Dataset.FLOAT32, maskDataset, (SectorROI) model.getRegion(), true, false, false);

		sec.setAreaData(areaData);
		sec.setCalculateRadial(true);
		sec.setROI((SectorROI)model.getRegion());
		
		Dataset[] mydata = sec.process(newSliceDataset, 1, maskDataset);
		int resLength = slice.getShape().length - dimension + 1;

		Dataset myraddata = DatasetUtils.cast(mydata[1], Dataset.FLOAT32);
		Dataset myraderrors = null;
		if (myraddata != null) {
			if (myraddata.hasErrors()) {
				myraderrors = mydata[1].getErrorBuffer();
			}
			int[] resRadShape = Arrays.copyOf(slice.getShape(), resLength);
			resRadShape[resLength - 1] = myraddata.getShape()[myraddata.getRank() - 1];
			myraddata = myraddata.reshape(resRadShape);
			if (myraderrors != null) {
				myraderrors = myraderrors.reshape(resRadShape);
				myraddata.setErrorBuffer(myraderrors);
			}
		}

		Dataset qaxis = null;
		try {
			qaxis = calculateQaxisDataset(reader.getQAxisCalibrationFromFile(), slice.getMetadata(IDiffractionMetadata.class).get(0), myraddata.getShape(), (SectorROI)model.getRegion());
		} catch (Exception e) {
			throw new OperationException(this, e);
		}
		OperationData toReturn = new OperationData();
		Dataset myres = new FloatDataset(myraddata);
		if (myraderrors != null) {
			myres.setErrorBuffer(new DoubleDataset(myraderrors));
		}
		if (qaxis != null) {
			AxesMetadataImpl axes = new AxesMetadataImpl(1);
			axes.setAxis(0, qaxis);
			myres.setMetadata(axes);
		}
		toReturn.setData(myres);
		return toReturn;
	}
	
	/**
	 * Copied from u.a.d.s.ncd.passerelle.actors.NcdSectorIntegrationForkJoinTransformer
	 * @return
	 */
	@SuppressWarnings("unused")
	private Dataset calculateQaxisDataset(QAxisCalibration cal, IDiffractionMetadata dif, int[] datasetShape, SectorROI intSector) {
		
		Dataset qaxis = null;
		Dataset qaxisErr = null;
		Amount<ScatteringVectorOverDistance> gradient = cal.getGradient();
		Amount<ScatteringVector> intercept = cal.getIntercept();
		Amount<Length> cameraLength = Amount.valueOf(dif.getOriginalDetector2DProperties().getDetectorDistance(), SI.MILLIMETER);
		Amount<Energy> energy = Amount.valueOf(12.39842/dif.getOriginalDiffractionCrystalEnvironment().getWavelength(), SI.KILO(NonSI.ELECTRON_VOLT));
		Amount<Length> pxSize = Amount.valueOf(dif.getOriginalDetector2DProperties().getHPxSize(), SI.MILLIMETER);
		Unit<ScatteringVector> axisUnit = Unit.ONE.divide(NonSI.ANGSTROM).asType(ScatteringVector.class);
		
		int[] secFrames = datasetShape;
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
			qaxis.setError(qaxisErr);
			qaxis.setName("q");
			return qaxis;
		}
		qaxis = DatasetUtils.cast(DatasetUtils.indices(numPoints).squeeze(), Dataset.FLOAT32);
		qaxis.setName("q");
		return qaxis;
	}
}