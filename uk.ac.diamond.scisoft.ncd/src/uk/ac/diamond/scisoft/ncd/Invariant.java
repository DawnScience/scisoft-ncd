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

import org.apache.commons.beanutils.ConvertUtils;

public class Invariant {

	public Object[] process(Serializable buffer, Serializable errors, final int[] dimensions) {
		
		float[] parentdata = (float[]) ConvertUtils.convert(buffer, float[].class);
		float[] parenterrors = (float[]) ConvertUtils.convert(errors, float[].class);
		float[] mydata = new float[dimensions[0]];
		float[] myerrors = new float[dimensions[0]];
		
		// first dim is timeframe
		int[] imagedim = Arrays.copyOfRange(dimensions, 1, dimensions.length);
		int imagesize = 1;
		for(int n: imagedim) imagesize *= n;
		for (int i = 0; i < parentdata.length; i++) {
			mydata[i/imagesize] += parentdata[i];
			myerrors[i/imagesize] += parenterrors[i];
		}
		
		return new Object[] {mydata, myerrors};
	}
}
