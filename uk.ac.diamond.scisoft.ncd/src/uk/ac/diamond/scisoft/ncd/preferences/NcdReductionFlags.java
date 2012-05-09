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

package uk.ac.diamond.scisoft.ncd.preferences;

public class NcdReductionFlags {

	private boolean enableAverage;
	private boolean enableBackground;
	private boolean enableDetectorResponse;
	private boolean enableInvariant;
	private boolean enableNormalisation;
	private boolean enableSector;
	private boolean enableRadial;
	private boolean enableAzimuthal;
	private boolean enableFastintegration;
	private boolean enableWaxs;
	private boolean enableSaxs;
	
	public NcdReductionFlags() {
		enableAverage = false;
		enableBackground = false;
		enableDetectorResponse = false;
		enableInvariant = false;
		enableNormalisation = false;
		enableSector = false;
		enableRadial = false;
		enableAzimuthal = false;
		enableFastintegration = false;
		enableWaxs = false;
		enableSaxs = false;
	}
	
	public NcdReductionFlags(NcdReductionFlags flags) {
		enableAverage = flags.enableAverage;
		enableBackground = flags.enableBackground;
		enableDetectorResponse = flags.enableDetectorResponse;
		enableInvariant = flags.enableInvariant;
		enableNormalisation = flags.enableNormalisation;
		enableSector = flags.enableSector;
		enableRadial = flags.enableRadial;
		enableAzimuthal = flags.enableAzimuthal;
		enableFastintegration = flags.enableFastintegration;
		enableWaxs = flags.enableWaxs;
		enableSaxs = flags.enableSaxs;
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
	
	public boolean isEnableRadial() {
		return enableRadial;
	}

	public void setEnableRadial(boolean enableRadial) {
		this.enableRadial = enableRadial;
	}

	public boolean isEnableAzimuthal() {
		return enableAzimuthal;
	}

	public void setEnableAzimuthal(boolean enableAzimuthal) {
		this.enableAzimuthal = enableAzimuthal;
	}

	public boolean isEnableFastintegration() {
		return enableFastintegration;
	}

	public void setEnableFastintegration(boolean enableFastintegration) {
		this.enableFastintegration = enableFastintegration;
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
