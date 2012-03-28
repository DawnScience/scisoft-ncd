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
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.HDFArray;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.State;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.IFileSystem;
import org.eclipse.core.filesystem.URIUtil;
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
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;
import uk.ac.diamond.scisoft.ncd.reduction.LazyNcdProcessing;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;
import uk.ac.gda.common.rcp.util.EclipseUtils;

public class DataReductionHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(DataReductionHandler.class);

	private static final String NXEntryClassName = "NXentry";
	private static final String NXDataClassName = "NXdata";
	
	private String detectorWaxs;
	private String detectorSaxs;
	private String calibration;
	private Integer dimWaxs, dimSaxs;
	private boolean enableWaxs, enableSaxs, enableBackground;
	private String workingDir;
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window.getActivePage();
		final IStructuredSelection sel = (IStructuredSelection)page.getSelection();
		if (sel != null) {
			
			final LazyNcdProcessing processing = new LazyNcdProcessing();
			final LazyNcdProcessing bgProcessing = new LazyNcdProcessing();

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

			if (flags.isEnableBackground()) {
				try {
					NcdReductionFlags bgFlags = new NcdReductionFlags();
					NcdDetectors bgDetectors = new NcdDetectors();
					
					readDataReductionStages(bgFlags);
					readDetectorInformation(bgFlags, bgDetectors);
					readDataReductionOptions(bgFlags, bgProcessing);
					
					bgFlags.setEnableInvariant(false);
					bgFlags.setEnableBackground(false);
					bgProcessing.setFlags(bgFlags);
					bgProcessing.setNcdDetectors(ncdDetectors);
					
					Integer bgFirstFrame = NcdDataReductionParameters.getBgFirstFrame();
					Integer bgLastFrame = NcdDataReductionParameters.getBgLastFrame();
					String bgFrameSelection = NcdDataReductionParameters.getBgAdvancedSelection();
					
					bgProcessing.setFirstFrame(bgFirstFrame);
					bgProcessing.setLastFrame(bgLastFrame);
					bgProcessing.setFrameSelection(bgFrameSelection);
				} catch (Exception e) {
					logger.error("SCISOFT NCD: Error reading data reduction parameters", e);
					Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, e.getMessage());
					ErrorDialog.openError(window.getShell(), "Data reduction error", "Error reading data reduction parameters", status);
					return Boolean.FALSE;
				}
			}
			
			detectorWaxs = ncdDetectors.getDetectorWaxs();
			detectorSaxs = ncdDetectors.getDetectorSaxs();
			calibration = NcdDataReductionParameters.getCalList().getItem(NcdDataReductionParameters.getCalList().getSelectionIndex());
			dimWaxs = ncdDetectors.getDimWaxs();
			dimSaxs = ncdDetectors.getDimSaxs();
			enableWaxs = flags.isEnableWaxs();
			enableSaxs = flags.isEnableSaxs();
			enableBackground = flags.isEnableBackground();
			workingDir = NcdDataReductionParameters.getWorkingDirectory();
			final String bgPath = NcdDataReductionParameters.getBgFile();
			final String bgName = FilenameUtils.getName(bgPath);
			
			Job ncdJob = new Job("Running NCD data reduction") {

				@Override
				protected IStatus run(IProgressMonitor monitor) {

					Object[] selObjects = sel.toArray();
					
					int idxMonitor = 0;
					if (enableWaxs) idxMonitor += 6;
					if (enableSaxs) idxMonitor += 6;
					monitor.beginTask("Running NCD data reduction",idxMonitor*selObjects.length);
					
					IFileSystem fileSystem = EFS.getLocalFileSystem();
					try {
						if (enableBackground) {
							final String bgFilename = createResultsFile(bgName, bgPath, "background");
							if (enableWaxs)
								bgProcessing.execute(detectorWaxs, dimWaxs, bgFilename, monitor);
							if (enableSaxs)
								bgProcessing.execute(detectorSaxs, dimSaxs, bgFilename, monitor);
							processing.setBgFile(bgFilename);
						}
					} catch (Exception e) {
						logger.error("SCISOFT NCD: Error processing background file", e);
						return Status.CANCEL_STATUS;
					}
					
					for (int i = 0; i < selObjects.length; i++) {
						final String inputfileName, inputfileExtension, inputfilePath;
						if (selObjects[i] instanceof IFile) {
							inputfilePath = ((IFile)selObjects[i]).getLocation().toString();
							inputfileExtension = ((IFile)selObjects[i]).getFileExtension();
							inputfileName = ((IFile)selObjects[i]).getName().toString();
						} else {
							inputfilePath = ((File)selObjects[i]).getAbsolutePath();
							inputfileExtension = FilenameUtils.getExtension(inputfilePath);
							inputfileName = FilenameUtils.getName(inputfilePath);
						}
						if (!inputfileExtension.equals("nxs"))
							continue;
						logger.info("Processing: " + inputfileName + " " + selObjects[i].getClass().toString());
						try {
							final String filename = createResultsFile(inputfileName, inputfilePath, "results");
							IFileStore outputFile = fileSystem.getStore(URIUtil.toURI(filename));
							
							if (monitor.isCanceled()) {
								outputFile.delete(EFS.NONE, new NullProgressMonitor());
								return Status.CANCEL_STATUS;
							}
							
							if (enableWaxs) {
								processing.setBgDetector(detectorWaxs+"_result");
								processing.execute(detectorWaxs, dimWaxs, filename, monitor);
							}
							
							if (monitor.isCanceled()) {
								outputFile.delete(EFS.NONE, new NullProgressMonitor());
								return Status.CANCEL_STATUS;
							}
							
							if (enableSaxs) {
								processing.setBgDetector(detectorSaxs+"_result");
								processing.execute(detectorSaxs, dimSaxs, filename, monitor);
							}
							
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
		String qUnit = NcdDataReductionParameters.getQUnit();
		
		String bgFile = null;
		Double bgScaling = null;
		if (flags.isEnableBackground()) {
			bgFile = NcdDataReductionParameters.getBgFile();
			bgScaling = NcdDataReductionParameters.getBgScale();
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
		processing.setCalibration(calibration);
		processing.setNormChannel(normChannel);
		processing.setEnableMask(enableMask);
		processing.setSlope(qGradient);
		processing.setIntercept(qIntercept);
		processing.setUnit(qUnit);
		
	}
	
	private String generateDateTimeStamp() {

		Date date = new Date();

		SimpleDateFormat format =
			new SimpleDateFormat("ddMMyy_HHmmss_");

		return format.format(date);
	}
	
	private String createResultsFile(String inputfileName, String inputfilePath, String prefix) throws HDF5Exception, URISyntaxException {
		String datetime = generateDateTimeStamp();
		String detNames = "_" + ((enableWaxs) ? detectorWaxs : "") + ((enableSaxs) ? detectorSaxs : "") + "_";
		final String filename = workingDir + File.separator + prefix + detNames + datetime + inputfileName;
		
		int fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
		H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_STRONG);
		int fid = H5.H5Fcreate(filename, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT,fapl);  
		H5.H5Pclose(fapl);
		
		int[] libversion = new int[3];
		H5.H5get_libversion(libversion);
		putattr(fid, "HDF5_version", StringUtils.join(ArrayUtils.toObject(libversion), "."));
		putattr(fid, "file_name", filename);
		
		Date date = new Date();
		SimpleDateFormat format =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		String dt =  format.format(date);
		putattr(fid, "file_time", dt);
		
		int entry_id = NcdNexusUtils.makegroup(fid, "entry1", NXEntryClassName);
		
		if (calibration != null) {
			int calib_id = NcdNexusUtils.makegroup(entry_id, calibration, NXDataClassName);
		    H5.H5Lcreate_external(inputfilePath, "/entry1/" + calibration + "/data", calib_id, "data", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		    H5.H5Gclose(calib_id);
		}
		
		if (enableWaxs)
			createDetectorNode(detectorWaxs, entry_id, inputfilePath);
		
		if (enableSaxs)
			createDetectorNode(detectorSaxs, entry_id, inputfilePath);
		
		H5.H5Gclose(entry_id);
		H5.H5Fclose(fid);
		
		return filename;
	}

	private void createDetectorNode(String detector, int entry_id, String inputfilePath) throws HDF5Exception, URISyntaxException {
		int detector_id = NcdNexusUtils.makegroup(entry_id, detector, NXDataClassName);
		DataSliceIdentifiers dataIDs = NcdNexusUtils.readDataId(inputfilePath, detector);
		boolean isNAPImount = H5.H5Aexists(dataIDs.dataset_id, "napimount");
		if (isNAPImount) {
			int attr_id = H5.H5Aopen(dataIDs.dataset_id, "napimount", HDF5Constants.H5P_DEFAULT);
			int type_id = H5.H5Aget_type(attr_id);
			int size = H5.H5Tget_size(type_id);
			byte[] link = new byte[size];
			H5.H5Aread(attr_id, type_id, link);
			
			String str = new String(link);
			final URI ulink = new URI(str);
			if (ulink.getScheme().equals("nxfile")) {
				String lpath = ulink.getPath();
				String ltarget = ulink.getFragment();
				File f = new File(lpath);
				if (!f.exists()) {
					logger.debug("File, {}, does not exist!", lpath);

					// see if linked file in same directory
					File file = new File(inputfilePath);
					f = new File(file.getParent(), f.getName());
					if (!f.exists()) {
						throw new HDF5Exception("File, " + lpath + ", does not exist");
					}
				}
				lpath = f.getAbsolutePath();
				H5.H5Lcreate_external(lpath, ltarget, detector_id, "data", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			} else {
				System.err.println("Wrong scheme: " + ulink.getScheme());
			}
			H5.H5Tclose(type_id);
			H5.H5Aclose(attr_id);
			
		} else
		    H5.H5Lcreate_external(inputfilePath, "/entry1/" + detector + "/data", detector_id, "data", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		
		H5.H5Gclose(detector_id);
	}
	
	private void putattr(int dataset_id, String name, Object value) throws NullPointerException, HDF5Exception {
		int attr_type = -1;
		int dataspace_id = -1;
		byte[] data = null;
		
		if (value instanceof String) {
			data = ((String) value).getBytes();
			attr_type = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attr_type, data.length);
		}
		if (value instanceof Integer) {
			HDFArray ha = new HDFArray(new int[] {(Integer) value});
			data = ha.byteify();
			attr_type = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_INT32);
		}
		dataspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
		int attribute_id = H5.H5Acreate(dataset_id, name, attr_type,
				dataspace_id, HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT);
		
		H5.H5Awrite(attribute_id, attr_type, data);
		
		H5.H5Tclose(attr_type);
		H5.H5Sclose(dataspace_id);
		H5.H5Aclose(attribute_id);
	}
}
