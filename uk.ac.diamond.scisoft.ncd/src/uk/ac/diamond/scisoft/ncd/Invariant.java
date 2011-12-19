/*
 * Copyright © 2011 Diamond Light Source Ltd.
 * Contact :  ScientificSoftware@diamond.ac.uk
 * 
 * This is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 * 
 * This software is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this software. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd;

import java.io.Serializable;
import java.util.Arrays;

public class Invariant {

	public float[] process(Serializable buffer, final int[] dimensions) {
		float[] parentdata;

		if (buffer instanceof float[]) {
			parentdata = (float[]) buffer;
		} else {
			double[] farr = (double[]) buffer;
			parentdata = new float[farr.length];
			for (int i = 0; i < farr.length; i++) {
				parentdata[i] = new Float(farr[i]);
			}
		}	
		
		float[] mydata = new float[dimensions[0]];
		
		// first dim is timeframe
		int[] imagedim = Arrays.copyOfRange(dimensions, 1, dimensions.length);
		int imagesize = 1;
		for(int n: imagedim) imagesize *= n;
		for (int i = 0; i < parentdata.length; i++) {
			mydata[i/imagesize] += parentdata[i];
		}
		
		return mydata;
	}
}
