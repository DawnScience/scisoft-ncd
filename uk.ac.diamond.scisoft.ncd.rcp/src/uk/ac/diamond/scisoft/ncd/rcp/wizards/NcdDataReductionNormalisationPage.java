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

import org.apache.commons.lang.math.NumberUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionNormalisationPage extends AbstractNcdDataReductionPage {

	private Text sampleThickness;
	private Text absScale;
	private NcdProcessingSourceProvider ncdSampleThicknessSourceProvider;
	private NcdProcessingSourceProvider ncdAbsScaleSourceProvider;

	protected static final int PAGENUMBER = 4;

	public NcdDataReductionNormalisationPage() {
		super("Normalisation");
		setTitle("3. Normalisation");
		setDescription("Set options for Normalisation stage");
		currentPageNumber = PAGENUMBER;
	}

	@Override
	public void createControl(Composite parent) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);

		ncdSampleThicknessSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAMPLETHICKNESS_STATE);
		ncdAbsScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSSCALING_STATE);

		Composite container = new Composite(parent, SWT.NULL);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		Composite subContainer = new Composite(container, SWT.NULL);
		subContainer.setLayout(new GridLayout(7, false));
		subContainer.setLayoutData(new GridData(GridData.FILL_BOTH));

		Label absScaleLabel = new Label(subContainer, SWT.NONE);
		absScaleLabel.setText("Abs. Scale");
		absScaleLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		absScale = new Text(subContainer, SWT.BORDER);
		absScale.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		absScale.setToolTipText("Select absolute scaling factor for calibration data");
		Double tmpAbsScaling = ncdAbsScaleSourceProvider.getAbsScaling();
		if (tmpAbsScaling != null) {
			absScale.setText(tmpAbsScaling.toString());
			absScale.setEnabled(false);
		}
		absScale.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				ncdAbsScaleSourceProvider.setAbsScaling(getAbsScale(), false);
			}
		});
		
		Label sampleThicknessLabel = new Label(subContainer, SWT.NONE);
		sampleThicknessLabel.setText("Sample Thickness (mm)");
		sampleThicknessLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		sampleThickness = new Text(subContainer, SWT.BORDER);
		sampleThickness.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		sampleThickness.setToolTipText("Set sample thickness in millimeters");
		Double tmpSampleThickness = ncdSampleThicknessSourceProvider.getSampleThickness();
		if (tmpSampleThickness != null) {
			sampleThickness.setText(tmpSampleThickness.toString());
		}
		sampleThickness.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				ncdSampleThicknessSourceProvider.setSampleThickness(getSampleThickness(), false);
			}
		});
		
		setControl(container);
	}

	private Double getSampleThickness() {
		String input = sampleThickness.getText();
		if (NumberUtils.isNumber(input)) {
			return Double.valueOf(input);
		}
		return null;
	}

	private Double getAbsScale() {
		String input = absScale.getText();
		if (NumberUtils.isNumber(input)) {
			return Double.valueOf(input);
		}
		return null;
	}

}
