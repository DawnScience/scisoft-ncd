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
package uk.ac.diamond.scisoft.ncd.rcp.wizards;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionWizard extends Wizard {

	private INcdDataReductionWizardPage selectedNcdDataReductionPage;
	private List<INcdDataReductionWizardPage> ncdDataReductionPages;

	private NcdDataReductionSetupPage setupPage;
	private NcdDataReductionDetectorParameterPage detectorPage;
	private final Logger logger = LoggerFactory.getLogger(NcdDataReductionWizard.class);

	public NcdDataReductionWizard() {
		super();
		setNeedsProgressMonitor(true);
		this.ncdDataReductionPages = new ArrayList<INcdDataReductionWizardPage>(7);
		detectorPage = new NcdDataReductionDetectorParameterPage();
		setupPage = new NcdDataReductionSetupPage();
		addPage(detectorPage);
		addPage(setupPage);
		// Create map of possible pages, only one of which will be selected at one time.
		final IConfigurationElement[] ce = Platform.getExtensionRegistry().getConfigurationElementsFor("uk.ac.diamond.scisoft.ncd.rcp.ncdDataReductionPage");
		if (ce!=null) 
			for (IConfigurationElement e : ce) {
			
				try {
					final INcdDataReductionWizardPage p = (INcdDataReductionWizardPage)e.createExecutableExtension("datareduction_page");
					ncdDataReductionPages.add(p);
					addPage(p);
				} catch (CoreException e1) {
					logger .error("Cannot get page "+e.getAttribute("datareduction_page"), e1);
				}
			}
		this.selectedNcdDataReductionPage = ncdDataReductionPages.get(0);
	}

	@Override
	public boolean canFinish() {
		NcdProcessingSourceProvider provider = setupPage.getProvider();
		// We make visible the current page if it is an active one.
		if (setupPage.isPageComplete() && provider != null) {

			for (int i = 0; i < ncdDataReductionPages.size(); i++) {
				if(ncdDataReductionPages.get(i).isCurrentNcdWizardpage()){
					if(ncdDataReductionPages.get(i) instanceof NcdDataReductionResponsePage){
						ncdDataReductionPages.get(i).setVisible(provider.isEnableDetectorResponse());
					}
					if (ncdDataReductionPages.get(i) instanceof NcdDataReductionSectorIntegrationPage) {
						ncdDataReductionPages.get(i).setVisible(provider.isEnableSector());
					}
					if (ncdDataReductionPages.get(i) instanceof NcdDataReductionNormalisationPage) {
						ncdDataReductionPages.get(i).setVisible(provider.isEnableNormalisation());
					}
					if (ncdDataReductionPages.get(i) instanceof NcdDataReductionBackgroundPage) {
						ncdDataReductionPages.get(i).setVisible(provider.isEnableBackground());
					}
					if (ncdDataReductionPages.get(i) instanceof NcdDataReductionAveragePage) {
						ncdDataReductionPages.get(i).setVisible(provider.isEnableAverage());
					}
					return ncdDataReductionPages.get(i).isPageComplete();
				}
					
			}
		}
		if (setupPage.isPageComplete() && provider != null && selectedNcdDataReductionPage != null
				&& !selectedNcdDataReductionPage.isPageComplete()) {
			selectedNcdDataReductionPage.setProvider(provider);
			return false;
		}
		return setupPage.isPageComplete()
				&& (selectedNcdDataReductionPage == null || selectedNcdDataReductionPage.isPageComplete());
	}

	@Override
	public boolean performFinish() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();

		//run the data reduction command
		IHandlerService handlerService = (IHandlerService) window.getService(IHandlerService.class);
		try {
			//
//			String value = System.getProperty("called.in.code");

//			System.setProperty("called.in.code", "yes");
//			value = System.getProperty("called.in.code");
			handlerService.executeCommand("uk.ac.diamond.scisoft.ncd.rcp.process", null);
//			System.setProperty("called.in.code", "no");
		} catch (Exception ex) {
			MessageDialog dialog = new MessageDialog(window.getShell(), "Error", null, 
					"Error:"+ex.toString(), MessageDialog.ERROR,
					new String[] { "OK" }, 0);
			dialog.open();
		}
		return true;

	}

}