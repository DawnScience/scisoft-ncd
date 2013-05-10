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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISourceProviderListener;

import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public abstract class AbstractNcdDataReductionPage extends WizardPage implements INcdDataReductionWizardPage, ISourceProviderListener {

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
	public NcdProcessingSourceProvider getProvider() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setProvider(NcdProcessingSourceProvider provider) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isActive() {
//		return isActive;
		return activePages.get(currentPageNumber);	
	}

	@Override
	public void setActive(boolean isActive){
		
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
		if(currentPageNumber == 0)
			return super.getNextPage();
		for (int i = currentPageNumber; i < pages.length; i++) {
	
			if(i<pages.length-1 &&((INcdDataReductionWizardPage)pages[i+1]).isActive())
				return pages[i+1];
		}
		return null;
	}

	public boolean isCurrentNcdWizardPage(){
		return isCurrentPage();
	}
}