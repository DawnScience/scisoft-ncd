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

import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;

import javax.measure.quantity.Angle;
import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;
import javax.measure.unit.UnitFormat;

import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.plotting.api.PlottingFactory;
import org.dawnsci.plotting.api.region.IRegion;
import org.dawnsci.plotting.api.region.IRegion.RegionType;
import org.dawnsci.plotting.api.trace.ColorOption;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISourceProviderListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;
import org.jscience.physics.amount.Amount;
import org.jscience.physics.amount.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.crystallography.HKL;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.roi.IROI;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.preferences.CalibrationPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;

public class NcdQAxisCalibration extends QAxisCalibrationBase implements ISourceProviderListener{
	
	private IMemento memento;
	
	protected IJobManager jobManager;
	
	protected IPlottingSystem plottingSystem;
	public static final  String SECTOR_NAME = "Calibration";

	private static final Logger logger = LoggerFactory.getLogger(NcdQAxisCalibration.class);
	
	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		this.memento = memento;
		super.init(site, memento);

		jobManager = Job.getJobManager();
	}
	
	@Override
	public void createPartControl(Composite parent) {

		super.createPartControl(parent);
		
		try {
	        this.plottingSystem = PlottingFactory.createPlottingSystem();
	        plottingSystem.setColorOption(ColorOption.NONE);
		} catch (Exception e) {
			logger.error("Cannot locate any plotting systems!", e);
		}
		
		final Composite plot = new Composite(parent, SWT.NONE);
		plot.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		plot.setLayout(new FillLayout());
		try {
	        IActionBars wrapper = this.getViewSite().getActionBars();
			plottingSystem.createPlotPart(plot, "Calibration Plot", wrapper, PlotType.XY, this);

		} catch (Exception e) {
			logger.error("Error creating plot part.", e);
		}
		
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				restoreState();
			}
		});
	}

	@Override
	public void saveState(IMemento memento) {
		
		if (memento != null) {
			
//			memento.putString(CalibrationPreferences.QAXIS_CURRENTMODE, currentDetector);
			memento.putString(CalibrationPreferences.QAXIS_ACIVEPLOT, GUI_PLOT_NAME);
			
			memento.putString(CalibrationPreferences.QAXIS_GRADIENT, gradient.getText());
			memento.putString(CalibrationPreferences.QAXIS_INTERCEPT, intercept.getText());
			memento.putString(CalibrationPreferences.QAXIS_CAMERALENGTH, cameralength.getText());
			
			memento.putInteger(CalibrationPreferences.QAXIS_STANDARD, standard.getSelectionIndex());
			
			Unit<Length> selUnit = SI.NANO(SI.METRE);
			for (Entry<Unit<Length>, Button> unitBtn : unitSel.entrySet())
				if (unitBtn.getValue().getSelection()) {
					selUnit = unitBtn.getKey();
					memento.putString(CalibrationPreferences.QAXIS_UNITS, selUnit.toString());
					break;
				}
			
			Amount<Energy> energy = getEnergy();
			if (energy != null) {
				memento.putFloat(CalibrationPreferences.QAXIS_ENERGY, new Float(energy.doubleValue(SI.KILO(NonSI.ELECTRON_VOLT))));
			}
			
			if (!(calibrationPeakList.isEmpty())) {
				IMemento calibrationPeaksMemento = memento.createChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
				for (CalibrationPeak peak: calibrationPeakList) {
					IMemento calibrationPeakMemento = calibrationPeaksMemento.createChild(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
					calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_PEAKPOS, (float) peak.getPeakPos());
					calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_TWOTHETA, (float) peak.getTwoTheta().doubleValue(NonSI.DEGREE_ANGLE));
					calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_DSPACING, (float) peak.getDSpacing().doubleValue(selUnit));
					HKL idx = peak.getReflection();
					calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_H, idx.getH());
					calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_K, idx.getK());
					calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_L, idx.getL());
				}
			}
			
			CalibrationResultsBean crb = (CalibrationResultsBean) ncdCalibrationSourceProvider.getCurrentState().get(NcdCalibrationSourceProvider.CALIBRATION_STATE);
			if (!crb.keySet().isEmpty()) {
				IMemento crbMemento = memento.createChild(CalibrationPreferences.QAXIS_CRB);
				for (String key : crb.keySet()) {
					IMemento crbDataMemento = crbMemento.createChild(CalibrationPreferences.QAXIS_CRBDATA, key);

					crbDataMemento.putFloat(CalibrationPreferences.QAXIS_GRADIENT, (float) crb.getGradient(key).getEstimatedValue());
					crbDataMemento.putFloat(CalibrationPreferences.QAXIS_GRADIENT_ERROR, (float) crb.getGradient(key).getAbsoluteError());
					String unitGradient = UnitFormat.getUCUMInstance().format(crb.getGradient(key).getUnit());
					crbDataMemento.putString(CalibrationPreferences.QAXIS_GRADIENT_UNIT, unitGradient);
					crbDataMemento.putFloat(CalibrationPreferences.QAXIS_INTERCEPT, (float) crb.getIntercept(key).getEstimatedValue());
					crbDataMemento.putFloat(CalibrationPreferences.QAXIS_INTERCEPT_ERROR, (float) crb.getIntercept(key).getAbsoluteError());
					String unitIntercept = UnitFormat.getUCUMInstance().format(crb.getIntercept(key).getUnit());
					crbDataMemento.putString(CalibrationPreferences.QAXIS_INTERCEPT_UNIT, unitIntercept);

					Amount<Length> mcl = crb.getMeanCameraLength(key);
					if (mcl != null) {
						crbDataMemento.putString(CalibrationPreferences.QAXIS_CAMERALENGTH, mcl.to(SI.METRE).toString());
					}
					ArrayList<CalibrationPeak> calPeaks = crb.getPeakList(key);
					if (calPeaks != null) {
						IMemento calibrationPeaksMemento = crbDataMemento.createChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
						for (CalibrationPeak peak : calPeaks) {
							IMemento calibrationPeakMemento = calibrationPeaksMemento.createChild(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
							calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_PEAKPOS,
									(float) peak.getPeakPos());
							calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_TWOTHETA,
									(float) peak.getTwoTheta().doubleValue(NonSI.DEGREE_ANGLE));
							calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_DSPACING,
									(float) peak.getDSpacing().doubleValue(selUnit));
							HKL idx = peak.getReflection();
							calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_H, idx.getH());
							calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_K, idx.getK());
							calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_L, idx.getL());
						}
					}
				}
			}

			IPlottingSystem plotSystem = PlottingFactory.getPlottingSystem(GUI_PLOT_NAME);
			Collection<IRegion> sectorRegions = plotSystem.getRegions(RegionType.SECTOR);
			if (sectorRegions != null && !(sectorRegions.isEmpty())) {
				IROI intBase = sectorRegions.iterator().next().getROI();
				if (intBase instanceof SectorROI) {
					SectorROI intSector = (SectorROI) intBase;
					IMemento roiMemento = memento.createChild(CalibrationPreferences.QAXIS_ROI);
					roiMemento.putFloat(CalibrationPreferences.QAXIS_ROISR, (float) intSector.getRadius(0));
					roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIER, (float) intSector.getRadius(1));
					roiMemento.putFloat(CalibrationPreferences.QAXIS_ROISP, (float) intSector.getAngle(0));
					roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIEP, (float) intSector.getAngle(1));
					roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIPTX, (float) intSector.getPoint()[0]);
					roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIPTY, (float) intSector.getPoint()[1]);
					roiMemento.putInteger(CalibrationPreferences.QAXIS_ROISYM, intSector.getSymmetry());
				}
			}
		}
	}
	
	private void restoreState() {
		if (this.memento != null) {
			String tmp;
			Integer val;
			Float flt;
			
//			tmp = this.memento.getString(CalibrationPreferences.QAXIS_CURRENTMODE);
//			if (tmp != null) {
//				currentDetector = tmp;
//			}
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_ACIVEPLOT);
			if (tmp != null) {
				GUI_PLOT_NAME = tmp;
			}
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_GRADIENT);
			if (tmp != null) {
				gradient.setText(tmp);
			}
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_INTERCEPT);
			if (tmp != null) {
				intercept.setText(tmp);
			}
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_CAMERALENGTH);
			if (tmp != null) {
				cameralength.setText(tmp);
			}
			
			val = this.memento.getInteger(CalibrationPreferences.QAXIS_STANDARD);
			if (val != null) {
				standard.select(val);
			}
			
			Unit<Length> selUnit = SI.NANO(SI.METRE);
			String units = this.memento.getString(CalibrationPreferences.QAXIS_UNITS);
			if (units != null) { 
				selUnit = Unit.valueOf(units).asType(Length.class);
			}
			for (Entry<Unit<Length>, Button> unitBtn : unitSel.entrySet())
				if (unitBtn.getKey().equals(selUnit)) {
					unitBtn.getValue().setSelection(true);
				} else {
					unitBtn.getValue().setSelection(false);
				}
			flt = memento.getFloat(CalibrationPreferences.QAXIS_ENERGY);
			if (flt != null) {
				energy.setText(flt.toString());
				ncdEnergySourceProvider.setEnergy(Amount.valueOf(flt, SI.KILO(NonSI.ELECTRON_VOLT)));
			}
			
			IMemento calibrationPeaksMemento = this.memento.getChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
			if (calibrationPeaksMemento != null) {
				IMemento[] peaks = calibrationPeaksMemento.getChildren(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
				calibrationPeakList = new ArrayList<CalibrationPeak>();
				for (IMemento peak: peaks) {
					float peakPos = peak.getFloat(CalibrationPreferences.QAXIS_PEAKPOS);
					Amount<Angle> tTheta = Amount.valueOf(peak.getFloat(CalibrationPreferences.QAXIS_TWOTHETA), NonSI.DEGREE_ANGLE);
					float dSpacing = peak.getFloat(CalibrationPreferences.QAXIS_DSPACING);
					int h = peak.getInteger(CalibrationPreferences.QAXIS_H);
					int k = peak.getInteger(CalibrationPreferences.QAXIS_K);
					int l = peak.getInteger(CalibrationPreferences.QAXIS_L);
					HKL hkl = new HKL(h, k, l, Amount.valueOf(dSpacing, selUnit));
					calibrationPeakList.add(new CalibrationPeak(peakPos, tTheta, hkl));
				}
			}
			setCalTable(calibrationPeakList);
				
			IMemento crbMemento = this.memento.getChild(CalibrationPreferences.QAXIS_CRB);
			CalibrationResultsBean crb = null;
			
			if (crbMemento != null) {
				
				crb = new CalibrationResultsBean();
				IMemento crbDataMemento[] = crbMemento.getChildren(CalibrationPreferences.QAXIS_CRBDATA);
				
				for (IMemento data: crbDataMemento) {
					
					String key = data.getID();
					
					Float amountGradient = data.getFloat(CalibrationPreferences.QAXIS_GRADIENT);
					Float errorGradient = data.getFloat(CalibrationPreferences.QAXIS_GRADIENT_ERROR);
					String unitStrGradient = data.getString(CalibrationPreferences.QAXIS_GRADIENT_UNIT);
					Unit<ScatteringVectorOverDistance> unitGradient = UnitFormat.getUCUMInstance().parseObject(unitStrGradient, new ParsePosition(0)).asType(ScatteringVectorOverDistance.class);
					Amount<ScatteringVectorOverDistance> valGradient = Amount.valueOf(amountGradient, errorGradient, unitGradient);
					Float amountIntercept = data.getFloat(CalibrationPreferences.QAXIS_INTERCEPT);
					Float errorIntercept = data.getFloat(CalibrationPreferences.QAXIS_INTERCEPT_ERROR);
					String unitStrIntercept = data.getString(CalibrationPreferences.QAXIS_INTERCEPT_UNIT);
					Unit<ScatteringVector> unitIntercept = UnitFormat.getUCUMInstance().parseObject(unitStrIntercept, new ParsePosition(0)).asType(ScatteringVector.class);
					Amount<ScatteringVector> valIntercept = Amount.valueOf(amountIntercept, errorIntercept, unitIntercept);
					tmp = data.getString(CalibrationPreferences.QAXIS_CAMERALENGTH);
					Amount<Length> meanCameraLength = null;
					if (tmp != null) {
						// JScience can't parse brackets
						tmp = tmp.replace("(", "").replace(")", "");
						meanCameraLength = Amount.valueOf(tmp).to(SI.METRE);
					}
					
					IMemento dataPeaksMemento = data.getChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
					ArrayList<CalibrationPeak> dataPeakList = new ArrayList<CalibrationPeak>();
					if (dataPeaksMemento != null) {
						IMemento peaks[] = dataPeaksMemento.getChildren(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
						for (IMemento peak: peaks) {
							float peakPos = peak.getFloat(CalibrationPreferences.QAXIS_PEAKPOS);
							Amount<Angle> tTheta = Amount.valueOf(peak.getFloat(CalibrationPreferences.QAXIS_TWOTHETA), NonSI.DEGREE_ANGLE);
							float dSpacing = peak.getFloat(CalibrationPreferences.QAXIS_DSPACING);
							int h = peak.getInteger(CalibrationPreferences.QAXIS_H);
							int k = peak.getInteger(CalibrationPreferences.QAXIS_K);
							int l = peak.getInteger(CalibrationPreferences.QAXIS_L);
							HKL hkl = new HKL(h, k, l, Amount.valueOf(dSpacing, selUnit));
							dataPeakList.add(new CalibrationPeak(peakPos, tTheta, hkl));
						}
					}
					
					crb.putCalibrationResult(key, valGradient, valIntercept, dataPeakList, meanCameraLength, selUnit);
				}
				
				ncdCalibrationSourceProvider.putCalibrationResult(crb);
			}
			
			IMemento roiMemento = this.memento.getChild(CalibrationPreferences.QAXIS_ROI);
			SectorROI roiData = null;
			if (roiMemento != null) {
				roiData = new SectorROI();
				roiData.setPlot(true);
				roiData.setAngles(roiMemento.getFloat(CalibrationPreferences.QAXIS_ROISP),
						roiMemento.getFloat(CalibrationPreferences.QAXIS_ROIEP));
				roiData.setRadii(roiMemento.getFloat(CalibrationPreferences.QAXIS_ROISR),
						roiMemento.getFloat(CalibrationPreferences.QAXIS_ROIER));
				roiData.setPoint(roiMemento.getFloat(CalibrationPreferences.QAXIS_ROIPTX),
						roiMemento.getFloat(CalibrationPreferences.QAXIS_ROIPTY));
				roiData.setSymmetry(roiMemento.getInteger(CalibrationPreferences.QAXIS_ROISYM));
				try {
					IPlottingSystem plotSystem = PlottingFactory.getPlottingSystem(GUI_PLOT_NAME);
					if (plotSystem != null) {
						plotSystem.setPlotType(PlotType.IMAGE);
						IRegion sector = plotSystem.createRegion(SECTOR_NAME, RegionType.SECTOR);
						sector.setROI(roiData.copy());
						sector.setUserRegion(true);
						sector.setVisible(true);
						plotSystem.addRegion(sector);
					}
				} catch (Exception e) {
					logger.error("SCISOFT NCD Q-Axis Calibration: cannot restore GUI bean information", e);
				}
			}

		}
	}

	protected Amount<Length> getLambda() {
		Amount<Length> lambdaDim = Constants.ℎ.times(Constants.c)
				.divide(getEnergy().to(SI.KILO(NonSI.ELECTRON_VOLT))).to(Length.UNIT);
		return lambdaDim;
	}

	@Override
	protected Amount<Energy> getEnergy() {
		return ncdEnergySourceProvider.getEnergy();
	}
	
	protected String getDetectorName() {
		return null;
	}
	
	@Override
	protected void updateCalibrationResults() {
		updateCalibrationResults(getDetectorName());
	}
	
	protected Amount<Length> getPixel() {
		return null;
	}
	
	protected CalibrationResultsBean getNcdCalibrationResults() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		NcdCalibrationSourceProvider ncdCalibrationSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		CalibrationResultsBean crb = (CalibrationResultsBean) ncdCalibrationSourceProvider.getCurrentState().get(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		return crb;
	}
}
