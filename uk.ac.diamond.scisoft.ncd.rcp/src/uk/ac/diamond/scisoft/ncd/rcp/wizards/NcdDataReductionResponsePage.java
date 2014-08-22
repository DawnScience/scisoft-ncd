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

import org.dawnsci.common.widgets.file.SelectorWidget;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionResponsePage extends AbstractNcdDataReductionPage {

	private NcdProcessingSourceProvider ncdDrFileSourceProvider;
//	private Text drFile;
//	private Button browseDr;

	protected static final int PAGENUMBER = 2;

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

		Composite container = new Composite(parent, SWT.NULL);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		Group group = new Group(container, SWT.NONE);
		group.setText("Reference Data");
		group.setLayout(new GridLayout(3, false));
		group.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, true));

		Label respLabel = new Label(group, SWT.NONE);
		respLabel.setText("Detector Response File:");
		SelectorWidget drFileSelector = new SelectorWidget(group, false, new String[] { "NeXus files", "All Files" }, new String[] {"*.nxs", "*.*"}) {
			@Override
			public void loadPath(String path, TypedEvent event) {
				File tmpDrFile = new File(path);
				if (tmpDrFile.exists())
					ncdDrFileSourceProvider.setDrFile(path);
				else
					ncdDrFileSourceProvider.setDrFile(null);
			}
		};
		drFileSelector.setTextToolTip("File with the detector response frame");
		drFileSelector.setButtonToolTip("Select Detector Response File");
		String tmpDrFile = ncdDrFileSourceProvider.getDrFile();
		if (tmpDrFile != null)
			drFileSelector.setText(tmpDrFile);

		setControl(container);
	}

}
