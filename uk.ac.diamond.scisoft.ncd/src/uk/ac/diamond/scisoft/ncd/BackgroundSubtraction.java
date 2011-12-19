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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;

public class BackgroundSubtraction {

	private static final Logger logger = LoggerFactory.getLogger(BackgroundSubtraction.class);
	
	private FloatDataset background;

	public void setBackground(AbstractDataset ds) {
		background = (FloatDataset) ds.cast(AbstractDataset.FLOAT32);
	}

	public FloatDataset getBackground() {
		return background;
	}

	public float[] process(Serializable buffer, final int[] dimensions) {
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
			for (int i = 0; i < mydata.length; i++) {
				mydata[i] = mydata[i] - background.getData()[i];
			}
		} else {
			float[] mybg = background.getData().clone();
			if (background.getShape().length >= dimensions.length) {
				// averaging
				logger.warn("averaging background to fit data");
				bgsize = 1;
				for (int n : imagedim) {
					bgsize *= n;
				}
				mybg = new float[bgsize];
				double multiplicity = mydata.length / bgsize;
				for (int i = 0; i < background.getData().length; i++) {
					mybg[i % bgsize] += background.getData()[i] / multiplicity;
				}
			}
			if (parentsize % bgsize == 0) {
				for (int i = 0; i < mydata.length; i++) {
					mydata[i] = mydata[i] - mybg[i % bgsize];
				}
			} else {
				logger.error("background and data sizes imcompatible");
			}
		}
		
		return mydata;
	}
}
