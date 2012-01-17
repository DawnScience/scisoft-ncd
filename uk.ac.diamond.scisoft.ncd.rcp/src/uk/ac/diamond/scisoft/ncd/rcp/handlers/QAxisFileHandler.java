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

import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeBuilder;
import gda.data.nexus.tree.NexusTreeNodeSelection;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;

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
import org.xml.sax.InputSource;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Parameter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.StraightLine;
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
						INexusTree detectorTree = NexusTreeBuilder.getNexusTree(qaxisFilename, getDetectorSelection(detectorSaxs));
						INexusTree node = detectorTree.getNode("entry1/"+detectorSaxs+"_processing/SectorIntegration/qaxis calibration");
						AbstractDataset qaxis = Nexus.createDataset(node.getData(), false);
						String units = (String)node.getAttribute("unit");
						if (units == null)
							units = "nm^-1";
						node = detectorTree.getNode("entry1/"+detectorSaxs+"_processing/SectorIntegration/camera length");
						double cameraLength = Double.NaN;
						if (node != null)
							cameraLength = Nexus.createDataset(node.getData(), false).getDouble(new int[] {0});
						node = detectorTree.getNode("entry1/"+detectorSaxs+"_processing/SectorIntegration/beam centre");
						AbstractDataset beam = Nexus.createDataset(node.getData(), false);
						node = detectorTree.getNode("entry1/"+detectorSaxs+"_processing/SectorIntegration/integration angles");
						AbstractDataset angles = Nexus.createDataset(node.getData(), false);
						node = detectorTree.getNode("entry1/"+detectorSaxs+"_processing/SectorIntegration/integration radii");
						AbstractDataset radii = Nexus.createDataset(node.getData(), false);
						node = detectorTree.getNode("entry1/"+detectorSaxs+"_processing/SectorIntegration/integration symmetry");
						String symmetryText = new String((byte[]) node.getData().getBuffer(), "UTF-8");
						int symmetry = SectorROI.getSymmetry(symmetryText);

						Parameter gradient = new Parameter(qaxis.getDouble(new int[] {0}));
						Parameter intercept = new Parameter(qaxis.getDouble(new int[] {1}));
						CalibrationResultsBean crb = new CalibrationResultsBean(detectorSaxs, new StraightLine(new Parameter[]{gradient, intercept}), new ArrayList<CalibrationPeak>(), cameraLength, units);

						SectorROI roiData = new SectorROI();
						roiData.setPlot(true);
						roiData.setAngles(angles.getDouble(new int[] {0}), angles.getDouble(new int[] {1}));
						roiData.setRadii(radii.getDouble(new int[] {0}), radii.getDouble(new int[] {1}));
						roiData.setPoint(beam.getDouble(new int[] {0}),	beam.getDouble(new int[] {1}));
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

	private NexusTreeNodeSelection getDetectorSelection(String detName) throws Exception {
		String xml = "<?xml version='1.0' encoding='UTF-8'?>" +
		"<nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>NXentry</nxClass><wanted>1</wanted><dataType>1</dataType>" +
		"<nexusTreeNodeSelection><nxClass>NXinstrument</nxClass><name>"+ detName + "_processing</name><wanted>1</wanted><dataType>1</dataType>" +
		"<nexusTreeNodeSelection><nxClass>NXdetector</nxClass><name>SectorIntegration</name><wanted>1</wanted><dataType>1</dataType>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>qaxis calibration</name><wanted>1</wanted><dataType>2</dataType>" +
		"</nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>camera length</name><wanted>1</wanted><dataType>2</dataType>" +
		"</nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>beam centre</name><wanted>1</wanted><dataType>2</dataType>" +
		"</nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>integration angles</name><wanted>1</wanted><dataType>2</dataType>" +
		"</nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>integration radii</name><wanted>1</wanted><dataType>2</dataType>" +
		"</nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>integration symmetry</name><wanted>1</wanted><dataType>2</dataType>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>";
		return NexusTreeNodeSelection.createFromXML(new InputSource(new StringReader(xml)));
	}
}
