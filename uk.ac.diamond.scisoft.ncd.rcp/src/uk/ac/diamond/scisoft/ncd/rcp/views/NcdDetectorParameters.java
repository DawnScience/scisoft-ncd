/*
 * Copyright 2012 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.rcp.views;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.measure.quantity.Length;
import javax.measure.unit.SI;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISourceProviderListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.services.ISourceProviderService;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.ncd.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.preferences.NcdConstants;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDetectorParameters extends ViewPart implements ISourceProviderListener {

	private static final Logger logger = LoggerFactory.getLogger(NcdDetectorSettings.class);
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.NcdDetectorSettings";
	
	private IMemento memento;
	
	private NcdProcessingSourceProvider ncdWaxsSourceProvider, ncdWaxsDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdSaxsSourceProvider, ncdSaxsDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdQGradientSourceProvider, ncdQInterceptSourceProvider, ncdQUnitSourceProvider;
	
	private NcdCalibrationSourceProvider ncdDetectorSourceProvider;
	
	
	private Button[] dimWaxs, dimSaxs;
	protected HashMap<String, Button> unitSel;

	private Button detTypeWaxs, detTypeSaxs, inputQAxis;
	private Text pxWaxs, pxSaxs, qGradient, qIntercept;
	private Combo detListWaxs, detListSaxs;
	private Label pxSaxsLabel, pxWaxsLabel;
	private Label qGradientLabel, qInterceptLabel;
	
	@Override
	public void sourceChanged(int sourcePriority, Map sourceValuesByName) {
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, Object sourceValue) {
		if (sourceName.equals(NcdCalibrationSourceProvider.NCDDETECTORS_STATE)) {
			detListSaxs.removeAll();
			detListWaxs.removeAll();
			if (sourceValue instanceof HashMap<?, ?>) {
				for (Object settings : ((HashMap<?, ?>) sourceValue).values()) {
					if (settings instanceof NcdDetectorSettings) {
						
						NcdDetectorSettings detSettings = (NcdDetectorSettings) settings;
						
						if (detSettings.getType().equals(DetectorTypes.SAXS_DETECTOR)) {
							detListSaxs.add(detSettings.getName());
							continue;
						}
						
						if (detSettings.getType().equals(DetectorTypes.WAXS_DETECTOR)) {
							detListWaxs.add(detSettings.getName());
							continue;
						}
					}
				}
			}
			
			if (detListSaxs.getItemCount() > 0) {
				detListSaxs.select(0);
				ncdSaxsDetectorSourceProvider.setSaxsDetector(detListSaxs.getItem(0));
			}
			
			if (detListWaxs.getItemCount() > 0) {
				detListWaxs.select(0);
				ncdWaxsDetectorSourceProvider.setSaxsDetector(detListWaxs.getItem(0));
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.SAXSDETECTOR_STATE)) {
			if (sourceValue instanceof String) {
				NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(sourceValue);
				if (detSettings != null) {
					int idxDim = detSettings.getDimmension() - 1;
					for (Button btn : dimSaxs) btn.setSelection(false);
					dimSaxs[idxDim].setSelection(true);
					
					Amount<Length> pxSize = detSettings.getPxSize();
					if (pxSize != null)
						pxSaxs.setText(String.format("%.3f", pxSize.doubleValue(SI.MILLIMETER)));
				}
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.WAXSDETECTOR_STATE)) {
			if (sourceValue instanceof String) {
				NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(sourceValue);
				if (detSettings != null) {
					int idxDim = detSettings.getDimmension() - 1;
					for (Button btn : dimWaxs) btn.setSelection(false);
					dimWaxs[idxDim].setSelection(true);
					
					Amount<Length> pxSize = detSettings.getPxSize();
					if (pxSize != null)
						pxWaxs.setText(String.format("%.3f", pxSize.doubleValue(SI.MILLIMETER)));
				}
			}
		}
	}

	private Double getSaxsPixel(boolean scale) {
		try {
			Double val = Double.valueOf(pxSaxs.getText());  
			if (scale)
				return val / 1000.0;
			return val;
		}
		catch (Exception e) {
			String msg = "SCISOFT NCD: Error reading SAXS detector pixel size";
			logger.error(msg, e);
			return null;
		}
	}
	
	private Double getWaxsPixel(boolean scale) {
		try {
			Double val = Double.valueOf(pxWaxs.getText());  
			if (scale)
				return val / 1000.0;
			return val;
		}
		catch (Exception e) {
			String msg = "SCISOFT NCD: Error reading WAXS detector pixel size";
			logger.error(msg, e);
			return null;
		}
	}
	
	private Double getQGradient() {
		String input = qGradient.getText();
		if (input!=null  && inputQAxis.getSelection()) {
			try {
				Double val = Double.valueOf(qGradient.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Error reading q-axis calibration gradient";
				logger.error(msg);
			}
		}
		return null;
	}
	
	private Double getQIntercept() {
		String input = qIntercept.getText();
		if (input!=null  && inputQAxis.getSelection()) {
			try {
				Double val = Double.valueOf(qIntercept.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Error reading q-axis calibration intercept";
				logger.error(msg);
			}
		}
		return null;
	}
	
	private String getQUnit() {
		if (inputQAxis.getSelection()) {
			for (Entry<String, Button> unitBtn : unitSel.entrySet())
				if (unitBtn.getValue().getSelection())
					return unitBtn.getKey();
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
				for (Button dim : dimWaxs)
					dim.setEnabled(true);
			    if (detListWaxs.getItemCount() > 0 && detListSaxs.getSelectionIndex() >= 0) {
					detListWaxs.notifyListeners(SWT.Selection, null);
			    }
			} else {
				detListWaxs.setEnabled(false);
				pxWaxs.setEnabled(false);
				pxWaxsLabel.setEnabled(false);
				for (Button dim : dimWaxs)
					dim.setEnabled(false);
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
				for (Button dim : dimSaxs)
					dim.setEnabled(true);
			    if (detListSaxs.getItemCount() > 0 && detListSaxs.getSelectionIndex() >= 0) {
					detListSaxs.notifyListeners(SWT.Selection, null);
			    }
			} else {
				detListSaxs.setEnabled(false);
				pxSaxs.setEnabled(false);
				pxSaxsLabel.setEnabled(false);
				for (Button dim : dimSaxs)
					dim.setEnabled(false);
			}
			
		}
	};

	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		this.memento = memento;
		super.init(site, memento);
	}
	
	@Override
	public void saveState(IMemento memento) {
		
		if (memento != null) {
			
			memento.putBoolean(NcdPreferences.NCD_WAXS, detTypeWaxs.getSelection());
			memento.putBoolean(NcdPreferences.NCD_SAXS, detTypeSaxs.getSelection());
			
			memento.putString(NcdPreferences.NCD_QGRADIENT, qGradient.getText());
			memento.putString(NcdPreferences.NCD_QINTERCEPT, qIntercept.getText());
			memento.putString(NcdPreferences.NCD_QINTERCEPT, qIntercept.getText());
			memento.putBoolean(NcdPreferences.NCD_QOVERRIDE, inputQAxis.getSelection());
			for (Entry<String, Button> unitBtn : unitSel.entrySet())
				if (unitBtn.getValue().getSelection()) {
					memento.putString(NcdPreferences.NCD_QUNIT, unitBtn.getKey());
					break;
				}
			
			memento.putInteger(NcdPreferences.NCD_WAXS_INDEX, detListWaxs.getSelectionIndex());
			memento.putInteger(NcdPreferences.NCD_SAXS_INDEX, detListSaxs.getSelectionIndex());
			
			HashMap<String, NcdDetectorSettings> detList = ncdDetectorSourceProvider.getNcdDetectors();
			for (Entry<String, NcdDetectorSettings> tmpDet : detList.entrySet()) {
				if (tmpDet.getValue().getType().equals(DetectorTypes.WAXS_DETECTOR)) {
					IMemento detMemento = memento.createChild(NcdPreferences.NCD_WAXS_DETECTOR, tmpDet.getKey());
					Amount<Length> pixels = tmpDet.getValue().getPxSize();
					if (pixels != null)
						detMemento.putFloat(NcdPreferences.NCD_PIXEL, (float) pixels.doubleValue(SI.MILLIMETER));
					int detDim = tmpDet.getValue().getDimmension();
					detMemento.putInteger(NcdPreferences.NCD_DIM, detDim);
				}
				if (tmpDet.getValue().getType().equals(DetectorTypes.SAXS_DETECTOR)) {
					IMemento detMemento = memento.createChild(NcdPreferences.NCD_SAXS_DETECTOR, tmpDet.getKey());
					Amount<Length> pixels = tmpDet.getValue().getPxSize();
					if (pixels != null)
						detMemento.putFloat(NcdPreferences.NCD_PIXEL, (float) pixels.doubleValue(SI.MILLIMETER));
					int detDim = tmpDet.getValue().getDimmension();
					detMemento.putInteger(NcdPreferences.NCD_DIM, detDim);
				}
			}
		}
	}
	
	private void restoreState() {
		if (memento != null) {
			Boolean val;
			Integer idx;
			String tmp;
			
			IMemento[] waxsMemento = memento.getChildren(NcdPreferences.NCD_WAXS_DETECTOR);
			if (waxsMemento != null) {
				detListWaxs.removeAll(); 
				for (IMemento det: waxsMemento) {
					NcdDetectorSettings ncdDetector = new NcdDetectorSettings(det.getID(), DetectorTypes.WAXS_DETECTOR, 1);
					Float px = det.getFloat(NcdPreferences.NCD_PIXEL);
					if (px != null)
						ncdDetector.setPxSize(Amount.valueOf(px.doubleValue(), SI.MILLIMETER));
					Integer dim = det.getInteger(NcdPreferences.NCD_DIM);
					if (dim != null)
						ncdDetector.setDimmension(dim.intValue());
					ncdDetectorSourceProvider.addNcdDetector(ncdDetector);
				}
			}
			
			idx = memento.getInteger(NcdPreferences.NCD_WAXS_INDEX);
			if (idx != null) {
				detListWaxs.select(idx);
				detListWaxs.notifyListeners(SWT.Selection, null);
			}
			
			IMemento[] saxsMemento = memento.getChildren(NcdPreferences.NCD_SAXS_DETECTOR);
			if (saxsMemento != null) {
				detListSaxs.removeAll(); 
				for (IMemento det: saxsMemento) {
					NcdDetectorSettings ncdDetector = new NcdDetectorSettings(det.getID(), DetectorTypes.SAXS_DETECTOR, 2);
					Float px = det.getFloat(NcdPreferences.NCD_PIXEL);
					if (px != null)
						ncdDetector.setPxSize(Amount.valueOf(px.doubleValue(), SI.MILLIMETER));
					Integer dim = det.getInteger(NcdPreferences.NCD_DIM);
					if (dim != null)
						ncdDetector.setDimmension(dim.intValue());
					ncdDetectorSourceProvider.addNcdDetector(ncdDetector);
				}
			}
			idx = memento.getInteger(NcdPreferences.NCD_SAXS_INDEX);
			if (idx != null) {
				detListSaxs.select(idx);
				detListSaxs.notifyListeners(SWT.Selection, null);
			}

			val = memento.getBoolean(NcdPreferences.NCD_WAXS);
			if (val!=null) {
				detTypeWaxs.setSelection(val);
				if (val.booleanValue()) detTypeWaxs.notifyListeners(SWT.Selection, null);
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_SAXS);
			if (val!=null) {
				detTypeSaxs.setSelection(val);
				if (val.booleanValue()) detTypeSaxs.notifyListeners(SWT.Selection, null);
			}
			
			tmp = this.memento.getString(NcdPreferences.NCD_QUNIT);
			if (tmp == null) tmp = NcdConstants.DEFAULT_UNIT;
			
			for (Entry<String, Button> unitBtn : unitSel.entrySet())
				if (unitBtn.getKey().equals(tmp)) unitBtn.getValue().setSelection(true);
				else unitBtn.getValue().setSelection(false);
			
			tmp = memento.getString(NcdPreferences.NCD_QGRADIENT);
			if (tmp != null) qGradient.setText(tmp);
			
			tmp = memento.getString(NcdPreferences.NCD_QINTERCEPT);
			if (tmp != null) qIntercept.setText(tmp);
			
			val = memento.getBoolean(NcdPreferences.NCD_QOVERRIDE);
			if (val!=null) {
				inputQAxis.setSelection(val);
				inputQAxis.notifyListeners(SWT.Selection, null);
			}
		}
	}
	
	@Override
	public void createPartControl(Composite parent) {
		
		ConfigureNcdSourceProviders();
		
		final ScrolledComposite sc = new ScrolledComposite(parent, SWT.VERTICAL);
		final Composite c = new Composite(sc, SWT.NONE);
		GridLayout grid = new GridLayout(3, false);
		c.setLayout(grid);

		Group g = new Group(c, SWT.NONE);
		g.setLayout(new GridLayout(4, false));
		g.setText("Experiment Parameters");
		g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1));
		
		Group gpSelectMode = new Group(g, SWT.NONE);
		gpSelectMode.setLayout(new GridLayout(7, false));
		gpSelectMode.setText("Detectors");
		gpSelectMode.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));

		detTypeWaxs = new Button(gpSelectMode, SWT.CHECK);
		detTypeWaxs.setText("WAXS");
		detTypeWaxs.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		detTypeWaxs.addSelectionListener(modeSelectionListenerWaxs);
		detTypeWaxs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdWaxsSourceProvider.setEnableWaxs(detTypeWaxs.getSelection());
			}
		});

		
		detListWaxs = new Combo(gpSelectMode, SWT.NONE);
		GridData gridData = new GridData(GridData.FILL, SWT.CENTER, true, false, 2, 1);
		detListWaxs.setLayoutData(gridData);
		detListWaxs.setToolTipText("Select the WAXS detector used in data collection");
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
		
		Group gpDimWaxs = new Group(gpSelectMode, SWT.NONE);
		gpDimWaxs.setLayout(new GridLayout(2, false));
		gpDimWaxs.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		gpDimWaxs.setToolTipText("Select the WAXS detector dimensionality");
		dimWaxs = new Button[NcdConstants.dimChoices.length];
		for (int i = 0; i < NcdConstants.dimChoices.length; i++) {
			dimWaxs[i] = new Button(gpDimWaxs, SWT.RADIO);
			dimWaxs[i].setText(NcdConstants.dimChoices[i]);
			dimWaxs[i].setToolTipText("Select the WAXS detector dimensionality");
			dimWaxs[i].setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
			dimWaxs[i].addSelectionListener(new SelectionAdapter() {
				
				@Override
				public void widgetSelected(SelectionEvent e) {
					String waxsDetector = ncdWaxsDetectorSourceProvider.getWaxsDetector();
					NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(waxsDetector);
					if (detSettings != null) {
						for (int i = 0; i < dimWaxs.length; i++) {
							if (dimWaxs[i].getSelection()) {
								detSettings.setDimmension(i + 1);
								ncdDetectorSourceProvider.addNcdDetector(detSettings);
								break;
							}
						}
					}
				}
			});
		}
		
		pxWaxsLabel = new Label(gpSelectMode, SWT.NONE);
		pxWaxsLabel.setText("pixel (mm)");
		pxWaxs = new Text(gpSelectMode, SWT.BORDER);
		pxWaxs.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		pxWaxs.setToolTipText("Set detector pixel size");
		
		Button pxSave =  new Button(gpSelectMode, SWT.NONE);
		pxSave.setText("Save");
		pxSave.setToolTipText("Save detector information");
		pxSave.setLayoutData(new GridData(GridData.CENTER, SWT.FILL, false, true, 1, 2));
		pxSave.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (detTypeWaxs.isEnabled()) {
					String waxsDetector = ncdWaxsDetectorSourceProvider.getWaxsDetector();
					NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(waxsDetector);
					if (detSettings != null) {
						detSettings.setPxSize(Amount.valueOf(getWaxsPixel(false), SI.MILLIMETER));
						ncdDetectorSourceProvider.addNcdDetector(detSettings);
					}
				}
				if (detTypeSaxs.isEnabled()) {
					String saxsDetector = ncdSaxsDetectorSourceProvider.getSaxsDetector();
					NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(saxsDetector);
					if (detSettings != null) {
						detSettings.setPxSize(Amount.valueOf(getSaxsPixel(false), SI.MILLIMETER));
						ncdDetectorSourceProvider.addNcdDetector(detSettings);
					}
				}
			}
		});

		
		if (ncdWaxsSourceProvider.isEnableWaxs()) detTypeWaxs.setSelection(true);
		else detTypeWaxs.setSelection(false);
		modeSelectionListenerWaxs.widgetSelected(null);
		
		detTypeSaxs = new Button(gpSelectMode, SWT.CHECK);
		detTypeSaxs.setText("SAXS");
		detTypeSaxs.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		detTypeSaxs.addSelectionListener(modeSelectionListenerSaxs);
		detTypeSaxs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdSaxsSourceProvider.setEnableSaxs(detTypeSaxs.getSelection());
			}
		});
		
		detListSaxs = new Combo(gpSelectMode, SWT.NONE);
		gridData = new GridData(GridData.FILL, SWT.CENTER, true, false, 2, 1);
		detListSaxs.setLayoutData(gridData);
		detListSaxs.setToolTipText("Select the SAXS detector used in data collection");
		detListSaxs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int idx = detListSaxs.getSelectionIndex();
				if (idx >= 0) {
					String det = detListSaxs.getItem(idx);
					ncdSaxsDetectorSourceProvider.setSaxsDetector(det);
					try {
						IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
						IViewPart saxsView = page.findView(SaxsQAxisCalibration.ID);
						if (saxsView instanceof SaxsQAxisCalibration) ((SaxsQAxisCalibration)saxsView).updateResults(det);
					} catch (Exception e1) {
						logger.warn("Saxs calibration view unavailable", e1);
					}
				}
			}
		});
		
		
		Group gpDimSaxs = new Group(gpSelectMode, SWT.NONE);
		gpDimSaxs.setLayout(new GridLayout(2, false));
		gpDimSaxs.setToolTipText("Select the SAXS detector dimensionality");
		gpDimSaxs.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
		dimSaxs = new Button[NcdConstants.dimChoices.length];
		for (int i = 0; i < NcdConstants.dimChoices.length; i++) {
			dimSaxs[i] = new Button(gpDimSaxs, SWT.RADIO);
			dimSaxs[i].setText(NcdConstants.dimChoices[i]);
			dimSaxs[i].setToolTipText("Select the SAXS detector dimensionality");
			dimSaxs[i].setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
			dimSaxs[i].addSelectionListener(new SelectionAdapter() {
				
				@Override
				public void widgetSelected(SelectionEvent e) {
					String saxsDetector = ncdSaxsDetectorSourceProvider.getSaxsDetector();
					NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(saxsDetector);
					if (detSettings != null) {
						for (int i = 0; i < dimSaxs.length; i++) {
							if (dimSaxs[i].getSelection()) {
								detSettings.setDimmension(i + 1);
								ncdDetectorSourceProvider.addNcdDetector(detSettings);
								break;
							}
						}
					}
				}
			});
		}
		
		pxSaxsLabel = new Label(gpSelectMode, SWT.NONE);
		pxSaxsLabel.setText("pixel (mm)");
		pxSaxs = new Text(gpSelectMode, SWT.BORDER);
		pxSaxs.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		pxSaxs.setToolTipText("Set detector pixel size");
		
		if (ncdSaxsSourceProvider.isEnableSaxs()) detTypeSaxs.setSelection(true);
		else detTypeSaxs.setSelection(false);
		modeSelectionListenerSaxs.widgetSelected(null);
		
		inputQAxis = new Button(gpSelectMode, SWT.CHECK);
		inputQAxis.setText("q-calibration");
		inputQAxis.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		inputQAxis.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				boolean enabled = inputQAxis.getSelection();
				for (Button unitButton : unitSel.values())
					unitButton.setEnabled(enabled);
				qGradient.setEnabled(enabled);
				qGradientLabel.setEnabled(enabled);
				qIntercept.setEnabled(enabled);
				qInterceptLabel.setEnabled(enabled);
			}
		});
		Group unitGrp = new Group(gpSelectMode, SWT.NONE);
		unitGrp.setLayout(new GridLayout(2, false));
		unitGrp.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		unitGrp.setToolTipText("Select q-axis calibration units");
		unitSel = new HashMap<String, Button>(2);
		Button tmpUnitSel = new Button(unitGrp, SWT.RADIO);
		tmpUnitSel.setText(NcdConstants.unitChoices[0]);
		tmpUnitSel.setToolTipText("calibrate q-axis in Ångstroms");
		tmpUnitSel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, true));
		tmpUnitSel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdQUnitSourceProvider.setQUnit(getQUnit());
			}
		});
		unitSel.put(NcdConstants.unitChoices[0], tmpUnitSel);
		tmpUnitSel = new Button(unitGrp, SWT.RADIO);
		tmpUnitSel.setText(NcdConstants.unitChoices[1]);
		tmpUnitSel.setToolTipText("calibrate q-axis in nanometers");
		tmpUnitSel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, true));
		tmpUnitSel.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdQUnitSourceProvider.setQUnit(getQUnit());
			}
		});
		unitSel.put(NcdConstants.unitChoices[1], tmpUnitSel);
		
		qGradientLabel = new Label(gpSelectMode, SWT.NONE);
		qGradientLabel.setText("Gradient");
		qGradientLabel.setLayoutData(new GridData(GridData.END, SWT.CENTER, false, false));
		qGradient = new Text(gpSelectMode, SWT.BORDER);
		qGradient.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		qGradient.setToolTipText("Input q-axis calibration line fit gradient");
		qGradient.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdQGradientSourceProvider.setQGradient(getQGradient());
			}
		});

		qInterceptLabel = new Label(gpSelectMode, SWT.NONE);
		qInterceptLabel.setText("Intercept");
		qInterceptLabel.setLayoutData(new GridData(GridData.END, SWT.CENTER, true, false));
		qIntercept = new Text(gpSelectMode, SWT.BORDER);
		qIntercept.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false, 2, 1));
		qIntercept.setToolTipText("Input q-axis calibration line fit intercept");
		qIntercept.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdQInterceptSourceProvider.setQIntercept(getQIntercept());
			}
		});

		
		inputQAxis.setSelection(false);
		inputQAxis.notifyListeners(SWT.Selection, null);
		
		sc.setContent(c);
		sc.setExpandVertical(true);
		sc.setExpandHorizontal(true);
		sc.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Rectangle r = sc.getClientArea();
				sc.setMinSize(c.computeSize(r.width, SWT.DEFAULT));
			}
		});
		
		restoreState();
	}

	private void ConfigureNcdSourceProviders() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		
		ncdWaxsSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXS_STATE);
		ncdSaxsSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXS_STATE);
		
		ncdWaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXSDETECTOR_STATE);
		ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		
		ncdQGradientSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.QGRADIENT_STATE);
		ncdQInterceptSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.QINTERCEPT_STATE);
		ncdQUnitSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.QUNIT_STATE);
		
		ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);

		ncdDetectorSourceProvider.addSourceProviderListener(this);
		ncdSaxsDetectorSourceProvider.addSourceProviderListener(this);
		ncdWaxsDetectorSourceProvider.addSourceProviderListener(this);
	}

	@Override
	public void setFocus() {
	}

}
