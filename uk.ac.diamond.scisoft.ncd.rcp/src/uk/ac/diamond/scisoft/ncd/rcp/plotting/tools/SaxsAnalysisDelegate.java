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
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.plotting.api.PlottingFactory;
import org.dawnsci.plotting.api.tool.IToolPageSystem;
import org.dawnsci.plotting.api.trace.ILineTrace;
import org.dawnsci.plotting.api.trace.ITrace;
import org.dawnsci.plotting.api.trace.ITraceListener;
import org.dawnsci.plotting.api.trace.TraceEvent;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.progress.UIJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IErrorDataset;
import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;

/**
 * Delegates plotting and maths to a single place which can be used on more than one part.
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
				if (!lineTrace.isUserTrace()) {
					return Status.CANCEL_STATUS;
				}
				
				IDataset xData = lineTrace.getXData();
				IDataset yData = lineTrace.getYData();
				if (xData == null || yData == null) {
					return Status.CANCEL_STATUS;
				}
				
				IDataset xErrors = null;
				IDataset yErrors = null;
				if (xData instanceof IErrorDataset && ((IErrorDataset) xData).hasErrors()) {
					xErrors = ((IErrorDataset) xData).getError().clone();
				}
				if (yData instanceof IErrorDataset && ((IErrorDataset) yData).hasErrors()) {
					yErrors = ((IErrorDataset) yData).getError().clone();
				}
				
				AbstractDataset xTraceData = (AbstractDataset) xData.clone();
				if (xErrors != null) {
					xTraceData.setError(xErrors);
				}
				AbstractDataset yTraceData = (AbstractDataset) yData.clone();
				if (yErrors != null) {
					yTraceData.setError(yErrors);
				}

				try {
				    this.pt.process(xTraceData, yTraceData.squeeze());
				} catch (Throwable ne) {
					logger.error("Cannot process "+yTraceData.getName(), ne);
					continue;
				}
				ILineTrace tr = saxsPlottingSystem.createLineTrace(lineTrace.getName());
				tr.setData(xTraceData, yTraceData);
				tr.setTraceColor(lineTrace.getTraceColor());
				tr.setErrorBarColor(lineTrace.getErrorBarColor());
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
	
	public Color getColor(SaxsAnalysisPlotType pt) {
		// TODO FIXME This means that all the plots are the same color if multiple are being processed.
		final int[] col = pt.getRgb();
		return new Color(Display.getDefault(), col[0], col[1], col[2]);
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

	public Object getAdapter(Class clazz) {
		if (clazz==IToolPageSystem.class || clazz==IPlottingSystem.class) {
			return saxsPlottingSystem;
		}
		return null;
	}

}
