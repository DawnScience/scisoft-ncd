/*
 * Copyright (c) 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.dawb.common.util.test.TestUtils;
import org.eclipse.dawnsci.analysis.api.processing.ExecutionType;
import org.eclipse.dawnsci.analysis.api.processing.IExecutionVisitor;
import org.eclipse.dawnsci.analysis.api.processing.IOperation;
import org.eclipse.dawnsci.analysis.api.processing.IOperationContext;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.eclipse.dawnsci.analysis.dataset.slicer.SourceInformation;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.ILazyDataset;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.analysis.processing.Activator;
import uk.ac.diamond.scisoft.analysis.processing.operations.mask.ImportMaskModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.powder.AzimuthalPixelIntegrationModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.twod.DiffractionMetadataImportModel;
import uk.ac.diamond.scisoft.analysis.processing.runner.OperationRunnerImpl;
import uk.ac.diamond.scisoft.analysis.processing.runner.SeriesRunner;

public class SingleImageDatasetTest {
	private static IOperationService service;
	private static ILazyDataset inputDataset, resultDataset;
	private static String rawPath;
	private static String maskPath;
	
	/**
	 * Manually creates the service so that no extension points have to be read.
	 * 
	 * We do this use annotations
	 * @throws Exception 
	 */
	@BeforeClass
	public static void before() throws Exception {
		service = (IOperationService)Activator.getService(IOperationService.class);
		service.createOperations(service.getClass().getClassLoader(), "uk.ac.diamond.scisoft.analysis.processing.operations");

		OperationRunnerImpl.setRunner(ExecutionType.SERIES,   new SeriesRunner());
		
		setupFiles();
		
		inputDataset = LoaderFactory.getDataSet(rawPath, "/entry1/detector/data", null);
		SourceInformation si = new SourceInformation(rawPath, inputDataset.getName(), inputDataset);
		inputDataset.setMetadata(new SliceFromSeriesMetadata(si));
		setupPipeline();
	}
	
	public static void setupFiles() throws Exception {
		rawPath  = TestUtils.getAbsolutePath("uk.ac.diamond.scisoft.ncd.processing.test", "data/i22-217330.nxs");
		maskPath  = TestUtils.getAbsolutePath("uk.ac.diamond.scisoft.ncd.processing.test", "data/mask_9m.nxs");
	}
	
	@SuppressWarnings("unchecked")
	public static void setupPipeline() throws Exception {
		//Import Detector Calibration, Import Mask from File, Set Poisson Errors, Azimuthal Integration, Normalisation for NCD
		final IOperation detCalib = service.create("uk.ac.diamond.scisoft.analysis.processing.operations.DiffractionMetadataImportOperation");
		final IOperation importMask = service.create("uk.ac.diamond.scisoft.analysis.processing.operations.ImportMaskOperation");
		final IOperation setPoisson = service.create("uk.ac.diamond.scisoft.analysis.processing.operations.PhotonCountingErrorOperation");
		final IOperation integration = service.create("uk.ac.diamond.scisoft.analysis.processing.operations.powder.AzimuthalPixelIntegrationOperation");
		final IOperation normalization = service.create("uk.ac.diamond.scisoft.analysis.processing.Normalisation");
		
		DiffractionMetadataImportModel detCalibModel = new DiffractionMetadataImportModel();
		detCalibModel.setFilePath(rawPath);
		detCalib.setModel(detCalibModel);
		ImportMaskModel importMaskModel = new ImportMaskModel();
		importMaskModel.setFilePath(maskPath);
		importMask.setModel(importMaskModel);
		integration.setModel(new AzimuthalPixelIntegrationModel());
		NormalisationModel normalizationModel = new NormalisationModel();
		normalizationModel.setUseScaleValueFromOriginalFile(false);
		normalization.setModel(normalizationModel);

		final IOperationContext context = service.createContext();

		context.setData(inputDataset);
//		context.setSlicing("all");
		context.setDataDimensions(new int[]{1,2});
		context.setSeries(detCalib, importMask, setPoisson, integration, normalization);
		
		context.setVisitor(new IExecutionVisitor.Stub() {
			@Override
			public void executed(OperationData result, IMonitor monitor) throws Exception {
				resultDataset = result.getData();
			}
		});
		context.setExecutionType(ExecutionType.SERIES);
		service.execute(context);
	}
	
	@Test
	public void testSingleImageDatasets() throws Exception {
		if (resultDataset ==  null) {
			throw new Exception("Pipeline should have succeeded with a single image");
		}
	}
}
