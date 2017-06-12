/*
 * Copyright 2013, 2017 Diamond Light Source Ltd.
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

import java.util.HashMap;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Length;

import tec.units.ri.quantity.Quantities;
import tec.units.ri.unit.MetricPrefix;
import tec.units.ri.unit.Units;

import org.apache.commons.lang.math.NumberUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.core.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.preferences.NcdConstants;

public class NcdDataReductionDetectorParameterPage extends AbstractNcdDataReductionPage {

	protected static final int PAGENUMBER = 0;

	private static final Unit<Length> MILLIMETRE = MetricPrefix.MILLI(Units.METRE);

	private Button detTypeWaxs;
	private Combo detListWaxs;
	private Button[] dimWaxs;
	private Text pxWaxs;
	private Button detTypeSaxs;
	private Combo detListSaxs;
	private Button[] dimSaxs;
	private Text pxSaxs;
	private NcdProcessingSourceProvider ncdWaxsSourceProvider;
	private NcdProcessingSourceProvider ncdSaxsSourceProvider;
	private NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdWaxsDetectorSourceProvider;
	private NcdCalibrationSourceProvider<?, ?> ncdDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdScalerSourceProvider;
	private Label pxSaxsLabel;
	private Label pxWaxsLabel;

	private static Combo calList;
	private Label calListLabel, normChanLabel;
	private static Spinner normChan;
	
	public NcdDataReductionDetectorParameterPage() {
		super("Detector Parameters");
		setTitle("NCD Detector Parameters");
		setDescription("Select the NCD Detector Parameters");
		currentPageNumber = PAGENUMBER;
	}

	@Override
	public void createControl(Composite parent) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = window.getService(ISourceProviderService.class);
		
		ncdWaxsSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXS_STATE);
		ncdSaxsSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXS_STATE);
		
		ncdWaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXSDETECTOR_STATE);
		ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		
		ncdDetectorSourceProvider = (NcdCalibrationSourceProvider<?, ?>) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
		ncdScalerSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SCALER_STATE);

		Composite container = new Composite(parent, SWT.NONE);
		GridLayout grid = new GridLayout(5, false);
		container.setLayout(grid);
		container.setLayoutData(new GridData(SWT.LEFT, SWT.TOP,true, false));

		detTypeWaxs = new Button(container, SWT.CHECK);
		detTypeWaxs.setText("WAXS");
		detTypeWaxs.addSelectionListener(modeSelectionListenerWaxs);
		detTypeWaxs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdWaxsSourceProvider.setEnableWaxs(detTypeWaxs.getSelection());
			}
		});

		detListWaxs = new Combo(container, SWT.NONE);
		detListWaxs.setToolTipText("Select the WAXS detector used in data collection");

		HashMap<String, NcdDetectorSettings> ncdDetectorSettings = ncdDetectorSourceProvider.getNcdDetectors();
		if (ncdDetectorSettings != null) {
			for (NcdDetectorSettings ncdSettings : ncdDetectorSettings.values()) {
				if (ncdSettings.getType().equals(DetectorTypes.WAXS_DETECTOR)) {
					detListWaxs.add(ncdSettings.getName());
				}
			}
		}

		String detWaxs = ncdWaxsDetectorSourceProvider.getWaxsDetector();
		if (detWaxs != null) {
			int idxWaxs = detListWaxs.indexOf(detWaxs);
			detListWaxs.select(idxWaxs);
		} else {
			detListWaxs.select(Math.min(0, detListWaxs.getItemCount() - 1));
		}
		detListWaxs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int idx = detListWaxs.getSelectionIndex();
				if (idx >= 0) {
					String det = detListWaxs.getItem(idx);
					ncdWaxsDetectorSourceProvider.setWaxsDetector(det);
				}
			}
		});
		
		Group gpDimWaxs = new Group(container, SWT.NONE);
		gpDimWaxs.setLayout(new GridLayout(2, false));
		gpDimWaxs.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gpDimWaxs.setToolTipText("Select the WAXS detector dimensionality");
		dimWaxs = new Button[NcdConstants.dimChoices.length];
		for (int i = 0; i < NcdConstants.dimChoices.length; i++) {
			dimWaxs[i] = new Button(gpDimWaxs, SWT.RADIO);
			dimWaxs[i].setText(NcdConstants.dimChoices[i]);
			dimWaxs[i].setToolTipText("Select the WAXS detector dimensionality");
			dimWaxs[i].addSelectionListener(new SelectionAdapter() {
				
				@Override
				public void widgetSelected(SelectionEvent e) {
					String waxsDetector = ncdWaxsDetectorSourceProvider.getWaxsDetector();
					NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(waxsDetector);
					if (detSettings != null) {
						for (int i = 0; i < dimWaxs.length; i++) {
							if (dimWaxs[i].getSelection()) {
								detSettings.setDimension(i + 1);
								ncdDetectorSourceProvider.addNcdDetector(detSettings);
								ncdDetectorSourceProvider.updateNcdDetectors();
								break;
							}
						}
					}
				}
			});
		}
		
		pxWaxsLabel = new Label(container, SWT.NONE);
		pxWaxsLabel.setText("pixel (mm):");
		pxWaxs = new Text(container, SWT.BORDER);
		pxWaxs.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		pxWaxs.setToolTipText("Set detector pixel size");
		NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(detWaxs);
		if (detSettings != null) {
			int idxDim = detSettings.getDimension() - 1;
			if (dimWaxs != null && !(dimWaxs[idxDim].isDisposed())) {
				for (Button btn : dimWaxs) btn.setSelection(false);
				dimWaxs[idxDim].setSelection(true);
			}
			Quantity<Length> pxSize = detSettings.getPxSize();
			if (pxSize != null && pxWaxs != null && !(pxWaxs.isDisposed())) {
				String pxText = String.format("%.3f", pxSize.to(MILLIMETRE).getValue().doubleValue());
				if (!(pxText.equals(pxWaxs.getText()))) {
					pxWaxs.setText(pxText);
				}
			}
		}
		pxWaxs.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (detTypeWaxs.isEnabled()) {
					String waxsDetector = ncdWaxsDetectorSourceProvider.getWaxsDetector();
					NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(waxsDetector);
					if (detSettings != null) {
						Double waxsPixel = getWaxsPixel();
						if (waxsPixel != null) {
							detSettings.setPxSize(Quantities.getQuantity(waxsPixel, MILLIMETRE));
							ncdDetectorSourceProvider.updateNcdDetectors();
						}
					}
				}
			}
		});
		
		if (ncdWaxsSourceProvider.isEnableWaxs()) detTypeWaxs.setSelection(true);
		else detTypeWaxs.setSelection(false);
		modeSelectionListenerWaxs.widgetSelected(null);
		
		detTypeSaxs = new Button(container, SWT.CHECK);
		detTypeSaxs.setText("SAXS");
		detTypeSaxs.addSelectionListener(modeSelectionListenerSaxs);
		detTypeSaxs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdSaxsSourceProvider.setEnableSaxs(detTypeSaxs.getSelection());
			}
		});
		
		detListSaxs = new Combo(container, SWT.NONE);
		detListSaxs.setToolTipText("Select the SAXS detector used in data collection");
		
		if (ncdDetectorSettings != null) {
			for (NcdDetectorSettings ncdSettings : ncdDetectorSettings.values()) {
				if (ncdSettings.getType().equals(DetectorTypes.SAXS_DETECTOR)) {
					detListSaxs.add(ncdSettings.getName());
				}
			}
		}

		String detSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
		if (detSaxs != null) {
			int idxSaxs = detListSaxs.indexOf(detSaxs);
			detListSaxs.select(idxSaxs);
		} else {
			detListSaxs.select(Math.min(0, detListSaxs.getItemCount() - 1));
		}
		detListSaxs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int idx = detListSaxs.getSelectionIndex();
				if (idx >= 0) {
					String det = detListSaxs.getItem(idx);
					ncdSaxsDetectorSourceProvider.setSaxsDetector(det);
				}
			}
		});
		
		
		Group gpDimSaxs = new Group(container, SWT.NONE);
		gpDimSaxs.setLayout(new GridLayout(2, false));
		gpDimSaxs.setToolTipText("Select the SAXS detector dimensionality");
		gpDimSaxs.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		dimSaxs = new Button[NcdConstants.dimChoices.length];
		for (int i = 0; i < NcdConstants.dimChoices.length; i++) {
			dimSaxs[i] = new Button(gpDimSaxs, SWT.RADIO);
			dimSaxs[i].setText(NcdConstants.dimChoices[i]);
			dimSaxs[i].setToolTipText("Select the SAXS detector dimensionality");
			dimSaxs[i].addSelectionListener(new SelectionAdapter() {
				
				@Override
				public void widgetSelected(SelectionEvent e) {
					String saxsDetector = ncdSaxsDetectorSourceProvider.getSaxsDetector();
					NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(saxsDetector);
					if (detSettings != null) {
						for (int i = 0; i < dimSaxs.length; i++) {
							if (dimSaxs[i].getSelection()) {
								detSettings.setDimension(i + 1);
								ncdDetectorSourceProvider.addNcdDetector(detSettings);
								ncdDetectorSourceProvider.updateNcdDetectors();
								break;
							}
						}
					}
				}
			});
		}
		
		pxSaxsLabel = new Label(container, SWT.NONE);
		pxSaxsLabel.setText("pixel (mm):");
		pxSaxs = new Text(container, SWT.BORDER);
		pxSaxs.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		pxSaxs.setToolTipText("Set detector pixel size");
		detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(detSaxs);
		if (detSettings != null) {
			int idxDim = detSettings.getDimension() - 1;
			if (dimSaxs != null && !(dimSaxs[idxDim].isDisposed())) {
				for (Button btn : dimSaxs) btn.setSelection(false);
				dimSaxs[idxDim].setSelection(true);
			}
			Quantity<Length> pxSize = detSettings.getPxSize();
			if (pxSize != null && pxSaxs != null && !(pxSaxs.isDisposed())) {
				String pxText = String.format("%.3f", pxSize.to(MILLIMETRE).getValue().doubleValue());
				if (!(pxText.equals(pxSaxs.getText()))) {
					pxSaxs.setText(pxText);
				}
			}
		}
		pxSaxs.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				if (detTypeSaxs.isEnabled()) {
					String saxsDetector = ncdSaxsDetectorSourceProvider.getSaxsDetector();
					NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(saxsDetector);
					if (detSettings != null) {
						Double saxsPixel = getSaxsPixel();
						if (saxsPixel != null) {
							detSettings.setPxSize(Quantities.getQuantity(saxsPixel, MILLIMETRE));
							ncdDetectorSourceProvider.updateNcdDetectors();
						}
					}
				}
			}
		});
		
		Group gpNorm = new Group(container, SWT.NONE);
		gpNorm.setLayout(new GridLayout(2, false));
		gpNorm.setText("Beam Intensity Monitoring Data");
		gpNorm.setToolTipText("Set dataset tha contains beam intensity monitoring data");
		gpNorm.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 7, 1));
		{
			Composite g = new Composite(gpNorm, SWT.NONE);
			g.setLayout(new GridLayout(4, false));
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
			
			calListLabel = new Label(g, SWT.NONE);
			calListLabel.setText("Normalisation Dataset");
			calListLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			calList = new Combo(g, SWT.READ_ONLY|SWT.BORDER);
			calList.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			calList.setToolTipText("Select the detector with calibration data");
			
			if (ncdDetectorSettings != null) {
				for (NcdDetectorSettings ncdSettings : ncdDetectorSettings.values()) {
					if (ncdSettings.getType().equals(DetectorTypes.CALIBRATION_DETECTOR)) {
						calList.add(ncdSettings.getName());
					}
				}
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
			
			
			normChanLabel = new Label(g, SWT.NONE);
			normChanLabel.setText("Channel");
			normChanLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			normChan = new Spinner(g, SWT.BORDER);
			normChan.setToolTipText("Select the channel number with calibration data");
			normChan.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
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
						ncdDetectorSourceProvider.updateNcdDetectors();
					}
				}
			});
		}
		
		String tmpScaler = ncdScalerSourceProvider.getScaler();
		if (tmpScaler != null) {
			int idxScaler = calList.indexOf(tmpScaler);
			calList.select(idxScaler);
		} else {
			calList.select(Math.min(0, calList.getItemCount() - 1));
		}
		calList.notifyListeners(SWT.Selection, null);
		
		if (ncdSaxsSourceProvider.isEnableSaxs()) {
			detTypeSaxs.setSelection(true);
		} else {
			detTypeSaxs.setSelection(false);
		}
		modeSelectionListenerSaxs.widgetSelected(null);

		setControl(container);
	}

	private Double getSaxsPixel() {
		String input = pxSaxs.getText();
		if (NumberUtils.isNumber(input)) {
			return Double.valueOf(input);
		}
		return null;
	}
	
	private Double getWaxsPixel() {
		String input = pxWaxs.getText();
		if (NumberUtils.isNumber(input)) {
			return Double.valueOf(input);
		}
		return null;
	}
	
	private SelectionListener modeSelectionListenerWaxs = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent e) {
			if (detTypeWaxs.getSelection()) {
				detListWaxs.setEnabled(true);
				pxWaxs.setEnabled(true);
				pxWaxsLabel.setEnabled(true);
				for (Button dim : dimWaxs) {
					dim.setEnabled(true);
				}
			    if (detListWaxs.getItemCount() > 0 && detListSaxs.getSelectionIndex() >= 0) {
					detListWaxs.notifyListeners(SWT.Selection, null);
			    }
			} else {
				detListWaxs.setEnabled(false);
				pxWaxs.setEnabled(false);
				pxWaxsLabel.setEnabled(false);
				for (Button dim : dimWaxs) {
					dim.setEnabled(false);
				}
			}
			
		}
	};

	private SelectionListener modeSelectionListenerSaxs = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent e) {
			if (detTypeSaxs.getSelection()) {
				detListSaxs.setEnabled(true);
				pxSaxs.setEnabled(true);
				pxSaxsLabel.setEnabled(true);
				for (Button dim : dimSaxs) {
					dim.setEnabled(true);
				}
			    if (detListSaxs.getItemCount() > 0 && detListSaxs.getSelectionIndex() >= 0) {
					detListSaxs.notifyListeners(SWT.Selection, null);
			    }
			} else {
				detListSaxs.setEnabled(false);
				pxSaxs.setEnabled(false);
				pxSaxsLabel.setEnabled(false);
				for (Button dim : dimSaxs) {
					dim.setEnabled(false);
				}
			}
			
		}
	};

}
