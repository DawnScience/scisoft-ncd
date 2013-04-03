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

import gda.analysis.io.ScanFileHolderException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.unit.SI;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.HDFArray;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.dawb.common.ui.plot.IPlottingSystem;
import org.dawb.common.ui.plot.PlottingFactory;
import org.dawb.common.ui.plot.region.IRegion;
import org.dawb.common.ui.plot.region.IRegion.RegionType;
import org.dawb.common.ui.plot.trace.IImageTrace;
import org.dawb.common.ui.plot.trace.ITrace;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.IFileSystem;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Node;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.roi.ROIBase;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.data.SliceInput;
import uk.ac.diamond.scisoft.ncd.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.reduction.LazyNcdProcessing;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

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

	private NcdProcessingSourceProvider ncdNormalisationSourceProvider, ncdScalerSourceProvider;
	private NcdProcessingSourceProvider ncdBackgroundSourceProvider;
	private NcdProcessingSourceProvider ncdResponseSourceProvider;
	private NcdProcessingSourceProvider ncdSectorSourceProvider;
	private NcdProcessingSourceProvider ncdInvariantSourceProvider;
	private NcdProcessingSourceProvider ncdAverageSourceProvider;
	private NcdProcessingSourceProvider ncdWaxsDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider;
	private NcdCalibrationSourceProvider ncdDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdDataSliceSourceProvider, ncdBkgSliceSourceProvider;
	private NcdProcessingSourceProvider ncdRadialSourceProvider, ncdAzimuthSourceProvider, ncdFastIntSourceProvider;
	private NcdProcessingSourceProvider ncdBgFileSourceProvider, ncdDrFileSourceProvider, ncdWorkingDirSourceProvider;

	private NcdProcessingSourceProvider ncdGridAverageSourceProvider;
	private NcdProcessingSourceProvider ncdAbsScaleSourceProvider;
	private NcdProcessingSourceProvider ncdSampleThicknessSourceProvider;
	private NcdProcessingSourceProvider ncdBgScaleSourceProvider;
	private NcdProcessingSourceProvider ncdNormChannelSourceProvider;
	private NcdProcessingSourceProvider ncdMaskSourceProvider;
	
	private void ConfigureNcdSourceProviders(IWorkbenchWindow window) {
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		
		ncdNormalisationSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.NORMALISATION_STATE);
		ncdBackgroundSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BACKGROUD_STATE);
		ncdResponseSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RESPONSE_STATE);
		ncdSectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SECTOR_STATE);
		ncdInvariantSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.INVARIANT_STATE);
		ncdAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AVERAGE_STATE);
		
		ncdScalerSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SCALER_STATE);
		
		ncdWaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXSDETECTOR_STATE);
		ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		
		ncdNormChannelSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.NORMCHANNEL_STATE);
		ncdDataSliceSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.DATASLICE_STATE);
		ncdBkgSliceSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGSLICE_STATE);
		ncdGridAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.GRIDAVERAGE_STATE);
		
		ncdBgFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGFILE_STATE);
		ncdDrFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.DRFILE_STATE);
		ncdWorkingDirSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WORKINGDIR_STATE);
		
		ncdMaskSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.MASK_STATE);
		
		ncdRadialSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RADIAL_STATE);
		ncdAzimuthSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AZIMUTH_STATE);
		ncdFastIntSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.FASTINT_STATE);
		
		ncdAbsScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSSCALING_STATE);
		ncdSampleThicknessSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAMPLETHICKNESS_STATE);
		ncdBgScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGSCALING_STATE);
		
		ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
	}
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window.getActivePage();
		final IStructuredSelection sel = (IStructuredSelection)page.getSelection();
		
		ConfigureNcdSourceProviders(window);
		
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
					
					bgFlags.setEnableBackground(false);
					bgProcessing.setFlags(bgFlags);
					bgProcessing.setNcdDetectors(ncdDetectors);
					
					SliceInput bgSliceInput = ncdBkgSliceSourceProvider.getBkgSlice();
					Integer bgFirstFrame = bgSliceInput.getStartFrame();
					Integer bgLastFrame = bgSliceInput.getStopFrame();
					String bgFrameSelection = bgSliceInput.getAdvancedSlice();
					
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
			
			enableWaxs = flags.isEnableWaxs();
			enableSaxs = flags.isEnableSaxs();
			enableBackground = flags.isEnableBackground();
			
			if (enableWaxs) {
				detectorWaxs = ncdWaxsDetectorSourceProvider.getWaxsDetector();
				dimWaxs = ncdDetectors.getDimWaxs();
			}
			if (enableSaxs) {
				detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
				dimSaxs = ncdDetectors.getDimSaxs();
			}
			
			if (flags.isEnableNormalisation()) {
				calibration = ncdScalerSourceProvider.getScaler();
			}
			
			final String bgPath = ncdBgFileSourceProvider.getBgFile();
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
							if (enableWaxs) {
								bgProcessing.execute(detectorWaxs, dimWaxs, bgFilename, monitor);
							}
							if (enableSaxs) {
								bgProcessing.execute(detectorSaxs, dimSaxs, bgFilename, monitor);
							}
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
						
						if (ignoreInputFile(inputfilePath, inputfileExtension)) {
							continue;
						}
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

				private boolean ignoreInputFile(String inputfilePath, String inputfileExtension) {
					if (!inputfileExtension.equals("nxs")) {
						return true;
					}
					HDF5File dataTree;
					try {
						dataTree = new HDF5Loader(inputfilePath).loadTree();
					} catch (Exception e) {
						logger.info("Error loading Nexus tree from {}", inputfilePath);
						return true;
					}
					if (enableWaxs) {
						HDF5NodeLink node = dataTree.findNodeLink("/entry1/" + detectorWaxs	+ "/data");
						if (node == null) {
							return true;
						}
					}
					
					if (enableSaxs) {
						HDF5NodeLink node = dataTree.findNodeLink("/entry1/" + detectorSaxs	+ "/data");
						if (node == null) {
							return true;
						}
					}
					
					return false;
				}
			};
			
			ncdJob.setUser(true);
			ncdJob.schedule();				

		}
		
		return null;
	}
	
	public void readDataReductionStages(NcdReductionFlags flags) {
		flags.setEnableWaxs(ncdWaxsDetectorSourceProvider.isEnableWaxs());
		flags.setEnableSaxs(ncdSaxsDetectorSourceProvider.isEnableSaxs());
		
		flags.setEnableNormalisation(ncdNormalisationSourceProvider.isEnableNormalisation());
		flags.setEnableBackground(ncdBackgroundSourceProvider.isEnableBackground());
		flags.setEnableDetectorResponse(ncdResponseSourceProvider.isEnableDetectorResponse());
		flags.setEnableSector(ncdSectorSourceProvider.isEnableSector());
		flags.setEnableInvariant(ncdInvariantSourceProvider.isEnableInvariant());
		flags.setEnableAverage(ncdAverageSourceProvider.isEnableAverage());
		
		if (flags.isEnableSector()) {
			flags.setEnableRadial(ncdRadialSourceProvider.isEnableRadial());
			flags.setEnableAzimuthal(ncdAzimuthSourceProvider.isEnableAzimuthal());
			flags.setEnableFastintegration(ncdFastIntSourceProvider.isEnableFastIntegration());
			if (!flags.isEnableRadial() && !flags.isEnableAzimuthal()) {
				throw new IllegalArgumentException(NcdMessages.NO_SEC_INT);
			}
		}
	}

	private void readDetectorInformation(final NcdReductionFlags flags, NcdDetectors ncdDetectors) throws ExecutionException {
		
		String detectorWaxs = null;
		NcdDetectorSettings detWaxsInfo = null;
		NcdDetectorSettings detSaxsInfo = null;
		String detectorSaxs = null;
		Amount<Length> pxWaxs = null;
		Amount<Length> pxSaxs = null;
		Integer dimWaxs = null ;
		Integer dimSaxs = null ;
		
		
		if (flags.isEnableWaxs()) {
			detectorWaxs = ncdWaxsDetectorSourceProvider.getWaxsDetector();
			detWaxsInfo = ncdDetectorSourceProvider.getNcdDetectors().get(detectorWaxs);
			if (detWaxsInfo != null) {
				pxWaxs = detWaxsInfo.getPxSize();
				dimWaxs = detWaxsInfo.getDimension();
				if ((detectorWaxs != null) && (pxWaxs != null)) {
					ncdDetectors.setDetectorWaxs(detectorWaxs);
					ncdDetectors.setPxWaxs(pxWaxs.doubleValue(SI.MILLIMETRE));
					ncdDetectors.setDimWaxs(dimWaxs);
				}
			}
		} 
		
		if (flags.isEnableSaxs()) {
			detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
			detSaxsInfo = ncdDetectorSourceProvider.getNcdDetectors().get(detectorSaxs);
			if (detSaxsInfo != null) {
				pxSaxs = detSaxsInfo.getPxSize();
				dimSaxs = detSaxsInfo.getDimension();
				if ((detectorSaxs != null) && (pxSaxs != null)) {
					ncdDetectors.setDetectorSaxs(detectorSaxs);
					ncdDetectors.setPxSaxs(pxSaxs.doubleValue(SI.MILLIMETRE));
					ncdDetectors.setDimSaxs(dimSaxs);
				}
			}
		}
		
		if (flags.isEnableWaxs()) {
			if (detectorWaxs == null || detWaxsInfo == null) {
				throw new ExecutionException("WAXS detector has not been selected");
			}
			if (pxWaxs == null) {
				throw new ExecutionException("WAXS detector pixel size has not been specified");
			}
			if (dimWaxs == null) {
				throw new ExecutionException("WAXS detector dimensionality has not been specified");
			}
		}
		
		if (flags.isEnableSaxs()) {
			if (detectorSaxs == null || detSaxsInfo == null) {
				throw new ExecutionException("SAXS detector has not been selected");
			}
			if (pxSaxs == null) {
				throw new ExecutionException("SAXS detector pixel size has not been specified");
			}
			if (dimSaxs == null) {
				throw new ExecutionException("SAXS detector dimensionality has not been specified");
			}
		}
		
	}

	public void readDataReductionOptions(NcdReductionFlags flags, LazyNcdProcessing processing) throws FileNotFoundException, IOException {

		workingDir = ncdWorkingDirSourceProvider.getWorkingDir();
		if (workingDir == null || workingDir.isEmpty()) {
			throw new IllegalArgumentException(NcdMessages.NO_WORKING_DIR);
		}
		File testDir = new File(workingDir);
		if (!(testDir.isDirectory())) {
			throw new FileNotFoundException(NLS.bind(NcdMessages.NO_WORKINGDIR_DATA, testDir.getCanonicalPath()));
		}
		if (!(testDir.canWrite())) {
			throw new IllegalArgumentException(NcdMessages.NO_WORKINGDIR_WRITE);
		}
		
		SliceInput dataSliceInput = ncdDataSliceSourceProvider.getDataSlice();
		Integer firstFrame = null;
		Integer lastFrame = null;
		String frameSelection = null;
		if (dataSliceInput != null) {
			firstFrame = dataSliceInput.getStartFrame();
			lastFrame = dataSliceInput.getStopFrame();
			frameSelection = dataSliceInput.getAdvancedSlice();
		}
		
		SliceInput gridAverageSlice = ncdGridAverageSourceProvider.getGridAverage();
		String gridAverage = null;
		if (gridAverageSlice != null) {
			gridAverage = gridAverageSlice.getAdvancedSlice();
		}
		
		String bgFile = null;
		Double bgScaling = null;
		if (flags.isEnableBackground()) {
			bgFile = ncdBgFileSourceProvider.getBgFile();
			bgScaling = ncdBgScaleSourceProvider.getBgScaling();
			
			if (bgFile == null) {
				throw new IllegalArgumentException(NcdMessages.NO_BG_FILE);
			}
			File testFile = new File(bgFile);
			if (!(testFile.isFile())) {
				throw new FileNotFoundException(NLS.bind(NcdMessages.NO_BG_DATA, testFile.getCanonicalPath()));
			}
			if (!(testFile.canRead())) {
				throw new IllegalArgumentException(NLS.bind(NcdMessages.NO_BG_READ, testFile.getCanonicalPath()));
			}
		}

		String drFile = null;
		if (flags.isEnableDetectorResponse()) {
			drFile = ncdDrFileSourceProvider.getDrFile();
			
			if (drFile == null) {
				throw new IllegalArgumentException(NcdMessages.NO_DR_FILE);
			}
			File testFile = new File(drFile);
			if (!(testFile.isFile())) {
				throw new FileNotFoundException(NLS.bind(NcdMessages.NO_DR_DATA, testFile.getCanonicalPath()));
			}
			if (!(testFile.canRead())) {
				throw new IllegalArgumentException(NLS.bind(NcdMessages.NO_DR_READ, testFile.getCanonicalPath()));
			}
		}
		
		int normChannel = -1;
		String calibration = null;
		Double absScaling = null;
		Double thickness = null;
		if (flags.isEnableNormalisation()) {
			normChannel = ncdNormChannelSourceProvider.getNormChannel();
			calibration = ncdScalerSourceProvider.getScaler();
			absScaling = ncdAbsScaleSourceProvider.getAbsScaling();
			thickness = ncdSampleThicknessSourceProvider.getSampleThickness();
		}
		
		boolean enableMask = ncdMaskSourceProvider.isEnableMask();

		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IViewPart activePlot = page.findView(PlotView.ID + "DP");
		if (activePlot instanceof PlotView) {
			BooleanDataset mask = null;
			SectorROI intSector = null;
			IPlottingSystem plotSystem = PlottingFactory.getPlottingSystem(((PlotView) activePlot).getPartName());
			if (flags.isEnableSector()) {
				Collection<IRegion> sectorRegions = plotSystem.getRegions(RegionType.SECTOR);
				if (sectorRegions == null || sectorRegions.isEmpty()) {
					throw new IllegalArgumentException(NcdMessages.NO_SEC_DATA);
				}
				if (sectorRegions.size() > 1) {
					throw new IllegalArgumentException(NcdMessages.NO_SEC_SUPPORT);
				}
				ROIBase intBase = sectorRegions.iterator().next().getROI();
				if (intBase instanceof SectorROI) {
					intSector = (SectorROI) intBase;
					int sym = intSector.getSymmetry(); 
					SectorROI tmpSector = intSector.copy();
					if ((sym != SectorROI.NONE) && (sym != SectorROI.FULL)) {
						tmpSector.setCombineSymmetry(true);
					}
					processing.setIntSector(tmpSector);
				}
				else {
					throw new IllegalArgumentException(NcdMessages.NO_SEC_DATA);
				}
				if (enableMask) {
					IPlottingSystem activePlotSystem = PlottingFactory.getPlottingSystem(((PlotView) activePlot).getPartName());
					Collection<ITrace> imageTraces = activePlotSystem.getTraces(IImageTrace.class);
					if (imageTraces == null || imageTraces.isEmpty())
						throw new IllegalArgumentException(NcdMessages.NO_IMAGE_PLOT);
					ITrace imageTrace = imageTraces.iterator().next();
					if (imageTrace != null && imageTrace instanceof IImageTrace)
						mask = (BooleanDataset) ((IImageTrace) imageTrace).getMask();
					if (mask != null) {
						processing.setMask(new BooleanDataset(mask));
						processing.setEnableMask(true);
					} else {
						throw new IllegalArgumentException(NcdMessages.NO_MASK_IMAGE);
					}
				}
			}
			IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			ISourceProviderService sourceProviderService = (ISourceProviderService) window.getService(ISourceProviderService.class);
			NcdCalibrationSourceProvider ncdCalibrationSourceProvider = (NcdCalibrationSourceProvider) sourceProviderService.getSourceProvider(NcdCalibrationSourceProvider.CALIBRATION_STATE);
			NcdProcessingSourceProvider ncdEnergySourceProvider = (NcdProcessingSourceProvider) sourceProviderService.getSourceProvider(NcdProcessingSourceProvider.ENERGY_STATE);
			CalibrationResultsBean crb = (CalibrationResultsBean) ncdCalibrationSourceProvider.getCurrentState().get(NcdCalibrationSourceProvider.CALIBRATION_STATE);
			Amount<Energy> energy = ncdEnergySourceProvider.getEnergy();
			//CalibrationResultsBean crb = null;
			//if (guiinfo.containsKey(GuiParameters.CALIBRATIONFUNCTIONNCD)) {
			//	Serializable bd = guiinfo.get(GuiParameters.CALIBRATIONFUNCTIONNCD);
            //
			//	if (bd != null && bd instanceof CalibrationResultsBean)
			//		crb = (CalibrationResultsBean) bd;
			//}
			processing.setCrb(crb);
			processing.setEnergy(energy);
		}
		
		processing.setBgFile(bgFile);
		if (absScaling != null && thickness != null) {
			processing.setAbsScaling(absScaling / thickness);
		}
		processing.setBgScaling(bgScaling);
		processing.setDrFile(drFile);
		processing.setFirstFrame(firstFrame);
		processing.setLastFrame(lastFrame);
		processing.setFrameSelection(frameSelection);
		processing.setGridAverageSelection(gridAverage);
		processing.setCalibration(calibration);
		processing.setNormChannel(normChannel);
	}
	
	private String generateDateTimeStamp() {

		Date date = new Date();

		SimpleDateFormat format =
			new SimpleDateFormat("ddMMyy_HHmmss");

		return format.format(date);
	}
	
	private String createResultsFile(String inputfileName, String inputfilePath, String prefix) throws HDF5Exception, URISyntaxException {
		String datetime = generateDateTimeStamp();
		String detNames = "_" + ((enableWaxs) ? detectorWaxs : "") + ((enableSaxs) ? detectorSaxs : "") + "_";
		final String filename = workingDir + File.separator + prefix + "_" + FilenameUtils.getBaseName(inputfileName) + detNames + datetime + ".nxs";
		
		int fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
		H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_STRONG);
		int fid = H5.H5Fcreate(filename, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, fapl);  
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
		
		try {
			writeNCDMetadata(entry_id, inputfilePath);
		} catch (ScanFileHolderException e) {
			logger.warn("Couldn't open scan data file. Scan metadata won't be written into NCD processing results file", e);
		}
		
		if (enableWaxs) {
			createDetectorNode(detectorWaxs, entry_id, inputfilePath);
		}
		
		if (enableSaxs)  {
			createDetectorNode(detectorSaxs, entry_id, inputfilePath);
		}
		
		H5.H5Gclose(entry_id);
		H5.H5Fclose(fid);
		
		return filename;
	}

	private void writeNCDMetadata(int entry_id, String inputfilePath) throws ScanFileHolderException, HDF5Exception {
		HDF5File inputFileTree = new HDF5Loader(inputfilePath).loadTree();
		
		writeStringMetadata("/entry1/entry_identifier", "entry_identifier", entry_id, inputFileTree);
		writeStringMetadata("/entry1/scan_command", "scan_command", entry_id, inputFileTree);
		writeStringMetadata("/entry1/scan_identifier", "scan_identifier", entry_id, inputFileTree);
		writeStringMetadata("/entry1/title", "title", entry_id, inputFileTree);
			
	}
	
	private void writeStringMetadata(String nodeName, String textName, int entry_id, HDF5File inputFileTree) throws HDF5Exception {
		
		HDF5Node node;
		HDF5NodeLink nodeLink;
		nodeLink = inputFileTree.findNodeLink(nodeName);
		if (nodeLink != null) {
			node = nodeLink.getDestination();
			if (node instanceof HDF5Dataset) {
				String text = ((AbstractDataset) ((HDF5Dataset) node).getDataset()).getString(0);
				
				int text_type = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
				H5.H5Tset_size(text_type, text.length());
				int text_id = NcdNexusUtils.makedata(entry_id, textName, text_type, 1, new long[] {1});
				int filespace_id = H5.H5Dget_space(text_id);
				int memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
				H5.H5Sselect_all(filespace_id);
				H5.H5Dwrite(text_id, text_type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, text.getBytes());
					
				H5.H5Sclose(filespace_id);
				H5.H5Sclose(memspace_id);
				H5.H5Tclose(text_type);
				H5.H5Dclose(text_id);
			}
		}
	}

	private void createDetectorNode(String detector, int entry_id, String inputfilePath) throws HDF5Exception, URISyntaxException {
		
		int detector_id = NcdNexusUtils.makegroup(entry_id, detector, NXDataClassName);
		
		int file_handle = H5.H5Fopen(inputfilePath, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		int entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		int detector_group_id = H5.H5Gopen(entry_group_id, detector, HDF5Constants.H5P_DEFAULT);
		int input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		boolean isNAPImount = H5.H5Aexists(input_data_id, "napimount");
		if (isNAPImount) {
			int attr_id = H5.H5Aopen(input_data_id, "napimount", HDF5Constants.H5P_DEFAULT);
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
		} else {
		    H5.H5Lcreate_external(inputfilePath, "/entry1/" + detector + "/data", detector_id, "data", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		}
		
		H5.H5Gclose(detector_id);
		
		H5.H5Dclose(input_data_id);
		H5.H5Gclose(detector_group_id);
		H5.H5Gclose(entry_group_id);
		H5.H5Fclose(file_handle);
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
