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

package uk.ac.diamond.scisoft.ncd.calibration.rcp.views;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.math.NumberUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.trace.ColorOption;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.ISourceProviderListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.services.ISourceProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.ncd.calibration.rcp.handlers.NcdAbsoluteCalibrationListener;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;

public class AbsoluteIntensityCalibration extends ViewPart implements ISourceProviderListener {

	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.AbsoluteIntensityCalibration";
	
	private static final String REEFRENCE_PLOT_NAME = "Dataset Plot";
	private static final String RESULTS_PLOT_NAME = "Absolute Calibration Plot";
	
	private IPlottingSystem plottingSystem;
	private Combo standard;
	private Text sampleThickness;
	private Label absScale;

	private Text selectedFile, selectedEmptyCellFile;
	private NcdAbsoluteCalibrationListener absoluteCalibrationListener;
	private Button runCalibratioin, clearCalibratioin;
	private NcdProcessingSourceProvider ncdSampleThicknessSourceProvider;
	private NcdProcessingSourceProvider ncdAbsScaleSourceProvider;
	private static final Logger logger = LoggerFactory.getLogger(AbsoluteIntensityCalibration.class);

	private IMemento memento;

	private static List<String> calibrants;
	static {
		calibrants = new ArrayList<String>(2);
		calibrants.add("Glassy Carbon");
		calibrants.add("Water");
	}

	private final class FileSelectionListener implements ISelectionListener {
		@Override
		public void selectionChanged(IWorkbenchPart part, ISelection selection) {
			if (selectedFile != null && !(selectedFile.isDisposed())) {
				if (selection instanceof IStructuredSelection
						&& ((IStructuredSelection) selection).toList().size() == 1
						&& (((IStructuredSelection) selection).getFirstElement() instanceof IFile)) {

					final Object sel = ((IStructuredSelection) selection).getFirstElement();
					if (sel instanceof IFile) {
						selectedFile.setText(((IFile) sel).getName());
					}
				} else {
					selectedFile.setText("");
				}
			}
		}
	}

	private FileSelectionListener fileSelectionListener;

	private String getDataFileName() {
		File tmpFile = new File(selectedFile.getText());
		if (tmpFile.exists()) {
			return tmpFile.getAbsolutePath();
		}
		return null;
	}
	
	private String getEmptyCellFileName() {
		File tmpFile = new File(selectedEmptyCellFile.getText());
		if (tmpFile.exists()) {
			return tmpFile.getAbsolutePath();
		}
		return null;
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
		
		ncdAbsScaleSourceProvider.addSourceProviderListener(this);
		
		Composite c = new Composite(parent, SWT.NONE);
		c.setLayout(new GridLayout(2, false));
		c.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		
		{
			Composite g = new Composite(c, SWT.NONE);
			g.setLayout(new GridLayout(2, false));
			g.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, true));
			
			Label standardLabel = new Label(g, SWT.NONE);
			standardLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
			standardLabel.setText("Calibration Standard");

			standard = new Combo(g, SWT.NONE);
			standard.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			for (String calibrant : calibrants) {
				standard.add(calibrant);
			}
			standard.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					int idx = standard.getSelectionIndex();
					if (idx >= 0) {
						String sel = standard.getItem(idx);
						absoluteCalibrationListener.setCalibrant(sel);
					}
				}
			});
			standard.select(0);

			Label fileLabel = new Label(g, SWT.NONE);
			fileLabel.setText("Calibrant File");
			fileLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
			selectedFile = new Text(g, SWT.BORDER);
			selectedFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			selectedFile.setToolTipText("Specify calibrant file name");
			selectedFile.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					if (absoluteCalibrationListener != null) {
						absoluteCalibrationListener.setDataFileName(getDataFileName());
					}
				}
			});

			DropTarget fileDT = new DropTarget(selectedFile, DND.DROP_MOVE| DND.DROP_DEFAULT| DND.DROP_COPY);
			fileDT.setTransfer(new Transfer[] { TextTransfer.getInstance (), FileTransfer.getInstance()});
			fileDT.addDropListener(new DropTargetAdapter() {
				@Override
				public void drop(DropTargetEvent event) {
					Object data = event.data;
					if (data instanceof String[]) {
						String[] stringData = (String[]) data;
						if (stringData.length > 0) {
							File file = new File(stringData[0]);
							if (file.exists() && file.isFile()) {
								selectedFile.setText(file.getAbsolutePath());
								selectedFile.notifyListeners(SWT.Modify, null);
							}
						}
					}
				}
				
			});

			
			Label emptyCellLabel = new Label(g, SWT.NONE);
			emptyCellLabel.setText("Empty Cell File");
			emptyCellLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
			selectedEmptyCellFile = new Text(g, SWT.BORDER);
			selectedEmptyCellFile.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			selectedEmptyCellFile.setToolTipText("Specify empty cell file name");
			selectedEmptyCellFile.addModifyListener(new ModifyListener() {
				
				@Override
				public void modifyText(ModifyEvent e) {
					if (absoluteCalibrationListener != null) {
						absoluteCalibrationListener.setEmptyCellFileName(getEmptyCellFileName());
					}
				}
			});

			DropTarget emptyDT = new DropTarget(selectedEmptyCellFile, DND.DROP_MOVE| DND.DROP_DEFAULT| DND.DROP_COPY);
			emptyDT.setTransfer(new Transfer[] { TextTransfer.getInstance (), FileTransfer.getInstance()});
			emptyDT.addDropListener(new DropTargetAdapter() {
				@Override
				public void drop(DropTargetEvent event) {
					Object data = event.data;
					if (data instanceof String[]) {
						String[] stringData = (String[]) data;
						if (stringData.length > 0) {
							File file = new File(stringData[0]);
							if (file.exists() && file.isFile()) {
								selectedEmptyCellFile.setText(file.getAbsolutePath());
								selectedEmptyCellFile.notifyListeners(SWT.Modify, null);
							}
						}
					}
				}
				
			});
			
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
			absScaleLabel.setText("Absolute Instensity Scale");
			absScaleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
			absScale = new Label(g, SWT.NONE);
			absScale.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			absScale.setToolTipText("Select absolute scaling factor for calibration data");
			Double tmpAbsScaling = ncdAbsScaleSourceProvider.getAbsScaling();
			if (tmpAbsScaling != null) {
				absScale.setText(tmpAbsScaling.toString());
			}
			
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
					plottingSystem.clear();
				}
			});
			
			Label instructions = new Label(g, SWT.NONE);
			instructions.setText("\nInstructions:\n" +
								"1. Complete q-axis calibration stage, i.e. check that correct values for\n" +
								"   beam center, distance, wavelength are set in the \"Diffraction\" tool page.\n" +
								"2. Make sure that sector region is locked to beam center by pressing the\n" +
								"   \"Lock to Metadata\" button on the toolbar of the \"Raidal Profile\" tool page.\n" +
								"3. Apply detector mask.\n" +
								"4. Drag&&Drop files with a calibrant and an empty cell image from the \"Project Explorer\" view.\n" + 
								"5. Check that correct \"Normalisation Dataset\" is selected in \"NCD Detector Parameters\" view.\n"+
								"   Reload detector information if you need to refresh the list of available normalisation datasets.\n"+
								"6. Specify the selected calibrant sample thinckness in millimeters.\n" +
								"7. Press \"Run Absolute Intensity Calibration\" button to calculate absolute intensity scaling factor.\n"+
								"8. To override absolute scale factor used in the \"Normalisation\" stage of the NCD Data Reduction\n" +
								"   pipeline, press \"Clear Calibration Data\" button.");
			instructions.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
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
		
	}
	
	@Override
	public void dispose() {
		if (fileSelectionListener != null) {
			ISelectionService selectionService = getViewSite().getWorkbenchWindow().getSelectionService();
			selectionService.removePostSelectionListener(fileSelectionListener);
		}
		super.dispose();
	}
}
