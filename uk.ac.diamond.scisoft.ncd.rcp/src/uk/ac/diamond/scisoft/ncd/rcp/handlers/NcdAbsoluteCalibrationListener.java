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

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import gda.analysis.io.ScanFileHolderException;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.measure.unit.NonSI;
import javax.measure.unit.Unit;

import org.dawb.common.ui.plot.PlottingFactory;
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.axis.IAxis;
import org.dawnsci.plotting.api.trace.ILineTrace;
import org.dawnsci.plotting.api.trace.ITrace;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.statushandlers.StatusManager;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.Slice;
import uk.ac.diamond.scisoft.analysis.io.DatLoader;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.ncd.calibration.NCDAbsoluteCalibration;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;
//dascgitolite@dasc-git.diamond.ac.uk/scisoft/scisoft-ncd.git

public class NcdAbsoluteCalibrationListener extends SelectionAdapter {

	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.handlers.NcdAbsoluteCalibrationListener"; //$NON-NLS-1$

	protected static final String PLOT_NAME = "Dataset Plot";
	
	@Override
	public void widgetSelected(SelectionEvent e) {
		
		final AbstractDataset dataI, absI;
		final List<Amount<ScatteringVector>> dataQ, absQ;
		
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		final NcdProcessingSourceProvider ncdSampleThicknessSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAMPLETHICKNESS_STATE);
		final NcdProcessingSourceProvider ncdAbsScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSSCALING_STATE);
		final NcdProcessingSourceProvider ncdAbsOffsetSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSOFFSET_STATE);
		final NcdCalibrationSourceProvider ncdCalibrationSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		final NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		
		try {
			URL fileURL = new URL("platform:/plugin/uk.ac.diamond.scisoft.ncd.rcp/data/Glassy Carbon L average.dat");
			
			DatLoader dataLoader = new DatLoader(FileLocator.resolve(fileURL).getPath());
			DataHolder data = dataLoader.loadFile();
			
			AbstractDataset absQDataset = data.getDataset(0);
			absQ = new ArrayList<Amount<ScatteringVector>>();
			final IndexIterator it = absQDataset.getIterator();
			while (it.hasNext()) {
				double val = absQDataset.getDouble(it.index);
				absQ.add(Amount.valueOf(val, NonSI.ANGSTROM.inverse().asType(ScatteringVector.class)));
			}
			absI = data.getDataset(1);
		} catch (IOException er) {
			Status status = new Status(IStatus.ERROR, ID, "Could not find reference glassy carbon data", er);
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		} catch (ScanFileHolderException er) {
			Status status = new Status(IStatus.ERROR, ID, "Error loading reference glassy carbon data", er);
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}

		IPlottingSystem plottingSystemRef = PlottingFactory.getPlottingSystem(PLOT_NAME);
		IAxis selAxis = plottingSystemRef.getSelectedXAxis();
		double lowerAxis = selAxis.getLower();
		double upperAxis = selAxis.getUpper();
		
		Collection<ITrace> traces = plottingSystemRef.getTraces();
		ITrace tmpTrace = null;
		if (traces != null && !(traces.isEmpty())) {
			tmpTrace = traces.iterator().next();			
		}
		
		if (tmpTrace == null || !(tmpTrace instanceof ILineTrace)) {
			Status status = new Status(IStatus.ERROR, ID, "Invalid input data. Please plot integrated I(q) profile from a glassy carbon sample.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
		if (detectorSaxs == null) {
			Status status = new Status(IStatus.ERROR, ID, "SAXS detector not selected. Please load SAXS detector information.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		CalibrationResultsBean crb = (CalibrationResultsBean) ncdCalibrationSourceProvider.getCurrentState().get(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		if (crb == null || !crb.containsKey(detectorSaxs)) {
			Status status = new Status(IStatus.ERROR, ID, "Couldn't find SAXS detector calibration data.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		Unit<ScatteringVector> unit = crb.getUnit(detectorSaxs).inverse().asType(ScatteringVector.class);
		
		ILineTrace dataTrace = (ILineTrace) tmpTrace;
		dataQ = new ArrayList<Amount<ScatteringVector>>();
		AbstractDataset dataQDataset = dataTrace.getXData();
		int idxLower = Math.min(dataQDataset.getSize() - 1, DatasetUtils.findIndexGreaterThanOrEqualTo(dataQDataset, lowerAxis));
		int idxUpper = Math.min(dataQDataset.getSize() - 1, DatasetUtils.findIndexGreaterThanOrEqualTo(dataQDataset, upperAxis));
		if (idxLower == idxUpper) {
			Status status = new Status(IStatus.ERROR, ID, "Invalid data range. Please check plot settings");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		for (int idx = idxLower; idx < idxUpper; idx++) {
			double val = dataQDataset.getDouble(idx);
			dataQ.add(Amount.valueOf(val, unit));
		}
		dataI = dataTrace.getYData().getSlice(new Slice(idxLower, idxUpper));
		
		final NCDAbsoluteCalibration ncdAbsoluteCalibration = new NCDAbsoluteCalibration();
		try {
			ncdAbsoluteCalibration.setAbsoluteData(absQ, absI, unit);
			ncdAbsoluteCalibration.setData(dataQ, dataI, unit);
		} catch (Exception ex) {
			Status status = new Status(IStatus.ERROR, ID, "Error setting calibration procedure", ex);
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		Job job = new Job("Absolute Intensity Calibration") {
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				ncdAbsoluteCalibration.calibrate();
				Display.getDefault().syncExec(new Runnable() {
					
					@Override
					public void run() {
						IPlottingSystem plottingSystemRef = PlottingFactory.getPlottingSystem(PLOT_NAME);
						plottingSystemRef.clear();
						
						ILineTrace refTrace = plottingSystemRef.createLineTrace("Reference Glassy Carbon Profile");
						refTrace.setData(ncdAbsoluteCalibration.getAbsQ(), absI);
						refTrace.setTraceColor(ColorConstants.red);
						plottingSystemRef.addTrace(refTrace);
						
						ILineTrace edeTrace = plottingSystemRef.createLineTrace("Calibrated Input Data");
						edeTrace.setData(ncdAbsoluteCalibration.getDataQ(), ncdAbsoluteCalibration.getCalibratedI());
						edeTrace.setTraceColor(ColorConstants.blue);
						plottingSystemRef.addTrace(edeTrace);
						
						plottingSystemRef.repaint();
						
						double[] polynom = ncdAbsoluteCalibration.getCalibrationPolynomial().getCoefficients();
						Double thickness = ncdSampleThicknessSourceProvider.getSampleThickness();
						if (thickness == null || thickness.isInfinite() || thickness.isNaN() || !(thickness > 0.0)) {
							Status status = new Status(IStatus.ERROR, ID, "Invalid sample thickness. Please specify sample thickness in millimeters.");
							StatusManager.getManager().handle(status, StatusManager.SHOW);
							return;
						}
						ncdAbsScaleSourceProvider.setAbsScaling(polynom[1] * thickness, true);
						ncdAbsOffsetSourceProvider.setAbsOffset(polynom[0]);
					}
				});
					
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}
}
