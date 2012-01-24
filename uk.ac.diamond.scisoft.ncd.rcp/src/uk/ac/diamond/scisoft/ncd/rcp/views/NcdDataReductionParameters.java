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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

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
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.services.ISourceProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.ncd.preferences.NcdConstants;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.handlers.AverageHandler;
import uk.ac.diamond.scisoft.ncd.rcp.handlers.BackgroundSubtractionHandler;
import uk.ac.diamond.scisoft.ncd.rcp.handlers.DetectorMaskHandler;
import uk.ac.diamond.scisoft.ncd.rcp.handlers.DetectorResponseHandler;
import uk.ac.diamond.scisoft.ncd.rcp.handlers.InvariantHandler;
import uk.ac.diamond.scisoft.ncd.rcp.handlers.NormalisationHandler;
import uk.ac.diamond.scisoft.ncd.rcp.handlers.SaxsDataReductionHandler;
import uk.ac.diamond.scisoft.ncd.rcp.handlers.SectorIntegrationHandler;
import uk.ac.diamond.scisoft.ncd.rcp.handlers.WaxsDataReductionHandler;

public class NcdDataReductionParameters extends ViewPart {

	private static final Logger logger = LoggerFactory.getLogger(NcdDataReductionParameters.class);
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters"; //$NON-NLS-1$

	private IMemento memento;

	private static Spinner normChan;
	private static Text bgFramesStart, bgFramesStop, detFramesStart, detFramesStop, bgAdvanced, detAdvanced, gridAverage;
	private static Text bgFile, drFile, bgScale, absScale;

	private static Text location, energy, pxWaxs, pxSaxs, qGradient, qIntercept;
	
	private static Button[] dimWaxs, dimSaxs;
	protected static HashMap<String, Button> unitSel;

	private static Button browse;
	private static String inputDirectory = "/tmp";
	private static Button bgButton, drButton, normButton, secButton, invButton, aveButton, browseBg, browseDr;
	//private static Button bgFramesButton, detFramesButton;
	private NcdProcessingSourceProvider ncdNormalisationSourceProvider;
	private NcdProcessingSourceProvider ncdBackgroundSourceProvider;
	private NcdProcessingSourceProvider ncdResponseSourceProvider;
	private NcdProcessingSourceProvider ncdSectorSourceProvider;
	private NcdProcessingSourceProvider ncdInvariantSourceProvider;
	private NcdProcessingSourceProvider ncdAverageSourceProvider;
	private NcdProcessingSourceProvider ncdWaxsSourceProvider;
	private NcdProcessingSourceProvider ncdSaxsSourceProvider;
	private NcdProcessingSourceProvider ncdMaskSourceProvider;
	private static Button detTypeWaxs, detTypeSaxs, useMask, bgAdvancedButton, detAdvancedButton, gridAverageButton, inputQAxis;
	private static Combo detListWaxs, detListSaxs, calList;
	private static HashMap<String, Double> pixels;
	private static HashMap<String, Integer> detDims;
	private static HashMap<String, Integer> maxChannel;
	private static Label calListLabel, normChanLabel, bgLabel, bgScaleLabel, absScaleLabel, drLabel, pxSaxsLabel, pxWaxsLabel;
	private static Label bgFramesStartLabel, bgFramesStopLabel, detFramesStartLabel, detFramesStopLabel, qGradientLabel, qInterceptLabel;

	public NcdDataReductionParameters() {
	}

	public static Spinner getNormChan() {
		return normChan;
	}

	public static String getBgFile() {
		return bgFile.getText();
	}

	public static void setBgFile(String bgFile) {
		NcdDataReductionParameters.bgFile.setText(bgFile);
	}

	public static String getDrFile() {
		return drFile.getText();
	}

	public static void setDrFile(String drFile) {
		NcdDataReductionParameters.drFile.setText(drFile);
	}

	public static String getWorkingDirectory() {
		return location.getText();
	}

	public static Combo getDetListWaxs() {
		return detListWaxs;
	}

	public static Combo getDetListSaxs() {
		return detListSaxs;
	}

	public static Combo getCalList() {
		return calList;
	}

	public static Double getEnergy() {
		return Double.valueOf(energy.getText());
	}

	public static Double getAbsScale() {
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
	
	public static Double getBgScale() {
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
	
	public static Integer getBgFirstFrame() {
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
	
	public static Integer getBgLastFrame() {
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
	
	public static String getBgAdvancedSelection() {
		if (bgAdvanced.isEnabled())
			return bgAdvanced.getText();
		return null;
	}
	
	public static Integer getDetFirstFrame() {
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
	
	public static Integer getDetLastFrame() {
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
	
	public static String getDetAdvancedSelection() {
		if (detAdvanced.isEnabled())
			return detAdvanced.getText();
		return null;
	}
	
	public static String getGridAverageSelection() {
		if (gridAverage.isEnabled())
			return gridAverage.getText();
		return null;
	}
	
	public static Double getSaxsPixel(boolean scale) {
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
	
	public static Double getWaxsPixel(boolean scale) {
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
	
	public static Double getQGradient() {
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
	
	public static Double getQIntercept() {
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
	
	public static String getQUnit() {
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
			
			memento.putString(NcdPreferences.NCD_BACKGROUNDSUBTRACTION, bgFile.getText());
			memento.putString(NcdPreferences.NCD_DETECTORRESPONSE, drFile.getText());
			
			memento.putString(NcdPreferences.NCD_ABSOLUTESCALE, absScale.getText());
			memento.putString(NcdPreferences.NCD_BACKGROUNDSCALE, bgScale.getText());
			
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
			
			for (String tmpDet: detListWaxs.getItems()) {
				IMemento detMemento = memento.createChild(NcdPreferences.NCD_WAXS_DETECTOR, tmpDet);
				if (pixels.containsKey(tmpDet))
					detMemento.putFloat(NcdPreferences.NCD_PIXEL, pixels.get(tmpDet).floatValue());
				if (detDims.containsKey(tmpDet))
					detMemento.putInteger(NcdPreferences.NCD_DIM, detDims.get(tmpDet).intValue());
			}
			for (String tmpDet: detListSaxs.getItems()) {
				IMemento detMemento = memento.createChild(NcdPreferences.NCD_SAXS_DETECTOR, tmpDet);
				if (pixels.containsKey(tmpDet))
					detMemento.putFloat(NcdPreferences.NCD_PIXEL, pixels.get(tmpDet).floatValue());
				if (detDims.containsKey(tmpDet))
					detMemento.putInteger(NcdPreferences.NCD_DIM, detDims.get(tmpDet).intValue());
			}
			for (String tmpDet: calList.getItems()) {
				IMemento detMemento = memento.createChild(NcdPreferences.NCD_NORM_DETECTOR, tmpDet);
				detMemento.putInteger(NcdPreferences.NCD_MAXCHANNEL, maxChannel.get(tmpDet).intValue());
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
			String tmp;
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_NORMALISATION);
			if (val!=null) {
				normButton.setSelection(val);
				if (val.booleanValue()) normButton.notifyListeners(SWT.Selection, null);
				else updateNormalisationWidgets();
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_BACKGROUND);
			if (val!=null) {
				bgButton.setSelection(val);
				if (val.booleanValue()) bgButton.notifyListeners(SWT.Selection, null);
				else updateBackgroundSubtractionWidgets();
				
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_RESPONSE);
			if (val!=null) {
				drButton.setSelection(val);
				if (val.booleanValue()) drButton.notifyListeners(SWT.Selection, null);
				else updateDetectorResponseWidgets();
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_SECTOR);
			if (val!=null) {
				secButton.setSelection(val);
				if (val.booleanValue()) secButton.notifyListeners(SWT.Selection, null);
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_INVARIANT);
			if (val!=null) {
				invButton.setSelection(val);
				if (val.booleanValue()) invButton.notifyListeners(SWT.Selection, null);
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_AVERAGE);
			if (val!=null) {
				aveButton.setSelection(val);
				if (val.booleanValue()) aveButton.notifyListeners(SWT.Selection, null);
				else updateAverageWidgets();
			}
			
			IMemento[] waxsMemento = memento.getChildren(NcdPreferences.NCD_WAXS_DETECTOR);
			if (waxsMemento != null) {
				detListWaxs.removeAll(); 
				for (IMemento det: waxsMemento) {
					detListWaxs.add(det.getID());
					
					Float px = det.getFloat(NcdPreferences.NCD_PIXEL);
					if (px != null)
						pixels.put(det.getID(), px.doubleValue());
					
					Integer dim = det.getInteger(NcdPreferences.NCD_DIM);
					if (dim != null)
						detDims.put(det.getID(), dim.intValue());
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
					detListSaxs.add(det.getID());
					
					Float px = det.getFloat(NcdPreferences.NCD_PIXEL);
					if (px != null)
						pixels.put(det.getID(), px.doubleValue());
					
					Integer dim = det.getInteger(NcdPreferences.NCD_DIM);
					if (dim != null)
						detDims.put(det.getID(), dim.intValue());
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
					calList.add(det.getID());
					maxChannel.put(det.getID(), det.getInteger(NcdPreferences.NCD_MAXCHANNEL));
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
			}
			tmp = memento.getString(NcdPreferences.NCD_ABSOLUTESCALE);
			if (tmp != null) absScale.setText(tmp);
			
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
			if (tmp != null) gridAverage.setText(tmp);
			val = memento.getBoolean(NcdPreferences.NCD_GRIDAVERAGE);
			if (val!=null) {
				gridAverageButton.setSelection(val);
				if (val.booleanValue()) gridAverageButton.notifyListeners(SWT.Selection, null);
			}
			
			tmp = memento.getString(NcdPreferences.NCD_QGRADIENT);
			if (tmp != null) qGradient.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_QINTERCEPT);
			if (tmp != null) qIntercept.setText(tmp);
			tmp = this.memento.getString(NcdPreferences.NCD_QUNIT);
			if (tmp == null) tmp = NcdConstants.DEFAULT_UNIT;
			
			for (Entry<String, Button> unitBtn : unitSel.entrySet())
				if (unitBtn.getKey().equals(tmp)) unitBtn.getValue().setSelection(true);
				else unitBtn.getValue().setSelection(false);
			
			val = memento.getBoolean(NcdPreferences.NCD_QOVERRIDE);
			if (val!=null) {
				inputQAxis.setSelection(val);
				inputQAxis.notifyListeners(SWT.Selection, null);
			}
			
			tmp = memento.getString(NcdPreferences.NCD_BACKGROUNDSUBTRACTION);
			if (tmp != null) bgFile.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_BACKGROUNDSCALE);
			if (tmp != null) bgScale.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_DETECTORRESPONSE);
			if (tmp != null) drFile.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_ENERGY);
			if (tmp != null) energy.setText(tmp);
			tmp = memento.getString(NcdPreferences.NCD_DIRECTORY);
			if (tmp != null) {
				location.setText(tmp);
				inputDirectory = tmp;
			}
		}
	}
	
	@Override
	public void createPartControl(final Composite parent) {
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
		ncdMaskSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.MASK_STATE);
		
		pixels = new HashMap<String, Double>();
		detDims = new HashMap<String, Integer>();
		maxChannel = new HashMap<String, Integer>();
		
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
					updateNormalisationWidgets();
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);
					try {
						handlerService.executeCommand(NormalisationHandler.COMMAND_ID, null);
						ncdNormalisationSourceProvider.setEnableNormalisation(normButton.getSelection());
					} catch (Exception err) {
						logger.error("Cannot set normalisation step", err);
					}
				}

			});
			if (NcdProcessingSourceProvider.isEnableNormalisation()) normButton.setSelection(true);
			else normButton.setSelection(false);		

			bgButton = new Button(g, SWT.CHECK);
			bgButton.setText("2. Background subtraction");
			bgButton.setToolTipText("Enable background subtraction step");
			bgButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);
					try {
						updateBackgroundSubtractionWidgets();
						handlerService.executeCommand(BackgroundSubtractionHandler.COMMAND_ID, null);
						ncdBackgroundSourceProvider.setEnableBackground(bgButton.getSelection());
						
					} catch (Exception err) {
						logger.error("Cannot set background subtraction step", err);
					}
				}
				
			});
			if (NcdProcessingSourceProvider.isEnableBackground()) bgButton.setSelection(true);
			else bgButton.setSelection(false);


			drButton = new Button(g, SWT.CHECK);
			drButton.setText("3. Detector response");
			drButton.setToolTipText("Enable detector response step");
			drButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);
					try {
						updateDetectorResponseWidgets();
						handlerService.executeCommand(DetectorResponseHandler.COMMAND_ID, null);
						ncdResponseSourceProvider.setEnableDetectorResponse(drButton.getSelection());
					} catch (Exception err) {
						logger.error("Cannot set detector response step", err);
					}
				}

			});
			if (NcdProcessingSourceProvider.isEnableDetectorResponse()) drButton.setSelection(true);
			else drButton.setSelection(false);

			secButton = new Button(g, SWT.CHECK);
			secButton.setText("4. Sector integration");
			secButton.setToolTipText("Enable sector integration step");
			secButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);
					try {
						handlerService.executeCommand(SectorIntegrationHandler.COMMAND_ID, null);
						ncdSectorSourceProvider.setEnableSector(secButton.getSelection());
					} catch (Exception err) {
						logger.error("Cannot set sector integration step", err);
					}
				}
			});
			if (NcdProcessingSourceProvider.isEnableSector()) secButton.setSelection(true);
			else secButton.setSelection(false);

			invButton = new Button(g, SWT.CHECK);
			invButton.setText("5. Invariant");
			invButton.setToolTipText("Enable invariant calculation step");
			invButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);
					try {
						handlerService.executeCommand(InvariantHandler.COMMAND_ID, null);
						ncdInvariantSourceProvider.setEnableInvariant(invButton.getSelection());
					} catch (Exception err) {
						logger.error("Cannot set invariant calculation step", err);
					}
				}
			});
			if (NcdProcessingSourceProvider.isEnableInvariant()) invButton.setSelection(true);
			else invButton.setSelection(false);

			aveButton = new Button(g, SWT.CHECK);
			aveButton.setText("6. Average");
			aveButton.setToolTipText("Enable average calculation step");
			aveButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);
					try {
						updateAverageWidgets();
						handlerService.executeCommand(AverageHandler.COMMAND_ID, null);
						ncdAverageSourceProvider.setEnableAverage(aveButton.getSelection());
					} catch (Exception err) {
						logger.error("Cannot set average calculation step", err);
					}
				}
			});
			if (NcdProcessingSourceProvider.isEnableAverage()) aveButton.setSelection(true);
			else aveButton.setSelection(false);
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
					if (calList.getSelectionIndex() >= 0) {
						normChan.setMinimum(0);
						normChan.setMaximum(maxChannel.get(calList.getItem(calList.getSelectionIndex())));
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
			
			absScaleLabel = new Label(g, SWT.NONE);
			absScaleLabel.setText("Abs. Scale");
			absScaleLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			absScale = new Text(g, SWT.BORDER);
			absScale.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1));
			absScale.setToolTipText("Select absolute scaling factor for calibration data");
			updateNormalisationWidgets();

			bgLabel = new Label(g, SWT.NONE);
			bgLabel.setText("Background Subtraction File");
			bgFile = new Text(g, SWT.BORDER);
			bgFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
			bgFile.setToolTipText("File with the background measurments");

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
			
			drLabel = new Label(g, SWT.NONE);
			drLabel.setText("Detector Response File");
			drFile = new Text(g, SWT.BORDER);
			drFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
			drFile.setToolTipText("File with the detector response frame");

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
		updateDetectorResponseWidgets();

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
			bgFramesStopLabel = new Label(g, SWT.NONE);
			bgFramesStopLabel.setText("Last");
			bgFramesStop = new Text(g, SWT.BORDER);
			bgFramesStop.setToolTipText("Last frame to select from the background data");
			bgFramesStop.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			
			bgAdvanced = new Text(g, SWT.BORDER);
			bgAdvanced.setToolTipText("Formatting string for advanced data selection");
			bgAdvanced.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false, 3, 1));
			bgAdvanced.setEnabled(false);
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

				}
			});
			bgAdvancedButton.setSelection(false);
		}
		updateBackgroundSubtractionWidgets();

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
			detFramesStopLabel = new Label(g, SWT.NONE);
			detFramesStopLabel.setText("Last");
			detFramesStop = new Text(g, SWT.BORDER);
			detFramesStop.setToolTipText("Last frame to select from the data file ");
			detFramesStop.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			
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
					
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);
					try {
						handlerService.executeCommand(WaxsDataReductionHandler.COMMAND_ID, null);
						ncdWaxsSourceProvider.setEnableWaxs(detTypeWaxs.getSelection());
					} catch (Exception err) {
						logger.error("Cannot set WAXS data reduction step", err);
					}
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
						if (pixels.containsKey(det)) {
							Double pxSize = pixels.get(det);
							if (pxSize != null)
								pxWaxs.setText(String.format("%.3f", pxSize.doubleValue()));
						}
						if (detDims.containsKey(det)) {
							Integer dim = detDims.get(det);
							if (dim != null) 
								for (int i = 0; i < dimWaxs.length; i++)
									if (dim > 0 && dim < dimWaxs.length + 1 && i == dim - 1)
										dimWaxs[i].setSelection(true);
									else 
										dimWaxs[i].setSelection(false);
						}
						//try {
						//	IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
						// IViewPart waxsView = page.findView(WaxsQAxisCalibration.ID);
						// if (waxsView != null) ((WaxsQAxisCalibration)waxsView).updateResults(det);
						//} catch (Exception e1) {
						//	logger.warn("Waxs calibration view unavailable", e1);
						//}
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
						int idxWaxs = detListWaxs.getSelectionIndex();
						if (idxWaxs >= 0)
							for (int i = 0; i < dimWaxs.length; i++) {
								if (dimWaxs[i].getSelection()) {
									detDims.put(detListWaxs.getItem(idxWaxs), i + 1);
									break;
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
					int idxWaxs = detListWaxs.getSelectionIndex();
					int idxSaxs = detListSaxs.getSelectionIndex();
					if (idxWaxs >= 0 && detTypeWaxs.isEnabled() && !(pxWaxs.getText().isEmpty())) {
						pixels.put(detListWaxs.getItem(idxWaxs), Double.valueOf(pxWaxs.getText()));
						for (int i = 0; i < dimWaxs.length; i++) {
							if (dimWaxs[i].getSelection()) {
								detDims.put(detListWaxs.getItem(idxWaxs),i + 1);
								break;
							}
						}
					}
					if (idxSaxs >= 0 && detTypeSaxs.isEnabled() && !(pxSaxs.getText().isEmpty())) {
						pixels.put(detListSaxs.getItem(idxSaxs), Double.valueOf(pxSaxs.getText()));
						for (int i = 0; i < dimSaxs.length; i++) {
							if (dimSaxs[i].getSelection()) {
								detDims.put(detListSaxs.getItem(idxSaxs),i + 1);
								break;
							}
						}
					}
				}
			});

			
			if (NcdProcessingSourceProvider.isEnableWaxs()) detTypeWaxs.setSelection(true);
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
					
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);
					try {
						handlerService.executeCommand(SaxsDataReductionHandler.COMMAND_ID, null);
						ncdSaxsSourceProvider.setEnableSaxs(detTypeSaxs.getSelection());
					} catch (Exception err) {
						logger.error("Cannot set SAXS data reduction step", err);
					}
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
						if (pixels.containsKey(det)) {
							Double pxSize = pixels.get(det);
							if (pxSize != null)
								pxSaxs.setText(String.format("%.3f", pxSize));
						}
						if (detDims.containsKey(det)) {
							Integer dim = detDims.get(det);
							if (dim != null)
								for (int i = 0; i < dimSaxs.length; i++)
									if (dim > 0 && dim < dimSaxs.length + 1 && i == dim -1)
										dimSaxs[i].setSelection(true);
									else 
										dimSaxs[i].setSelection(false);
								
							
						}
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
						int idxSaxs = detListSaxs.getSelectionIndex();
						if (idxSaxs >= 0)
							for (int i = 0; i < dimSaxs.length; i++) {
								if (dimSaxs[i].getSelection()) {
									detDims.put(detListSaxs.getItem(idxSaxs), i + 1);
									break;
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
			
			if (NcdProcessingSourceProvider.isEnableSaxs()) detTypeSaxs.setSelection(true);
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
			unitSel.put(NcdConstants.unitChoices[0], tmpUnitSel);
			tmpUnitSel = new Button(unitGrp, SWT.RADIO);
			tmpUnitSel.setText(NcdConstants.unitChoices[1]);
			tmpUnitSel.setToolTipText("calibrate q-axis in nanometers");
			tmpUnitSel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, true));
			unitSel.put(NcdConstants.unitChoices[1], tmpUnitSel);
			qGradientLabel = new Label(gpSelectMode, SWT.NONE);
			qGradientLabel.setText("Gradient");
			qGradientLabel.setLayoutData(new GridData(GridData.END, SWT.CENTER, false, false));
			qGradient = new Text(gpSelectMode, SWT.BORDER);
			qGradient.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			qGradient.setToolTipText("Input q-axis calibration line fit gradient");
			qInterceptLabel = new Label(gpSelectMode, SWT.NONE);
			qInterceptLabel.setText("Intercept");
			qInterceptLabel.setLayoutData(new GridData(GridData.END, SWT.CENTER, true, false));
			qIntercept = new Text(gpSelectMode, SWT.BORDER);
			qIntercept.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false, 2, 1));
			qIntercept.setToolTipText("Input q-axis calibration line fit intercept");
			
			inputQAxis.setSelection(false);
			inputQAxis.notifyListeners(SWT.Selection, null);
			
			new Label(g, SWT.NONE).setText("Energy (keV)");
			energy = new Text(g, SWT.BORDER);
			energy.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			energy.setToolTipText("Set the energy used in data collection");
			
			useMask = new Button(g, SWT.CHECK);
			useMask.setText("Apply detector mask");
			useMask.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			useMask.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);
					try {
						handlerService.executeCommand(DetectorMaskHandler.COMMAND_ID, null);
						ncdMaskSourceProvider.setEnableMask(useMask.getSelection());
					} catch (Exception err) {
						logger.error("Cannot set detector mask", err);
					}
				}

			});
			if (NcdProcessingSourceProvider.isEnableMask()) useMask.setSelection(true);
			else useMask.setSelection(false);		
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

	@Override
	public void setFocus() {

	}

	private static void updateNormalisationWidgets() {
		boolean selection = normButton.getSelection();
		calList.setEnabled(selection);
		normChan.setEnabled(selection);
		calListLabel.setEnabled(selection);
		normChanLabel.setEnabled(selection);
		absScale.setEnabled(selection);
		absScaleLabel.setEnabled(selection);
	}

	private void updateBackgroundSubtractionWidgets() {
		
		boolean selection = bgButton.getSelection();
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
	
	private void updateDetectorResponseWidgets() {
		boolean selection = drButton.getSelection();
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
	
	private void updateAverageWidgets() {
		boolean selection = aveButton.getSelection();
		gridAverage.setEnabled(selection);
		gridAverageButton.setEnabled(selection);
		gridAverageButton.notifyListeners(SWT.Selection, null);
		
	}
	
	public static void setNormalisationDetectors(ArrayList<String> dataList) {
		calList.removeAll();
		if (dataList.size() > 0) {
			calList.setItems(dataList.toArray(new String[dataList.size()]));
		    if (calList.getItemCount() > 0) {
		    	normButton.setEnabled(true);
		    	calList.select(0);
		    }
		    else {
		    	if (normButton.getSelection()) {
			    	normButton.setSelection(false);
				    normButton.notifyListeners(SWT.Selection, new Event());
		    	}
		    	normButton.setEnabled(false);
		    }
		    
	    	updateNormalisationWidgets();
		    calList.notifyListeners(SWT.Selection, new Event());
		}
	}

	public static void setWaxsDetectors(ArrayList<String> dataList) {
		detListWaxs.removeAll();
		pxWaxs.setText("");
		if (dataList.size() > 0) {
			detListWaxs.setItems(dataList.toArray(new String[dataList.size()]));
			detListWaxs.select(0);
			detListWaxs.notifyListeners(SWT.Selection, null);
		}
	}

	public static void setSaxsDetectors(ArrayList<String> dataList) {
		detListSaxs.removeAll();
		pxSaxs.setText("");
		if (dataList.size() > 0) {
			detListSaxs.setItems(dataList.toArray(new String[dataList.size()]));
			detListSaxs.select(0);
			detListSaxs.notifyListeners(SWT.Selection, null);
		}
	}
	
	public static Double getPixelData(String detector) {
		if (pixels.containsKey(detector))
			return pixels.get(detector);
		return null;
	}

	public static void setPixelData(HashMap<String, Double> pixelData) {
		pixels = new HashMap<String, Double>();
		pixels.putAll(pixelData);
	}

	public static Integer getDimData(String detector) {
		if (detDims.containsKey(detector))
			return detDims.get(detector);
		return null;
	}

	public static void setDimData(HashMap<String, Integer> detDimData) {
		detDims = new HashMap<String, Integer>();
		detDims.putAll(detDimData);
		for (int i = 0; i < NcdConstants.dimChoices.length; i++) {
			dimWaxs[i].notifyListeners(SWT.Selection, null);
			dimSaxs[i].notifyListeners(SWT.Selection, null);
		}
	}

	public static void setChannelData(HashMap<String, Integer> channelData) {
		maxChannel = new HashMap<String, Integer>();
		maxChannel.putAll(channelData);
	}
}
