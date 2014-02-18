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

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.statushandlers.StatusManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class BackgroundSubtractionFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(BackgroundSubtractionFileHandler.class);
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		
		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
			if (detectorSaxs == null) {
				return errorDialog(NcdMessages.NO_SAXS_DETECTOR, null);
			}
			NcdProcessingSourceProvider ncdBgFileSourceProvider = (NcdProcessingSourceProvider) service
					.getSourceProvider(NcdProcessingSourceProvider.BKGFILE_STATE);

			String fileName;
			if (((IStructuredSelection) selection).getFirstElement() instanceof IFile) {
				fileName = ((IFile) ((IStructuredSelection) selection).getFirstElement()).getLocation().toString();
			} else {
				fileName = ((File) ((IStructuredSelection) selection).getFirstElement()).getAbsolutePath();
			}

			try {
				HDF5File bgFile = new HDF5Loader(fileName).loadTree();
				HDF5NodeLink node = bgFile.findNodeLink("/entry1/" + detectorSaxs + "/data");
				if (node == null) {
					return errorDialog(NLS.bind(NcdMessages.NO_BG_DATA, fileName), null);
				}

				ncdBgFileSourceProvider.setBgFile(fileName);
			} catch (Exception e) {
				return errorDialog(NLS.bind(NcdMessages.NO_BG_DATA, fileName), e);
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
