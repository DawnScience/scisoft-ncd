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
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
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
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.services.ISourceProviderService;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.ncd.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.data.SliceInput;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionParameters extends ViewPart implements ISourceProviderListener {

	private static final Logger logger = LoggerFactory.getLogger(NcdDataReductionParameters.class);
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters"; //$NON-NLS-1$

	private IMemento memento;

	private static Spinner normChan;
	private Text bgFramesStart, bgFramesStop, detFramesStart, detFramesStop, bgAdvanced, detAdvanced, gridAverage;
	private Text bgFile, drFile, bgScale, absScale;

	private Text location;
	
	private Button browse;
	private String inputDirectory = "/tmp";
	private Button bgButton, drButton, normButton, secButton, invButton, aveButton, browseBg, browseDr;
	
	private NcdProcessingSourceProvider ncdNormalisationSourceProvider, ncdScalerSourceProvider;
	private NcdProcessingSourceProvider ncdBackgroundSourceProvider;
	private NcdProcessingSourceProvider ncdResponseSourceProvider;
	private NcdProcessingSourceProvider ncdSectorSourceProvider;
	private NcdProcessingSourceProvider ncdInvariantSourceProvider;
	private NcdProcessingSourceProvider ncdAverageSourceProvider;
	private NcdProcessingSourceProvider ncdNormChannelSourceProvider;
	private NcdProcessingSourceProvider ncdMaskSourceProvider;
	private NcdProcessingSourceProvider ncdRadialSourceProvider, ncdAzimuthSourceProvider, ncdFastIntSourceProvider;
	private NcdProcessingSourceProvider ncdDataSliceSourceProvider, ncdBkgSliceSourceProvider, ncdGridAverageSourceProvider;
	private NcdProcessingSourceProvider ncdBgFileSourceProvider, ncdDrFileSourceProvider, ncdWorkingDirSourceProvider;
	private NcdProcessingSourceProvider ncdAbsScaleSourceProvider, ncdBgScaleSourceProvider;
	
	private NcdCalibrationSourceProvider ncdDetectorSourceProvider;
	
	private Button useMask, bgAdvancedButton, detAdvancedButton, gridAverageButton;
	private Button radialButton, azimuthalButton, fastIntButton;
	private static Combo calList;
	private Label calListLabel, normChanLabel, bgLabel, bgScaleLabel, absScaleLabel, drLabel;
	private Label bgFramesStartLabel, bgFramesStopLabel, detFramesStartLabel, detFramesStopLabel;

	private ExpandableComposite secEcomp, refEcomp, bgEcomp, aveEcomp;
	private ExpansionAdapter expansionAdapter;
	
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
		GridLayout gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		
		expansionAdapter = new ExpansionAdapter() {
			@Override
			public void expansionStateChanged(ExpansionEvent e) {
				c.layout();
				sc.notifyListeners(SWT.Resize, null);
			}		
		};
		
		{
			Group g = new Group(c, SWT.BORDER);
			g.setText("Data reduction pipeline");
			g.setLayout(gl);
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
			
			drButton = new Button(g, SWT.CHECK);
			drButton.setText("1. Detector response");
			drButton.setToolTipText("Enable detector response step");
			drButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdResponseSourceProvider.setEnableDetectorResponse(drButton.getSelection());
				}
			});

			secButton = new Button(g, SWT.CHECK);
			secButton.setText("2. Sector integration");
			secButton.setToolTipText("Enable sector integration step");
			secButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdSectorSourceProvider.setEnableSector(secButton.getSelection());
				}
			});

			normButton = new Button(g, SWT.CHECK);
			normButton.setText("3. Normalisation");
			normButton.setToolTipText("Enable normalisation step");
			normButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdNormalisationSourceProvider.setEnableNormalisation(normButton.getSelection());
				}

			});

			bgButton = new Button(g, SWT.CHECK);
			bgButton.setText("4. Background subtraction");
			bgButton.setToolTipText("Enable background subtraction step");
			bgButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdBackgroundSourceProvider.setEnableBackground(bgButton.getSelection());
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

		{
			Group g = new Group(c, SWT.NONE);
			g.setLayout(new GridLayout(3, false));
			g.setText("Results directory");
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
			
			new Label(g, SWT.NONE).setText("Directory:");
			location = new Text(g, SWT.BORDER);
			location.setText(inputDirectory);
			location.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			location.setToolTipText("Location of NCD data reduction results directory");
			location.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					File dir = new File(location.getText());
					if (dir.exists()) {
						inputDirectory = dir.getPath();
						ncdWorkingDirSourceProvider.setWorkingDir(inputDirectory);
					} else {
						ncdWorkingDirSourceProvider.setWorkingDir(null);
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
		
		secEcomp = new ExpandableComposite(c, SWT.NONE);
		secEcomp.setText("Sector Integration Parameters");
		secEcomp.setToolTipText("Select Sector Integration options");
		gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		secEcomp.setLayout(gl);
		secEcomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		secEcomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(secEcomp, SWT.NONE);
			g.setLayout(gl);
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1));
			
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
			
			useMask = new Button(g, SWT.CHECK);
			useMask.setText("Apply detector mask");
			useMask.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			useMask.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdMaskSourceProvider.setEnableMask(useMask.getSelection());
				}
			});
			secEcomp.setClient(g);
		}
		secEcomp.setExpanded(false);
		
		refEcomp = new ExpandableComposite(c, SWT.NONE);
		refEcomp.setText("Reference data");
		refEcomp.setToolTipText("Set options for NCD data reduction");
		gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		refEcomp.setLayout(gl);
		refEcomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		refEcomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(refEcomp, SWT.NONE);
			g.setLayout(new GridLayout(7, false));
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true, 3, 1));
			
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
			absScale.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
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
			bgFile.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					File tmpBgFile = new File(bgFile.getText());
					if (tmpBgFile.exists())
						ncdBgFileSourceProvider.setBgFile(tmpBgFile.getAbsolutePath());
					else
						ncdBgFileSourceProvider.setBgFile(null);
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
			bgScale.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
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
			drFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 5, 1));
			drFile.setToolTipText("File with the detector response frame");
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

			browseDr = new Button(g, SWT.NONE);
			browseDr.setText("...");
			browseDr.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
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
			refEcomp.setClient(g);
		}
		refEcomp.setExpanded(false);

		bgEcomp = new ExpandableComposite(c, SWT.NONE);
		bgEcomp.setText("Background frame selection");
		bgEcomp.setToolTipText("Set background data slicing parameters");
		gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		bgEcomp.setLayout(gl);
		bgEcomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		bgEcomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(bgEcomp, SWT.NONE);
			g.setLayout(new GridLayout(4, false));
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
			
			bgFramesStartLabel = new Label(g, SWT.NONE);
			bgFramesStartLabel.setText("First");
			bgFramesStart = new Text(g, SWT.BORDER);
			bgFramesStart.setToolTipText("First frame to select from the background data");
			bgFramesStart.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			bgFramesStart.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
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
			bgFramesStop.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
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
			bgAdvanced.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
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
			bgEcomp.setClient(g);
		}
		bgEcomp.setExpanded(false);
		
		ExpandableComposite ecomp = new ExpandableComposite(c, SWT.NONE);
		ecomp.setText("Data frame selection");
		ecomp.setToolTipText("Set data slicing parameters");
		gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		ecomp.setLayout(gl);
		ecomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		ecomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(ecomp, SWT.NONE);
			g.setLayout(new GridLayout(4, false));
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
			
			detFramesStartLabel = new Label(g, SWT.NONE);
			detFramesStartLabel.setText("First");
			detFramesStart = new Text(g, SWT.BORDER);
			detFramesStart.setToolTipText("First frame to select from the data file ");
			detFramesStart.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			detFramesStart.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
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
			detFramesStop.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
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
			detAdvanced.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					SliceInput tmpDetSlice = new SliceInput(getDetAdvancedSelection());
					ncdDataSliceSourceProvider.setBkgSlice(tmpDetSlice);
				}
			});
			
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
			ecomp.setClient(g);
		}
		ecomp.setExpanded(false);
		
		aveEcomp = new ExpandableComposite(c, SWT.NONE);
		aveEcomp.setText("Grid data averaging");
		aveEcomp.setToolTipText("Specify dimensions for averaging");
		gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		aveEcomp.setLayout(gl);
		aveEcomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		aveEcomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(aveEcomp, SWT.NONE);
			g.setLayout(new GridLayout(2, false));
			g.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, true));
			
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
					ncdGridAverageSourceProvider.setGrigAverage(new SliceInput(getGridAverageSelection()));
				}
			});
			gridAverage = new Text(g, SWT.BORDER);
			gridAverage.setToolTipText("Comma-separated list of grid dimensions to average");
			gridAverage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			gridAverage.setEnabled(false);
			gridAverage.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					ncdGridAverageSourceProvider.setGrigAverage(new SliceInput(getGridAverageSelection()));
				}
			});
			aveEcomp.setClient(g);
		}
		aveEcomp.setExpanded(false);

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
		
		ncdScalerSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SCALER_STATE);
		
		ncdRadialSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RADIAL_STATE);
		ncdAzimuthSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AZIMUTH_STATE);
		ncdFastIntSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.FASTINT_STATE);
		
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
		
		ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);

		ncdDetectorSourceProvider.addSourceProviderListener(this);
		ncdScalerSourceProvider.addSourceProviderListener(this);
		ncdNormalisationSourceProvider.addSourceProviderListener(this);
		ncdBackgroundSourceProvider.addSourceProviderListener(this);
		ncdResponseSourceProvider.addSourceProviderListener(this);
		ncdSectorSourceProvider.addSourceProviderListener(this);
		ncdAverageSourceProvider.addSourceProviderListener(this);
		ncdBgFileSourceProvider.addSourceProviderListener(this);
		ncdDrFileSourceProvider.addSourceProviderListener(this);
	}

	@Override
	public void setFocus() {

	}

	private void updateNormalisationWidgets(boolean selection) {
		if (refEcomp != null && !(refEcomp.isDisposed())) {
			if (!selection && !(drButton.getSelection()) && !(bgButton.getSelection())) {
				refEcomp.setExpanded(selection);
				refEcomp.setEnabled(selection);
			} else {
				refEcomp.setExpanded(true);
				refEcomp.setEnabled(true);
			}
			expansionAdapter.expansionStateChanged(new ExpansionEvent(aveEcomp, selection));
		}
		if (normButton != null && !(normButton.isDisposed()))
			normButton.setSelection(selection);
		if (calList != null && !(calList.isDisposed()))
			calList.setEnabled(selection);
		if (normChan != null && !(normChan.isDisposed()))
			normChan.setEnabled(selection);
		if (calListLabel != null && !(calListLabel.isDisposed()))
			calListLabel.setEnabled(selection);
		if (normChanLabel != null && !(normChanLabel.isDisposed()))
			normChanLabel.setEnabled(selection);
		if (absScale != null && !(absScale.isDisposed()))
			absScale.setEnabled(selection);
		if (absScaleLabel != null && !(absScaleLabel.isDisposed()))
			absScaleLabel.setEnabled(selection);
	}

	private void updateSectorIntegrationWidgets(boolean selection) {
		if (secEcomp != null && !(secEcomp.isDisposed())) {
			secEcomp.setExpanded(selection);
			secEcomp.setEnabled(selection);
			expansionAdapter.expansionStateChanged(new ExpansionEvent(secEcomp, selection));
		}
		if (secButton != null && !(secButton.isDisposed()))
			secButton.setSelection(selection);
		if (radialButton != null && !(radialButton.isDisposed()))
			radialButton.setEnabled(selection);
		if (azimuthalButton != null && !(azimuthalButton.isDisposed()))
			azimuthalButton.setEnabled(selection);
		if (fastIntButton != null && !(fastIntButton.isDisposed()))
			fastIntButton.setEnabled(selection);
		if (useMask != null && !(useMask.isDisposed()))
			useMask.setEnabled(selection);
	}

	private void updateBackgroundSubtractionWidgets(boolean selection) {
		if (refEcomp != null && !(refEcomp.isDisposed())) {
			if (!selection && !(drButton.getSelection()) && !(normButton.getSelection())) {
				refEcomp.setExpanded(selection);
				refEcomp.setEnabled(selection);
			} else {
				refEcomp.setExpanded(true);
				refEcomp.setEnabled(true);
			}
			expansionAdapter.expansionStateChanged(new ExpansionEvent(aveEcomp, selection));
		}
		if (bgEcomp != null && !(bgEcomp.isDisposed())) {
			bgEcomp.setEnabled(selection);
			if (!selection)
				bgEcomp.setExpanded(selection);
			expansionAdapter.expansionStateChanged(new ExpansionEvent(bgEcomp, selection));
		}
		if (bgButton != null && !(bgButton.isDisposed()))
			bgButton.setSelection(selection);
		if (bgLabel != null && !(bgLabel.isDisposed()))
			bgLabel.setEnabled(selection);
		if (bgFile != null && !(bgFile.isDisposed()))
			bgFile.setEnabled(selection);
		if (browseBg != null && !(browseBg.isDisposed()))
			browseBg.setEnabled(selection);
		if (bgScaleLabel != null && !(bgScaleLabel.isDisposed()))
			bgScaleLabel.setEnabled(selection);
		if (bgScale != null && !(bgScale.isDisposed()))
			bgScale.setEnabled(selection);
		
		if (bgFramesStart != null && !(bgFramesStart.isDisposed()))
			bgFramesStart.setEnabled(selection);
		if (bgFramesStop != null && !(bgFramesStop.isDisposed()))
			bgFramesStop.setEnabled(selection);
		if (bgFramesStartLabel != null && !(bgFramesStartLabel.isDisposed()))
			bgFramesStartLabel.setEnabled(selection);
		if (bgFramesStopLabel != null && !(bgFramesStopLabel.isDisposed()))
			bgFramesStopLabel.setEnabled(selection);
		if (bgAdvanced != null && !(bgAdvanced.isDisposed()))
			bgAdvanced.setEnabled(selection);
		if (bgAdvancedButton != null && !(bgAdvancedButton.isDisposed())) {
			bgAdvancedButton.setEnabled(selection);
			bgAdvancedButton.notifyListeners(SWT.Selection, null);
		}
	}
	
	private void updateDetectorResponseWidgets(boolean selection) {
		if (refEcomp != null && !(refEcomp.isDisposed())) {
			if (!selection && !(bgButton.getSelection()) && !(normButton.getSelection())) {
				refEcomp.setExpanded(selection);
				refEcomp.setEnabled(selection);
			} else {
				refEcomp.setExpanded(true);
				refEcomp.setEnabled(true);
			}
			expansionAdapter.expansionStateChanged(new ExpansionEvent(aveEcomp, selection));
		}
		if (drButton != null && !(drButton.isDisposed()))
			drButton.setSelection(selection);
		if (drLabel != null && !(drLabel.isDisposed()))
			drLabel.setEnabled(selection);
		if (drFile != null && !(drFile.isDisposed()))
			drFile.setEnabled(selection);
		if (browseDr != null && !(browseDr.isDisposed()))
			browseDr.setEnabled(selection);
	}
	
	private void updateAverageWidgets(boolean selection) {
		if (aveEcomp != null && !(aveEcomp.isDisposed())) {
			aveEcomp.setEnabled(selection);
			if (!selection)
				aveEcomp.setExpanded(selection);
			expansionAdapter.expansionStateChanged(new ExpansionEvent(aveEcomp, selection));
		}
		if (aveButton != null && !(aveButton.isDisposed()))
			aveButton.setSelection(selection);
		if (gridAverage != null && !(gridAverage.isDisposed()))
			gridAverage.setEnabled(selection);
		if (gridAverageButton != null && !(gridAverageButton.isDisposed())) {
			gridAverageButton.setEnabled(selection);
			gridAverageButton.notifyListeners(SWT.Selection, null);
		}
	}
	
	@Override
	public void sourceChanged(int sourcePriority, @SuppressWarnings("rawtypes") Map sourceValuesByName) {
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, Object sourceValue) {
		if (sourceName.equals(NcdCalibrationSourceProvider.NCDDETECTORS_STATE)) {
			if (calList != null && !(calList.isDisposed())) {
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
					calList.select(0);
					ncdScalerSourceProvider.setScaler(calList.getItem(0));
				}
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.SCALER_STATE)) {
			if (sourceValue instanceof String) {
				if ((normChan != null) && !(normChan.isDisposed())) {
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
		
		if (sourceName.equals(NcdProcessingSourceProvider.BKGFILE_STATE)) {
			if (bgFile != null  && !(bgFile.isDisposed())) {
				String tmpText = bgFile.getText();
				if (!(tmpText.equals(sourceValue)) && (sourceValue != null))
					bgFile.setText((String) sourceValue);
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.DRFILE_STATE)) {
			if (drFile != null && !(drFile.isDisposed())) {
				String tmpText = drFile.getText();
				if (!(tmpText.equals(sourceValue)) && (sourceValue != null))
					drFile.setText((String) sourceValue);
			}
		}
	}
}
