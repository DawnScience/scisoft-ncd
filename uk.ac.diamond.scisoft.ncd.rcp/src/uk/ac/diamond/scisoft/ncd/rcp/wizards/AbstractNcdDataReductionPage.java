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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;

public abstract class AbstractNcdDataReductionPage extends WizardPage {

	protected int currentPageNumber;
	protected static Map<Integer, Boolean> activePages = new HashMap<Integer, Boolean>(7);
	
	public AbstractNcdDataReductionPage(String name) {
		super(name);
		activePages.put(currentPageNumber, false);

	}

	@Override
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		container.setLayout(layout);
		layout.numColumns = 1;
		layout.verticalSpacing = 9;
		
		//TODO
		
		setControl(container);
	}

	@Override
	public void setVisible(boolean vis) {
		super.setVisible(vis);
		if (!vis) {
			setErrorMessage(null);
		}
	}

	@Override
	public IWizardPage getNextPage() {
		IWizard wizard = getWizard();
		IWizardPage[] pages = wizard.getPages();
		if(currentPageNumber == 0) {
			return super.getNextPage();
		}
		for (int i = currentPageNumber; i < pages.length; i++) {
	
			if(i < (pages.length - 1) && activePages.get(i+1)) {
				return pages[i+1];
			}
		}
		return null;
	}

}