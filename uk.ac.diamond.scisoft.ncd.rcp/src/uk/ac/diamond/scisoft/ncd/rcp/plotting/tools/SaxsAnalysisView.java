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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.plotting.api.PlottingFactory;
import org.dawnsci.plotting.api.tool.IToolPageSystem;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
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
	
	private Pattern namePattern = Pattern.compile(SaxsAnalysisPlotType.getRegex()+" \\((.+)\\)");

	@Override
	public void createPartControl(Composite parent) {
		delegate.createPlotPart(parent, getViewSite().getActionBars(), PlotType.XY, this);
		
		// We try at this point to get the plotting system that we were connected to
		final String secondId = getViewSite().getSecondaryId();
		if (secondId!=null && secondId.indexOf('(')>-1) {
			setPartName(secondId);
			final Matcher matcher = namePattern.matcher(secondId);
			if (matcher.matches()) {
				final String saxsName = matcher.group(1);
				final String plotName = matcher.group(2);
				
				// We async this in case the plotting system is not there as yet.
				Display.getDefault().asyncExec(new Runnable() {
					@Override
					public void run() {
		                final IPlottingSystem sys = PlottingFactory.getPlottingSystem(plotName);
		                if (sys!=null) {
		                	delegate.setLinkedPlottingSystem(sys);
		                	delegate.activate(false);
		    				delegate.process(SaxsAnalysisPlotType.forName(saxsName));
		               }				
					}
				});

			}
		}
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
	
	@Override
	public Object getAdapter(Class  clazz) {
		if (clazz==IToolPageSystem.class || clazz==IPlottingSystem.class) {
			return delegate.getAdapter(clazz);
		}
		return super.getAdapter(clazz);
	}

}
