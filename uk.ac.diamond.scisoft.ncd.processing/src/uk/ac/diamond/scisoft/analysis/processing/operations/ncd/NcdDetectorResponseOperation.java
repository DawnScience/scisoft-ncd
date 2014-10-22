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
import org.eclipse.dawnsci.analysis.api.dataset.Slice;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IntegerDataset;
import org.eclipse.dawnsci.hdf5.HierarchicalDataFactory;
import org.eclipse.dawnsci.hdf5.HierarchicalDataUtils;
import org.eclipse.dawnsci.hdf5.IHierarchicalDataFile;

import uk.ac.diamond.scisoft.ncd.core.DetectorResponse;

public class NcdDetectorResponseOperation extends AbstractOperation<NcdDetectorResponseModel, OperationData> {
	
	public Dataset response;

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.ncd.NcdDetectorResponseOperation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.SAME;
	}
	
	@Override
	public OperationData execute(IDataset slice, IMonitor monitor) throws OperationException {
		DetectorResponse response = new DetectorResponse();
		Dataset responseDataset = getResponseFromFile();
		response.setResponse(responseDataset);
		FloatDataset errors = (FloatDataset) slice.getError();
		FloatDataset data = (FloatDataset) slice.getSlice(new Slice());
		int[] flatShape = data.getShape();

		Object[] detData = response.process(data.getData(), errors.getData(), flatShape[0], flatShape);
		OperationData toReturn = new OperationData();
		float[] mydata = (float[]) detData[0];
		double[] myerrors = (double[]) detData[1];

		Dataset myres = new FloatDataset(mydata, slice.getShape());
		myres.setErrorBuffer(new DoubleDataset(myerrors, slice.getShape()));
		toReturn.setData(myres);
		return toReturn;
	}

	private Dataset getResponseFromFile() {
		if (response == null) {
			String filePath = model.getFilePath();
			try {
				
				if (!filePath.isEmpty() && HierarchicalDataFactory.isHDF5(filePath)) {
					IHierarchicalDataFile hiFile = null;
					try {
						hiFile = HierarchicalDataFactory.getReader(filePath);
						ncsa.hdf.object.Dataset dataset = (ncsa.hdf.object.Dataset) hiFile.getData("/entry1/detector/data");
						long[] dataShape = HierarchicalDataUtils.getDims(dataset);
						
						final int[] intShape  = getInt(dataShape);
						response = new IntegerDataset((int[])dataset.read(), intShape);
					} catch (Exception e) {
						throw new OperationException(this, e);
					} finally {
						if (hiFile!= null)
							try {
								hiFile.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
					}
				}
				
				
			} catch (Exception e) {
				throw new OperationException(this, e);
			}
			
		}
		return response;
	}
	
	private int[] getInt(long[] longShape) {
		final int[] intShape  = new int[longShape.length];
		for (int i = 0; i < intShape.length; i++) intShape[i] = (int)longShape[i];
		return intShape;
	}
}