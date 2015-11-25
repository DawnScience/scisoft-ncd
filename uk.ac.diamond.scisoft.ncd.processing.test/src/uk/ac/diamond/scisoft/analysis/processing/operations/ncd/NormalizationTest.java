/*
 * Copyright (c) 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import junit.framework.Assert;

import org.eclipse.core.runtime.Path;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.SliceND;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.ExecutionType;
import org.eclipse.dawnsci.analysis.api.processing.IExecutionVisitor;
import org.eclipse.dawnsci.analysis.api.processing.IOperation;
import org.eclipse.dawnsci.analysis.api.processing.IOperationContext;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.eclipse.dawnsci.analysis.dataset.slicer.SourceInformation;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.TestUtils;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.analysis.processing.Activator;
import uk.ac.diamond.scisoft.analysis.processing.actor.actors.OperationTransformer;
import uk.ac.diamond.scisoft.analysis.processing.operations.mask.ImportMaskModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.powder.AzimuthalPixelIntegrationModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.twod.DiffractionMetadataImportModel;
import uk.ac.diamond.scisoft.analysis.processing.runner.OperationRunnerImpl;
import uk.ac.diamond.scisoft.analysis.processing.runner.SeriesRunner;

@SuppressWarnings("deprecation")
public class NormalizationTest {
	private static IOperationService service;
	private static Path inputPath;
	private static IDataset inputDataset;

	/**
	 * Manually creates the service so that no extension points have to be read.
	 * 
	 * We do this use annotations
	 * @throws Exception 
	 */
	@BeforeClass
	public static void before() throws Exception {
		String testFileFolder = TestUtils.getGDALargeTestFilesLocation();
		if( testFileFolder == null){
			Assert.fail("TestUtils.getGDALargeTestFilesLocation() returned null - test aborted");
		}
		
		inputPath = new Path(testFileFolder + "/NCDReductionTest/i22-196083.nxs");
		inputDataset = LoaderFactory.getDataSet(inputPath.toOSString(), "/entry1/detector/data", null);
		SourceInformation si = new SourceInformation(inputPath.toOSString(), inputDataset.getName(), inputDataset);
		inputDataset.setMetadata(new SliceFromSeriesMetadata(si));
		
		service = (IOperationService)Activator.getService(IOperationService.class);
		service.createOperations(service.getClass().getClassLoader(), "uk.ac.diamond.scisoft.analysis.processing.operations");

		OperationRunnerImpl.setRunner(ExecutionType.SERIES,   new SeriesRunner());
	
		OperationTransformer.setOperationService(service);
		
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	public void setupAndRunPipeline() throws Exception {
		//Import Detector Calibration, Import Mask from File, Set Poisson Errors, Azimuthal Integration, Normalisation for NCD
		final IOperation detCalib = service.create("uk.ac.diamond.scisoft.analysis.processing.operations.DiffractionMetadataImportOperation");
		final IOperation importMask = service.create("uk.ac.diamond.scisoft.analysis.processing.operations.ImportMaskOperation");
		final IOperation setPoisson = service.create("uk.ac.diamond.scisoft.analysis.processing.operations.PhotonCountingErrorOperation");
		final IOperation integration = service.create("uk.ac.diamond.scisoft.analysis.processing.operations.powder.AzimuthalPixelIntegrationOperation");
		final IOperation normalization = service.create("uk.ac.diamond.scisoft.analysis.processing.Normalisation");
		
		DiffractionMetadataImportModel detCalibModel = new DiffractionMetadataImportModel();
		detCalibModel.setFilePath(inputPath.toOSString());
		detCalib.setModel(detCalibModel);
		ImportMaskModel importMaskModel = new ImportMaskModel();
		String maskPath = org.dawb.common.util.test.TestUtils.getAbsolutePath("uk.ac.diamond.scisoft.ncd.processing.test", "data/mask_9m.nxs");
		importMaskModel.setFilePath(maskPath);
		importMask.setModel(importMaskModel);
		integration.setModel(new AzimuthalPixelIntegrationModel());
		NormalisationModel normalizationModel = new NormalisationModel();
		normalizationModel.setUseScaleValueFromOriginalFile(false);
		normalizationModel.setThicknessFromFileIsDefault(false);
		normalization.setModel(normalizationModel);

		final IOperationContext context = service.createContext();

		context.setData(inputDataset);
//		context.setSlicing("all", "0");
		context.setDataDimensions(new int[]{2,3});
		context.setSlicing(new SliceND(inputDataset.getShape()));
		context.getSlicing().setSlice(1, 0, 1, 1);
		context.setSeries(detCalib, importMask, setPoisson, integration, normalization);
		
		context.setVisitor(new IExecutionVisitor.Stub() {
			@Override
			public void executed(OperationData result, IMonitor monitor) throws Exception {
				result.getData();
			}
		});
		context.setExecutionType(ExecutionType.SERIES);
		service.execute(context);
	}
}
