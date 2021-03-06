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

import org.dawb.common.ui.views.ValuePageView;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.dawnsci.plotting.api.preferences.BasePlottingConstants;
import org.eclipse.dawnsci.plotting.api.preferences.PlottingConstants;
import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.navigator.resources.ProjectExplorer;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import uk.ac.diamond.scisoft.analysis.rcp.views.DatasetInspectorView;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDetectorParameters;


public class NcdPerspective implements IPerspectiveFactory {
	
	/**
	 * String used elsewhere, do not change
	 */
	static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.ncdperspective";
	static final String ProjectFolder_ID = "uk.ac.diamond.scisoft.ncd.rcp.projectFolder";
	static final String ToolsFolder_ID = "org.dawb.workbench.plotting.views.toolFoleder";
	static final String ToolPageView2D_ID = "org.dawb.workbench.plotting.views.toolPageView.2D";
	
	@Override
	public void createInitialLayout(IPageLayout layout) {

		IFolderLayout projectFolderLayout = layout.createFolder(ProjectFolder_ID, IPageLayout.LEFT, 0.2f, IPageLayout.ID_EDITOR_AREA);
		String explorer = ProjectExplorer.VIEW_ID;
		projectFolderLayout.addView(explorer);
		if (layout.getViewLayout(explorer) != null)
			layout.getViewLayout(explorer).setCloseable(false);
		projectFolderLayout.addView(Activator.FILEVIEW_ID);

		IFolderLayout toolsFolderLayout = layout.createFolder(ToolsFolder_ID, IPageLayout.RIGHT, 0.65f, IPageLayout.ID_EDITOR_AREA);
		toolsFolderLayout.addView(ToolPageView2D_ID);
		if (layout.getViewLayout(ToolPageView2D_ID) != null)
			layout.getViewLayout(ToolPageView2D_ID).setCloseable(false);
		toolsFolderLayout.addView(ValuePageView.ID);
		layout.addView(NcdDataReductionParameters.ID, IPageLayout.BOTTOM, 0.65f, ToolPageView2D_ID);
		
		
		String inspector = DatasetInspectorView.ID;
		layout.addView(inspector, IPageLayout.BOTTOM, 0.7f, IPageLayout.ID_EDITOR_AREA);
		if (layout.getViewLayout(inspector) != null)
			layout.getViewLayout(inspector).setCloseable(false);

		String plot = PlotView.ID + "DP";
		layout.addView(plot, IPageLayout.RIGHT, 0.45f, IPageLayout.ID_EDITOR_AREA);
		if (layout.getViewLayout(plot) != null)
			layout.getViewLayout(plot).setCloseable(false);
		
		layout.addView(NcdDetectorParameters.ID, IPageLayout.BOTTOM, 0.75f, IPageLayout.ID_EDITOR_AREA);
		
		final ScopedPreferenceStore store = new ScopedPreferenceStore(InstanceScope.INSTANCE, "org.dawnsci.plotting");
	    store.setValue(BasePlottingConstants.COLOUR_SCHEME, "NCD");

	}

}
