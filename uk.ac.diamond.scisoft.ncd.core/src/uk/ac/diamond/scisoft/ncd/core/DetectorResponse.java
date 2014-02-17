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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;

public class DetectorResponse {

	private static final Logger logger = LoggerFactory.getLogger(DetectorResponse.class);

	private FloatDataset response;

	public FloatDataset getResponse() {
		return response;
	}

	public void setResponse(AbstractDataset response) {
		this.response = (FloatDataset) response.cast(AbstractDataset.FLOAT32).squeeze();
	}

	
	public Object[] process(Serializable buffer, Serializable error, int frames, final int[] dimensions) {
		
		float[] parentdata = (float[]) ConvertUtils.convert(buffer, float[].class);
		float[] parenterror = (float[]) ConvertUtils.convert(error, float[].class);
		
		float[] mydata = new float[parentdata.length];
		double[] myerror = new double[parenterror.length];
		
		float[] responseBuffer = response.getData();

		int dataLength = 1;
		for (int i = 1; i < dimensions.length; i++) {
			if (dimensions[i] != response.getShape()[i - 1]) {
				logger.error("detector response dataset and image dimensions do not match");
			}
			dataLength *= dimensions[i];
		}

		for (int i = 0; i < frames; i++) {
			for (int j = 0; j < dataLength; j++) {
				mydata[i * dataLength + j] = new Float(responseBuffer[j] * parentdata[i * dataLength + j]);
				myerror[i * dataLength + j] = new Float(responseBuffer[j] * responseBuffer[j] * parenterror[i * dataLength + j]);
			}
		}
		
		return new Object[] {mydata, myerror};
	}
	
	@Deprecated
	public float[] process(Serializable buffer, int frames, final int[] dimensions) {
		return (float[]) process(buffer, buffer, frames, dimensions)[0];
	}
}
