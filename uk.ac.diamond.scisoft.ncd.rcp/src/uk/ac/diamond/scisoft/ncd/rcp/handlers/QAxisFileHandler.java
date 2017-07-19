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
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;
import javax.measure.unit.UnitFormat;

import org.dawnsci.plotting.tools.diffraction.DiffractionDefaultMetadata;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.dawnsci.analysis.api.diffraction.DetectorProperties;
import org.eclipse.dawnsci.analysis.api.diffraction.DiffractionCrystalEnvironment;
import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.api.tree.Attribute;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.api.tree.Node;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.dawnsci.analysis.api.tree.Tree;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.statushandlers.StatusManager;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.ncd.calibration.rcp.views.NcdQAxisCalibration;
import uk.ac.diamond.scisoft.ncd.core.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.ncd.core.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;

public class QAxisFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(DetectorMaskFileHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		NcdCalibrationSourceProvider ncdCalibrationSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		NcdProcessingSourceProvider ncdEnergySourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ENERGY_STATE);

		final ISelection selection = HandlerUtil.getCurrentSelection(event);

		if (selection instanceof IStructuredSelection && ((IStructuredSelection)selection).toList().size() == 1) {
			
			final Object sel = ((IStructuredSelection)selection).getFirstElement();
			
			String qaxisFilename;
			if (sel instanceof IFile) {
				qaxisFilename = ((IFile)sel).getLocation().toString();
			} else {
				qaxisFilename = ((File)sel).getAbsolutePath();
			}
			try {
				
				String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
				if (detectorSaxs == null) {
					return errorDialog(NcdMessages.NO_SAXS_DETECTOR, null);
				}
				CalibrationResultsBean crb = null;
				
				Tree qaxisFile = new HDF5Loader(qaxisFilename).loadTree();
				NodeLink nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
						+ "_processing/SectorIntegration/qaxis calibration");
				
				if (nodeLink == null) {
					return errorDialog(NLS.bind(NcdMessages.NO_QAXIS_DATA, qaxisFilename), null);
				}
				
				Amount<ScatteringVectorOverDistance> amountGradient = null;
				Amount<ScatteringVector> amountIntercept = null;
				Unit<ScatteringVector> unit = SI.NANO(SI.METRE).inverse().asType(ScatteringVector.class);
				Amount<Length> cameraLength = null;
				Unit<Length> cameraLengthUnit = SI.MILLIMETRE;   // The default unit used for saving camera length value
				Amount<Energy> energy = null;
				Node node = nodeLink.getDestination();
				if (node instanceof DataNode) {
					Dataset qaxis = DatasetUtils.sliceAndConvertLazyDataset(((DataNode) node).getDataset());
					double gradient = qaxis.getDouble(0);
					double intercept = qaxis.getDouble(1);

					// The default value that was used when unit setting was fixed.
					UnitFormat unitFormat = UnitFormat.getUCUMInstance();
					Attribute unitsAttr = node.getAttribute("unit");
					if (unitsAttr != null) {
						String unitString = unitsAttr.getFirstElement();
						unit = unitFormat.parseProductUnit(unitString, new ParsePosition(0)).asType(ScatteringVector.class);
					}
					amountGradient = Amount.valueOf(gradient, unit.divide(SI.MILLIMETER).asType(ScatteringVectorOverDistance.class));
					amountIntercept = Amount.valueOf(intercept,  unit);
					
				} else if (node instanceof GroupNode) {
					Node gradientData = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
							+ "_processing/SectorIntegration/qaxis calibration/gradient").getDestination();
					Node gradientError = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
							+ "_processing/SectorIntegration/qaxis calibration/gradient_errors").getDestination();
					Node interceptData = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
							+ "_processing/SectorIntegration/qaxis calibration/intercept").getDestination();
					Node interceptError = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
							+ "_processing/SectorIntegration/qaxis calibration/intercept_errors").getDestination();
					
					double gradient = ((DataNode) gradientData).getDataset().getSlice().getDouble(0);
					double errgradient = ((DataNode) gradientError).getDataset().getSlice().getDouble(0);
					String strUnit = gradientData.getAttribute("units").getFirstElement();
					Unit<ScatteringVectorOverDistance> gradientUnit = UnitFormat.getUCUMInstance()
							.parseObject(strUnit, new ParsePosition(0)).asType(ScatteringVectorOverDistance.class);
					
					double intercept = ((DataNode) interceptData).getDataset().getSlice().getDouble(0);
					double erritercept = ((DataNode) interceptError).getDataset().getSlice().getDouble(0);
					strUnit = interceptData.getAttribute("units").getFirstElement();
					unit = UnitFormat.getUCUMInstance()
							.parseObject(strUnit, new ParsePosition(0)).asType(ScatteringVector.class);
					
					amountGradient = Amount.valueOf(gradient, errgradient, gradientUnit);
					amountIntercept = Amount.valueOf(intercept,  erritercept, unit);
				}
				
				if (amountGradient != null && amountIntercept != null) {
					nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
							+ "_processing/SectorIntegration/camera length");
					if (nodeLink != null) {
						node = nodeLink.getDestination();
						if (node instanceof DataNode) {
							double dataVal = ((DataNode) node).getDataset().getSlice().getDouble(0); 
							if (node.containsAttribute("units")) {
								cameraLengthUnit = Unit.valueOf(node.getAttribute("units").getFirstElement())
										.asType(Length.class);
							}
							cameraLength = Amount.valueOf(dataVal, cameraLengthUnit);
						} else if (node instanceof GroupNode) {
							Node data = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
									+ "_processing/SectorIntegration/camera length/data").getDestination();
							Node error = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
									+ "_processing/SectorIntegration/camera length/errors").getDestination();
							
							double dataVal = ((DataNode) data).getDataset().getSlice().getDouble(0); 
							double errorVal = ((DataNode) error).getDataset().getSlice().getDouble(0); 
							cameraLengthUnit = Unit.valueOf(data.getAttribute("units").getFirstElement())
										.asType(Length.class);
							cameraLength = Amount.valueOf(dataVal, errorVal, cameraLengthUnit);
						}
					}
					
					crb = new CalibrationResultsBean(detectorSaxs, amountGradient, amountIntercept, new ArrayList<CalibrationPeak>(), cameraLength, unit.inverse().asType(Length.class));
					ncdCalibrationSourceProvider.putCalibrationResult(crb);
					
					nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
							+ "_processing/SectorIntegration/energy");
					if (nodeLink != null) {
						node = nodeLink.getDestination();
						if (node instanceof DataNode) {
							energy = Amount.valueOf(
									((DataNode) node).getDataset().getSlice().getDouble(0), SI.KILO(NonSI.ELECTRON_VOLT));
						}
					}
					ncdEnergySourceProvider.setEnergy(energy);
				}

				SectorROI roiData = new SectorROI();
				roiData.setPlot(true);
				roiData.setClippingCompensation(true);
				nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
						+ "_processing/SectorIntegration/beam centre");
				if (nodeLink != null) {
					node = nodeLink.getDestination();
					if (node instanceof DataNode) {
						Dataset beam = DatasetUtils.sliceAndConvertLazyDataset(((DataNode) node).getDataset());
						roiData.setPoint(beam.getDouble(0), beam.getDouble(1));
					}
				}
				nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
						+ "_processing/SectorIntegration/integration angles");
				if (nodeLink != null) {
					node = nodeLink.getDestination();
					if (node instanceof DataNode) {
						Dataset angles = DatasetUtils.sliceAndConvertLazyDataset(((DataNode) node).getDataset());
						roiData.setAnglesDegrees(angles.getDouble(0), angles.getDouble(1));
					}
				}
				nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
						+ "_processing/SectorIntegration/integration radii");
				if (nodeLink != null) {
					node = nodeLink.getDestination();
					if (node instanceof DataNode) {
						Dataset radii = DatasetUtils.sliceAndConvertLazyDataset(((DataNode) node).getDataset());
						roiData.setRadii(radii.getDouble(0), radii.getDouble(1));
					}
				}
				nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
						+ "_processing/SectorIntegration/integration symmetry");
				if (nodeLink != null) {
					node = nodeLink.getDestination();
					if (node instanceof DataNode) {
						String symmetryText = ((DataNode) node).getDataset().getSlice().getString(0);
						int symmetry = SectorROI.getSymmetry(symmetryText);
						if (roiData.checkSymmetry(symmetry)) {
							roiData.setSymmetry(symmetry);
						}
					}
				}

				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IViewPart activePlot = page.findView(PlotView.ID + "DP");
				if (activePlot instanceof PlotView) {
					IPlottingSystem<?> plotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
					plotSystem.setPlotType(PlotType.IMAGE);
					IRegion sector = plotSystem.getRegion(NcdQAxisCalibration.SECTOR_NAME);
					if (sector != null) {
						plotSystem.removeRegion(sector);
					}
					sector = plotSystem.createRegion(NcdQAxisCalibration.SECTOR_NAME, RegionType.SECTOR);
					sector.setROI(roiData.copy());
					sector.setUserRegion(true);
					sector.setVisible(true);
					plotSystem.addRegion(sector);
				}
				
				// update locked diffraction metadata in Diffraction tool
				ILoaderService loaderService = Activator.getService(ILoaderService.class);
				IDiffractionMetadata lockedMeta = loaderService.getLockedDiffractionMetaData();
				if (lockedMeta == null) {
					nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs	+ "/data");
					if (nodeLink != null) {
						int[] dataShape = ((DataNode) nodeLink.getDestination()).getDataset().getShape();
						int[] imageShape = Arrays.copyOfRange(dataShape, Math.max(dataShape.length - 2, 0), dataShape.length);
						loaderService.setLockedDiffractionMetaData(DiffractionDefaultMetadata.getDiffractionMetadata(imageShape));
					} else {
						logger.info("SCISOFT NCD: Couldn't read calibration image shape for configuring diffraction metadata");
						return null;
					}
				}
				DetectorProperties detectorProperties = loaderService.getLockedDiffractionMetaData().getDetector2DProperties();
				DiffractionCrystalEnvironment crystalEnvironment = loaderService.getLockedDiffractionMetaData().getDiffractionCrystalEnvironment();
				if (energy != null) {
					crystalEnvironment.setWavelengthFromEnergykeV(energy.doubleValue(SI.KILO(NonSI.ELECTRON_VOLT)));
				}
				if (cameraLength != null) {
					detectorProperties.setDetectorDistance(cameraLength.doubleValue(SI.MILLIMETRE));
				}
				double[] cp = roiData.getPoint();
				if (cp != null) {
					detectorProperties.setBeamCentreCoords(cp);
				}
			} catch (Exception e) {
				return errorDialog(NLS.bind(NcdMessages.NO_QAXIS_DATA, qaxisFilename), null);
			}
		}

		return null;
	}

	private IStatus errorDialog(String msg, Exception e) {
		logger.error(msg, e);
		Status status = new Status(IStatus.ERROR, Activator.PLUGIN_ID, msg, e);
		StatusManager.getManager().handle(status, StatusManager.SHOW);
		return null;
	}
}
