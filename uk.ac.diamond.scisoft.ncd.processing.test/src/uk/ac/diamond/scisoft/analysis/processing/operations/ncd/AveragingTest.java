package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.processing.ExecutionType;
import org.eclipse.dawnsci.analysis.api.processing.IOperation;
import org.eclipse.dawnsci.analysis.api.processing.IOperationContext;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.dataset.impl.Random;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.eclipse.dawnsci.analysis.dataset.slicer.SourceInformation;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.processing.Activator;
import uk.ac.diamond.scisoft.analysis.processing.actor.actors.OperationTransformer;
import uk.ac.diamond.scisoft.analysis.processing.runner.OperationRunnerImpl;
import uk.ac.diamond.scisoft.analysis.processing.runner.SeriesRunner;

public class AveragingTest {
	private static IOperationService service;
	private static ILazyDataset randomDataset;
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
		service.createOperations(service.getClass().getClassLoader(), "uk.ac.diamond.scisoft.analysis.processing.operations.ncd");

		OperationRunnerImpl.setRunner(ExecutionType.SERIES,   new SeriesRunner());
	
		OperationTransformer.setOperationService(service);
		
		int datasetLength = 1000;
		randomDataset = Random.rand(0.0, 1000.0, 24, datasetLength);
		randomDataset.setName("random");
		randomDataset.setError(randomDataset.clone());
		SourceInformation si = new SourceInformation("filepath", randomDataset.getName(), randomDataset);
		randomDataset.setMetadata(new SliceFromSeriesMetadata(si));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAveragingJake() throws Exception {
		//compare my averaging with no Rg filtering with Jake's
		final IOperation averageJake = service.create("uk.ac.diamond.scisoft.analysis.processing.operations.AveragingOperation");
		final IOperationContext context = service.createContext();

		context.setData(randomDataset);
		context.setSlicing("all"); // All 24 images in first dimension.
		context.setSeries(averageJake);
		context.setExecutionType(ExecutionType.SERIES);
		service.execute(context);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testAveragingJun() throws Exception {
		//compare my averaging with no Rg filtering with Jake's
		final IOperation averageJun = service.create("uk.ac.diamond.scisoft.analysis.processing.NcdAveraging");
		averageJun.setModel(new NcdAveragingModel());
		final IOperationContext context = service.createContext();

		context.setData(randomDataset);
		context.setSlicing("all"); // All 24 images in first dimension.
		context.setSeries(averageJun);
		context.setExecutionType(ExecutionType.SERIES);
		service.execute(context);
	}
}
