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

import org.eclipse.swt.widgets.Composite;

public class WaxsQAxisCalibration extends NcdQAxisCalibration {
	
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.WaxsQAxisCalibration";
	
	@Override
	public void createPartControl(Composite parent) {
		GUI_PLOT_NAME = "Dataset Plot";

		super.createPartControl(parent);
		
		detTypes[1].setSelection(true);
		detTypes[0].setSelection(false);
		gpSelectMode.dispose();
	}

	@Override
	protected String getDetectorName() {
		int idx = NcdDataReductionParameters.getDetListWaxs().getSelectionIndex();
		if (idx < 0)
			return null;
		
		return NcdDataReductionParameters.getDetListWaxs().getItem(idx);
	}

	@Override
	protected Double getPixel(boolean scale) {
		return NcdDataReductionParameters.getWaxsPixel(scale);
	}
	
}
