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

package uk.ac.diamond.scisoft.ncd.rcp.views;

import gda.observable.IObserver;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.PlotServer;
import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.fitting.functions.APeak;
import uk.ac.diamond.scisoft.analysis.plotserver.AxisMapBean;
import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationPeak;
import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationResultsBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBean;
import uk.ac.diamond.scisoft.analysis.plotserver.DataBeanException;
import uk.ac.diamond.scisoft.analysis.plotserver.DataSetWithAxisInformation;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiPlotMode;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiUpdate;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.DataSetPlotter;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.PlottingMode;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.sideplot.ISidePlot;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.sideplot.SectorProfile;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.rcp.views.SidePlotView;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;

public class QAxisCalibrationBase extends ViewPart implements IObserver {

	private static final Logger logger = LoggerFactory.getLogger(QAxisCalibrationBase.class);

	private CalibrationTable calTable;
	protected ArrayList<CalibrationPeak> calibrationPeakList = new ArrayList<CalibrationPeak>();

	protected LinkedHashMap<String, LinkedHashMap<String, Double> > cal2peaks;
	

	protected Combo standard;
	protected Spinner braggOrder;
	private Button sectorButton;
	private Button fittingButton;
	private PlotServer plotServer;
	private GuiBean bean;
	protected Group gpSelectMode;


	private StoredPlottingObject oneDData;
	private StoredPlottingObject twoDData;

	protected ArrayList<APeak> peaks = new ArrayList<APeak>();

	private Button calibrateButton;
	protected Text gradient;

	protected Text intercept;
	protected Text cameralength;

	protected Double disttobeamstop;

	protected Button[] detTypes;

	private String[] detChoices = new String[] { "SAXS", "WAXS" };

	protected String currentMode = detChoices[0];
	
	protected boolean originalData2D = true;

	public static String SAXS_PLOT_NAME = "uk.ac.gda.client.ncd.saxsview";
	public static String WAXS_PLOT_NAME = "uk.ac.gda.client.ncd.waxsview";
	public static String GUI_PLOT_NAME = "Saxs Plot";
	protected String ACTIVE_PLOT;
	protected PlotView pv;

	private boolean weHaveOnePending = false;

	private class StoredPlottingObject {
		private IDataset dataset, axis;
		private GuiPlotMode plotMode;
		String plotViewName;

		public StoredPlottingObject(String plotName) {
			DataSetPlotter mp = getMainPlotterofView(plotName);
			if (mp == null) {
				return;
			}
			plotMode = mp.getMode().getGuiPlotMode();
			dataset = mp.getCurrentDataSet();
			if (plotMode == GuiPlotMode.ONED) {
				axis = get1DAxis(plotName);
			}
			plotViewName = plotName;
		}

		private IDataset get1DAxis(String plotName) {
			DataSetPlotter mp = getMainPlotterofView(plotName);
			if (mp == null) {
				return null;
			}
			return mp.getXAxisValues().get(0).toDataset();
		}

		public void plot() {
			plot(plotViewName);
		}

		private void plot(String plotName) {

			PlotView pv = getPlotViewOfView(plotName);
			if (pv == null) {
				return;
			}
			pv.updatePlotMode(plotMode);
			DataBean dBean = new DataBean();
			DataSetWithAxisInformation dswai = new DataSetWithAxisInformation();
			dswai.setData(dataset);
			AxisMapBean amb = new AxisMapBean(AxisMapBean.FULL);
			dswai.setAxisMap(amb);
			try {
				dBean.addData(dswai);
				if (axis != null) {
					dBean.addAxis(AxisMapBean.XAXIS, axis);
				}
				pv.processPlotUpdate(dBean);
			} catch (NullPointerException e) {
				logger.error("The main plotter object does not exist in QAxis calibration");
			} catch (DataBeanException e) {
				logger.error("something wrong with the beans {}", e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public QAxisCalibrationBase() {
		cal2peaks = new LinkedHashMap<String, LinkedHashMap<String, Double> >();
		LinkedHashMap<String, Double> hkl2peaks = new LinkedHashMap<String, Double>();
		
		hkl2peaks.put("(0, 0, 1)", 67.0);
		hkl2peaks.put("(0, 0, 2)", 33.5);
		hkl2peaks.put("(0, 0, 3)", 22.3);
		hkl2peaks.put("(0, 0, 4)", 16.75);
		hkl2peaks.put("(0, 0, 5)", 13.4);
		hkl2peaks.put("(0, 0, 6)", 11.6);
		hkl2peaks.put("(0, 0, 7)", 9.6);
		hkl2peaks.put("(0, 0, 8)", 8.4);
		hkl2peaks.put("(0, 0, 9)", 7.4);
		hkl2peaks.put("(0, 0, 10)", 6.7);
		hkl2peaks.put("(0, 0, 11)", 6.1);
		hkl2peaks.put("(0, 0, 12)", 5.6);
		hkl2peaks.put("(0, 0, 13)", 5.15);
		hkl2peaks.put("(0, 0, 15)", 4.46);
		hkl2peaks.put("(0, 0, 20)", 3.35);
		hkl2peaks.put("(0, 0, 21)", 3.2);
		hkl2peaks.put("(0, 0, 22)", 3.05);
		hkl2peaks.put("(0, 0, 30)", 2.2);
		hkl2peaks.put("(0, 0, 35)", 1.9);
		hkl2peaks.put("(0, 0, 41)", 1.6);
		hkl2peaks.put("(0, 0, 52)", 1.3);
		hkl2peaks.put("(0, 0, 71)", 0.95);
		cal2peaks.put("Collagen Wet", (LinkedHashMap<String, Double>) hkl2peaks.clone()); // SAXS
		hkl2peaks.clear();
		
		hkl2peaks.put("(0, 0, 1)", 65.3);
		hkl2peaks.put("(0, 0, 2)", 32.7);
		hkl2peaks.put("(0, 0, 3)", 21.8);
		hkl2peaks.put("(0, 0, 4)", 16.3);
		hkl2peaks.put("(0, 0, 5)", 13.1);
		hkl2peaks.put("(0, 0, 6)", 10.9);
		hkl2peaks.put("(0, 0, 7)", 9.33);
		hkl2peaks.put("(0, 0, 8)", 8.16);
		hkl2peaks.put("(0, 0, 9)", 7.26);
		hkl2peaks.put("(0, 0, 10)", 6.53);
		hkl2peaks.put("(0, 0, 11)", 5.94);
		hkl2peaks.put("(0, 0, 12)", 5.44);
		hkl2peaks.put("(0, 0, 13)", 5.02);
		hkl2peaks.put("(0, 0, 15)", 4.35);
		cal2peaks.put("Collagen Dry", (LinkedHashMap<String, Double>) hkl2peaks.clone()); // SAXS
		hkl2peaks.clear();
		
		hkl2peaks.put("(0, 0, 1)",  5.838     ); 
		hkl2peaks.put("(0, 0, 2)",  2.919     );
		hkl2peaks.put("(0, 0, 3)",  1.946     );
		hkl2peaks.put("(0, 0, 4)",  1.4595    );
		hkl2peaks.put("(0, 0, 5)",  1.1676    );
		hkl2peaks.put("(0, 0, 6)",  0.973     );
		hkl2peaks.put("(0, 0, 7)",  0.834     );
		hkl2peaks.put("(0, 0, 8)",  0.72975   );
		hkl2peaks.put("(0, 0, 9)",  0.64866667);
		hkl2peaks.put("(0, 0, 10)", 0.5838    );
		hkl2peaks.put("(0, 0, 11)", 0.53072727);
		hkl2peaks.put("(0, 0, 12)", 0.4865    );
		hkl2peaks.put("(0, 0, 13)", 0.44907692);
		cal2peaks.put("Ag Behenate", (LinkedHashMap<String, Double>) hkl2peaks.clone()); // SAXS
		hkl2peaks.clear();
		        
		hkl2peaks.put("(1, 1, 0)", 0.4166);
		hkl2peaks.put("(2, 0, 0)", 0.378);
		hkl2peaks.put("(2, 1, 0)", 0.3014);
		hkl2peaks.put("(0, 2, 0)", 0.249);
		cal2peaks.put("HDPE", (LinkedHashMap<String, Double>) hkl2peaks.clone()); // WAXS
		hkl2peaks.clear();
		
		hkl2peaks.put("(1, 1, 1)", 0.31355);
		hkl2peaks.put("(2, 2, 0)", 0.19201);
		hkl2peaks.put("(3, 1, 1)", 0.16374);
		hkl2peaks.put("(2, 2, 2)", 0.15677);
		hkl2peaks.put("(4, 0, 0)", 0.13577);
		hkl2peaks.put("(3, 3, 1)", 0.12459);
		hkl2peaks.put("(4, 2, 2)", 0.11085);
		hkl2peaks.put("(3, 3, 3)", 0.10451);
		hkl2peaks.put("(5, 1, 1)", 0.10451);
		hkl2peaks.put("(4, 4, 0)", 0.09600);
		hkl2peaks.put("(5, 3, 1)", 0.09179);
		hkl2peaks.put("(4, 4, 2)", 0.09051);
		hkl2peaks.put("(6, 2, 0)", 0.08586);
		hkl2peaks.put("(5, 3, 3)", 0.08281);
		hkl2peaks.put("(6, 2, 2)", 0.08187);
		hkl2peaks.put("(4, 4, 4)", 0.07838);
		hkl2peaks.put("(7, 1, 1)", 0.07604);
		hkl2peaks.put("(5, 5, 1)", 0.07604);
		hkl2peaks.put("(6, 4, 2)", 0.07257);		
		cal2peaks.put("Silicon", (LinkedHashMap<String, Double>) hkl2peaks.clone()); // WAXS
		hkl2peaks.clear();
	}

	@Override
	public void createPartControl(Composite parent) {

		ScrolledComposite scrolledComposite = new ScrolledComposite(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);

		Composite composite = new Composite(scrolledComposite, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scrolledComposite.setContent(composite);

		Composite dataTable = new Composite(composite, SWT.NONE);
		dataTable.setLayout(new GridLayout(1, false));
		dataTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		calTable = new CalibrationTable(dataTable);
		calTable.setInput(calibrationPeakList);

		Composite calibrationResultsComposite = new Composite(composite, SWT.NONE);
		calibrationResultsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		calibrationResultsComposite.setLayout(new GridLayout(2, true));

		Group gpCalibrationResultsComposite = new Group(calibrationResultsComposite, SWT.NONE);
		gpCalibrationResultsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		gpCalibrationResultsComposite.setLayout(new GridLayout(4, false));
		gpCalibrationResultsComposite.setText("Calibration Function");

		calibrationResultsDisplay(gpCalibrationResultsComposite);

		Group gpCameraDistance = new Group(calibrationResultsComposite, SWT.NONE);
		gpCameraDistance.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		gpCameraDistance.setLayout(new GridLayout(3, false));
		gpCameraDistance.setText("Camera Length");

		Label disLab = new Label(gpCameraDistance, SWT.NONE);
		disLab.setText("Camera Distance");

		cameralength = new Text(gpCameraDistance, SWT.READ_ONLY | SWT.FILL | SWT.CENTER);
		cameralength.setText("--");
		cameralength.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		disLab = new Label(gpCameraDistance, SWT.NONE);
		disLab.setText("m");

		Group calibrationControls = new Group(calibrationResultsComposite, SWT.NONE);
		calibrationControls.setLayout(new GridLayout(2, false));
		calibrationControls.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		calibrationControls.setText("Calibration Controls");

		Label lblStandard = new Label(calibrationControls, SWT.NONE);
		lblStandard.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, true));
		lblStandard.setText("Standard");

		standard = new Combo(calibrationControls, SWT.NONE);
		standard.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

		Label lblN = new Label(calibrationControls, SWT.NONE);
		lblN.setToolTipText("n in Braggs law");
		lblN.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, true));
		lblN.setText("Maximum reflection index");

		braggOrder = new Spinner(calibrationControls, SWT.BORDER);
		braggOrder.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

		Group progress = new Group(calibrationResultsComposite, SWT.NONE);
		progress.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		progress.setLayout(new GridLayout(1, false));
		progress.setText("Progress");

		plotServer = PlotServerProvider.getPlotServer();
		plotServer.addIObserver(this);

		displayControlButtons(progress);
		setupGUI();
	}

	private void calibrationResultsDisplay(Group group) {
		Label mlab = new Label(group, SWT.NONE);
		mlab.setText("Gradient");

		gradient = new Text(group, SWT.READ_ONLY | SWT.CENTER);
		gradient.setText("--");
		gradient.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Label clab = new Label(group, SWT.NONE);
		clab.setText("Intercept");

		intercept = new Text(group, SWT.READ_ONLY | SWT.CENTER);
		intercept.setText("--");
		intercept.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	}

	public void runCalibration() {
		set1DfittingActivatedState(false);
		weHaveOnePending = true;
		runJythonCommand();
	}

	protected SelectionListener modeSelectionListener = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent e) {
			for (Button bu : detTypes) {
				if (bu.getSelection()) {
					findViewAndDetermineMode(bu.getText());
					return;
				}
			}
		}
	};

	private void displayControlButtons(Group progress) {
		progress.setLayout(new GridLayout(2, true));
		progress.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		gpSelectMode = new Group(progress, SWT.FILL);
		gpSelectMode.setLayout(new GridLayout(2, true));
		gpSelectMode.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 2, 1));

		detTypes = new Button[detChoices.length];
		int i = 0;
		for (String det : detChoices) {
			detTypes[i] = new Button(gpSelectMode, SWT.RADIO);
			detTypes[i].setText(det);
			detTypes[i].setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
			if (i == 0) {
				detTypes[i].setSelection(true);
			}
			detTypes[i].addSelectionListener(modeSelectionListener);
			i++;
		}

		sectorButton = new Button(progress, SWT.PUSH);
		sectorButton.setText("Radial Integration");
		sectorButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 2, 1));
		sectorButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				if (pv == null) {
					modeSelectionListener.widgetSelected(null);
					if (pv == null || !sectorButton.isEnabled()) {
						return;
					}
				}

				if (pv.getMainPlotter().getMode() != PlottingMode.TWOD && twoDData != null) {
					twoDData.plot(ACTIVE_PLOT);
				}

				runRadialIntegrationAction();
			}
		});

		fittingButton = new Button(progress, SWT.PUSH);
		fittingButton.setText("1D Fitting");
		fittingButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		fittingButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {

				if (pv == null) {
					modeSelectionListener.widgetSelected(null);
					if (pv == null) {
						return;
					}
				}

				if (pv.getMainPlotter().getMode() == PlottingMode.TWOD) {
					runRadialIntegrationAction();
					runPushRadialProfileAction();
					oneDData = new StoredPlottingObject(ACTIVE_PLOT);
				} else {
					if (oneDData == null) {
						oneDData = new StoredPlottingObject(ACTIVE_PLOT);
					} else {
						oneDData.plot(ACTIVE_PLOT);
					}
				}

				set1DfittingActivatedState(true);
			}
		});

		calibrateButton = new Button(progress, SWT.NONE);
		calibrateButton.setText("Calibrate");
		calibrateButton.setEnabled(false);
		calibrateButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		calibrateButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				UIJob uiJob = new UIJob("Calibration") {
					@Override
					public IStatus runInUIThread(IProgressMonitor monitor) {
						runCalibration();
						return Status.OK_STATUS;
					}
				};
				uiJob.schedule();
			}
		});
	}

	private void storePeaks() {
		if (pv == null) {
			return;
		}
		bean = pv.getGUIInfo();
		if (bean == null) {
			return;
		}

		Serializable dataEntery;
		if (bean.containsKey(GuiParameters.FITTEDPEAKS)) {
			peaks.clear();
			dataEntery = bean.get(GuiParameters.FITTEDPEAKS);
			if (dataEntery instanceof ArrayList<?>) {
				for (Object p : (ArrayList<?>) dataEntery) {
					if (p instanceof APeak) {
						peaks.add((APeak) p);
					}
				}
			}
		}

		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				calibrateButton.setEnabled(peaks.size() >= 2);
			}
		});
	}

	protected void findViewAndDetermineMode(String et) {
		int bo = 0;
		IWorkbenchWindow page = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

		if (pv != null) {
			pv.deleteDataObserver(this);
		}

		if (page != null) {
			// if in SAXS perspective
			// TODO choices should be in a clever object
			if ("SAXS".equals(et)) {

				bo = (10);
				currentMode = et;
				ACTIVE_PLOT = SAXS_PLOT_NAME;

			} else { // if (et == ExperimentType.WAXS)

				bo = (2);
				currentMode = et;
				ACTIVE_PLOT = WAXS_PLOT_NAME;

			}

			final int boo = bo;
			page.getShell().getDisplay().asyncExec(new Runnable() {

				@Override
				public void run() {
					braggOrder.setSelection(boo);
				}
			});
			
			pv = getPlotViewOfView(ACTIVE_PLOT);
			if (pv == null) {
				logger.warn("no plotview found for " + et);
				page.getShell().getDisplay().asyncExec(new Runnable() {

					@Override
					public void run() {
						fittingButton.setEnabled(false);
						sectorButton.setEnabled(false);
						calibrateButton.setEnabled(false);
					}
				});
				return;
			}

			pv.addDataObserver(this);
			determineMode();
		}
	}

	private void determineMode() {
		if (pv.getMainPlotter().getMode() == PlottingMode.TWOD) {
			originalData2D = true;
		} else {
			originalData2D = false;
		}

		oneDData = null;
		twoDData = null;

		final boolean is2Dtemp = originalData2D;
		pv.getSite().getShell().getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				fittingButton.setEnabled(true);
				sectorButton.setEnabled(is2Dtemp);
			}
		});
	}

	protected void runJythonCommand() {
	}

	private void setupGUI() {
		for (String calibrant : cal2peaks.keySet()) {
			standard.add(calibrant);
		}
		standard.select(0);

		braggOrder.setSelection(0);
	}

	@Override
	public void setFocus() {
		logger.debug("setting focus");
	}

	@Override
	public void update(Object source, Object arg) {

		if (arg instanceof DataBean) {
			if (!weHaveOnePending) {
				DataBean bean = (DataBean) arg;
				logger.debug("new data on plot view: {}", bean);
				determineMode();
			} else {
				weHaveOnePending = false;
			}
		}
		if (arg instanceof GuiUpdate) {
			GuiUpdate guiUpdate = (GuiUpdate) arg;
			if (guiUpdate.getGuiName().contains(GUI_PLOT_NAME)) {
				bean = guiUpdate.getGuiData();
				if (bean.containsKey(GuiParameters.ROIDATA)) {
					Object obj = bean.get(GuiParameters.ROIDATA);
					if (obj instanceof SectorROI) {
						SectorROI sectorROI = (SectorROI) obj;
						double[] radii = sectorROI.getRadii();
						disttobeamstop = radii[0];
					}
				}
				if (bean.containsKey(GuiParameters.FITTEDPEAKS)) {
					storePeaks();
				}
				if (bean.containsKey(GuiParameters.CALIBRATIONFUNCTIONNCD)) {
					Serializable bd = bean.get(GuiParameters.CALIBRATIONFUNCTIONNCD);

					if (bd != null && bd instanceof CalibrationResultsBean) {
						CalibrationResultsBean crb = (CalibrationResultsBean) bd;
						updateCalibrationResults(crb);
					}
				}
			}
		}
	}

	protected void updateCalibrationResults(CalibrationResultsBean crb) {
		calibrationPeakList.clear();
		if (crb.containsKey(currentMode)) {
			calibrationPeakList.addAll(crb.getPeakList(currentMode));
			final double dist = crb.getMeanCameraLength(currentMode) / 1000;
			if (crb.getFuction(currentMode) != null) {
				final double mVal = crb.getFuction(currentMode).getParameterValue(0);
				final double cVal = crb.getFuction(currentMode).getParameterValue(1);

				PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

					@Override
					public void run() {
						gradient.setText(String.format("%5.5g",mVal));
						intercept.setText(String.format("%3.5f",cVal));
						cameralength.setText(String.format("%3.2f",dist));
						calTable.refresh();
					}
				});
			}
		}
	}

	private PlotView getPlotViewOfView(String currentView) {
		IWorkbenchWindow page = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage[] pages = page.getPages();
		if (pages.length > 0) {
			if (pages[0] == null) {
				return null;
			}

			IViewPart view = pages[0].findView(currentView);
			if (view == null) {
				return null;
			}
			return (PlotView) view;
		}
		return null;
	}

	private DataSetPlotter getMainPlotterofView(String currentView) {
		return getPlotViewOfView(currentView).getMainPlotter();
	}

	private void runRadialIntegrationAction() {

		if (pv == null) {
			return;
		}

		if (pv.getMainPlotter().getMode() == PlottingMode.TWOD && originalData2D) {

			twoDData = new StoredPlottingObject(ACTIVE_PLOT);

			ISidePlot sidePlot = getOurSidePlotView().getActivePlot();

			if (!(sidePlot instanceof SectorProfile)) {

				IContributionItem[] items = pv.getViewSite().getActionBars().getToolBarManager().getItems();
				for (IContributionItem item : items) {
					if (item instanceof ActionContributionItem
							&& item.getId() != null
							&& item.getId().equalsIgnoreCase(
									"uk.ac.diamond.scisoft.analysis.rcp.plotting.sideplot.SectorProfileAction")) {
						IAction act = ((ActionContributionItem) item).getAction();
						act.run();
						break;
					}
				}
			}
			// check to see is there is an existing sectorROI
			GuiBean guiinfo = pv.getGUIInfo();
			if (guiinfo.containsKey(GuiParameters.ROIDATA)) {
				if (guiinfo.get(GuiParameters.ROIDATA) instanceof SectorROI) {
					return;
				}
			}

			GuiBean guibean = new GuiBean();
			SectorROI sroi = new SectorROI(); // TODO better placement
			sroi.setPlot(true);
			guibean.put(GuiParameters.ROIDATA, sroi);
			getOurSidePlotView().updateGUI(guibean);
		}
	}

	private SidePlotView getOurSidePlotView() {
		IWorkbenchWindow page = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage[] pages = page.getPages();
		if (pages[0] == null) {
			return null;
		}

		IViewReference viewRef = pages[0].findViewReference("uk.ac.diamond.scisoft.analysis.rcp.views.SidePlotView",
				pv.getTitle());
		if (viewRef == null) {
			return null;
		}

		SidePlotView sidePV = (SidePlotView) viewRef.getView(true);
		return sidePV;
	}

	private void set1DfittingActivatedState(boolean state) {

		IContributionItem[] items = pv.getViewSite().getActionBars().getToolBarManager().getItems();
		for (IContributionItem item2 : items) {
			if (item2 instanceof ActionContributionItem
					&& item2.getId() != null
					&& item2.getId().equalsIgnoreCase(
							"uk.ac.diamond.scisoft.analysis.rcp.plotting.sideplot.Fitting1DAction")) {
				IAction fit1d = ((ActionContributionItem) item2).getAction();
				if (fit1d.isChecked() != state) {
					fit1d.run();
				}
			}
		}
	}

	private void runPushRadialProfileAction() {
		if (pv == null) {
			return;
		}

		if (pv.getMainPlotter().getMode() == PlottingMode.TWOD) {

			SidePlotView sidePV = getOurSidePlotView();
			ISidePlot sideplot = sidePV.getActivePlot();
			if (sideplot instanceof SectorProfile) {
				weHaveOnePending = true;
				((SectorProfile) sideplot).pushPlottingData(sidePV.getViewSite(), ACTIVE_PLOT, 0);
			}
		}
	}

	@Override
	public void dispose() {
		plotServer.deleteIObserver(this);
	}
	
	protected void setCalTable(ArrayList<CalibrationPeak> cpl) {
		calTable.setInput(cpl);
	}

}
