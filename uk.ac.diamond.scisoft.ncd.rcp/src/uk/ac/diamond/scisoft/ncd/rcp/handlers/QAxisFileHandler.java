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

import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.plotting.api.PlottingFactory;
import org.dawnsci.plotting.api.region.IRegion;
import org.dawnsci.plotting.api.region.IRegion.RegionType;
import org.dawnsci.plotting.tools.diffraction.DiffractionDefaultMetadata;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
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

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.diffraction.DetectorProperties;
import uk.ac.diamond.scisoft.analysis.diffraction.DiffractionCrystalEnvironment;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Attribute;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Group;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Node;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.io.IDiffractionMetadata;
import uk.ac.diamond.scisoft.analysis.io.ILoaderService;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationPeak;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdQAxisCalibration;

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
				
				HDF5File qaxisFile = new HDF5Loader(qaxisFilename).loadTree();
				HDF5NodeLink nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
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
				HDF5Node node = nodeLink.getDestination();
				if (node instanceof HDF5Dataset) {
					AbstractDataset qaxis = (AbstractDataset) ((HDF5Dataset) node).getDataset().getSlice();
					double gradient = qaxis.getDouble(0);
					double intercept = qaxis.getDouble(1);

					// The default value that was used when unit setting was fixed.
					UnitFormat unitFormat = UnitFormat.getUCUMInstance();
					HDF5Attribute unitsAttr = node.getAttribute("unit");
					if (unitsAttr != null) {
						String unitString = unitsAttr.getFirstElement();
						unit = unitFormat.parseProductUnit(unitString, new ParsePosition(0)).asType(ScatteringVector.class);
					}
					amountGradient = Amount.valueOf(gradient, unit.divide(SI.MILLIMETER).asType(ScatteringVectorOverDistance.class));
					amountIntercept = Amount.valueOf(intercept,  unit);
					
				} else if (node instanceof HDF5Group) {
					HDF5Node gradientData = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
							+ "_processing/SectorIntegration/qaxis calibration/gradient").getDestination();
					HDF5Node gradientError = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
							+ "_processing/SectorIntegration/qaxis calibration/gradient_errors").getDestination();
					HDF5Node interceptData = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
							+ "_processing/SectorIntegration/qaxis calibration/intercept").getDestination();
					HDF5Node interceptError = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
							+ "_processing/SectorIntegration/qaxis calibration/intercept_errors").getDestination();
					
					double gradient = ((HDF5Dataset) gradientData).getDataset().getSlice().getDouble(0);
					double errgradient = ((HDF5Dataset) gradientError).getDataset().getSlice().getDouble(0);
					String strUnit = gradientData.getAttribute("units").getFirstElement();
					Unit<ScatteringVectorOverDistance> gradientUnit = UnitFormat.getUCUMInstance()
							.parseObject(strUnit, new ParsePosition(0)).asType(ScatteringVectorOverDistance.class);
					
					double intercept = ((HDF5Dataset) interceptData).getDataset().getSlice().getDouble(0);
					double erritercept = ((HDF5Dataset) interceptError).getDataset().getSlice().getDouble(0);
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
						if (node instanceof HDF5Dataset) {
							double dataVal = ((HDF5Dataset) node).getDataset().getSlice().getDouble(0); 
							if (node.containsAttribute("units")) {
								cameraLengthUnit = Unit.valueOf(node.getAttribute("units").getFirstElement())
										.asType(Length.class);
							}
							cameraLength = Amount.valueOf(dataVal, cameraLengthUnit);
						} else if (node instanceof HDF5Group) {
							HDF5Node data = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
									+ "_processing/SectorIntegration/camera length/data").getDestination();
							HDF5Node error = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
									+ "_processing/SectorIntegration/camera length/errors").getDestination();
							
							double dataVal = ((HDF5Dataset) data).getDataset().getSlice().getDouble(0); 
							double errorVal = ((HDF5Dataset) error).getDataset().getSlice().getDouble(0); 
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
						if (node instanceof HDF5Dataset) {
							energy = Amount.valueOf(
									((HDF5Dataset) node).getDataset().getSlice().getDouble(0), SI.KILO(NonSI.ELECTRON_VOLT));
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
					if (node instanceof HDF5Dataset) {
						AbstractDataset beam = (AbstractDataset) ((HDF5Dataset) node).getDataset().getSlice();
						roiData.setPoint(beam.getDouble(0), beam.getDouble(1));
					}
				}
				nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
						+ "_processing/SectorIntegration/integration angles");
				if (nodeLink != null) {
					node = nodeLink.getDestination();
					if (node instanceof HDF5Dataset) {
						AbstractDataset angles = (AbstractDataset) ((HDF5Dataset) node).getDataset().getSlice();
						roiData.setAnglesDegrees(angles.getDouble(0), angles.getDouble(1));
					}
				}
				nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
						+ "_processing/SectorIntegration/integration radii");
				if (nodeLink != null) {
					node = nodeLink.getDestination();
					if (node instanceof HDF5Dataset) {
						AbstractDataset radii = (AbstractDataset) ((HDF5Dataset) node).getDataset().getSlice();
						roiData.setRadii(radii.getDouble(0), radii.getDouble(1));
					}
				}
				nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs
						+ "_processing/SectorIntegration/integration symmetry");
				if (nodeLink != null) {
					node = nodeLink.getDestination();
					if (node instanceof HDF5Dataset) {
						String symmetryText = ((AbstractDataset) ((HDF5Dataset) node).getDataset()).getString(0);
						int symmetry = SectorROI.getSymmetry(symmetryText);
						if (roiData.checkSymmetry(symmetry)) {
							roiData.setSymmetry(symmetry);
						}
					}
				}

				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				IViewPart activePlot = page.findView(PlotView.ID + "DP");
				if (activePlot instanceof PlotView) {
					IPlottingSystem plotSystem = PlottingFactory.getPlottingSystem("Dataset Plot");
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
				ILoaderService loaderService = (ILoaderService)PlatformUI.getWorkbench().getService(ILoaderService.class);
				IDiffractionMetadata lockedMeta = loaderService.getLockedDiffractionMetaData();
				if (lockedMeta == null) {
					nodeLink = qaxisFile.findNodeLink("/entry1/" + detectorSaxs	+ "/data");
					if (nodeLink != null) {
						int[] dataShape = ((HDF5Dataset) nodeLink.getDestination()).getDataset().getShape();
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
