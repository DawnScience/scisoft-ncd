/*-
 * Copyright © 2011 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.rcp.views;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.IParameter;
import org.eclipse.core.commands.Parameterization;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.progress.UIJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationPeak;
import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationResultsBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.rcp.plotting.actions.InjectPyDevConsoleHandler;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.preferences.CalibrationPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.gda.common.rcp.util.BundleUtils;

import gda.analysis.functions.APeak;
import gda.analysis.functions.StraightLine;
import gda.analysis.functions.Parameter;

public class NcdQAxisCalibration extends QAxisCalibrationBase {
	
	private IMemento memento;

	private static final Logger logger = LoggerFactory.getLogger(NcdQAxisCalibration.class);
	
	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
	 this.memento = memento;
	 super.init(site, memento);
	}
	
	@Override
	public void createPartControl(Composite parent) {
		SAXS_PLOT_NAME = "uk.ac.diamond.scisoft.analysis.rcp.plotViewDP";
		WAXS_PLOT_NAME = "uk.ac.diamond.scisoft.analysis.rcp.plotViewDP";
		GUI_PLOT_NAME = "Dataset Plot";

		super.createPartControl(parent);
		
		modeSelectionListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				findViewAndDetermineMode(currentMode);
				return;
			}
		};
		
		restoreState();
	}

	@Override
	public void saveState(IMemento memento) {
		
		if (memento != null) {
			
			memento.putString(CalibrationPreferences.QAXIS_CURRENTMODE, currentMode);
			memento.putString(CalibrationPreferences.QAXIS_ACIVEPLOT, ACTIVE_PLOT);
			
			memento.putString(CalibrationPreferences.QAXIS_GRADIENT, gradient.getText());
			memento.putString(CalibrationPreferences.QAXIS_INTERCEPT, intercept.getText());
			memento.putString(CalibrationPreferences.QAXIS_CAMERALENGTH, cameralength.getText());
			
			memento.putInteger(CalibrationPreferences.QAXIS_STANDARD, standard.getSelectionIndex());
			memento.putInteger(CalibrationPreferences.QAXIS_MAXBRAGGORDER, braggOrder.getSelection());
			
			if (!(calibrationPeakList.isEmpty())) {
				IMemento calibrationPeaksMemento = memento.createChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
				for (CalibrationPeak peak: calibrationPeakList) {
					IMemento calibrationPeakMemento = calibrationPeaksMemento.createChild(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
					calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_PEAKPOS, (float) peak.getPeakPos());
					calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_TWOTHETA, (float) peak.getTwoTheta());
					calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_DSPACING, (float) peak.getDSpacing());
					calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_H, peak.getIndex("h"));
					calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_K, peak.getIndex("k"));
					calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_L, peak.getIndex("l"));
				}
			}
			
			try {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IViewPart activePlot = page.findView(ACTIVE_PLOT);
				if (activePlot instanceof PlotView) {
					GuiBean guiinfo = ((PlotView)page.showView(ACTIVE_PLOT)).getGUIInfo();

					if (guiinfo.containsKey(GuiParameters.ROIDATA)) {
						if (guiinfo.get(GuiParameters.ROIDATA) instanceof SectorROI) {
							SectorROI intSector = (SectorROI)guiinfo.get(GuiParameters.ROIDATA);
							IMemento roiMemento = memento.createChild(CalibrationPreferences.QAXIS_ROI);
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROISR, (float) intSector.getRadius(0));
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIER, (float) intSector.getRadius(1));
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROISP, (float) intSector.getAngle(0));
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIEP, (float) intSector.getAngle(1));
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIPTX, (float) intSector.getPoint()[0]);
							roiMemento.putFloat(CalibrationPreferences.QAXIS_ROIPTY, (float) intSector.getPoint()[1]);
						}
					}

					if (guiinfo.containsKey(GuiParameters.CALIBRATIONFUNCTIONNCD)) {
						Serializable bd = guiinfo.get(GuiParameters.CALIBRATIONFUNCTIONNCD);
						IMemento crbMemento = memento.createChild(CalibrationPreferences.QAXIS_CRB);

						if (bd != null && bd instanceof CalibrationResultsBean) {
							CalibrationResultsBean crb = (CalibrationResultsBean) bd;
							for (String key: crb.keySet()) {
								IMemento crbDataMemento = crbMemento.createChild(CalibrationPreferences.QAXIS_CRBDATA, key);

								crbDataMemento.putFloat(CalibrationPreferences.QAXIS_GRADIENT,
										(float) crb.getFuction(key).getParameter(0).getValue());
								crbDataMemento.putFloat(CalibrationPreferences.QAXIS_INTERCEPT,
										(float) crb.getFuction(key).getParameter(1).getValue());

								crbDataMemento.putFloat(CalibrationPreferences.QAXIS_CAMERALENGTH,
										(float) crb.getMeanCameraLength(key));

								IMemento calibrationPeaksMemento = crbDataMemento.createChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
								for (CalibrationPeak peak: crb.getPeakList(key)) {
									IMemento calibrationPeakMemento = calibrationPeaksMemento.createChild(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
									calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_PEAKPOS, (float) peak.getPeakPos());
									calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_TWOTHETA, (float) peak.getTwoTheta());
									calibrationPeakMemento.putFloat(CalibrationPreferences.QAXIS_DSPACING, (float) peak.getDSpacing());
									calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_H, peak.getIndex("h"));
									calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_K, peak.getIndex("k"));
									calibrationPeakMemento.putInteger(CalibrationPreferences.QAXIS_L, peak.getIndex("l"));
								}

							}

						}
					}
				}
			} catch (PartInitException e) {
				logger.error("SCISOFT NCD Q-Axis Calibration: cannot save GUI bean information", e);
			}
		}
	}
	
	private void restoreState() {
		if (this.memento != null) {
			String tmp;
			Integer val;
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_CURRENTMODE);
			if (tmp != null) currentMode = tmp;
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_ACIVEPLOT);
			if (tmp != null) ACTIVE_PLOT = tmp;
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_GRADIENT);
			if (tmp != null) gradient.setText(tmp);
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_INTERCEPT);
			if (tmp != null) intercept.setText(tmp);
			
			tmp = this.memento.getString(CalibrationPreferences.QAXIS_CAMERALENGTH);
			if (tmp != null) cameralength.setText(tmp);
			
			val = this.memento.getInteger(CalibrationPreferences.QAXIS_STANDARD);
			if (val != null) standard.select(val);
			
			val = this.memento.getInteger(CalibrationPreferences.QAXIS_MAXBRAGGORDER);
			if (val != null) braggOrder.setSelection(val);
			
			IMemento calibrationPeaksMemento = this.memento.getChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
			if (calibrationPeaksMemento != null) {
				IMemento[] peaks = calibrationPeaksMemento.getChildren(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
				calibrationPeakList = new ArrayList<CalibrationPeak>();
				for (IMemento peak: peaks) {
					Float peakPos = peak.getFloat(CalibrationPreferences.QAXIS_PEAKPOS);
					Float tTheta = peak.getFloat(CalibrationPreferences.QAXIS_TWOTHETA);
					Float dSpacing = peak.getFloat(CalibrationPreferences.QAXIS_DSPACING);
					Integer h = peak.getInteger(CalibrationPreferences.QAXIS_H);
					Integer k = peak.getInteger(CalibrationPreferences.QAXIS_K);
					Integer l = peak.getInteger(CalibrationPreferences.QAXIS_L);
					calibrationPeakList.add(new CalibrationPeak(peakPos, tTheta, dSpacing, new int[] {h,k,l}));
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
					
					Parameter gradient = new Parameter(data.getFloat(CalibrationPreferences.QAXIS_GRADIENT));
					Parameter intercept = new Parameter(data.getFloat(CalibrationPreferences.QAXIS_INTERCEPT));
					Float meanCameraLength = data.getFloat(CalibrationPreferences.QAXIS_CAMERALENGTH);
					
					IMemento dataPeaksMemento = data.getChild(CalibrationPreferences.QAXIS_ARRAYCALIBRATIONPEAK);
					ArrayList<CalibrationPeak> dataPeakList = new ArrayList<CalibrationPeak>();
					if (dataPeaksMemento != null) {
						IMemento peaks[] = dataPeaksMemento.getChildren(CalibrationPreferences.QAXIS_CALIBRATIONPEAK);
						for (IMemento peak: peaks) {
							Float peakPos = peak.getFloat(CalibrationPreferences.QAXIS_PEAKPOS);
							Float tTheta = peak.getFloat(CalibrationPreferences.QAXIS_TWOTHETA);
							Float dSpacing = peak.getFloat(CalibrationPreferences.QAXIS_DSPACING);
							Integer h = peak.getInteger(CalibrationPreferences.QAXIS_H);
							Integer k = peak.getInteger(CalibrationPreferences.QAXIS_K);
							Integer l = peak.getInteger(CalibrationPreferences.QAXIS_L);
							dataPeakList.add(new CalibrationPeak(peakPos, tTheta, dSpacing, new int[] {h,k,l}));
						}
					}
					
					crb.putCalibrationResult(key, new StraightLine(new Parameter[]{gradient, intercept}), dataPeakList, meanCameraLength);
				}

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
			}
			try {
				IWorkbenchPage page = this.getViewSite().getWorkbenchWindow().getActivePage();
				IViewPart activePlot = page.findView(ACTIVE_PLOT);
				if (activePlot != null) {
					if (roiData != null)
						((PlotView)activePlot).putGUIInfo(GuiParameters.ROIDATA, roiData);
					if (crb != null) {
						((PlotView)activePlot).putGUIInfo(GuiParameters.CALIBRATIONFUNCTIONNCD, crb);
						updateCalibrationResults(crb);
					}
				}
			} catch (Exception e) {
				logger.error("SCISOFT NCD Q-Axis Calibration: cannot restore GUI bean information", e);
			}

		}
	}

	@Override
	protected void runJythonCommand() {
		
		currentMode = getDetectorName();

		final int n = braggOrder.getSelection();
		final String lambdanm, mmpp;
		try {
			lambdanm = "(1e-3 * 1e9 * 4.13566733e-15 * 299792458) / " + NcdDataReductionParameters.getEnergy().toString();
			mmpp = getPixel(true).toString();
		} catch (Exception e) {
			logger.error("SCISOFT NCD: Error reading data reduction parameters", e);
			Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, "Failed to read energy or detector pixel size parameter from NCD Q axis calibration view.");
			ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Input parameter error", "Please check that energy and detector pixel size parameters are properly set.", status);
			return;
			
		}
		
		final StringBuilder command = new StringBuilder();
		try {
			File pythonPlugin = new File(BundleUtils.getBundleLocation("uk.ac.diamond.scisoft.ncd.rcp").getAbsolutePath());
			command.append("sys.path.append(\"".concat(new File(pythonPlugin, "scripts").getAbsolutePath()).concat("\");"));
			pythonPlugin = new File(BundleUtils.getBundleLocation("uk.ac.diamond.scisoft.ncd").getAbsolutePath());
			command.append("sys.path.append(\"".concat(new File(pythonPlugin, "scripts").getAbsolutePath()).concat("\");"));
		} catch (IOException e) {
			String msg = "SCISOFT NCD: Error configuring Jython console";
			logger.error(msg, e);
			Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, e.getMessage());
			ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Jython console configuration error", msg, status);
			return;
		}
		command.append("import calibrateDetector as ca;");

		command.append("ca.calibrate(");

		command.append(String.format("\"%s\"", getDetectorName()));
		command.append(",");

		command.append(String.format("\"%s\"", GUI_PLOT_NAME));
		command.append(",None,");
		
		command.append("[");

		for (Iterator<APeak> iterator = peaks.iterator(); iterator.hasNext();) {
			APeak peak = iterator.next();
			command.append(String.format("%5.5g", peak.getPosition()));
			if (iterator.hasNext()) {
				command.append(",");
			}
		}

		command.append("],");

		command.append("[");

		for (Map.Entry<String, Double>  peak : cal2peaks.get(standard.getText()).entrySet()) {
			command.append(String.format("(%s, %5.5g),", peak.getKey(), peak.getValue()));
		}

		command.append("],");

		command.append(lambdanm);
		command.append(",");

		command.append(mmpp);
		command.append(",");

		command.append(n);
		command.append(",");

		if (originalData2D)
			command.append(disttobeamstop);
		else 
			command.append("None");

		command.append(")\n");
		
		UIJob job = new UIJob("Calibration") {
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				try {
					ICommandService commandService = (ICommandService) PlatformUI.getWorkbench().getService(ICommandService.class);
					IHandlerService handlerService = (IHandlerService) PlatformUI.getWorkbench().getService(IHandlerService.class);

					Command inject = commandService.getCommand(InjectPyDevConsoleHandler.COMMAND_ID);

					IParameter script = inject.getParameter(InjectPyDevConsoleHandler.INJECT_COMMANDS_PARAM);
					Parameterization parm = new Parameterization(script, command.toString());
					ParameterizedCommand command = new ParameterizedCommand(inject, new Parameterization[] {parm});
					handlerService.executeCommand(command, null);

					if (NcdDataReductionParameters.getDetListSaxs().isEnabled())
						NcdDataReductionParameters.getDetListSaxs().notifyListeners(SWT.Selection, null);
					//if (NcdDataReductionParameters.getDetListWaxs().isEnabled())
					//	NcdDataReductionParameters.getDetListWaxs().notifyListeners(SWT.Selection, null);
				} catch (Exception err) {
					String msg = "SCISOFT NCD: Error running q-axis calibration script";
					logger.error(msg, err);
					Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, err.getMessage());
					ErrorDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Q-axis calibration script error", msg, status);
					return Status.CANCEL_STATUS;
				}
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();
	}

	protected String getDetectorName() {
		//  Override in subclass to refer to the calibrated detector
		return null;
	}

	@SuppressWarnings("unused")
	protected Double getPixel(boolean b) {
		// Override in subclass to return the appropriate pixel size
		return null;
	}
	
	public void updateResults(String detector) {
		GuiBean guiinfo;
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			IViewPart activePlot = page.findView(ACTIVE_PLOT);
			if (activePlot != null) {
				guiinfo = ((PlotView)activePlot).getGUIInfo();

				if (guiinfo.containsKey(GuiParameters.CALIBRATIONFUNCTIONNCD)) {
					Serializable bd = guiinfo.get(GuiParameters.CALIBRATIONFUNCTIONNCD);

					if (bd != null && bd instanceof CalibrationResultsBean) {
						CalibrationResultsBean crb = (CalibrationResultsBean) bd;
						if (crb.keySet().contains(detector)) {
							currentMode = detector;
							updateCalibrationResults(crb);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.info("No GUI bean information available");
		}
	}
}
