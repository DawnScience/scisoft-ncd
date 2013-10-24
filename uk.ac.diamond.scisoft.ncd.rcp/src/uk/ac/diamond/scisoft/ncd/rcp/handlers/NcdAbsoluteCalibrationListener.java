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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.measure.unit.NonSI;
import javax.measure.unit.Unit;

import org.dawb.common.services.ILoaderService;
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlottingFactory;
import org.dawnsci.plotting.api.region.IRegion;
import org.dawnsci.plotting.api.region.IRegion.RegionType;
import org.dawnsci.plotting.api.trace.IImageTrace;
import org.dawnsci.plotting.api.trace.ILineTrace;
import org.dawnsci.plotting.api.trace.ITrace;
import org.dawnsci.plotting.tools.masking.MaskingTool;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.draw2d.ColorConstants;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.navigator.resources.ProjectExplorer;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.statushandlers.StatusManager;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
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
import uk.ac.diamond.scisoft.analysis.roi.IROI;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile.XAxis;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.calibration.NCDAbsoluteCalibration;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class NcdAbsoluteCalibrationListener extends SelectionAdapter {

	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.handlers.NcdAbsoluteCalibrationListener";

	private String referencePlotName;
	private String resultsPlotName;
	
	private AbstractDataset imageDataset, mask;

	private String dataFileName;
	
	public NcdAbsoluteCalibrationListener(String referencePlotName, String resultsPlotName) {
		super();
		this.referencePlotName = referencePlotName;
		this.resultsPlotName = resultsPlotName;
	}

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
		final NcdCalibrationSourceProvider ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
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

		final Double thickness = ncdSampleThicknessSourceProvider.getSampleThickness();
		if (thickness == null || thickness.isInfinite() || thickness.isNaN() || !(thickness > 0.0)) {
			Status status = new Status(IStatus.ERROR, ID, "Invalid sample thickness. Please specify sample thickness in millimeters.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		IPlottingSystem plottingSystemRef = PlottingFactory.getPlottingSystem(referencePlotName);
		
		ISelectionService selService = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getSelectionService();
		String pluginName = ProjectExplorer.VIEW_ID;
		ISelection selection = selService.getSelection(pluginName);
		
		String scaler = ncdScalerSourceProvider.getScaler();
		if (scaler == null) {
			Status status = new Status(IStatus.ERROR, ID, "Normalisation dataset not selected. Please load detector information.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
		if (detectorSaxs == null) {
			Status status = new Status(IStatus.ERROR, ID, "SAXS detector not selected. Please load detector information.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		if (selection instanceof IStructuredSelection
			&& ((IStructuredSelection) selection).toList().size() == 1
			&& (((IStructuredSelection) selection).getFirstElement() instanceof IFile)) {

				final Object sel = ((IStructuredSelection) selection).getFirstElement();
				if (sel instanceof IFile) {
					dataFileName = ((IFile) sel).getLocation().toString();
				} else {
					Status status = new Status(IStatus.ERROR, ID, "Invalid file selection. Please select calibration file in Profject Explorer view.");
					StatusManager.getManager().handle(status, StatusManager.SHOW);
					return;
				}
				try {
					HDF5File dataTree = new HDF5Loader(dataFileName).loadTree();
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
						Status status = new Status(IStatus.ERROR, ID, "Cannot find normalisation data. Please check normalisation dataset setting.");
						StatusManager.getManager().handle(status, StatusManager.SHOW);
						return;
					}

					nodeLink = dataTree.findNodeLink("/entry1/" + detectorSaxs + "/data");
					if (nodeLink == null) {
						Status status = new Status(IStatus.ERROR, ID, "Cannot find calibrant image data. Please check detector settings.");
						StatusManager.getManager().handle(status, StatusManager.SHOW);
						return;
					}

					// Open first frame if dataset has miltiple images
					HDF5Dataset node = (HDF5Dataset) nodeLink.getDestination();
					final int[] shape = node.getDataset().getShape();
					
					final int[] sqShape = AbstractDataset.squeezeShape(shape, true);
					if (sqShape.length > 2) {
						Status status = new Status(IStatus.INFO, ID, "WARNING: This dataset contains several frames. By default, the first frame will be loaded for absolute intensity calibration.");
						StatusManager.getManager().handle(status, StatusManager.LOG);
					}
					
					final int[] start = new int[shape.length];
					final int[] stop = Arrays.copyOf(shape, shape.length);
					Arrays.fill(start, 0, shape.length, 0);
					if (shape.length > 2) {
						Arrays.fill(stop, 0, shape.length - 2, 1);
					}
					imageDataset = (AbstractDataset) node.getDataset().getSlice(start, stop, null).clone().squeeze();
					imageDataset = DatasetUtils.cast(imageDataset, AbstractDataset.FLOAT32);
					imageDataset.idivide(norm);
					
					mask = MaskingTool.getSavedMask();
					
				} catch (Exception ex) {
					Status status = new Status(IStatus.ERROR, ID, "Cannot load image data. Please check file selection in the Project Explorer view and detector settings.");
					StatusManager.getManager().handle(status, StatusManager.SHOW);
					return;
				}
		}

		CalibrationResultsBean crb = (CalibrationResultsBean) ncdCalibrationSourceProvider.getCurrentState().get(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		if (crb == null || !crb.containsKey(detectorSaxs)) {
			Status status = new Status(IStatus.ERROR, ID, "Couldn't find SAXS detector calibration data.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		Unit<ScatteringVector> unit = crb.getUnit(detectorSaxs).inverse().asType(ScatteringVector.class);
		
		dataQ = new ArrayList<Amount<ScatteringVector>>();
		
		SectorROI sroi = null;
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
		}
		
		ILoaderService loaderService = (ILoaderService)PlatformUI.getWorkbench().getService(ILoaderService.class);
		IDiffractionMetadata dm = loaderService.getLockedDiffractionMetaData();
		QSpace qSpace = null;
		
		if (dm != null) {
			DetectorProperties detprops = dm.getDetector2DProperties().clone();
	    	DiffractionCrystalEnvironment diffexp = dm.getDiffractionCrystalEnvironment().clone();
			qSpace = new QSpace(detprops, diffexp);
		} else {
			Status status = new Status(IStatus.ERROR, ID, "Invalid input data. Diffraction metadata was not found.");
			StatusManager.getManager().handle(status, StatusManager.SHOW);
			return;
		}
		
		AbstractDataset[] profile = ROIProfile.sector(imageDataset, mask, sroi, true, false, false, qSpace, XAxis.Q, false);
		dataI = profile[0];
		AbstractDataset dataQDataset = profile[4];
		for (int idx = 0; idx < dataQDataset.getSize(); idx++) {
			double val = dataQDataset.getDouble(idx);
			dataQ.add(Amount.valueOf(val, unit));
		}
		
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
						IPlottingSystem plottingSystemRes = PlottingFactory.getPlottingSystem(resultsPlotName);
						plottingSystemRes.clear();
						
						ILineTrace refTrace = plottingSystemRes.createLineTrace("Reference Glassy Carbon Profile");
						refTrace.setData(ncdAbsoluteCalibration.getAbsQ(), absI);
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
						
						double[] polynom = ncdAbsoluteCalibration.getCalibrationPolynomial().getCoefficients();
						ncdAbsScaleSourceProvider.setAbsScaling(polynom[1] * thickness, true);
						ncdAbsOffsetSourceProvider.setAbsOffset(polynom[0] * thickness);
						
						IPlottingSystem plottingSystemRef = PlottingFactory.getPlottingSystem(referencePlotName);
						ITrace trace = plottingSystemRef.getTrace(dataFileName);
						if (trace != null) {
							plottingSystemRes.removeTrace(trace);
						}
						imageDataset.imultiply(polynom[1]);
						IImageTrace imageTrace = (IImageTrace) plottingSystemRef.createPlot2D(imageDataset, null, new NullProgressMonitor());
						imageTrace.setMask(mask);
					}
				});
					
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}
}
