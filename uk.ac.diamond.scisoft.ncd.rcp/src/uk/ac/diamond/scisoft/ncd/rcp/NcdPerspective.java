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

package uk.ac.diamond.scisoft.ncd.rcp;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.navigator.resources.ProjectExplorer;
import org.osgi.framework.FrameworkUtil;

import uk.ac.diamond.scisoft.analysis.rcp.views.DatasetInspectorView;
import uk.ac.diamond.scisoft.analysis.rcp.views.ImageExplorerView;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.rcp.views.SidePlotView;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;
import uk.ac.diamond.scisoft.ncd.rcp.views.SaxsQAxisCalibration;
//import uk.ac.diamond.scisoft.ncd.rcp.views.WaxsQAxisCalibration;

public class NcdPerspective implements IPerspectiveFactory {
	
	public static final String PLUGIN_ID = FrameworkUtil.getBundle(NcdPerspective.class).getSymbolicName();
	
	static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.ncdperspective";
	static final String QAxisFolder_ID = "uk.ac.diamond.scisoft.ncd.rcp.qaxisfolder";
	static final String SideplotFolder_ID = "uk.ac.diamond.scisoft.ncd.rcp.sideplotfolder";

	@Override
	public void createInitialLayout(IPageLayout layout) {

		layout.addView(NcdDataReductionParameters.ID, IPageLayout.BOTTOM, 0.5f, IPageLayout.ID_EDITOR_AREA);
		
		String explorer = ProjectExplorer.VIEW_ID;
		layout.addView(explorer, IPageLayout.LEFT, 0.2f, IPageLayout.ID_EDITOR_AREA);
		if (layout.getViewLayout(explorer) != null)
			layout.getViewLayout(explorer).setCloseable(false);

		String plot = PlotView.ID + "DP";
		layout.addView(plot, IPageLayout.RIGHT, 0.3f, IPageLayout.ID_EDITOR_AREA);
		if (layout.getViewLayout(plot) != null)
			layout.getViewLayout(plot).setCloseable(false);
		
		IFolderLayout sideplotFolderLayout = layout.createFolder(SideplotFolder_ID, IPageLayout.RIGHT, 0.5f, plot);
		String sidePlot = SidePlotView.ID + ":Dataset Plot";
		sideplotFolderLayout.addView(sidePlot);
		sideplotFolderLayout.addView(ImageExplorerView.ID);
		if (layout.getViewLayout(sidePlot) != null)
			layout.getViewLayout(sidePlot).setCloseable(false);
		if (layout.getViewLayout(ImageExplorerView.ID) != null)
			layout.getViewLayout(ImageExplorerView.ID).setCloseable(false);
		
		IFolderLayout qaxisFolderLayout = layout.createFolder(QAxisFolder_ID, IPageLayout.RIGHT, 0.35f, NcdDataReductionParameters.ID);
		qaxisFolderLayout.addView(SaxsQAxisCalibration.ID);
		//qaxisFolderLayout.addView(WaxsQAxisCalibration.ID);
		qaxisFolderLayout.addView(IConsoleConstants.ID_CONSOLE_VIEW);

		String inspector = DatasetInspectorView.ID;
		layout.addStandaloneView(inspector, false, IPageLayout.RIGHT, 0.55f, QAxisFolder_ID);
		if (layout.getViewLayout(inspector) != null)
			layout.getViewLayout(inspector).setCloseable(false);

	}

}
