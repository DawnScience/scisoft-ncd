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

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Collection;

import gda.analysis.io.ScanFileHolderException;

import org.dawb.common.ui.plot.IPlottingSystem;
import org.dawb.common.ui.plot.PlottingFactory;
import org.dawb.common.ui.plot.trace.ILineTrace;
import org.dawb.common.ui.plot.trace.ITrace;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.statushandlers.StatusManager;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.io.DatLoader;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.ncd.calibration.NCDAbsoluteCalibration;

public class NcdAbsoluteCalibrationListener extends SelectionAdapter {

	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.handlers.NcdAbsoluteCalibrationListener"; //$NON-NLS-1$

	protected static final String PLOT_NAME = "Dataset Plot";
	
	private Text absScale;
	private Label absOffset;

	public void setAbsScaleWidgets(Text absScale, Label absOffset) {
		this.absScale = absScale;
		this.absOffset = absOffset;
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		
		final AbstractDataset absQ, absI;
		final AbstractDataset dataQ, dataI;
		try {
			URL fileURL = new URL("platform:/plugin/uk.ac.diamond.scisoft.ncd.rcp/data/Glassy Carbon L average.dat");
			
			DatLoader dataLoader = new DatLoader(FileLocator.resolve(fileURL).getPath());
			DataHolder data = dataLoader.loadFile();
			absQ = data.getDataset(0);
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
		
		ILineTrace dataTrace = (ILineTrace) tmpTrace;
		dataQ = dataTrace.getXData();
		dataI = dataTrace.getYData();
		
		final NCDAbsoluteCalibration ncdAbsoluteCalibration = new NCDAbsoluteCalibration();
		try {
			ncdAbsoluteCalibration.setAbsoluteData(absQ, absI);
			ncdAbsoluteCalibration.setData(dataQ, dataI);
		} catch (Exception ex) {
			Status status = new Status(IStatus.ERROR, ID, "Error setting calibration procedure", ex);
			StatusManager.getManager().handle(status, StatusManager.SHOW);
		}
		Job job = new Job("Absolute Intensity Calibration") {
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				final AbstractDataset resDataset = ncdAbsoluteCalibration.calibrate();
				Display.getDefault().syncExec(new Runnable() {
					
					@Override
					public void run() {
						IPlottingSystem plottingSystemRef = PlottingFactory.getPlottingSystem(PLOT_NAME);
						plottingSystemRef.clear();
						
						ILineTrace refTrace = plottingSystemRef.createLineTrace("Reference Glassy Carbon Profile");
						refTrace.setData(absQ, absI);
						refTrace.setTraceColor(ColorConstants.red);
						plottingSystemRef.addTrace(refTrace);
						
						ILineTrace edeTrace = plottingSystemRef.createLineTrace("Calibrated Input Data");
						edeTrace.setData(dataQ, resDataset);
						edeTrace.setTraceColor(ColorConstants.blue);
						plottingSystemRef.addTrace(edeTrace);
						
						plottingSystemRef.repaint();
						
						double[] polynom = ncdAbsoluteCalibration.getCalibrationPolynomial().getCoefficients();
						
					    DecimalFormat nForm = new DecimalFormat("0.#####");
					    DecimalFormat sForm = new DecimalFormat("0.#####E0");
						if (absScale != null && !(absScale.isDisposed())) {
							if (Math.abs(polynom[1]) > 1e5) {
								absScale.setText(sForm.format(polynom[1]));
							} else {
								absScale.setText(nForm.format(polynom[1]));
							}
						}
						
						if (absOffset != null && !(absOffset.isDisposed())) {
							if (Math.abs(polynom[0]) > 1e5) {
								absOffset.setText(sForm.format(polynom[0]));
							} else {
								absOffset.setText(nForm.format(polynom[0]));
							}
						}
					}
				});
					
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}
}
