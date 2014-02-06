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
import java.util.Arrays;

import org.apache.commons.beanutils.ConvertUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;

public class BackgroundSubtraction {

	private static final Logger logger = LoggerFactory.getLogger(BackgroundSubtraction.class);
	
	private FloatDataset background;
	private DoubleDataset backgroundErrors;

	public void setBackground(AbstractDataset ds) {
		background = (FloatDataset) ds.cast(AbstractDataset.FLOAT32);
		backgroundErrors = (DoubleDataset) ds.getErrorBuffer();
	}

	public FloatDataset getBackground() {
		return background;
	}

	public Object[] process(Serializable buffer, Serializable error, final int[] dimensions) {
		
		float[] parentdata = (float[]) ConvertUtils.convert(buffer, float[].class);
		double[] parenterror = (double[]) ConvertUtils.convert(error, double[].class);
		
		float[] mydata = new float[parentdata.length];
		double[] myerror = new double[parenterror.length];
		
		// first dim is timeframe
		int[] imagedim = Arrays.copyOfRange(dimensions, 1, dimensions.length);

		int bgsize = 1;
		for (int n : background.getShape()) {
			bgsize *= n;
		}
		int parentsize = 1;
		for (int n : dimensions) {
			parentsize *= n;
		}

		// match
		if (bgsize == parentsize) {
			for (int i = 0; i < parentdata.length; i++) {
				mydata[i] = parentdata[i] - background.getData()[i];
				myerror[i] = parenterror[i] + backgroundErrors.getData()[i];
			}
		} else {
			float[] mybg = background.getData().clone();
			double[] myerr = backgroundErrors.getData().clone();
			if (background.getShape().length >= dimensions.length) {
				// averaging
				logger.warn("averaging background to fit data");
				bgsize = 1;
				for (int n : imagedim) {
					bgsize *= n;
				}
				mybg = new float[bgsize];
				myerr = new double[bgsize];
				double multiplicity = parentdata.length / bgsize;
				for (int i = 0; i < background.getData().length; i++) {
					mybg[i % bgsize] += background.getData()[i] / multiplicity;
					myerr[i % bgsize] += backgroundErrors.getData()[i] / multiplicity;
				}
			}
			if (parentsize % bgsize == 0) {
				for (int i = 0; i < parentdata.length; i++) {
					mydata[i] = parentdata[i] - mybg[i % bgsize];
					myerror[i] = parenterror[i] + myerr[i % bgsize];
				}
			} else {
				logger.error("background and data sizes incompatible");
			}
		}
		
		return new Object[] {mydata, myerror};
	}
	
	@Deprecated
	public float[] process(Serializable buffer, final int[] dimensions) {
		return (float[]) process(buffer, buffer, dimensions)[0];
	}
}
