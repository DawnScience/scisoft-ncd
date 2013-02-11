/*-
 * Copyright 2012 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.data;

import javax.measure.quantity.Length;

import org.jscience.physics.amount.Amount;

public class NcdDetectorSettings {
	
	private String type;				// Type declared in DetectorTypes class 
	private int dim;					// Detector dimensionality
	private String name;				// Detector Name
	private Amount<Length> pxSize;		// Detector pixel size
	private Integer maxchannel;			// Number of recorded scaler channels

	public NcdDetectorSettings(NcdDetectorSettings ncdDetector) {
		super();
		this.type = ncdDetector.getType();
		this.dim = ncdDetector.getDimension();
		this.name = ncdDetector.getName();
		Amount<Length> tmpPxSize = ncdDetector.getPxSize();
		if (tmpPxSize != null)
			this.pxSize = tmpPxSize.copy();
		this.maxchannel = ncdDetector.getMaxChannel();
	}
	
	public NcdDetectorSettings(String name, String type, int dim) {
		super();
		this.type = type;
		this.dim = dim;
		this.name = name;
		this.pxSize = null;
		this.maxchannel = null;
	}
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public int getDimension() {
		return dim;
	}
	
	public void setDimension(int dim) {
		this.dim = dim;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	public Amount<Length> getPxSize() {
		return pxSize;
	}
	
	public void setPxSize(Amount<Length> pxSize) {
		this.pxSize = pxSize.copy();
	}
	
	public Integer getMaxChannel() {
		return maxchannel;
	}
	
	public void setMaxChannel(Integer maxchannel) {
		this.maxchannel = maxchannel;
	}
	
}
