/*-
 * Copyright © 2011 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import java.io.Serializable;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.State;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.IFileSystem;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationResultsBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.roi.MaskingBean;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;
import uk.ac.diamond.scisoft.ncd.reduction.LazyNcdProcessing;
import uk.ac.gda.common.rcp.util.EclipseUtils;

public class DataReductionHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(DataReductionHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window.getActivePage();
		final IStructuredSelection sel = (IStructuredSelection)page.getSelection();
		if (sel != null) {
			
			final LazyNcdProcessing processing = new LazyNcdProcessing();

			NcdReductionFlags flags = new NcdReductionFlags();
			NcdDetectors ncdDetectors = new NcdDetectors();
			try {
				readDataReductionStages(flags);
				readDetectorInformation(flags, ncdDetectors);
				readDataReductionOptions(flags, processing);
				
				processing.setFlags(flags);
				processing.setNcdDetectors(ncdDetectors);
			}
			catch (Exception e) {
				logger.error("SCISOFT NCD: Error reading data reduction parameters", e);
				Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, e.getMessage());
				ErrorDialog.openError(window.getShell(), "Data reduction error", "Error reading data reduction parameters", status);
				return Boolean.FALSE;
			}

			final String detectorWaxs = ncdDetectors.getDetectorWaxs();
			final String detectorSaxs = ncdDetectors.getDetectorSaxs();
			final Integer dimWaxs = ncdDetectors.getDimWaxs();
			final Integer dimSaxs = ncdDetectors.getDimSaxs();
			final boolean enableWaxs = flags.isEnableWaxs();
			final boolean enableSaxs = flags.isEnableSaxs();
			final String workingDir = NcdDataReductionParameters.getWorkingDirectory();
			
			Job ncdJob = new Job("Running NCD data reduction") {

				@Override
				protected IStatus run(IProgressMonitor monitor) {

					Object[] selObjects = sel.toArray();
					
					int idxMonitor = 0;
					if (enableWaxs) idxMonitor += 6;
					if (enableSaxs) idxMonitor += 6;
					monitor.beginTask("Running NCD data reduction",idxMonitor*selObjects.length);
					
					for (int i = 0; i < selObjects.length; i++) {
						String inputfileExtension = ((IFile)selObjects[i]).getFileExtension();
						if (!inputfileExtension.equals("nxs"))
							continue;
						String tmpfilePath = ((IFile)selObjects[i]).getLocation().toString();
						String inputfileName = ((IFile)selObjects[i]).getName().toString();
						logger.info("Processing: " + inputfileName + " " + selObjects[i].getClass().toString());
						try {
							String detNames = "_" + ((enableWaxs) ? detectorWaxs : "") + ((enableSaxs) ? detectorSaxs : "") + "_";
							String datetime = generateDateTimeStamp();
							final String filename = workingDir + "/results" + detNames + datetime + inputfileName;
							
							IFileSystem fileSystem = EFS.getLocalFileSystem();
							IFileStore inputFile = fileSystem.getStore(URI.create(tmpfilePath));
							IFileStore outputFile = fileSystem.getStore(URI.create(filename));
							inputFile.copy(outputFile, EFS.OVERWRITE, new NullProgressMonitor());
							
							// Check that results file is writable
							IFileInfo info = outputFile.fetchInfo();
							if (info.exists() && info.getAttribute(EFS.ATTRIBUTE_READ_ONLY)) {
								info.setAttribute(EFS.ATTRIBUTE_OWNER_WRITE, true);
								outputFile.putInfo(info, EFS.SET_ATTRIBUTES, new NullProgressMonitor());
							}
							
							if (monitor.isCanceled()) {
								outputFile.delete(EFS.NONE, new NullProgressMonitor());
								return Status.CANCEL_STATUS;
							}
							
							if (enableWaxs)
								processing.execute(detectorWaxs, dimWaxs, filename, monitor);
							
							if (monitor.isCanceled()) {
								outputFile.delete(EFS.NONE, new NullProgressMonitor());
								return Status.CANCEL_STATUS;
							}
							
							if (enableSaxs)
								processing.execute(detectorSaxs, dimSaxs, filename, monitor);
							
							if (monitor.isCanceled()) {
								outputFile.delete(EFS.NONE, new NullProgressMonitor());
								return Status.CANCEL_STATUS;
							}
							
/*							Display.getDefault().syncExec(new Runnable() {

								@Override
								public void run() {
									try {
										IEditorPart editor = EclipseUtils.openExternalEditor(filename);
										window.getActivePage().activate(editor);
									} catch (Exception e) {
										logger.error("SCISOFT NCD: Could not open data reduction results file", e);
										Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage());
										ErrorDialog.openError(window.getShell(), "Data reduction error", "Could not open data reduction results file", status);
									}
								}
							});
*/


						} catch (final Exception e) {
							logger.error("SCISOFT NCD: Error running NCD data reduction", e);
/*							Display.getDefault().syncExec(new Runnable() {
                            
								@Override
								public void run() {
									Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage());
									ErrorDialog.openError(window.getShell(), "Data reduction error", "Error running NCD data reduction", status);
								}
							});
							return Status.CANCEL_STATUS;
*/						}
					}

					monitor.done();
					return Status.OK_STATUS;
				}

				private String generateDateTimeStamp() {

					Date date = new Date();

					SimpleDateFormat format =
						new SimpleDateFormat("ddMMyy_HHmmss_");

					return format.format(date);
				}
			};
			
			ncdJob.setUser(true);
			ncdJob.schedule();				

		} else
			return Boolean.FALSE;
		return Boolean.TRUE;
	}
	
	public void readDataReductionStages(NcdReductionFlags flags) {

		final ICommandService service = (ICommandService) PlatformUI.getWorkbench().getService(ICommandService.class);

		Boolean enableWaxs = readReductionStage(service, WaxsDataReductionHandler.COMMAND_ID,
				WaxsDataReductionHandler.STATE_ID);

		Boolean enableSaxs = readReductionStage(service, SaxsDataReductionHandler.COMMAND_ID,
				SaxsDataReductionHandler.STATE_ID);
		
		Boolean enableAverage = readReductionStage(service, AverageHandler.COMMAND_ID,
				AverageHandler.STATE_ID);

		Boolean enableBackground = readReductionStage(service, BackgroundSubtractionHandler.COMMAND_ID,
				BackgroundSubtractionHandler.STATE_ID);

		Boolean enableDetectorResponse = readReductionStage(service, DetectorResponseHandler.COMMAND_ID,
				DetectorResponseHandler.STATE_ID);
		
		Boolean enableInvariant = readReductionStage(service, InvariantHandler.COMMAND_ID,
				InvariantHandler.STATE_ID);

		Boolean enableNormalisation = readReductionStage(service, NormalisationHandler.COMMAND_ID,
				NormalisationHandler.STATE_ID);
		
		Boolean enableSector = readReductionStage(service, SectorIntegrationHandler.COMMAND_ID,
				SectorIntegrationHandler.STATE_ID);
		
		if (enableWaxs) flags.setEnableWaxs(true);
		if (enableSaxs) flags.setEnableSaxs(true);
		if (enableNormalisation) flags.setEnableNormalisation(true);
		if (enableBackground) flags.setEnableBackground(true);
		if (enableDetectorResponse) flags.setEnableDetectorResponse(true);
		if (enableSector) flags.setEnableSector(true);
		if (enableInvariant) flags.setEnableInvariant(true);
		if (enableAverage) flags.setEnableAverage(true);
		
	}

	private Boolean readReductionStage(final ICommandService service, String COMMAND_ID, String STATE_ID) {

		Command command = service.getCommand(COMMAND_ID);
		State state = command.getState(STATE_ID);
		return (Boolean)state.getValue();
	}

	private void readDetectorInformation(final NcdReductionFlags flags, NcdDetectors ncdDetectors) throws ExecutionException {
		
		String detectorWaxs = null;
		String detectorSaxs = null;
		Double pxWaxs = null;
		Double pxSaxs = null;
		Integer dimWaxs = null ;
		Integer dimSaxs = null ;
		
		
		if (flags.isEnableWaxs()) {
			int idxWaxs = NcdDataReductionParameters.getDetListWaxs().getSelectionIndex();
			if (idxWaxs >= 0) {
				detectorWaxs = NcdDataReductionParameters.getDetListWaxs().getItem(idxWaxs);
				pxWaxs = NcdDataReductionParameters.getWaxsPixel(false);
				dimWaxs = NcdDataReductionParameters.getDimData(detectorWaxs);
				if ((detectorWaxs!=null) && (pxWaxs != null)) {
					ncdDetectors.setDetectorWaxs(detectorWaxs);
					ncdDetectors.setPxWaxs(pxWaxs);
					ncdDetectors.setDimWaxs(dimWaxs);
				}
			}
		} 
		
		if (flags.isEnableSaxs()) {
			int idxSaxs = NcdDataReductionParameters.getDetListSaxs().getSelectionIndex();
			if (idxSaxs >= 0) {
				detectorSaxs = NcdDataReductionParameters.getDetListSaxs().getItem(idxSaxs);
				pxSaxs = NcdDataReductionParameters.getSaxsPixel(false);
				dimSaxs = NcdDataReductionParameters.getDimData(detectorSaxs);
				if ((detectorSaxs != null) && (pxSaxs != null)) {
					ncdDetectors.setDetectorSaxs(detectorSaxs);
					ncdDetectors.setPxSaxs(pxSaxs);
					ncdDetectors.setDimSaxs(dimSaxs);
				}
			}
		}
		
		if (flags.isEnableWaxs()) {
			if (detectorWaxs == null)
				throw new ExecutionException("WAXS detector has not been selected");
			if (pxWaxs == null)
				throw new ExecutionException("WAXS detector pixel size has not been specified");
			if (dimWaxs == null)
				throw new ExecutionException("WAXS detector dimensionality has not been specified");
		}
		
		if (flags.isEnableSaxs()) {
			if (detectorSaxs == null)
				throw new ExecutionException("SAXS detector has not been selected");
			if (pxSaxs == null)
				throw new ExecutionException("SAXS detector pixel size has not been specified");
			if (dimSaxs == null)
				throw new ExecutionException("SAXS detector dimensionality has not been specified");
		}
		
	}

	public void readDataReductionOptions(NcdReductionFlags flags, LazyNcdProcessing processing) throws PartInitException {

		final ICommandService service = (ICommandService) PlatformUI.getWorkbench().getService(ICommandService.class);

		Integer firstFrame = NcdDataReductionParameters.getDetFirstFrame();
		Integer lastFrame = NcdDataReductionParameters.getDetLastFrame();
		String frameSelection = NcdDataReductionParameters.getDetAdvancedSelection();
		String gridAverage = NcdDataReductionParameters.getGridAverageSelection();
		
		Double qGradient = NcdDataReductionParameters.getQGradient();
		Double qIntercept = NcdDataReductionParameters.getQIntercept();
		
		String bgFile = null;
		Double bgScaling = null;
		Integer bgFirstFrame = null;
		Integer bgLastFrame = null;
		String bgFrameSelection = null;
		if (flags.isEnableBackground()) {
			bgFile = NcdDataReductionParameters.getBgFile();
			bgScaling = NcdDataReductionParameters.getBgScale();
			bgFirstFrame = NcdDataReductionParameters.getBgFirstFrame();
			bgLastFrame = NcdDataReductionParameters.getBgLastFrame();
			bgFrameSelection = NcdDataReductionParameters.getBgAdvancedSelection();
		}

		String drFile = null;
		if (flags.isEnableDetectorResponse())
			drFile = NcdDataReductionParameters.getDrFile();
		
		int normChannel = -1;
		String calibration = null;
		Double absScaling = null;
		if (flags.isEnableNormalisation()) {
			normChannel = NcdDataReductionParameters.getNormChan().getSelection();
			calibration = NcdDataReductionParameters.getCalList().getItem(NcdDataReductionParameters.getCalList().getSelectionIndex());
			absScaling = NcdDataReductionParameters.getAbsScale();
		}
		
		Boolean enableMask = readReductionStage(service, DetectorMaskHandler.COMMAND_ID,
				DetectorMaskHandler.STATE_ID);

		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IViewPart activePlot = page.findView(PlotView.ID + "DP");
		if (activePlot instanceof PlotView) {
			GuiBean guiinfo = ((PlotView)page.showView(PlotView.ID + "DP")).getGUIInfo();
			BooleanDataset mask = null;
			if (enableMask) {
				if (guiinfo.containsKey(GuiParameters.MASKING)) {
					if (guiinfo.get(GuiParameters.MASKING) instanceof MaskingBean) {
						MaskingBean mb = (MaskingBean)guiinfo.get(GuiParameters.MASKING);
						mask = mb.getMask();
					}
				}
			}
			SectorROI intSector = null;
			if (guiinfo.containsKey(GuiParameters.ROIDATA)) {
				if (guiinfo.get(GuiParameters.ROIDATA) instanceof SectorROI) {
					intSector = (SectorROI)guiinfo.get(GuiParameters.ROIDATA);
				}
				else flags.setEnableSector(false);
			}
			else flags.setEnableSector(false);

			CalibrationResultsBean crb = null;
			if (guiinfo.containsKey(GuiParameters.CALIBRATIONFUNCTIONNCD)) {
				Serializable bd = guiinfo.get(GuiParameters.CALIBRATIONFUNCTIONNCD);

				if (bd != null && bd instanceof CalibrationResultsBean)
					crb = (CalibrationResultsBean) bd;
			}
			processing.setMask(mask);
			processing.setIntSector(intSector);
			processing.setCrb(crb);
		}
		
		processing.setBgFile(bgFile);
		processing.setAbsScaling(absScaling);
		processing.setBgScaling(bgScaling);
		processing.setDrFile(drFile);
		processing.setFirstFrame(firstFrame);
		processing.setLastFrame(lastFrame);
		processing.setFrameSelection(frameSelection);
		processing.setGridAverageSelection(gridAverage);
		processing.setBgFirstFrame(bgFirstFrame);
		processing.setBgLastFrame(bgLastFrame);
		processing.setBgFrameSelection(bgFrameSelection);
		processing.setCalibration(calibration);
		processing.setNormChannel(normChannel);
		processing.setEnableMask(enableMask);
		processing.setSlope(qGradient);
		processing.setIntercept(qIntercept);
		
	}

}
