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

package uk.ac.diamond.scisoft.ncd.calibration.rcp.handlers;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.measure.unit.NonSI;
import javax.measure.unit.Unit;

import org.dawnsci.plotting.tools.masking.MaskingTool;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.PlottingFactory;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.trace.ILineTrace;
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
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Dataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.diffraction.DetectorProperties;
import uk.ac.diamond.scisoft.analysis.diffraction.DiffractionCrystalEnvironment;
import uk.ac.diamond.scisoft.analysis.diffraction.QSpace;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.io.DatLoader;
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.io.IDiffractionMetadata;
import uk.ac.diamond.scisoft.analysis.io.ILoaderService;
import uk.ac.diamond.scisoft.analysis.io.ScanFileHolderException;
import uk.ac.diamond.scisoft.analysis.roi.IROI;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile.XAxis;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.calibration.NCDAbsoluteCalibration;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;

public class NcdAbsoluteCalibrationListener extends SelectionAdapter {

	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.handlers.NcdAbsoluteCalibrationListener";

	private String referencePlotName;
	private String resultsPlotName;
	private String calibrant = "Glassy Carbon";
	private String dataFileName, emptyCellFileName;
	
	private static final Double I_H2O = 1.65e-2;
	
	public NcdAbsoluteCalibrationListener(String referencePlotName, String resultsPlotName) {
		super();
		this.referencePlotName = referencePlotName;
		this.resultsPlotName = resultsPlotName;
	}

	public void setCalibrant(String calibrant) {
		this.calibrant = calibrant;
	}

	public void setDataFileName(String dataFileName) {
		this.dataFileName = dataFileName;
	}

	public void setEmptyCellFileName(String emptyCellFileName) {
		this.emptyCellFileName = emptyCellFileName;
	}

	private AbstractDataset[] integrateInputData(ISourceProviderService service,
			String scaler,
			String detectorSaxs,
			SectorROI sroi,
			QSpace qSpace,
			String fileName) {
		final AbstractDataset imageDataset;
		final BooleanDataset mask;
		final NcdCalibrationSourceProvider ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
		try {
			HDF5File dataTree = new HDF5Loader(fileName).loadTree();
			HDF5NodeLink nodeLink = dataTree.findNodeLink("/entry1/" + scaler + "/data");
			NcdDetectorSettings scalerData = ncdDetectorSourceProvider.getNcdDetectors().get(scaler);
			Double norm = null;
			if (nodeLink != null) {
				Integer channel = scalerData.getNormChannel();
				IDataset normDataset = ((HDF5Dataset) nodeLink.getDestination()).getDataset().getSlice();
				final int[] normIdx = new int[normDataset.getRank()];
				Arrays.fill(normIdx, 0);
				normIdx[normIdx.length - 1] = channel;
				norm = ((HDF5Dataset) nodeLink.getDestination()).getDataset().getSlice().getDouble(normIdx);
			} else {
				Status status = new Status(IStatus.ERROR, ID,
						"Cannot find normalisation data. Please check normalisation dataset setting.");
				StatusManager.getManager().handle(status, StatusManager.SHOW);
				return null;
			}

			nodeLink = dataTree.findNodeLink("/entry1/" + detectorSaxs + "/data");
			if (nodeLink == null) {
				Status status = new Status(IStatus.ERROR, ID,
						"Cannot find calibrant image data. Please check detector settings.");
				StatusManager.getManager().handle(status, StatusManager.SHOW);
				return null;
			}

			// Open first frame if dataset has multiple images
			HDF5Dataset node = (HDF5Dataset) nodeLink.getDestination();
			final int[] shape = node.getDataset().getShape();

			final int[] sqShape = AbstractDataset.squeezeShape(shape, true);
			if (sqShape.length > 2) {
				Status status = new Status(
						IStatus.INFO,
						ID,
						"WARNING: This dataset contains several frames. By default, the first frame will be loaded for absolute intensity calibration.");
				StatusManager.getManager().handle(status, StatusManager.LOG);
			}

			final int[] start = new int[shape.length];
			final int[] stop = Arrays.copyOf(shape, shape.length);
			Arrays.fill(start, 0, shape.length, 0);
			if (shape.length > 2) {
				Arrays.fill(stop, 0, shape.length - 2, 1);
			}
			AbstractDataset imageIntDataset = (AbstractDataset) node.getDataset().getSlice(start, stop, null).clone()
					.squeeze();
			imageDataset = DatasetUtils.cast(imageIntDataset, Dataset.FLOAT32);
			imageDataset.idivide(norm);

			mask = MaskingTool.getSavedMask();

			AbstractDataset[] profile = ROIProfile.sector(imageDataset, mask, sroi, true, false, false, qSpace, XAxis.Q, false);
			AbstractDataset dataI = profile[0];
			AbstractDataset dataQDataset = profile[4];
			
			return new AbstractDataset[] {dataQDataset, dataI};
			
		} catch (Exception ex) {
			Status status = new Status(IStatus.ERROR, ID,
					"Cannot load image data. Please check file selection in the Project Explorer view and detector settings.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return null;
		}
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		
		final AbstractDataset glassyCarbonI;
		final List<Amount<ScatteringVector>> dataQ, absQ;
		
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		final ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		final NcdProcessingSourceProvider ncdSampleThicknessSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAMPLETHICKNESS_STATE);
		final NcdProcessingSourceProvider ncdAbsScaleSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.ABSSCALING_STATE);
		final NcdCalibrationSourceProvider ncdCalibrationSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		final NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
		final NcdProcessingSourceProvider ncdScalerSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SCALER_STATE);
		
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
			glassyCarbonI = data.getDataset(1);
		} catch (IOException er) {
			Status status = new Status(IStatus.ERROR, ID, "Could not find reference glassy carbon data", er);
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		} catch (ScanFileHolderException er) {
			Status status = new Status(IStatus.ERROR, ID, "Error loading reference glassy carbon data", er);
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}

		final Double thickness = ncdSampleThicknessSourceProvider.getSampleThickness();
		if (thickness == null || thickness.isInfinite() || thickness.isNaN() || !(thickness > 0.0)) {
			Status status = new Status(IStatus.ERROR, ID, "Invalid sample thickness. Please specify sample thickness in millimeters.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		IPlottingSystem plottingSystemRef = PlottingFactory.getPlottingSystem(referencePlotName);
		
		final String scaler = ncdScalerSourceProvider.getScaler();
		if (scaler == null) {
			Status status = new Status(IStatus.ERROR, ID, "Normalisation dataset not selected. Please load detector information.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		final String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
		if (detectorSaxs == null) {
			Status status = new Status(IStatus.ERROR, ID, "SAXS detector not selected. Please load detector information.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		CalibrationResultsBean crb = (CalibrationResultsBean) ncdCalibrationSourceProvider.getCurrentState().get(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		if (crb == null || !crb.containsKey(detectorSaxs)) {
			Status status = new Status(IStatus.ERROR, ID, "Couldn't find SAXS detector calibration data.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		final Unit<ScatteringVector> unit = NonSI.ANGSTROM.inverse().asType(ScatteringVector.class);
		
		dataQ = new ArrayList<Amount<ScatteringVector>>();
		
		final SectorROI sroi;
		Collection<IRegion> rois = plottingSystemRef.getRegions(RegionType.SECTOR);
		if (rois != null && !(rois.isEmpty())) {
			IROI tmpRoi = rois.iterator().next().getROI();			
			if (tmpRoi != null && (tmpRoi instanceof SectorROI)) {
				sroi = (SectorROI) tmpRoi;
			} else {
				Status status = new Status(IStatus.ERROR, ID, "Invalid input data. Please specify sector integration region.");
				StatusManager.getManager().handle(status, StatusManager.SHOW);
				return;
			}
		} else {
			Status status = new Status(IStatus.ERROR, ID, "Invalid input data. Sector region was not found.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		ILoaderService loaderService = (ILoaderService)PlatformUI.getWorkbench().getService(ILoaderService.class);
		IDiffractionMetadata dm = loaderService.getLockedDiffractionMetaData();
		final QSpace qSpace;
		
		if (dm != null) {
			DetectorProperties detprops = dm.getDetector2DProperties().clone();
	    	DiffractionCrystalEnvironment diffexp = dm.getDiffractionCrystalEnvironment().clone();
			qSpace = new QSpace(detprops, diffexp);
		} else {
			Status status = new Status(IStatus.ERROR, ID, "Invalid input data. Diffraction metadata was not found.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		Job job = new Job("Absolute Intensity Calibration") {
			
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				
				monitor.beginTask("Running Absolute Intensity Calibration", 2);
				monitor.subTask("Reading calibration data...");
				monitor.worked(1);
				
				AbstractDataset[] sampelData = integrateInputData(service, scaler, detectorSaxs, sroi, qSpace, dataFileName);
				AbstractDataset dataQDataset = sampelData[0];
				AbstractDataset dataI = sampelData[1];
				
				AbstractDataset[] emptyCellData = integrateInputData(service, scaler, detectorSaxs, sroi, qSpace, emptyCellFileName);
				AbstractDataset emptyI = emptyCellData[1];
				
				monitor.subTask("Calculating calibration parameters...");
				for (int idx = 0; idx < dataQDataset.getSize(); idx++) {
					double val = dataQDataset.getDouble(idx);
					dataQ.add(Amount.valueOf(val, unit));
				}
				
				final NCDAbsoluteCalibration ncdAbsoluteCalibration = new NCDAbsoluteCalibration();
				try {
					if (calibrant.equals("Glassy Carbon")) {
						ncdAbsoluteCalibration.setAbsoluteData(absQ, glassyCarbonI, unit);
					} else if (calibrant.equals("Water")) {
						AbstractDataset tmpI = AbstractDataset.ones(dataI.getShape(), AbstractDataset.FLOAT64);
						tmpI.imultiply(I_H2O);
						ncdAbsoluteCalibration.setAbsoluteData(dataQ, tmpI, unit);
					}
					ncdAbsoluteCalibration.setData(dataQ, dataI, emptyI, unit);
				} catch (Exception ex) {
					Status status = new Status(IStatus.ERROR, ID, "Error setting calibration procedure", ex);
					StatusManager.getManager().handle(status, StatusManager.SHOW);
					return status;
				}
				ncdAbsoluteCalibration.calibrate();
				
				Display.getDefault().syncExec(new Runnable() {
					
					@Override
					public void run() {
						IPlottingSystem plottingSystemRes = PlottingFactory.getPlottingSystem(resultsPlotName);
						plottingSystemRes.clear();
						
						ILineTrace refTrace = plottingSystemRes.createLineTrace("Reference Glassy Carbon Profile");
						refTrace.setData(ncdAbsoluteCalibration.getAbsQ(), ncdAbsoluteCalibration.getAbsI());
						refTrace.setTraceColor(ColorConstants.red);
						plottingSystemRes.addTrace(refTrace);
						
						ILineTrace calTrace = plottingSystemRes.createLineTrace("Calibrated Input Data");
						calTrace.setData(ncdAbsoluteCalibration.getDataQ(), ncdAbsoluteCalibration.getCalibratedI());
						calTrace.setTraceColor(ColorConstants.blue);
						plottingSystemRes.addTrace(calTrace);
						
						plottingSystemRes.getSelectedXAxis().setTitle("q / \u212b\u207b\u00b9");
						plottingSystemRes.getSelectedXAxis().setLog10(true);
						plottingSystemRes.getSelectedYAxis().setTitle("I(q) / cm\u207b\u00b9");
						plottingSystemRes.getSelectedYAxis().setLog10(true);
						plottingSystemRes.repaint();
						
						double absScale = ncdAbsoluteCalibration.getAbsoluteScale();
						ncdAbsScaleSourceProvider.setAbsScaling(absScale * thickness, true);
					}
				});
				monitor.done();	
				return Status.OK_STATUS;
			}
		};
		
		job.setUser(true);
		job.schedule();
	}
}
