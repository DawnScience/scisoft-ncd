package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.processing.ExecutionType;
import org.eclipse.dawnsci.analysis.api.processing.IOperation;
import org.eclipse.dawnsci.analysis.api.processing.IOperationContext;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.impl.BooleanDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Random;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.processing.Activator;
import uk.ac.diamond.scisoft.analysis.processing.actor.actors.OperationTransformer;
import uk.ac.diamond.scisoft.analysis.processing.actor.runner.GraphRunner;
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
		
		OperationRunnerImpl.setRunner(ExecutionType.SERIES,   new SeriesRunner());
	
		OperationTransformer.setOperationService(service);
		
		randomDataset = Random.rand(0.0, 1000.0, 24, 1000, 1000);

	}

	@Test
	public void testAveragingJake() throws Exception {
		//compare my averaging with no Rg filtering with Jake's
		final IOperation averageJake = service.create("uk.ac.diamond.scisoft.analysis.processing.averagingOperation");
		final IOperationContext context = service.createContext();
		context.setData(randomDataset);
		context.setSlicing("all"); // All 24 images in first dimension.
		context.setSeries(averageJake);
		service.execute(context);
	}

	@Test
	public void testAveragingJun() throws Exception {
		//compare my averaging with no Rg filtering with Jake's
		final IOperation averageJun = service.create("uk.ac.diamond.scisoft.analysis.processing.NcdAveraging");
		averageJun.setModel(new NcdAveragingModel());
		final IOperationContext context = service.createContext();
		context.setData(randomDataset);
		context.setSlicing("all"); // All 24 images in first dimension.
		context.setSeries(averageJun);
		service.execute(context);
	}
}
