/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.OperationRank;

/**
 * Run the NCD background subtraction code. This operation uses sector integrated datasets
 * @author rbv51579
 *
 */
public class NcdBackgroundSubtractionIntegratedOperation extends AbstractNcdBackgroundSubtractionOperation<NcdBackgroundSubtractionModel> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.NcdBackgroundSubtractionIntegratedOperation";
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
	public String getDataPath() {
		return "/entry/result/data";
	}
}