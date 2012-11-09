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

import java.util.ArrayList;
import java.util.List;

import javax.measure.quantity.Length;
import javax.measure.unit.Unit;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.dawb.common.ui.plot.AbstractPlottingSystem;
import org.dawb.common.ui.plot.PlottingFactory;
import org.dawb.common.ui.plot.region.IRegion.RegionType;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.progress.UIJob;
import org.uncommons.maths.combinatorics.CombinationGenerator;

import uk.ac.diamond.scisoft.analysis.crystallography.CalibrationStandards;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.fitting.Fitter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.CompositeFunction;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Gaussian;
import uk.ac.diamond.scisoft.analysis.fitting.functions.IPeak;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Parameter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.StraightLine;
import uk.ac.diamond.scisoft.analysis.optimize.GeneticAlg;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.calibration.CalibrationMethods;
import uk.ac.diamond.scisoft.ncd.rcp.views.QAxisCalibrationBase.StoredPlottingObject;

class MultivariateFunctionWithMonitor implements MultivariateFunction {
	
	/**
	 * 
	 */
	//private final NcdQAxisCalibration ncdQAxisCalibration;
	private IProgressMonitor monitor;
	private IJobManager jobManager;
	private ArrayList<IPeak> initPeaks;
	
	ArrayList<IPeak> peaks;
	StoredPlottingObject twoDData;
	String calibrant;
	
	private double lambda;
	private double mmpp;
	private Unit<Length> unitScale;

	public void setInitPeaks(ArrayList<IPeak> initPeaks) {
		this.initPeaks = initPeaks;
	}

	public void setLambda(double lambda) {
		this.lambda = lambda;
	}

	public void setMmpp(double mmpp) {
		this.mmpp = mmpp;
	}

	public void setUnitScale(Unit<Length> unitScale) {
		this.unitScale = unitScale;
	}

	public MultivariateFunctionWithMonitor(IProgressMonitor monitor) {
		super();
		//this.ncdQAxisCalibration = ncdQAxisCalibration;
		this.monitor = monitor;
		jobManager = Job.getJobManager();
	}

	@Override
	public double value(double[] beamxy) {
		
		if (monitor.isCanceled())
			return Double.NaN;
		
		final SectorROI sroi = twoDData.getROI();
		sroi.setPoint(beamxy);
		sroi.setDpp(1.0);
		AbstractDataset[] intresult = ROIProfile.sector((AbstractDataset) twoDData.getStoredDataset(),
				twoDData.getMask(), sroi, true, false, true);
		AbstractDataset axis = DatasetUtils.linSpace(sroi.getRadius(0), sroi.getRadius(1),
				intresult[0].getSize(), AbstractDataset.INT32);
		double error = 0.0;
		for (int idx = 0; idx < peaks.size(); idx++) {
			IPeak peak = initPeaks.get(idx);
			// logger.info("idx {} peak start position {}", idx, peak.getParameterValues());
			double pos = peak.getPosition();
			double fwhm = peak.getFWHM() / 2.0;
			int startIdx = DatasetUtils.findIndexGreaterThanorEqualTo(axis, pos - fwhm);
			int stopIdx = DatasetUtils.findIndexGreaterThanorEqualTo(axis, pos + fwhm) + 1;

			AbstractDataset axisSlice = axis.getSlice(new int[] { startIdx }, new int[] { stopIdx }, null);
			AbstractDataset peakSlice = intresult[0].getSlice(new int[] { startIdx }, new int[] { stopIdx },
					null);
			try {
				CompositeFunction peakFit = Fitter.fit(axisSlice, peakSlice, new GeneticAlg(0.0001),
						new Gaussian(peak.getParameters()));
				//CompositeFunction peakFit = Fitter.fit(axisSlice, peakSlice, new GeneticAlg(0.0001),
				//		new PseudoVoigt(peak.getParameters()));
				peak.setParameterValues(peakFit.getParameterValues());
				//logger.info("idx {} peak fitting result {}", idx, peakFit.getParameterValues());
				peaks.set(idx, peak);
				error += Math.log(1.0 + peak.getHeight() / peak.getFWHM());
				//error += (Double) peakSlice.max();// peak.getHeight();// / peak.getFWHM();
			} catch (Exception e) {
				NcdQAxisCalibration.logger.error("Peak fitting failed", e);
				return Double.NaN;
			}

		}
		if (checkPeakOverlap(peaks))
			return Double.NaN;
		NcdQAxisCalibration.logger.info("Error value for beam postion ({}, {}) is {}", new Object[] { beamxy[0], beamxy[1], error });

		final String jobName = "Sector plot";
		UIJob plotingJob = new UIJob(jobName) {
			
			@Override
			public boolean belongsTo(Object family) {
				return family == jobName;
			}
		      
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				try {
					AbstractPlottingSystem plotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
					plotSystem.getRegions(RegionType.SECTOR).iterator().next().setROI(sroi);
					
					CalibrationMethods calibrationMethod = new CalibrationMethods(peaks,
					CalibrationStandards.getCalibrationPeakMap(calibrant), lambda, mmpp, unitScale);
					calibrationMethod.performCalibration(true);
					Parameter gradient = new Parameter(calibrationMethod.getFitResult()[1]);
					Parameter intercept = new Parameter(calibrationMethod.getFitResult()[0]);
					StraightLine calibrationFunction = new StraightLine(new Parameter[] { gradient, intercept});
					
					//TODO: Set up listeners to update GUI elements
					//NcdQAxisCalibration.plotCalibrationResults(calibrationFunction, calibrationMethod.getIndexedPeakList());

					return Status.OK_STATUS;
					
				} catch (Exception e) {
					NcdQAxisCalibration.logger.error("Error updating plot view", e);
					return Status.CANCEL_STATUS;
				}
			}
		};
		
		jobManager.cancel(jobName);
		plotingJob.schedule();

		return error;
	}
	
	private boolean checkPeakOverlap(ArrayList<IPeak> peaks) {
		if (peaks.size() < 2)
			return false;
		CombinationGenerator<IPeak> peakPair = new CombinationGenerator<IPeak>(peaks, peaks.size());
		for (List<IPeak> pair : peakPair) {
			IPeak peak1 = pair.get(0);
			IPeak peak2 = pair.get(1);
			double dist = Math.abs(peak2.getPosition() - peak1.getPosition());
			double fwhm1 = peak1.getFWHM();
			double fwhm2 = peak2.getFWHM();
			if (dist < (fwhm1 + fwhm2) / 2.0)
				return true;
		}
		return false;
	}
}