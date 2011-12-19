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

package uk.ac.diamond.scisoft.ncd.rcp.views;

import org.eclipse.swt.widgets.Composite;

public class WaxsQAxisCalibration extends NcdQAxisCalibration {
	
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.WaxsQAxisCalibration";
	
	@Override
	public void createPartControl(Composite parent) {
		SAXS_PLOT_NAME = "uk.ac.diamond.scisoft.analysis.rcp.plotViewDP";
		WAXS_PLOT_NAME = "uk.ac.diamond.scisoft.analysis.rcp.plotViewDP";
		GUI_PLOT_NAME = "Dataset Plot";

		super.createPartControl(parent);
		
		detTypes[1].setSelection(true);
		detTypes[0].setSelection(false);
		findViewAndDetermineMode(currentMode);
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
