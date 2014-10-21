/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.metadata.MaskMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;

import uk.ac.diamond.scisoft.analysis.metadata.MaskMetadataImpl;
import uk.ac.diamond.scisoft.analysis.processing.io.NexusNcdMetadataReader;

public class NcdMetadataImportOperation extends AbstractOperation<NcdMetadataImportModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.NcdMetadataImport";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.TWO;
	}
	
	@Override
	public OperationData execute(IDataset slice, IMonitor monitor) throws OperationException {

		NexusNcdMetadataReader reader = new NexusNcdMetadataReader(model.getFilePath());
		IDataset mask;
		try {
			mask = reader.getMaskFromFile();
		} catch (Exception e) {
			throw new OperationException(this, e);
		}

		MaskMetadata mm = new MaskMetadataImpl(mask);
		slice.setMetadata(mm);

		SectorROI roi = new SectorROI();
		model.setRegion(roi);
		return new OperationData(slice);
	}
}