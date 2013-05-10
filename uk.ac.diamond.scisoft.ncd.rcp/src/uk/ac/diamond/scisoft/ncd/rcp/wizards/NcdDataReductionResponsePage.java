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

import java.io.File;
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
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionResponsePage extends AbstractNcdDataReductionPage {

	private NcdProcessingSourceProvider ncdDrFileSourceProvider;
	private Text drFile;
	private Button browseDr;

	public static int PAGENUMBER = 2;

	public NcdDataReductionResponsePage() {
		super("Detector response");
		setTitle("1. Detector response");
		setDescription("Choose file with the detector response frame parameter");
		currentPageNumber = PAGENUMBER;
	}

	@Override
	public void createControl(Composite parent) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
	
		ncdDrFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.DRFILE_STATE);
		ncdDrFileSourceProvider.addSourceProviderListener(this);

		Composite container = new Composite(parent, SWT.NULL);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		Group group = new Group(container, SWT.NONE);
		group.setText("Reference Data");
		group.setLayout(new GridLayout(3, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, true));

		Label respLabel = new Label(group, SWT.NONE);
		respLabel.setText("Detector Response File:");
		drFile = new Text(group, SWT.BORDER);
		drFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		drFile.setToolTipText("File with the detector response frame");
		String tmpDrFile = ncdDrFileSourceProvider.getDrFile();
		if (tmpDrFile != null)
			drFile.setText(tmpDrFile);
		drFile.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				File tmpDrFile = new File(drFile.getText());
				if (tmpDrFile.exists())
					ncdDrFileSourceProvider.setDrFile(tmpDrFile.getAbsolutePath());
				else
					ncdDrFileSourceProvider.setDrFile(null);
			}
		});

		browseDr = new Button(group, SWT.NONE);
		browseDr.setText("...");
		browseDr.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		browseDr.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog dChooser = new FileDialog(getShell());
				dChooser.setText("Select Detector Response File");
				dChooser.setFilterNames(new String[] { "NeXus files", "All Files"});
				dChooser.setFilterExtensions(new String[] {"*.nxs", "*.*"});
				final File fl = new File(dChooser.open());
				if (fl.exists()) {
					drFile.setText(fl.toString());
				}
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
