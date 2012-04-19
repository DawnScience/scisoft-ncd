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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

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
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.PlotServer;
import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.fitting.functions.APeak;
import uk.ac.diamond.scisoft.analysis.plotserver.AxisMapBean;
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
import uk.ac.diamond.scisoft.analysis.roi.MaskingBean;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.data.HKL;
import uk.ac.diamond.scisoft.ncd.preferences.NcdConstants;

public class QAxisCalibrationBase extends ViewPart implements IObserver {

	private static final Logger logger = LoggerFactory.getLogger(QAxisCalibrationBase.class);

	private Unit<Length> NANOMETER = SI.NANO(SI.METER);
	
	private CalibrationTable calTable;
	protected ArrayList<CalibrationPeak> calibrationPeakList = new ArrayList<CalibrationPeak>();

	protected LinkedHashMap<String, LinkedHashMap<HKL, Amount<Length>> > cal2peaks;
	

	protected Combo standard;
	protected Spinner braggOrder;
	private Button sectorButton;
	private Button fittingButton;
	protected Button beamRefineButton;
	private PlotServer plotServer;
	protected Group gpSelectMode, calibrationControls;
	protected Label lblN;


	protected StoredPlottingObject oneDData;
	protected StoredPlottingObject twoDData;

	protected ArrayList<APeak> peaks = new ArrayList<APeak>();

	private Button calibrateButton;
	protected Text gradient;

	protected Text intercept;
	protected Text cameralength, cameralengthErr;

	protected Double disttobeamstop;

	protected Button[] detTypes;

	protected HashMap<String, Unit<Length>> unitScale;
	protected HashMap<String, Button> unitSel;

	protected String currentMode = NcdConstants.detChoices[0];
	
	protected boolean originalData2D = true;

	public static String SAXS_PLOT_NAME = "uk.ac.gda.client.ncd.saxsview";
	public static String WAXS_PLOT_NAME = "uk.ac.gda.client.ncd.waxsview";
	public static String GUI_PLOT_NAME = "Saxs Plot";
	protected String ACTIVE_PLOT;
	protected PlotView pv;

	private boolean weHaveOnePending = false;

	protected class StoredPlottingObject {
		private IDataset dataset, axis;
		private BooleanDataset mask;
		private SectorROI sroi;
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

		public BooleanDataset getMask() {
			return mask;
		}
		
		public void setMask(BooleanDataset mask) {
			this.mask = (BooleanDataset) mask.clone();
		}
		
		public SectorROI getROI() {
			return sroi;
		}
		
		public void setROI(SectorROI sroi) {
			this.sroi = sroi.copy();
		}
		
		private IDataset get1DAxis(String plotName) {
			DataSetPlotter mp = getMainPlotterofView(plotName);
			if (mp == null) {
				return null;
			}
			return mp.getXAxisValues().get(0).toDataset();
		}

		public IDataset getStoredDataset() {
			return dataset;
		}

		public void plot() {
			plot(plotViewName);
		}

		protected void plot(String plotName) {

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
		cal2peaks = new LinkedHashMap<String, LinkedHashMap<HKL, Amount<Length>> >();
		LinkedHashMap<HKL, Amount<Length>> hkl2peaks = new LinkedHashMap<HKL, Amount<Length>>();
		
		hkl2peaks.put(new HKL(0, 0, 1), Amount.valueOf(67.0, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 2), Amount.valueOf(33.5, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 3), Amount.valueOf(22.3, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 4), Amount.valueOf(16.75, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 5), Amount.valueOf(13.4, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 6), Amount.valueOf(11.6, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 7), Amount.valueOf(9.6, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 8), Amount.valueOf(8.4, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 9), Amount.valueOf(7.4, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 10), Amount.valueOf(6.7, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 11), Amount.valueOf(6.1, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 12), Amount.valueOf(5.6, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 13), Amount.valueOf(5.15, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 15), Amount.valueOf(4.46, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 20), Amount.valueOf(3.35, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 21), Amount.valueOf(3.2, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 22), Amount.valueOf(3.05, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 30), Amount.valueOf(2.2, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 35), Amount.valueOf(1.9, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 41), Amount.valueOf(1.6, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 52), Amount.valueOf(1.3, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 71), Amount.valueOf(0.95, NANOMETER));
		cal2peaks.put("Collagen Wet", (LinkedHashMap<HKL, Amount<Length>>) hkl2peaks.clone()); // SAXS
		hkl2peaks.clear();
		
		hkl2peaks.put(new HKL(0, 0, 1),  Amount.valueOf(65.3, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 2),  Amount.valueOf(32.7, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 3),  Amount.valueOf(21.8, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 4),  Amount.valueOf(16.3, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 5),  Amount.valueOf(13.1, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 6),  Amount.valueOf(10.9, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 7),  Amount.valueOf(9.33, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 8),  Amount.valueOf(8.16, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 9),  Amount.valueOf(7.26, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 10), Amount.valueOf(6.53, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 11), Amount.valueOf(5.94, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 12), Amount.valueOf(5.44, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 13), Amount.valueOf(5.02, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 15), Amount.valueOf(4.35, NANOMETER));
		cal2peaks.put("Collagen Dry", (LinkedHashMap<HKL, Amount<Length>>) hkl2peaks.clone()); // SAXS
		hkl2peaks.clear();
		
		hkl2peaks.put(new HKL(0, 0, 1),  Amount.valueOf(5.838     , NANOMETER)); 
		hkl2peaks.put(new HKL(0, 0, 2),  Amount.valueOf(2.919     , NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 3),  Amount.valueOf(1.946     , NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 4),  Amount.valueOf(1.4595    , NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 5),  Amount.valueOf(1.1676    , NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 6),  Amount.valueOf(0.973     , NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 7),  Amount.valueOf(0.834     , NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 8),  Amount.valueOf(0.72975   , NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 9),  Amount.valueOf(0.64866667, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 10), Amount.valueOf(0.5838    , NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 11), Amount.valueOf(0.53072727, NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 12), Amount.valueOf(0.4865    , NANOMETER));
		hkl2peaks.put(new HKL(0, 0, 13), Amount.valueOf(0.44907692, NANOMETER));
		cal2peaks.put("Ag Behenate", (LinkedHashMap<HKL, Amount<Length>>) hkl2peaks.clone()); // SAXS
		hkl2peaks.clear();
		        
		hkl2peaks.put(new HKL(1, 1, 0), Amount.valueOf(0.4166, NANOMETER));
		hkl2peaks.put(new HKL(2, 0, 0), Amount.valueOf(0.378 , NANOMETER));
		hkl2peaks.put(new HKL(2, 1, 0), Amount.valueOf(0.3014, NANOMETER));
		hkl2peaks.put(new HKL(0, 2, 0), Amount.valueOf(0.249 , NANOMETER));
		cal2peaks.put("HDPE", (LinkedHashMap<HKL, Amount<Length>>) hkl2peaks.clone()); // WAXS
		hkl2peaks.clear();
		
		hkl2peaks.put(new HKL(1, 1, 1), Amount.valueOf(0.31355, NANOMETER));
		hkl2peaks.put(new HKL(2, 2, 0), Amount.valueOf(0.19201, NANOMETER));
		hkl2peaks.put(new HKL(3, 1, 1), Amount.valueOf(0.16374, NANOMETER));
		hkl2peaks.put(new HKL(2, 2, 2), Amount.valueOf(0.15677, NANOMETER));
		hkl2peaks.put(new HKL(4, 0, 0), Amount.valueOf(0.13577, NANOMETER));
		hkl2peaks.put(new HKL(3, 3, 1), Amount.valueOf(0.12459, NANOMETER));
		hkl2peaks.put(new HKL(4, 2, 2), Amount.valueOf(0.11085, NANOMETER));
		hkl2peaks.put(new HKL(3, 3, 3), Amount.valueOf(0.10451, NANOMETER));
		hkl2peaks.put(new HKL(5, 1, 1), Amount.valueOf(0.10451, NANOMETER));
		hkl2peaks.put(new HKL(4, 4, 0), Amount.valueOf(0.09600, NANOMETER));
		hkl2peaks.put(new HKL(5, 3, 1), Amount.valueOf(0.09179, NANOMETER));
		hkl2peaks.put(new HKL(4, 4, 2), Amount.valueOf(0.09051, NANOMETER));
		hkl2peaks.put(new HKL(6, 2, 0), Amount.valueOf(0.08586, NANOMETER));
		hkl2peaks.put(new HKL(5, 3, 3), Amount.valueOf(0.08281, NANOMETER));
		hkl2peaks.put(new HKL(6, 2, 2), Amount.valueOf(0.08187, NANOMETER));
		hkl2peaks.put(new HKL(4, 4, 4), Amount.valueOf(0.07838, NANOMETER));
		hkl2peaks.put(new HKL(7, 1, 1), Amount.valueOf(0.07604, NANOMETER));
		hkl2peaks.put(new HKL(5, 5, 1), Amount.valueOf(0.07604, NANOMETER));
		hkl2peaks.put(new HKL(6, 4, 2), Amount.valueOf(0.07257, NANOMETER));		
		cal2peaks.put("Silicon", (LinkedHashMap<HKL, Amount<Length>>) hkl2peaks.clone()); // WAXS
		hkl2peaks.clear();
		
		unitScale = new HashMap<String, Unit<Length>>(2);
		unitScale.put(NcdConstants.unitChoices[0], NonSI.ANGSTROM);
		unitScale.put(NcdConstants.unitChoices[1], NANOMETER);
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
		gpCameraDistance.setLayout(new GridLayout(5, false));
		gpCameraDistance.setText("Camera Length");

		Label disLab = new Label(gpCameraDistance, SWT.NONE);
		disLab.setText("Camera Distance");

		cameralength = new Text(gpCameraDistance, SWT.READ_ONLY | SWT.FILL | SWT.CENTER);
		cameralength.setText("--");
		cameralength.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		disLab = new Label(gpCameraDistance, SWT.NONE);
		disLab.setText("+/-");

		cameralengthErr = new Text(gpCameraDistance, SWT.READ_ONLY | SWT.FILL | SWT.CENTER);
		cameralengthErr.setText("--");
		cameralengthErr.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		disLab = new Label(gpCameraDistance, SWT.NONE);
		disLab.setText("m");

		calibrationControls = new Group(calibrationResultsComposite, SWT.NONE);
		calibrationControls.setLayout(new GridLayout(3, false));
		calibrationControls.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		calibrationControls.setText("Calibration Controls");

		Label lblStandard = new Label(calibrationControls, SWT.NONE);
		lblStandard.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, true));
		lblStandard.setText("Standard");

		standard = new Combo(calibrationControls, SWT.NONE);
		standard.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

		Group unitGrp = new Group(calibrationControls, SWT.NONE);
		unitGrp.setLayout(new GridLayout(2, false));
		unitGrp.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		unitGrp.setToolTipText("Select q-axis calibration units");
		
		unitSel = new HashMap<String, Button>(2);
		unitSel.put(NcdConstants.unitChoices[0], new Button(unitGrp, SWT.RADIO));
		unitSel.get(NcdConstants.unitChoices[0]).setText(NcdConstants.unitChoices[0]);
		unitSel.get(NcdConstants.unitChoices[0]).setToolTipText("calibrate q-axis in Ångstroms");
		unitSel.get(NcdConstants.unitChoices[0]).setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, true));
		
		unitSel.put(NcdConstants.unitChoices[1], new Button(unitGrp, SWT.RADIO));
		unitSel.get(NcdConstants.unitChoices[1]).setText(NcdConstants.unitChoices[1]);
		unitSel.get(NcdConstants.unitChoices[1]).setToolTipText("calibrate q-axis in nanometers");
		unitSel.get(NcdConstants.unitChoices[1]).setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, true));
		
		lblN = new Label(calibrationControls, SWT.NONE);
		lblN.setToolTipText("n in Braggs law");
		lblN.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, true, 2, 1));
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
		runJavaCommand();
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

		detTypes = new Button[NcdConstants.detChoices.length];
		int i = 0;
		for (String det : NcdConstants.detChoices) {
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
		sectorButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
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
		calibrateButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 2, 1));
		calibrateButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				runCalibration();
			}
		});
	}

	private void storePeaks() {
		if (pv == null) {
			return;
		}
		GuiBean bean = pv.getGUIInfo();
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
		IWorkbenchWindow page = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

		if (pv != null) {
			pv.deleteDataObserver(this);
		}

		if (page != null) {
			// if in SAXS perspective
			// TODO choices should be in a clever object
			if ("SAXS".equals(et)) {

				currentMode = et;
				ACTIVE_PLOT = SAXS_PLOT_NAME;

			} else { // if (et == ExperimentType.WAXS)

				currentMode = et;
				ACTIVE_PLOT = WAXS_PLOT_NAME;

			}

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

		//oneDData = null;
		//twoDData = null;

		final boolean is2Dtemp = originalData2D;
		pv.getSite().getShell().getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				fittingButton.setEnabled(true);
				sectorButton.setEnabled(is2Dtemp);
			}
		});
	}

	protected void runJavaCommand() {
	}
	
	@Deprecated
	protected void runJythonCommand() {
	}

	private void setupGUI() {
		for (String calibrant : cal2peaks.keySet()) {
			standard.add(calibrant);
		}
		standard.select(0);

		braggOrder.setSelection(0);
		
		unitSel.values().iterator().next().setSelection(true);
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
				GuiBean bean = guiUpdate.getGuiData();
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
		
		findViewAndDetermineMode(currentMode);
	}

	protected void updateCalibrationResults(CalibrationResultsBean crb) {
		calibrationPeakList.clear();
		if (crb.containsKey(currentMode)) {
			calibrationPeakList.addAll(crb.getPeakList(currentMode));
			final double dist = crb.getMeanCameraLength(currentMode) / 1000;
			final String units = crb.getUnit(currentMode);
			if (crb.getFuction(currentMode) != null) {
				final double mVal = crb.getFuction(currentMode).getParameterValue(0);
				final double cVal = crb.getFuction(currentMode).getParameterValue(1);

				PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

					@Override
					public void run() {
						gradient.setText(String.format("%5.5g",mVal));
						intercept.setText(String.format("%3.5f",cVal));
						cameralength.setText(String.format("%3.3f",dist));
						for (Button unitBtn : unitSel.values())
							unitBtn.setSelection(false);
						if (units == null) 
							unitSel.get(NcdConstants.DEFAULT_UNIT).setSelection(true);
						else
							unitSel.get(units).setSelection(true);
						calTable.setInput(calibrationPeakList);
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
			if (guiinfo.containsKey(GuiParameters.MASKING)) {
				if (guiinfo.get(GuiParameters.MASKING) instanceof MaskingBean) {
					MaskingBean mb = (MaskingBean)guiinfo.get(GuiParameters.MASKING);
					twoDData.setMask(mb.getMask());
				}
			}
			
			if (guiinfo.containsKey(GuiParameters.ROIDATA)) {
				if (guiinfo.get(GuiParameters.ROIDATA) instanceof SectorROI) {
					SectorROI sroi = (SectorROI)guiinfo.get(GuiParameters.ROIDATA);
					twoDData.setROI(sroi);
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
	
	protected String getUnitName() {
		for (Entry<String,Button> unitBtn : unitSel.entrySet())
			if (unitBtn.getValue().getSelection())
				return unitBtn.getKey();
		return null;
	}

	protected Unit<Length> getUnitScale() {
		for (Entry<String,Button> unitBtn : unitSel.entrySet())
			if (unitBtn.getValue().getSelection())
				return unitScale.get(unitBtn.getKey());
		return null;
	}
}
