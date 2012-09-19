/*
 * Copyright 2012 Diamond Light Source Ltd.
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
import org.eclipse.ui.navigator.resources.ProjectExplorer;
import org.osgi.framework.FrameworkUtil;

import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDetectorParameters;
import uk.ac.diamond.scisoft.ncd.rcp.views.SaxsQAxisCalibration;


public class NcdCalibrationPerspective implements IPerspectiveFactory {
	
	public static final String PLUGIN_ID = FrameworkUtil.getBundle(NcdCalibrationPerspective.class).getSymbolicName();
	
	static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.ncdcalibrationperspective";
	static final String ProjectFolder_ID = "uk.ac.diamond.scisoft.ncd.rcp.projectfolder";
	static final String NcdFolder_ID = "uk.ac.diamond.scisoft.ncd.rcp.ncdfolder";
	static final String QAxisFolder_ID = "uk.ac.diamond.scisoft.ncd.rcp.qaxisfolder";
	static final String ToolPageView2D_ID = "org.dawb.workbench.plotting.views.toolPageView.2D";
	static final String ToolPageView1D_ID = "org.dawb.workbench.plotting.views.toolPageView.1D";
	
	// Currently defined in uk.ac.diamond.sda.navigator.views class 
	static final String FileView_ID = "uk.ac.diamond.sda.navigator.views.FileView";

	@Override
	public void createInitialLayout(IPageLayout layout) {

		layout.addView(ToolPageView2D_ID, IPageLayout.RIGHT, 0.7f, IPageLayout.ID_EDITOR_AREA);
		layout.getViewLayout(ToolPageView2D_ID).setCloseable(false);
		layout.addView(ToolPageView1D_ID, IPageLayout.BOTTOM, 0.6f, ToolPageView2D_ID);
		layout.getViewLayout(ToolPageView1D_ID).setCloseable(false);
		IFolderLayout ncdFolderLayout = layout.createFolder(NcdFolder_ID, IPageLayout.BOTTOM, 0.7f, ToolPageView1D_ID);
		ncdFolderLayout.addView(NcdDetectorParameters.ID);
		ncdFolderLayout.addView(NcdDataReductionParameters.ID);
		
		layout.addView(SaxsQAxisCalibration.ID, IPageLayout.BOTTOM, 0.6f, IPageLayout.ID_EDITOR_AREA);
		
		IFolderLayout projectFolderLayout = layout.createFolder(ProjectFolder_ID, IPageLayout.LEFT, 0.3f, IPageLayout.ID_EDITOR_AREA);
		String explorer = ProjectExplorer.VIEW_ID;
		projectFolderLayout.addView(explorer);
		if (layout.getViewLayout(explorer) != null)
			layout.getViewLayout(explorer).setCloseable(false);
		projectFolderLayout.addView(FileView_ID);
		
		String plot = PlotView.ID + "DP";
		layout.addView(plot, IPageLayout.LEFT, 0.4f, IPageLayout.ID_EDITOR_AREA);
		if (layout.getViewLayout(plot) != null)
			layout.getViewLayout(plot).setCloseable(false);
		
		layout.setEditorAreaVisible(false);
	}

}
