/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.io.Serializable;
import java.util.List;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.metadata.AxesMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;

import uk.ac.diamond.scisoft.analysis.processing.operations.EmptyModel;
import uk.ac.diamond.scisoft.ncd.core.DegreeOfOrientation;

public class OrientationOperation extends AbstractOperation<EmptyModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.Orientation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ONE;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}

	@Override
	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		Dataset data = (Dataset) slice;
		
		Dataset inputAxis = null;
		try {
			List<AxesMetadata> axes = slice.getMetadata(AxesMetadata.class);
			if (axes != null) {
				inputAxis = (Dataset) axes.get(0).getAxes()[0].getSlice(); //assume q is first axis
			}
		} catch (Exception e) {
			throw new OperationException(this, e);
		}
		
		Object[] myobj;
		Dataset axis = null;

		axis = inputAxis.clone().squeeze();
		DegreeOfOrientation degree = new DegreeOfOrientation();
		myobj = degree.process(data.getBuffer(), axis.getBuffer(), data.getShape());
		
		float[] mydata = (float[]) myobj[0];
		float[] myangle = (float[]) myobj[1];
		float[] myvector = (float[]) myobj[2];

		Dataset resultData = new FloatDataset(mydata, new int[]{1});
		resultData.setName("Data");
		Dataset resultAngle = new FloatDataset(myangle, new int[]{1});
		resultAngle.setName("Angle");
		Dataset resultVector = new FloatDataset(myvector, new int[]{2});
		resultVector.setName("Vector");
		return new OperationData(slice, new Serializable[]{resultData, resultAngle, resultVector});
	}
}
