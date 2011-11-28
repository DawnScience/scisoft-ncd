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
