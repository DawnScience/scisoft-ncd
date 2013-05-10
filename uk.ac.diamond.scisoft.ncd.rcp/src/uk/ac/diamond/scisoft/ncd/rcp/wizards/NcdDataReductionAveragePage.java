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
				ncdGridAverageSourceProvider.setGrigAverage(new SliceInput(getGridAverageSelection()));
			}
		});
		gridAverage = new Text(g, SWT.BORDER);
		gridAverage.setToolTipText("Comma-separated list of grid dimensions to average");
		gridAverage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		gridAverage.setEnabled(false);
		SliceInput tmpAverage = ncdGridAverageSourceProvider.getGridAverage();
		if (tmpAverage != null && tmpAverage.getAdvancedSlice() != null)
			gridAverage.setText(tmpAverage.getAdvancedSlice());
		gridAverage.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				ncdGridAverageSourceProvider.setGrigAverage(new SliceInput(getGridAverageSelection()));
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
