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
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.data.SliceInput;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionAveragePage extends AbstractNcdDataReductionPage {

	public static int PAGENUMBER = 6;
	private Button gridAverageButton;
	private Text gridAverage;
	private NcdProcessingSourceProvider ncdGridAverageSourceProvider;

	public NcdDataReductionAveragePage() {
		super("Average");
		setTitle("6. Average calculation");
		setDescription("Specify dimensions for averaging");
		currentPageNumber = PAGENUMBER;
	}

	@Override
	public void createControl(Composite parent) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);

		ncdGridAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.GRIDAVERAGE_STATE);

		Composite container = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		container.setLayout(layout);
		layout.numColumns = 1;
		layout.verticalSpacing = 9;
		
		Group g = new Group(container, SWT.NONE);
		g.setText("Grid data averaging");
		g.setToolTipText("Specify dimensions for averaging");
		g.setLayout(new GridLayout(2, false));
		g.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		
		gridAverageButton = new Button(g, SWT.CHECK);
		gridAverageButton.setText("Average dimensions");
		gridAverageButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		gridAverageButton.setSelection(false);
		gridAverageButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean sel = gridAverageButton.getSelection();
				gridAverage.setEnabled(sel);
				ncdGridAverageSourceProvider.setGrigAverage(new SliceInput(getGridAverageSelection()), false);
			}
		});
		gridAverage = new Text(g, SWT.BORDER);
		gridAverage.setToolTipText("Comma-separated list of grid dimensions to average");
		gridAverage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		gridAverage.setEnabled(false);
		SliceInput tmpAverage = ncdGridAverageSourceProvider.getGridAverage();
		if (tmpAverage != null && tmpAverage.getAdvancedSlice() != null) {
			gridAverage.setText(tmpAverage.getAdvancedSlice());
		}
		gridAverage.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				ncdGridAverageSourceProvider.setGrigAverage(new SliceInput(getGridAverageSelection()), false);
			}
		});
		
		setControl(container);
	}

	private String getGridAverageSelection() {
		if (gridAverage.isEnabled())
			return gridAverage.getText();
		return null;
	}

	@Override
	public boolean isCurrentNcdWizardpage() {
		return isCurrentPage();
	}

}
