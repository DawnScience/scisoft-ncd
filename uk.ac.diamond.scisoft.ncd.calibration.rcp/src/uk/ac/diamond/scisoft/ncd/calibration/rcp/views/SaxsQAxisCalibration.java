/*
 * Copyright 2011, 2017 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.calibration.rcp.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;

import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimplePointChecker;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.dawnsci.analysis.api.fitting.functions.IPeak;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.trace.IImageTrace;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace.PointStyle;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace.TraceType;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.statushandlers.StatusManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tec.units.ri.quantity.Quantities;
import tec.units.ri.unit.MetricPrefix;
import tec.units.ri.unit.Units;
import uk.ac.diamond.scisoft.analysis.crystallography.CalibrationFactory;
import uk.ac.diamond.scisoft.analysis.crystallography.CalibrationStandards;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Parameter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.StraightLine;
import uk.ac.diamond.scisoft.ncd.calibration.CalibrationMethods;
import uk.ac.diamond.scisoft.ncd.calibration.rcp.Activator;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;

public class SaxsQAxisCalibration<V extends ScatteringVector<V>, D extends ScatteringVectorOverDistance<D>>
		extends NcdQAxisCalibration {
	
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.SaxsQAxisCalibration";
	private MultivariateFunctionSourceProvider beamxySourceProvider, peaksSourceProvider;
	private ISourceProviderService service;
	
	private static final Logger logger = LoggerFactory.getLogger(SaxsQAxisCalibration.class);

	private static final Unit<Length> MILLIMETRE = MetricPrefix.MILLI(Units.METRE);

	@Override
	public void createPartControl(Composite parent) {
		GUI_PLOT_NAME = "Dataset Plot";
		
		super.createPartControl(parent);
		
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		beamxySourceProvider = (MultivariateFunctionSourceProvider) service.getSourceProvider(MultivariateFunctionSourceProvider.SECTORREFINEMENT_STATE);
		peaksSourceProvider = (MultivariateFunctionSourceProvider) service.getSourceProvider(MultivariateFunctionSourceProvider.PEAKREFINEMENT_STATE);
		beamxySourceProvider.addSourceProviderListener(this);
		peaksSourceProvider.addSourceProviderListener(this);
		
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
					Quantity<D> gradient = getGradient();
					Quantity<V> intercept = getIntercept();
					if (gradient == null || intercept == null) {
						if (crb.containsKey(det))
							crb.clearData(det);
							plottingSystem.clear();
					} else {
						Quantity<Length> meanCameraLength = null;
						Quantity<Energy> amountEnergy = getEnergy();
						if (amountEnergy != null) {
							Quantity<Length> wv = Constants.ℎ.times(Constants.c).divide(amountEnergy).to(Length.UNIT);
							meanCameraLength = Constants.two_π.divide(gradient).divide(wv).to(Length.UNIT);
						}
						crb.putCalibrationResult(det, gradient, intercept, null, meanCameraLength, getUnit());
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
	protected String getDetectorName() {
		return ncdSaxsDetectorSourceProvider.getSaxsDetector();
	}

	@Override
	protected Quantity<Length> getPixel() {
		NcdDetectorSettings detSettings = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(getDetectorName()); 
		if (detSettings == null) {
			throw new IllegalArgumentException(NcdMessages.NO_SAXS_DETECTOR);
		}
		Quantity<Length> pxSize = detSettings.getPxSize();
		if (pxSize == null) {
			throw new IllegalArgumentException(NcdMessages.NO_SAXS_PIXEL);
		}
		return Quantities.getQuantity(pxSize.getValue(), pxSize.getUnit()).to(MILLIMETRE);
	}
	
	@Override
	protected void runJavaCommand() {
		
		try {
			String saxsDet = getDetectorName();
			if (saxsDet == null) {
				throw new IllegalArgumentException(NcdMessages.NO_SAXS_DETECTOR);
			}
			Quantity<Energy> energy = getEnergy();
			if (energy == null) {
				throw new IllegalArgumentException(NLS.bind(NcdMessages.NO_ENERGY_DATA, "calibration settings"));
			}
			Quantity<Length> pxSize = getPixel();
			if (pxSize == null) {
				throw new IllegalArgumentException(NcdMessages.NO_SAXS_PIXEL);
			}

			final boolean runRefinement = beamRefineButton.getSelection();

			IPlottingSystem<Composite> plotSystem = PlottingFactory.getPlottingSystem(GUI_PLOT_NAME);
			Collection<IRegion> sectorRegions = plotSystem.getRegions(RegionType.SECTOR);
			if (sectorRegions == null || sectorRegions.isEmpty()) {
				throw new IllegalArgumentException(NcdMessages.NO_SEC_DATA);
			}
			if (sectorRegions.size() > 1) {
				throw new IllegalArgumentException(NcdMessages.NO_SEC_SUPPORT);
			}
			final SectorROI sroi = (SectorROI) sectorRegions.iterator().next().getROI();
			if (runRefinement) {
				IImageTrace trace = (IImageTrace) plotSystem.getTraces().iterator().next();
				final Dataset dataset = DatasetUtils.convertToDataset(trace.getData());
				final Dataset mask = DatasetUtils.convertToDataset(trace.getMask());

				final MultivariateFunctionWithMonitor beamOffset = new MultivariateFunctionWithMonitor(dataset, mask,
						sroi);
				beamOffset.addSourceProviders(service);

				int cmaesLambda = uk.ac.diamond.scisoft.ncd.core.rcp.Activator.getDefault().getPreferenceStore()
						.getInt(NcdPreferences.CMAESlambda);
				double cmaesInputSigmaPref = uk.ac.diamond.scisoft.ncd.core.rcp.Activator.getDefault()
						.getPreferenceStore().getInt(NcdPreferences.CMAESsigma);
				double[] cmaesInputSigma = new double[] { cmaesInputSigmaPref, cmaesInputSigmaPref };
				int cmaesMaxIterations = uk.ac.diamond.scisoft.ncd.core.rcp.Activator.getDefault().getPreferenceStore()
						.getInt(NcdPreferences.CMAESmaxiteration);
				int cmaesCheckerPref = uk.ac.diamond.scisoft.ncd.core.rcp.Activator.getDefault().getPreferenceStore()
						.getInt(NcdPreferences.CMAESchecker);
				SimplePointChecker<PointValuePair> cmaesChecker = new SimplePointChecker<PointValuePair>(1e-4, 1.0 / cmaesCheckerPref);
				beamOffset.configureOptimizer(cmaesLambda == 0 ? null : cmaesLambda,
								cmaesInputSigmaPref == 0 ? null	: cmaesInputSigma,
								cmaesMaxIterations == 0 ? null : cmaesMaxIterations,
								null,
								cmaesCheckerPref == 0 ? null : cmaesChecker);

				beamOffset.setInitPeaks(peaks);

				beamOffset.optimize(sroi.getPoint());
			} else {
				peaksSourceProvider.putPeaks(peaks);
			}
		} catch (Exception e) {
			Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage());
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}

	}

	private void plotCalibrationResults(Quantity<D> gradient, Quantity<V> intercept, List<CalibrationPeak> list) {
		
		StraightLine calibrationFunction = new StraightLine(new Parameter[] {
				new Parameter(gradient.getEstimatedValue()),
				new Parameter(intercept.getEstimatedValue()) });
		
		plottingSystem.clear();
		
		Quantity<Length> px = getPixel();
		IPlottingSystem<Composite> plotSystem = PlottingFactory.getPlottingSystem(GUI_PLOT_NAME);
		final SectorROI sroi = (SectorROI) plotSystem.getRegions(RegionType.SECTOR).iterator().next().getROI();
		Dataset xAxis = DatasetFactory.createRange(sroi.getIntRadius(1), Dataset.FLOAT32);
		xAxis.imultiply(px.getValue());
		Dataset qvalues = calibrationFunction.calculateValues(xAxis);
		
        ILineTrace calibrationLine = plottingSystem.createLineTrace("Fitting line");
        calibrationLine.setTraceType(TraceType.SOLID_LINE);
        calibrationLine.setLineWidth(2);
        calibrationLine.setTraceColor(ColorConstants.red);
        calibrationLine.setData(xAxis, qvalues);
        plottingSystem.addTrace(calibrationLine);

		ArrayList<Double> peakPos = new ArrayList<Double>();
		ArrayList<Double> qData = new ArrayList<Double>();
		ArrayList<Double> qEst = new ArrayList<Double>();
		ArrayList<Double> qEstError = new ArrayList<Double>();
		
		for (CalibrationPeak peak : list) {
			Quantity<Length> pxPos = px.multiply(peak.getPeakPos()); 
			peakPos.add(pxPos.getValue().doubleValue());
			qData.add(2.0 * Math.PI / peak.getDSpacing().to(getUnitScale()).getValue().doubleValue());
			Quantity<V> q = gradient.multiply(pxPos).plus(intercept).to(intercept.getUnit());
			qEst.add(q.getValue().doubleValue());
			qEstError.add(q.getAbsoluteError());
		}
		
		DatasetFactory.createFromList(peakPos);
        ILineTrace estPoints = plottingSystem.createLineTrace("Peak Positions");
        estPoints.setTraceType(TraceType.POINT);
        estPoints.setTraceColor(ColorConstants.red);
        estPoints.setPointStyle(PointStyle.CIRCLE);
        estPoints.setPointSize(5);
        Dataset qDataset = DatasetFactory.createFromList(qEst);
        qDataset.setErrors(DatasetFactory.createFromList(qEstError));
        estPoints.setData(DatasetFactory.createFromList(peakPos), qDataset);
        plottingSystem.addTrace(estPoints);
        
        DatasetFactory.createFromList(peakPos);
        ILineTrace referencePoints = plottingSystem.createLineTrace("Calibration Points");
        referencePoints.setTraceType(TraceType.POINT);
        referencePoints.setTraceColor(ColorConstants.darkBlue);
        referencePoints.setPointStyle(PointStyle.FILLED_TRIANGLE);
        referencePoints.setPointSize(10);
        referencePoints.setData(DatasetFactory.createFromList(peakPos), DatasetFactory.createFromList(qData));
        plottingSystem.addTrace(referencePoints);
        
        plottingSystem.getSelectedXAxis().setTitle("Pixel position / " + px.getUnit().toString());
        plottingSystem.getSelectedYAxis().setTitle("q / " + getUnitScale().inverse().toString());
        plottingSystem.autoscaleAxes();

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
						IPlottingSystem<Composite> plotSystem = PlottingFactory.getPlottingSystem(GUI_PLOT_NAME);
						
						 
						IRegion sector = plotSystem.getRegions(RegionType.SECTOR).iterator().next();
						
						IROI roi = sector.getROI();
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
					List<IPeak> newPeaks = (List<IPeak>) sourceValue;
					try {
						if (newPeaks.size() < 2) {
							logger.error("SCISOFT NCD: Error running q-axis calibration procedure");
							Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID,
									"Insuffcient number of calibration peaks.");
							ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
									"Q-axis calibration error", "Error running q-axis calibration procedure.", status);
							return Status.CANCEL_STATUS;
						}
						
						CalibrationStandards cs = CalibrationFactory.getCalibrationStandards();
						String calibrant = standard.getText();
						CalibrationMethods calibrationMethod = new CalibrationMethods(newPeaks,
								cs.getCalibrationPeakMap(calibrant), getLambda(), getPixel(), getUnitScale());
						calibrationMethod.performCalibration(true);

						CalibrationResultsBean crb = new CalibrationResultsBean();
						
						Quantity<D> gradient = calibrationMethod.getGradient();
						Quantity<V> intercept = calibrationMethod.getIntercept();
						Quantity<Length> meanCameraLength = calibrationMethod.getMeanCameraLength().to(Units.METRE);
						cameralength.setText(meanCameraLength.toString());

						crb.putCalibrationResult(getDetectorName(), gradient, intercept, calibrationMethod.getIndexedPeakList(),
								meanCameraLength, getUnitScale());
						ncdCalibrationSourceProvider.putCalibrationResult(crb);

						plotCalibrationResults(gradient, intercept, calibrationMethod.getIndexedPeakList());
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
