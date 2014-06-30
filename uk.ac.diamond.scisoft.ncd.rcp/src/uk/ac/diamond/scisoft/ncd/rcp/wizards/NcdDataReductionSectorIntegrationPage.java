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

import org.apache.commons.math3.util.Pair;
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

import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.core.rcp.SaxsPlotsSourceProvider;

public class NcdDataReductionSectorIntegrationPage extends AbstractNcdDataReductionPage {

	private Button radialButton;
	private Button azimuthalButton;
	private Button fastIntButton;
	private Button useMask;
	private Button loglogButton, guinierButton, porodButton, kratkyButton, zimmButton, debyebuecheButton;
	
	private NcdProcessingSourceProvider ncdRadialSourceProvider;
	private NcdProcessingSourceProvider ncdAzimuthSourceProvider;
	private NcdProcessingSourceProvider ncdFastIntSourceProvider;
	private NcdProcessingSourceProvider ncdMaskSourceProvider;
	
	private SaxsPlotsSourceProvider loglogPlotSourceProvider;
	private SaxsPlotsSourceProvider guinierPlotSourceProvider;
	private SaxsPlotsSourceProvider porodPlotSourceProvider;
	private SaxsPlotsSourceProvider kratkyPlotSourceProvider;
	private SaxsPlotsSourceProvider zimmPlotSourceProvider;
	private SaxsPlotsSourceProvider debyebuechePlotSourceProvider;
	
	protected static final int PAGENUMBER = 3;

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
		loglogPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.LOGLOG_STATE);
		guinierPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.GUINIER_STATE);
		porodPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.POROD_STATE);
		kratkyPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.KRATKY_STATE);
		zimmPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.ZIMM_STATE);
		debyebuechePlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.DEBYE_BUECHE_STATE);

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
		
		Group saxsPlotComp = new Group(container, SWT.NONE);
		saxsPlotComp.setText("1D SAXS Analysis Data");
		saxsPlotComp.setToolTipText("Include in results files data for making 1D SAXS analysis plots");
		GridLayout gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		saxsPlotComp.setLayout(gl);
		saxsPlotComp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		{
			Composite g = new Composite(saxsPlotComp, SWT.NONE);
			g.setLayout(gl);
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1));
			
			// TODO Replace all these Buttons with a loop over SaxsAnalysisPlotType.values()
			loglogButton = new Button(g, SWT.CHECK);
			loglogButton.setText(SaxsAnalysisPlotType.LOGLOG_PLOT.getName());
			Pair<String, String> axesNames = SaxsAnalysisPlotType.LOGLOG_PLOT.getAxisNames();
			String toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			loglogButton.setToolTipText(toolTipText);
			loglogButton.setSelection(loglogPlotSourceProvider.isEnableLogLog());
			loglogButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					loglogPlotSourceProvider.setEnableLogLog(loglogButton.getSelection());
				}
			});
			
			guinierButton = new Button(g, SWT.CHECK);
			guinierButton.setText(SaxsAnalysisPlotType.GUINIER_PLOT.getName());
			axesNames = SaxsAnalysisPlotType.GUINIER_PLOT.getAxisNames();
			toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			guinierButton.setToolTipText(toolTipText);
			guinierButton.setSelection(guinierPlotSourceProvider.isEnableGuinier());
			guinierButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					guinierPlotSourceProvider.setEnableGuinier(guinierButton.getSelection());
				}
			});
			
			porodButton = new Button(g, SWT.CHECK);
			porodButton.setText(SaxsAnalysisPlotType.POROD_PLOT.getName());
			axesNames = SaxsAnalysisPlotType.POROD_PLOT.getAxisNames();
			toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			porodButton.setToolTipText(toolTipText);
			porodButton.setSelection(porodPlotSourceProvider.isEnablePorod());
			porodButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					porodPlotSourceProvider.setEnablePorod(porodButton.getSelection());
				}
			});
			
			kratkyButton = new Button(g, SWT.CHECK);
			kratkyButton.setText(SaxsAnalysisPlotType.KRATKY_PLOT.getName());
			axesNames = SaxsAnalysisPlotType.KRATKY_PLOT.getAxisNames();
			toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			kratkyButton.setToolTipText(toolTipText);
			kratkyButton.setSelection(kratkyPlotSourceProvider.isEnableKratky());
			kratkyButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					kratkyPlotSourceProvider.setEnableKratky(kratkyButton.getSelection());
				}
			});
			
			zimmButton = new Button(g, SWT.CHECK);
			zimmButton.setText(SaxsAnalysisPlotType.ZIMM_PLOT.getName());
			axesNames =  SaxsAnalysisPlotType.ZIMM_PLOT.getAxisNames();
			toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			zimmButton.setToolTipText(toolTipText);
			zimmButton.setSelection(zimmPlotSourceProvider.isEnableZimm());
			zimmButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					zimmPlotSourceProvider.setEnableZimm(zimmButton.getSelection());
				}
			});
			
			debyebuecheButton = new Button(g, SWT.CHECK);
			debyebuecheButton.setText(SaxsAnalysisPlotType.DEBYE_BUECHE_PLOT.getName());
			axesNames = SaxsAnalysisPlotType.DEBYE_BUECHE_PLOT.getAxisNames();
			toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			debyebuecheButton.setToolTipText(toolTipText);
			debyebuecheButton.setSelection(debyebuechePlotSourceProvider.isEnableDebyeBueche());
			debyebuecheButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					debyebuechePlotSourceProvider.setEnableDebyeBueche(debyebuecheButton.getSelection());
				}
			});
		}
		
		setControl(container);
	}

}
