/*-
 * Copyright © 2011 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd.rcp.views;

import org.eclipse.swt.widgets.Composite;

public class SaxsQAxisCalibration extends NcdQAxisCalibration {
	
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.SaxsQAxisCalibration";
	
	@Override
	public void createPartControl(Composite parent) {
		SAXS_PLOT_NAME = "uk.ac.diamond.scisoft.analysis.rcp.plotViewDP";
		WAXS_PLOT_NAME = "uk.ac.diamond.scisoft.analysis.rcp.plotViewDP";
		GUI_PLOT_NAME = "Dataset Plot";
		
		super.createPartControl(parent);
		
		detTypes[0].setSelection(true);
		detTypes[1].setSelection(false);
		findViewAndDetermineMode(currentMode);
		gpSelectMode.dispose();
		
	}

	@Override
	protected String getDetectorName() {
		int idx = NcdDataReductionParameters.getDetListSaxs().getSelectionIndex();
		if (idx < 0)
			return null;
		
		return NcdDataReductionParameters.getDetListSaxs().getItem(idx);
	}

	@Override
	protected Double getPixel(boolean scale) {
		return NcdDataReductionParameters.getSaxsPixel(scale);
	}
	
}
