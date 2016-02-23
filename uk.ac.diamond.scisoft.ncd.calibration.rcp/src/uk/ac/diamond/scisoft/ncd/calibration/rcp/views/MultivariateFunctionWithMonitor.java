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

package uk.ac.diamond.scisoft.ncd.calibration.rcp.views;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.SimplePointChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.random.Well19937a;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.dawnsci.analysis.api.fitting.functions.IParameter;
import org.eclipse.dawnsci.analysis.api.fitting.functions.IPeak;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.roi.LinearROI;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.ui.services.ISourceProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.fitting.Fitter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.CompositeFunction;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Gaussian;
import uk.ac.diamond.scisoft.analysis.optimize.GeneticAlg;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;

public class MultivariateFunctionWithMonitor implements MultivariateFunction {
	
	private IProgressMonitor monitor;
	private ArrayList<IPeak> initPeaks;
	
	private Dataset dataset, mask;
	private SectorROI sroi;
	private double maxFWHM;
	
	private String jobName = "Beam Position Refinement";
	
	private int cmaesLambda = 5;
	private double[] cmaesInputSigma = new double[] { 3.0, 3.0 };
	private int cmaesMaxIterations = 1000;
	private int cmaesCheckFeasableCount = 10;
	private ConvergenceChecker<PointValuePair> cmaesChecker = new SimplePointChecker<PointValuePair>(1e-4, 1e-2);
	
	private static final Logger logger = LoggerFactory.getLogger(MultivariateFunctionWithMonitor.class);
	
	private MultivariateFunctionSourceProvider beamxySourceProvider, peaksSourceProvider;
	
	public void setInitPeaks(ArrayList<IPeak> initPeaks) {
		this.initPeaks = new ArrayList<IPeak>(initPeaks);
		maxFWHM = -Double.MAX_VALUE;
		for (IPeak peak : initPeaks) {
			if (peak.getFWHM() > maxFWHM) {
				maxFWHM = peak.getFWHM();
			}
		}
	}

	public MultivariateFunctionWithMonitor(Dataset dataset, Dataset mask, SectorROI sroi) {
		super();
		this.dataset = dataset;
		this.mask = mask;
		this.sroi = sroi;
	}

	public void addSourceProviders(ISourceProviderService service) {
		if (service != null) {
			beamxySourceProvider = (MultivariateFunctionSourceProvider) service.getSourceProvider(MultivariateFunctionSourceProvider.SECTORREFINEMENT_STATE);
			peaksSourceProvider = (MultivariateFunctionSourceProvider) service.getSourceProvider(MultivariateFunctionSourceProvider.PEAKREFINEMENT_STATE);
		}
	}
	
	public void configureOptimizer(Integer cmaesLambda, double[] cmaesInputSigma, Integer cmaesMaxIterations, Integer cmaesCheckFeasableCount, ConvergenceChecker<PointValuePair> cmaesChecker) {
		if (cmaesLambda != null) {
			this.cmaesLambda = cmaesLambda;
		}
		if (cmaesInputSigma != null) {
			this.cmaesInputSigma = Arrays.copyOf(cmaesInputSigma, cmaesInputSigma.length);
		}
		if (cmaesMaxIterations != null) {
			this.cmaesMaxIterations = cmaesMaxIterations;
		}
		if (cmaesCheckFeasableCount != null) {
			this.cmaesCheckFeasableCount = cmaesCheckFeasableCount;
		}
		if (cmaesChecker != null) {
			this.cmaesChecker = cmaesChecker;
		}
	}
	
	private void setMonitor(IProgressMonitor monitor) {
		this.monitor = monitor;
	}
	
	@Override
	public double value(double[] beamxy) {
		
		LinearROI dist = new LinearROI(beamxy, sroi.getPoint());
		if (monitor.isCanceled() || dist.getLength() > 3.0 * maxFWHM)
			return Double.NaN;
		
		ArrayList<IPeak> peaks = new ArrayList<IPeak>(initPeaks.size());
		
		SectorROI tmpRoi = new SectorROI(beamxy[0], beamxy[1], sroi.getRadius(0), sroi.getRadius(1), sroi.getAngle(0), sroi.getAngle(1), 1.0, true, sroi.getSymmetry());
		Dataset[] intresult = ROIProfile.sector(dataset, mask, tmpRoi, true, false, true);
		Dataset axis = DatasetFactory.createLinearSpace(tmpRoi.getRadius(0), tmpRoi.getRadius(1),
				intresult[0].getSize(), Dataset.INT32);
		double error = 0.0;
		for (int idx = 0; idx < initPeaks.size(); idx++) {
			IPeak peak = initPeaks.get(idx);
			// logger.info("idx {} peak start position {}", idx, peak.getParameterValues());
			double pos = peak.getPosition();
			double fwhm = peak.getFWHM();
			int startIdx = DatasetUtils.findIndexGreaterThanOrEqualTo(axis, pos - fwhm);
			int stopIdx = DatasetUtils.findIndexGreaterThanOrEqualTo(axis, pos + fwhm) + 1;

			Dataset axisSlice = axis.getSlice(new int[] { startIdx }, new int[] { stopIdx }, null);
			Dataset peakSlice = intresult[0].getSlice(new int[] { startIdx }, new int[] { stopIdx },
					null);
			try {
				CompositeFunction peakFit = Fitter.fit(axisSlice, peakSlice, new GeneticAlg(0.0001),
						peak.copy());
				//CompositeFunction peakFit = Fitter.fit(axisSlice, peakSlice, new GeneticAlg(0.0001),
				//		new PseudoVoigt(peak.getParameters()));
				peak = createNewPeak(peakFit.getParameters());
				//logger.info("idx {} peak fitting result {}", idx, peakFit.getParameterValues());
				peaks.add(peak);
				error += Math.log(1.0 + peak.getHeight() / peak.getFWHM());
				//error += (Double) peakSlice.max();// peak.getHeight();// / peak.getFWHM();
			} catch (Exception e) {
				logger.error("Peak fitting failed", e);
				return Double.NaN;
			}

		}
		//if (checkPeakOverlap(peaks))
		//	return Double.NaN;
		
		if (beamxySourceProvider != null) {
			beamxySourceProvider.putBeamPosition(beamxy);
		}
		if (peaksSourceProvider != null) {
			peaksSourceProvider.putPeaks(peaks);
		}
		
		logger.info("Error value for beam postion ({}, {}) is {}", new Object[] { beamxy[0], beamxy[1], error });
		return error;
	}
	
	private IPeak createNewPeak(IParameter[] parameters) throws Exception {
		IParameter posn = null, fwhm = null, area = null;
		Gaussian newPeak = new Gaussian();
		for (IParameter parameter : parameters) {
			if (parameter.getName().equals("posn")) {
				posn = parameter;
			}
			else if (parameter.getName().equals("fwhm") || parameter.getName().equals("g_fwhm")) {
				fwhm = parameter;
			}
			else if (parameter.getName().equals("area")) {
				area = parameter;
			}
		}
		if (posn == null || fwhm == null || area == null) {
			throw new Exception("position, fwhm, or area not defined");
		}
		newPeak.setParameter(0, posn);
		newPeak.setParameter(1, fwhm);
		newPeak.setParameter(2, area);

		return newPeak;
	}

	@SuppressWarnings("unused")
	private boolean checkPeakOverlap(ArrayList<IPeak> peaks) {
		if (peaks.size() < 2)
			return false;
		for (int i = 0; i < peaks.size() - 1; i++) {
			IPeak peak1 = peaks.get(i);
			for (int j = i + 1; j < peaks.size(); j++) {
				IPeak peak2 = peaks.get(j);
				double dist = Math.abs(peak2.getPosition() - peak1.getPosition());
				double fwhm1 = peak1.getFWHM();
				double fwhm2 = peak2.getFWHM();
				if (dist < Math.max(fwhm1, fwhm2) / 2.0) {
					logger.info("Peaks {} and {} overlap ", new Object[] { peak1, peak2 });
					return true;
				}
			}
		}
		return false;
	}
	
	public void optimize(final double[] startPosition) {
		final int cmaesLambda = this.cmaesLambda;
		final double[] cmaesInputSigma = this.cmaesInputSigma;
		final int cmaesMaxIterations = this.cmaesMaxIterations;
		final int cmaesCheckFeasableCount = this.cmaesCheckFeasableCount;
		final ConvergenceChecker<PointValuePair> cmaesChecker = this.cmaesChecker;
		final MultivariateFunctionWithMonitor function = this;
		Job job = new Job(jobName) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {

				function.setInitPeaks(initPeaks);
				function.setMonitor(monitor);

				CMAESOptimizer beamPosOptimizer = new CMAESOptimizer(
						cmaesMaxIterations,
						0.0,
						true,
						0,
						cmaesCheckFeasableCount,
						new Well19937a(),
						false,
						cmaesChecker);
				final PointValuePair res = beamPosOptimizer.optimize(new MaxEval(cmaesMaxIterations),
						new ObjectiveFunction(function),
						GoalType.MAXIMIZE,
						new CMAESOptimizer.PopulationSize(cmaesLambda),
						new CMAESOptimizer.Sigma(cmaesInputSigma),
						SimpleBounds.unbounded(2),
						new InitialGuess(startPosition));
				final double[] newBeamPos = res.getPoint();
				logger.info("Optimiser terminated at beam position ({}, {}) with the value {}", new Object[] { newBeamPos[0], newBeamPos[1], res.getValue() });
				// Run calculation with optimised beam center to update UI 
				function.value(newBeamPos);
				
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}
}