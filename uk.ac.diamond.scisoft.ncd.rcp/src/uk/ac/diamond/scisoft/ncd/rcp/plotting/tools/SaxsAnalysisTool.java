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


import java.util.Collection;

import org.apache.commons.math3.util.Pair;
import org.dawb.common.ui.menu.CheckableActionGroup;
import org.dawb.common.ui.menu.MenuAction;
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.plotting.api.PlottingFactory;
import org.dawnsci.plotting.api.tool.AbstractToolPage;
import org.dawnsci.plotting.api.trace.ILineTrace;
import org.dawnsci.plotting.api.trace.ITrace;
import org.dawnsci.plotting.api.trace.ITraceListener;
import org.dawnsci.plotting.api.trace.TraceEvent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.progress.UIJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;
import uk.ac.diamond.scisoft.ncd.utils.SaxsAnalysisPlotType;


public class SaxsAnalysisTool extends AbstractToolPage {

    private static final Logger logger = LoggerFactory.getLogger(SaxsAnalysisTool.class);
    private static final String PLOT_TYPE_PROP = "uk.ac.diamond.scisoft.ncd.rcp.plotting.tools.plotType";
    
	private SaxsAnalysisPlotType  plotType;
	private ITraceListener     traceListener;
	private IPlottingSystem    saxsPlottingSystem;
	private SaxsJob            saxsUpdateJob;
	
	public SaxsAnalysisTool() {
		
		String pt = Activator.getDefault().getPreferenceStore().getString(PLOT_TYPE_PROP);
		if (pt==null || "".equals(pt)) pt = SaxsAnalysisPlotType.LOGLOG_PLOT.getName();
		plotType = SaxsAnalysisPlotType.forName(pt);
		
		saxsUpdateJob = new SaxsJob();
		traceListener = new ITraceListener.Stub() {
			@Override
			protected void update(TraceEvent evt) {
				process(plotType);
			}	
		};
		try {
			saxsPlottingSystem = PlottingFactory.createPlottingSystem();
		} catch (Exception e) {
			logger.error("Cannot get a plotting system for the sas plot!", e);
		}
	}
	
	protected void process(final SaxsAnalysisPlotType plotType) {
		
		if (saxsPlottingSystem==null || saxsPlottingSystem.getPlotComposite()==null) return;
		
		final Collection<ITrace> traces = getPlottingSystem().getTraces(ILineTrace.class);
		if (traces!=null && !traces.isEmpty()) {
			saxsPlottingSystem.setTitle(plotType.getName());
			Pair<String, String> axesTitles = plotType.getAxisNames();
			saxsPlottingSystem.getSelectedXAxis().setTitle(axesTitles.getFirst());
			saxsPlottingSystem.getSelectedYAxis().setTitle(axesTitles.getSecond());
			saxsUpdateJob.schedule(traces, plotType);
		} else {
			saxsPlottingSystem.clear();
		}
	}

	private class SaxsJob extends UIJob {

		private Collection<ITrace> traces;
		private SaxsAnalysisPlotType  plotType;
		public SaxsJob() {
			super("Process ");
		}


		@Override
		public IStatus runInUIThread(IProgressMonitor monitor) {
			
			if (monitor.isCanceled()) return Status.CANCEL_STATUS;
			
			saxsPlottingSystem.clear();
			for (ITrace trace : traces) {
				ILineTrace lineTrace = (ILineTrace) trace;
				if (!lineTrace.isUserTrace())                                 return Status.CANCEL_STATUS;
				if (lineTrace.getXData()==null || lineTrace.getYData()==null) return Status.CANCEL_STATUS;

				AbstractDataset xTraceData = (AbstractDataset) lineTrace.getXData().clone();
				AbstractDataset yTraceData = (AbstractDataset) lineTrace.getYData().clone();

				plotType.process(xTraceData, yTraceData.squeeze());
				ILineTrace tr = saxsPlottingSystem.createLineTrace(lineTrace.getName());
				tr.setData(xTraceData, yTraceData);
				saxsPlottingSystem.addTrace(tr);
				saxsPlottingSystem.repaint();
			}

			
			return Status.OK_STATUS;
		}
		
		public void schedule(Collection<ITrace> traces, final SaxsAnalysisPlotType plotType) {
			this.traces   = traces;
			this.plotType = plotType;
			SaxsJob.this.setName("Process "+plotType.getName());
			schedule();
		}
	}

	@Override
	public ToolPageRole getToolPageRole() {
		return ToolPageRole.ROLE_1D;
	}

	@Override
	public void createControl(Composite parent) {
		
		createActions();
		saxsPlottingSystem.createPlotPart(parent, plotType.getName(), getSite().getActionBars(), PlotType.XY, getViewPart());
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
					plotType = pt;
					Activator.getDefault().getPreferenceStore().setValue(PLOT_TYPE_PROP, pt.getName());
					process(pt);
					plotChoice.setToolTipText(pt.getName());
				}
			};
			if (plotType==pt) {
				plotChoice.setToolTipText(pt.getName());
                action.setChecked(true);
			}
			plotChoice.add(action);
			grp.add(action);
		}
		
		getSite().getActionBars().getToolBarManager().add(plotChoice);
	}

	@Override
	public Control getControl() {
		return saxsPlottingSystem.getPlotComposite();
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
		if (getPlottingSystem() != null) {
			getPlottingSystem().addTraceListener(traceListener);
			process(plotType);
		}
	}
	
	@Override
	public void deactivate() {
		if (getPlottingSystem()!=null) getPlottingSystem().removeTraceListener(traceListener);
		super.deactivate();		
	}
	
	
	@Override
	public void dispose() {
		saxsPlottingSystem.dispose();
		super.dispose();
	}
}
