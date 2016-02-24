/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.eclipse.dawnsci.analysis.api.metadata.MaskMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.metadata.AxesMetadataImpl;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.processing.io.NexusNcdMetadataReader;
import uk.ac.diamond.scisoft.analysis.processing.io.QAxisCalibration;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.ncd.core.SectorIntegration;
import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdSourceProviderAdapter;
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
		IROI roi = model.getRegion();
		if (model.getRegion() == null) {
			try {
				NexusNcdMetadataReader reader = new NexusNcdMetadataReader(model.getFilePath());
				roi = reader.getROIDataFromFile();
				if (roi == null) {
					throw new Exception("ROI must be defined for this operation");
				}
			} catch (Exception e) {
				throw new OperationException(this, e);
			}
		}

		if (!(roi instanceof SectorROI)) {
			throw new OperationException(this, new IllegalArgumentException("The ROI must be a sector ROI"));
		}
		SectorROI sectorRoi = (SectorROI) roi;
		if (!sectorRoi.checkSymmetry(sectorRoi.getSymmetry())){
			throw new OperationException(this, new IllegalArgumentException("The symmetry is not compatible with the ROI"));
		}
		sectorRoi.setAverageArea(false);
		sectorRoi.setClippingCompensation(true);
		model.setRegion(sectorRoi);
		
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
		
		Dataset maskDataset = null;
		if (mask != null) {
			maskDataset = DatasetUtils.convertToDataset(mask.get(0).getMask()).getSlice();
		}
		Dataset sliceDataset = DatasetUtils.convertToDataset(slice.getSliceView());
		
		sliceDataset.resize(NcdOperationUtils.addDimension(sliceDataset.getShape()));
		Dataset sliceErrors = sliceDataset.getError();
		sliceDataset.clearMetadata(null);
		sliceDataset.setError(sliceErrors);
		if (!sliceDataset.hasErrors()) {
			// Use counting statistics if no input error estimates are available 
			DoubleDataset inputErrorsBuffer = new DoubleDataset(sliceDataset);
			sliceDataset.setErrorBuffer(inputErrorsBuffer);
		}

		boolean calculateAzimuthal;
		if (model.getAzimuthalOrRadialIntegration().equals(NcdSectorIntegrationModel.IntegrationOperationName.azimuthal)) {
			calculateAzimuthal = false; //azimuthal integration, so calculate radial profile
		}
		else {
			calculateAzimuthal = true;
		}
		Dataset[] areaData = ROIProfile.area(areaShape, Dataset.FLOAT32, maskDataset, sectorRoi, !calculateAzimuthal, calculateAzimuthal, false);

		sec.setAreaData(areaData);
		sec.setCalculateRadial(!calculateAzimuthal);
		sec.setCalculateAzimuthal(calculateAzimuthal);
		sec.setROI(sectorRoi);
		
		Dataset[] mydata = sec.process(sliceDataset, 1, maskDataset);
		int resLength = slice.getShape().length - dimension + 1;

		int dataIndex;
		// mydata[0] is azimuthal profile, radial integration. mydata[1] is radial profile, azimuthal integration
		if (model.getAzimuthalOrRadialIntegration().equals(NcdSectorIntegrationModel.IntegrationOperationName.radial)) {
			dataIndex = 0;
		}
		else {
			dataIndex = 1;
		}
		Dataset myraddata = DatasetUtils.cast(mydata[dataIndex], Dataset.FLOAT32);
		Dataset myraderrors = null;
		if (myraddata != null) {
			if (myraddata.hasErrors()) {
				myraderrors = NcdOperationUtils.getErrorBuffer(mydata[dataIndex]);
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
			if (slice.getMetadata(IDiffractionMetadata.class) == null) {
				throw new Exception("Diffraction metadata is required for this operation - add an Import Detector Calibration operation before this sector integration");
			}
			NexusNcdMetadataReader reader = new NexusNcdMetadataReader(model.getFilePath());
			qaxis = calculateQaxisDataset(reader.getQAxisCalibrationFromFile(), getFirstDiffractionMetadata(slice), myraddata.getShape(), (SectorROI)model.getRegion());
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
	private Dataset calculateQaxisDataset(QAxisCalibration cal, IDiffractionMetadata dif, int[] datasetShape, SectorROI intSector) {
		
		Dataset qaxis = null;
		Dataset qaxisErr = null;
		
		if (dif == null) {
			throw new OperationException(this, new Exception("No diffraction metadata available"));
		}
		Amount<ScatteringVectorOverDistance> gradient = null;
		Amount<ScatteringVector> intercept = null;
		Amount<Length> pxSize = Amount.valueOf(dif.getOriginalDetector2DProperties().getHPxSize(), SI.MILLIMETER);
		Unit<ScatteringVector> axisUnit = NonSI.ANGSTROM.inverse().asType(ScatteringVector.class);

		if (cal == null) {
			NcdCalibrationSourceProvider ncdCalibrationSourceProvider;
			try {
				String calibPath = model.getCalibrationPath();
				if (calibPath == null || calibPath.isEmpty()) {
					throw new Exception("No calibration available");
				}
				ncdCalibrationSourceProvider = getSourceProviderAdapter(calibPath).getNcdCalibrationSourceProvider();
			} catch (Exception e) {
				throw new OperationException(this, e);
			}
			HashMap<String, NcdDetectorSettings> detectors = ncdCalibrationSourceProvider.getNcdDetectors();
			for (String detectorName : detectors.keySet()) {
				if (ncdCalibrationSourceProvider.getGradient(detectorName) != null) {
					gradient = ncdCalibrationSourceProvider.getGradient(detectorName);
					intercept = ncdCalibrationSourceProvider.getIntercept(detectorName);
				}
			}
		}
		else {
			gradient = cal.getGradient();
			intercept = cal.getIntercept();
		}
		
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
	
	//from NcdDataReductionTransformer
	private NcdSourceProviderAdapter getSourceProviderAdapter(final String xmlPath) throws Exception {
		
		if (xmlPath == null || xmlPath.isEmpty()) {
			return null;
		}
		final File file = new File(xmlPath);
		FileReader reader=null;
		try {
			reader = new FileReader(file);
			
			JAXBContext jc = JAXBContext.newInstance (NcdSourceProviderAdapter.class);
			Unmarshaller u = jc.createUnmarshaller ();
			
			return (NcdSourceProviderAdapter) u.unmarshal(reader);
			
		} catch (Exception ne) {
			throw new Exception("Cannot export ncd parameters", ne);
		} finally {
			if (reader!=null) {
				try {
					reader.close();
				} catch (IOException e) {
					throw new Exception("Cannot export ncd parameters", e);
				}
		    }
		}
	}
}
