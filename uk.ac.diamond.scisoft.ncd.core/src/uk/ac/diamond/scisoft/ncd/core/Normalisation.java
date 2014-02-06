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

package uk.ac.diamond.scisoft.ncd.core;

import java.io.Serializable;

import org.apache.commons.beanutils.ConvertUtils;

public class Normalisation {


	private double normvalue = 1;
	private int calibChannel = 1;

	public double getNormvalue() {
		return normvalue;
	}

	public void setNormvalue(double normvalue) {
		this.normvalue = normvalue;
	}

	public int getCalibChannel() {
		return calibChannel;
	}

	public void setCalibChannel(int calibChannel) {
		this.calibChannel = calibChannel;
	}

	
	public Object[] process(Serializable buffer, Serializable errors, Serializable cbuffer, int frames, final int[] dimensions, final int[] cdimensions) {

		float[] parentdata = (float[]) ConvertUtils.convert(buffer, float[].class);
		double[] parenterrors = (double[]) ConvertUtils.convert(errors, double[].class);
		
		float[] calibdata = (float[]) cbuffer;
		float[] mydata = new float[parentdata.length];
		double[] myerrors = new double[parenterrors.length];

		int calibTotalChannels = cdimensions[1];
		int parentDataLength = 1;
		for (int i = 1; i < dimensions.length; i++) {
			parentDataLength *= dimensions[i];
		}
		
		for (int i = 0; i < frames; i++) {
			float calReading = calibdata[i * calibTotalChannels + calibChannel];
			if (calReading == 0) {
				calReading = 1; //TODO better idea?
			}
			for (int j = i * parentDataLength; j < (i + 1) * parentDataLength; j++) {
				mydata[j] = (float) ((normvalue / calReading) * parentdata[j]);
				myerrors[j] = (normvalue / calReading) * (normvalue / calReading) * parenterrors[j];
			}
		}

		return new Object[] {mydata, myerrors};
	}
	
	@Deprecated
	public float[] process(Serializable buffer, Serializable cbuffer, int frames, final int[] dimensions, final int[] cdimensions) {
		return (float[]) process(buffer, buffer, cbuffer, frames, dimensions, cdimensions)[0];
	}
}
