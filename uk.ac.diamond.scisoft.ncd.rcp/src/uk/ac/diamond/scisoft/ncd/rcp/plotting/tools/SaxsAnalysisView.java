/*
 * Copyright 2013 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.rcp.plotting.tools;

import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;

import uk.ac.diamond.scisoft.ncd.utils.SaxsAnalysisPlotType;

/**
 * This view statically follows the part with the plot it is interested in
 * and does the current plotType maths on its contents
 */
public class SaxsAnalysisView extends ViewPart {

	public final static String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.sasAnalysisStaticView";
 
	private SaxsAnalysisDelegate delegate;
	
	public SaxsAnalysisView() {
		delegate = new SaxsAnalysisDelegate();
	}

	@Override
	public void createPartControl(Composite parent) {
		delegate.createPlotPart(parent, getViewSite().getActionBars(), PlotType.XY, this);
	}
	
	public void setLinkage(final IWorkbenchPart linkedPart, final SaxsAnalysisPlotType plotType) {
		
        final IPlottingSystem linked = (IPlottingSystem)linkedPart.getAdapter(IPlottingSystem.class);
        delegate.setLinkedPlottingSystem(linked);
        delegate.activate(false);
		delegate.process(plotType);
		setPartName(plotType.getName()+" ("+linkedPart.getTitle()+")");
	}

	@Override
	public void setFocus() {
		delegate.setFocus();
	}
	
	@Override
	public void dispose() {
		delegate.deactivate();
		delegate.dispose();
		super.dispose();
	}

}
