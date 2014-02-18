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

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlottingFactory;
import org.dawnsci.plotting.api.region.IRegion;
import org.dawnsci.plotting.api.region.IRegion.RegionType;
import org.dawnsci.plotting.api.trace.IImageTrace;
import org.dawnsci.plotting.api.trace.ITrace;
import org.dawnsci.plotting.tools.masking.MaskingTool;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.resources.ProjectExplorer;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.statushandlers.StatusManager;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.roi.IROI;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.service.IDataReductionContext;
import uk.ac.diamond.scisoft.ncd.core.service.IDataReductionService;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.SaxsPlotsSourceProvider;

public class DataReductionHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(DataReductionHandler.class);
		
	private IDataReductionService service;
	private Object[] selObjects;
	private IDataReductionContext context;
	
	private class NcdDataReductionJob implements IRunnableWithProgress {

		@Override
		public void run(IProgressMonitor monitor) {
			
			int work = context.getWorkAmount();
			monitor.beginTask("Running NCD data reduction", work * (selObjects.length + 1));
			monitor.worked(work);
			
			// Process each file 
			for (int i = 0; i < selObjects.length; i++) {
				final String inputfilePath;
				if (selObjects[i] instanceof IFile) {
					inputfilePath = ((IFile) selObjects[i]).getLocation().toString();
				} else {
					inputfilePath = ((File) selObjects[i]).getAbsolutePath();
				}
				if (inputfilePath == null) {
					continue;
				}

				try {
					monitor.setTaskName("Processing : " + inputfilePath);
					IStatus status = service.process(inputfilePath, context, monitor);
					monitor.worked(work);
					if (status.getSeverity() == IStatus.CANCEL) {
						monitor.done();
						return;
					}
				} catch (Exception e) {
					String msg = "SCISOFT NCD: Error running NCD data reduction process";
					MultiStatus mStatus = new MultiStatus(Activator.PLUGIN_ID, IStatus.ERROR, msg, e);
					for (StackTraceElement ste : e.getStackTrace()) {
						mStatus.add(new Status(IStatus.ERROR, Activator.PLUGIN_ID, ste.toString()));
					}
					StatusManager.getManager().handle(mStatus, StatusManager.BLOCK|StatusManager.SHOW);
					logger.error(msg, e);
					monitor.done();
					return;
				}
			}
			monitor.done();
		}
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window.getActivePage();
		IStructuredSelection sel = (IStructuredSelection)page.getSelection(ProjectExplorer.VIEW_ID);
		if (sel == null) {
			sel = (IStructuredSelection)page.getSelection(Activator.FILEVIEW_ID);
		}
				
		if (sel != null) {
			
			try {
				// We get the data reduction service using OSGI
				service = (IDataReductionService)Activator.getService(IDataReductionService.class);
			
				// Get data from NcdProcessingSourceProvider's and store in IDataReductionContext
				context = service.createContext();
				createData(context, window);
			
				// Now we configure the context, which throws exceptions if 
				// the configuration is invalid.
				createMaskAndRegion(context);
				service.configure(context);
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Error reading data reduction parameters";
				logger.error(msg, e);
				MultiStatus mStatus = new MultiStatus(Activator.PLUGIN_ID, IStatus.ERROR, msg, e);
				for (StackTraceElement ste : e.getStackTrace()) {
					mStatus.add(new Status(IStatus.ERROR, Activator.PLUGIN_ID, ste.toString()));
				}
				StatusManager.getManager().handle(mStatus, StatusManager.BLOCK|StatusManager.SHOW);
				return Boolean.FALSE;
			}
			
			if (context.isEnableSector() && !isCalibrationResultsBean(context)) {
				boolean proceed = MessageDialog
						.openConfirm(
								window.getShell(),
								"Missing NCD calibration data",
								"IMPORTANT! NCD calibration data was not found for currently selected SAXS detector.\n"
								+ "Data reduction pipeline will look for calibration information in the input files.\n"
								+ "Proceed with data reduction anyway?");
				if (!proceed) {
					return Boolean.FALSE;
				}
			}
			
			selObjects = sel.toArray();
			
			boolean runModal = uk.ac.diamond.scisoft.ncd.rcp.Activator.getDefault().getPreferenceStore().getBoolean(NcdPreferences.NCD_REDUCTION_MODAL);
			final NcdDataReductionJob ncdProcess = new NcdDataReductionJob();
			if (runModal) {
				try {
					ProgressMonitorDialog dlg = new ProgressMonitorDialog(window.getShell()); 
					dlg.run(true, true, ncdProcess);
				} catch (InvocationTargetException ex) {
					Throwable cause = ex.getCause();
					String msg = "NCD Data Reduction has failed";
					logger.error(msg, cause);
					Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, msg, cause);
					StatusManager.getManager().handle(status, StatusManager.BLOCK|StatusManager.SHOW);
					return Boolean.FALSE;
				} catch (InterruptedException ex) {
					Throwable cause = ex.getCause();
					String msg = "NCD Data Reduction was interrupted";
					logger.error(msg, cause);
					Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, msg, cause);
					StatusManager.getManager().handle(status, StatusManager.BLOCK|StatusManager.SHOW);
					return Boolean.FALSE;
				}
			} else {
			final Job ncdJob = new Job("Running NCD data reduction") {

				@Override
				protected IStatus run(IProgressMonitor monitor) {
					monitor.beginTask("Running NCD data reduction",context.getWorkAmount()*selObjects.length);
					ncdProcess.run(monitor);
					monitor.done();
					return Status.OK_STATUS;
				}
			};
			
			ncdJob.setUser(true);
			ncdJob.schedule();
			}
		} else {
			String msg = "Please select NeXus files to process in Project Explorer view before running NCD Data Reduction";
			Status status = new Status(IStatus.CANCEL, Activator.PLUGIN_ID, msg);
			StatusManager.getManager().handle(status, StatusManager.BLOCK | StatusManager.SHOW);
		}
		return Boolean.TRUE;
	}
	
	/**
	 * Get mask and region for plotting
	 * @param context
	 */
	private void createMaskAndRegion(IDataReductionContext context) {
		
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IViewPart activePlot = page.findView(PlotView.ID + "DP");
		if (activePlot instanceof PlotView) {
			BooleanDataset mask = null;
			SectorROI intSector = null;
			IPlottingSystem plotSystem = PlottingFactory.getPlottingSystem(((PlotView) activePlot).getPartName());
			if (context.isEnableSector()) {
				Collection<IRegion> sectorRegions = plotSystem.getRegions(RegionType.SECTOR);
				if (sectorRegions == null || sectorRegions.isEmpty()) {
					throw new IllegalArgumentException(NcdMessages.NO_SEC_DATA);
				}
				if (sectorRegions.size() > 1) {
					throw new IllegalArgumentException(NcdMessages.NO_SEC_SUPPORT);
				}
				IROI intBase = sectorRegions.iterator().next().getROI();
				if (intBase instanceof SectorROI) {
					intSector = (SectorROI) intBase;
					context.setSector(intSector);
				} else {
					throw new IllegalArgumentException(NcdMessages.NO_SEC_DATA);
				}
			}
			if (context.isEnableMask()) {
				IPlottingSystem activePlotSystem = PlottingFactory.getPlottingSystem(((PlotView) activePlot).getPartName());
				Collection<ITrace> imageTraces = activePlotSystem.getTraces(IImageTrace.class);
				if (imageTraces == null || imageTraces.isEmpty()) {
					mask = MaskingTool.getSavedMask();
				} else {
					ITrace imageTrace = imageTraces.iterator().next();
					if (imageTrace instanceof IImageTrace) {
						mask = (BooleanDataset) ((IImageTrace) imageTrace).getMask();
					}
				}
				if (mask != null) {
					context.setMask(new BooleanDataset(mask));
				} else {
					throw new IllegalArgumentException(NcdMessages.NO_MASK_IMAGE);
				}
			}
		}
		
	}


	/**
	 * Set data available in NcdProcessingSourceProvider to the IDataReductionContext
	 * @param context
	 * @param window
	 */
	private void createData(final IDataReductionContext context, IWorkbenchWindow window) {
		
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		
		NcdProcessingSourceProvider ncdNormalisationSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.NORMALISATION_STATE);
		context.setEnableNormalisation(ncdNormalisationSourceProvider.isEnableNormalisation());		
		
		NcdProcessingSourceProvider ncdBackgroundSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BACKGROUD_STATE);
		context.setEnableBackground(ncdBackgroundSourceProvider.isEnableBackground());
		
		NcdProcessingSourceProvider ncdResponseSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RESPONSE_STATE);
		context.setEnableDetectorResponse(ncdResponseSourceProvider.isEnableDetectorResponse());
		
		NcdProcessingSourceProvider ncdSectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SECTOR_STATE);
		boolean enableSector = ncdSectorSourceProvider.isEnableSector();
		context.setEnableSector(enableSector);
	
		NcdProcessingSourceProvider ncdInvariantSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.INVARIANT_STATE);
		context.setEnableInvariant(ncdInvariantSourceProvider.isEnableInvariant());

		NcdProcessingSourceProvider ncdAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AVERAGE_STATE);
		context.setEnableAverage(ncdAverageSourceProvider.isEnableAverage());
		
		SaxsPlotsSourceProvider loglogPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.LOGLOG_STATE);
		context.setEnableLogLogPlot(enableSector && loglogPlotSourceProvider.isEnableLogLog());
		
		SaxsPlotsSourceProvider guinierPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.GUINIER_STATE);
		context.setEnableGuinierPlot(enableSector && guinierPlotSourceProvider.isEnableGuinier());
		
		SaxsPlotsSourceProvider porodPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.POROD_STATE);
		context.setEnablePorodPlot(enableSector && porodPlotSourceProvider.isEnablePorod());
		
		SaxsPlotsSourceProvider kratkyPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.KRATKY_STATE);
		context.setEnableKratkyPlot(enableSector && kratkyPlotSourceProvider.isEnableKratky());
		
		SaxsPlotsSourceProvider zimmPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.ZIMM_STATE);
		context.setEnableZimmPlot(enableSector && zimmPlotSourceProvider.isEnableZimm());
		
		SaxsPlotsSourceProvider debyebuechePlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.DEBYE_BUECHE_STATE);
		context.setEnableDebyeBuechePlot(enableSector && debyebuechePlotSourceProvider.isEnableDebyeBueche());
		
		NcdProcessingSourceProvider ncdScalerSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SCALER_STATE);	
		context.setCalibrationName(ncdScalerSourceProvider.getScaler());
		
		NcdProcessingSourceProvider ncdWaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXSDETECTOR_STATE);
		context.setEnableWaxs(ncdWaxsDetectorSourceProvider.isEnableWaxs());
		context.setWaxsDetectorName(ncdWaxsDetectorSourceProvider.getWaxsDetector());

		NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);		
		context.setEnableSaxs(ncdSaxsDetectorSourceProvider.isEnableSaxs());
		context.setSaxsDetectorName(ncdSaxsDetectorSourceProvider.getSaxsDetector());

		NcdProcessingSourceProvider ncdDataSliceSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.DATASLICE_STATE);
		context.setDataSliceInput(ncdDataSliceSourceProvider.getDataSlice());

		NcdProcessingSourceProvider ncdBkgSliceSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGSLICE_STATE);
		context.setBgSliceInput(ncdBkgSliceSourceProvider.getBkgSlice());
		
		NcdProcessingSourceProvider ncdGridAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.GRIDAVERAGE_STATE);		
		context.setGridAverageSlice(ncdGridAverageSourceProvider.getGridAverage());

		NcdProcessingSourceProvider ncdBgFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGFILE_STATE);
		context.setBgPath(ncdBgFileSourceProvider.getBgFile());

		NcdProcessingSourceProvider ncdDrFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.DRFILE_STATE);
		context.setDrFile(ncdDrFileSourceProvider.getDrFile());

		NcdProcessingSourceProvider ncdWorkingDirSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WORKINGDIR_STATE);		
		context.setWorkingDir(ncdWorkingDirSourceProvider.getWorkingDir());

		NcdProcessingSourceProvider ncdMaskSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.MASK_STATE);		
        context.setEnableMask(enableSector && ncdMaskSourceProvider.isEnableMask());

		NcdProcessingSourceProvider ncdRadialSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RADIAL_STATE);
		context.setEnableRadial(ncdRadialSourceProvider.isEnableRadial());

		NcdProcessingSourceProvider ncdAzimuthSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AZIMUTH_STATE);
		context.setEnableAzimuthal(ncdAzimuthSourceProvider.isEnableAzimuthal());
		
		NcdProcessingSourceProvider ncdFastIntSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.FASTINT_STATE);
		context.setEnableFastIntegration(ncdFastIntSourceProvider.isEnableFastIntegration());

		NcdProcessingSourceProvider ncdAbsScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSSCALING_STATE);
		context.setAbsScaling(ncdAbsScaleSourceProvider.getAbsScaling());

		NcdProcessingSourceProvider ncdSampleThicknessSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAMPLETHICKNESS_STATE);
		context.setSampleThickness(ncdSampleThicknessSourceProvider.getSampleThickness());
		
		NcdProcessingSourceProvider ncdBgScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGSCALING_STATE);
		context.setBgScaling(ncdBgScaleSourceProvider.getBgScaling());
		
		NcdCalibrationSourceProvider ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
		context.setDetWaxsInfo(ncdDetectorSourceProvider.getNcdDetectors().get(context.getWaxsDetectorName()));
		context.setDetSaxsInfo(ncdDetectorSourceProvider.getNcdDetectors().get(context.getSaxsDetectorName()));
		context.setScalerData(ncdDetectorSourceProvider.getNcdDetectors().get(context.getCalibrationName()));
		
		NcdCalibrationSourceProvider ncdCalibrationSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		CalibrationResultsBean crb = (CalibrationResultsBean) ncdCalibrationSourceProvider.getCurrentState().get(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		context.setCalibrationResults(crb);
		
		NcdProcessingSourceProvider ncdEnergySourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ENERGY_STATE);
		Amount<Energy> energy = ncdEnergySourceProvider.getEnergy();
		if (energy != null) {
			context.setEnergy(energy.doubleValue(SI.KILO(NonSI.ELECTRON_VOLT)));
		}
	}
	
	private boolean isCalibrationResultsBean(IDataReductionContext context) {
		CalibrationResultsBean crb = context.getCalibrationResults();
		if (crb != null) {
			if (crb.containsKey(context.getSaxsDetectorName())) {
				Unit<Length> qaxisUnit = crb.getUnit(context.getSaxsDetectorName());
				Amount<ScatteringVectorOverDistance> slope = crb.getGradient(context.getSaxsDetectorName());
				Amount<ScatteringVector> intercept = crb.getIntercept(context.getSaxsDetectorName());
				if (slope != null && intercept != null && qaxisUnit != null) {
					return true;
				}
			}
		}
		return false;
	}
}
