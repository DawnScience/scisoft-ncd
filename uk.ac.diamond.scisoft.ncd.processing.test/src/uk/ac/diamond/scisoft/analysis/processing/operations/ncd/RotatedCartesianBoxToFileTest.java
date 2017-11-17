/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.io.File;

import org.eclipse.dawnsci.analysis.api.processing.IOperation;
import org.eclipse.dawnsci.analysis.api.processing.IOperationContext;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;
import org.eclipse.dawnsci.hdf.object.operation.HierarchicalFileExecutionVisitor;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.dataset.Random;
import org.eclipse.january.dataset.ShortDataset;
import org.eclipse.january.metadata.AxesMetadata;
import org.eclipse.january.metadata.MetadataFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.processing.Activator;
import uk.ac.diamond.scisoft.analysis.processing.operations.RotatedCartesianBox;
import uk.ac.diamond.scisoft.analysis.processing.operations.RotatedCartesianBoxModel;

public class RotatedCartesianBoxToFileTest {
	
private static IOperationService service;
	
	/**
	 * Manually creates the service so that no extension points have to be read.
	 * 
	 * We do this use annotations
	 * @throws Exception 
	 */
	@BeforeClass
	public static void before() throws Exception {
		service = (IOperationService)Activator.getService(IOperationService.class);
		
		// Just read all these operations.
		service.createOperations(service.getClass().getClassLoader(), "uk.ac.diamond.scisoft.analysis.processing.operations.ncd");
	}
	
	@Test
	public void testIntegration() throws Exception {
		int[] dsShape = new int[]{100, 100, 100};
		ILazyDataset lz = Random.lazyRand("test", dsShape);

		final IOperationContext context = service.createContext();
		context.setData(lz);
//		context.setSlicing(sliceMap); //
		context.setDataDimensions(new int[]{1,2});
		
		final IDataset axDataset1 = DatasetFactory.createRange(ShortDataset.class, 100);
		axDataset1.setShape(new int[] {100,1,1});
		axDataset1.setName("z");
		
		final IDataset axDataset2 = DatasetFactory.createRange(IntegerDataset.class, 100);
		axDataset2.setShape(new int[] {1,100,1});
		axDataset2.setName("y");
		
		final IDataset axDataset3 = DatasetFactory.createRange(IntegerDataset.class, 100);
		axDataset3.setShape(new int[] {1,1,100});
		axDataset3.setName("x");
		
		AxesMetadata am = MetadataFactory.createMetadata(AxesMetadata.class, 3);
		am.addAxis(0, axDataset1);
		am.addAxis(1, axDataset2);
		am.addAxis(2, axDataset3);
		
		lz.addMetadata(am);
		
		//pixel integration
		final IOperation rotatedCartesianBox = new RotatedCartesianBox();
		RotatedCartesianBoxModel parameters = new RotatedCartesianBoxModel();
		parameters.setRoi(new RectangularROI(30,30,40,40,0.0));
		rotatedCartesianBox.setModel(parameters);
		
		try {

			final File tmp = File.createTempFile("Test", ".h5");
			tmp.deleteOnExit();
			tmp.createNewFile();
			
			long time =  System.currentTimeMillis();
			
			context.setSeries(rotatedCartesianBox);
			context.setVisitor(new HierarchicalFileExecutionVisitor(tmp.getAbsolutePath()));
			
			service.execute(context);
			
			System.out.println( System.currentTimeMillis()  - time);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("debug");
			
	}

}
