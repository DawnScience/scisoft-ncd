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

}
