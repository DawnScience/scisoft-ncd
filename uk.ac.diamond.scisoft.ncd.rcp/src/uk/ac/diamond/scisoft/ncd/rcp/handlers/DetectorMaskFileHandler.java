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

import org.dawb.common.ui.plot.AbstractPlottingSystem;
import org.dawb.common.ui.plot.PlottingFactory;
import org.dawb.common.ui.plot.trace.IImageTrace;
import org.dawb.common.ui.plot.trace.ITrace;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.services.ISourceProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Node;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class DetectorMaskFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(DetectorMaskFileHandler.class);
	private AbstractDataset mask;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final ISelection selection = HandlerUtil.getCurrentSelection(event);

		if (selection instanceof IStructuredSelection) {
			if (((IStructuredSelection)selection).toList().size() == 1) {

				final Object sel = ((IStructuredSelection)selection).getFirstElement();
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				Shell shell = window.getShell();
				try {
					ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
					NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
					String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
					if (detectorSaxs != null) {
						String maskFilename;
						if (sel instanceof IFile)
							maskFilename = ((IFile)sel).getLocation().toString();
						else 
							maskFilename = ((File)sel).getAbsolutePath();
						HDF5File qaxisFile = new HDF5Loader(maskFilename).loadTree();
						HDF5Node node = qaxisFile.findNodeLink("/entry1/"+detectorSaxs+"_processing/SectorIntegration/mask").getDestination();
						if (node == null) {
							String msg = "No mask data found in "+ maskFilename;
							return DetectorMaskErrorDialog(shell, msg, null);
						}
						
						mask = (AbstractDataset) ((HDF5Dataset) node).getDataset().getSlice();
						
						IWorkbenchPage page = window.getActivePage();
						IViewPart activePlot = page.findView(PlotView.ID + "DP");
						if (activePlot instanceof PlotView) {
							AbstractPlottingSystem activePlotSystem = PlottingFactory
									.getPlottingSystem(((PlotView) activePlot).getPartName());
							ITrace imageTrace = activePlotSystem.getTraces(IImageTrace.class).iterator().next();
							if (imageTrace != null && imageTrace instanceof IImageTrace)
								((IImageTrace) imageTrace).setMask(DatasetUtils.cast(mask, AbstractDataset.BOOL));
						}
						return Status.OK_STATUS;
					} 
					String msg = "SCISOFT NCD: No SAXS detector is selected";
					return DetectorMaskErrorDialog(shell, msg, null);

				} catch (Exception e) {
					String msg = "SCISOFT NCD: Failed to read detector mask from "+((IFile)sel).getLocation().toString();
					return DetectorMaskErrorDialog(shell, msg, e);
				}
			}

		}

		return null;
	}

	private IStatus DetectorMaskErrorDialog(Shell shell, String msg, Exception e) {
		logger.error(msg, e);
		Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, msg, e);
		ErrorDialog.openError(shell, "Mask loading error", "Error loading detector mask", status);
		return Status.CANCEL_STATUS;
	}
}
