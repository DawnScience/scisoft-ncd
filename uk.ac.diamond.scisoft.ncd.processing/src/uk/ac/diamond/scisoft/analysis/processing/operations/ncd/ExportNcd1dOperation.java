/*
 * Copyright (c) 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.ArrayList;
import java.util.List;

import org.dawb.common.services.ServiceManager;
import org.eclipse.dawnsci.analysis.api.conversion.IConversionContext;
import org.eclipse.dawnsci.analysis.api.conversion.IConversionContext.ConversionScheme;
import org.eclipse.dawnsci.analysis.api.conversion.IConversionService;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.metadata.AxesMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.IExportOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.metadata.AxesMetadataImpl;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;

import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

@SuppressWarnings("deprecation")
public class ExportNcd1dOperation extends AbstractOperation<ExportNcd1dModel, OperationData> implements IExportOperation {
	private List<Dataset> sliceData;
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
			sliceData = new ArrayList<Dataset>(ssm.getTotalSlices());
			counter = 0;
		}
		
		//first accumulate all data
		sliceData.add(counter, DatasetUtils.convertToDataset(input.clone()).squeeze());
		counter++;
				
		if (counter == ssm.getTotalSlices()) {
			IConversionService service = null;
			try {
				service = (IConversionService)ServiceManager.getService(IConversionService.class, true);
			} catch (Exception e1) {
				throw new OperationException(this, e1);
			}

			IConversionContext context = service.open("/dls/path_to_some_hdf5_file.nxs"); //a dummy file to create context - TODO provide file-free way to create context!
			Dataset ag = null;
			try {
				ag = NcdOperationUtils.convertListOfDatasetsToDataset(sliceData);
				
				//copy errors from input datasets
				List<Dataset> errorDatasets = new ArrayList<Dataset>(ssm.getTotalSlices());
				for (int i=0; i < ssm.getTotalSlices(); ++i) {
					errorDatasets.add(i, sliceData.get(i).getError());
				}
				Dataset errorDataset = NcdOperationUtils.convertListOfDatasetsToDataset(errorDatasets);
				ag.setError(errorDataset);
				
				//now set other metadata
				ILazyDataset qAxis = sliceData.get(0).getMetadata(AxesMetadata.class).get(0).getAxis(0)[0];
				AxesMetadataImpl axes = new AxesMetadataImpl(2);
				axes.setAxis(1, qAxis);
				ag.setMetadata(axes);
			} catch (Exception e1) {
				throw new OperationException(this, "exception while converting datasets into an aggregate: " + e1);
			}
			
			context.setLazyDataset(ag);
			context.setOutputPath(model.getOutputDirectoryPath());
			context.setConversionScheme(ConversionScheme.CUSTOM_NCD);
			context.setUserObject(model.getExportFormat());
			String filePath = ssm.getFilePath();
			filePath = filePath.replace('\\', '/');
			filePath = filePath.substring(filePath.lastIndexOf('/'));
			String outputPath = filePath.substring(1, filePath.lastIndexOf('.'))+"_export";
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
