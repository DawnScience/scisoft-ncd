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
		this.response = (FloatDataset) response.cast(AbstractDataset.FLOAT32);
	}

	
	public float[] process(Serializable buffer, int frames, final int[] dimensions) {
		float[] mydata;
		if (buffer instanceof float[]) {
			mydata = Arrays.copyOf((float[]) buffer, ((float[]) buffer).length);
		} else {
			double[] farr = (double[]) buffer;
			mydata = new float[farr.length];
			for (int i = 0; i < farr.length; i++) {
				mydata[i] = new Float(farr[i]);
			}
		}
		
		float[] responseBuffer = response.getData();

		int dataLength = 1;
		for (int i = 1; i < dimensions.length; i++) {
			if (dimensions[i] != response.getShape()[i - 1]) {
				logger.error("dimensions do not match");
			}
			dataLength *= dimensions[i];
		}

		for (int i = 0; i < frames; i++) {
			for (int j = 0; j < dataLength; j++) {
				mydata[i * dataLength + j] = new Float(responseBuffer[j] * mydata[i * dataLength + j]);
			}
		}
		
		return mydata;
	}
}