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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.dawb.common.ui.plot.AbstractPlottingSystem;
import org.dawb.common.ui.plot.PlottingFactory;
import org.dawb.common.ui.plot.region.IRegion.RegionType;
import org.dawb.common.ui.plot.tool.IToolPage;
import org.dawb.common.ui.plot.trace.IImageTrace;
import org.dawb.workbench.plotting.tools.FittingTool;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISourceProviderListener;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.services.ISourceProviderService;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.fitting.functions.IPeak;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.data.HKL;
import uk.ac.diamond.scisoft.ncd.preferences.NcdConstants;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class QAxisCalibrationBase extends ViewPart implements ISourceProviderListener {

	private static final Logger logger = LoggerFactory.getLogger(QAxisCalibrationBase.class);

	protected NcdCalibrationSourceProvider ncdCalibrationSourceProvider, ncdDetectorSourceProvider;
	protected NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider,  ncdWaxsDetectorSourceProvider, ncdEnergySourceProvider;
	
	protected Unit<Length> NANOMETER = SI.NANO(SI.METER);
	
	private static CalibrationTable calTable;
	protected ArrayList<CalibrationPeak> calibrationPeakList = new ArrayList<CalibrationPeak>();

	protected LinkedHashMap<String, LinkedHashMap<HKL, Amount<Length>> > cal2peaks;
	
	protected static Text energy;
	protected static Combo standard;
	protected Button beamRefineButton;
	protected Group gpSelectMode, calibrationControls;


	protected StoredPlottingObject twoDData;

	protected ArrayList<IPeak> peaks = new ArrayList<IPeak>();

	private Button calibrateButton;
	protected static Text gradient,intercept;
	protected static Button inputQAxis;
	protected static Label cameralength;

	protected Double disttobeamstop;

	protected Button[] detTypes;

	protected static HashMap<Unit<Length>, Button> unitSel;

	protected String currentMode = NcdConstants.detChoices[0];
	
	private static class Compare implements Comparator<IPeak> {

		@Override
		public int compare(IPeak o1, IPeak o2) {
			if (o1.getPosition() > o2.getPosition()) {
				return 1;
			}
			if (o1.getPosition() < o2.getPosition()) {
				return -1;
			}
			return 0;
		}

	}

	protected class StoredPlottingObject {
		private IDataset dataset;
		private BooleanDataset mask;
		private SectorROI sroi;

		public StoredPlottingObject() {
			try {
				AbstractPlottingSystem plotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
				IImageTrace trace = (IImageTrace) plotSystem.getTraces().iterator().next();
				dataset = trace.getData();
				mask = (BooleanDataset) trace.getMask();
				sroi = (SectorROI) plotSystem.getRegions(RegionType.SECTOR).iterator().next().getROI();
			} catch (Exception e) {
				logger.error("Error reading input data", e);
			}
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
		
		public IDataset getStoredDataset() {
			return dataset;
		}
	}

	protected Double getEnergy() {
		String input = energy.getText();
		if (input!= null) {
			try {
				Double val = Double.valueOf(energy.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: energy was not set";
				logger.info(msg);
			}
		}
		return null;
	}

	protected Double getGradient() {
		String input = gradient.getText();
		if (input != null) {
			try {
				Double val = Double.valueOf(gradient.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Error reading q-axis calibration gradient";
				logger.error(msg);
			}
		}
		return null;
	}
	
	protected Double getIntercept() {
		String input = intercept.getText();
		if (input != null) {
			try {
				Double val = Double.valueOf(intercept.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Error reading q-axis calibration intercept";
				logger.error(msg);
			}
		}
		return null;
	}
	
	protected Unit<Length> getUnit() {
		for (Entry<Unit<Length>, Button> unitBtn : unitSel.entrySet())
			if (unitBtn.getValue().getSelection())
				return unitBtn.getKey();
		return null;
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
		hkl2peaks.put(new HKL(0, 0, 14), Amount.valueOf(4.66, NANOMETER));
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
	}

	@Override
	public void createPartControl(Composite parent) {

		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		ncdCalibrationSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
		ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		ncdWaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXSDETECTOR_STATE);
		ncdEnergySourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ENERGY_STATE);
		
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
		gpCalibrationResultsComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
		gpCalibrationResultsComposite.setLayout(new GridLayout(7, false));
		gpCalibrationResultsComposite.setText("Calibration Function");

		calibrationResultsDisplay(gpCalibrationResultsComposite);

		calibrationControls = new Group(calibrationResultsComposite, SWT.NONE);
		calibrationControls.setLayout(new GridLayout(3, false));
		calibrationControls.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		calibrationControls.setText("Calibration Controls");

		Label lblStandard = new Label(calibrationControls, SWT.NONE);
		lblStandard.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, true));
		lblStandard.setText("Standard");

		standard = new Combo(calibrationControls, SWT.NONE);
		standard.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));

		Group progress = new Group(calibrationResultsComposite, SWT.NONE);
		progress.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));
		progress.setLayout(new GridLayout(1, false));
		progress.setText("Progress");

		ncdSaxsDetectorSourceProvider.addSourceProviderListener(this);
		ncdCalibrationSourceProvider.addSourceProviderListener(this);
		
		displayControlButtons(progress);
		setupGUI();
	}

	private void calibrationResultsDisplay(Group group) {
		Label mlab = new Label(group, SWT.NONE);
		mlab.setText("Gradient");

		gradient = new Text(group, SWT.CENTER);
		gradient.setText("--");
		gradient.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Label clab = new Label(group, SWT.NONE);
		clab.setText("Intercept");

		intercept = new Text(group, SWT.CENTER);
		intercept.setText("--");
		intercept.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		Label cmlab = new Label(group, SWT.NONE);
		cmlab.setText("Camera Length");
		cmlab.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));

		cameralength = new Label(group, SWT.NONE);
		cameralength.setAlignment(SWT.CENTER);
		cameralength.setText("--");
		cameralength.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Label disLab = new Label(group, SWT.NONE);
		disLab.setText("m");
		disLab.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
		
		new Label(group, SWT.NONE).setText("Energy (keV)");
		energy = new Text(group, SWT.BORDER);
		energy.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false, 3 ,1));
		energy.setToolTipText("Set the energy used in data collection");
		energy.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				ncdEnergySourceProvider.setEnergy(getEnergy());
			}
		});
		
		Group unitGrp = new Group(group, SWT.NONE);
		unitGrp.setLayout(new GridLayout(2, false));
		unitGrp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		unitGrp.setToolTipText("Select q-axis calibration units");
		
		unitSel = new HashMap<Unit<Length>, Button>(2);
		Button unitButton = new Button(unitGrp, SWT.RADIO);
		unitButton.setText(NonSI.ANGSTROM.toString());
		unitButton.setToolTipText("calibrate q-axis in Ångstroms");
		unitButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		unitSel.put(NonSI.ANGSTROM, unitButton);
		
		unitButton = new Button(unitGrp, SWT.RADIO);
		unitButton.setText(NANOMETER.toString());
		unitButton.setToolTipText("calibrate q-axis in nanometers");
		unitButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		unitSel.put(NANOMETER, unitButton);
		
		inputQAxis = new Button(group, SWT.NONE);
		inputQAxis.setText("Override");
		inputQAxis.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
	}

	public void runCalibration() {
		twoDData = new StoredPlottingObject();
		storePeaks();
		
		runJavaCommand();
	}

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
			i++;
		}

		calibrateButton = new Button(progress, SWT.NONE);
		calibrateButton.setText("Calibrate");
		calibrateButton.setEnabled(true);
		calibrateButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 2, 1));
		calibrateButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				runCalibration();
			}
		});
	}

	private void storePeaks() {
		AbstractPlottingSystem plotSystem = PlottingFactory.getPlottingSystem("Radial Profile");
		IToolPage fittingTool = plotSystem.getToolPage("org.dawb.workbench.plotting.tools.fittingTool");
		if (fittingTool != null && fittingTool instanceof FittingTool) {
			List<? extends IPeak> fittedPeaks = ((FittingTool) fittingTool).getFittedPeaks().getPeaks();
			Collections.sort(fittedPeaks, new Compare());
			if (peaks != null && peaks.size() > 0)
				peaks.clear();
			for (IPeak peak : fittedPeaks) {
				peaks.add(peak);
			}
		}
	}

	protected void runJavaCommand() {
	}
	
	private void setupGUI() {
		for (String calibrant : cal2peaks.keySet()) {
			standard.add(calibrant);
		}
		standard.select(0);

		unitSel.values().iterator().next().setSelection(true);
	}

	@Override
	public void setFocus() {
		logger.debug("setting focus");
	}

	protected void updateCalibrationResults() {
		
		currentMode = ncdSaxsDetectorSourceProvider.getSaxsDetector();
		
		calibrationPeakList.clear();
		
		calTable.setInput(calibrationPeakList);
		gradient.setText("--");
		intercept.setText("--");
		cameralength.setText("--");
		if (ncdCalibrationSourceProvider.getNcdDetectors().containsKey(currentMode)) {
			ArrayList<CalibrationPeak> peakList = ncdCalibrationSourceProvider.getPeakList(currentMode);
			if (peakList != null)
				calibrationPeakList.addAll(peakList);
			
			final Unit<Length> units = ncdCalibrationSourceProvider.getUnit(currentMode);
			if (ncdCalibrationSourceProvider.getFunction(currentMode) != null) {

				PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

					@Override
					public void run() {
						gradient.setText(String.format("%5.5g",ncdCalibrationSourceProvider.getFunction(currentMode).getParameterValue(0)));
						intercept.setText(String.format("%3.5f",ncdCalibrationSourceProvider.getFunction(currentMode).getParameterValue(1)));
						Amount<Length> mcl = ncdCalibrationSourceProvider.getMeanCameraLength(currentMode);
						if (mcl != null) {
							cameralength.setText(String.format("%3.3f", mcl.doubleValue(SI.MILLIMETER)));
						}
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

	protected void setCalTable(ArrayList<CalibrationPeak> cpl) {
		calTable.setInput(cpl);
	}
	
	protected Unit<Length> getUnitScale() {
		for (Entry<Unit<Length>, Button> unitBtn : unitSel.entrySet())
			if (unitBtn.getValue().getSelection())
				return unitBtn.getKey();
		return null;
	}

	@Override
	public void sourceChanged(int sourcePriority, @SuppressWarnings("rawtypes") Map sourceValuesByName) {
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, Object sourceValue) {
		if (sourceName.equals(NcdCalibrationSourceProvider.CALIBRATION_STATE)) {
			if (sourceValue instanceof CalibrationResultsBean)
				updateCalibrationResults();
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.SAXSDETECTOR_STATE)) {
			if (sourceValue instanceof String)
				updateCalibrationResults();
		}
	}
}
