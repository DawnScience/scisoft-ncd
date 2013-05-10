/*-
 * Copyright © 2013 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;

import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.wizards.NcdDataReductionWizard;

public class NcdDataReductionWizardHandler extends AbstractHandler {

	public static String COMMAND_ID = NcdProcessingSourceProvider.OPEN_NCD_WIZARD;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window.getActivePage();
		IStructuredSelection sel = (IStructuredSelection)page.getSelection();

		if (sel != null) {
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
		} else {
			// Customized MessageDialog with configured buttons
//			MessageDialog dialog = new MessageDialog(window.getShell(), "Information", null, "Please select a file with detector information", MessageDialog.INFORMATION,
//					new String[] { "OK" }, 0);
//			dialog.open();
//			return null;
		}

		WizardDialog wizardDialog = new WizardDialog(Display.getDefault().getActiveShell(), new NcdDataReductionWizard());
		if (wizardDialog.open() == Window.OK) {
			System.out.println("Ok pressed");
		} else {
			System.out.println("Cancel pressed");
		}

		return null;
	}

}
