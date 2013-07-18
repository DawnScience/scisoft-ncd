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
import java.util.Collection;

import javax.measure.quantity.Energy;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

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
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.Activator;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.roi.IROI;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.reduction.service.CalibrationAbsentException;
import uk.ac.diamond.scisoft.ncd.reduction.service.IDataReductionContext;
import uk.ac.diamond.scisoft.ncd.reduction.service.IDataReductionService;

public class DataReductionHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(DataReductionHandler.class);
		
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window.getActivePage();
		final IStructuredSelection sel = (IStructuredSelection)page.getSelection();
				
		if (sel != null) {
			
			// We get the data reduction service using OSGI
			final IDataReductionService service = (IDataReductionService)Activator.getService(IDataReductionService.class);
			
			// Get data from NcdProcessingSourceProvider's and store in IDataReductionContext
			final IDataReductionContext context = service.createContext();
			createData(context, window);
		
			// Now we configure the context, which throws exceptions if 
			// the configuration is invalid.
			try {
				createMaskAndRegion(context);
				service.configure(context);
				
			} catch (CalibrationAbsentException exception) {
				boolean proceed = MessageDialog
						.openConfirm(
								window.getShell(),
								"Missing NCD calibration data",
								"IMPORTANT! NCD calibration data was not found for currently selected SAXS detector. " +
								"Please open NCD Calibration perspective to configure calibration data.\nProceed with data reduction anyway?");
				if (!proceed) {
					return Boolean.FALSE;
				}
			} catch (Exception e) {
				logger.error("SCISOFT NCD: Error reading data reduction parameters", e);
				Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, e.getMessage());
				ErrorDialog.openError(window.getShell(), "Data reduction error", "Error reading data reduction parameters", status);
				return Boolean.FALSE;
			}
			
			final Object[] selObjects = sel.toArray();
			
			// Process each file 
			final Job ncdJob = new Job("Running NCD data reduction") {

				@Override
				protected IStatus run(IProgressMonitor monitor) {

					monitor.beginTask("Running NCD data reduction",context.getWorkAmount()*selObjects.length);
							
					for (int i = 0; i < selObjects.length; i++) {
						final String inputfilePath;
						if (selObjects[i] instanceof IFile) {
							inputfilePath = ((IFile)selObjects[i]).getLocation().toString();
						} else {
							inputfilePath = ((File)selObjects[i]).getAbsolutePath();
						}
						if (inputfilePath==null) continue;
						
 					    try {
							IStatus status = service.process(inputfilePath, context, monitor);
							if (status.getSeverity()==IStatus.CANCEL) {
								monitor.done();
								return status;
							}
						} catch (Exception e) {
							logger.error("SCISOFT NCD: Error reading data reduction parameters", e);
							return new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, e.getMessage());
						}
					}
					monitor.done();
					return Status.OK_STATUS;
				}
			};
			
			ncdJob.setUser(true);
			ncdJob.schedule();				

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
				if (context.isEnableMask()) {
					IPlottingSystem activePlotSystem = PlottingFactory.getPlottingSystem(((PlotView) activePlot).getPartName());
					Collection<ITrace> imageTraces = activePlotSystem.getTraces(IImageTrace.class);
					if (imageTraces == null || imageTraces.isEmpty()) {
						mask = MaskingTool.getSavedMask();
					} else {
						ITrace imageTrace = imageTraces.iterator().next();
						if (imageTrace != null && imageTrace instanceof IImageTrace) {
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
		context.setEnableSector(ncdSectorSourceProvider.isEnableSector());
	
		NcdProcessingSourceProvider ncdInvariantSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.INVARIANT_STATE);
		context.setEnableInvariant(ncdInvariantSourceProvider.isEnableInvariant());

		NcdProcessingSourceProvider ncdAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AVERAGE_STATE);
		context.setEnableAverage(ncdAverageSourceProvider.isEnableAverage());
		
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
        context.setEnableMask(ncdMaskSourceProvider.isEnableMask());

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
		context.setEnergy(energy.doubleValue(SI.KILO(NonSI.ELECTRON_VOLT)));
	}

}
