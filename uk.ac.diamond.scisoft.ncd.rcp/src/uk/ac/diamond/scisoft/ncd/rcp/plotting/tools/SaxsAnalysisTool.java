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

import org.apache.commons.lang.ArrayUtils;
import org.dawnsci.plotting.api.tool.AbstractToolPage;
import org.dawnsci.plotting.api.trace.ILineTrace;
import org.dawnsci.plotting.api.trace.ITrace;
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
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.SDAPlotter;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;


public class SaxsAnalysisTool extends AbstractToolPage {

    private static final Logger logger = LoggerFactory.getLogger(SaxsAnalysisTool.class);
    
	private Composite composite;
	private Button loglog, guinier, porod, kratky, zimm, debyebuche;
	
	private static final String LOGLOG_PLOT = "Log/Log Plot";
	private static final String GUINIER_PLOT = "Guinier Plot";
	private static final String POROD_PLOT = "Porod Plot";
	private static final String KRATKY_PLOT = "Kratky Plot";
	private static final String ZIMM_PLOT = "Zimm Plot";
	private static final String DEBYE_BUCHE_PLOT = "Debye Buche Plot";
	
	Map<String,Button> plotMap;
	Map<String,SaxsPlotSelectionAdapter> plotListeners;
	
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
			Collection<ITrace> traces = getPlottingSystem().getTraces();
			
			if (btn.getSelection()) {
				try {
					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
							.showView(PlotView.PLOT_VIEW_MULTIPLE_ID, plotName, IWorkbenchPage.VIEW_VISIBLE);
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
						
						xData = (IDataset[]) ArrayUtils.add(xData, xTraceData);
						yData = (IDataset[]) ArrayUtils.add(yData, yTraceData);
					}
				}
				try {
					SDAPlotter.plot(plotName, plotName, xData, yData);
				} catch (Exception ex) {
					logger.error("Error plotting SAXS data in {} view", plotName, ex);
				}
			} else {
				IViewReference ivr = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().findViewReference(PlotView.PLOT_VIEW_MULTIPLE_ID, plotName);
				if (ivr != null) {
					PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().hideView(ivr.getView(false));
				}
			}
		}

		private String[] setPlotAxisNames() {
			
			String[] res = new String[] {"", ""};
			
			if (plotName.equals(LOGLOG_PLOT)) {
				res[0] = "log10(q)";
				res[1] = "log10(I)";
			}
			if (plotName.equals(GUINIER_PLOT)) {
				res[0] = "q^2";
				res[1] = "ln(I)";
			}
			if (plotName.equals(POROD_PLOT)) {
				res[0] = "q";
				res[1] = "Iq^4";
			}
			if (plotName.equals(KRATKY_PLOT)) {
				res[0] = "q";
				res[1] = "Iq^2";
			}
			if (plotName.equals(ZIMM_PLOT)) {
				res[0] = "q^2";
				res[1] = "1/I";
			}
			if (plotName.equals(DEBYE_BUCHE_PLOT)) {
				res[0] = "q^2";
				res[1] = "sqrt(1/I)";
			}
			
			return res;
		}

		private void updatePlotData(AbstractDataset xTraceData, AbstractDataset yTraceData) {
			String[] axisNames = setPlotAxisNames();
			xTraceData.setName(axisNames[0]);
			yTraceData.setName(axisNames[1]);
			if (plotName.equals(LOGLOG_PLOT)) {
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
			if (plotName.equals(GUINIER_PLOT)) {
				IndexIterator itr = yTraceData.getIterator();
				while (itr.hasNext()) {
					int idx = itr.index;
					yTraceData.set(Math.log(yTraceData.getDouble(idx)), idx);
				}
				xTraceData.ipower(2);
				return;
			}
			if (plotName.equals(POROD_PLOT)) {
				IndexIterator itr = yTraceData.getIterator();
				while (itr.hasNext()) {
					int idx = itr.index;
					yTraceData.set(Math.pow(yTraceData.getDouble(idx), 4) * xTraceData.getDouble(idx), idx);
				}
				return;
			}
			if (plotName.equals(KRATKY_PLOT)) {
				IndexIterator itr = yTraceData.getIterator();
				while (itr.hasNext()) {
					int idx = itr.index;
					yTraceData.set(Math.pow(yTraceData.getDouble(idx), 2) * xTraceData.getDouble(idx), idx);
				}
				return;
			}
			if (plotName.equals(ZIMM_PLOT)) {
				yTraceData.ipower(-1);
				xTraceData.ipower(2);
				return;
			}
			if (plotName.equals(DEBYE_BUCHE_PLOT)) {
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
		
		createSaxsPlotWidget(properties, LOGLOG_PLOT, loglog);
		createSaxsPlotWidget(properties, GUINIER_PLOT, guinier);
		createSaxsPlotWidget(properties, POROD_PLOT, porod);
		createSaxsPlotWidget(properties, KRATKY_PLOT, kratky);
		createSaxsPlotWidget(properties, ZIMM_PLOT, zimm);
		createSaxsPlotWidget(properties, DEBYE_BUCHE_PLOT, debyebuche);
		
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
		btn.setToolTipText(plotName);
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
		if (getControl()!=null && !getControl().isDisposed()) {
			getControl().setFocus();
		}
	}
	
	@Override
	public void activate() {
		super.activate();
		//if (plotMap != null) {
		//	for (Entry<String, Button> entry : plotMap.entrySet()) {
		//		String plotName = entry.getKey();
		//		Button btn = entry.getValue();
		//		if (btn.getSelection()) {
		//			try {
		//				PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
		//						.showView(PlotView.PLOT_VIEW_MULTIPLE_ID, plotName, IWorkbenchPage.VIEW_VISIBLE);
		//			} catch (PartInitException e) {
		//				logger.error("Error creating SAXS {} plot view", plotName, e);
		//			}
		//		}
		//	}
		//}
	}
	
	
	@Override
	public void deactivate() {
		super.deactivate();
		//if (plotMap != null) {
		//	for (Entry<String, Button> entry : plotMap.entrySet()) {
		//		String plotName = entry.getKey();
		//		Button btn = entry.getValue();
		//		btn.removeSelectionListener(plotListeners.get(plotName));
		//	}
		//}
	}
}
