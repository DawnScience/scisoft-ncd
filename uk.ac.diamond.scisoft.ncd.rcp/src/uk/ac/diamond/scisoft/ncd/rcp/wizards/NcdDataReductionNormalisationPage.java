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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.validator.routines.DoubleValidator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionNormalisationPage extends AbstractNcdDataReductionPage {

	private Combo calList;
	private Spinner normChan;
	private Text sampleThickness;
	private Text absScale;
	private Label absOffset, normChanLabel;
	private NcdProcessingSourceProvider ncdScalerSourceProvider;
	private NcdCalibrationSourceProvider ncdDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdSampleThicknessSourceProvider;
	private NcdProcessingSourceProvider ncdAbsScaleSourceProvider;

	public static int PAGENUMBER = 4;

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

		ncdScalerSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SCALER_STATE);
		ncdScalerSourceProvider.addSourceProviderListener(this);

		ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
		ncdDetectorSourceProvider.addSourceProviderListener(this);

		ncdSampleThicknessSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAMPLETHICKNESS_STATE);

		ncdAbsScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSSCALING_STATE);
		ncdAbsScaleSourceProvider.addSourceProviderListener(this);

		Composite container = new Composite(parent, SWT.NULL);
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(GridData.FILL_BOTH));

		Composite subContainer = new Composite(container, SWT.NULL);
		subContainer.setLayout(new GridLayout(7, false));
		subContainer.setLayoutData(new GridData(GridData.FILL_BOTH));

		Label calListLabel = new Label(subContainer, SWT.NONE);
		calListLabel.setText("Normalisation Data");
		calList = new Combo(subContainer, SWT.NONE);
		GridData gridData = new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1);
		calList.setLayoutData(gridData);
		calList.setToolTipText("Select the detector with calibration data");
		HashMap<String, NcdDetectorSettings> ncdDetectorSettings = ncdDetectorSourceProvider.getNcdDetectors();
		if (ncdDetectorSettings != null) {
			for (NcdDetectorSettings ncdSettings : ncdDetectorSettings.values()) {
				if (ncdSettings.getType().equals(DetectorTypes.CALIBRATION_DETECTOR)) {
					calList.add(ncdSettings.getName());
				}
			}
		}

		String tmpScaler = ncdScalerSourceProvider.getScaler();
		if (tmpScaler != null) {
			int idxScaler = calList.indexOf(tmpScaler);
			calList.select(idxScaler);
		} else {
			calList.select(Math.min(0, calList.getItemCount() - 1));
		}
		calList.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int idx = calList.getSelectionIndex();
				if (idx >= 0) {
					String detName = calList.getItem(idx);
					ncdScalerSourceProvider.setScaler(detName);
					
					NcdDetectorSettings calDet = ncdDetectorSourceProvider.getNcdDetectors().get(detName);
					normChan.setMinimum(0);
					normChan.setMaximum(calDet.getMaxChannel());
					if (calDet.getMaxChannel() < 1) {
						normChanLabel.setEnabled(false);
						normChan.setEnabled(false);
					} else {
						normChanLabel.setEnabled(true);
						normChan.setEnabled(true);
					}
					normChan.setSelection(calDet.getNormChannel());
					Display dsp = normChan.getDisplay();
					if (dsp.getActiveShell()!=null) dsp.getActiveShell().redraw();
				}
			}
		});
		
		
		normChanLabel = new Label(subContainer, SWT.NONE);
		normChanLabel.setText("Channel");
		normChanLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		normChan = new Spinner(subContainer, SWT.BORDER);
		normChan.setToolTipText("Select the channel number with calibration data");
		normChan.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		String scaler = ncdScalerSourceProvider.getScaler();
		NcdDetectorSettings calDet = ncdDetectorSourceProvider.getNcdDetectors().get(scaler);
		if (calDet != null) {
			normChan.setSelection(calDet.getNormChannel());
		}
		normChan.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String scaler = ncdScalerSourceProvider.getScaler();
				NcdDetectorSettings calDet = ncdDetectorSourceProvider.getNcdDetectors().get(scaler);
				if (calDet != null) {
					calDet.setNormChannel(normChan.getSelection());
					ncdDetectorSourceProvider.addNcdDetector(calDet);
				}
			}
		});
		
		Label sampleThicknessLabel = new Label(subContainer, SWT.NONE);
		sampleThicknessLabel.setText("Sample Thickness (mm)");
		sampleThicknessLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false));
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
		
		Label absScaleLabel = new Label(subContainer, SWT.NONE);
		absScaleLabel.setText("Abs. Scale");
		absScaleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		absScale = new Text(subContainer, SWT.BORDER);
		absScale.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		absScale.setToolTipText("Select absolute scaling factor for calibration data");
		Double tmpAbsScaling = ncdAbsScaleSourceProvider.getAbsScaling();
		if (tmpAbsScaling != null) {
			absScale.setText(tmpAbsScaling.toString());
		}
		absScale.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				ncdAbsScaleSourceProvider.setAbsScaling(getAbsScale(), false);
			}
		});
		
		Label absOffsetLabel = new Label(subContainer, SWT.NONE);
		absOffsetLabel.setText("Offset");
		absOffsetLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		absOffset = new Label(subContainer, SWT.NONE);
		absOffset.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		absOffset.setToolTipText("Offset value between reference and calibrated profiles. Should be close to zero.");
		
		setControl(container);
	}

	private DoubleValidator doubleValidator = DoubleValidator.getInstance();

	private Double getSampleThickness() {
		String input = sampleThickness.getText();
		return doubleValidator.validate(input);
	}

	private Double getAbsScale() {
		String input = absScale.getText();
		return doubleValidator.validate(input);
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
