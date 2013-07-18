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
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.data.xml.PxSizeXmlAdapter;

@XmlAccessorType(XmlAccessType.FIELD)

public class NcdDetectorSettings {
	
	private String type;				// Type declared in DetectorTypes class 
	private int dim;					// Detector dimensionality
	private String name;				// Detector Name
    @XmlElement
    @XmlJavaTypeAdapter(PxSizeXmlAdapter.class)
	private Amount<Length> pxSize;		// Detector pixel size
	private Integer normChannel;        // Selected scaler channel
	private Integer maxChannel;			// Number of recorded scaler channels

	public NcdDetectorSettings() {
		super();
		this.type = null;
		this.dim = -1;
		this.name = null;
		this.pxSize = null;
		this.normChannel = 0;
		this.maxChannel = 0;
	}
	
	public NcdDetectorSettings(NcdDetectorSettings ncdDetector) {
		super();
		this.type = ncdDetector.getType();
		this.dim = ncdDetector.getDimension();
		this.name = ncdDetector.getName();
		Amount<Length> tmpPxSize = ncdDetector.getPxSize();
		if (tmpPxSize != null) {
			this.pxSize = tmpPxSize.copy();
		}
		setNormChannel(ncdDetector.getNormChannel());
		setMaxChannel(ncdDetector.getMaxChannel());
	}
	
	public NcdDetectorSettings(String name, String type, int dim) {
		super();
		this.type = type;
		this.dim = dim;
		this.name = name;
		this.pxSize = null;
		this.maxChannel = null;
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
	
	public Integer getNormChannel() {
		return normChannel;
	}
	
	public void setNormChannel(Integer normChannel) {
		if (normChannel != null) {
			this.normChannel = normChannel;
		} else {
			this.normChannel = 0;
		}
	}
	
	public Integer getMaxChannel() {
		return maxChannel;
	}
	
	public void setMaxChannel(Integer maxChannel) {
		if (maxChannel != null) {
			this.maxChannel = maxChannel;
		} else {
			this.maxChannel = 0;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + dim;
		result = prime * result + ((maxChannel == null) ? 0 : maxChannel.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((normChannel == null) ? 0 : normChannel.hashCode());
		result = prime * result + ((pxSize == null) ? 0 : pxSize.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		NcdDetectorSettings other = (NcdDetectorSettings) obj;
		if (dim != other.dim)
			return false;
		if (maxChannel == null) {
			if (other.maxChannel != null)
				return false;
		} else if (!maxChannel.equals(other.maxChannel))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (normChannel == null) {
			if (other.normChannel != null)
				return false;
		} else if (!normChannel.equals(other.normChannel))
			return false;
		if (pxSize == null) {
			if (other.pxSize != null)
				return false;
		} else if (!pxSize.equals(other.pxSize))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}
	
}
