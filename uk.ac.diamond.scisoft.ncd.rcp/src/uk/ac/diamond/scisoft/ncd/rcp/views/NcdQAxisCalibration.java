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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.measure.quantity.Angle;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.SimplePointChecker;
import org.dawb.common.ui.plot.AbstractPlottingSystem;
import org.dawb.common.ui.plot.AbstractPlottingSystem.ColorOption;
import org.dawb.common.ui.plot.IPlottingSystem;
import org.dawb.common.ui.plot.PlotType;
import org.dawb.common.ui.plot.PlottingFactory;
import org.dawb.common.ui.plot.region.IRegion;
import org.dawb.common.ui.plot.region.IRegion.RegionType;
import org.dawb.common.ui.plot.trace.IImageTrace;
import org.dawb.common.ui.plot.trace.ILineTrace;
import org.dawb.common.ui.plot.trace.ILineTrace.PointStyle;
import org.dawb.common.ui.plot.trace.ILineTrace.TraceType;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
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
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISourceProviderListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.services.ISourceProviderService;
import org.jscience.physics.amount.Amount;
import org.jscience.physics.amount.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.crystallography.CalibrationFactory;
import uk.ac.diamond.scisoft.analysis.crystallography.CalibrationStandards;
import uk.ac.diamond.scisoft.analysis.crystallography.HKL;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.fitting.functions.IPeak;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Parameter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.StraightLine;
import uk.ac.diamond.scisoft.analysis.roi.ROIBase;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.calibration.CalibrationMethods;
import uk.ac.diamond.scisoft.ncd.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.preferences.CalibrationPreferences;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;

public class NcdQAxisCalibration extends QAxisCalibrationBase implements ISourceProviderListener{
	
	private IMemento memento;
	
	private IJobManager jobManager;
	
	protected String GUI_PLOT_NAME = "Dataset Plot";
	protected String ACTIVE_PLOT = "Dataset Plot";
	private AbstractPlottingSystem plottingSystem;

	private static final Logger logger = LoggerFactory.getLogger(NcdQAxisCalibration.class);
	
	private MultivariateFunctionSourceProvider beamxySourceProvider, peaksSourceProvider;
	private ISourceProviderService service;
	
	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		this.memento = memento;
		super.init(site, memento);

		jobManager = Job.getJobManager();
	}
	
	@Override
	public void createPartControl(Composite parent) {

		super.createPartControl(parent);
		
		restoreState();
		
		try {
	        this.plottingSystem = PlottingFactory.createPlottingSystem();
	        plottingSystem.setColorOption(ColorOption.NONE);
		} catch (Exception e) {
			logger.error("Cannot locate any plotting systems!", e);
		}
		
		final Composite plot = new Composite(parent, SWT.NONE);
		plot.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		plot.setLayout(new FillLayout());
		try {
	        IActionBars wrapper = this.getViewSite().getActionBars();
			plottingSystem.createPlotPart(plot, "Calibration Plot", wrapper, PlotType.XY, this);

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
		
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		beamxySourceProvider = (MultivariateFunctionSourceProvider) service.getSourceProvider(MultivariateFunctionSourceProvider.SECTORREFINEMENT_STATE);
		peaksSourceProvider = (MultivariateFunctionSourceProvider) service.getSourceProvider(MultivariateFunctionSourceProvider.PEAKREFINEMENT_STATE);
		beamxySourceProvider.addSourceProviderListener(this);
		peaksSourceProvider.addSourceProviderListener(this);
	}

	@Override
	public void saveState(IMemento memento) {
		
		if (memento != null) {
			
			memento.putString(CalibrationPreferences.QAXIS_CURRENTMODE, currentDetector);
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
					calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_TWOTHETA, (float) peak.getTwoTheta().doubleValue(NonSI.DEGREE_ANGLE));
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
									(float) peak.getTwoTheta().doubleValue(NonSI.DEGREE_ANGLE));
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

			IPlottingSystem plotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
			Collection<IRegion> sectorRegions = plotSystem.getRegions(RegionType.SECTOR);
			if (sectorRegions != null && !(sectorRegions.isEmpty())) {
				ROIBase intBase = sectorRegions.iterator().next().getROI();
				if (intBase instanceof SectorROI) {
					SectorROI intSector = (SectorROI) intBase;
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
	}
	
	private void restoreState() {
		if (this.memento != null) {
			String tmp;
			Integer val;
			Float flt;
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_CURRENTMODE);
			if (tmp != null) currentDetector = tmp;
			
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
			
			Unit<Length> selUnit = SI.NANO(SI.METER);
			String units = this.memento.getString(CalibrationPreferences.QAXIS_UNITS);
			if (units != null) 
				selUnit = Unit.valueOf(units).asType(Length.class);
			
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
					Amount<Angle> tTheta = Amount.valueOf(peak.getFloat(CalibrationPreferences.QAXIS_TWOTHETA), NonSI.DEGREE_ANGLE);
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
							Amount<Angle> tTheta = Amount.valueOf(peak.getFloat(CalibrationPreferences.QAXIS_TWOTHETA), NonSI.DEGREE_ANGLE);
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
				try {
					IPlottingSystem plotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
					plotSystem.setPlotType(PlotType.IMAGE);
					IRegion sector = plotSystem.createRegion("Stored Sector", RegionType.SECTOR);
					sector.setROI(roiData.copy());
					sector.setUserRegion(true);
					sector.setVisible(true);
					plotSystem.addRegion(sector);
				} catch (Exception e) {
					logger.error("SCISOFT NCD Q-Axis Calibration: cannot restore GUI bean information", e);
				}
			}

		}
	}

	protected Amount<Length> getLambda() {
		Amount<Length> lambdaDim = Constants.ℎ.times(Constants.c).divide(
				Amount.valueOf(ncdEnergySourceProvider.getEnergy(), SI.KILO(NonSI.ELECTRON_VOLT))).to(Length.UNIT);
		return lambdaDim;
	}
	
	@Override
	protected void runJavaCommand() {
		final boolean runRefinement = beamRefineButton.getSelection();

		IPlottingSystem plotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
		Collection<IRegion> sectorRegions = plotSystem.getRegions(RegionType.SECTOR);
		if (sectorRegions == null || sectorRegions.isEmpty())
			throw new IllegalArgumentException(NcdMessages.NO_SEC_DATA);
		if (sectorRegions.size() > 1)
			throw new IllegalArgumentException(NcdMessages.NO_SEC_SUPPORT);
		final SectorROI sroi = (SectorROI) sectorRegions.iterator().next().getROI();
		if (runRefinement) {
			IImageTrace trace = (IImageTrace) plotSystem.getTraces().iterator().next();
			final AbstractDataset dataset = trace.getData();
			final AbstractDataset mask = trace.getMask();
			
			final MultivariateFunctionWithMonitor beamOffset = new MultivariateFunctionWithMonitor(dataset, mask, sroi);
			beamOffset.addSourceProviders(service);

			int cmaesLambda = Activator.getDefault().getPreferenceStore().getInt(NcdPreferences.CMAESlambda);
			double cmaesInputSigmaPref = Activator.getDefault().getPreferenceStore().getInt(NcdPreferences.CMAESsigma);
			double[] cmaesInputSigma = new double[] { cmaesInputSigmaPref, cmaesInputSigmaPref };
			int cmaesMaxIterations = Activator.getDefault().getPreferenceStore().getInt(NcdPreferences.CMAESmaxiteration);
			int cmaesCheckerPref = Activator.getDefault().getPreferenceStore().getInt(NcdPreferences.CMAESchecker);
			SimplePointChecker<PointValuePair> cmaesChecker = new SimplePointChecker<PointValuePair>(1e-4, 1.0 / cmaesCheckerPref);
			beamOffset.configureOptimizer(cmaesLambda == 0 ? null : cmaesLambda,
					cmaesInputSigmaPref == 0 ? null	: cmaesInputSigma,
					cmaesMaxIterations == 0 ? null : cmaesMaxIterations,
					null,
					cmaesCheckerPref == 0 ? null : cmaesChecker);
			
			beamOffset.setInitPeaks(peaks);
			
			beamOffset.optimize(sroi.getPoint());
		} else 
			peaksSourceProvider.putPeaks(peaks);

	}

	private void plotCalibrationResults(StraightLine calibrationFunction, List<CalibrationPeak> list) {
		
		plottingSystem.clear();
		
		double px = getPixel(false).doubleValue(SI.MILLIMETER);
		IPlottingSystem plotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
		final SectorROI sroi = (SectorROI) plotSystem.getRegions(RegionType.SECTOR).iterator().next().getROI();
		AbstractDataset xAxis = AbstractDataset.arange(sroi.getIntRadius(1), AbstractDataset.FLOAT32);
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
	protected Amount<Length> getPixel(boolean b) {
		// Override in subclass to return the appropriate pixel size
		return null;
	}
	
	@Override
	public void sourceChanged(int sourcePriority, @SuppressWarnings("rawtypes") Map sourceValuesByName) {
		super.sourceChanged(sourcePriority, sourceValuesByName);
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, final Object sourceValue) {
		super.sourceChanged(sourcePriority, sourceName, sourceValue);

		if (sourceName.equals(MultivariateFunctionSourceProvider.SECTORREFINEMENT_STATE)) {
			final String jobName = "Sector plot";
			UIJob plotingJob = new UIJob(jobName) {

				@Override
				public boolean belongsTo(Object family) {
					return family == jobName;
				}

				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					try {
						IPlottingSystem plotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
						
						 
						IRegion sector = plotSystem.getRegions(RegionType.SECTOR).iterator().next();
						
						ROIBase roi = sector.getROI();
						roi.setPoint((double[]) sourceValue);
						sector.setROI(roi);

						return Status.OK_STATUS;

					} catch (Exception e) {
						logger.error("Error updating plot view", e);
						return Status.CANCEL_STATUS;
					}
				}
			};

			jobManager.cancel(jobName);
			try {
				jobManager.join(jobName, new NullProgressMonitor());
			} catch (OperationCanceledException e) {
				logger.error(jobName + " job is interrupted", e);
			} catch (InterruptedException e) {
				logger.error(jobName + " job is interrupted", e);
			}
			plotingJob.schedule();
		}
		
		if (sourceName.equals(MultivariateFunctionSourceProvider.PEAKREFINEMENT_STATE)) {
			final String jobName = "Peak plot";
			UIJob plotingJob = new UIJob(jobName) {

				@Override
				public boolean belongsTo(Object family) {
					return family == jobName;
				}

				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					ArrayList<IPeak> newPeaks = (ArrayList<IPeak>) sourceValue;
					try {
						if (newPeaks.size() < 2) {
							logger.error("SCISOFT NCD: Error running q-axis calibration procedure");
							Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID,
									"Insuffcient number of calibration peaks.");
							ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
									"Q-axis calibration error", "Error running q-axis calibration procedure.", status);
							return Status.CANCEL_STATUS;
						}
						
						CalibrationStandards cs = CalibrationFactory.getCalibrationStandards();
						String calibrant = standard.getText();
						CalibrationMethods calibrationMethod = new CalibrationMethods(newPeaks,
								cs.getCalibrationPeakMap(calibrant), getLambda(), getPixel(false), getUnitScale());
						calibrationMethod.performCalibration(true);

						CalibrationResultsBean crb = new CalibrationResultsBean();
						Parameter gradient = new Parameter(calibrationMethod.getFitResult()[1]);
						Parameter intercept = new Parameter(calibrationMethod.getFitResult()[0]);
						StraightLine calibrationFunction = new StraightLine(new Parameter[] { gradient, intercept });
						Amount<Length> meanCameraLength = calibrationMethod.getMeanCameraLength().to(SI.METER);
						cameralength.setText(meanCameraLength.toString());

						crb.putCalibrationResult(getDetectorName(), calibrationFunction, calibrationMethod.getIndexedPeakList(),
								meanCameraLength, getUnitScale());
						ncdCalibrationSourceProvider.putCalibrationResult(crb);

						plotCalibrationResults(calibrationFunction, calibrationMethod.getIndexedPeakList());
						return Status.OK_STATUS;
					} catch (Exception e) {
						logger.error("Error updating plot view", e);
						return Status.CANCEL_STATUS;
					}
				}
			};

			jobManager.cancel(jobName);
			try {
				jobManager.join(jobName, new NullProgressMonitor());
			} catch (OperationCanceledException e) {
				logger.error(jobName + " job is interrupted", e);
			} catch (InterruptedException e) {
				logger.error(jobName + " job is interrupted", e);
			}
			plotingJob.schedule();
		}
	}
}
