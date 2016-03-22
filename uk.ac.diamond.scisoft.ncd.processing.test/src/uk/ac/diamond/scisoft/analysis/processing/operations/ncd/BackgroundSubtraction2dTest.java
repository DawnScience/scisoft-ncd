/*
 * Copyright (c) 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.io.File;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.ExecutionType;
import org.eclipse.dawnsci.analysis.api.processing.IExecutionVisitor;
import org.eclipse.dawnsci.analysis.api.processing.IOperation;
import org.eclipse.dawnsci.analysis.api.processing.IOperationContext;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.dataset.impl.AggregateDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Comparisons;
import org.eclipse.dawnsci.analysis.dataset.impl.Random;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.eclipse.dawnsci.analysis.dataset.slicer.SourceInformation;
import org.eclipse.dawnsci.hdf.object.HierarchicalDataFactory;
import org.eclipse.dawnsci.hdf.object.IHierarchicalDataFile;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import uk.ac.diamond.scisoft.analysis.processing.Activator;
import uk.ac.diamond.scisoft.analysis.processing.actor.actors.OperationTransformer;
import uk.ac.diamond.scisoft.analysis.processing.operations.backgroundsubtraction.SubtractBlankFrameModel;
import uk.ac.diamond.scisoft.analysis.processing.runner.OperationRunnerImpl;
import uk.ac.diamond.scisoft.analysis.processing.runner.SeriesRunner;

public class BackgroundSubtraction2dTest {
	private static IOperationService service;
	private static ILazyDataset randomDataset;
	private static int datasetNumFrames = 24;
	private static ILazyDataset jakeResultDataset, junResultDataset;
	@Rule
	public static TemporaryFolder folder = new TemporaryFolder();
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
	
		OperationTransformer.setOperationService(service);
		
		int datasetLength = 1000;
		randomDataset = Random.rand(0.0, 1000.0, datasetNumFrames, datasetLength, datasetLength);
		randomDataset.setName("random");
		randomDataset.setError(randomDataset.clone());
		//set this correctly for this file because Jun's background subtraction uses it
		File newFolder = folder.newFolder();
		IHierarchicalDataFile writer = HierarchicalDataFactory.getWriter(newFolder.getAbsolutePath() + "/random.nxs");
		@SuppressWarnings("unused")
		String group1   = writer.group("/entry");
		String group   = writer.group("/entry/result");
		writer.createDataset("data", (IDataset) randomDataset, group);
		writer.createDataset("errors", (IDataset) randomDataset.getError(), group);
		writer.close();
		SourceInformation si = new SourceInformation(writer.getPath(), randomDataset.getName(), randomDataset);
		randomDataset.setMetadata(new SliceFromSeriesMetadata(si));
		
		setupBgSubtractionJake();
		setupBgSubtractionJun();
	}

	private volatile static int counter;
	@SuppressWarnings("unchecked")
	public static void setupBgSubtractionJake() throws Exception {
		final IOperation bgSubtractionJake = service.create("uk.ac.diamond.scisoft.analysis.processing.operations.backgroundsubtraction.SubtractBlankFrameOperation");
		final IOperationContext context = service.createContext();
		SubtractBlankFrameModel model = new SubtractBlankFrameModel();
		model.setStartFrame(0);
		model.setEndFrame(1);
		bgSubtractionJake.setModel(model);

		context.setData(randomDataset);
//		context.setSlicing("all");
		context.setDataDimensions(new int[]{1,2});
		context.setSeries(bgSubtractionJake);
		counter = 0;
		
		final ILazyDataset[] datasets = new ILazyDataset[datasetNumFrames];
		context.setVisitor(new IExecutionVisitor.Stub() {
			@Override
			public void executed(OperationData result, IMonitor monitor) throws Exception {
				datasets[counter] = result.getData();
				counter++;
				if (counter == datasetNumFrames) {
					jakeResultDataset = new AggregateDataset(false, datasets);
				}
			}
		});
		context.setExecutionType(ExecutionType.SERIES);
		service.execute(context);
	}

	@SuppressWarnings("unchecked")
	public static void setupBgSubtractionJun() throws Exception {
		final IOperation bgSubtractionJun = service.create("uk.ac.diamond.scisoft.analysis.processing.NcdBackgroundSubtractionFromData");
		NcdBackgroundSubtractionFromDataModel model = new NcdBackgroundSubtractionFromDataModel();
		model.setImageSelectionString("0");
		bgSubtractionJun.setModel(model);
		final IOperationContext context = service.createContext();

		context.setData(randomDataset);
//		context.setSlicing("all");
		context.setDataDimensions(new int[]{1,2});
		context.setSeries(bgSubtractionJun);
		counter = 0;
		
		final ILazyDataset[] datasets = new ILazyDataset[datasetNumFrames];
		context.setVisitor(new IExecutionVisitor.Stub() {
			@Override
			public void executed(OperationData result, IMonitor monitor) throws Exception {
				datasets[counter] = result.getData();
				counter++;
				if (counter == datasetNumFrames) {
					junResultDataset = new AggregateDataset(false, datasets);
				}
			}
		});
		context.setExecutionType(ExecutionType.SERIES);
		service.execute(context);
	}
	
	@Test
	public void testJunJakeDatasets() throws Exception {
		if (junResultDataset == null || jakeResultDataset == null) {
			throw new Exception("datasets should not be null");
		}
		if (junResultDataset.getSize() != jakeResultDataset.getSize()) {
			throw new Exception("datasets should be the same size");
		}
		if (!Comparisons.allCloseTo(junResultDataset.getSliceView(), jakeResultDataset.getSliceView(), 0.0001, 0.1)) {
			throw new Exception("the values of the two datasets are not close to one another");
		}
		if (!Comparisons.allCloseTo(junResultDataset.getError(), jakeResultDataset.getError(), 0.0001, 0.1)) {
			throw new Exception("the values of the errors of the two datasets are not close to one another");
		}
	}
}
