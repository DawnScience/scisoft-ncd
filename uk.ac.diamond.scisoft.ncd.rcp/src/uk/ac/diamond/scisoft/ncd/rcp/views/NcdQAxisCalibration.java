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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.measure.quantity.Length;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optimization.ConvergenceChecker;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.SimplePointChecker;
import org.apache.commons.math3.optimization.direct.CMAESOptimizer;
import org.apache.commons.math3.random.Well19937a;
import org.dawb.common.ui.plot.AbstractPlottingSystem;
import org.dawb.common.ui.plot.AbstractPlottingSystem.ColorOption;
import org.dawb.common.ui.plot.PlotType;
import org.dawb.common.ui.plot.PlottingFactory;
import org.dawb.common.ui.plot.region.IRegion.RegionType;
import org.dawb.common.ui.plot.trace.ILineTrace;
import org.dawb.common.ui.plot.trace.ILineTrace.PointStyle;
import org.dawb.common.ui.plot.trace.ILineTrace.TraceType;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uncommons.maths.combinatorics.CombinationGenerator;

import uk.ac.diamond.scisoft.analysis.PlotServerProvider;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.fitting.Fitter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.CompositeFunction;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Gaussian;
import uk.ac.diamond.scisoft.analysis.fitting.functions.IPeak;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Parameter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.StraightLine;
import uk.ac.diamond.scisoft.analysis.optimize.GeneticAlg;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.calibration.CalibrationMethods;
import uk.ac.diamond.scisoft.ncd.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.data.HKL;
import uk.ac.diamond.scisoft.ncd.preferences.CalibrationPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;

public class NcdQAxisCalibration extends QAxisCalibrationBase {
	
	private IMemento memento;
	private String calibrant;
	
	private int cmaesLambda = 5;
	private double[] cmaesInputSigma = new double[] { 3.0, 3.0 };
	private int cmaesMaxIterations = 1000;
	private int cmaesCheckFeasableCount = 10;
	private ConvergenceChecker<PointValuePair> cmaesChecker = new SimplePointChecker<PointValuePair>(1e-4, 1e-2);
	
	protected String GUI_PLOT_NAME = "Dataset Plot";
	protected String ACTIVE_PLOT = "Dataset Plot";
	private AbstractPlottingSystem plottingSystem;

	private static final Logger logger = LoggerFactory.getLogger(NcdQAxisCalibration.class);
	
	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
	 this.memento = memento;
	 super.init(site, memento);
	}
	
	@Override
	public void createPartControl(Composite parent) {

		super.createPartControl(parent);
		
		calibrationControls.setLayout(new GridLayout(2, false));
		beamRefineButton = new Button(calibrationControls, SWT.CHECK);
		beamRefineButton.setText("Refine Beam Position");
		beamRefineButton.setToolTipText("Run peak profile optimisation algorithm to refine beam center position");
		beamRefineButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		
		restoreState();
		
		try {
	        this.plottingSystem = PlottingFactory.createPlottingSystem();
	        plottingSystem.setColorOption(ColorOption.NONE);
	        plottingSystem.setDatasetChoosingRequired(false);
		} catch (Exception e) {
			logger.error("Cannot locate any plotting systems!", e);
		}
		
		final Composite plot = new Composite(parent, SWT.NONE);
		plot.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		plot.setLayout(new FillLayout());
		try {
	        IActionBars wrapper = this.getViewSite().getActionBars();
			plottingSystem.createPlotPart(plot, "Calibration Plot", wrapper, PlotType.PT1D, this);

		} catch (Exception e) {
			logger.error("Error creating plot part.", e);
		}
		
		inputQAxis.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				MessageDialog calibrationDialogue = new MessageDialog(getSite().getShell(),
						"Override calibration data", null,
						"Do you want to override q-axis calibration data? (WARNING: The existing calibration peak list and camera distance values will be erased!)",
						MessageDialog.QUESTION,	new String[]{IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL}, 0);
				
				switch(calibrationDialogue.open()) {
				case 0: 
					CalibrationResultsBean crb = ncdCalibrationSourceProvider.getCalibrationResults();
					String det = ncdSaxsDetectorSourceProvider.getSaxsDetector();
					Double newGradient = getGradient();
					Double newIntercept = getIntercept();
					if (newGradient == null || newIntercept == null) {
						if (crb.containsKey(det))
							crb.clearData(det);
					} else {
						Parameter gradient = new Parameter(newGradient);
						Parameter intercept = new Parameter(newIntercept);
						StraightLine calibrationFunction = new StraightLine(new Parameter[] { gradient, intercept });
						crb.putCalibrationResult(det, calibrationFunction, null, null, getUnit());
					}
					ncdCalibrationSourceProvider.putCalibrationResult(crb);
					break;
				case 1:
					String saxsDet = ncdSaxsDetectorSourceProvider.getSaxsDetector();
					ncdSaxsDetectorSourceProvider.setSaxsDetector(saxsDet);
					break;
				}
			}
		});
	}

	@Override
	public void saveState(IMemento memento) {
		
		if (memento != null) {
			
			memento.putString(CalibrationPreferences.QAXIS_CURRENTMODE, currentMode);
			memento.putString(CalibrationPreferences.QAXIS_ACIVEPLOT, ACTIVE_PLOT);
			
			memento.putString(CalibrationPreferences.QAXIS_GRADIENT, gradient.getText());
			memento.putString(CalibrationPreferences.QAXIS_INTERCEPT, intercept.getText());
			memento.putString(CalibrationPreferences.QAXIS_CAMERALENGTH, cameralength.getText());
			
			memento.putInteger(CalibrationPreferences.QAXIS_STANDARD, standard.getSelectionIndex());
			
			Unit<Length> selUnit = SI.NANO(SI.METER);
			for (Entry<Unit<Length>, Button> unitBtn : unitSel.entrySet())
				if (unitBtn.getValue().getSelection()) {
					selUnit = unitBtn.getKey();
					memento.putString(CalibrationPreferences.QAXIS_UNITS, selUnit.toString());
					break;
				}
			
			Double energy = getEnergy();
			if (energy != null)
				memento.putFloat(CalibrationPreferences.QAXIS_ENERGY, energy.floatValue());
			
			if (!(calibrationPeakList.isEmpty())) {
				IMemento calibrationPeaksMemento = memento.createChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
				for (CalibrationPeak peak: calibrationPeakList) {
					IMemento calibrationPeakMemento = calibrationPeaksMemento.createChild(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
					calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_PEAKPOS, (float) peak.getPeakPos());
					calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_TWOTHETA, (float) peak.getTwoTheta());
					calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_DSPACING, (float) peak.getDSpacing().doubleValue(selUnit));
					HKL idx = peak.getReflection();
					calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_H, idx.getH());
					calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_K, idx.getK());
					calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_L, idx.getL());
				}
			}
			
			CalibrationResultsBean crb = (CalibrationResultsBean) ncdCalibrationSourceProvider.getCurrentState().get(NcdCalibrationSourceProvider.CALIBRATION_STATE);
			if (!crb.keySet().isEmpty()) {
				IMemento crbMemento = memento.createChild(CalibrationPreferences.QAXIS_CRB);
				for (String key : crb.keySet()) {
					IMemento crbDataMemento = crbMemento.createChild(CalibrationPreferences.QAXIS_CRBDATA, key);

					crbDataMemento.putFloat(CalibrationPreferences.QAXIS_GRADIENT, (float) crb.getFunction(key).getParameterValue(0));
					crbDataMemento.putFloat(CalibrationPreferences.QAXIS_INTERCEPT, (float) crb.getFunction(key).getParameterValue(1));

					Amount<Length> mcl = crb.getMeanCameraLength(key);
					if (mcl != null)
						crbDataMemento.putString(CalibrationPreferences.QAXIS_CAMERALENGTH, mcl.to(SI.METER).toString());
					
					ArrayList<CalibrationPeak> calPeaks = crb.getPeakList(key);
					if (calPeaks != null) {
						IMemento calibrationPeaksMemento = crbDataMemento.createChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
						for (CalibrationPeak peak : calPeaks) {
							IMemento calibrationPeakMemento = calibrationPeaksMemento.createChild(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
							calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_PEAKPOS,
									(float) peak.getPeakPos());
							calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_TWOTHETA,
									(float) peak.getTwoTheta());
							calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_DSPACING,
									(float) peak.getDSpacing().doubleValue(selUnit));
							HKL idx = peak.getReflection();
							calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_H, idx.getH());
							calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_K, idx.getK());
							calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_L, idx.getL());
						}
					}
				}
			}
			
			try {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IViewPart activePlot = page.findView(ACTIVE_PLOT);
				if (activePlot instanceof PlotView) {
					GuiBean guiinfo = ((PlotView)page.showView(ACTIVE_PLOT)).getGUIInfo();

					if (guiinfo.containsKey(GuiParameters.ROIDATA)) {
						if (guiinfo.get(GuiParameters.ROIDATA) instanceof SectorROI) {
							SectorROI intSector = (SectorROI)guiinfo.get(GuiParameters.ROIDATA);
							IMemento roiMemento = memento.createChild(CalibrationPreferences.QAXIS_ROI);
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROISR, (float) intSector.getRadius(0));
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIER, (float) intSector.getRadius(1));
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROISP, (float) intSector.getAngle(0));
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIEP, (float) intSector.getAngle(1));
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIPTX, (float) intSector.getPoint()[0]);
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIPTY, (float) intSector.getPoint()[1]);
						}
					}
				}
			} catch (PartInitException e) {
				logger.error("SCISOFT NCD Q-Axis Calibration: cannot save GUI bean information", e);
			}
		}
	}
	
	private void restoreState() {
		if (this.memento != null) {
			String tmp;
			Integer val;
			Float flt;
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_CURRENTMODE);
			if (tmp != null) currentMode = tmp;
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_ACIVEPLOT);
			if (tmp != null) ACTIVE_PLOT = tmp;
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_GRADIENT);
			if (tmp != null) gradient.setText(tmp);
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_INTERCEPT);
			if (tmp != null) intercept.setText(tmp);
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_CAMERALENGTH);
			if (tmp != null) cameralength.setText(tmp);
			
			val = this.memento.getInteger(CalibrationPreferences.QAXIS_STANDARD);
			if (val != null) standard.select(val);
			
			Unit<Length> selUnit = NANOMETER;
			String units = this.memento.getString(CalibrationPreferences.QAXIS_UNITS);
			if (units != null) 
				selUnit = (Unit<Length>) Unit.valueOf(units);
			
			for (Entry<Unit<Length>, Button> unitBtn : unitSel.entrySet())
				if (unitBtn.getKey().equals(selUnit))
					unitBtn.getValue().setSelection(true);
				else
					unitBtn.getValue().setSelection(false);
			
			flt = memento.getFloat(CalibrationPreferences.QAXIS_ENERGY);
			if (flt != null) {
				energy.setText(String.format("%.3f", flt));
				ncdEnergySourceProvider.setEnergy(new Double(flt));
			}
			
			IMemento calibrationPeaksMemento = this.memento.getChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
			if (calibrationPeaksMemento != null) {
				IMemento[] peaks = calibrationPeaksMemento.getChildren(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
				calibrationPeakList = new ArrayList<CalibrationPeak>();
				for (IMemento peak: peaks) {
					Float peakPos = peak.getFloat(CalibrationPreferences.QAXIS_PEAKPOS);
					Float tTheta = peak.getFloat(CalibrationPreferences.QAXIS_TWOTHETA);
					Float dSpacing = peak.getFloat(CalibrationPreferences.QAXIS_DSPACING);
					Integer h = peak.getInteger(CalibrationPreferences.QAXIS_H);
					Integer k = peak.getInteger(CalibrationPreferences.QAXIS_K);
					Integer l = peak.getInteger(CalibrationPreferences.QAXIS_L);
					calibrationPeakList.add(new CalibrationPeak(peakPos, tTheta, Amount.valueOf(dSpacing, selUnit), new int[] {h,k,l}));
				}
			}
			setCalTable(calibrationPeakList);
				
			IMemento crbMemento = this.memento.getChild(CalibrationPreferences.QAXIS_CRB);
			CalibrationResultsBean crb = null;
			
			if (crbMemento != null) {
				
				crb = new CalibrationResultsBean();
				IMemento crbDataMemento[] = crbMemento.getChildren(CalibrationPreferences.QAXIS_CRBDATA);
				
				for (IMemento data: crbDataMemento) {
					
					String key = data.getID();
					
					Parameter gradient = new Parameter(data.getFloat(CalibrationPreferences.QAXIS_GRADIENT));
					Parameter intercept = new Parameter(data.getFloat(CalibrationPreferences.QAXIS_INTERCEPT));
					tmp = data.getString(CalibrationPreferences.QAXIS_CAMERALENGTH);
					Amount<Length> meanCameraLength = null;
					if (tmp != null) {
						// JScience can't parse brackets
						tmp = tmp.replace("(", "").replace(")", "");
						meanCameraLength = Amount.valueOf(tmp).to(SI.METER);
					}
					
					IMemento dataPeaksMemento = data.getChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
					ArrayList<CalibrationPeak> dataPeakList = new ArrayList<CalibrationPeak>();
					if (dataPeaksMemento != null) {
						IMemento peaks[] = dataPeaksMemento.getChildren(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
						for (IMemento peak: peaks) {
							Float peakPos = peak.getFloat(CalibrationPreferences.QAXIS_PEAKPOS);
							Float tTheta = peak.getFloat(CalibrationPreferences.QAXIS_TWOTHETA);
							Float dSpacing = peak.getFloat(CalibrationPreferences.QAXIS_DSPACING);
							Integer h = peak.getInteger(CalibrationPreferences.QAXIS_H);
							Integer k = peak.getInteger(CalibrationPreferences.QAXIS_K);
							Integer l = peak.getInteger(CalibrationPreferences.QAXIS_L);
							dataPeakList.add(new CalibrationPeak(peakPos, tTheta, Amount.valueOf(dSpacing, selUnit), new int[] {h,k,l}));
						}
					}
					
					crb.putCalibrationResult(key, new StraightLine(new Parameter[]{gradient, intercept}), dataPeakList, meanCameraLength, selUnit);
				}
				
				ncdCalibrationSourceProvider.putCalibrationResult(crb);
			}
			
			IMemento roiMemento = this.memento.getChild(CalibrationPreferences.QAXIS_ROI);
			SectorROI roiData = null;
			if (roiMemento != null) {
				roiData = new SectorROI();
				roiData.setPlot(true);
				roiData.setAngles(roiMemento.getFloat(CalibrationPreferences.QAXIS_ROISP), 
						roiMemento.getFloat(CalibrationPreferences.QAXIS_ROIEP));
				roiData.setRadii(roiMemento.getFloat(CalibrationPreferences.QAXIS_ROISR), 
						roiMemento.getFloat(CalibrationPreferences.QAXIS_ROIER));
				roiData.setPoint(roiMemento.getFloat(CalibrationPreferences.QAXIS_ROIPTX), 
						roiMemento.getFloat(CalibrationPreferences.QAXIS_ROIPTY));
			}
			try {
				GuiBean newBean = PlotServerProvider.getPlotServer().getGuiState(GUI_PLOT_NAME);
				if (newBean == null)
					newBean = new GuiBean();
				newBean.put(GuiParameters.ROIDATA, roiData);
				PlotServerProvider.getPlotServer().updateGui(GUI_PLOT_NAME, newBean);
			} catch (Exception e) {
				logger.error("SCISOFT NCD Q-Axis Calibration: cannot restore GUI bean information", e);
			}

		}
	}

	protected Double getLambda() {
		Amount<Length> lambdaDim = Amount.valueOf(1e-3 * 1e9 * 4.13566733e-15 * 299792458
				/ ncdEnergySourceProvider.getEnergy(), SI.NANO(SI.METER));
		return lambdaDim.doubleValue(getUnitScale());
	}
	
	@Override
	protected void runJavaCommand() {
		currentMode = getDetectorName();
		calibrant = standard.getText();
		final boolean runRefinement = beamRefineButton.getSelection();

		final Unit<Length> unitScale = getUnitScale();
		final Double lambda, mmpp;
		try {
			lambda = getLambda();
			mmpp = getPixel(false);
		} catch (Exception e) {
			logger.error("SCISOFT NCD: Error reading data reduction parameters", e);
			Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID,
					"Failed to read energy or detector pixel size parameter from NCD Q axis calibration view.");
			ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
					"Input parameter error",
					"Please check that energy and detector pixel size parameters are properly set.", status);
			return;
		}

		final ArrayList<IPeak> initPeaks = (ArrayList<IPeak>) peaks.clone();
		final MultivariateFunction beamOffset = new MultivariateFunction() {

			private IJobManager jobManager = Job.getJobManager();
			
			@Override
			public double value(double[] beamxy) {
				final SectorROI sroi = twoDData.getROI();
				sroi.setPoint(beamxy);
				sroi.setDpp(1.0);
				AbstractDataset[] intresult = ROIProfile.sector((AbstractDataset) twoDData.getStoredDataset(),
						twoDData.getMask(), sroi, true, false, true);
				AbstractDataset axis = DatasetUtils.linSpace(sroi.getRadius(0), sroi.getRadius(1),
						intresult[0].getSize(), AbstractDataset.INT32);
				double error = 0.0;
				for (int idx = 0; idx < peaks.size(); idx++) {
					IPeak peak = initPeaks.get(idx);
					// logger.info("idx {} peak start position {}", idx, peak.getParameterValues());
					double pos = peak.getPosition();
					double fwhm = peak.getFWHM() / 2.0;
					int startIdx = DatasetUtils.findIndexGreaterThanorEqualTo(axis, pos - fwhm);
					int stopIdx = DatasetUtils.findIndexGreaterThanorEqualTo(axis, pos + fwhm) + 1;

					AbstractDataset axisSlice = axis.getSlice(new int[] { startIdx }, new int[] { stopIdx }, null);
					AbstractDataset peakSlice = intresult[0].getSlice(new int[] { startIdx }, new int[] { stopIdx },
							null);
					try {
						CompositeFunction peakFit = Fitter.fit(axisSlice, peakSlice, new GeneticAlg(0.0001),
								new Gaussian(peak.getParameters()));
						//CompositeFunction peakFit = Fitter.fit(axisSlice, peakSlice, new GeneticAlg(0.0001),
						//		new PseudoVoigt(peak.getParameters()));
						peak.setParameterValues(peakFit.getParameterValues());
						//logger.info("idx {} peak fitting result {}", idx, peakFit.getParameterValues());
						peaks.set(idx, peak);
						error += Math.log(1.0 + peak.getHeight() / peak.getFWHM());
						//error += (Double) peakSlice.max();// peak.getHeight();// / peak.getFWHM();
					} catch (Exception e) {
						logger.error("Peak fitting failed", e);
						return Double.NaN;
					}

				}
				if (checkPeakOverlap(peaks))
					return Double.NaN;
				logger.info("Beam position distance error for postion {}", new double[] { error, beamxy[0], beamxy[1] });

				final String jobName = "Sector plot";
				UIJob plotingJob = new UIJob(jobName) {
					
					@Override
					public boolean belongsTo(Object family) {
						return family == jobName;
					}
				      
					@Override
					public IStatus runInUIThread(IProgressMonitor monitor) {
						try {
							GuiBean newBean = PlotServerProvider.getPlotServer().getGuiState(GUI_PLOT_NAME);
							if (newBean == null)
								newBean = new GuiBean();
							newBean.put(GuiParameters.ROIDATA, twoDData.getROI());
							PlotServerProvider.getPlotServer().updateGui(GUI_PLOT_NAME, newBean);
							
							AbstractPlottingSystem plotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
							plotSystem.getRegions(RegionType.SECTOR).iterator().next().setROI(sroi);
							
							CalibrationMethods calibrationMethod = new CalibrationMethods(peaks,
							cal2peaks.get(calibrant), lambda, mmpp, unitScale);
							calibrationMethod.performCalibration(true);
							Parameter gradient = new Parameter(calibrationMethod.getFitResult()[1]);
							Parameter intercept = new Parameter(calibrationMethod.getFitResult()[0]);
							StraightLine calibrationFunction = new StraightLine(new Parameter[] { gradient, intercept});
							plotCalibrationResults(calibrationFunction, calibrationMethod.getIndexedPeakList());

							return Status.OK_STATUS;
							
						} catch (Exception e) {
							logger.error("Error updating plot view", e);
							return Status.CANCEL_STATUS;
						}
					}
				};
				
				jobManager.cancel(jobName);
				plotingJob.schedule();

				return error;
			}

			private boolean checkPeakOverlap(ArrayList<IPeak> peaks) {
				if (peaks.size() < 2)
					return false;
				CombinationGenerator<IPeak> peakPair = new CombinationGenerator<IPeak>(peaks, peaks.size());
				for (List<IPeak> pair : peakPair) {
					IPeak peak1 = pair.get(0);
					IPeak peak2 = pair.get(1);
					double dist = Math.abs(peak2.getPosition() - peak1.getPosition());
					double fwhm1 = peak1.getFWHM();
					double fwhm2 = peak2.getFWHM();
					if (dist < (fwhm1 + fwhm2) / 2.0)
						return true;
				}
				return false;
			}
		};

		logger.info("Beam position before fit {}", twoDData.getROI().getPoint());
		Job job = new Job("Beam Position Refinement") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				
				if (runRefinement) {
					CMAESOptimizer beamPosOptimizer = new CMAESOptimizer(cmaesLambda, cmaesInputSigma, cmaesMaxIterations, 0.0, true,
							0, cmaesCheckFeasableCount, new Well19937a(), false, cmaesChecker);
					//SimplexOptimizer beamPosOptimizer = new SimplexOptimizer(cmaesChecker);
					//SimplexOptimizer beamPosOptimizer = new SimplexOptimizer(1e-6, 1e-4);
					//beamPosOptimizer.setSimplex(new MultiDirectionalSimplex(cmaesInputSigma));
					final PointValuePair newBeamPos = beamPosOptimizer.optimize(cmaesMaxIterations, beamOffset, GoalType.MAXIMIZE,
							twoDData.getROI().getPoint());
					Display.getDefault().syncExec(new Runnable() {
						@Override
						public void run() {
							twoDData.getROI().setPoint(newBeamPos.getPoint());
						}
					});
				}

				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						CalibrationMethods calibrationMethod = new CalibrationMethods(peaks, cal2peaks.get(calibrant), lambda, mmpp, unitScale);
						calibrationMethod.performCalibration(true);
						logger.info("Beam position after fit {}", twoDData.getROI().getPoint());

						CalibrationResultsBean crb = new CalibrationResultsBean();
						Parameter gradient = new Parameter(calibrationMethod.getFitResult()[1]);
						Parameter intercept = new Parameter(calibrationMethod.getFitResult()[0]);
						StraightLine calibrationFunction = new StraightLine(new Parameter[] { gradient, intercept });
						Amount<Length> meanCameraLength = calibrationMethod.getMeanCameraLength().to(SI.METER);
						cameralength.setText(meanCameraLength.toString());

						crb.putCalibrationResult(currentMode, calibrationFunction,
								calibrationMethod.getIndexedPeakList(), meanCameraLength, unitScale);
						ncdCalibrationSourceProvider.putCalibrationResult(crb);

						plotCalibrationResults(calibrationFunction, calibrationMethod.getIndexedPeakList());
						
						try {

							GuiBean newBean = PlotServerProvider.getPlotServer().getGuiState(GUI_PLOT_NAME);
							if (newBean == null)
								newBean = new GuiBean();
							newBean.put(GuiParameters.ROIDATA, twoDData.getROI());
							PlotServerProvider.getPlotServer().updateGui(GUI_PLOT_NAME, newBean);
						} catch (Exception e) {
							logger.error("SCISOFT NCD: Error running q-axis calibration procedure", e);
							Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID,
									"Error running q-axis calibration procedure.");
							ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
									"Q-axis calibration error", "Q-axis calibration procedure has failed.", status);
							return;
						}

					}
				});

				return Status.OK_STATUS;
			}
		};

		job.schedule();
	}

	private void plotCalibrationResults(StraightLine calibrationFunction, List<CalibrationPeak> list) {
		
		plottingSystem.clear();
		
		double px = getPixel(false);
		AbstractDataset xAxis = AbstractDataset.arange(twoDData.getROI().getIntRadius(1), AbstractDataset.FLOAT32);
		xAxis.imultiply(px);
		AbstractDataset qvalues = calibrationFunction.makeDataset(xAxis);
		
        ILineTrace calibrationLine = plottingSystem.createLineTrace("Fitting line");
        calibrationLine.setTraceType(TraceType.SOLID_LINE);
        calibrationLine.setLineWidth(2);
        calibrationLine.setTraceColor(ColorConstants.red);
        calibrationLine.setData(xAxis, qvalues);
        plottingSystem.addTrace(calibrationLine);

		ArrayList<Double> peakPos = new ArrayList<Double>();
		ArrayList<Double> qData = new ArrayList<Double>();
		
		for (CalibrationPeak peak : list) {
			peakPos.add(peak.getPeakPos() * px);
			qData.add(2.0 * Math.PI / peak.getDSpacing().doubleValue(getUnitScale()));
		}
		
		AbstractDataset.createFromList(peakPos);
        ILineTrace referencePoints = plottingSystem.createLineTrace("Calibration Points");
        referencePoints.setTraceType(TraceType.POINT);
        referencePoints.setTraceColor(ColorConstants.darkBlue);
        referencePoints.setPointStyle(PointStyle.FILLED_TRIANGLE);
        referencePoints.setPointSize(10);
        referencePoints.setData(AbstractDataset.createFromList(peakPos), AbstractDataset.createFromList(qData));
        plottingSystem.addTrace(referencePoints);
        
        plottingSystem.getSelectedXAxis().setTitle("Pixel position / mm");
        plottingSystem.getSelectedYAxis().setTitle("q / " + getUnitScale().inverse().toString());
        plottingSystem.autoscaleAxes();

	}
	
	protected String getDetectorName() {
		//  Override in subclass to refer to the calibrated detector
		return null;
	}

	@SuppressWarnings("unused")
	protected Double getPixel(boolean b) {
		// Override in subclass to return the appropriate pixel size
		return null;
	}
	
	public void updateResults(String detector) {
		GuiBean guiinfo;
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			IViewPart activePlot = page.findView(ACTIVE_PLOT);
			if (activePlot != null) {
				guiinfo = ((PlotView)activePlot).getGUIInfo();

				if (guiinfo.containsKey(GuiParameters.CALIBRATIONFUNCTIONNCD)) {
					Serializable bd = guiinfo.get(GuiParameters.CALIBRATIONFUNCTIONNCD);

					if (bd != null && bd instanceof CalibrationResultsBean) {
						CalibrationResultsBean crb = (CalibrationResultsBean) bd;
						if (crb.keySet().contains(detector)) {
							currentMode = detector;
							ncdCalibrationSourceProvider.putCalibrationResult(crb);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.info("No GUI bean information available");
		}
	}
}