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

import org.apache.commons.beanutils.ConvertUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.metadata.MaskMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;

import uk.ac.diamond.scisoft.analysis.processing.io.NexusNcdMetadataReader;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.ncd.core.SectorIntegration;

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

		model.setRegion(roi);
		
		SectorIntegration sec = new SectorIntegration();
		int[] frames = slice.getShape();
		int dimension = 0;
		int[] areaShape = (int[]) ConvertUtils.convert(
				Arrays.copyOfRange(frames, frames.length - dimension, frames.length), int[].class);
		List<MaskMetadata> mask;
		try {
			mask = slice.getMetadata(MaskMetadata.class);
		} catch (Exception e) {
			throw new OperationException(this, e);
		}
		Dataset[] areaData = ROIProfile.area(areaShape, Dataset.FLOAT32, (Dataset) mask.get(0).getMask().getSlice(), (SectorROI) model.getRegion(), true, false, false);

		sec.setAreaData(areaData);
		sec.setCalculateRadial(true);
		sec.setROI((SectorROI)model.getRegion());
		
		Dataset[] mydata = sec.process((Dataset)slice.getSlice(), slice.getShape()[0], (Dataset)mask.get(0).getMask().getSlice());
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

		OperationData toReturn = new OperationData();
		Dataset myres = new FloatDataset(myraddata);
		myres.setErrorBuffer(new DoubleDataset(myraderrors));
		toReturn.setData(myres);
		return toReturn;
	}
}