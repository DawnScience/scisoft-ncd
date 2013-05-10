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

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionSectorIntegrationPage extends AbstractNcdDataReductionPage {

	private Button radialButton;
	private Button azimuthalButton;
	private Button fastIntButton;
	private Button useMask;
	private NcdProcessingSourceProvider ncdRadialSourceProvider;
	private NcdProcessingSourceProvider ncdAzimuthSourceProvider;
	private NcdProcessingSourceProvider ncdFastIntSourceProvider;
	private NcdProcessingSourceProvider ncdMaskSourceProvider;
	
	public static int PAGENUMBER = 3;

	public NcdDataReductionSectorIntegrationPage() {
		super("Sector integration");
		setTitle("2. Sector integration");
		setDescription("Select Sector Integration options");
		currentPageNumber = PAGENUMBER;
	}

	@Override
	public void createControl(Composite parent) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);

		ncdRadialSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RADIAL_STATE);
		ncdAzimuthSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AZIMUTH_STATE);
		ncdFastIntSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.FASTINT_STATE);
		ncdMaskSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.MASK_STATE);
//		ncdRadialSourceProvider.addSourceProviderListener(this);
//		ncdAzimuthSourceProvider.addSourceProviderListener(this);
//		ncdFastIntSourceProvider.addSourceProviderListener(this);
//		ncdMaskSourceProvider.addSourceProviderListener(this);

		Composite container = new Composite(parent, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		Group subContainer = new Group(container, SWT.NONE);
		subContainer.setText("Sector Integration Parameters");
		subContainer.setToolTipText("Select Sector Integration options");
		subContainer.setLayout(new GridLayout(2, false));
		subContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		radialButton = new Button(subContainer, SWT.CHECK);
		radialButton.setText("Radial Profile");
		radialButton.setToolTipText("Activate radial profile calculation");
		radialButton.setSelection(ncdRadialSourceProvider.isEnableRadial());
		radialButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdRadialSourceProvider.setEnableRadial(radialButton.getSelection());
			}
		});

		azimuthalButton = new Button(subContainer, SWT.CHECK);
		azimuthalButton.setText("Azimuthal Profile");
		azimuthalButton.setToolTipText("Activate azimuthal profile calculation");
		azimuthalButton.setSelection(ncdAzimuthSourceProvider.isEnableAzimuthal());
		azimuthalButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdAzimuthSourceProvider.setEnableAzimuthal(azimuthalButton.getSelection());
			}
		});

		fastIntButton = new Button(subContainer, SWT.CHECK);
		fastIntButton.setText("Fast Integration");
		fastIntButton.setToolTipText("Use fast algorithm for profile calculations");
		fastIntButton.setSelection(ncdFastIntSourceProvider.isEnableFastIntegration());
		fastIntButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdFastIntSourceProvider.setEnableFastIntegration(fastIntButton.getSelection());
			}

		});
		
		useMask = new Button(subContainer, SWT.CHECK);
		useMask.setText("Apply detector mask");
		useMask.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		useMask.setSelection(ncdMaskSourceProvider.isEnableMask());
		useMask.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdMaskSourceProvider.setEnableMask(useMask.getSelection());
			}
		});
		
		setControl(container);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void sourceChanged(int sourcePriority, Map sourceValuesByName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, Object sourceValue) {
		// TODO Auto-generated method stub
		
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
	public boolean isCurrentNcdWizardpage() {
		return isCurrentPage();
	}
}
