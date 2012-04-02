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

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import java.io.File;
import java.util.ArrayList;

import javax.measure.quantity.Length;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Parameter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.StraightLine;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Node;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationPeak;
import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationResultsBean;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiPlotMode;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;
import uk.ac.diamond.scisoft.ncd.rcp.views.SaxsQAxisCalibration;

public class QAxisFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(DetectorMaskFileHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final ISelection selection = HandlerUtil.getCurrentSelection(event);

		if (selection instanceof IStructuredSelection) {
			if (((IStructuredSelection)selection).toList().size() == 1) {
				
				final Object sel = ((IStructuredSelection)selection).getFirstElement();
				
				String qaxisFilename;
				if (sel instanceof IFile)
					qaxisFilename = ((IFile)sel).getLocation().toString();
				else
					qaxisFilename = ((File)sel).getAbsolutePath();
				
				try {
					int idxSaxs = NcdDataReductionParameters.getDetListSaxs().getSelectionIndex();
					if (idxSaxs >= 0) {
						String detectorSaxs = NcdDataReductionParameters.getDetListSaxs().getItem(idxSaxs);
						HDF5File qaxisFile = new HDF5Loader(qaxisFilename).loadTree();
						HDF5Node node = qaxisFile.findNodeLink("/entry1/"+detectorSaxs+"_processing/SectorIntegration/qaxis calibration").getDestination();
						AbstractDataset qaxis = (AbstractDataset) ((HDF5Dataset)node).getDataset().getSlice();
						String units = (String)node.getAttribute("unit");
						if (units != null) {
							Unit<Length> inv_units = (Unit<Length>) Unit.valueOf(units).inverse();
							units = inv_units.toString();
						} else {
							// The default value that was used when unit setting was fixed.
							units = SI.NANO(SI.METER).toString(); 
						}
						node = qaxisFile.findNodeLink("/entry1/"+detectorSaxs+"_processing/SectorIntegration/camera length").getDestination();
						double cameraLength = Double.NaN;
						if (node instanceof HDF5Dataset)
							cameraLength = ((HDF5Dataset)node).getDataset().getSlice().getDouble(0);
						node = qaxisFile.findNodeLink("/entry1/"+detectorSaxs+"_processing/SectorIntegration/beam centre").getDestination();
						AbstractDataset beam = (AbstractDataset) ((HDF5Dataset)node).getDataset().getSlice();
						node = qaxisFile.findNodeLink("/entry1/"+detectorSaxs+"_processing/SectorIntegration/integration angles").getDestination();
						AbstractDataset angles = (AbstractDataset) ((HDF5Dataset)node).getDataset().getSlice();
						node = qaxisFile.findNodeLink("/entry1/"+detectorSaxs+"_processing/SectorIntegration/integration radii").getDestination();
						AbstractDataset radii = (AbstractDataset) ((HDF5Dataset)node).getDataset().getSlice();
						node = qaxisFile.findNodeLink("/entry1/"+detectorSaxs+"_processing/SectorIntegration/integration symmetry").getDestination();
						String symmetryText =  ((AbstractDataset)((HDF5Dataset)node).getDataset()).getString(0);
						int symmetry = SectorROI.getSymmetry(symmetryText);

						Parameter gradient = new Parameter(qaxis.getDouble(0));
						Parameter intercept = new Parameter(qaxis.getDouble(1));
						CalibrationResultsBean crb = new CalibrationResultsBean(detectorSaxs, new StraightLine(new Parameter[]{gradient, intercept}), new ArrayList<CalibrationPeak>(), cameraLength, units);

						SectorROI roiData = new SectorROI();
						roiData.setPlot(true);
						roiData.setAnglesDegrees(angles.getDouble(0), angles.getDouble(1));
						roiData.setRadii(radii.getDouble(0), radii.getDouble(1));
						roiData.setPoint(beam.getDouble(0),	beam.getDouble(1));
						roiData.setClippingCompensation(true);
						if (roiData.checkSymmetry(symmetry))
							roiData.setSymmetry(symmetry);
						try {
							IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
							IViewPart activePlot = page.findView(PlotView.ID + "DP");
							if (activePlot instanceof PlotView) {
								((PlotView)activePlot).putGUIInfo(GuiParameters.PLOTMODE, GuiPlotMode.TWOD);
								((PlotView)activePlot).putGUIInfo(GuiParameters.ROIDATA, roiData);
								((PlotView)activePlot).putGUIInfo(GuiParameters.CALIBRATIONFUNCTIONNCD, crb);
							}

							IViewPart saxsView = page.findView(SaxsQAxisCalibration.ID);
							if (saxsView instanceof SaxsQAxisCalibration)
								((SaxsQAxisCalibration)saxsView).updateResults(detectorSaxs);
						} catch (Exception e) {
							logger.error("SCISOFT NCD Q-Axis Calibration: cannot restore GUI bean information", e);
						}
						return Status.OK_STATUS;
					} 
					logger.error("SCISOFT NCD: No SAXS detector is selected");
					return Status.CANCEL_STATUS;

				} catch (Exception e) {
					logger.error("SCISOFT NCD: Failed to read qaxis values from " + qaxisFilename, e);
					return Status.CANCEL_STATUS;
				}
			}

		}

		return null;
	}

}
