/*
 * Copyright 2011 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.ncd;

import java.io.Serializable;
import java.util.Arrays;

public class Average {

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
		
		// first dim is timeframe
		int firstdim = dimensions[0];
		int[] imagedim = Arrays.copyOfRange(dimensions, 1, dimensions.length);
		int imagesize = 1;
		for(int n: imagedim) imagesize *= n;
		
		float[] mydata = new float[imagesize];

		for (int i = 0; i < parentdata.length; i++) {
			mydata[i%imagesize] += parentdata[i]/firstdim;
		}	
		
		return mydata;
	}

}