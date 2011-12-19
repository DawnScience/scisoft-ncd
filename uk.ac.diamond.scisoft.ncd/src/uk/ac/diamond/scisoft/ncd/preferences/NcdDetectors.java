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

package uk.ac.diamond.scisoft.ncd.preferences;

public class NcdDetectors {

	private String detectorWaxs, detectorSaxs;
	private Double pxWaxs, pxSaxs;
	private Integer dimWaxs, dimSaxs;
	
	public NcdDetectors() {
		// TODO Auto-generated constructor stub
		detectorWaxs = null;
		detectorSaxs = null;
		
		pxWaxs = 0.0;
		pxSaxs = 0.0;
		
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

	public Double getPxWaxs() {
		return pxWaxs;
	}

	public void setPxWaxs(Double pxWaxs) {
		this.pxWaxs = pxWaxs;
	}

	public Double getPxSaxs() {
		return pxSaxs;
	}

	public void setPxSaxs(Double pxSaxs) {
		this.pxSaxs = pxSaxs;
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
