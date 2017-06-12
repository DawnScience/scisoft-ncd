/*
 * Copyright 2012, 2017 Diamond Light Source Ltd.
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

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Length;

import tec.units.ri.quantity.Quantities;
import tec.units.ri.unit.MetricPrefix;
import tec.units.ri.unit.Units;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Rectangle;
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
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISourceProviderListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.core.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.preferences.NcdConstants;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;

public class NcdDetectorParameters extends ViewPart implements ISourceProviderListener {

	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.NcdDetectorParameters";
	
	private IMemento memento;
	
	private NcdProcessingSourceProvider ncdWaxsSourceProvider, ncdWaxsDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdSaxsSourceProvider, ncdSaxsDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdScalerSourceProvider;
	
	private NcdCalibrationSourceProvider<?, ?> ncdDetectorSourceProvider;
	
	
	private static Button[] dimWaxs, dimSaxs;

	private static Button detTypeWaxs, detTypeSaxs;
	private static Text pxWaxs, pxSaxs;
	private static Combo detListWaxs, detListSaxs;
	private Label pxSaxsLabel, pxWaxsLabel;
	
	private static Combo calList;
	private Label calListLabel, normChanLabel;
	private static Spinner normChan;

	private static final Unit<Length> MILLIMETRE = MetricPrefix.MILLI(Units.METRE);

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
			
			memento.putInteger(NcdPreferences.NCD_WAXS_INDEX, detListWaxs.getSelectionIndex());
			memento.putInteger(NcdPreferences.NCD_SAXS_INDEX, detListSaxs.getSelectionIndex());
			
			HashMap<String, NcdDetectorSettings> detList = ncdDetectorSourceProvider.getNcdDetectors();
			for (Entry<String, NcdDetectorSettings> tmpDet : detList.entrySet()) {
				if (tmpDet.getValue().getType().equals(DetectorTypes.WAXS_DETECTOR)) {
					IMemento detMemento = memento.createChild(NcdPreferences.NCD_WAXS_DETECTOR, tmpDet.getKey());
					Quantity<Length> pixels = tmpDet.getValue().getPxSize();
					if (pixels != null) {
						detMemento.putFloat(NcdPreferences.NCD_PIXEL, (float) pixels.to(MILLIMETRE).getValue().doubleValue());
					}
					int detDim = tmpDet.getValue().getDimension();
					detMemento.putInteger(NcdPreferences.NCD_DIM, detDim);
				}
				if (tmpDet.getValue().getType().equals(DetectorTypes.SAXS_DETECTOR)) {
					IMemento detMemento = memento.createChild(NcdPreferences.NCD_SAXS_DETECTOR, tmpDet.getKey());
					Quantity<Length> pixels = tmpDet.getValue().getPxSize();
					if (pixels != null) {
						detMemento.putFloat(NcdPreferences.NCD_PIXEL, (float) pixels.to(MILLIMETRE).getValue().doubleValue());
					}
					int detDim = tmpDet.getValue().getDimension();
					detMemento.putInteger(NcdPreferences.NCD_DIM, detDim);
				}
				if (tmpDet.getValue().getType().equals(DetectorTypes.CALIBRATION_DETECTOR)) {
					IMemento detMemento = memento.createChild(NcdPreferences.NCD_NORM_DETECTOR, tmpDet.getKey());
					Integer maxChannel = tmpDet.getValue().getMaxChannel();
					detMemento.putInteger(NcdPreferences.NCD_MAXCHANNEL, maxChannel);
					Integer normChannel = tmpDet.getValue().getNormChannel();
					detMemento.putInteger(NcdPreferences.NCD_MAXCHANNEL_INDEX, normChannel);
				}
			}
			
			memento.putInteger(NcdPreferences.NCD_NORM_INDEX, calList.getSelectionIndex());
		}
	}
	
	private void restoreState() {
		if (memento != null) {
			Boolean val;
			Integer idx;
			
			IMemento[] waxsMemento = memento.getChildren(NcdPreferences.NCD_WAXS_DETECTOR);
			if (waxsMemento != null) {
				detListWaxs.removeAll(); 
				for (IMemento det: waxsMemento) {
					NcdDetectorSettings ncdDetector = new NcdDetectorSettings(det.getID(), DetectorTypes.WAXS_DETECTOR, 1);
					Float px = det.getFloat(NcdPreferences.NCD_PIXEL);
					if (px != null)
						ncdDetector.setPxSize(Quantities.getQuantity(px.doubleValue(), MILLIMETRE));
					Integer dim = det.getInteger(NcdPreferences.NCD_DIM);
					if (dim != null)
						ncdDetector.setDimension(dim.intValue());
					ncdDetectorSourceProvider.addNcdDetector(ncdDetector);
				}
			}
			ncdDetectorSourceProvider.updateNcdDetectors();
			
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
						ncdDetector.setPxSize(Quantities.getQuantity(px.doubleValue(), MILLIMETRE));
					Integer dim = det.getInteger(NcdPreferences.NCD_DIM);
					if (dim != null)
						ncdDetector.setDimension(dim.intValue());
					ncdDetectorSourceProvider.addNcdDetector(ncdDetector);
				}
			}
			ncdDetectorSourceProvider.updateNcdDetectors();
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
			
			IMemento[] normMemento = memento.getChildren(NcdPreferences.NCD_NORM_DETECTOR);
			if (normMemento != null) {
				calList.removeAll(); 
				for (IMemento det: normMemento) {
					NcdDetectorSettings ncdDetector = new NcdDetectorSettings(det.getID(), DetectorTypes.CALIBRATION_DETECTOR, 1);
					ncdDetector.setMaxChannel(det.getInteger(NcdPreferences.NCD_MAXCHANNEL));
					ncdDetector.setNormChannel(det.getInteger(NcdPreferences.NCD_MAXCHANNEL_INDEX));
					ncdDetectorSourceProvider.addNcdDetector(ncdDetector);
				}
			}
			ncdDetectorSourceProvider.updateNcdDetectors();
			idx = memento.getInteger(NcdPreferences.NCD_NORM_INDEX);
			if (idx != null) {
				calList.select(idx);
				calList.notifyListeners(SWT.Selection, null);
			}
		}
	}
	
	@Override
	public void createPartControl(Composite parent) {
		
		ConfigureNcdSourceProviders();
		
		final ScrolledComposite sc = new ScrolledComposite(parent, SWT.VERTICAL);
		final Composite c = new Composite(sc, SWT.NONE);
		GridLayout grid = new GridLayout(7, false);
		c.setLayout(grid);

		detTypeWaxs = new Button(c, SWT.CHECK);
		detTypeWaxs.setText("WAXS");
		detTypeWaxs.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		detTypeWaxs.addSelectionListener(modeSelectionListenerWaxs);
		detTypeWaxs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdWaxsSourceProvider.setEnableWaxs(detTypeWaxs.getSelection());
			}
		});

		
		detListWaxs = new Combo(c, SWT.NONE);
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
		
		Group gpDimWaxs = new Group(c, SWT.NONE);
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
					NcdDetectorSettings detSettings = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(waxsDetector);
					if (detSettings != null) {
						for (int i = 0; i < dimWaxs.length; i++) {
							if (dimWaxs[i].getSelection()) {
								detSettings.setDimension(i + 1);
								ncdDetectorSourceProvider.addNcdDetector(detSettings);
								break;
							}
						}
					}
				}
			});
		}
		
		pxWaxsLabel = new Label(c, SWT.NONE);
		pxWaxsLabel.setText("pixel (mm)");
		pxWaxsLabel.setLayoutData(new GridData(GridData.END, SWT.CENTER, true, false));
		pxWaxs = new Text(c, SWT.BORDER);
		pxWaxs.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false, 2, 1));
		pxWaxs.setToolTipText("Set detector pixel size");
		pxWaxs.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (detTypeWaxs.isEnabled()) {
					String waxsDetector = ncdWaxsDetectorSourceProvider.getWaxsDetector();
					NcdDetectorSettings detSettings = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(waxsDetector);
					if (detSettings != null) {
						Double waxsPixel = getWaxsPixel();
						if (waxsPixel != null)
							detSettings.setPxSize(Quantities.getQuantity(waxsPixel, MILLIMETRE));
					}
				}
			}
		});
		
		if (ncdWaxsSourceProvider.isEnableWaxs()) detTypeWaxs.setSelection(true);
		else detTypeWaxs.setSelection(false);
		modeSelectionListenerWaxs.widgetSelected(null);
		
		detTypeSaxs = new Button(c, SWT.CHECK);
		detTypeSaxs.setText("SAXS");
		detTypeSaxs.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		detTypeSaxs.addSelectionListener(modeSelectionListenerSaxs);
		detTypeSaxs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ncdSaxsSourceProvider.setEnableSaxs(detTypeSaxs.getSelection());
			}
		});
		
		detListSaxs = new Combo(c, SWT.NONE);
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
				}
			}
		});
		
		
		Group gpDimSaxs = new Group(c, SWT.NONE);
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
					NcdDetectorSettings detSettings = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(saxsDetector);
					if (detSettings != null) {
						for (int i = 0; i < dimSaxs.length; i++) {
							if (dimSaxs[i].getSelection()) {
								detSettings.setDimension(i + 1);
								ncdDetectorSourceProvider.addNcdDetector(detSettings);
								break;
							}
						}
					}
				}
			});
		}
		
		pxSaxsLabel = new Label(c, SWT.NONE);
		pxSaxsLabel.setText("pixel (mm)");
		pxSaxsLabel.setLayoutData(new GridData(GridData.END, SWT.CENTER, true, false));
		pxSaxs = new Text(c, SWT.BORDER);
		pxSaxs.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false, 2, 1));
		pxSaxs.setToolTipText("Set detector pixel size");
		pxSaxs.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				if (detTypeSaxs.isEnabled()) {
					String saxsDetector = ncdSaxsDetectorSourceProvider.getSaxsDetector();
					NcdDetectorSettings detSettings = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(saxsDetector);
					if (detSettings != null) {
						Double saxsPixel = getSaxsPixel();
						if (saxsPixel != null)
							detSettings.setPxSize(Quantities.getQuantity(saxsPixel, MILLIMETRE));
					}
				}
			}
		});
		
		Group gpNorm = new Group(c, SWT.NONE);
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
			String tmpScaler = ncdScalerSourceProvider.getScaler();
			if (tmpScaler != null) {
				calList.add(tmpScaler);
			}
			calList.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					int idx = calList.getSelectionIndex();
					if (idx >= 0) {
						String detName = calList.getItem(idx);
						ncdScalerSourceProvider.setScaler(detName);
						
						NcdDetectorSettings calDet = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(detName);
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
			NcdDetectorSettings calDet = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(scaler);
			if (calDet != null) {
				normChan.setSelection(calDet.getNormChannel());
			}
			normChan.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					String scaler = ncdScalerSourceProvider.getScaler();
					NcdDetectorSettings calDet = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(scaler);
					if (calDet != null) {
						calDet.setNormChannel(normChan.getSelection());
						ncdDetectorSourceProvider.addNcdDetector(calDet);
					}
				}
			});
		}
		
		
		if (ncdSaxsSourceProvider.isEnableSaxs()) {
			detTypeSaxs.setSelection(true);
		} else {
			detTypeSaxs.setSelection(false);
		}
		modeSelectionListenerSaxs.widgetSelected(null);
		
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
		
		ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
		ncdScalerSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SCALER_STATE);

		ncdDetectorSourceProvider.addSourceProviderListener(this);
		ncdSaxsDetectorSourceProvider.addSourceProviderListener(this);
		ncdWaxsDetectorSourceProvider.addSourceProviderListener(this);
		ncdScalerSourceProvider.addSourceProviderListener(this);
	}

	@Override
	public void sourceChanged(int sourcePriority, @SuppressWarnings("rawtypes") Map sourceValuesByName) {
		for (Object key : sourceValuesByName.keySet()) {
			if (key instanceof String) {
				String name = (String) key;
				sourceChanged(sourcePriority, name, sourceValuesByName.get(name));
			}
		}
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, Object sourceValue) {
		if (sourceName.equals(NcdCalibrationSourceProvider.NCDDETECTORS_STATE)) {
			if ((detListSaxs != null) && (detListWaxs != null) &&
					!(detListSaxs.isDisposed())	&& !(detListWaxs.isDisposed())) {
				int idxSaxs = detListSaxs.getSelectionIndex();
				String saveDetSaxs = null;
				if (idxSaxs != -1) {
					saveDetSaxs = detListSaxs.getItem(idxSaxs);
				}
				int idxWaxs = detListWaxs.getSelectionIndex();
				String saveDetWaxs = null;
				if (idxWaxs != -1) {
					saveDetWaxs = detListWaxs.getItem(idxWaxs);
				}
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

				// Restore saved detector selection
				// If no detector was selected, select first one in the list
				// If saved detector selection isn't available in the updated list, select first one
				if (detListSaxs.getItemCount() > 0) {
					idxSaxs = (idxSaxs != -1) ? ArrayUtils.indexOf(detListSaxs.getItems(), saveDetSaxs) : 0;
					idxSaxs = (idxSaxs == -1) ? 0 : idxSaxs;
					detListSaxs.select(idxSaxs);
					ncdSaxsDetectorSourceProvider.setSaxsDetector(detListSaxs.getItem(idxSaxs));
				}

				if (detListWaxs.getItemCount() > 0) {
					idxWaxs = (idxWaxs != -1) ? ArrayUtils.indexOf(detListWaxs.getItems(), saveDetWaxs) : 0;
					idxWaxs = (idxWaxs == -1) ? 0 : idxWaxs;
					detListWaxs.select(idxWaxs);
					ncdWaxsDetectorSourceProvider.setWaxsDetector(detListWaxs.getItem(idxWaxs));
				}
			}
			
			if (calList != null && !(calList.isDisposed())) {
				int idxSel = calList.getSelectionIndex();
				String saveSelection = ncdScalerSourceProvider.getScaler();
				calList.removeAll();
				if (sourceValue instanceof HashMap<?, ?>) {
					for (Object settings : ((HashMap<?, ?>) sourceValue).values()) {
						if (settings instanceof NcdDetectorSettings) {

							NcdDetectorSettings detSettings = (NcdDetectorSettings) settings;

							if (detSettings.getType().equals(DetectorTypes.CALIBRATION_DETECTOR)) {
								calList.add(detSettings.getName());
								continue;
							}
						}
					}
				}
				if (calList.getItemCount() > 0) {
					idxSel = (idxSel != -1) ? ArrayUtils.indexOf(calList.getItems(), saveSelection) : 0;
					idxSel = (idxSel == -1) ? 0 : idxSel;
					calList.select(idxSel);
					ncdScalerSourceProvider.setScaler(calList.getItem(idxSel));
					calList.notifyListeners(SWT.Selection, null);
				}
			}
		}

		if (sourceName.equals(NcdProcessingSourceProvider.SAXSDETECTOR_STATE)) {
			if (sourceValue instanceof String) {
				NcdDetectorSettings detSettings = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(sourceValue);
				if (detSettings != null) {
					int idxDim = detSettings.getDimension() - 1;
					if (dimSaxs != null && !(dimSaxs[idxDim].isDisposed())) {
						for (Button btn : dimSaxs) btn.setSelection(false);
						dimSaxs[idxDim].setSelection(true);
					}
					Quantity<Length> pxSize = detSettings.getPxSize();
					if (pxSaxs != null && !(pxSaxs.isDisposed())) {
						if (pxSize != null) {
							String pxText = String.format("%.3f", pxSize.to(MILLIMETRE).getValue().doubleValue());
							if (!(pxText.equals(pxSaxs.getText()))) {
								pxSaxs.setText(pxText);
							}
						} else {
							pxSaxs.setText("");
						}
					}
				}
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.WAXSDETECTOR_STATE)) {
			if (sourceValue instanceof String) {
				NcdDetectorSettings detSettings = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(sourceValue);
				if (detSettings != null) {
					int idxDim = detSettings.getDimension() - 1;
					if (dimWaxs != null && !(dimWaxs[idxDim].isDisposed())) {
						for (Button btn : dimWaxs) btn.setSelection(false);
						dimWaxs[idxDim].setSelection(true);
					}
					Quantity<Length> pxSize = detSettings.getPxSize();
					if (pxWaxs != null && !(pxWaxs.isDisposed())) {
						if (pxSize != null) {
							String pxText = String.format("%.3f", pxSize.to(MILLIMETRE).getValue().doubleValue());
							if (!(pxText.equals(pxWaxs.getText()))) {
								pxWaxs.setText(pxText);
							}
						} else {
							pxWaxs.setText("");
						}
					}
				}
			}
		}
		
		if(sourceName.equals(NcdProcessingSourceProvider.WAXS_STATE)){
			if(detTypeWaxs != null && !detTypeWaxs.isDisposed()){
				boolean tmpBool = detTypeWaxs.getSelection();
				if(sourceValue != null && (Boolean) sourceValue != tmpBool){
					detTypeWaxs.setSelection((Boolean) sourceValue);
					
					detListWaxs.setEnabled((Boolean) sourceValue);
					pxWaxs.setEnabled((Boolean) sourceValue);
					pxWaxsLabel.setEnabled((Boolean) sourceValue);
					for (Button dim : dimWaxs)
						dim.setEnabled((Boolean) sourceValue);
				}
			}
		}
		if(sourceName.equals(NcdProcessingSourceProvider.SAXS_STATE)){
			if(detTypeSaxs != null && !detTypeSaxs.isDisposed()){
				boolean tmpBool = detTypeSaxs.getSelection();
				if(sourceValue != null && (Boolean) sourceValue != tmpBool){
					detTypeSaxs.setSelection((Boolean) sourceValue);
					
					detListSaxs.setEnabled((Boolean) sourceValue);
					pxSaxs.setEnabled((Boolean) sourceValue);
					pxSaxsLabel.setEnabled((Boolean) sourceValue);
					for (Button dim : dimSaxs)
						dim.setEnabled((Boolean) sourceValue);
				}
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.SCALER_STATE)) {
			if (sourceValue instanceof String) {
				if ((calList != null) && !(calList.isDisposed())) {
					int idxSel = calList.indexOf((String) sourceValue);
					if (idxSel != -1) {
						calList.select(idxSel);
					} else {
						return;
					}
				}
				if ((normChan != null) && !(normChan.isDisposed())) {
					NcdDetectorSettings detSettings = (NcdDetectorSettings) ncdDetectorSourceProvider.getNcdDetectors().get(sourceValue);
					if (detSettings != null) {
						int max = detSettings.getMaxChannel();
						normChan.setMaximum(max);
						normChan.setSelection(detSettings.getNormChannel());
					}
				}
			}
		}	
	}

	@Override
	public void setFocus() {
		getViewSite().getShell().setFocus();
	}

}
