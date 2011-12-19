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

public class NcdReductionFlags {

	private boolean enableAverage;
	private boolean enableBackground;
	private boolean enableDetectorResponse;
	private boolean enableInvariant;
	private boolean enableNormalisation;
	private boolean enableSector;
	private boolean enableWaxs;
	private boolean enableSaxs;
	
	public NcdReductionFlags() {
		enableAverage = false;
		enableBackground = false;
		enableDetectorResponse = false;
		enableInvariant = false;
		enableNormalisation = false;
		enableSector = false;
		enableWaxs = false;
		enableSaxs = false;
	}
	
	public boolean isEnableAverage() {
		return enableAverage;
	}
	public void setEnableAverage(boolean enableAverage) {
		this.enableAverage = enableAverage;
	}
	public boolean isEnableBackground() {
		return enableBackground;
	}
	public void setEnableBackground(boolean enableBackground) {
		this.enableBackground = enableBackground;
	}
	public boolean isEnableDetectorResponse() {
		return enableDetectorResponse;
	}
	public void setEnableDetectorResponse(boolean enableDetectorResponse) {
		this.enableDetectorResponse = enableDetectorResponse;
	}
	public boolean isEnableInvariant() {
		return enableInvariant;
	}
	public void setEnableInvariant(boolean enableInvariant) {
		this.enableInvariant = enableInvariant;
	}
	public boolean isEnableNormalisation() {
		return enableNormalisation;
	}
	public void setEnableNormalisation(boolean enableNormalisation) {
		this.enableNormalisation = enableNormalisation;
	}
	public boolean isEnableSector() {
		return enableSector;
	}
	public void setEnableSector(boolean enableSector) {
		this.enableSector = enableSector;
	}
	public boolean isEnableWaxs() {
		return enableWaxs;
	}
	public void setEnableWaxs(boolean enableWaxs) {
		this.enableWaxs = enableWaxs;
	}
	public boolean isEnableSaxs() {
		return enableSaxs;
	}
	public void setEnableSaxs(boolean enableSaxs) {
		this.enableSaxs = enableSaxs;
	}
}
