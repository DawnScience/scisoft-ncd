/*
 * Copyright (c) 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.dawb.common.services.ServiceManager;
import org.eclipse.dawnsci.analysis.api.conversion.IConversionContext;
import org.eclipse.dawnsci.analysis.api.conversion.IConversionService;
import org.eclipse.dawnsci.analysis.api.conversion.IConversionContext.ConversionScheme;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.IExportOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.AggregateDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;

@SuppressWarnings("deprecation")
public class ExportNcd1dOperation extends AbstractOperation<ExportNcd1dModel, OperationData> implements IExportOperation {
	private Dataset[] sliceData;
	private int counter;
	
	@Override
	public OperationRank getInputRank() {
		return OperationRank.ONE;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.ExportNcd1d";
	}

	@Override
	public void init() {
		sliceData = null;
		counter = 0;
	}

	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		if (model.getOutputDirectoryPath() == null) throw new OperationException(this, "Output directory not set!");
		SliceFromSeriesMetadata ssm = getSliceSeriesMetadata(input);
		
		if (model.getOutputDirectoryPath() == null || model.getOutputDirectoryPath().isEmpty()) {
			throw new OperationException(this, "output directory cannot be empty");
		}
		
		if (sliceData == null) {
			sliceData = new Dataset[ssm.getTotalSlices()];
			counter = 0;
		}
		
		//first accumulate all data
		sliceData[counter] = (Dataset) input;
		counter++;
				
		if (counter == ssm.getTotalSlices()) {
			IConversionService service = null;
			try {
				service = (IConversionService)ServiceManager.getService(IConversionService.class, true);
			} catch (Exception e1) {
				throw new OperationException(this, e1);
			}

			IConversionContext context = service.open("/dls/path_to_some_hdf5_file.nxs"); //a dummy file to create context - TODO provide file-free way to create context!
			AggregateDataset ag = new AggregateDataset(true, sliceData);
			context.setLazyDataset(ag);
			context.setOutputPath(model.getOutputDirectoryPath());
			context.setConversionScheme(ConversionScheme.CUSTOM_NCD);
			context.setUserObject(model.getExportFormat());
			String filePath = ssm.getFilePath();
			filePath = filePath.replace('\\', '/');
			filePath = filePath.substring(filePath.lastIndexOf('/'));
			String outputPath = filePath.substring(1, filePath.lastIndexOf('.'))+"_processed";
			context.setDatasetName(outputPath);

			try {
				service.process(context);
			} catch (Exception e) {
				throw new OperationException(this, e);
			}
		}
		return new OperationData(input);

	}
}
