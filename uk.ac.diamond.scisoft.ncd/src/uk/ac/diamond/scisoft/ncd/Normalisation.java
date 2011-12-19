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

	
	public float[] process(Serializable buffer, Serializable cbuffer, int frames, final int[] dimensions, final int[] cdimensions) {
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
		
		float[] calibdata = (float[]) cbuffer;
		float[] mydata = new float[parentdata.length];

		int calibTotalChannels = cdimensions[1];
		int parentDataLength = 1;
		for (int i = 1; i < dimensions.length; i++) {
			parentDataLength *= dimensions[i];
		}
		
		for (int i = 0; i < frames; i++) {
			float calReading = calibdata[i * calibTotalChannels + calibChannel];
			if (calReading == 0) calReading = 1; //TODO better idea?
			for (int j = i * parentDataLength; j < (i + 1) * parentDataLength; j++) {
				mydata[j] = (float) ((normvalue / calReading) * parentdata[j]);
			}
		}

		return mydata;
	}
}
