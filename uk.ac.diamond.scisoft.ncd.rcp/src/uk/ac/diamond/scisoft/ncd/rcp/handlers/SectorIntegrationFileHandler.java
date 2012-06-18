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

import org.dawb.common.ui.plot.AbstractPlottingSystem;
import org.dawb.common.ui.plot.PlottingFactory;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.services.ISourceProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Node;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class SectorIntegrationFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(SectorIntegrationFileHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		Shell shell = window.getShell();

		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			if (((IStructuredSelection)selection).toList().size() == 1 && (((IStructuredSelection)selection).getFirstElement() instanceof IFile)) {

				final Object sel = ((IStructuredSelection)selection).getFirstElement();
				try {
					String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
					if (detectorSaxs != null) {
						String dataFilename;
						if (sel instanceof IFile)
							dataFilename = ((IFile)sel).getLocation().toString();
						else 
							dataFilename = ((File)sel).getAbsolutePath();
						HDF5File qaxisFile = new HDF5Loader(dataFilename).loadTree();
						HDF5Node node = qaxisFile.findNodeLink("/entry1/"+detectorSaxs+"/data").getDestination();
						if (node == null) {
							String msg = "No data found in "+ dataFilename;
							return DataLoadErrorDialog(shell, msg, null);
						}
						
						// Open first frame if dataset has miltiple images
						int[] shape = ((HDF5Dataset) node).getDataset().squeeze().getShape();
						int[] start = new int[shape.length];
						int[] stop = Arrays.copyOf(shape, shape.length);
						Arrays.fill(start, 0, shape.length, 0);
						if (shape.length > 2)
							Arrays.fill(stop, 0, shape.length - 2, 1);
						AbstractDataset data = (AbstractDataset) ((HDF5Dataset) node).getDataset().squeeze().getSlice(start, stop, null).clone();
						
						AbstractPlottingSystem activePlotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
						if (activePlotSystem != null)
							activePlotSystem.createPlot2D(data, null, new NullProgressMonitor());
						
						return Status.OK_STATUS;
					} 
					return Status.CANCEL_STATUS;

				} catch (Exception e) {
					logger.error("Can not load Nexus tree from "+((IFile)sel).getLocation().toString()+" file", e);
					return Status.CANCEL_STATUS;
				}
			}

		}
		return null;
	}
	
	private IStatus DataLoadErrorDialog(Shell shell, String msg, Exception e) {
		logger.error(msg, e);
		Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, msg, e);
		ErrorDialog.openError(shell, "Image loading error", "Error loading calibration image", status);
		return Status.CANCEL_STATUS;
	}
}
