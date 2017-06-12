/*
 * Copyright 2011, 2017 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.core.preferences;

import javax.measure.Quantity;
import javax.measure.quantity.Length;

import tec.units.ri.quantity.Quantities;

public class NcdDetectors {

	private String detectorWaxs, detectorSaxs;
	private Quantity<Length> pxWaxs, pxSaxs;
	private Integer dimWaxs, dimSaxs;
	
	public NcdDetectors() {
		detectorWaxs = null;
		detectorSaxs = null;
		
		pxWaxs = null;
		pxSaxs = null;
		
		dimWaxs = 1;
		dimSaxs = 2;
	}

	public NcdDetectors(NcdDetectors detectors) {
		detectorWaxs = detectors.detectorWaxs;
		detectorSaxs = detectors.detectorSaxs;
		
		pxWaxs = detectors.pxWaxs;
		pxSaxs = detectors.pxSaxs;
		
		dimWaxs = 1;
		dimSaxs = 2;
	}

	public String getDetectorWaxs() {
		return detectorWaxs;
	}

	public void setDetectorWaxs(String detectorWaxs) {
		this.detectorWaxs = detectorWaxs;
	}

	public String getDetectorSaxs() {
		return detectorSaxs;
	}

	public void setDetectorSaxs(String detectorSaxs) {
		this.detectorSaxs = detectorSaxs;
	}

	public Quantity<Length> getPxWaxs() {
		return (pxWaxs == null) ? null : Quantities.getQuantity(pxWaxs.getValue(), pxWaxs.getUnit());
	}

	public void setPxWaxs(Quantity<Length> pxWaxs) {
		this.pxWaxs = (pxWaxs == null) ? null : Quantities.getQuantity(pxWaxs.getValue(), pxWaxs.getUnit());
	}

	public Quantity<Length> getPxSaxs() {
		return (pxSaxs == null) ? null : Quantities.getQuantity(pxSaxs.getValue(), pxSaxs.getUnit());
	}

	public void setPxSaxs(Quantity<Length> pxSaxs) {
		this.pxSaxs = (pxSaxs == null) ? null : Quantities.getQuantity(pxSaxs.getValue(), pxSaxs.getUnit());
	}
	
	public Integer getDimWaxs() {
		return dimWaxs;
	}

	public void setDimWaxs(Integer dimWaxs) {
		this.dimWaxs = dimWaxs;
	}
	
	public Integer getDimSaxs() {
		return dimSaxs;
	}

	public void setDimSaxs(Integer dimSaxs) {
		this.dimSaxs = dimSaxs;
	}
}
