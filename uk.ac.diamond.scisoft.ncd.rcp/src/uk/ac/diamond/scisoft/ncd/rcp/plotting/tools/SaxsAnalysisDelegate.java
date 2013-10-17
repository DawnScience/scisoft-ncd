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

import java.util.Collection;

import org.apache.commons.math3.util.Pair;
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.plotting.api.PlottingFactory;
import org.dawnsci.plotting.api.trace.ILineTrace;
import org.dawnsci.plotting.api.trace.ITrace;
import org.dawnsci.plotting.api.trace.ITraceListener;
import org.dawnsci.plotting.api.trace.TraceEvent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.progress.UIJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;
import uk.ac.diamond.scisoft.ncd.utils.SaxsAnalysisPlotType;

/**
 * Deligates plotting and maths to a single place which can be used on more than one part.
 */
class SaxsAnalysisDelegate {
	
	private static final Logger logger = LoggerFactory.getLogger(SaxsAnalysisDelegate.class);
    private static final String PLOT_TYPE_PROP = "uk.ac.diamond.scisoft.ncd.rcp.plotting.tools.plotType";
    

	private IPlottingSystem       linkage;
	private IPlottingSystem       saxsPlottingSystem;
	private SaxsAnalysisPlotType  plotType;
	private SaxsJob               saxsUpdateJob;
	private ITraceListener        traceListener;


	SaxsAnalysisDelegate() {
		
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
	
	protected void setLinkedPlottingSystem(IPlottingSystem linkage) {
		this.linkage = linkage;
	}
	
	protected void process(final SaxsAnalysisPlotType pt) {
		
		if (saxsPlottingSystem==null || saxsPlottingSystem.getPlotComposite()==null) return;
		
		plotType = pt;
		Activator.getDefault().getPreferenceStore().setValue(PLOT_TYPE_PROP, pt.getName());
		
		final Collection<ITrace> traces = linkage.getTraces(ILineTrace.class);
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

	public void createPlotPart(Composite parent, IActionBars bars, PlotType hint, IWorkbenchPart part) {
		saxsPlottingSystem.createPlotPart(parent, plotType.getName(), bars, hint, part);
	}
	
	public void dispose() {
		saxsPlottingSystem.dispose();
		linkage = null;
	}


	public void setFocus() {
		saxsPlottingSystem.setFocus();
	}
	
	
	private class SaxsJob extends UIJob {

		private Collection<ITrace>    traces;
		private SaxsAnalysisPlotType  pt;
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

				try {
				    this.pt.process(xTraceData, yTraceData.squeeze());
				} catch (Throwable ne) {
					logger.error("Cannot process "+yTraceData.getName(), ne);
					continue;
				}
				ILineTrace tr = saxsPlottingSystem.createLineTrace(lineTrace.getName());
				tr.setData(xTraceData, yTraceData);
				saxsPlottingSystem.addTrace(tr);
				saxsPlottingSystem.repaint();
			}

			
			return Status.OK_STATUS;
		}
		
		public void schedule(Collection<ITrace> traces, final SaxsAnalysisPlotType plotType) {
			this.traces   = traces;
			this.pt       = plotType;
			SaxsJob.this.setName("Process "+plotType.getName());
			schedule();
		}
	}


	public void activate(boolean process) {
		if (linkage != null) {
			linkage.addTraceListener(traceListener);
			if (process) process(plotType);
		}	
	}
	public void deactivate() {
		if (linkage != null) {
			linkage.removeTraceListener(traceListener);
		}	
	}

	public Control getComposite() {
		return saxsPlottingSystem.getPlotComposite();
	}

	public SaxsAnalysisPlotType getPlotType() {
		return plotType;
	}

}
