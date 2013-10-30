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
			handlerService.executeCommand("uk.ac.diamond.scisoft.ncd.rcp.process.wizard", null);
		} catch (Exception ex) {
			MessageDialog dialog = new MessageDialog(window.getShell(), "Error", null, 
					"Error:"+ex.toString(), MessageDialog.ERROR,
					new String[] { "OK" }, 0);
			dialog.open();
		}
		return true;

	}

}