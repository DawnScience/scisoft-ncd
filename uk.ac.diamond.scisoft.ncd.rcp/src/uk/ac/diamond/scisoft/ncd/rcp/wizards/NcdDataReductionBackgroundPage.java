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
import java.util.Map;

import org.apache.commons.validator.routines.DoubleValidator;
import org.apache.commons.validator.routines.IntegerValidator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.data.SliceInput;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionBackgroundPage extends AbstractNcdDataReductionPage {

	public static int PAGENUMBER = 5;
	private Text bgFile;
	private Button browseBg;
	private Text bgScale;
	private IntegerValidator integerValidator = IntegerValidator.getInstance();
	private DoubleValidator doubleValidator = DoubleValidator.getInstance();
	private NcdProcessingSourceProvider ncdBgFileSourceProvider;
	private NcdProcessingSourceProvider ncdBgScaleSourceProvider;
	private NcdProcessingSourceProvider ncdBkgSliceSourceProvider;
	private Text bgFramesStop;
	private Text bgFramesStart;
	private Text bgAdvanced;

	public NcdDataReductionBackgroundPage() {
		super("Background subtraction");
		setTitle("4. Background subtraction");
		setDescription("Enable background subtraction step");
		// TODO Auto-generated constructor stub
		currentPageNumber = PAGENUMBER;
	}

	@Override
	public void createControl(Composite parent) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);

		ncdBgFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGFILE_STATE);
		ncdBgFileSourceProvider.addSourceProviderListener(this);

		ncdBgScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGSCALING_STATE);

		ncdBkgSliceSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGSLICE_STATE);

		Composite container = new Composite(parent, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		Group subContainer = new Group(container, SWT.NONE);
		subContainer.setText("Reference Data");
		subContainer.setLayout(new GridLayout(7, false));
		subContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		Label bgLabel = new Label(subContainer, SWT.NONE);
		bgLabel.setText("Background Subtraction File");
		bgFile = new Text(subContainer, SWT.BORDER);
		bgFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		bgFile.setToolTipText("File with the background measurments");
		String tmpBgFile = ncdBgFileSourceProvider.getBgFile();
		if (tmpBgFile != null)
			bgFile.setText(tmpBgFile);
		bgFile.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				File tmpBgFile = new File(bgFile.getText());
				if (tmpBgFile.exists())
					ncdBgFileSourceProvider.setBgFile(tmpBgFile.getAbsolutePath());
				else
					ncdBgFileSourceProvider.setBgFile(null);
			}
		});

		browseBg = new Button(subContainer, SWT.NONE);
		browseBg.setText("...");
		browseBg.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		browseBg.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dChooser = new FileDialog(getShell());
				dChooser.setText("Select Background Data File");
				dChooser.setFilterNames(new String[] { "NeXus files", "All Files"});
				dChooser.setFilterExtensions(new String[] {"*.nxs", "*.*"});
				final File fl = new File(dChooser.open());
				if (fl.exists()) {
					bgFile.setText(fl.getAbsolutePath());
				}
			}
		});
		
		Label bgScaleLabel = new Label(subContainer, SWT.NONE);
		bgScaleLabel.setText("Bg. Scale");
		bgScaleLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		bgScale = new Text(subContainer, SWT.BORDER);
		bgScale.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
		bgScale.setToolTipText("Scaling values for background data");
		Double tmpBgScaling = ncdBgScaleSourceProvider.getBgScaling();
		if (tmpBgScaling != null) {
			bgScale.setText(tmpBgScaling.toString());
		}
		bgScale.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				ncdBgScaleSourceProvider.setBgScaling(getBgScale(), false);
			}
		});

		Group g = new Group(container, SWT.NONE);
		g.setText("Background frame selection");
		g.setToolTipText("Set background data slicing parameters");
		g.setLayout(new GridLayout(4, false));
		g.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		
		SliceInput tmpSlice = ncdBkgSliceSourceProvider.getBkgSlice();
		
		final Label bgFramesStartLabel = new Label(g, SWT.NONE);
		bgFramesStartLabel.setText("First");
		bgFramesStart = new Text(g, SWT.BORDER);
		bgFramesStart.setToolTipText("First frame to select from the background data");
		bgFramesStart.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		if (tmpSlice != null && tmpSlice.getStartFrame() != null)
			bgFramesStart.setText(tmpSlice.getStartFrame().toString());
		bgFramesStart.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				Integer bgStartInt = getBgFirstFrame();
				Integer bgStopInt = getBgLastFrame();
				SliceInput tmpBkgSlice = new SliceInput(bgStartInt, bgStopInt);
				ncdBkgSliceSourceProvider.setBkgSlice(tmpBkgSlice);
			}
		});
		
		final Label bgFramesStopLabel = new Label(g, SWT.NONE);
		bgFramesStopLabel.setText("Last");
		bgFramesStop = new Text(g, SWT.BORDER);
		bgFramesStop.setToolTipText("Last frame to select from the background data");
		bgFramesStop.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		if (tmpSlice != null && tmpSlice.getStopFrame() != null)
			bgFramesStop.setText(tmpSlice.getStopFrame().toString());
		bgFramesStop.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				Integer bgStartInt = getBgFirstFrame();
				Integer bgStopInt = getBgLastFrame();
				SliceInput tmpBkgSlice = new SliceInput(bgStartInt, bgStopInt);
				ncdBkgSliceSourceProvider.setBkgSlice(tmpBkgSlice);
			}
		});
		
		bgAdvanced = new Text(g, SWT.BORDER);
		bgAdvanced.setToolTipText("Formatting string for advanced data selection");
		bgAdvanced.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false, 3, 1));
		bgAdvanced.setEnabled(false);
		if (tmpSlice != null && tmpSlice.getAdvancedSlice() != null)
			bgAdvanced.setText(tmpSlice.getAdvancedSlice());
		bgAdvanced.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				SliceInput tmpBkgSlice = new SliceInput(getBgAdvancedSelection());
				ncdBkgSliceSourceProvider.setBkgSlice(tmpBkgSlice);
			}
		});
		
		final Button bgAdvancedButton = new Button(g, SWT.CHECK);
		bgAdvancedButton.setText("Advanced");
		bgAdvancedButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		bgAdvancedButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean sel = bgAdvancedButton.getSelection();
				bgAdvanced.setEnabled(sel);
				bgFramesStart.setEnabled(!sel);
				bgFramesStop.setEnabled(!sel);
				bgFramesStartLabel.setEnabled(!sel);
				bgFramesStopLabel.setEnabled(!sel);
				SliceInput tmpBkgSlice;
				if (sel) {
					tmpBkgSlice = new SliceInput(getBgAdvancedSelection());
				} else {
					Integer bgStartInt = getBgFirstFrame();
					Integer bgStopInt = getBgLastFrame();
					tmpBkgSlice = new SliceInput(bgStartInt, bgStopInt);
				}
				ncdBkgSliceSourceProvider.setBkgSlice(tmpBkgSlice);
			}
		});
		
		setControl(container);
	}

	private Double getBgScale() {
		String input = bgScale.getText();
		return doubleValidator.validate(input);
	}

	private Integer getBgFirstFrame() {
		String input = bgFramesStart.getText();
		if (bgFramesStart.isEnabled())
			return integerValidator.validate(input);
		return null;
	}

	private Integer getBgLastFrame() {
		String input = bgFramesStop.getText();
		if (bgFramesStop.isEnabled())
			return integerValidator.validate(input);
		return null;
	}

	private String getBgAdvancedSelection() {
		if (bgAdvanced.isEnabled())
			return bgAdvanced.getText();
		return null;
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

	@SuppressWarnings("rawtypes")
	@Override
	public void sourceChanged(int sourcePriority, Map sourceValuesByName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, Object sourceValue) {
		// TODO Auto-generated method stub
		
	}
}
