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

import org.dawb.common.ui.menu.CheckableActionGroup;
import org.dawb.common.ui.menu.MenuAction;
import org.dawb.common.ui.util.EclipseUtils;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.tool.AbstractToolPage;
import org.eclipse.dawnsci.plotting.api.tool.IToolPageSystem;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;

import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;


public class SaxsAnalysisTool extends AbstractToolPage {

	private SaxsAnalysisDelegate delegate;
	private Action openSeparate;
	
	public SaxsAnalysisTool() {
		
		delegate = new SaxsAnalysisDelegate();
	}
	


	@Override
	public ToolPageRole getToolPageRole() {
		return ToolPageRole.ROLE_1D;
	}

	@Override
	public void createControl(Composite parent) {
		
		createActions();
		delegate.setLinkedPlottingSystem(getPlottingSystem());
		delegate.createPlotPart(parent, getSite().getActionBars(), PlotType.XY, getViewPart());

		setTitle("SAXS Analysis ("+delegate.getPlotType().getName()+")");
		
		super.createControl(parent);
	}

	
	private void createActions() {
		
		final MenuAction    plotChoice = new MenuAction("Plot Type");
		// It's a saxs barrow! Do you see what I have done there? Do you?
		plotChoice.setImageDescriptor(Activator.getImageDescriptor("icons/baggage-cart-box.png"));
		final CheckableActionGroup grp = new CheckableActionGroup();
		
		for (final SaxsAnalysisPlotType pt : SaxsAnalysisPlotType.values()) {
			final IAction action = new Action(pt.getName(), IAction.AS_CHECK_BOX) {
				@Override
				public void run() {
					if (openSeparate!=null) {
						openSeparate.setText("Open '"+pt.getName()+"' in separate view locked to '"+getPlottingSystem().getPart().getTitle()+"'");
					}
					setTitle("SAXS Analysis ("+pt.getName()+")");
					delegate.process(pt);
					plotChoice.setToolTipText(pt.getName());
				}
			};
			if (delegate.getPlotType()==pt) {
				plotChoice.setToolTipText(pt.getName());
                action.setChecked(true);
			}
			plotChoice.add(action);
			grp.add(action);
		}
		
		getSite().getActionBars().getToolBarManager().add(plotChoice);
		
		this.openSeparate = new Action("Open '"+delegate.getPlotType().getName()+"' in separate view locked to '"+getPlottingSystem().getPart().getTitle()+"'", 
				                                Activator.getImageDescriptor("icons/plot-open.png"))  {
			@Override
			public void run() {
				
				try {
					final SaxsAnalysisView saxsView = (SaxsAnalysisView)EclipseUtils.getPage().showView(SaxsAnalysisView.ID, 
							                                            delegate.getPlotType().getName()+" ("+getPart().getTitle()+")",
							                                            IWorkbenchPage.VIEW_ACTIVATE);
				    saxsView.setLinkage(getPart(), delegate.getPlotType());
				} catch (Throwable e) {
					logger.error("Problem opening view '"+delegate.getPlotType().getName()+" ("+getPart().getTitle()+")'", e);
				}
			}
		};
		getSite().getActionBars().getToolBarManager().add(openSeparate);
		

		final Action closeAll = new Action("Close all saxs views linked to '"+getPart().getTitle()+"'", 
			 	                             Activator.getImageDescriptor("icons/plot-close.png"))  {
			@Override
			public void run() {

				try {
					Pattern pattern = Pattern.compile(SaxsAnalysisPlotType.getRegex()+" \\((.+)\\)");
					final IViewReference[] refs = EclipseUtils.getPage().getViewReferences();
					for (IViewReference ivf : refs) {
						if (!ivf.getId().equals(SaxsAnalysisView.ID))continue;
						final String secondId = ivf.getSecondaryId();
						final Matcher matcher = pattern.matcher(secondId);
						if (matcher.matches()) {
							final String plotName = matcher.group(2);
							if (plotName.equals(getPlottingSystem().getPlotName())) {
								EclipseUtils.getPage().hideView(ivf);
							}
						}
					}
					
				
				} catch (Throwable e) {
					logger.error("Problem closing views", e);
				}
			}
		};
		getSite().getActionBars().getToolBarManager().add(closeAll);

	}

	@Override
	public Control getControl() {
		return delegate.getComposite();
	}

	@Override
	public void setFocus() {
		if (getControl() != null && !(getControl().isDisposed())) {
			getControl().setFocus();
		}
	}
	
	@Override
	public void activate() {
		super.activate();
		delegate.activate(true);
	}
	
	@Override
	public void deactivate() {
		delegate.deactivate();
		super.deactivate();		
	}
	
	
	@Override
	public void dispose() {
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
