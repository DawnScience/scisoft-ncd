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

package uk.ac.diamond.scisoft.ncd.calibration.rcp.views;

import javax.measure.Quantity;
import javax.measure.quantity.Length;

import org.eclipse.swt.widgets.Composite;

import tec.units.ri.quantity.Quantities;
import tec.units.ri.unit.MetricPrefix;
import tec.units.ri.unit.Units;
import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;

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
	protected Quantity<Length> getPixel() {
		NcdDetectorSettings detSettings = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(getDetectorName()); 
		if (detSettings == null) {
			throw new IllegalArgumentException(NcdMessages.NO_WAXS_DETECTOR);
		}
		Quantity<Length> pxSize = detSettings.getPxSize();
		if (pxSize == null) {
			throw new IllegalArgumentException(NcdMessages.NO_WAXS_PIXEL);
		}
		return Quantities.getQuantity(pxSize.getValue(), pxSize.getUnit()).to(MetricPrefix.MILLI(Units.METRE));
	}
}
