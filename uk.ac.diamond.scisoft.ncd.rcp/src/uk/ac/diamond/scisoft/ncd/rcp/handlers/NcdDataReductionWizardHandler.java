/*
 * Copyright 2013 Diamond Light Source Ltd.
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

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.wizards.NcdDataReductionWizard;

public class NcdDataReductionWizardHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window.getActivePage();
		IStructuredSelection sel = (IStructuredSelection)page.getSelection();

		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		NcdCalibrationSourceProvider ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
		if (sel != null) {
			Map<String, NcdDetectorSettings> ncdDetectors = ncdDetectorSourceProvider.getNcdDetectors(); 
				if (ncdDetectors == null || ncdDetectors.isEmpty()) {
					//run the detector information command
					IHandlerService handlerService = (IHandlerService) window.getService(IHandlerService.class);
					try {
						handlerService.executeCommand("uk.ac.diamond.scisoft.ncd.rcp.readDetectorInfo", null);
					} catch (Exception ex) {
						MessageDialog dialog = new MessageDialog(window.getShell(), "Error", null, 
								"Error:"+ex.toString(), MessageDialog.ERROR,
								new String[] { "OK" }, 0);
						dialog.open();
					}
			}
			
			WizardDialog wizardDialog = new WizardDialog(Display.getDefault().getActiveShell(), new NcdDataReductionWizard());
			wizardDialog.open();
		}


		return null;
	}

}
