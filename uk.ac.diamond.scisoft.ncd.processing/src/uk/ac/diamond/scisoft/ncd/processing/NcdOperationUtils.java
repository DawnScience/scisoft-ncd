/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.ncd.processing;

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
}