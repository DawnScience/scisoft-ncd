/*
 * Copyright 2011 Diamond Light Source Ltd. Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the
 * License.
 */

package uk.ac.diamond.scisoft.ncd.calibration.rcp.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.lang.math.NumberUtils;
import org.dawnsci.plotting.tools.diffraction.DiffractionDefaultMetadata;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.dawnsci.analysis.api.diffraction.DetectorProperties;
import org.eclipse.dawnsci.analysis.api.diffraction.DiffractionCrystalEnvironment;
import org.eclipse.dawnsci.analysis.api.fitting.functions.IPeak;
import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.tool.IToolPage;
import org.eclipse.dawnsci.plotting.api.tool.IToolPageSystem;
import org.eclipse.dawnsci.plotting.api.trace.ITrace;
import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
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
import org.eclipse.ui.statushandlers.StatusManager;
import org.jscience.mathematics.number.Real;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.crystallography.CalibrationFactory;
import uk.ac.diamond.scisoft.analysis.crystallography.CalibrationStandards;
import uk.ac.diamond.scisoft.ncd.calibration.rcp.Activator;
import uk.ac.diamond.scisoft.ncd.core.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.ncd.core.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.preferences.NcdConstants;

public class QAxisCalibrationBase extends ViewPart implements ISourceProviderListener {

	private static final Logger logger = LoggerFactory.getLogger(QAxisCalibrationBase.class);

	protected NcdCalibrationSourceProvider ncdCalibrationSourceProvider, ncdDetectorSourceProvider;
	protected NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider, ncdWaxsDetectorSourceProvider,
			ncdEnergySourceProvider;
	private ILoaderService loaderService;
	protected String GUI_PLOT_NAME = "Dataset Plot";

	private static CalibrationTable calTable;
	protected ArrayList<CalibrationPeak> calibrationPeakList = new ArrayList<CalibrationPeak>();

	protected static Text energy;
	protected static Combo standard;
	protected Button beamRefineButton;
	protected Group calibrationControls;

	protected ArrayList<IPeak> peaks = new ArrayList<IPeak>();

	private Button calibrateButton;
	protected static Text gradient, intercept;
	protected static Button inputQAxis;
	protected static Label cameralength;

	protected Double disttobeamstop;

	protected static HashMap<Unit<Length>, Button> unitSel;

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

	protected Amount<Energy> getEnergy() {
		String input = energy.getText();
		if (NumberUtils.isNumber(input)) {
			Double val = Double.valueOf(input);
			return Amount.valueOf(val, SI.KILO(NonSI.ELECTRON_VOLT));
		}
		return null;
	}

	protected Amount<ScatteringVectorOverDistance> getGradient() {
		// Ignoring units for now because of bugs in JScience unit parser
		String input = gradient.getText();
		try {
			Real realVal = Real.valueOf(input);
			Unit<ScatteringVectorOverDistance> unit = getUnit().inverse().divide(SI.MILLIMETRE)
					.asType(ScatteringVectorOverDistance.class);
			return Amount.valueOf(realVal.doubleValue(), unit);
		} catch (Exception e) {
			return null;
		}
	}

	protected Amount<ScatteringVector> getIntercept() {
		// Ignoring units for now because of bugs in JScience unit parser
		String input = intercept.getText();
		try {
			Real realVal = Real.valueOf(input);
			Unit<ScatteringVector> unit = getUnit().inverse().asType(ScatteringVector.class);
			return Amount.valueOf(realVal.doubleValue(), unit);
		} catch (Exception e) {
			return null;
		}
	}

	protected Unit<Length> getUnit() {
		for (Entry<Unit<Length>, Button> unitBtn : unitSel.entrySet())
			if (unitBtn.getValue().getSelection())
				return unitBtn.getKey();
		return null;
	}

	public QAxisCalibrationBase() {
	}

	@Override
	public void createPartControl(Composite parent) {

		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		ncdCalibrationSourceProvider = (NcdCalibrationSourceProvider) service
				.getSourceProvider(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service
				.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
		ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service
				.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		ncdWaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service
				.getSourceProvider(NcdProcessingSourceProvider.WAXSDETECTOR_STATE);
		ncdEnergySourceProvider = (NcdProcessingSourceProvider) service
				.getSourceProvider(NcdProcessingSourceProvider.ENERGY_STATE);
		loaderService = Activator.getService(ILoaderService.class);

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
		gpCalibrationResultsComposite.setLayout(new GridLayout(6, false));
		gpCalibrationResultsComposite.setText("Calibration Function");

		calibrationResultsDisplay(gpCalibrationResultsComposite);

		calibrationControls = new Group(calibrationResultsComposite, SWT.NONE);
		calibrationControls.setLayout(new GridLayout(4, false));
		calibrationControls.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
		calibrationControls.setText("Calibration Controls");

		Label lblStandard = new Label(calibrationControls, SWT.NONE);
		lblStandard.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		lblStandard.setText("Standard");

		standard = new Combo(calibrationControls, SWT.NONE);
		standard.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		beamRefineButton = new Button(calibrationControls, SWT.CHECK);
		beamRefineButton.setText("Refine Beam Position");
		beamRefineButton.setToolTipText("Run peak profile optimisation algorithm to refine beam center position");
		beamRefineButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));

		calibrateButton = new Button(calibrationControls, SWT.NONE);
		calibrateButton.setText("Calibrate");
		calibrateButton.setEnabled(true);
		calibrateButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		calibrateButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				runCalibration();
			}
		});

		ncdSaxsDetectorSourceProvider.addSourceProviderListener(this);
		ncdCalibrationSourceProvider.addSourceProviderListener(this);

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

		new Label(group, SWT.NONE).setText("Energy (keV)");
		energy = new Text(group, SWT.BORDER);
		energy.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false, 3, 1));
		energy.setToolTipText("Set the energy used in data collection");
		energy.addFocusListener(new FocusListener() {

			@Override
			public void focusLost(FocusEvent e) {
				Amount<Energy> tmpEnergy = getEnergy();
				Amount<Energy> savedEnergy = ncdEnergySourceProvider.getEnergy();
				if (tmpEnergy != null) {
					if (savedEnergy != null && tmpEnergy.equals(savedEnergy)) {
						return;
					}
					ncdEnergySourceProvider.setEnergy(tmpEnergy);
				} else {
					if (savedEnergy != null) {
						ncdEnergySourceProvider.setEnergy(savedEnergy);
					}
				}
			}

			@Override
			public void focusGained(FocusEvent e) {
				Amount<Energy> savedEnergy = ncdEnergySourceProvider.getEnergy();
				if (savedEnergy != null) {
					ncdEnergySourceProvider.setEnergy(savedEnergy);
				}
			}
		});

		Group unitGrp = new Group(group, SWT.NONE);
		unitGrp.setLayout(new GridLayout(2, false));
		unitGrp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		unitGrp.setToolTipText("Select q-axis calibration units");

		unitSel = new HashMap<Unit<Length>, Button>(2);
		Button unitButton = new Button(unitGrp, SWT.RADIO);
		unitButton.setText(NonSI.ANGSTROM.toString());
		unitButton.setToolTipText("calibrate q-axis in \u212bngstroms");
		unitButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		unitSel.put(NonSI.ANGSTROM, unitButton);

		unitButton = new Button(unitGrp, SWT.RADIO);
		unitButton.setText(SI.NANO(SI.METRE).toString());
		unitButton.setToolTipText("calibrate q-axis in nanometers");
		unitButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		unitSel.put(SI.NANO(SI.METRE), unitButton);

		inputQAxis = new Button(group, SWT.NONE);
		inputQAxis.setText("Override");
		inputQAxis.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
	}

	public void runCalibration() {
		if (checkCalibrationObjectInput()) {
			storePeaks();
			runJavaCommand();
		}
	}

	private IStatus ErrorDialog(String msg, Exception e) {
		logger.error(msg, e);
		Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, msg, e);
		StatusManager.getManager().handle(status, StatusManager.SHOW);
		return null;
	}

	private boolean checkCalibrationObjectInput() {
		try {
			IPlottingSystem<Composite> plotSystem = PlottingFactory.getPlottingSystem(GUI_PLOT_NAME);

			Collection<ITrace> traces = plotSystem.getTraces();
			if (traces == null || traces.isEmpty()) {
				String msg = "Please load calibration image into " + GUI_PLOT_NAME + " view.";
				IllegalArgumentException e = new IllegalArgumentException(msg);
				logger.error(msg, e);
				ErrorDialog("SCISOFT NCD: Error running q-axis calibration procedure", e);
				return false;
			}

			Collection<IRegion> regions = plotSystem.getRegions(RegionType.SECTOR);
			if (regions == null || regions.size() != 1) {
				String msg = "Please specify single sector region in the " + GUI_PLOT_NAME + " view.";
				IllegalArgumentException e = new IllegalArgumentException(msg);
				logger.error(msg, e);
				ErrorDialog("SCISOFT NCD: Error running q-axis calibration procedure", e);
				return false;
			}

			return true;

		} catch (Exception e) {
			String msg = "Error reading object parameters from " + GUI_PLOT_NAME
					+ " view. Try clearing existing regions an reload calibration image.";
			logger.error(msg, e);
			ErrorDialog(msg, e);
			return false;
		}
	}

	private void storePeaks() {
		IToolPage radialTool = PlottingFactory.getToolSystem(GUI_PLOT_NAME).getToolPage(
				"org.dawb.workbench.plotting.tools.radialProfileTool");
		IToolPage fittingTool = ((IToolPageSystem) radialTool.getToolPlottingSystem())
				.getToolPage("org.dawb.workbench.plotting.tools.fittingTool");
		if (fittingTool != null) {
			// Use adapters to avoid direct link which is weak.
			List<IPeak> fittedPeaks = (List<IPeak>) fittingTool.getAdapter(IPeak.class);

			if (fittedPeaks != null) {
				Collections.sort(fittedPeaks, new Compare());
				peaks = new ArrayList<IPeak>(fittedPeaks);
			}
		}
	}

	protected void runJavaCommand() {
	}

	private void setupGUI() {
		final CalibrationStandards cs = CalibrationFactory.getCalibrationStandards();
		for (String calibrant : cs.getCalibrantList()) {
			standard.add(calibrant);
		}
		standard.select(0);

		unitSel.values().iterator().next().setSelection(true);
	}

	@Override
	public void setFocus() {
		getViewSite().getShell().setFocus();
	}

	protected void updateCalibrationResults() {
		updateCalibrationResults(ncdSaxsDetectorSourceProvider.getSaxsDetector());
	}

	protected void updateCalibrationResults(final String currentDetector) {

		// Check if the view was disposed
		if (gradient == null || gradient.isDisposed())
			return;

		calibrationPeakList.clear();

		calTable.setInput(calibrationPeakList);
		gradient.setText("--");
		intercept.setText("--");
		cameralength.setText("--");
		if (ncdCalibrationSourceProvider.getNcdDetectors().containsKey(currentDetector)) {
		}
		ArrayList<CalibrationPeak> peakList = ncdCalibrationSourceProvider.getPeakList(currentDetector);
		if (peakList != null) {
			calibrationPeakList.addAll(peakList);
		}

		final Unit<Length> units = ncdCalibrationSourceProvider.getUnit(currentDetector);
		final Amount<ScatteringVectorOverDistance> amountGrad = ncdCalibrationSourceProvider
				.getGradient(currentDetector);
		final Amount<ScatteringVector> amountIntercept = ncdCalibrationSourceProvider.getIntercept(currentDetector);
		if (amountGrad != null && amountIntercept != null) {

			PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					gradient.setText(amountGrad.toString());
					intercept.setText(amountIntercept.toString());
					Amount<Length> mcl = ncdCalibrationSourceProvider.getMeanCameraLength(currentDetector);
					if (mcl != null) {
						cameralength.setText(mcl.to(SI.METRE).toString());
					}
					for (Button unitBtn : unitSel.values()) {
						unitBtn.setSelection(false);
					}
					if (units == null) {
						unitSel.get(NcdConstants.DEFAULT_UNIT).setSelection(true);
					} else {
						unitSel.get(units).setSelection(true);
					}
					calTable.setInput(calibrationPeakList);
					calTable.refresh();

					// update locked diffraction metadata in Diffraction tool
					IPlottingSystem<Composite> plotSystem = PlottingFactory.getPlottingSystem(GUI_PLOT_NAME);
					IDiffractionMetadata lockedMeta = loaderService.getLockedDiffractionMetaData();
					if (lockedMeta == null) {
						Collection<ITrace> traces = plotSystem.getTraces();
						if (traces != null && !traces.isEmpty()) {
							int[] datashape = traces.iterator().next().getData().getShape();
							loaderService.setLockedDiffractionMetaData(DiffractionDefaultMetadata
									.getDiffractionMetadata(datashape));
						} else {
							logger.info("SCISOFT NCD: Couldn't find calibration image for configuring diffraction metadata");
							return;
						}
					}
					try {
						DetectorProperties detectorProperties = loaderService.getLockedDiffractionMetaData()
								.getDetector2DProperties();
						DiffractionCrystalEnvironment crystalProperties = loaderService.getLockedDiffractionMetaData()
								.getDiffractionCrystalEnvironment();

						Collection<IRegion> sectorRegions = plotSystem.getRegions(RegionType.SECTOR);
						if (sectorRegions != null && sectorRegions.size() == 1) {
							final SectorROI sroi = (SectorROI) sectorRegions.iterator().next().getROI();
							double[] cp = sroi.getPoint();
							detectorProperties.setBeamCentreCoords(cp);
						}

						Amount<Length> pxSize = ncdCalibrationSourceProvider.getNcdDetectors().get(currentDetector)
								.getPxSize();
						detectorProperties.setHPxSize(pxSize.doubleValue(SI.MILLIMETRE));
						detectorProperties.setVPxSize(pxSize.doubleValue(SI.MILLIMETRE));

						Amount<Energy> energy = ncdEnergySourceProvider.getEnergy();
						if (energy != null) {
							crystalProperties.setWavelengthFromEnergykeV(energy.doubleValue(SI
									.KILO(NonSI.ELECTRON_VOLT)));
						}
						if (mcl != null) {
							detectorProperties.setDetectorDistance(mcl.doubleValue(SI.MILLIMETRE));
						}
					} catch (Exception e) {
						logger.info("caught exception updating IDiffractionMetadata on " + GUI_PLOT_NAME
								+ ". Life goes on. (In GDA the IDiffractionMetadata is likely to be correct already.)");
					}
				}
			});
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
		for (Object key : sourceValuesByName.keySet()) {
			if (key instanceof String) {
				String name = (String) key;
				sourceChanged(sourcePriority, name, sourceValuesByName.get(name));
			}
		}
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, Object sourceValue) {
		if (sourceName.equals(NcdCalibrationSourceProvider.CALIBRATION_STATE)) {
			if (sourceValue instanceof CalibrationResultsBean) {
				updateCalibrationResults();
			}
		}

		if (sourceName.equals(NcdProcessingSourceProvider.ENERGY_STATE)) {
			if (sourceValue instanceof Amount<?>) {
				Double val = ((Amount<Energy>) sourceValue).doubleValue(SI.KILO(NonSI.ELECTRON_VOLT));
				energy.setText(val.toString());
			}
		}

		if (sourceName.equals(NcdProcessingSourceProvider.SAXSDETECTOR_STATE)) {
			if (sourceValue instanceof String) {
				updateCalibrationResults();
			}
		}
	}
}
