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

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;

public class BackgroundSubtractionFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(BackgroundSubtractionFileHandler.class);
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			if (((IStructuredSelection)selection).toList().size() == 1 && (((IStructuredSelection)selection).getFirstElement() instanceof IFile)) {
				String fileName = ((IFile)((IStructuredSelection)selection).getFirstElement()).getLocation().toString();
				NcdDataReductionParameters.setBgFile(fileName);
			} else {
				logger.error("SCISOFT NCD: Error setting background subtraction file reference");
				Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, "Only single file can be used as a reference for background subtraction procedure.");
				ErrorDialog.openError(window.getShell(), "Background subtraction file selection error", "Please select one data file.", status);
			}
			
		}
		return null;
	}

}
