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
