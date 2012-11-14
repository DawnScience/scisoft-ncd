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

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optimization.ConvergenceChecker;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.SimplePointChecker;
import org.apache.commons.math3.optimization.direct.CMAESOptimizer;
import org.apache.commons.math3.random.Well19937a;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.services.ISourceProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.fitting.Fitter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.CompositeFunction;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Gaussian;
import uk.ac.diamond.scisoft.analysis.fitting.functions.IPeak;
import uk.ac.diamond.scisoft.analysis.optimize.GeneticAlg;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;

public class MultivariateFunctionWithMonitor implements MultivariateFunction {
	
	private IProgressMonitor monitor;
	private ArrayList<IPeak> initPeaks;
	
	private ArrayList<IPeak> peaks;
	
	private AbstractDataset dataset, mask;
	private SectorROI sroi;
	
	int cmaesLambda = 5;
	double[] cmaesInputSigma = new double[] { 3.0, 3.0 };
	int cmaesMaxIterations = 1000;
	int cmaesCheckFeasableCount = 10;
	ConvergenceChecker<PointValuePair> cmaesChecker = new SimplePointChecker<PointValuePair>(1e-4, 1e-2);
	
	private static final Logger logger = LoggerFactory.getLogger(MultivariateFunctionWithMonitor.class);
	
	private MultivariateFunctionSourceProvider beamxySourceProvider, peaksSourceProvider;
	
	public void setInitPeaks(ArrayList<IPeak> initPeaks) {
		this.initPeaks = initPeaks;
		peaks = new ArrayList<IPeak>(initPeaks);
	}

	public MultivariateFunctionWithMonitor(AbstractDataset dataset, AbstractDataset mask, SectorROI sroi) {
		super();
		this.dataset = dataset;
		this.mask = mask;
		this.sroi = sroi;
	}

	public void registerListeners(ISourceProviderService service) {
		beamxySourceProvider = (MultivariateFunctionSourceProvider) service.getSourceProvider(MultivariateFunctionSourceProvider.SECTORREFINEMENT_STATE);
		peaksSourceProvider = (MultivariateFunctionSourceProvider) service.getSourceProvider(MultivariateFunctionSourceProvider.PEAKREFINEMENT_STATE);
	}
	
	public void configureOptimizer(Integer cmaesLambda, double[] cmaesInputSigma, Integer cmaesMaxIterations, Integer cmaesCheckFeasableCount, ConvergenceChecker<PointValuePair> cmaesChecker) {
		if (cmaesLambda != null)
			this.cmaesLambda = cmaesLambda;
		if (cmaesInputSigma != null)
			this.cmaesInputSigma = cmaesInputSigma;
		if (cmaesMaxIterations != null)
			this.cmaesMaxIterations = cmaesMaxIterations;
		if (cmaesCheckFeasableCount != null)
			this.cmaesCheckFeasableCount = cmaesCheckFeasableCount;
		if (cmaesChecker != null)
			this.cmaesChecker = cmaesChecker;
	}
	
	private void setMonitor(IProgressMonitor monitor) {
		this.monitor = monitor;
	}
	
	@Override
	public double value(double[] beamxy) {
		
		if (monitor.isCanceled())
			return Double.NaN;
		
		sroi.setPoint(beamxy);
		sroi.setDpp(1.0);
		AbstractDataset[] intresult = ROIProfile.sector(dataset, mask, sroi, true, false, true);
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
				logger.error("Peak fitting failed", e);
				return Double.NaN;
			}

		}
		if (checkPeakOverlap(peaks))
			return Double.NaN;
		beamxySourceProvider.putBeamPosition(beamxy);
		peaksSourceProvider.putPeaks(peaks);
		logger.info("Error value for beam postion ({}, {}) is {}", new Object[] { beamxy[0], beamxy[1], error });

		return error;
	}
	
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
				if (dist < (fwhm1 + fwhm2) / 2.0)
					return true;
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
		Job job = new Job("Beam Position Refinement") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {

				function.setInitPeaks(initPeaks);
				setMonitor(monitor);

				CMAESOptimizer beamPosOptimizer = new CMAESOptimizer(cmaesLambda, cmaesInputSigma, cmaesMaxIterations,
						0.0, true, 0, cmaesCheckFeasableCount, new Well19937a(), false, cmaesChecker);
				final double[] newBeamPos = beamPosOptimizer.optimize(cmaesMaxIterations, function,
						GoalType.MAXIMIZE, startPosition).getPoint();
				
				value(newBeamPos);
				
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}
}