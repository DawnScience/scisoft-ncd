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

package uk.ac.diamond.scisoft.ncd.rcp.views;

import java.text.DecimalFormat;
import java.util.Map;

import org.apache.commons.validator.routines.DoubleValidator;
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.plotting.api.PlottingFactory;
import org.dawnsci.plotting.api.trace.ColorOption;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISourceProviderListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.services.ISourceProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.handlers.NcdAbsoluteCalibrationListener;

public class AbsoluteIntensityCalibration extends ViewPart implements ISourceProviderListener {

	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.AbsoluteIntensityCalibration";
	
	private static final String REEFRENCE_PLOT_NAME = "Dataset Plot";
	private static final String RESULTS_PLOT_NAME = "Absolute Calibration Plot";
	
	private IPlottingSystem plottingSystem;
	private Text sampleThickness;
	private Label absScale, absOffset;
	private NcdAbsoluteCalibrationListener absoluteCalibrationListener;
	private Button runCalibratioin, clearCalibratioin;
	private NcdProcessingSourceProvider ncdSampleThicknessSourceProvider;
	private NcdProcessingSourceProvider ncdAbsScaleSourceProvider;
	private NcdProcessingSourceProvider ncdAbsOffsetSourceProvider;
	private static final Logger logger = LoggerFactory.getLogger(AbsoluteIntensityCalibration.class);

	private DoubleValidator doubleValidator = DoubleValidator.getInstance();
	private IMemento memento;
	
	private Double getSampleThickness() {
		String input = sampleThickness.getText();
		return doubleValidator.validate(input);
	}
	
	private Double getAbsScale() {
		String input = absScale.getText();
		return doubleValidator.validate(input);
	}
	
	private Double getAbsOffset() {
		String input = absOffset.getText();
		return doubleValidator.validate(input);
	}
	
	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
	 this.memento = memento;
	 super.init(site, memento);
	}
	
	@Override
	public void createPartControl(Composite parent) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		ncdSampleThicknessSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAMPLETHICKNESS_STATE);
		ncdAbsScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSSCALING_STATE);
		ncdAbsOffsetSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSOFFSET_STATE);
		
		ncdAbsScaleSourceProvider.addSourceProviderListener(this);
		ncdAbsOffsetSourceProvider.addSourceProviderListener(this);
		
		Composite c = new Composite(parent, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		
		{
			Composite g = new Composite(c, SWT.NONE);
			g.setLayout(new GridLayout(2, false));
			g.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, true));
			
			Label sampleThicknessLabel = new Label(g, SWT.NONE);
			sampleThicknessLabel.setText("Sample Thickness (mm)");
			sampleThicknessLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
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
			
			Label absScaleLabel = new Label(g, SWT.NONE);
			absScaleLabel.setText("Abs. Scale");
			absScaleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
			absScale = new Label(g, SWT.NONE);
			absScale.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			absScale.setToolTipText("Select absolute scaling factor for calibration data");
			Double tmpAbsScaling = ncdAbsScaleSourceProvider.getAbsScaling();
			if (tmpAbsScaling != null) {
				absScale.setText(tmpAbsScaling.toString());
			}
			
			Label absOffsetLabel = new Label(g, SWT.NONE);
			absOffsetLabel.setText("Offset");
			absOffsetLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			absOffset = new Label(g, SWT.NONE);
			absOffset.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			absOffset.setToolTipText("Offset value between reference and calibrated profiles. Should be close to zero.");
			
			absoluteCalibrationListener = new NcdAbsoluteCalibrationListener(REEFRENCE_PLOT_NAME, RESULTS_PLOT_NAME);
			
			runCalibratioin = new Button(g, SWT.PUSH);
			runCalibratioin.setText("Run Absolute Intensity Calibration");
			runCalibratioin.setToolTipText("Run absolute intensity calibration procedure." +
							" Please plot normalised glassy carbon sample image before starting calibration.");
			runCalibratioin.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			runCalibratioin.addSelectionListener(absoluteCalibrationListener);
			
			clearCalibratioin = new Button(g, SWT.PUSH);
			clearCalibratioin.setText("Clear Calibration Data");
			clearCalibratioin.setToolTipText("Clear absolute intensity calibration data to enable manual scaling for data reduction.");
			clearCalibratioin.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			clearCalibratioin.addSelectionListener(new SelectionAdapter() {
				
				@Override
				public void widgetSelected(SelectionEvent e) {
					ncdSampleThicknessSourceProvider.setSampleThickness(null, true);
					ncdAbsScaleSourceProvider.setAbsScaling(null, true);
					ncdAbsOffsetSourceProvider.setAbsOffset(null);
					plottingSystem.clear();
				}
			});
		}
		
		try {
	        plottingSystem = PlottingFactory.createPlottingSystem();
	        plottingSystem.setColorOption(ColorOption.NONE);
		} catch (Exception e) {
			logger.error("Cannot locate any plotting systems!", e);
		}
		
		final Composite plot = new Composite(c, SWT.NONE);
		plot.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		plot.setLayout(new FillLayout());
		try {
	        IActionBars wrapper = this.getViewSite().getActionBars();
			plottingSystem.createPlotPart(plot, RESULTS_PLOT_NAME, wrapper, PlotType.XY, this);

		} catch (Exception e) {
			logger.error("Error creating plot part.", e);
		}
		
		restoreState();
	}

	@Override
	public void saveState(IMemento memento) {
		if (memento != null) {
			Double sampleThicknessVal = getSampleThickness();
			if (sampleThicknessVal != null) {
				memento.putFloat(NcdPreferences.NCD_SAMPLETHICKNESS, sampleThicknessVal.floatValue());
			}
			Double absScaleVal = getAbsScale();
			if (absScaleVal != null) {
				memento.putFloat(NcdPreferences.NCD_ABSOLUTESCALE, absScaleVal.floatValue());
			}
			Double absOffsetVal = getAbsOffset();
			if (absOffsetVal != null) {
				memento.putFloat(NcdPreferences.NCD_ABSOLUTEOFFSET, absOffsetVal.floatValue());
			}
		}
	}
		
	private void restoreState() {
		if (memento != null) {
			Float flt;
			flt = memento.getFloat(NcdPreferences.NCD_ABSOLUTESCALE);
			if (flt != null) {
				absScale.setText(flt.toString());
				ncdAbsScaleSourceProvider.setAbsScaling(new Double(flt), true);
			}

			flt = memento.getFloat(NcdPreferences.NCD_ABSOLUTEOFFSET);
			if (flt != null) {
				absOffset.setText(flt.toString());
				ncdAbsOffsetSourceProvider.setAbsOffset(new Double(flt));
			}

			flt = memento.getFloat(NcdPreferences.NCD_SAMPLETHICKNESS);
			if (flt != null) {
				sampleThickness.setText(flt.toString());
				ncdSampleThicknessSourceProvider.setSampleThickness(new Double(flt), true);
			}
		}
	}
	
	@Override
	public void setFocus() {
		getViewSite().getShell().setFocus();
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
			    DecimalFormat sForm = new DecimalFormat("0.####E0");
				String sourceText = sForm.format(sourceValue);
				if (sourceText != null) {
					absScale.setText(sourceText);
				}
			} else {
				absScale.setText("");
			}
		}
		
		if (sourceName.equals(NcdProcessingSourceProvider.ABSOFFSET_STATE)) {
			if (sourceValue != null) {
			    DecimalFormat sForm = new DecimalFormat("0.####E0");
				String sourceText = sForm.format(sourceValue);
				if (sourceText != null) {
					absOffset.setText(sourceText);
				}
			} else {
				absOffset.setText("");
			}
		}
	}
}
