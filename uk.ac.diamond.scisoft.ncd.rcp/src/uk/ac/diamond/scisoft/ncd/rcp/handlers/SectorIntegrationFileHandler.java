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

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.dawb.common.ui.plot.IPlottingSystem;
import org.dawb.common.ui.plot.PlottingFactory;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.statushandlers.StatusManager;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class SectorIntegrationFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(SectorIntegrationFileHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		NcdProcessingSourceProvider ncdEnergySourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ENERGY_STATE);

		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			if (((IStructuredSelection) selection).toList().size() == 1
					&& (((IStructuredSelection) selection).getFirstElement() instanceof IFile)) {

				final Object sel = ((IStructuredSelection) selection).getFirstElement();
				String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
				if (detectorSaxs == null) {
					return ErrorDialog(NcdMessages.NO_SAXS_DETECTOR, null);
				}
				String dataFileName;
				if (sel instanceof IFile) {
					dataFileName = ((IFile) sel).getLocation().toString();
				} else {
					dataFileName = ((File) sel).getAbsolutePath();
				}
				try {
					HDF5File dataTree = new HDF5Loader(dataFileName).loadTree();
					HDF5NodeLink nodeLink = dataTree.findNodeLink("/entry1/instrument/monochromator/energy");
					if (nodeLink != null) {
	    				double energy = ((HDF5Dataset) nodeLink.getDestination()).getDataset().getSlice().getDouble(0);
	    				ncdEnergySourceProvider.setEnergy(Amount.valueOf(energy, SI.KILO(NonSI.ELECTRON_VOLT)));
	    				logger.info("Energy value : {}", energy);
					} else {
						logger.info(NLS.bind(NcdMessages.NO_ENERGY_DATA, dataFileName));
					}
					nodeLink = dataTree.findNodeLink("/entry1/" + detectorSaxs + "/data");
					if (nodeLink == null) {
						return ErrorDialog(NLS.bind(NcdMessages.NO_IMAGE_DATA, dataFileName), null);
					}

					// Open first frame if dataset has miltiple images
					HDF5Dataset node = (HDF5Dataset) nodeLink.getDestination();
					int[] shape = node.getDataset().squeeze().getShape();
					int[] start = new int[shape.length];
					int[] stop = Arrays.copyOf(shape, shape.length);
					Arrays.fill(start, 0, shape.length, 0);
					if (shape.length > 2) {
						Arrays.fill(stop, 0, shape.length - 2, 1);
					}
					AbstractDataset data = (AbstractDataset) node.getDataset().squeeze()
							.getSlice(start, stop, null).clone();

					IPlottingSystem activePlotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
					if (activePlotSystem != null) {
						activePlotSystem.createPlot2D(data, null, new NullProgressMonitor());
					}

				} catch (Exception e) {
					return ErrorDialog(NLS.bind(NcdMessages.NO_IMAGE_DATA, dataFileName), e);
				}
			}

		}
		return null;
	}
	
	private IStatus ErrorDialog(String msg, Exception e) {
		logger.error(msg, e);
		Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, msg, e);
		StatusManager.getManager().handle(status, StatusManager.SHOW);
		return null;
	}
}
