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

package uk.ac.diamond.scisoft.ncd.rcp.views;

import javax.measure.quantity.Length;
import javax.measure.unit.SI;

import org.eclipse.swt.widgets.Composite;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.data.NcdDetectorSettings;

public class WaxsQAxisCalibration extends NcdQAxisCalibration {
	
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.WaxsQAxisCalibration";
	
	@Override
	public void createPartControl(Composite parent) {
		GUI_PLOT_NAME = "Dataset Plot";

		super.createPartControl(parent);
		
	}

	@Override
	protected String getDetectorName() {
		return ncdWaxsDetectorSourceProvider.getWaxsDetector();
	}

	@Override
	protected Amount<Length> getPixel(boolean scale) {
		NcdDetectorSettings detector = ncdDetectorSourceProvider.getNcdDetectors().get(getDetectorName()); 
		return detector.getPxSize().copy().to(SI.MILLIMETRE);
	}
	
}
