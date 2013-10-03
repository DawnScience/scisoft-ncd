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
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math3.util.Pair;
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.tool.AbstractToolPage;
import org.dawnsci.plotting.api.trace.ILineTrace;
import org.dawnsci.plotting.api.trace.ITrace;
import org.dawnsci.plotting.api.trace.ITraceListener;
import org.dawnsci.plotting.api.trace.TraceEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.ncd.utils.SaxsAnalysisPlots;


public class SaxsAnalysisTool extends AbstractToolPage {

    private static final Logger logger = LoggerFactory.getLogger(SaxsAnalysisTool.class);
    
	private static Composite composite;
	private static Button loglog, guinier, porod, kratky, zimm, debyebueche;
	private static IPlottingSystem plottingSystem = null;
	
	private static Map<String,Button> plotMap;
	private static Map<String,SaxsPlotSelectionAdapter> plotListeners;
	
	public SaxsAnalysisTool() {
	}

	private ITraceListener traceListener = new ITraceListener.Stub() {
		@Override
		protected void update(TraceEvent evt) {
			if (plotMap != null) {
				for (Button btn : plotMap.values()) {
					if (btn != null && !(btn.isDisposed()) && btn.getSelection()) {
						btn.notifyListeners(SWT.Selection, null);
					}
				}
			}
		}

	};
	
	private class SaxsPlotSelectionAdapter extends SelectionAdapter {
		
		private Button btn;
		private String plotName;
		
		public SaxsPlotSelectionAdapter(Button btn, String plotName) {
			super();
			this.btn = btn;
			this.plotName = plotName;
			
		}
		
		@Override
		public void widgetSelected(SelectionEvent e) {
			final Collection<ITrace> traces = plottingSystem.getTraces();
			if (btn.getSelection()) {

				Display.getDefault().asyncExec(new Runnable() {

					@Override
					public void run() {
						PlotView pv;
						IPlottingSystem ps;
						try {
							pv = (PlotView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
									.showView(PlotView.PLOT_VIEW_MULTIPLE_ID, plotName, IWorkbenchPage.VIEW_VISIBLE);
							ps = pv.getPlottingSystem();
							ps.setTitle(plotName);
							Pair<String, String> axesTitles = SaxsAnalysisPlots.getSaxsPlotAxes(plotName);
							ps.getSelectedXAxis().setTitle(axesTitles.getFirst());
							ps.getSelectedYAxis().setTitle(axesTitles.getSecond());
							ps.clear();
						} catch (Exception ex) {
							logger.error("Error creating SAXS {} plot view", plotName, ex);
							return;
						}
						IDataset[] xData = new IDataset[] {};
						IDataset[] yData = new IDataset[] {};
						for (ITrace trace : traces) {
							if (trace instanceof ILineTrace) {
								ILineTrace lineTrace = (ILineTrace) trace;
								AbstractDataset xTraceData = (AbstractDataset) lineTrace.getXData().clone();
								AbstractDataset yTraceData = (AbstractDataset) lineTrace.getYData().clone();

								updatePlotData(xTraceData, yTraceData);
								xTraceData.setErrorBuffer(null);
								yTraceData.setErrorBuffer(null);
								xData = (IDataset[]) ArrayUtils.add(xData, xTraceData);
								yData = (IDataset[]) ArrayUtils.add(yData, yTraceData);
								ILineTrace tr = ps.createLineTrace(lineTrace.getName());
								tr.setData(xTraceData, yTraceData);
								ps.addTrace(tr);
							}
						}
						ps.repaint();
					}
				});
			} else {
				IViewReference ivr = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
						.findViewReference(PlotView.PLOT_VIEW_MULTIPLE_ID, plotName);
				if (ivr != null) {
					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().hideView(ivr.getView(false));

				}
			}
		}

		private void updatePlotData(AbstractDataset xTraceData, AbstractDataset yTraceData) {
			if (plotName.equals(SaxsAnalysisPlots.LOGLOG_PLOT)) {
				IndexIterator itr = yTraceData.getIterator();
				while (itr.hasNext()) {
					int idx = itr.index;
					yTraceData.set(Math.log10(yTraceData.getDouble(idx)), idx);
				}
				itr = xTraceData.getIterator();
				while (itr.hasNext()) {
					int idx = itr.index;
					xTraceData.set(Math.log10(xTraceData.getDouble(idx)), idx);
				}
				return;
			}
			if (plotName.equals(SaxsAnalysisPlots.GUINIER_PLOT)) {
				IndexIterator itr = yTraceData.getIterator();
				while (itr.hasNext()) {
					int idx = itr.index;
					yTraceData.set(Math.log(yTraceData.getDouble(idx)), idx);
				}
				xTraceData.ipower(2);
				return;
			}
			if (plotName.equals(SaxsAnalysisPlots.POROD_PLOT)) {
				IndexIterator itr = yTraceData.getIterator();
				while (itr.hasNext()) {
					int idx = itr.index;
					yTraceData.set(Math.pow(yTraceData.getDouble(idx), 4) * xTraceData.getDouble(idx), idx);
				}
				return;
			}
			if (plotName.equals(SaxsAnalysisPlots.KRATKY_PLOT)) {
				IndexIterator itr = yTraceData.getIterator();
				while (itr.hasNext()) {
					int idx = itr.index;
					yTraceData.set(Math.pow(yTraceData.getDouble(idx), 2) * xTraceData.getDouble(idx), idx);
				}
				return;
			}
			if (plotName.equals(SaxsAnalysisPlots.ZIMM_PLOT)) {
				yTraceData.ipower(-1);
				xTraceData.ipower(2);
				return;
			}
			if (plotName.equals(SaxsAnalysisPlots.DEBYE_BUECHE_PLOT)) {
				yTraceData.ipower(-0.5);
				xTraceData.ipower(2);
				return;
			}
		}
	}

	@Override
	public ToolPageRole getToolPageRole() {
		return ToolPageRole.ROLE_1D;
	}

	@Override
	public void createControl(Composite parent) {
		composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		
		final ScrolledComposite sc = new ScrolledComposite(composite, SWT.V_SCROLL);
		sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		final Composite properties = new Composite(sc, SWT.NONE);
		properties.setLayout(new GridLayout(2, true));
		properties.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true));
		
		plotMap = new HashMap<String, Button>();
		plotListeners = new HashMap<String, SaxsAnalysisTool.SaxsPlotSelectionAdapter>();
		
		createSaxsPlotWidget(properties, SaxsAnalysisPlots.LOGLOG_PLOT, loglog);
		createSaxsPlotWidget(properties, SaxsAnalysisPlots.GUINIER_PLOT, guinier);
		createSaxsPlotWidget(properties, SaxsAnalysisPlots.POROD_PLOT, porod);
		createSaxsPlotWidget(properties, SaxsAnalysisPlots.KRATKY_PLOT, kratky);
		createSaxsPlotWidget(properties, SaxsAnalysisPlots.ZIMM_PLOT, zimm);
		createSaxsPlotWidget(properties, SaxsAnalysisPlots.DEBYE_BUECHE_PLOT, debyebueche);
		
		sc.setContent(properties);
		sc.setExpandVertical(true);
		sc.setExpandHorizontal(true);
		composite.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Rectangle r = sc.getClientArea();
				sc.setMinHeight(properties.computeSize(r.width, SWT.DEFAULT).y);
			}
		});
	}

	private void createSaxsPlotWidget(Composite c, String plotName, Button btn) {
		btn = new Button(c, SWT.CHECK);
		btn.setText(plotName);
		Pair<String, String> axesNames = SaxsAnalysisPlots.getSaxsPlotAxes(plotName);
		String toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
		btn.setToolTipText(toolTipText);
		SaxsPlotSelectionAdapter adapter = new SaxsPlotSelectionAdapter(btn, plotName);
		btn.addSelectionListener(new SaxsPlotSelectionAdapter(btn, plotName));
		plotListeners.put(plotName, adapter);
		plotMap.put(plotName, btn);
	}
	
	@Override
	public Control getControl() {
		if (composite == null) {
			return null;
		}
		return composite;
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
		
		if (plottingSystem == null) {
			plottingSystem = getPlottingSystem();
		}
		
		if (plottingSystem != null) {
			plottingSystem.addTraceListener(traceListener);
		}
		
		if (plotMap != null) {
			for (Entry<String, Button> entry : plotMap.entrySet()) {
				String plotName = entry.getKey();
				Button btn = entry.getValue();
				if (btn.getSelection()) {
					try {
						PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
								.showView(PlotView.PLOT_VIEW_MULTIPLE_ID, plotName, IWorkbenchPage.VIEW_VISIBLE);
					} catch (PartInitException e) {
						logger.error("Error creating SAXS {} plot view", plotName, e);
					}
				}
			}
		}
	}
	
	
	@Override
	public void deactivate() {
		if (plottingSystem != null) {
			plottingSystem.removeTraceListener(traceListener);
		}
		
		if (plotMap != null) {
			for (Entry<String, Button> entry : plotMap.entrySet()) {
				String plotName = entry.getKey();
				Button btn = entry.getValue();
				btn.removeSelectionListener(plotListeners.get(plotName));
			}
		}
		super.deactivate();		
	}
	
	
	@Override
	public boolean isStaticTool() {
		return true;
	}

	@Override
	public boolean isAlwaysSeparateView() {
		return true;
		
	}
	
	@Override
	public void dispose() {
		plottingSystem = null;
		super.dispose();
	}
}
