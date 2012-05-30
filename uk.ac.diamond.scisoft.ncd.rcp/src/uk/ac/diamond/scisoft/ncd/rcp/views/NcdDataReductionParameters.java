/*
 * Copyright 2011 Diamond Light Source Ltd.
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

import java.io.File;
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
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
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
import uk.ac.diamond.scisoft.ncd.data.SliceInput;
import uk.ac.diamond.scisoft.ncd.preferences.NcdConstants;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionParameters extends ViewPart implements ISourceProviderListener {

	private static final Logger logger = LoggerFactory.getLogger(NcdDataReductionParameters.class);
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters"; //$NON-NLS-1$

	private IMemento memento;

	private Spinner normChan;
	private Text bgFramesStart, bgFramesStop, detFramesStart, detFramesStop, bgAdvanced, detAdvanced, gridAverage;
	private Text bgFile, drFile, bgScale, absScale;

	private Text location, energy, pxWaxs, pxSaxs, qGradient, qIntercept;
	
	private Button[] dimWaxs, dimSaxs;
	protected HashMap<String, Button> unitSel;

	private Button browse;
	private String inputDirectory = "/tmp";
	private Button bgButton, drButton, normButton, secButton, invButton, aveButton, browseBg, browseDr;
	
	private NcdProcessingSourceProvider ncdNormalisationSourceProvider, ncdScalerSourceProvider;
	private NcdProcessingSourceProvider ncdBackgroundSourceProvider;
	private NcdProcessingSourceProvider ncdResponseSourceProvider;
	private NcdProcessingSourceProvider ncdSectorSourceProvider;
	private NcdProcessingSourceProvider ncdInvariantSourceProvider;
	private NcdProcessingSourceProvider ncdAverageSourceProvider;
	private NcdProcessingSourceProvider ncdWaxsSourceProvider, ncdWaxsDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdSaxsSourceProvider, ncdSaxsDetectorSourceProvider;
	private NcdProcessingSourceProvider ncdNormChannelSourceProvider;
	private NcdProcessingSourceProvider ncdEnergySourceProvider, ncdMaskSourceProvider;
	private NcdProcessingSourceProvider ncdRadialSourceProvider, ncdAzimuthSourceProvider, ncdFastIntSourceProvider;
	private NcdProcessingSourceProvider ncdDataSliceSourceProvider, ncdBkgSliceSourceProvider, ncdGridAverageSourceProvider;
	private NcdProcessingSourceProvider ncdBgFileSourceProvider, ncdDrFileSourceProvider, ncdWorkingDirSourceProvider;
	private NcdProcessingSourceProvider ncdQGradientSourceProvider, ncdQInterceptSourceProvider, ncdQUnitSourceProvider;
	private NcdProcessingSourceProvider ncdAbsScaleSourceProvider, ncdBgScaleSourceProvider;
	
	private NcdCalibrationSourceProvider ncdDetectorSourceProvider;
	
	private Button detTypeWaxs, detTypeSaxs, useMask, bgAdvancedButton, detAdvancedButton, gridAverageButton, inputQAxis;
	private Button radialButton, azimuthalButton, fastIntButton;
	private Combo detListWaxs, detListSaxs, calList;
	private Label calListLabel, normChanLabel, bgLabel, bgScaleLabel, absScaleLabel, drLabel, pxSaxsLabel, pxWaxsLabel;
	private Label bgFramesStartLabel, bgFramesStopLabel, detFramesStartLabel, detFramesStopLabel, qGradientLabel, qInterceptLabel;

	private Double getEnergy() {
		return Double.valueOf(energy.getText());
	}

	private Double getAbsScale() {
		String input = absScale.getText();
		if (input!= null) {
			try {
				Double val = Double.valueOf(absScale.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Absolute intensity scaling factor was not set";
				logger.info(msg);
			}
		}
		return null;
	}
	
	private Double getBgScale() {
		String input = bgScale.getText();
		if (input!= null) {
			try {
				Double val = Double.valueOf(bgScale.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Background scaling factor was not set";
				logger.info(msg);
			}
		}
		return null;
	}
	
	private Integer getBgFirstFrame() {
		String input = bgFramesStart.getText();
		if (input!=null && bgFramesStart.isEnabled()) {
			try {
				Integer val = Integer.valueOf(bgFramesStart.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Starting from the first background frame";
				logger.info(msg);
			}
		}
		return null;
	}
	
	private Integer getBgLastFrame() {
		String input = bgFramesStop.getText();
		if (input!=null &&  detFramesStop.isEnabled()) {
			try {
				Integer val = Integer.valueOf(bgFramesStop.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Including the last background frame";
				logger.info(msg);
			}
		}
		return null;
	}
	
	private String getBgAdvancedSelection() {
		if (bgAdvanced.isEnabled())
			return bgAdvanced.getText();
		return null;
	}
	
	private Integer getDetFirstFrame() {
		String input = detFramesStart.getText();
		if (input!=null && detFramesStart.isEnabled()) {
			try {
				Integer val = Integer.valueOf(detFramesStart.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Starting from the first data frame";
				logger.info(msg);
			}
		}
		return null;
	}
	
	private Integer getDetLastFrame() {
		String input = detFramesStop.getText();
		if (input!=null  && detFramesStop.isEnabled()) {
			try {
				Integer val = Integer.valueOf(detFramesStop.getText());  
				return val;
			} catch (Exception e) {
				String msg = "SCISOFT NCD: Including the last data frame";
				logger.info(msg);
			}
		}
		return null;
	}
	
	private String getDetAdvancedSelection() {
		if (detAdvanced.isEnabled())
			return detAdvanced.getText();
		return null;
	}
	
	private String getGridAverageSelection() {
		if (gridAverage.isEnabled())
			return gridAverage.getText();
		return null;
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
			memento.putBoolean(NcdPreferences.NCD_STAGE_NORMALISATION, normButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_STAGE_BACKGROUND, bgButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_STAGE_RESPONSE, drButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_STAGE_SECTOR, secButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_STAGE_INVARIANT, invButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_STAGE_AVERAGE, aveButton.getSelection());
			
			memento.putBoolean(NcdPreferences.NCD_SECTOR_RADIAL, radialButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_SECTOR_AZIMUTH, azimuthalButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_SECTOR_FAST, fastIntButton.getSelection());
			
			memento.putString(NcdPreferences.NCD_BACKGROUNDSUBTRACTION, bgFile.getText());
			memento.putString(NcdPreferences.NCD_DETECTORRESPONSE, drFile.getText());
			
			Double absScale = getAbsScale();
			if (absScale != null)
				memento.putFloat(NcdPreferences.NCD_ABSOLUTESCALE, absScale.floatValue());
			
			Double bgScale = getBgScale();
			if (bgScale != null)
				memento.putFloat(NcdPreferences.NCD_BACKGROUNDSCALE, bgScale.floatValue());
			
			memento.putString(NcdPreferences.NCD_BGFIRSTFRAME, bgFramesStart.getText());
			memento.putString(NcdPreferences.NCD_BGLASTFRAME, bgFramesStop.getText());
			memento.putString(NcdPreferences.NCD_BGFRAMESELECTION, bgAdvanced.getText());
			memento.putString(NcdPreferences.NCD_DATAFIRSTFRAME, detFramesStart.getText());
			memento.putString(NcdPreferences.NCD_DATALASTFRAME, detFramesStop.getText());
			memento.putString(NcdPreferences.NCD_DATAFRAMESELECTION, detAdvanced.getText());
			memento.putBoolean(NcdPreferences.NCD_BGADVANCED, bgAdvancedButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_DATAADVANCED, detAdvancedButton.getSelection());
			
			memento.putString(NcdPreferences.NCD_GRIDAVERAGESELECTION, gridAverage.getText());
			memento.putBoolean(NcdPreferences.NCD_GRIDAVERAGE, gridAverageButton.getSelection());
			
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
			memento.putInteger(NcdPreferences.NCD_NORM_INDEX, calList.getSelectionIndex());
			
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
				if (tmpDet.getValue().getType().equals(DetectorTypes.CALIBRATION_DETECTOR)) {
					IMemento detMemento = memento.createChild(NcdPreferences.NCD_NORM_DETECTOR, tmpDet.getKey());
					int maxChannel = tmpDet.getValue().getMaxChannel();
					detMemento.putInteger(NcdPreferences.NCD_MAXCHANNEL, maxChannel);
				}
			}
			memento.putInteger(NcdPreferences.NCD_MAXCHANNEL_INDEX, normChan.getSelection());
			
			memento.putString(NcdPreferences.NCD_ENERGY, energy.getText());
			memento.putString(NcdPreferences.NCD_DIRECTORY, inputDirectory);
		}
	}
			
	private void restoreState() {
		if (memento != null) {
			Boolean val;
			Integer idx;
			Float flt;
			String tmp;
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_NORMALISATION);
			if (val!=null)
				ncdNormalisationSourceProvider.setEnableNormalisation(val);
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_BACKGROUND);
			if (val!=null)
				ncdBackgroundSourceProvider.setEnableBackground(val);
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_RESPONSE);
			if (val!=null)
				ncdResponseSourceProvider.setEnableDetectorResponse(val);
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_SECTOR);
			if (val!=null)
				ncdSectorSourceProvider.setEnableSector(val);
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_INVARIANT);
			if (val!=null) {
				invButton.setSelection(val);
				if (val.booleanValue()) invButton.notifyListeners(SWT.Selection, null);
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_AVERAGE);
			if (val!=null)
				ncdAverageSourceProvider.setEnableAverage(val);
			
			val = memento.getBoolean(NcdPreferences.NCD_SECTOR_RADIAL);
			if (val!=null) {
				radialButton.setSelection(val);
				radialButton.notifyListeners(SWT.Selection, null);
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_SECTOR_AZIMUTH);
			if (val!=null) {
				azimuthalButton.setSelection(val);
				azimuthalButton.notifyListeners(SWT.Selection, null);
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_SECTOR_FAST);
			if (val!=null) {
				fastIntButton.setSelection(val);
				fastIntButton.notifyListeners(SWT.Selection, null);
			}
			
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
			
			IMemento[] normMemento = memento.getChildren(NcdPreferences.NCD_NORM_DETECTOR);
			if (normMemento != null) {
				calList.removeAll(); 
				for (IMemento det: normMemento) {
					NcdDetectorSettings ncdDetector = new NcdDetectorSettings(det.getID(), DetectorTypes.CALIBRATION_DETECTOR, 1);
					ncdDetector.setMaxChannel(det.getInteger(NcdPreferences.NCD_MAXCHANNEL));
					ncdDetectorSourceProvider.addNcdDetector(ncdDetector);
				}
			}
			idx = memento.getInteger(NcdPreferences.NCD_NORM_INDEX);
			if (idx != null) {
				calList.select(idx);
				calList.notifyListeners(SWT.Selection, null);
			}
			
			idx = memento.getInteger(NcdPreferences.NCD_MAXCHANNEL_INDEX);
			if (idx != null) {
				normChan.setSelection(idx);
				ncdNormChannelSourceProvider.setNormChannel(idx);
			}
			
			flt = memento.getFloat(NcdPreferences.NCD_ABSOLUTESCALE);
			if (flt != null) {
				absScale.setText(flt.toString());
				ncdAbsScaleSourceProvider.setAbsScaling(new Double(flt));
			}
			
			tmp = memento.getString(NcdPreferences.NCD_BGFIRSTFRAME);
			if (tmp != null) bgFramesStart.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_BGLASTFRAME);
			if (tmp != null) bgFramesStop.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_BGFRAMESELECTION);
			if (tmp != null) bgAdvanced.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_DATAFIRSTFRAME);
			if (tmp != null) detFramesStart.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_DATALASTFRAME);
			if (tmp != null) detFramesStop.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_DATAFRAMESELECTION);
			if (tmp != null) detAdvanced.setText(tmp);
			
			val = memento.getBoolean(NcdPreferences.NCD_BGADVANCED);
			if (val!=null) {
				bgAdvancedButton.setSelection(val);
				if (val.booleanValue()) bgAdvancedButton.notifyListeners(SWT.Selection, null);
			}
			val = memento.getBoolean(NcdPreferences.NCD_DATAADVANCED);
			if (val!=null) {
				detAdvancedButton.setSelection(val);
				if (val.booleanValue()) detAdvancedButton.notifyListeners(SWT.Selection, null);
			}
			
			tmp = memento.getString(NcdPreferences.NCD_GRIDAVERAGESELECTION);
			if (tmp != null)
				gridAverage.setText(tmp);
			
			val = memento.getBoolean(NcdPreferences.NCD_GRIDAVERAGE);
			if (val!=null) {
				gridAverageButton.setSelection(val);
				if (val.booleanValue()) gridAverageButton.notifyListeners(SWT.Selection, null);
			}
			
			for (Entry<String, Button> unitBtn : unitSel.entrySet())
				if (unitBtn.getKey().equals(tmp)) unitBtn.getValue().setSelection(true);
				else unitBtn.getValue().setSelection(false);
			
			tmp = memento.getString(NcdPreferences.NCD_QGRADIENT);
			if (tmp != null) qGradient.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_QINTERCEPT);
			if (tmp != null) qIntercept.setText(tmp);
			tmp = this.memento.getString(NcdPreferences.NCD_QUNIT);
			if (tmp == null) tmp = NcdConstants.DEFAULT_UNIT;
			
			val = memento.getBoolean(NcdPreferences.NCD_QOVERRIDE);
			if (val!=null) {
				inputQAxis.setSelection(val);
				inputQAxis.notifyListeners(SWT.Selection, null);
			}
			
			
			tmp = memento.getString(NcdPreferences.NCD_BACKGROUNDSUBTRACTION);
			if (tmp != null) {
				bgFile.setText(tmp);
				ncdBgFileSourceProvider.setBgFile(tmp);
			}
			
			flt = memento.getFloat(NcdPreferences.NCD_BACKGROUNDSCALE);
			if (flt != null) {
				bgScale.setText(flt.toString());
				ncdBgScaleSourceProvider.setBgScaling(new Double(flt));
			}
			
			tmp = memento.getString(NcdPreferences.NCD_DETECTORRESPONSE);
			if (tmp != null) {
				drFile.setText(tmp);
				ncdDrFileSourceProvider.setDrFile(tmp);
			}
			
			tmp = memento.getString(NcdPreferences.NCD_ENERGY);
			if (tmp != null) {
				energy.setText(tmp);
				ncdEnergySourceProvider.setEnergy(getEnergy());
			}
			
			tmp = memento.getString(NcdPreferences.NCD_DIRECTORY);
			if (tmp != null) {
				location.setText(tmp);
				inputDirectory = tmp;
				ncdWorkingDirSourceProvider.setWorkingDir(inputDirectory);
			}
		}
	}
	
	@Override
	public void createPartControl(final Composite parent) {
		
		ConfigureNcdSourceProviders();
		
		final ScrolledComposite sc = new ScrolledComposite(parent, SWT.VERTICAL);
		final Composite c = new Composite(sc, SWT.NONE);
		GridLayout grid = new GridLayout(3, false);
		c.setLayout(grid);

		Group g = new Group(c, SWT.BORDER);
		g.setText("Data reduction pipeline");
		GridLayout gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		g.setLayout(gl);
		g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1));
		{
			normButton = new Button(g, SWT.CHECK);
			normButton.setText("1. Normalisation");
			normButton.setToolTipText("Enable normalisation step");
			normButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdNormalisationSourceProvider.setEnableNormalisation(normButton.getSelection());
				}

			});

			bgButton = new Button(g, SWT.CHECK);
			bgButton.setText("2. Background subtraction");
			bgButton.setToolTipText("Enable background subtraction step");
			bgButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdBackgroundSourceProvider.setEnableBackground(bgButton.getSelection());
				}
			});


			drButton = new Button(g, SWT.CHECK);
			drButton.setText("3. Detector response");
			drButton.setToolTipText("Enable detector response step");
			drButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdResponseSourceProvider.setEnableDetectorResponse(drButton.getSelection());
				}
			});

			secButton = new Button(g, SWT.CHECK);
			secButton.setText("4. Sector integration");
			secButton.setToolTipText("Enable sector integration step");
			secButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdSectorSourceProvider.setEnableSector(secButton.getSelection());
				}
			});

			invButton = new Button(g, SWT.CHECK);
			invButton.setText("5. Invariant");
			invButton.setToolTipText("Enable invariant calculation step");
			invButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
						ncdInvariantSourceProvider.setEnableInvariant(invButton.getSelection());
				}
			});

			aveButton = new Button(g, SWT.CHECK);
			aveButton.setText("6. Average");
			aveButton.setToolTipText("Enable average calculation step");
			aveButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdAverageSourceProvider.setEnableAverage(aveButton.getSelection());
				}
			});
		}

		g = new Group(c, SWT.BORDER);
		g.setText("Sector Integration Parameters");
		gl = new GridLayout(3, false);
		gl.horizontalSpacing = 15;
		g.setLayout(gl);
		g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1));
		{
			radialButton = new Button(g, SWT.CHECK);
			radialButton.setText("Radial Profile");
			radialButton.setToolTipText("Activate radial profile calculation");
			radialButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdRadialSourceProvider.setEnableRadial(radialButton.getSelection());
				}
			});

			azimuthalButton = new Button(g, SWT.CHECK);
			azimuthalButton.setText("Azimuthal Profile");
			azimuthalButton.setToolTipText("Activate azimuthal profile calculation");
			azimuthalButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdAzimuthSourceProvider.setEnableAzimuthal(azimuthalButton.getSelection());
				}
			});

			fastIntButton = new Button(g, SWT.CHECK);
			fastIntButton.setText("Fast Integration");
			fastIntButton.setToolTipText("Use fast algorithm for profile calculations");
			fastIntButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdFastIntSourceProvider.setEnableFastIntegration(fastIntButton.getSelection());
				}

			});
		}

		g = new Group(c, SWT.NONE);
		g.setLayout(new GridLayout(6, false));
		g.setText("Reference data");
		g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1));
		{
			calListLabel = new Label(g, SWT.NONE);
			calListLabel.setText("Normalisation Data");
			calList = new Combo(g, SWT.NONE);
			GridData gridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
			calList.setLayoutData(gridData);
			calList.setToolTipText("Select the detector with calibration data");
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
			normChan.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
			normChan.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdNormChannelSourceProvider.setNormChannel(normChan.getSelection());
				}
			});
			
			absScaleLabel = new Label(g, SWT.NONE);
			absScaleLabel.setText("Abs. Scale");
			absScaleLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			absScale = new Text(g, SWT.BORDER);
			absScale.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
			absScale.setToolTipText("Select absolute scaling factor for calibration data");
			absScale.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdAbsScaleSourceProvider.setAbsScaling(getAbsScale());
				}
			});

			bgLabel = new Label(g, SWT.NONE);
			bgLabel.setText("Background Subtraction File");
			bgFile = new Text(g, SWT.BORDER);
			bgFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
			bgFile.setToolTipText("File with the background measurments");
			bgFile.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdBgFileSourceProvider.setBgFile(bgFile.getText());
				}
			});

			browseBg = new Button(g, SWT.NONE);
			browseBg.setText("...");
			browseBg.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
			browseBg.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					FileDialog dChooser = new FileDialog(getViewSite().getShell());
					dChooser.setText("Select Background Data File");
					dChooser.setFilterNames(new String[] { "NeXus files", "All Files"});
					dChooser.setFilterExtensions(new String[] {"*.nxs", "*.*"});
					final File fl = new File(dChooser.open());
					if (fl.exists()) {
						bgFile.setText(fl.getAbsolutePath());
					}
				}
			});
			
			bgScaleLabel = new Label(g, SWT.NONE);
			bgScaleLabel.setText("Bg. Scale");
			bgScaleLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			bgScale = new Text(g, SWT.BORDER);
			bgScale.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
			bgScale.setToolTipText("Scaling values for background data");
			bgScale.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdBgScaleSourceProvider.setBgScaling(getBgScale());
				}
			});
			
			drLabel = new Label(g, SWT.NONE);
			drLabel.setText("Detector Response File");
			drFile = new Text(g, SWT.BORDER);
			drFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
			drFile.setToolTipText("File with the detector response frame");
			drFile.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdDrFileSourceProvider.setDrFile(drFile.getText());
				}
			});

			browseDr = new Button(g, SWT.NONE);
			browseDr.setText("...");
			browseDr.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
			browseDr.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					FileDialog dChooser = new FileDialog(getViewSite().getShell());
					dChooser.setText("Select Detector Response File");
					dChooser.setFilterNames(new String[] { "NeXus files", "All Files"});
					dChooser.setFilterExtensions(new String[] {"*.nxs", "*.*"});
					final File fl = new File(dChooser.open());
					if (fl.exists()) {
						drFile.setText(fl.toString());
					}
				}
			});
		}

		g = new Group(c, SWT.NONE);
		g.setLayout(new GridLayout(4, false));
		g.setText("Background frame selection");
		g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		{
			bgFramesStartLabel = new Label(g, SWT.NONE);
			bgFramesStartLabel.setText("First");
			bgFramesStart = new Text(g, SWT.BORDER);
			bgFramesStart.setToolTipText("First frame to select from the background data");
			bgFramesStart.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			bgFramesStart.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					Integer bgStartInt = getBgFirstFrame();
					Integer bgStopInt = getBgLastFrame();
					SliceInput tmpBkgSlice = new SliceInput(bgStartInt, bgStopInt);
					ncdBkgSliceSourceProvider.setBkgSlice(tmpBkgSlice);
				}
			});
			
			bgFramesStopLabel = new Label(g, SWT.NONE);
			bgFramesStopLabel.setText("Last");
			bgFramesStop = new Text(g, SWT.BORDER);
			bgFramesStop.setToolTipText("Last frame to select from the background data");
			bgFramesStop.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			bgFramesStop.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
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
			bgAdvanced.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					SliceInput tmpBkgSlice = new SliceInput(getBgAdvancedSelection());
					ncdBkgSliceSourceProvider.setBkgSlice(tmpBkgSlice);
				}
			});
			
			bgAdvancedButton = new Button(g, SWT.CHECK);
			bgAdvancedButton.setText("Advanced");
			bgAdvancedButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
			bgAdvancedButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean sel = bgAdvancedButton.getSelection() && bgButton.getSelection();
					boolean bgSel = bgButton.getSelection();
					bgAdvanced.setEnabled(sel);
					bgFramesStart.setEnabled(!sel && bgSel);
					bgFramesStop.setEnabled(!sel && bgSel);
					bgFramesStartLabel.setEnabled(!sel && bgSel);
					bgFramesStopLabel.setEnabled(!sel && bgSel);
					if (bgSel) {
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
				}
			});
			bgAdvancedButton.setSelection(false);
		}

		g = new Group(c, SWT.NONE);
		g.setLayout(new GridLayout(4, false));
		g.setText("Data frame selection");
		g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		{
			detFramesStartLabel = new Label(g, SWT.NONE);
			detFramesStartLabel.setText("First");
			detFramesStart = new Text(g, SWT.BORDER);
			detFramesStart.setToolTipText("First frame to select from the data file ");
			detFramesStart.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			detFramesStart.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					Integer detStartInt = getDetFirstFrame();
					Integer detStopInt = getDetLastFrame();
					SliceInput tmpDetSlice = new SliceInput(detStartInt, detStopInt);
					ncdDataSliceSourceProvider.setDataSlice(tmpDetSlice);
				}
			});
			
			detFramesStopLabel = new Label(g, SWT.NONE);
			detFramesStopLabel.setText("Last");
			detFramesStop = new Text(g, SWT.BORDER);
			detFramesStop.setToolTipText("Last frame to select from the data file ");
			detFramesStop.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			detFramesStop.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					Integer detStartInt = getDetFirstFrame();
					Integer detStopInt = getDetLastFrame();
					SliceInput tmpDetSlice = new SliceInput(detStartInt, detStopInt);
					ncdDataSliceSourceProvider.setDataSlice(tmpDetSlice);
				}
			});
			
			
			detAdvanced = new Text(g, SWT.BORDER);
			detAdvanced.setToolTipText("Formatting string for advanced data selection");
			detAdvanced.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false, 3, 1));
			detAdvanced.setEnabled(false);
			detAdvancedButton = new Button(g, SWT.CHECK);
			detAdvancedButton.setText("Advanced");
			detAdvancedButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
			detAdvancedButton.setSelection(false);
			detAdvancedButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean sel = detAdvancedButton.getSelection();
					detAdvanced.setEnabled(sel);
					detFramesStart.setEnabled(!sel);
					detFramesStop.setEnabled(!sel);
					detFramesStartLabel.setEnabled(!sel);
					detFramesStopLabel.setEnabled(!sel);
					SliceInput tmpDetSlice;
					if (sel) {
						tmpDetSlice = new SliceInput(getDetAdvancedSelection());
					} else {
						Integer detStartInt = getDetFirstFrame();
						Integer detStopInt = getDetLastFrame();
						tmpDetSlice = new SliceInput(detStartInt, detStopInt);
					}
					ncdDataSliceSourceProvider.setDataSlice(tmpDetSlice);
				}
			});
		}
		
		g = new Group(c, SWT.NONE);
		g.setLayout(new GridLayout(1, false));
		g.setText("Grid data averaging");
		g.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, true));
		{
			gridAverageButton = new Button(g, SWT.CHECK);
			gridAverageButton.setText("Average dimensions");
			gridAverageButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
			gridAverageButton.setSelection(false);
			gridAverageButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean sel = gridAverageButton.getSelection();
					boolean aveSel = aveButton.getSelection();
					gridAverage.setEnabled(sel && aveSel);
				}
			});
			gridAverage = new Text(g, SWT.BORDER);
			gridAverage.setToolTipText("Comma-separated list of grid dimensions to average");
			gridAverage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
			gridAverage.setEnabled(false);
			gridAverage.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdGridAverageSourceProvider.setGrigAverage(new SliceInput(getGridAverageSelection()));
				}
			});
		}
		
		g = new Group(c, SWT.NONE);
		g.setLayout(new GridLayout(4, false));
		g.setText("Experiment Parameters");
		g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1));
		{
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
					boolean sel = (dimWaxs[0].getSelection() || !(detTypeWaxs.getSelection())) &&
									(dimSaxs[0].getSelection() || !(detTypeSaxs.getSelection()));
					updateSectorIntegrationInvariant(sel);
					
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
						boolean sel = (dimWaxs[0].getSelection() || !(detTypeWaxs.getSelection())) &&
										(dimSaxs[0].getSelection() || !(detTypeSaxs.getSelection()));
						updateSectorIntegrationInvariant(sel);
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
					boolean sel = (dimWaxs[0].getSelection() || !(detTypeWaxs.getSelection())) &&
									(dimSaxs[0].getSelection() || !(detTypeSaxs.getSelection()));
					updateSectorIntegrationInvariant(sel);
					
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
						boolean sel = (dimWaxs[0].getSelection() || !(detTypeWaxs.getSelection())) &&
										(dimSaxs[0].getSelection() || !(detTypeSaxs.getSelection()));
						updateSectorIntegrationInvariant(sel);
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
			
			boolean sel = (dimWaxs[0].getSelection() || !(detTypeWaxs.getSelection())) &&
							(dimSaxs[0].getSelection() || !(detTypeSaxs.getSelection()));
			updateSectorIntegrationInvariant(sel);

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
			
			new Label(g, SWT.NONE).setText("Energy (keV)");
			energy = new Text(g, SWT.BORDER);
			energy.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			energy.setToolTipText("Set the energy used in data collection");
			energy.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdEnergySourceProvider.setEnergy(getEnergy());
				}
			});
			
			useMask = new Button(g, SWT.CHECK);
			useMask.setText("Apply detector mask");
			useMask.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			useMask.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdMaskSourceProvider.setEnableMask(useMask.getSelection());
				}
			});
		}

		g = new Group(c, SWT.NONE);
		g.setLayout(new GridLayout(3, false));
		g.setText("Results directory");
		g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		{
			new Label(g, SWT.NONE).setText("Directory:");
			location = new Text(g, SWT.BORDER);
			location.setText(inputDirectory);
			location.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			location.setToolTipText("Location of NCD data reduction results directory");
			location.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetDefaultSelected(SelectionEvent e) {
					File dir = new File(location.getText());
					if (dir.exists()) {
						inputDirectory = dir.getPath();
					} else {
						location.setText(inputDirectory);
					}
					ncdWorkingDirSourceProvider.setWorkingDir(inputDirectory);
				}
			});

			browse = new Button(g, SWT.NONE);
			browse.setText("...");
			browse.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					DirectoryDialog dChooser = new DirectoryDialog(getViewSite().getShell());
					dChooser.setText("Select working directory for NCD data reduction");
					dChooser.setFilterPath(inputDirectory);
					final File dir = new File(dChooser.open());
					if (dir.exists()) {
						inputDirectory = dir.toString();
						location.setText(inputDirectory);
					}
				}
			});

		}

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
		
		ncdNormalisationSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.NORMALISATION_STATE);
		ncdBackgroundSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BACKGROUD_STATE);
		ncdResponseSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RESPONSE_STATE);
		ncdSectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SECTOR_STATE);
		ncdInvariantSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.INVARIANT_STATE);
		ncdAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AVERAGE_STATE);
		
		ncdWaxsSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXS_STATE);
		ncdSaxsSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXS_STATE);
		ncdScalerSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SCALER_STATE);
		
		ncdWaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXSDETECTOR_STATE);
		ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		
		ncdRadialSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RADIAL_STATE);
		ncdAzimuthSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AZIMUTH_STATE);
		ncdFastIntSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.FASTINT_STATE);
		
		ncdEnergySourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ENERGY_STATE);
		ncdNormChannelSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.NORMCHANNEL_STATE);
		
		ncdMaskSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.MASK_STATE);
		
		ncdDataSliceSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.DATASLICE_STATE);
		ncdBkgSliceSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGSLICE_STATE);
		ncdGridAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.GRIDAVERAGE_STATE);
		
		ncdBgFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGFILE_STATE);
		ncdDrFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.DRFILE_STATE);
		ncdWorkingDirSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WORKINGDIR_STATE);
		
		ncdAbsScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSSCALING_STATE);
		ncdBgScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGSCALING_STATE);
		
		ncdQGradientSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.QGRADIENT_STATE);
		ncdQInterceptSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.QINTERCEPT_STATE);
		ncdQUnitSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.QUNIT_STATE);
		
		ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);

		ncdDetectorSourceProvider.addSourceProviderListener(this);
		ncdSaxsDetectorSourceProvider.addSourceProviderListener(this);
		ncdWaxsDetectorSourceProvider.addSourceProviderListener(this);
		ncdScalerSourceProvider.addSourceProviderListener(this);
		ncdNormalisationSourceProvider.addSourceProviderListener(this);
		ncdBackgroundSourceProvider.addSourceProviderListener(this);
		ncdDetectorSourceProvider.addSourceProviderListener(this);
		ncdSectorSourceProvider.addSourceProviderListener(this);
		ncdAverageSourceProvider.addSourceProviderListener(this);
}

	@Override
	public void setFocus() {

	}

	private void updateNormalisationWidgets(boolean selection) {
		normButton.setSelection(selection);
		calList.setEnabled(selection);
		normChan.setEnabled(selection);
		calListLabel.setEnabled(selection);
		normChanLabel.setEnabled(selection);
		absScale.setEnabled(selection);
		absScaleLabel.setEnabled(selection);
	}

	private void updateSectorIntegrationWidgets(boolean selection) {
		secButton.setSelection(selection);
		radialButton.setEnabled(selection);
		azimuthalButton.setEnabled(selection);
		fastIntButton.setEnabled(selection);
	}

	private void updateBackgroundSubtractionWidgets(boolean selection) {
		bgButton.setSelection(selection);
		bgLabel.setEnabled(selection);
		bgFile.setEnabled(selection);
		browseBg.setEnabled(selection);
		bgScaleLabel.setEnabled(selection);
		bgScale.setEnabled(selection);
		
		bgFramesStart.setEnabled(selection);
		bgFramesStop.setEnabled(selection);
		bgFramesStartLabel.setEnabled(selection);
		bgFramesStopLabel.setEnabled(selection);
		bgAdvanced.setEnabled(selection);
		bgAdvancedButton.setEnabled(selection);
		bgAdvancedButton.notifyListeners(SWT.Selection, null);
		
	}
	
	private void updateDetectorResponseWidgets(boolean selection) {
		drButton.setSelection(selection);
		drLabel.setEnabled(selection);
		drFile.setEnabled(selection);
		browseDr.setEnabled(selection);
		
	}
	
	private void updateSectorIntegrationInvariant(boolean selection) {
		if (selection) {
			if (secButton.getSelection()) {
				secButton.setSelection(false);
			    secButton.notifyListeners(SWT.Selection, new Event());
			}
			if (invButton.getSelection()) {
				invButton.setSelection(false);
				invButton.notifyListeners(SWT.Selection, new Event());
			}
			secButton.setEnabled(false);
			invButton.setEnabled(false);
		} else {
			secButton.setEnabled(true);
			invButton.setEnabled(true);
		}
	}
	
	private void updateAverageWidgets(boolean selection) {
		aveButton.setSelection(selection);
		gridAverage.setEnabled(selection);
		gridAverageButton.setEnabled(selection);
		gridAverageButton.notifyListeners(SWT.Selection, null);
	}
	
	@Override
	public void sourceChanged(int sourcePriority, Map sourceValuesByName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, Object sourceValue) {
		if (sourceName.equals(NcdCalibrationSourceProvider.NCDDETECTORS_STATE)) {
			calList.removeAll();
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
						
						if (detSettings.getType().equals(DetectorTypes.CALIBRATION_DETECTOR)) {
							calList.add(detSettings.getName());
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
				ncdSaxsDetectorSourceProvider.setSaxsDetector(detListWaxs.getItem(0));
			}
			
			if (calList.getItemCount() > 0) {
				calList.select(0);
				ncdScalerSourceProvider.setScaler(calList.getItem(0));
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
		
		if (sourceName.equals(NcdProcessingSourceProvider.SCALER_STATE)) {
			if (sourceValue instanceof String) {
				NcdDetectorSettings detSettings = ncdDetectorSourceProvider.getNcdDetectors().get(sourceValue);
				if (detSettings != null) {
					int max = detSettings.getMaxChannel();
					normChan.setMaximum(max);
				}
				Integer normChannel = ncdNormChannelSourceProvider.getNormChannel();
				if (normChannel != null)
					normChan.setSelection(normChannel);
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.NORMALISATION_STATE)) {
			boolean isEnableNormalisation = ncdNormalisationSourceProvider.isEnableNormalisation();
			updateNormalisationWidgets(isEnableNormalisation);
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.BACKGROUD_STATE)) {
			boolean isEnableBackground = ncdBackgroundSourceProvider.isEnableBackground();
			updateBackgroundSubtractionWidgets(isEnableBackground);
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.RESPONSE_STATE)) {
			boolean isEnableDetectorResponse = ncdBackgroundSourceProvider.isEnableDetectorResponse();
			updateDetectorResponseWidgets(isEnableDetectorResponse);
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.SECTOR_STATE)) {
			boolean isEnableSector = ncdSectorSourceProvider.isEnableSector();
			updateSectorIntegrationWidgets(isEnableSector);
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.AVERAGE_STATE)) {
			boolean isEnableAverage = ncdAverageSourceProvider.isEnableAverage();
			updateAverageWidgets(isEnableAverage);
		}
	}
}
