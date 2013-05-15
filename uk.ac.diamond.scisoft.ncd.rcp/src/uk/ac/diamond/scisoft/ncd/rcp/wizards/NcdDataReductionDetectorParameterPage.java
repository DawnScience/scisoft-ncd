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

import javax.measure.quantity.Length;
import javax.measure.unit.SI;

import org.apache.commons.validator.routines.DoubleValidator;
import org.eclipse.jface.wizard.IWizardPage;
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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.ncd.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.preferences.NcdConstants;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdDataReductionDetectorParameterPage extends AbstractNcdDataReductionPage {

	public static int PAGENUMBER = 0;
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
	private NcdCalibrationSourceProvider ncdDetectorSourceProvider;
	private Label pxSaxsLabel;
	private Label pxWaxsLabel;

	private DoubleValidator doubleValidator = DoubleValidator.getInstance();

	public NcdDataReductionDetectorParameterPage() {
		super("Detector Parameters");
		setTitle("NCD Detector Parameters");
		setDescription("Select the NCD Detector Parameters");
		currentPageNumber = PAGENUMBER;
	}

	@Override
	public void createControl(Composite parent) {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		
		ncdWaxsSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXS_STATE);
		ncdSaxsSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXS_STATE);
		
		ncdWaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXSDETECTOR_STATE);
		ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		
		ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);

		ncdDetectorSourceProvider.addSourceProviderListener(this);
		ncdSaxsDetectorSourceProvider.addSourceProviderListener(this);
		ncdWaxsDetectorSourceProvider.addSourceProviderListener(this);

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
			Amount<Length> pxSize = detSettings.getPxSize();
			if (pxSize != null && pxWaxs != null && !(pxWaxs.isDisposed())) {
				String pxText = String.format("%.3f", pxSize.doubleValue(SI.MILLIMETRE));
				if (!(pxText.equals(pxWaxs.getText())))
					pxWaxs.setText(pxText);
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
						if (waxsPixel != null)
							detSettings.setPxSize(Amount.valueOf(waxsPixel, SI.MILLIMETRE));
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
			Amount<Length> pxSize = detSettings.getPxSize();
			if (pxSize != null && pxSaxs != null && !(pxSaxs.isDisposed())) {
				String pxText = String.format("%.3f", pxSize.doubleValue(SI.MILLIMETRE));
				if (!(pxText.equals(pxSaxs.getText())))
					pxSaxs.setText(pxText);
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
						if (saxsPixel != null)
							detSettings.setPxSize(Amount.valueOf(saxsPixel, SI.MILLIMETRE));
					}
				}
			}
		});
		
		if (ncdSaxsSourceProvider.isEnableSaxs()) detTypeSaxs.setSelection(true);
		else detTypeSaxs.setSelection(false);
		modeSelectionListenerSaxs.widgetSelected(null);

		setControl(container);
	}

	private Double getSaxsPixel() {
		String input = pxSaxs.getText();
		return doubleValidator.validate(input);
	}
	
	private Double getWaxsPixel() {
		String input = pxWaxs.getText();
		return doubleValidator.validate(input);
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
	public NcdProcessingSourceProvider getProvider() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setProvider(NcdProcessingSourceProvider provider) {
		// TODO Auto-generated method stub
		
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void sourceChanged(int sourcePriority, Map sourceValuesByName) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void sourceChanged(int sourcePriority, String sourceName, Object sourceValue) {
	}

	@Override
	public IWizardPage getNextPage() {
//		IWizard wizard = getWizard();
//		IWizardPage[] pages = wizard.getPages();
////		if(currentPageNumber == 0)
////			return super.getNextPage();
//		for (int i = currentPageNumber; i < pages.length; i++) {
//	
//			if(((INcdDataReductionWizardPage)pages[i]).isActive())
//				return pages[i];
//		}
		return super.getNextPage();
	}

	@Override
	public boolean isCurrentNcdWizardpage() {
		return isCurrentPage();
	}
}
