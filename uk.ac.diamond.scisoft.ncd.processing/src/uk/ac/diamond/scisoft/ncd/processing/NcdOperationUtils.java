/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.ncd.processing;

import java.util.List;

import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;

public class NcdOperationUtils {
	/**
	 * Add a dimension of 1 to account for slicing of original data in the Processing pipeline
	 * @param set
	 * @return
	 */
	public static int[] addDimension(int[] set) {
		int[] dataShape = new int[set.length + 1];
		dataShape[0] = 1;
		int index = 1;
		for (int dimension: set) {
			dataShape[index++] = dimension;
		}
		return dataShape;
	}
	
	/**
	 * Check existing paths list, then /entry/result/data in case this is a file from created from the Processing pipeline.
	 * @param fileToRead
	 * @return
	 * @throws Exception
	 */
	public static ILazyDataset getDataset(String fileToRead, List<String> dataPathsToTry) throws Exception {
		dataPathsToTry.add("/entry/result/data");
		ILazyDataset toReturn = null;
		for (String location : dataPathsToTry) {
			IDataHolder holder = LoaderFactory.getData(fileToRead);
			toReturn = holder.getLazyDataset(location);
			if (toReturn != null) {
				break;
			}
		}
		return toReturn;
	}
}