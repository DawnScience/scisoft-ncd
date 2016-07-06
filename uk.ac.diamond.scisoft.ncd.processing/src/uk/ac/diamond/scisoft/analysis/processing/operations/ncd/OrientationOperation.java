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

import org.eclipse.dawnsci.analysis.api.processing.Atomic;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.processing.model.EmptyModel;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.metadata.AxesMetadata;

import uk.ac.diamond.scisoft.ncd.core.DegreeOfOrientation;

@Atomic
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
		Dataset data = DatasetUtils.convertToDataset(slice);
		
		Dataset inputAxis = null;
		try {
			List<AxesMetadata> axes = slice.getMetadata(AxesMetadata.class);
			if (axes != null) {
				inputAxis = DatasetUtils.sliceAndConvertLazyDataset(axes.get(0).getAxes()[0]); //assume q is first axis
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

		Dataset resultData = DatasetFactory.createFromObject(mydata, 1);
		resultData.setName("Data");
		Dataset resultAngle = DatasetFactory.createFromObject(myangle, 1);
		resultAngle.setName("Angle");
		Dataset resultVector = DatasetFactory.createFromObject(myvector, 1);
		resultVector.setName("Vector");
		return new OperationData(slice, new Serializable[]{resultData, resultAngle, resultVector});
	}
}
