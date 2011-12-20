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
