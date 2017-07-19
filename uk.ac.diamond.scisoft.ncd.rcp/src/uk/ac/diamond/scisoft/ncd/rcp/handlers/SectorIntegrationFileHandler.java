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
import java.util.Arrays;
import java.util.Collection;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.dawnsci.plotting.tools.diffraction.DiffractionDefaultMetadata;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.dawnsci.analysis.api.diffraction.DetectorProperties;
import org.eclipse.dawnsci.analysis.api.diffraction.DiffractionCrystalEnvironment;
import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.dawnsci.analysis.api.tree.Tree;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.ShapeUtils;
import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.statushandlers.StatusManager;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;

public class SectorIntegrationFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(SectorIntegrationFileHandler.class);
	
	private static final String PLOT_NAME = "Dataset Plot";
	private static final String ENERGY_NODE = "/entry1/instrument/monochromator/energy";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		NcdProcessingSourceProvider ncdEnergySourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ENERGY_STATE);

		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection
			&& ((IStructuredSelection) selection).toList().size() == 1
			&& (((IStructuredSelection) selection).getFirstElement() instanceof IFile)) {

				final Object sel = ((IStructuredSelection) selection).getFirstElement();
				String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
				if (detectorSaxs == null) {
					return errorDialog(NcdMessages.NO_SAXS_DETECTOR, null);
				}
				String dataFileName;
				if (sel instanceof IFile) {
					dataFileName = ((IFile) sel).getLocation().toString();
				} else {
					dataFileName = ((File) sel).getAbsolutePath();
				}
				try {
					Tree dataTree = new HDF5Loader(dataFileName).loadTree();
					NodeLink nodeLink = dataTree.findNodeLink(ENERGY_NODE);
					Double energy = null;   // energy value in keV
					if (nodeLink != null) {
	    				energy = ((DataNode) nodeLink.getDestination()).getDataset().getSlice().getDouble(0);
	    				ncdEnergySourceProvider.setEnergy(Amount.valueOf(energy, SI.KILO(NonSI.ELECTRON_VOLT)));
	    				logger.info("Energy value : {}", energy);
					} else {
						logger.info(NLS.bind(NcdMessages.NO_ENERGY_DATA, dataFileName));
					}
					nodeLink = dataTree.findNodeLink("/entry1/" + detectorSaxs + "/data");
					if (nodeLink == null) {
						return errorDialog(NLS.bind(NcdMessages.NO_IMAGE_DATA, dataFileName), null);
					}

					// Open first frame if dataset has miltiple images
					DataNode node = (DataNode) nodeLink.getDestination();
					final int[] shape = node.getDataset().getShape();
					
					final int[] sqShape = ShapeUtils.squeezeShape(shape, true);
					if (sqShape.length > 2) {
						MessageDialog.openWarning(window.getShell(),"Multiple data frames detected","WARNING: This dataset contains several frames. By default, the first frame will be loaded for NCD calibration." +
								" If you require different frame, please switch to NCD Data Reduction or DExplore perspectives, plot the required frame in Dataset Plot and return to continue NCD calibration process.");
					}
					
					final int[] start = new int[shape.length];
					final int[] stop = Arrays.copyOf(shape, shape.length);
					Arrays.fill(start, 0, shape.length, 0);
					if (shape.length > 2) {
						Arrays.fill(stop, 0, shape.length - 2, 1);
					}
					Dataset data = DatasetUtils.convertToDataset(node.getDataset()
							.getSlice(start, stop, null).clone()).squeeze();

					IPlottingSystem<?> activePlotSystem = PlottingFactory.getPlottingSystem(PLOT_NAME);
					if (activePlotSystem != null) {
						activePlotSystem.createPlot2D(data, null, new NullProgressMonitor());
					}

					IPlottingSystem<?> plotSystem = PlottingFactory.getPlottingSystem(PLOT_NAME);
					ILoaderService loaderService = Activator.getService(ILoaderService.class);
					IDiffractionMetadata lockedMeta = loaderService.getLockedDiffractionMetaData();
					if (lockedMeta == null) {
						loaderService.setLockedDiffractionMetaData(DiffractionDefaultMetadata.getDiffractionMetadata(data.getShape()));
					}
					DetectorProperties detectorProperties = loaderService.getLockedDiffractionMetaData().getDetector2DProperties();
					DiffractionCrystalEnvironment crystalProperties = loaderService.getLockedDiffractionMetaData().getDiffractionCrystalEnvironment();
					
					Collection<IRegion> sectorRegions = plotSystem.getRegions(RegionType.SECTOR);
					if (sectorRegions != null && !sectorRegions.isEmpty() && sectorRegions.size() == 1) {
						final SectorROI sroi = (SectorROI) sectorRegions.iterator().next().getROI();
						detectorProperties.setBeamCentreCoords(sroi.getPoint());
					}
					if (energy != null) {
						crystalProperties.setWavelengthFromEnergykeV(energy);
					}
				} catch (Exception e) {
					return errorDialog(NLS.bind(NcdMessages.NO_IMAGE_DATA, dataFileName), e);
				}
		}
		return null;
	}
	
	private IStatus errorDialog(String msg, Exception e) {
		logger.error(msg, e);
		Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, msg, e);
		StatusManager.getManager().handle(status, StatusManager.SHOW);
		return null;
	}
}
