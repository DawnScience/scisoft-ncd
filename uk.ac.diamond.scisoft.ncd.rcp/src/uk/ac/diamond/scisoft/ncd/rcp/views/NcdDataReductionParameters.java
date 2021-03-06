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
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.math3.util.Pair;
import org.apache.commons.validator.routines.IntegerValidator;
import org.dawb.common.services.ServiceManager;
import org.dawnsci.common.widgets.file.SelectorWidget;
import org.eclipse.dawnsci.analysis.api.persistence.IPersistenceService;
import org.eclipse.dawnsci.analysis.api.persistence.IPersistentFile;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.tool.IToolPage;
import org.eclipse.dawnsci.plotting.api.tool.IToolPageSystem;
import org.eclipse.dawnsci.plotting.api.trace.IImageTrace;
import org.eclipse.dawnsci.plotting.api.trace.ITrace;
import org.eclipse.january.dataset.BooleanDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
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
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.services.ISourceProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.data.SliceInput;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.core.rcp.SaxsPlotsSourceProvider;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;

public class NcdDataReductionParameters extends ViewPart implements ISourceProviderListener {

	private static final Logger logger = LoggerFactory.getLogger(NcdDataReductionParameters.class);

	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters"; //$NON-NLS-1$

	protected static final String PLOT_NAME = "Dataset Plot";

	private IMemento memento;

	private Text bgFramesStart, bgFramesStop, detFramesStart, detFramesStop, bgAdvanced, detAdvanced, gridAverage;
	private Text bgScale, sampleThickness, absScale;

	private Button runDataReduction, runDataReductionWizard;

	private SelectorWidget maskFileSelector;
	private SelectorWidget locationSelector;
	private SelectorWidget drFileSelector;
	private SelectorWidget bgFileSelector;

	private String inputDirectory = "Please specify results directory";
	private Button bgButton, drButton, normButton, secButton, invButton, aveButton;
	private Button loglogButton, guinierButton, porodButton, kratkyButton, zimmButton, debyebuecheButton;
	
	private NcdProcessingSourceProvider ncdNormalisationSourceProvider;
	private NcdProcessingSourceProvider ncdBackgroundSourceProvider;
	private NcdProcessingSourceProvider ncdResponseSourceProvider;
	private NcdProcessingSourceProvider ncdSectorSourceProvider;
	private NcdProcessingSourceProvider ncdInvariantSourceProvider;
	private NcdProcessingSourceProvider ncdAverageSourceProvider;
	private NcdProcessingSourceProvider ncdMaskSourceProvider, ncdMaskFileSourceProvider;
	private NcdProcessingSourceProvider ncdRadialSourceProvider, ncdAzimuthSourceProvider, ncdFastIntSourceProvider;
	private NcdProcessingSourceProvider ncdDataSliceSourceProvider, ncdBkgSliceSourceProvider, ncdGridAverageSourceProvider;
	private NcdProcessingSourceProvider ncdBgFileSourceProvider, ncdDrFileSourceProvider, ncdWorkingDirSourceProvider;
	private NcdProcessingSourceProvider ncdSampleThicknessSourceProvider, ncdAbsScaleSourceProvider, ncdBgScaleSourceProvider;
	private NcdProcessingSourceProvider ncdUseFormSampleThicknessSourceProvider;
	
	private SaxsPlotsSourceProvider loglogPlotSourceProvider;
	private SaxsPlotsSourceProvider guinierPlotSourceProvider;
	private SaxsPlotsSourceProvider porodPlotSourceProvider;
	private SaxsPlotsSourceProvider kratkyPlotSourceProvider;
	private SaxsPlotsSourceProvider zimmPlotSourceProvider;
	private SaxsPlotsSourceProvider debyebuechePlotSourceProvider;
	
	private Button useMask, bgAdvancedButton, detAdvancedButton, gridAverageButton;
	private Button radialButton, azimuthalButton, fastIntButton, useFormSampleThicknessButton;
	private Label bgLabel, bgScaleLabel, sampleThicknessLabel, absScaleLabel, drLabel;
	private Label bgFramesStartLabel, bgFramesStopLabel, detFramesStartLabel, detFramesStopLabel, useFormSampleThicknessLabel;

	private ExpandableComposite ecomp, saxsPlotEcomp, secEcomp, normEcomp, refEcomp, bgEcomp, aveEcomp;
	private ExpansionAdapter expansionAdapter;
	
	private IntegerValidator integerValidator = IntegerValidator.getInstance();

	private IDataset mask;

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
	
	private Double getBgScale() {
		String input = bgScale.getText();
		if (NumberUtils.isNumber(input)) {
			return Double.valueOf(input);
		}
		return null;
	}
	
	private Integer getBgFirstFrame() {
		String input = bgFramesStart.getText();
		if (bgFramesStart.isEnabled()) {
			return integerValidator.validate(input);
		}
		return null;
	}
	
	private Integer getBgLastFrame() {
		String input = bgFramesStop.getText();
		if (bgFramesStop.isEnabled()) {
			return integerValidator.validate(input);
		}
		return null;
	}
	
	private String getBgAdvancedSelection() {
		if (bgAdvanced.isEnabled()) {
			return bgAdvanced.getText();
		}
		return null;
	}
	
	private Integer getDetFirstFrame() {
		String input = detFramesStart.getText();
		if (detFramesStart.isEnabled()) {
			return integerValidator.validate(input);
		}
		return null;
	}
	
	private Integer getDetLastFrame() {
		String input = detFramesStop.getText();
		if (detFramesStop.isEnabled()) {
			return integerValidator.validate(input);
		}
		return null;
	}
	
	private String getDetAdvancedSelection() {
		if (detAdvanced.isEnabled()) {
			return detAdvanced.getText();
		}
		return null;
	}
	
	private String getGridAverageSelection() {
		if (gridAverage.isEnabled()) {
			return gridAverage.getText();
		}
		return null;
	}
	
	private Boolean isUseFileThicknessSelection() {
		if (useFormSampleThicknessButton.isEnabled()) {
			return true;
		}
		return false;
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
			memento.putBoolean(NcdPreferences.NCD_SECTOR_MASK, useMask.getSelection());
			memento.putString(NcdPreferences.NCD_SECTOR_MASKFILE, maskFileSelector.getText());
			
			memento.putBoolean(NcdPreferences.NCD_PLOT_LOGLOG, loglogButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_PLOT_GUINIER, guinierButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_PLOT_POROD, porodButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_PLOT_KRATKY, kratkyButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_PLOT_ZIMM, zimmButton.getSelection());
			memento.putBoolean(NcdPreferences.NCD_PLOT_DEBYEBUECHE, debyebuecheButton.getSelection());
			
			memento.putString(NcdPreferences.NCD_BACKGROUNDSUBTRACTION, bgFileSelector.getText());
			memento.putString(NcdPreferences.NCD_DETECTORRESPONSE, drFileSelector.getText());
			
			Double sampleThicknessVal = getSampleThickness();
			if (sampleThicknessVal != null) {
				memento.putFloat(NcdPreferences.NCD_SAMPLETHICKNESS, sampleThicknessVal.floatValue());
			}
			Double absScaleVal = getAbsScale();
			if (absScaleVal != null) {
				memento.putFloat(NcdPreferences.NCD_ABSOLUTESCALE, absScaleVal.floatValue());
			}
			Double bgScaleVal = getBgScale();
			if (bgScaleVal != null) {
				memento.putFloat(NcdPreferences.NCD_BACKGROUNDSCALE, bgScaleVal.floatValue());
			}
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
			
			memento.putString(NcdPreferences.NCD_DIRECTORY, inputDirectory);
			
			memento.putBoolean(NcdPreferences.NCD_USEFORMSAMPLETHICKNESS, useFormSampleThicknessButton.getSelection());
		}
	}
			
	private void restoreState() {
		if (memento != null) {
			Boolean val;
			Float flt;
			String tmp;
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_NORMALISATION);
			if (val!=null) {
				ncdNormalisationSourceProvider.setEnableNormalisation(val);
			}
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_BACKGROUND);
			if (val!=null) {
				ncdBackgroundSourceProvider.setEnableBackground(val);
			}
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_RESPONSE);
			if (val!=null) {
				ncdResponseSourceProvider.setEnableDetectorResponse(val);
			}
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_SECTOR);
			if (val!=null) {
				ncdSectorSourceProvider.setEnableSector(val);
			}
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_INVARIANT);
			if (val!=null) {
				invButton.setSelection(val);
				if (val.booleanValue()) invButton.notifyListeners(SWT.Selection, null);
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_STAGE_AVERAGE);
			if (val!=null) {
				ncdAverageSourceProvider.setEnableAverage(val);
			}
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
			
			val = memento.getBoolean(NcdPreferences.NCD_SECTOR_MASK);
			if (val!=null) {
				useMask.setSelection(val);
				useMask.notifyListeners(SWT.Selection, null);
			}
			
			tmp = memento.getString(NcdPreferences.NCD_SECTOR_MASKFILE);
			if (tmp!=null) {
				maskFileSelector.setText(tmp);
				ncdMaskFileSourceProvider.setMaskFile(tmp);
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_PLOT_LOGLOG);
			if (val!=null) {
				loglogPlotSourceProvider.setEnableLogLog(val);
			}
			val = memento.getBoolean(NcdPreferences.NCD_PLOT_GUINIER);
			if (val!=null) {
				guinierPlotSourceProvider.setEnableGuinier(val);
			}
			val = memento.getBoolean(NcdPreferences.NCD_PLOT_POROD);
			if (val!=null) {
				porodPlotSourceProvider.setEnablePorod(val);
			}
			val = memento.getBoolean(NcdPreferences.NCD_PLOT_KRATKY);
			if (val!=null) {
				kratkyPlotSourceProvider.setEnableKratky(val);
			}
			val = memento.getBoolean(NcdPreferences.NCD_PLOT_ZIMM);
			if (val!=null) {
				zimmPlotSourceProvider.setEnableZimm(val);
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_PLOT_DEBYEBUECHE);
			if (val!=null) {
				debyebuechePlotSourceProvider.setEnableDebyeBueche(val);
			}
			
			flt = memento.getFloat(NcdPreferences.NCD_ABSOLUTESCALE);
			if (flt != null) {
				absScale.setText(flt.toString());
				ncdAbsScaleSourceProvider.setAbsScaling(new Double(flt), true);
			}
			
			flt = memento.getFloat(NcdPreferences.NCD_SAMPLETHICKNESS);
			if (flt != null) {
				sampleThickness.setText(flt.toString());
				ncdSampleThicknessSourceProvider.setSampleThickness(new Double(flt), true);
			}
			
			tmp = memento.getString(NcdPreferences.NCD_BGFIRSTFRAME);
			if (tmp != null) {
				bgFramesStart.setText(tmp);
			}
			tmp = memento.getString(NcdPreferences.NCD_BGLASTFRAME);
			if (tmp != null) {
				bgFramesStop.setText(tmp);
			}
			tmp = memento.getString(NcdPreferences.NCD_BGFRAMESELECTION);
			if (tmp != null) {
				bgAdvanced.setText(tmp);
			}
			val = memento.getBoolean(NcdPreferences.NCD_BGADVANCED);
			if (val!=null) {
				bgAdvancedButton.setSelection(val);
			}
			bgAdvancedButton.notifyListeners(SWT.Selection, null);
			if (ncdBkgSliceSourceProvider.getBkgSlice() != null) {
				bgEcomp.setExpanded(true);
				expansionAdapter.expansionStateChanged(new ExpansionEvent(bgEcomp, true));
			}
			
			tmp = memento.getString(NcdPreferences.NCD_DATAFIRSTFRAME);
			if (tmp != null) {
				detFramesStart.setText(tmp);
			}
			tmp = memento.getString(NcdPreferences.NCD_DATALASTFRAME);
			if (tmp != null) {
				detFramesStop.setText(tmp);
			}
			tmp = memento.getString(NcdPreferences.NCD_DATAFRAMESELECTION);
			if (tmp != null) {
				detAdvanced.setText(tmp);
			}			
			if (ncdDataSliceSourceProvider.getDataSlice() != null) {
				ecomp.setExpanded(true);
				expansionAdapter.expansionStateChanged(new ExpansionEvent(ecomp, true));
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_DATAADVANCED);
			if (val!=null) {
				detAdvancedButton.setSelection(val);
			}
			detAdvancedButton.notifyListeners(SWT.Selection, null);
			
			tmp = memento.getString(NcdPreferences.NCD_GRIDAVERAGESELECTION);
			if (tmp != null) {
				gridAverage.setText(tmp);
			}
			if (ncdGridAverageSourceProvider.getGridAverage() != null) {
				aveEcomp.setExpanded(true);
				expansionAdapter.expansionStateChanged(new ExpansionEvent(aveEcomp, true));
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_GRIDAVERAGE);
			if (val!=null) {
				gridAverageButton.setSelection(val);
				if (val.booleanValue()) gridAverageButton.notifyListeners(SWT.Selection, null);
			}
			
			tmp = memento.getString(NcdPreferences.NCD_BACKGROUNDSUBTRACTION);
			if (tmp != null) {
				bgFileSelector.setText(tmp);
				ncdBgFileSourceProvider.setBgFile(tmp);
			}
			
			flt = memento.getFloat(NcdPreferences.NCD_BACKGROUNDSCALE);
			if (flt != null) {
				bgScale.setText(flt.toString());
				ncdBgScaleSourceProvider.setBgScaling(new Double(flt), true);
			}
			
			tmp = memento.getString(NcdPreferences.NCD_DETECTORRESPONSE);
			if (tmp != null) {
				drFileSelector.setText(tmp);
				ncdDrFileSourceProvider.setDrFile(tmp);
			}
			
			tmp = memento.getString(NcdPreferences.NCD_DIRECTORY);
			if (tmp != null) {
				locationSelector.setText(tmp);
				inputDirectory = tmp;
				ncdWorkingDirSourceProvider.setWorkingDir(inputDirectory);
			}
			
			val = memento.getBoolean(NcdPreferences.NCD_USEFORMSAMPLETHICKNESS);
			if (val != null) {
				useFormSampleThicknessButton.setSelection(val);
				//ncdSampleThicknessSourceProvider should have a thickness
			}
		}
	}
	
	@Override
	public void createPartControl(final Composite parent) {
		
		ConfigureNcdSourceProviders();
		
		final ScrolledComposite sc = new ScrolledComposite(parent, SWT.H_SCROLL | SWT.V_SCROLL);
		final Composite c = new Composite(sc, SWT.NONE);
		GridLayout grid = new GridLayout(1, false);
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
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			
			drButton = new Button(g, SWT.CHECK);
			drButton.setText("1. Detector response");
			drButton.setToolTipText("Enable detector response step");
			drButton.setSelection(ncdResponseSourceProvider.isEnableDetectorResponse());
			drButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdResponseSourceProvider.setEnableDetectorResponse(drButton.getSelection());
				}
			});

			secButton = new Button(g, SWT.CHECK);
			secButton.setText("2. Sector integration");
			secButton.setToolTipText("Enable sector integration step");
			secButton.setSelection(ncdSectorSourceProvider.isEnableSector());
			secButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdSectorSourceProvider.setEnableSector(secButton.getSelection());
				}
			});

			normButton = new Button(g, SWT.CHECK);
			normButton.setText("3. Normalisation");
			normButton.setToolTipText("Enable normalisation step");
			normButton.setSelection(ncdNormalisationSourceProvider.isEnableNormalisation());
			normButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdNormalisationSourceProvider.setEnableNormalisation(normButton.getSelection());
				}

			});

			bgButton = new Button(g, SWT.CHECK);
			bgButton.setText("4. Background subtraction");
			bgButton.setToolTipText("Enable background subtraction step");
			bgButton.setSelection(ncdBackgroundSourceProvider.isEnableBackground());
			bgButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdBackgroundSourceProvider.setEnableBackground(bgButton.getSelection());
				}
			});


			invButton = new Button(g, SWT.CHECK);
			invButton.setText("5. Invariant");
			invButton.setToolTipText("Enable invariant calculation step");
			invButton.setSelection(ncdInvariantSourceProvider.isEnableInvariant());
			invButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
						ncdInvariantSourceProvider.setEnableInvariant(invButton.getSelection());
				}
			});

			aveButton = new Button(g, SWT.CHECK);
			aveButton.setText("6. Average");
			aveButton.setToolTipText("Enable average calculation step");
			aveButton.setSelection(ncdAverageSourceProvider.isEnableAverage());
			aveButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdAverageSourceProvider.setEnableAverage(aveButton.getSelection());
				}
			});
			
			runDataReduction = new Button(g, SWT.NONE);
			runDataReduction.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, false, true));
			runDataReduction.setText("   RUN   ");
			runDataReduction.setImage(Activator.getImageDescriptor("icons/ncd_icon.png").createImage());
			runDataReduction.setToolTipText("Run NCD data reduction pipeline");
			runDataReduction.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(IHandlerService.class);
					try {
						handlerService.executeCommand("uk.ac.diamond.scisoft.ncd.rcp.process.button", null);
					} catch (Exception ex) {
						throw new RuntimeException(ex);
					}
				}
			});
			
			runDataReductionWizard = new Button(g, SWT.NONE);
			runDataReductionWizard.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, false, true));
			runDataReductionWizard.setText("Start Wizard");
			runDataReductionWizard.setToolTipText("Start NCD data reduction wizard");
			runDataReductionWizard.setImage(Activator.getImageDescriptor("icons/new_wiz.gif").createImage());
			runDataReductionWizard.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getService(IHandlerService.class);
					try {
						handlerService.executeCommand("uk.ac.diamond.scisoft.ncd.rcp.openNcdDataReductionWizardButton", null);
					} catch (Exception ex) {
						throw new RuntimeException(ex);
					}
				}
			});
		}

		{
			Group g = new Group(c, SWT.NONE);
			g.setLayout(new GridLayout(2, false));
			g.setText("Results directory");
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			
			new Label(g, SWT.NONE).setText("Directory:");
			locationSelector = new SelectorWidget(g) {
				@Override
				public void pathChanged(String path, TypedEvent event) {
					File dir = new File(path);
					if (dir.exists() && dir.isDirectory()) {
						inputDirectory = path;
						ncdWorkingDirSourceProvider.setWorkingDir(inputDirectory);
					} else {
						ncdWorkingDirSourceProvider.setWorkingDir(null);
					}
				}
			};
			locationSelector.setLabel("");
			locationSelector.setText(inputDirectory);
			locationSelector.setTextToolTip("Location of NCD data reduction results directory");
			locationSelector.setButtonToolTip("Select working directory for NCD data reduction");
			String tmpWorkigDir = ncdWorkingDirSourceProvider.getWorkingDir();
			if (tmpWorkigDir != null) {
				locationSelector.setText(tmpWorkigDir);
			}
		}
		
		secEcomp = new ExpandableComposite(c, SWT.NONE);
		secEcomp.setText("Sector Integration Parameters");
		secEcomp.setToolTipText("Select Sector Integration options");
		gl = new GridLayout(3, false);
		gl.horizontalSpacing = 15;
		secEcomp.setLayout(gl);
		secEcomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		secEcomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(secEcomp, SWT.NONE);
			g.setLayout(gl);
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			
			radialButton = new Button(g, SWT.CHECK);
			radialButton.setText("Radial Profile");
			radialButton.setToolTipText("Activate radial profile calculation");
			radialButton.setSelection(ncdRadialSourceProvider.isEnableRadial());
			radialButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdRadialSourceProvider.setEnableRadial(radialButton.getSelection());
				}
			});

			azimuthalButton = new Button(g, SWT.CHECK);
			azimuthalButton.setText("Azimuthal Profile");
			azimuthalButton.setToolTipText("Activate azimuthal profile calculation");
			azimuthalButton.setSelection(ncdAzimuthSourceProvider.isEnableAzimuthal());
			azimuthalButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdAzimuthSourceProvider.setEnableAzimuthal(azimuthalButton.getSelection());
				}
			});

			fastIntButton = new Button(g, SWT.CHECK);
			fastIntButton.setText("Fast Integration");
			fastIntButton.setToolTipText("Use fast algorithm for profile calculations");
			fastIntButton.setEnabled(ncdFastIntSourceProvider.isEnableFastIntegration());
			fastIntButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdFastIntSourceProvider.setEnableFastIntegration(fastIntButton.getSelection());
				}

			});
			
			useMask = new Button(g, SWT.CHECK);
			useMask.setText("Apply detector mask");
			useMask.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			useMask.setSelection(ncdMaskSourceProvider.isEnableMask());
			useMask.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdMaskSourceProvider.setEnableMask(useMask.getSelection());
					maskFileSelector.setEnabled(useMask.getSelection());
					IImageTrace trace = getCurrentTrace();
					if (trace != null) {
						if (useMask.getSelection()) {
							trace.setMask(mask);
						} else {
							trace.setMask(null);
						}
					}
				}
			});
			maskFileSelector = new SelectorWidget(g, false, new String[] { "NeXus files",
					"All Files" }, new String[] { "*.nxs", "*.*" }) {
				@Override
				public void pathChanged(String path, TypedEvent event) {
					if (!(event instanceof ModifyEvent)) {
						try {
							createMask(path);
							// set mask
							IImageTrace trace = getCurrentTrace();
							mask = trace != null ? trace.getMask() : null;
						} catch (Exception e) {
							logger.error("Problem importing mask:", e);
						}
					}
				}
			};
			maskFileSelector.getComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
			maskFileSelector.setLabel("");
			maskFileSelector.setTextToolTip("File with the mask information: dropping a file in this field will " +
					"automatically try to load the persisted mask in the file if there is any.");
			maskFileSelector.setButtonToolTip("Select Mask File");
			String tmpMaskFile = ncdMaskFileSourceProvider.getMaskFile();
			if (tmpMaskFile != null)
				maskFileSelector.setText(tmpMaskFile);
			secEcomp.setClient(g);
		}
		secEcomp.setExpanded(false);
		
		saxsPlotEcomp = new ExpandableComposite(c, SWT.NONE);
		saxsPlotEcomp.setText("1D SAXS Analysis Data");
		saxsPlotEcomp.setToolTipText("Include in results files data for making 1D SAXS analysis plots");
		gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		saxsPlotEcomp.setLayout(gl);
		saxsPlotEcomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		saxsPlotEcomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(saxsPlotEcomp, SWT.NONE);
			g.setLayout(gl);
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
			
			// TODO Replace all these Buttons with a loop over SaxsAnalysisPlotType.values()
			loglogButton = new Button(g, SWT.CHECK);
			loglogButton.setText(SaxsAnalysisPlotType.LOGLOG_PLOT.getName());
			Pair<String, String> axesNames = SaxsAnalysisPlotType.LOGLOG_PLOT.getAxisNames();
			String toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			loglogButton.setToolTipText(toolTipText);
			loglogButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					loglogPlotSourceProvider.setEnableLogLog(loglogButton.getSelection());
				}
			});
			
			guinierButton = new Button(g, SWT.CHECK);
			guinierButton.setText(SaxsAnalysisPlotType.GUINIER_PLOT.getName());
			axesNames = SaxsAnalysisPlotType.GUINIER_PLOT.getAxisNames();
			toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			guinierButton.setToolTipText(toolTipText);
			guinierButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					guinierPlotSourceProvider.setEnableGuinier(guinierButton.getSelection());
				}
			});
			
			porodButton = new Button(g, SWT.CHECK);
			porodButton.setText(SaxsAnalysisPlotType.POROD_PLOT.getName());
			axesNames = SaxsAnalysisPlotType.POROD_PLOT.getAxisNames();
			toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			porodButton.setToolTipText(toolTipText);
			porodButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					porodPlotSourceProvider.setEnablePorod(porodButton.getSelection());
				}
			});
			
			kratkyButton = new Button(g, SWT.CHECK);
			kratkyButton.setText(SaxsAnalysisPlotType.KRATKY_PLOT.getName());
			axesNames = SaxsAnalysisPlotType.KRATKY_PLOT.getAxisNames();
			toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			kratkyButton.setToolTipText(toolTipText);
			kratkyButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					kratkyPlotSourceProvider.setEnableKratky(kratkyButton.getSelection());
				}
			});
			
			zimmButton = new Button(g, SWT.CHECK);
			zimmButton.setText(SaxsAnalysisPlotType.ZIMM_PLOT.getName());
			axesNames =  SaxsAnalysisPlotType.ZIMM_PLOT.getAxisNames();
			toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			zimmButton.setToolTipText(toolTipText);
			zimmButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					zimmPlotSourceProvider.setEnableZimm(zimmButton.getSelection());
				}
			});
			
			debyebuecheButton = new Button(g, SWT.CHECK);
			debyebuecheButton.setText(SaxsAnalysisPlotType.DEBYE_BUECHE_PLOT.getName());
			axesNames = SaxsAnalysisPlotType.DEBYE_BUECHE_PLOT.getAxisNames();
			toolTipText = axesNames.getSecond() + " vs. " + axesNames.getFirst();
			debyebuecheButton.setToolTipText(toolTipText);
			debyebuecheButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					debyebuechePlotSourceProvider.setEnableDebyeBueche(debyebuecheButton.getSelection());
				}
			});
			
			saxsPlotEcomp.setClient(g);
		}
		saxsPlotEcomp.setExpanded(false);
		
		normEcomp = new ExpandableComposite(c, SWT.NONE);
		normEcomp.setText("Normalisation");
		normEcomp.setToolTipText("Set options for Normalisation stage");
		gl = new GridLayout(4, false);
		gl.horizontalSpacing = 15;
		normEcomp.setLayout(gl);
		normEcomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		normEcomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(normEcomp, SWT.NONE);
			g.setLayout(new GridLayout(4, false));
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
			
			absScaleLabel = new Label(g, SWT.NONE);
			absScaleLabel.setText("Abs. Scale");
			absScaleLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			absScale = new Text(g, SWT.BORDER);
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
			
			sampleThicknessLabel = new Label(g, SWT.NONE);
			sampleThicknessLabel.setText("Sample Thickness (mm)");
			sampleThicknessLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			sampleThickness = new Text(g, SWT.BORDER);
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
			
			useFormSampleThicknessButton = new Button(g, SWT.CHECK);
			useFormSampleThicknessButton.setText("Use sample thickness in this form");
			useFormSampleThicknessButton.setToolTipText("Use sample thickness in this GUI instead of value in the file");
			useFormSampleThicknessButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdUseFormSampleThicknessSourceProvider.setUseFormSampleThickness(useFormSampleThicknessButton.getSelection(), false);
				}
			});
			
			normEcomp.setClient(g);
		}
		normEcomp.setExpanded(false);
		
		refEcomp = new ExpandableComposite(c, SWT.NONE);
		refEcomp.setText("Reference data");
		refEcomp.setToolTipText("Set options for NCD data reduction");
		gl = new GridLayout(4, false);
		gl.horizontalSpacing = 15;
		refEcomp.setLayout(gl);
		refEcomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		refEcomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(refEcomp, SWT.NONE);
			g.setLayout(new GridLayout(3, false));
			g.setLayoutData(new GridData(SWT.FILL, SWT.LEFT, true, false));
			
			bgFileSelector = new SelectorWidget(g, false, new String[] { "NeXus files", "All Files"}, new String[] {"*.nxs", "*.*"}) {
				@Override
				public void pathChanged(String path, TypedEvent event) {
					File tmpBgFile = new File(path);
					if (tmpBgFile.exists())
						ncdBgFileSourceProvider.setBgFile(path);
					else
						ncdBgFileSourceProvider.setBgFile(null);
				}
			};
			bgFileSelector.setLabel("Background Subtraction File:");
			bgFileSelector.setTextToolTip("File with the background measurements");
			bgFileSelector.setButtonToolTip("Select Background Data File");
			String tmpBgFile = ncdBgFileSourceProvider.getBgFile();
			if (tmpBgFile != null)
				bgFileSelector.setText(tmpBgFile);
			
			bgScaleLabel = new Label(g, SWT.NONE);
			bgScaleLabel.setText("Background Scale:");
			bgScaleLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
			bgScale = new Text(g, SWT.BORDER);
			bgScale.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
			bgScale.setToolTipText("Scaling values for background data");
			Double tmpBgScaling = ncdBgScaleSourceProvider.getBgScaling();
			if (tmpBgScaling != null) {
				bgScale.setText(tmpBgScaling.toString());
			}
			bgScale.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					ncdBgScaleSourceProvider.setBgScaling(getBgScale(), false);
				}
			});
			
			drFileSelector = new SelectorWidget(g, false, new String[] { "NeXus files", "All Files" }, new String[] {"*.nxs", "*.*"}) {
				@Override
				public void pathChanged(String path, TypedEvent event) {
					File tmpDrFile = new File(path);
					if (tmpDrFile.exists())
						ncdDrFileSourceProvider.setDrFile(path);
					else
						ncdDrFileSourceProvider.setDrFile(null);
				}
			};
			drFileSelector.setLabel("Detector Response File:");
			drFileSelector.setTextToolTip("File with the detector response frame");
			drFileSelector.setButtonToolTip("Select Detector Response File");
			String tmpDrFile = ncdDrFileSourceProvider.getDrFile();
			if (tmpDrFile != null)
				drFileSelector.setText(tmpDrFile);
			refEcomp.setClient(g);
		}
		refEcomp.setExpanded(false);

		bgEcomp = new ExpandableComposite(c, SWT.NONE);
		bgEcomp.setText("Background frame selection");
		bgEcomp.setToolTipText("Set background data slicing parameters");
		gl = new GridLayout(1, false);
		gl.horizontalSpacing = 15;
		bgEcomp.setLayout(gl);
		bgEcomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		bgEcomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(bgEcomp, SWT.NONE);
			g.setLayout(new GridLayout(4, false));
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
			
			SliceInput tmpSlice = ncdBkgSliceSourceProvider.getBkgSlice();
			
			bgFramesStartLabel = new Label(g, SWT.NONE);
			bgFramesStartLabel.setText("First");
			bgFramesStart = new Text(g, SWT.BORDER);
			bgFramesStart.setToolTipText("First frame to select from the background data");
			bgFramesStart.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			if (tmpSlice != null && tmpSlice.getStartFrame() != null)
				bgFramesStart.setText(tmpSlice.getStartFrame().toString());
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
			if (tmpSlice != null && tmpSlice.getStopFrame() != null)
				bgFramesStop.setText(tmpSlice.getStopFrame().toString());
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
			if (tmpSlice != null && tmpSlice.getAdvancedSlice() != null)
				bgAdvanced.setText(tmpSlice.getAdvancedSlice());
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
			bgEcomp.setClient(g);
		}
		if (ncdBkgSliceSourceProvider.getBkgSlice() != null) {
			bgEcomp.setExpanded(true);
			expansionAdapter.expansionStateChanged(new ExpansionEvent(bgEcomp, true));
		}
		
		ecomp = new ExpandableComposite(c, SWT.NONE);
		ecomp.setText("Data frame selection");
		ecomp.setToolTipText("Set data slicing parameters");
		gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		ecomp.setLayout(gl);
		ecomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		ecomp.addExpansionListener(expansionAdapter);

		{
			Composite g = new Composite(ecomp, SWT.NONE);
			g.setLayout(new GridLayout(4, false));
			g.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
			
			SliceInput tmpSlice = ncdDataSliceSourceProvider.getDataSlice();
			
			detFramesStartLabel = new Label(g, SWT.NONE);
			detFramesStartLabel.setText("First");
			detFramesStart = new Text(g, SWT.BORDER);
			detFramesStart.setToolTipText("First frame to select from the data file ");
			detFramesStart.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
			if (tmpSlice != null && tmpSlice.getStartFrame() != null)
				detFramesStart.setText(tmpSlice.getStartFrame().toString());
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
			if (tmpSlice != null && tmpSlice.getStopFrame() != null)
				detFramesStop.setText(tmpSlice.getStopFrame().toString());
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
			if (tmpSlice != null && tmpSlice.getAdvancedSlice() != null)
				detAdvanced.setText(tmpSlice.getAdvancedSlice());
			detAdvanced.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					SliceInput tmpDetSlice = new SliceInput(getDetAdvancedSelection());
					ncdDataSliceSourceProvider.setDataSlice(tmpDetSlice);
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
		if (ncdDataSliceSourceProvider.getDataSlice() != null) {
			ecomp.setExpanded(true);
			expansionAdapter.expansionStateChanged(new ExpansionEvent(ecomp, true));
		}
		
		aveEcomp = new ExpandableComposite(c, SWT.NONE);
		aveEcomp.setText("Grid data averaging");
		aveEcomp.setToolTipText("Specify dimensions for averaging");
		gl = new GridLayout(2, false);
		gl.horizontalSpacing = 15;
		aveEcomp.setLayout(gl);
		aveEcomp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
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
					ncdGridAverageSourceProvider.setGrigAverage(new SliceInput(getGridAverageSelection()), false);
				}
			});
			gridAverage = new Text(g, SWT.BORDER);
			gridAverage.setToolTipText("Comma-separated list of grid dimensions to average");
			gridAverage.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			gridAverage.setEnabled(false);
			SliceInput tmpAverage = ncdGridAverageSourceProvider.getGridAverage();
			if (tmpAverage != null && tmpAverage.getAdvancedSlice() != null) {
				gridAverage.setText(tmpAverage.getAdvancedSlice());
			}
			gridAverage.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					ncdGridAverageSourceProvider.setGrigAverage(new SliceInput(getGridAverageSelection()), false);
				}
			});
			aveEcomp.setClient(g);
		}
		if (ncdGridAverageSourceProvider.getGridAverage() != null) {
			aveEcomp.setExpanded(true);
			expansionAdapter.expansionStateChanged(new ExpansionEvent(aveEcomp, true));
		}

		sc.setContent(c);
		sc.setExpandVertical(true);
		sc.setExpandHorizontal(true);
		sc.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Rectangle r = sc.getClientArea();
				sc.setMinHeight(c.computeSize(r.width, SWT.DEFAULT).y);
				sc.setMinWidth(c.computeSize(SWT.DEFAULT, r.height).x);
			}
		});
		
		restoreState();
	}

	private void ConfigureNcdSourceProviders() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		
		ncdNormalisationSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.NORMALISATION_STATE);
		ncdNormalisationSourceProvider.addSourceProviderListener(this);
		ncdBackgroundSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BACKGROUD_STATE);
		ncdBackgroundSourceProvider.addSourceProviderListener(this);
		ncdResponseSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RESPONSE_STATE);
		ncdResponseSourceProvider.addSourceProviderListener(this);
		ncdSectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SECTOR_STATE);
		ncdSectorSourceProvider.addSourceProviderListener(this);
		ncdInvariantSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.INVARIANT_STATE);
		ncdInvariantSourceProvider.addSourceProviderListener(this);
		ncdAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AVERAGE_STATE);
		ncdAverageSourceProvider.addSourceProviderListener(this);
		
		ncdRadialSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.RADIAL_STATE);
		ncdRadialSourceProvider.addSourceProviderListener(this);
		ncdAzimuthSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.AZIMUTH_STATE);
		ncdAzimuthSourceProvider.addSourceProviderListener(this);
		ncdFastIntSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.FASTINT_STATE);
		ncdFastIntSourceProvider.addSourceProviderListener(this);
		
		ncdMaskSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.MASK_STATE);
		ncdMaskSourceProvider.addSourceProviderListener(this);
		ncdMaskFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.MASKFILE_STATE);
		ncdMaskFileSourceProvider.addSourceProviderListener(this);
		
		ncdDataSliceSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.DATASLICE_STATE);
		ncdDataSliceSourceProvider.addSourceProviderListener(this);
		ncdBkgSliceSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGSLICE_STATE);
		ncdBkgSliceSourceProvider.addSourceProviderListener(this);
		ncdGridAverageSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.GRIDAVERAGE_STATE);
		ncdGridAverageSourceProvider.addSourceProviderListener(this);
		
		ncdBgFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGFILE_STATE);
		ncdBgFileSourceProvider.addSourceProviderListener(this);
		ncdDrFileSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.DRFILE_STATE);
		ncdDrFileSourceProvider.addSourceProviderListener(this);
		
		ncdWorkingDirSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WORKINGDIR_STATE);
		ncdWorkingDirSourceProvider.addSourceProviderListener(this);
		
		ncdSampleThicknessSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAMPLETHICKNESS_STATE);
		ncdSampleThicknessSourceProvider.addSourceProviderListener(this);
		ncdAbsScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSSCALING_STATE);
		ncdAbsScaleSourceProvider.addSourceProviderListener(this);
		ncdUseFormSampleThicknessSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.USEFORMSAMPLETHICKNESS_STATE);
		ncdUseFormSampleThicknessSourceProvider.addSourceProviderListener(this);
		
		ncdBgScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.BKGSCALING_STATE);
		ncdBgScaleSourceProvider.addSourceProviderListener(this);
		
		loglogPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.LOGLOG_STATE);
		loglogPlotSourceProvider.addSourceProviderListener(this);
		guinierPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.GUINIER_STATE);
		guinierPlotSourceProvider.addSourceProviderListener(this);
		porodPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.POROD_STATE);
		porodPlotSourceProvider.addSourceProviderListener(this);
		kratkyPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.KRATKY_STATE);
		kratkyPlotSourceProvider.addSourceProviderListener(this);
		zimmPlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.ZIMM_STATE);
		zimmPlotSourceProvider.addSourceProviderListener(this);
		debyebuechePlotSourceProvider = (SaxsPlotsSourceProvider) service.getSourceProvider(SaxsPlotsSourceProvider.DEBYE_BUECHE_STATE);
		debyebuechePlotSourceProvider.addSourceProviderListener(this);
	}

	@Override
	public void setFocus() {
		getViewSite().getShell().setFocus();
	}

	private void updateNormalisationWidgets(boolean selection) {
		if (normEcomp != null && !(normEcomp.isDisposed())) {
			normEcomp.setExpanded(selection);
			normEcomp.setEnabled(selection);
			expansionAdapter.expansionStateChanged(new ExpansionEvent(normEcomp, selection));
		}
		if (normButton != null && !(normButton.isDisposed()))
			normButton.setSelection(selection);
		if (sampleThickness != null && !(sampleThickness.isDisposed()))
			sampleThickness.setEnabled(selection);
		if (absScale != null && !(absScale.isDisposed()))
			absScale.setEnabled(selection);
		if (absScaleLabel != null && !(absScaleLabel.isDisposed()))
			absScaleLabel.setEnabled(selection);
		if (useFormSampleThicknessButton != null && !(useFormSampleThicknessButton.isDisposed()))
			useFormSampleThicknessButton.setEnabled(selection);
	}

	private void updateSectorIntegrationWidgets(boolean selection) {
		if (saxsPlotEcomp != null && !(saxsPlotEcomp.isDisposed())) {
			saxsPlotEcomp.setExpanded(selection);
			saxsPlotEcomp.setEnabled(selection);
			expansionAdapter.expansionStateChanged(new ExpansionEvent(saxsPlotEcomp, selection));
		}
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
		if (maskFileSelector != null && !(maskFileSelector.isDisposed()))
			maskFileSelector.setEnabled(selection);
	}

	private void updateBackgroundSubtractionWidgets(boolean selection) {
		if (refEcomp != null && !(refEcomp.isDisposed())) {
			if (!selection && !(drButton.getSelection())) {
				refEcomp.setExpanded(selection);
				refEcomp.setEnabled(selection);
			} else {
				refEcomp.setExpanded(true);
				refEcomp.setEnabled(true);
			}
			expansionAdapter.expansionStateChanged(new ExpansionEvent(refEcomp, selection));
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
		if (bgFileSelector != null && !(bgFileSelector.isDisposed()))
			bgFileSelector.setEnabled(selection);
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
			if (!selection && !(bgButton.getSelection())) {
				refEcomp.setExpanded(selection);
				refEcomp.setEnabled(selection);
			} else {
				refEcomp.setExpanded(true);
				refEcomp.setEnabled(true);
			}
			expansionAdapter.expansionStateChanged(new ExpansionEvent(refEcomp, selection));
		}
		if (drButton != null && !(drButton.isDisposed()))
			drButton.setSelection(selection);
		if (drLabel != null && !(drLabel.isDisposed()))
			drLabel.setEnabled(selection);
		if (drFileSelector != null && !(drFileSelector.isDisposed()))
			drFileSelector.setEnabled(selection);
	}
	
	private void updateAverageWidgets(boolean selection) {
		if (aveEcomp != null && !(aveEcomp.isDisposed())) {
			aveEcomp.setEnabled(selection);
			if (!selection) {
				aveEcomp.setExpanded(selection);
			}
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
		for (Object key : sourceValuesByName.keySet()) {
			if (key instanceof String) {
				String name = (String) key;
				sourceChanged(sourcePriority, name, sourceValuesByName.get(name));
			}
		}
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, Object sourceValue) {
		if (sourceName.equals(NcdProcessingSourceProvider.BKGSCALING_STATE)) {
			if (sourceValue != null) {
			    DecimalFormat sForm = new DecimalFormat("0.#####E0");
				String sourceText = sForm.format(sourceValue);
				if (sourceText != null) {
					bgScale.setText(sourceText);
				}
			} else {
				bgScale.setText("");
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.SAMPLETHICKNESS_STATE)) {
			if (sourceValue != null) {
			    DecimalFormat sForm = new DecimalFormat("0.#####E0");
				String sourceText = sForm.format(sourceValue);
				if (sourceText != null) {
					sampleThickness.setText(sourceText);
				}
			} else {
				sampleThickness.setText("");
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.ABSSCALING_STATE)) {
			if (sourceValue != null) {
			    DecimalFormat sForm = new DecimalFormat("0.#####E0");
				String sourceText = sForm.format(sourceValue);
				if (sourceText != null) {
					absScale.setText(sourceText);
					absScale.setEnabled(false);
				} else {
					absScale.setText("");
					absScale.setEnabled(true);
				}
			} else {
				absScale.setText("");
				absScale.setEnabled(true);
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.USEFORMSAMPLETHICKNESS_STATE)) {
			boolean isUseFormSampleThickness = ncdUseFormSampleThicknessSourceProvider.isUseFormSampleThickness();
			useFormSampleThicknessButton.setEnabled(isUseFormSampleThickness);
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
		
		if (sourceName.equals(NcdProcessingSourceProvider.INVARIANT_STATE)) {
			boolean isEnableInvariant = ncdInvariantSourceProvider.isEnableInvariant();
			invButton.setSelection(isEnableInvariant);
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.AVERAGE_STATE)) {
			boolean isEnableAverage = ncdAverageSourceProvider.isEnableAverage();
			updateAverageWidgets(isEnableAverage);
		}
		
		if (sourceName.equals(SaxsPlotsSourceProvider.LOGLOG_STATE)) {
			boolean isEnable = loglogPlotSourceProvider.isEnableLogLog();
			loglogButton.setSelection(isEnable);
		}
		
		if (sourceName.equals(SaxsPlotsSourceProvider.GUINIER_STATE)) {
			boolean isEnable = guinierPlotSourceProvider.isEnableGuinier();
			guinierButton.setSelection(isEnable);
		}
		
		if (sourceName.equals(SaxsPlotsSourceProvider.POROD_STATE)) {
			boolean isEnable = porodPlotSourceProvider.isEnablePorod();
			porodButton.setSelection(isEnable);
		}
		
		if (sourceName.equals(SaxsPlotsSourceProvider.KRATKY_STATE)) {
			boolean isEnable = kratkyPlotSourceProvider.isEnableKratky();
			kratkyButton.setSelection(isEnable);
		}
		
		if (sourceName.equals(SaxsPlotsSourceProvider.ZIMM_STATE)) {
			boolean isEnable = zimmPlotSourceProvider.isEnableZimm();
			zimmButton.setSelection(isEnable);
		}
		
		if (sourceName.equals(SaxsPlotsSourceProvider.DEBYE_BUECHE_STATE)) {
			boolean isEnable = debyebuechePlotSourceProvider.isEnableDebyeBueche();
			debyebuecheButton.setSelection(isEnable);
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.MASKFILE_STATE)) {
			if (maskFileSelector != null  && !(maskFileSelector.isDisposed())) {
				String tmpText = maskFileSelector.getText();
				if (!(tmpText.equals(sourceValue)) && (sourceValue != null))
					maskFileSelector.setText((String) sourceValue);
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.BKGFILE_STATE)) {
			if (bgFileSelector != null  && !(bgFileSelector.isDisposed())) {
				String tmpText = bgFileSelector.getText();
				if (!(tmpText.equals(sourceValue)) && (sourceValue != null))
					bgFileSelector.setText((String) sourceValue);
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.DRFILE_STATE)) {
			if (drFileSelector != null && !(drFileSelector.isDisposed())) {
				String tmpText = drFileSelector.getText();
				if (!(tmpText.equals(sourceValue)) && (sourceValue != null))
					drFileSelector.setText((String) sourceValue);
			}
		}

		if(sourceName.equals(NcdProcessingSourceProvider.WORKINGDIR_STATE)){
			if(locationSelector !=null && !locationSelector.isDisposed()){
				String tmpText = locationSelector.getText();
				if(!tmpText.equals(sourceValue) && sourceValue != null){
					locationSelector.setText((String) sourceValue);
				}
			}
		}
		
		if(sourceName.equals(NcdProcessingSourceProvider.DATASLICE_STATE)){
			SliceInput slice = (SliceInput)sourceValue;
			if(detFramesStart != null && !detFramesStart.isDisposed()){
				String tmpText = detFramesStart.getText();
				
				// TODO shield the text field so that only integers can be input
				if(slice != null && slice.getStartFrame() != null 
						&& !tmpText.equals(slice.getStartFrame().toString())){
					detFramesStart.setText(slice.getStartFrame().toString());
				}
			}
			if(detFramesStop != null && !detFramesStop.isDisposed()){
				String tmpText = detFramesStop.getText();
				if(slice != null && slice.getStopFrame() != null 
						&& !tmpText.equals(slice.getStopFrame().toString())){
					detFramesStop.setText(slice.getStopFrame().toString());
				}
			}
			if(detAdvancedButton != null && !detAdvancedButton.isDisposed()){
				boolean tmpBool = detAdvancedButton.getSelection();
				if(slice != null && slice.isAdvanced() != tmpBool){
					detAdvancedButton.setSelection(slice.isAdvanced());
				}
			}
			if(detAdvanced != null && !detAdvanced.isDisposed()){
				String tmpText = detAdvanced.getText();
				if(slice != null && slice.getAdvancedSlice() != null 
						&& !tmpText.equals(slice.getAdvancedSlice())){
					detAdvanced.setText(slice.getAdvancedSlice());
				}
			}
		}
		
		if(sourceName.equals(NcdProcessingSourceProvider.BKGSLICE_STATE)){
			SliceInput slice = (SliceInput)sourceValue;
			if(bgFramesStart != null && !bgFramesStart.isDisposed()){
				String tmpText = bgFramesStart.getText();
				
				// TODO shield the text field so that only integers can be input
				if(slice != null && slice.getStartFrame() != null 
						&& !tmpText.equals(slice.getStartFrame().toString())){
					bgFramesStart.setText(slice.getStartFrame().toString());
				}
			}
			if(bgFramesStop != null && !bgFramesStop.isDisposed()){
				String tmpText = bgFramesStop.getText();
				if(slice != null && slice.getStopFrame() != null 
						&& !tmpText.equals(slice.getStopFrame().toString())){
					bgFramesStop.setText(slice.getStopFrame().toString());
				}
			}
			if(bgAdvancedButton != null && !bgAdvancedButton.isDisposed()){
				boolean tmpBool = bgAdvancedButton.getSelection();
				if(slice != null && slice.isAdvanced() != tmpBool){
					bgAdvancedButton.setSelection(slice.isAdvanced());
				}
			}
			if(bgAdvanced != null && !bgAdvanced.isDisposed()){
				String tmpText = bgAdvanced.getText();
				if(slice != null && slice.getAdvancedSlice() != null 
						&& !tmpText.equals(slice.getAdvancedSlice())){
					bgAdvanced.setText(slice.getAdvancedSlice());
				}
			}
		}
		
		if(sourceName.equals(NcdProcessingSourceProvider.GRIDAVERAGE_STATE)){
			SliceInput slice = (SliceInput)sourceValue;
			if(gridAverageButton != null && !gridAverageButton.isDisposed()){
				boolean tmpBool = gridAverageButton.getSelection();
				if(slice != null && slice.isAdvanced() != tmpBool){
					gridAverageButton.setSelection(slice.isAdvanced());
				}
			}
			if(gridAverage != null && !gridAverage.isDisposed()){
				String tmpText = gridAverage.getText();
				if(slice != null && slice.getAdvancedSlice() != null 
						&& !tmpText.equals(slice.getAdvancedSlice())){
					gridAverage.setText(slice.getAdvancedSlice());
				}
			}
		}
		
		if(sourceName.equals(NcdProcessingSourceProvider.RADIAL_STATE)){
			if(radialButton != null && !radialButton.isDisposed()){
				boolean tmpBool = radialButton.getSelection();
				if(sourceValue != null && (Boolean) sourceValue != tmpBool){
					radialButton.setSelection((Boolean) sourceValue);
				}
			}
		}
		
		if(sourceName.equals(NcdProcessingSourceProvider.AZIMUTH_STATE)){
			if(azimuthalButton != null && !azimuthalButton.isDisposed()){
				boolean tmpBool = azimuthalButton.getSelection();
				if(sourceValue != null && (Boolean) sourceValue != tmpBool){
					azimuthalButton.setSelection((Boolean) sourceValue);
				}
			}
		}
		
		if(sourceName.equals(NcdProcessingSourceProvider.FASTINT_STATE)){
			if(fastIntButton != null && !fastIntButton.isDisposed()){
				boolean tmpBool = fastIntButton.getSelection();
				if(sourceValue != null && (Boolean) sourceValue != tmpBool){
					fastIntButton.setSelection((Boolean) sourceValue);
				}
			}
		}
		
		if(sourceName.equals(NcdProcessingSourceProvider.MASK_STATE)){
			if(useMask != null && !useMask.isDisposed()){
				boolean tmpBool = useMask.getSelection();
				if(sourceValue != null && (Boolean) sourceValue != tmpBool){
					useMask.setSelection((Boolean) sourceValue);
				}
			}
		}
	}

	/**
	 * 
	 */
	private IPlottingSystem<Composite> getCurrentPlottingSystem() {
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
				.getActivePage();
		if (page == null)
			return null;
		IViewPart activePlot = page.findView(PlotView.ID + "DP");
		if (activePlot instanceof PlotView) {
			IPlottingSystem<Composite> activePlotSystem = PlottingFactory
					.getPlottingSystem(((PlotView) activePlot).getPartName());
			return activePlotSystem;
		}
		return null;
	}

	/**
	 * 
	 * @return Current ImageTrace if any, null otherwise
	 */
	private IImageTrace getCurrentTrace() {
		IPlottingSystem<Composite> system = getCurrentPlottingSystem();
		if (system == null)
			return null;
		Collection<ITrace> traces = system.getTraces();
		if (traces == null)
			return null;
		Iterator<ITrace> it = traces.iterator();
		if (!it.hasNext())
			return null;
		ITrace trace = it.next();
		if (!(trace instanceof IImageTrace))
			return null;
		return (IImageTrace) trace;
	}

	/**
	 * 
	 * @param filePath
	 * @throws Exception
	 */
	private void createMask(final String filePath) throws Exception {
		IPersistentFile file = null;
		try {
			IPersistenceService service = (IPersistenceService) ServiceManager.getService(IPersistenceService.class);
			file = service.getPersistentFile(filePath);
			final IImageTrace image = getCurrentTrace();
			if (image == null) {
				MessageDialog.openWarning(Display.getDefault().getActiveShell(), "Error setting mask",
						"No image could be found on the Dataset Plot. Please load an image to the Dataset Plot" +
						" and then retry setting the mask.");
				maskFileSelector.setText("");
				return;
			}
			final IPlottingSystem<Composite> system = getCurrentPlottingSystem();
			if (system == null) {
				logger.error("The plotting system is NULL.");
			}

			//String name = options.getString("Mask");
			String name = file.getMaskNames(null).get(0);
			final BooleanDataset mask = (BooleanDataset) file.getMask(name, null);
			if (mask != null) {
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {
						// we set the maskDataset on the masking tool
						if (system != null) {
							final IToolPageSystem tsystem = (IToolPageSystem) system.getAdapter(IToolPageSystem.class);
							final IToolPage tool = tsystem.getActiveTool();
							if (tool != null
									&& tool.getToolId().equals("org.dawb.workbench.plotting.tools.maskingTool")) {
								tool.setToolData(mask);
							}
						}
						// we set the mask on the image trace
						image.setMask(mask);
					}
				});
			} else {
				MessageDialog.openWarning(Display.getDefault().getActiveShell(), "Error setting mask",
						"No mask could be found in the selected file. Please select a file with a persisted mask with the following path: /entry/mask");
				maskFileSelector.setText("");
			}
		} finally {
			if (file != null)
				file.close();
		}
	}
}
