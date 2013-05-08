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

import org.dawb.common.ui.plot.PlottingFactory;
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.trace.IImageTrace;
import org.dawnsci.plotting.api.trace.ITrace;
import org.dawnsci.plotting.tools.masking.MaskingTool;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.statushandlers.StatusManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class DetectorMaskFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(DetectorMaskFileHandler.class);
	private AbstractDataset mask;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final ISelection selection = HandlerUtil.getCurrentSelection(event);

		if (selection instanceof IStructuredSelection) {

			final Object sel = ((IStructuredSelection) selection).getFirstElement();
			IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
			NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
			String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
			if (detectorSaxs == null) {
				return ErrorDialog(NcdMessages.NO_SAXS_DETECTOR, null);
			}
			String maskFileName;
			if (sel instanceof IFile) {
				maskFileName = ((IFile) sel).getLocation().toString();
			} else {
				maskFileName = ((File) sel).getAbsolutePath();
			}
			try {
				HDF5File dataTree = new HDF5Loader(maskFileName).loadTree();
				HDF5NodeLink node = dataTree.findNodeLink("/entry1/" + detectorSaxs
						+ "_processing/SectorIntegration/mask");
				if (node == null) {
					return ErrorDialog(NLS.bind(NcdMessages.NO_MASK_DATA, maskFileName), null);
				}
				mask = (AbstractDataset) ((HDF5Dataset) node.getDestination()).getDataset().getSlice();

				IWorkbenchPage page = window.getActivePage();
				IViewPart activePlot = page.findView(PlotView.ID + "DP");
				if (activePlot instanceof PlotView) {
					IPlottingSystem activePlotSystem = PlottingFactory.getPlottingSystem(((PlotView) activePlot).getPartName());
					Collection<ITrace> imageTraces = activePlotSystem.getTraces(IImageTrace.class);
					if (imageTraces == null || imageTraces.isEmpty()) {
						throw new IllegalArgumentException(NcdMessages.NO_IMAGE_PLOT);
					}
					ITrace imageTrace = imageTraces.iterator().next();
					if (imageTrace != null && imageTrace instanceof IImageTrace) {
						BooleanDataset boolMask = (BooleanDataset) DatasetUtils.cast(mask, AbstractDataset.BOOL);
						BooleanDataset savedMask = MaskingTool.getSavedMask();
						if (savedMask != null) {
							MessageDialog dialog = new MessageDialog(window.getShell(), "Loading Detector Mask", null,
									"Should the mask loaded from the file be superimposed on the existing mask or overwrite it?", MessageDialog.QUESTION, new String[] {"Superimpose",  "Overwrite"}, 0);
							int overwrite = dialog.open();
							if (overwrite == 0) {
								boolMask = boolMask.imultiply(savedMask);
							}
						}
						((IImageTrace) imageTrace).setMask(boolMask);
						MaskingTool.setSavedMask(boolMask, true);
					}
				}

			} catch (Exception e) {
				return ErrorDialog(NLS.bind(NcdMessages.NO_MASK_DATA, maskFileName), e);
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
