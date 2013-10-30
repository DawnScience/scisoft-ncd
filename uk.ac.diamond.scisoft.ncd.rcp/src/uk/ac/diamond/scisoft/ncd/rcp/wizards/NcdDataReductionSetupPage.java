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

import java.io.File;

import org.apache.commons.validator.routines.IntegerValidator;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.data.SliceInput;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionSetupPage extends AbstractNcdDataReductionPage {


	private Button drButton;
	private Button secButton;
	private Button normButton;
	private Button bgButton;
	private Button invButton;
	private Button aveButton;
	private NcdProcessingSourceProvider ncdResponseSourceProvider;
	private NcdProcessingSourceProvider ncdSectorSourceProvider;
	private NcdProcessingSourceProvider ncdNormalisationSourceProvider;
	private NcdProcessingSourceProvider ncdBackgroundSourceProvider;
	private NcdProcessingSourceProvider ncdInvariantSourceProvider;
	private NcdProcessingSourceProvider ncdAverageSourceProvider;
	private NcdProcessingSourceProvider ncdWorkingDirSourceProvider;

	private Text detAdvanced;
	private Button detAdvancedButton;
	private Text detFramesStop;
	private Text detFramesStart;
	private IntegerValidator integerValidator = IntegerValidator.getInstance();
	private NcdProcessingSourceProvider ncdDataSliceSourceProvider;

	private String inputDirectory = "Please specify results directory";
	private Text location;
	private Button browse;
	public static int PAGENUMBER = 1;

	protected NcdDataReductionSetupPage() {
		super("Data Reduction parameters setup");
		setTitle("Data Reduction parameters setup");
		setDescription("Choose the steps to set up, the result directory and the data frame selection.");
		currentPageNumber = PAGENUMBER;
		activePages.put(PAGENUMBER, false);
	}

	@Override
	public void createControl(Composite parent) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		ncdResponseSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RESPONSE_STATE);
		ncdSectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SECTOR_STATE);
		ncdNormalisationSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.NORMALISATION_STATE);
		ncdBackgroundSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BACKGROUD_STATE);
		ncdInvariantSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.INVARIANT_STATE);
		ncdAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AVERAGE_STATE);
		ncdDataSliceSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.DATASLICE_STATE);
		ncdWorkingDirSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WORKINGDIR_STATE);

		Composite container = new Composite(parent, SWT.NULL);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		// Selection process
		Group subContainer = new Group(container, SWT.NULL);
		subContainer.setText("Data Reduction Pipeline");
		subContainer.setToolTipText("Tick the steps needed to your Data Reduction Pipeline");
		GridLayout gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		subContainer.setLayout(gl);
		subContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

		drButton = new Button(subContainer, SWT.CHECK);
		drButton.setText("1. Detector response");
		drButton.setToolTipText("Enable detector response step");
		drButton.setSelection(ncdResponseSourceProvider.isEnableDetectorResponse());
		drButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdResponseSourceProvider.setEnableDetectorResponse(drButton.getSelection());
				activePages.put(NcdDataReductionResponsePage.PAGENUMBER, drButton.getSelection());
				updateNextButton();
			}
		});

		secButton = new Button(subContainer, SWT.CHECK);
		secButton.setText("2. Sector integration");
		secButton.setToolTipText("Enable sector integration step");
		secButton.setSelection(ncdSectorSourceProvider.isEnableSector());
		secButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdSectorSourceProvider.setEnableSector(secButton.getSelection());
				activePages.put(NcdDataReductionSectorIntegrationPage.PAGENUMBER, secButton.getSelection());
				updateNextButton();
			}
		});

		normButton = new Button(subContainer, SWT.CHECK);
		normButton.setText("3. Normalisation");
		normButton.setToolTipText("Enable normalisation step");
		normButton.setSelection(ncdNormalisationSourceProvider.isEnableNormalisation());
		normButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdNormalisationSourceProvider.setEnableNormalisation(normButton.getSelection());
				activePages.put(NcdDataReductionNormalisationPage.PAGENUMBER, normButton.getSelection());
				updateNextButton();
			}

		});

		bgButton = new Button(subContainer, SWT.CHECK);
		bgButton.setText("4. Background subtraction");
		bgButton.setToolTipText("Enable background subtraction step");
		bgButton.setSelection(ncdBackgroundSourceProvider.isEnableBackground());
		bgButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdBackgroundSourceProvider.setEnableBackground(bgButton.getSelection());
				activePages.put(NcdDataReductionBackgroundPage.PAGENUMBER, bgButton.getSelection());
				updateNextButton();
			}
		});

		invButton = new Button(subContainer, SWT.CHECK);
		invButton.setText("5. Invariant");
		invButton.setToolTipText("Enable invariant calculation step");
		invButton.setSelection(ncdInvariantSourceProvider.isEnableInvariant());
		invButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdInvariantSourceProvider.setEnableInvariant(invButton.getSelection());
			}
		});

		aveButton = new Button(subContainer, SWT.CHECK);
		aveButton.setText("6. Average");
		aveButton.setToolTipText("Enable average calculation step");
		aveButton.setSelection(ncdAverageSourceProvider.isEnableAverage());
		aveButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdAverageSourceProvider.setEnableAverage(aveButton.getSelection());
				activePages.put(NcdDataReductionAveragePage.PAGENUMBER, aveButton.getSelection());
				updateNextButton();
			}
		});

		// Results directory
		Group r = new Group(container, SWT.NONE);
		r.setLayout(new GridLayout(3, false));
		r.setText("Results directory");
		r.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		
		new Label(r, SWT.NONE).setText("Directory:");
		location = new Text(r, SWT.BORDER);
		location.setText(inputDirectory);
		location.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		location.setToolTipText("Location of NCD data reduction results directory");
		String tmpWorkigDir = ncdWorkingDirSourceProvider.getWorkingDir();
		if (tmpWorkigDir != null)
			location.setText(tmpWorkigDir);
		location.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				File dir = new File(location.getText());
				if (dir.exists()) {
					inputDirectory = dir.getPath();
					ncdWorkingDirSourceProvider.setWorkingDir(inputDirectory);
				} else {
					ncdWorkingDirSourceProvider.setWorkingDir(null);
				}
			}
		});

		browse = new Button(r, SWT.NONE);
		browse.setText("...");
		browse.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dChooser = new DirectoryDialog(getShell());
				dChooser.setText("Select working directory for NCD data reduction");
				dChooser.setFilterPath(inputDirectory);
				final File dir = new File(dChooser.open());
				if (dir.exists()) {
					inputDirectory = dir.toString();
					location.setText(inputDirectory);
				}
			}
		});

		//Data frame selection
		Group g = new Group(container, SWT.NONE);
		g.setText("Data frame selection");
		g.setToolTipText("Set data slicing parameters");
		g.setLayout(new GridLayout(4, false));
		g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		
		SliceInput tmpSlice = ncdDataSliceSourceProvider.getDataSlice();
		
		final Label detFramesStartLabel = new Label(g, SWT.NONE);
		detFramesStartLabel.setText("First");
		detFramesStart = new Text(g, SWT.BORDER);
		detFramesStart.setToolTipText("First frame to select from the data file ");
		detFramesStart.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		if (tmpSlice != null && tmpSlice.getStartFrame() != null)
			detFramesStart.setText(tmpSlice.getStartFrame().toString());
		detFramesStart.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				Integer detStartInt = getDetFirstFrame();
				Integer detStopInt = getDetLastFrame();
				SliceInput tmpDetSlice = new SliceInput(detStartInt, detStopInt);
				ncdDataSliceSourceProvider.setDataSlice(tmpDetSlice);
			}
		});
		
		final Label detFramesStopLabel = new Label(g, SWT.NONE);
		detFramesStopLabel.setText("Last");
		detFramesStop = new Text(g, SWT.BORDER);
		detFramesStop.setToolTipText("Last frame to select from the data file ");
		detFramesStop.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		if (tmpSlice != null && tmpSlice.getStopFrame() != null)
			detFramesStop.setText(tmpSlice.getStopFrame().toString());
		detFramesStop.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				Integer detStartInt = getDetFirstFrame();
				Integer detStopInt = getDetLastFrame();
				SliceInput tmpDetSlice = new SliceInput(detStartInt, detStopInt);
				ncdDataSliceSourceProvider.setDataSlice(tmpDetSlice);
			}
		});
		
		detAdvanced = new Text(g, SWT.BORDER);
		detAdvanced.setToolTipText("Formatting string for advanced data selection");
		detAdvanced.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false, 3, 1));
		detAdvanced.setEnabled(false);
		if (tmpSlice != null && tmpSlice.getAdvancedSlice() != null)
			detAdvanced.setText(tmpSlice.getAdvancedSlice());
		detAdvanced.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				SliceInput tmpDetSlice = new SliceInput(getDetAdvancedSelection());
				ncdDataSliceSourceProvider.setDataSlice(tmpDetSlice);
			}
		});
		
		detAdvancedButton = new Button(g, SWT.CHECK);
		detAdvancedButton.setText("Advanced");
		detAdvancedButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		detAdvancedButton.setSelection(false);
		detAdvancedButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean sel = detAdvancedButton.getSelection();
				detAdvanced.setEnabled(sel);
				detFramesStart.setEnabled(!sel);
				detFramesStop.setEnabled(!sel);
				detFramesStartLabel.setEnabled(!sel);
				detFramesStopLabel.setEnabled(!sel);
				SliceInput tmpDetSlice;
				if (sel) {
					tmpDetSlice = new SliceInput(getDetAdvancedSelection());
				} else {
					Integer detStartInt = getDetFirstFrame();
					Integer detStopInt = getDetLastFrame();
					tmpDetSlice = new SliceInput(detStartInt, detStopInt);
				}
				ncdDataSliceSourceProvider.setDataSlice(tmpDetSlice);
			}
		});
		
		setActivePages();
		setControl(container);
	}

	private void updateNextButton(){
		if(ncdResponseSourceProvider.isEnableDetectorResponse()
				|| ncdSectorSourceProvider.isEnableSector()
				|| ncdNormalisationSourceProvider.isEnableNormalisation()
				|| ncdBackgroundSourceProvider.isEnableBackground()
				|| ncdAverageSourceProvider.isEnableAverage()) {
			setPageComplete(true);
		} else {
			setPageComplete(false);
		}
	}

	private Integer getDetFirstFrame() {
		String input = detFramesStart.getText();
		if (detFramesStart.isEnabled()) {
			return integerValidator.validate(input);
		}
		return null;
	}
	
	private Integer getDetLastFrame() {
		String input = detFramesStop.getText();
		if (detFramesStop.isEnabled()) {
			return integerValidator.validate(input);
		}
		return null;
	}
	
	private String getDetAdvancedSelection() {
		if (detAdvanced.isEnabled()) {
			return detAdvanced.getText();
		}
		return null;
	}

	private void setActivePages(){
		activePages.put(NcdDataReductionResponsePage.PAGENUMBER, drButton.getSelection());
		activePages.put(NcdDataReductionSectorIntegrationPage.PAGENUMBER, secButton.getSelection());
		activePages.put(NcdDataReductionNormalisationPage.PAGENUMBER, normButton.getSelection());
		activePages.put(NcdDataReductionBackgroundPage.PAGENUMBER, bgButton.getSelection());
		activePages.put(NcdDataReductionAveragePage.PAGENUMBER, aveButton.getSelection());
	}

	@Override
	public IWizardPage getNextPage() {
		IWizard wizard = getWizard();
		IWizardPage[] pages = wizard.getPages();
		for (int i = currentPageNumber; i < pages.length; i++) {
	
			if(((INcdDataReductionWizardPage)pages[i]).isActive())
				return pages[i];
		}
		return null;
	}

	@Override
	public boolean isCurrentNcdWizardpage() {
		return isCurrentPage();
	}
}
