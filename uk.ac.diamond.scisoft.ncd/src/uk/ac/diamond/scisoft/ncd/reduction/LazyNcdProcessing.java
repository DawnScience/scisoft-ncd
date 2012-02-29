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

package uk.ac.diamond.scisoft.ncd.reduction;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeBuilder;
//import gda.device.detector.NXDetectorData;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.iterators.ArrayIterator;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math.util.MultidimensionalCounter;
import org.apache.commons.math.util.MultidimensionalCounter.Iterator;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.nexusformat.NexusException;
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.IntegerDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Maths;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.analysis.dataset.Stats;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationResultsBean;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.Average;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5DetectorResponse;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5Invariant;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5Normalisation;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5SectorIntegration;
import uk.ac.diamond.scisoft.ncd.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class LazyNcdProcessing {

	private boolean enableMask;
	private int normChannel;
	private Double bgScaling;
	private String bgFile, drFile, calibration;
	private SectorROI intSector;
	private Double slope;
	private Double intercept;
	private Double cameraLength;
	private String qaxisUnit;
	private BooleanDataset mask;

	private CalibrationResultsBean crb;
	
	private NcdReductionFlags flags;
	private NcdDetectors ncdDetectors;
	private Double absScaling;
	private Integer firstFrame;
	private Integer lastFrame;
	private String frameSelection;
	private Integer bgFirstFrame;
	private Integer bgLastFrame;
	private String bgFrameSelection;
	private String gridAverage;
	
	int frameBatch;
	
	public LazyNcdProcessing() {
		enableMask = false;
		normChannel = -1;
		absScaling = null;
		bgScaling = null;
		bgFile = null;
		drFile = null;
		calibration = null;
		intSector = null;
		slope = null;
		intercept = null;
		cameraLength = null;
		qaxisUnit = null;
		mask = null;
		crb = null;
		firstFrame = null;
		lastFrame = null;
		
		flags = new NcdReductionFlags();
		ncdDetectors = new NcdDetectors();
		
		frameBatch = 150;	//TODO: calculate based on the image size
		
	}

	public void setAbsScaling(Double absScaling) {
		this.absScaling = absScaling;
	}
	
	public void setBgScaling(Double bgScaling) {
		this.bgScaling = bgScaling;
	}

	public void setBgFile(String bgFile) {
		this.bgFile = bgFile;
	}

	public void setDrFile(String drFile) {
		this.drFile = drFile;
	}

	public void setEnableMask(boolean enableMask) {
		this.enableMask = enableMask;
	}

	public void setNormChannel(int normChannel) {
		this.normChannel = normChannel;
	}

	public void setCalibration(String calibration) {
		this.calibration = calibration;
	}

	public void setIntSector(SectorROI intSector) {
		this.intSector = intSector;
	}

	public void setMask(BooleanDataset mask) {
		this.mask = mask;
	}

	public void setCrb(CalibrationResultsBean crb) {
		this.crb = crb;
	}

	public void setFlags(NcdReductionFlags flags) {
		this.flags = flags;
	}

	public void setNcdDetectors(NcdDetectors ncdDetectors) {
		this.ncdDetectors = ncdDetectors;
	}

	public void setFirstFrame(Integer firstFrame) {
		this.firstFrame = firstFrame;
	}

	public void setLastFrame(Integer lastFrame) {
		this.lastFrame = lastFrame;
	}

	public void setBgFirstFrame(Integer bgFirstFrame) {
		this.bgFirstFrame = bgFirstFrame;
	}

	public void setBgLastFrame(Integer bgLastFrame) {
		this.bgLastFrame = bgLastFrame;
	}

	public void execute(String detector, int dim, String filename, IProgressMonitor monitor) throws ExecutionException, NexusException {

		NexusFile nxsFile = null; 
		String[] tmpName = FilenameUtils.getName(filename).split("_");
		String monitorFile = tmpName[tmpName.length - 1];
		try {
		nxsFile = new NexusFile(filename, NexusFile.NXACC_RDWR);
		nxsFile.opengroup("entry1", "NXentry");
		
		INexusTree detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(detector, calibration));
		INexusTree tmpNXdata = detectorTree.getNode("entry1/instrument");
		int[] frames = tmpNXdata.getNode(detector).getNode("data").getData().dimensions;
		int frameBatch = 5;	//TODO: calculate based on the image size
		
		AbstractDataset qaxis = null;
		
		if (crb != null) {
			if (crb.containsKey(detector)) {
				if (slope == null) slope = crb.getFuction(detector).getParameterValue(0);
				if (intercept == null) intercept = crb.getFuction(detector).getParameterValue(1);
				cameraLength = crb.getMeanCameraLength(detector);
				if (qaxisUnit == null) qaxisUnit = crb.getUnit(detector);
			}
		}
		
		if (slope != null && intercept != null) {
			if (dim == 1) {
				int numPoints = frames[frames.length - 1];
				qaxis = AbstractDataset.zeros(new int[]{numPoints}, AbstractDataset.FLOAT32);
				double pxWaxs = ncdDetectors.getPxWaxs();
				for (int i = 0; i < numPoints; i++)
					qaxis.set(i*pxWaxs *slope + intercept, i);
			} else if (dim > 1 && flags.isEnableSector()) {
				double d2bs = intSector.getRadii()[0]; 
				int numPoints = (int) Math.ceil(intSector.getRadii()[1] - d2bs); 
				qaxis = AbstractDataset.zeros(new int[]{numPoints}, AbstractDataset.FLOAT32);
				double pxSaxs = ncdDetectors.getPxSaxs();
				for (int i = 0; i < numPoints; i++)
					qaxis.set((i+d2bs)*pxSaxs *slope + intercept, i);
			}
		}

		String activeDataset = detector;
		
		String detInvariant = detector;
		int[] invFrames = frames.clone();
		
		if (frameSelection != null) {
			String selNodeName = detector+"_selection";
			nxsFile.makegroup(selNodeName, "NXinstrument");
			nxsFile.opengroup(selNodeName, "NXinstrument");
			monitor.setTaskName(monitorFile + " : Selecting input frames");
			LazySelection lazySelection = new LazySelection(activeDataset, frames, frameBatch, nxsFile);
			lazySelection.setCalibration(calibration);
			lazySelection.setFormat(frameSelection);
			lazySelection.execute(tmpNXdata, dim, monitor);
			activeDataset = lazySelection.getActiveDataset();

			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(activeDataset, calibration));
			tmpNXdata = detectorTree.getNode("entry1/"+detector+"_selection");
			frames = updateFrameInfo(tmpNXdata, activeDataset);
			if (flags.isEnableInvariant()) detInvariant = activeDataset;
			nxsFile.closegroup();
		}
		
		if (bgFrameSelection != null) {
			String selNodeName = detector+"_bgselection";
			nxsFile.makegroup(selNodeName, "NXinstrument");
			nxsFile.opengroup(selNodeName, "NXinstrument");
			INexusTree bgDetectorTree = NexusTreeBuilder.getNexusTree(bgFile, NcdDataUtils.getDetectorSelection(detector, calibration));
			INexusTree bgNXdata = bgDetectorTree.getNode("entry1/instrument");
			int[] bgFrames = bgNXdata.getNode(detector).getNode("data").getData().dimensions;
			
			monitor.setTaskName(monitorFile + " : Selecting background input frames");
			LazySelection lazySelection = new LazySelection(detector, bgFrames, frameBatch, nxsFile);
			lazySelection.setCalibration(calibration);
			lazySelection.setFormat(bgFrameSelection);
			lazySelection.execute(bgNXdata, dim, monitor);
			nxsFile.closegroup();
		}
		
		
		String nodeName = detector+"_processing";
		nxsFile.makegroup(nodeName, "NXinstrument");
		nxsFile.opengroup(nodeName, "NXinstrument");
		
		if (flags.isEnableNormalisation()) {
			monitor.setTaskName(monitorFile + " : Normalising data");
			LazyNormalisation lazyNormalisation = new LazyNormalisation(activeDataset, frames, frameBatch, nxsFile);
			lazyNormalisation.setDetector(detector);
			lazyNormalisation.setFirstFrame(firstFrame, dim);
			lazyNormalisation.setLastFrame(lastFrame, dim);
			lazyNormalisation.setCalibration(calibration);
			lazyNormalisation.setNormChannel(normChannel);
			lazyNormalisation.setAbsScaling(absScaling);
			lazyNormalisation.setQaxis(qaxis, qaxisUnit);
			
			lazyNormalisation.execute(tmpNXdata, dim, monitor);
			activeDataset = lazyNormalisation.getActiveDataset();

			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(activeDataset, calibration));
			tmpNXdata = detectorTree.getNode("entry1/"+detector+"_processing");
			frames = updateFrameInfo(tmpNXdata, activeDataset);
			if (flags.isEnableInvariant()) detInvariant = activeDataset;
		}
		
		monitor.worked(1);

		if (flags.isEnableBackground()) {
			monitor.setTaskName(monitorFile + " : Subtracting background");
			
			LazyBackgroundSubtraction lazyBackgroundSubtraction = new LazyBackgroundSubtraction(activeDataset, frames, frameBatch, nxsFile);
			lazyBackgroundSubtraction.setDetector(detector);
			lazyBackgroundSubtraction.setFirstFrame(firstFrame, dim);
			lazyBackgroundSubtraction.setLastFrame(lastFrame, dim);
			if (bgFrameSelection != null) {
				lazyBackgroundSubtraction.setBgFile(filename);
				lazyBackgroundSubtraction.setBgRoot("entry1/" + detector+"_bgselection");
			}
			else {
				lazyBackgroundSubtraction.setBgFile(bgFile);
				lazyBackgroundSubtraction.setBgRoot("entry1/instrument");
			}
			lazyBackgroundSubtraction.setBgFirstFrame(bgFirstFrame);
			lazyBackgroundSubtraction.setBgLastFrame(bgLastFrame);
			lazyBackgroundSubtraction.setBgScale(bgScaling);
			lazyBackgroundSubtraction.setCalibration(calibration);
			lazyBackgroundSubtraction.setNormChannel(normChannel);
			lazyBackgroundSubtraction.setQaxis(qaxis, qaxisUnit);
			if (flags.isEnableNormalisation())
				lazyBackgroundSubtraction.setAbsScaling(absScaling);
			
			lazyBackgroundSubtraction.execute(tmpNXdata, dim, monitor);
			activeDataset = lazyBackgroundSubtraction.getActiveDataset();
			
			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(activeDataset, calibration));
			tmpNXdata = detectorTree.getNode("entry1/"+detector+"_processing");
			frames = updateFrameInfo(tmpNXdata, activeDataset);
			if (flags.isEnableInvariant()) detInvariant = activeDataset;
		}
		
		monitor.worked(1);

		if (flags.isEnableDetectorResponse())	{
			monitor.setTaskName(monitorFile + " : Correct for detector response");
			LazyDetectorResponse lazyDetectorResponse = new LazyDetectorResponse(activeDataset, frames, frameBatch, nxsFile);
			lazyDetectorResponse.setDetector(detector);
			lazyDetectorResponse.setFirstFrame(firstFrame, dim);
			lazyDetectorResponse.setLastFrame(lastFrame, dim);
			lazyDetectorResponse.setDrFile(drFile);
			lazyDetectorResponse.setQaxis(qaxis,qaxisUnit);
			
			lazyDetectorResponse.execute(tmpNXdata, dim, monitor);
			activeDataset = lazyDetectorResponse.getActiveDataset();
			
			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(activeDataset, calibration));
			tmpNXdata = detectorTree.getNode("entry1/"+detector+"_processing");
			frames = updateFrameInfo(tmpNXdata, activeDataset);
			if (flags.isEnableInvariant()) detInvariant = activeDataset;
		}
		
		monitor.worked(1);

		if (dim > 1 && flags.isEnableSector()) {
			monitor.setTaskName(monitorFile + " : Performing sector integration");
			LazySectorIntegration lazySectorIntegration = new LazySectorIntegration(activeDataset, frames, frameBatch, nxsFile);
			lazySectorIntegration.setFirstFrame(firstFrame, dim);
			lazySectorIntegration.setLastFrame(lastFrame, dim);
			lazySectorIntegration.setQaxis(qaxis,qaxisUnit);
			lazySectorIntegration.setIntSector(intSector);
			if (enableMask) 
				lazySectorIntegration.setMask(mask);
			lazySectorIntegration.setCalibrationData(slope, intercept);
			lazySectorIntegration.setCameraLength(cameraLength);
			
			lazySectorIntegration.execute(tmpNXdata, dim, monitor);
			activeDataset = lazySectorIntegration.getActiveDataset();
			
			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(activeDataset, calibration));
			tmpNXdata = detectorTree.getNode("entry1/"+detector+"_processing");
			
			frames = updateFrameInfo(tmpNXdata, activeDataset);
		}
		
		monitor.worked(1);

		if (dim > 1 && flags.isEnableInvariant()) {
			monitor.setTaskName(monitorFile + " : Calculating invariant");
			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(detInvariant, calibration));
			if (detInvariant.equals(detector))
				tmpNXdata = detectorTree.getNode("entry1/instrument");
			else
				tmpNXdata = detectorTree.getNode("entry1/"+detector+"_processing");
			
			LazyInvariant lazyInvariant = new LazyInvariant(detInvariant, invFrames, frameBatch, nxsFile);
			lazyInvariant.setFirstFrame(firstFrame, dim);
			lazyInvariant.setLastFrame(lastFrame, dim);
			lazyInvariant.execute(tmpNXdata, dim, monitor);
			
			detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(activeDataset, calibration));
			if (activeDataset.equals(detector))
				tmpNXdata = detectorTree.getNode("entry1/instrument");
			else
				tmpNXdata = detectorTree.getNode("entry1/"+detector+"_processing");
		}
		
		monitor.worked(1);

		if (flags.isEnableAverage()) {
			monitor.setTaskName(monitorFile + " : Averaging  datasets");
			LazyAverage lazyAverage = new LazyAverage(activeDataset, frames, frameBatch, nxsFile);
			int aveDim = (dim > 1 && flags.isEnableSector()) ? dim-1 : dim;
			
			if (gridAverage != null) {
				int[] averageIndices = NcdDataUtils.createGridAxesList(gridAverage, frames.length - dim + 1);
				lazyAverage.setAverageIndices(averageIndices , aveDim);
			}
			lazyAverage.setFirstFrame(firstFrame, aveDim);
			lazyAverage.setLastFrame(lastFrame, aveDim);
			lazyAverage.setQaxis(qaxis, qaxisUnit);
			
			lazyAverage.execute(tmpNXdata, aveDim, monitor);
		}
		
		monitor.worked(1);
		closeResultsFile(nxsFile);
		} catch (CancellationException e) {
			if (nxsFile != null) closeResultsFile(nxsFile);
		} catch (Exception e) {
			if (nxsFile != null) closeResultsFile(nxsFile);
			throw new ExecutionException("Error running data reduction using " + detector + " data in " + monitorFile +
					". Please check input parameters." , e);
		}
	}

	private void closeResultsFile(NexusFile nxsFile) throws NexusException {
		nxsFile.flush();
		nxsFile.closegroup();
		nxsFile.closegroup();
		nxsFile.close();
	}
	
	private int[] updateFrameInfo(INexusTree tmpNXdata, String activeDataset) {
		if (firstFrame != null && lastFrame != null) lastFrame -= firstFrame;
		if (firstFrame != null) firstFrame = 0;
		return tmpNXdata.getNode(activeDataset).getNode("data").getData().dimensions;
	}

	public void setFrameSelection(String frameSelection) {
		this.frameSelection = frameSelection;
	}

	public void setBgFrameSelection(String bgFrameSelection) {
		this.bgFrameSelection = bgFrameSelection;
	}

	public void setGridAverageSelection(String gridAverage) {
		this.gridAverage = gridAverage;
	}

	public void setSlope(Double slope) {
		this.slope = slope;
	}

	public void setIntercept(Double intercept) {
		this.intercept = intercept;
	}

	public void setCameraLength(Double cameraLength) {
		this.cameraLength = cameraLength;
	}

	public void setUnit(String unit) {
		this.qaxisUnit = unit;
	}

	
	public void executeHDF5(String detector, int dim, String filename, IProgressMonitor monitor) throws NullPointerException, HDF5Exception {
		
		String[] tmpName = FilenameUtils.getName(filename).split("_");
		String monitorFile = tmpName[tmpName.length - 1];
		
		int nxsfile_handle = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_group_id = H5.H5Gopen(nxsfile_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		int instrument_group_id = H5.H5Gopen(entry_group_id, "instrument", HDF5Constants.H5P_DEFAULT);
		int detector_group_id = H5.H5Gopen(instrument_group_id, detector, HDF5Constants.H5P_DEFAULT);
		int input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		int input_dataspace_id = H5.H5Dget_space(input_data_id);
		int input_datatype_id = H5.H5Dget_type(input_data_id);
		int input_dataclass_id = H5.H5Tget_class(input_datatype_id);
		int input_datasize_id = H5.H5Tget_size(input_datatype_id);
		
		DataSliceIdentifiers input_ids = new DataSliceIdentifiers();
		input_ids.setIDs(input_data_id, input_dataspace_id, input_dataclass_id, input_datatype_id, input_datasize_id);
		
		int rank = H5.H5Sget_simple_extent_ndims(input_dataspace_id);
		long[] frames = new long[rank];
		H5.H5Sget_simple_extent_dims(input_dataspace_id, frames, null);
		int[] frames_int = (int[]) ConvertUtils.convert(frames, int[].class);
		
		int secRank = rank - dim + 1;
		long[] secFrames = new long[secRank];
		
		int processing_group_id = NcdNexusUtils.makegroup(entry_group_id, detector + "_processing", "NXinstrument");
	    int sec_group_id = -1;
	    int sec_data_id = -1;
	    int az_data_id = -1;
		if(flags.isEnableSector()) {
		    sec_group_id = NcdNexusUtils.makegroup(processing_group_id, LazySectorIntegration.name, "NXdetector");
			int type = HDF5Constants.H5T_NATIVE_FLOAT;
			int[] radii = intSector.getIntRadii();
			secFrames = Arrays.copyOf(frames, secRank);
			secFrames[secRank - 1] = radii[1] - radii[0] + 1;
			sec_data_id = NcdNexusUtils.makedata(sec_group_id, "data", type, secRank, secFrames, true, "counts");
			
			double[] angles = intSector.getAngles();
			long[] azFrames = Arrays.copyOf(frames, secRank);
			azFrames[secRank - 1] = (int) Math.ceil((angles[1] - angles[0]) * radii[1] * intSector.getDpp());
			az_data_id = NcdNexusUtils.makedata(sec_group_id, "azimuth", type, secRank, azFrames, false, "counts");
		}
		
	    int inv_group_id = -1;
	    int inv_data_id = -1;
		int invRank = flags.isEnableSector() ? secRank - 1: rank - dim;
		if(flags.isEnableInvariant()) {
		    inv_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyInvariant.name, "NXdetector");
			int type = HDF5Constants.H5T_NATIVE_FLOAT;
			long[] invFrames = Arrays.copyOf(flags.isEnableSector() ? secFrames : frames, invRank);
			inv_data_id = NcdNexusUtils.makedata(inv_group_id, "data", type, invRank, invFrames, true, "counts");
		}
		
	    int norm_group_id = -1;
	    int norm_data_id = -1;
	    int norm_axis_id = -1;
		int calibration_group_id = -1;
		int calibration_data_id = -1;
		int calibration_dataspace_id = -1;
		int calibration_datatype_id = -1;
		int calibration_dataclass_id = -1;
		int calibration_datasize_id = -1;
		
		DataSliceIdentifiers calibration_ids = null;
		
		int rankCal;
		long[] framesCal = null;
		
		if(flags.isEnableNormalisation()) {
		    norm_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyNormalisation.name, "NXdetector");
		    int type = HDF5Constants.H5T_NATIVE_FLOAT;
			norm_data_id = NcdNexusUtils.makedata(norm_group_id, "data", type, flags.isEnableSector() ? secRank : rank,
					flags.isEnableSector() ? secFrames : frames, true, "counts");
		    //norm_axis_id = NcdNexusUtils.makeaxis(norm_group_id, "q", type, 1, new long[] {1200}, new int[] {1,3}, 2, "counts");
			
			calibration_group_id = H5.H5Gopen(instrument_group_id, calibration, HDF5Constants.H5P_DEFAULT);
			calibration_data_id = H5.H5Dopen(calibration_group_id, "data", HDF5Constants.H5P_DEFAULT);
			calibration_dataspace_id = H5.H5Dget_space(calibration_data_id);
			calibration_datatype_id = H5.H5Dget_type(calibration_data_id);
			calibration_dataclass_id = H5.H5Tget_class(calibration_datatype_id);
			calibration_datasize_id = H5.H5Tget_size(calibration_datatype_id);
			
			calibration_ids = new DataSliceIdentifiers();
			calibration_ids.setIDs(calibration_data_id, calibration_dataspace_id, calibration_dataclass_id, calibration_datatype_id, calibration_datasize_id);
			
			rankCal = H5.H5Sget_simple_extent_ndims(calibration_dataspace_id);
			framesCal = new long[rankCal];
			H5.H5Sget_simple_extent_dims(calibration_dataspace_id, framesCal, null);
		}
		
	    int bkg_group_id;
	    DataSliceIdentifiers bgIds, bgSelection_ids;
		if(flags.isEnableBackground()) {
		    bkg_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyBackgroundSubtraction.name, "NXdetector");
		    bgIds = readBackgroundSubtractionData(detector);
		    
			int bgRank = H5.H5Sget_simple_extent_ndims(bgIds.dataspace_id);
			long[] bgFrames = new long[bgRank];
			H5.H5Sget_simple_extent_dims(bgIds.dataspace_id, bgFrames, null);
			int[] bgFrames_int = (int[]) ConvertUtils.convert(bgFrames, int[].class);
			
			MultidimensionalCounter countBgFrames = new MultidimensionalCounter(Arrays.copyOfRange(bgFrames_int, 0, bgFrames_int.length - dim));
			int totalLength = countBgFrames.getSize();
		
			if (bgFrameSelection != null) {
			    int sel_group_id = NcdNexusUtils.makegroup(entry_group_id, detector + "_background", "NXinstrument");
			    int bkgsel_group_id = NcdNexusUtils.makegroup(sel_group_id, "BackgroundSelection", "NXdetector");
			    bgSelection_ids = executeSelection(dim, bgFrames_int, bgIds, bkgsel_group_id);
			}
		}
	    
	    int dr_group_id;
	    int dr_data_id = -1;
	    AbstractDataset drData = null;
		if(flags.isEnableDetectorResponse()) {
		    dr_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyDetectorResponse.name, "NXdetector");
		    int type = HDF5Constants.H5T_NATIVE_FLOAT;
			dr_data_id = NcdNexusUtils.makedata(dr_group_id, "data", type, rank, frames, true, "counts");
			
			drData = readDetectorResponseData(drFile, detector);
		}
	    
		int sliceDim = 0;
		int sliceSize = (int) frames[0];
		int lastSliceSize = 0;

		// We will slice only 2D data. 1D data is loaded into memory completely
		if (dim == 2) {
			// Find dimension that needs to be sliced
			MultidimensionalCounter dimCounter = new MultidimensionalCounter(Arrays.copyOfRange(frames_int, 0, rank - dim));
			if (dimCounter.getSize() > frameBatch) {
				//TOD0: dimCounter.getCounts(frameBatch) is broken in Apache Math v2.2
				//		Use getCounts(int index) after update to v3.0
				int[] sliceIdx = NcdDataUtils.getCounts(dimCounter, frameBatch);
				sliceDim = ArrayUtils.lastIndexOf(sliceIdx, 0) + 1;
				sliceSize = sliceIdx[sliceDim];
				lastSliceSize = frames_int[sliceDim] % sliceSize;
			}
		}
		
		int[] iter_array = Arrays.copyOfRange(frames_int, 0, sliceDim + 1);
		int [] start = new int[iter_array.length];
		int[] step =  new int[iter_array.length];
		Arrays.fill(start, 0);
		Arrays.fill(step, 1);
		step[sliceDim] = sliceSize;
		IntegerDataset idx_dataset = new IntegerDataset(iter_array);
		IndexIterator iter = idx_dataset.getSliceIterator(start, iter_array, step);
		
		if (flags.isEnableSector()) {
			while (iter.hasNext()) {
				long[] start_pos = (long[]) ConvertUtils.convert(iter.getPos(), long[].class);
				long[] start_data = Arrays.copyOf(start_pos, frames.length);

				long[] block_data = Arrays.copyOf(frames, frames.length);
				Arrays.fill(block_data, 0, sliceDim, 1);
				block_data[sliceDim] = (start_pos[sliceDim] + sliceSize > frames[sliceDim]) ? lastSliceSize : sliceSize;

				long[] count_data = new long[frames.length];
				Arrays.fill(count_data, 1);

				input_ids.setSlice(start_data, block_data, count_data, block_data);
				AbstractDataset data = sliceInputData(input_ids);

				if (flags.isEnableDetectorResponse()) {
					monitor.setTaskName(monitorFile + " : Correct for detector response");

					DataSliceIdentifiers dr_id = new DataSliceIdentifiers(dr_data_id, start_data, block_data,
							count_data, block_data);

					data = executeDetectorResponse(dim, data, drData, dr_id);
				}

				monitor.setTaskName(monitorFile + " : Performing sector integration");
				DataSliceIdentifiers sector_id = new DataSliceIdentifiers(sec_data_id, start_data, block_data,
						count_data, block_data);
				DataSliceIdentifiers azimuth_id = new DataSliceIdentifiers(az_data_id, start_data, block_data,
						count_data, block_data);

				data = executeSectorIntegration(dim, data, sector_id, azimuth_id);
			}

			dim = 1;
			sliceDim = 0;
			sliceSize = (int) secFrames[0];
			lastSliceSize = 0;
			
			frames = secFrames;
			frames_int = (int[]) ConvertUtils.convert(secFrames, int[].class);

			iter = idx_dataset.getSliceIterator(new int[] {0}, new int[] {sliceSize}, new int[] {sliceSize});
			
			int sec_dataspace_id = H5.H5Dget_space(sec_data_id);
			int sec_datatype_id = H5.H5Dget_type(sec_data_id);
			int sec_dataclass_id = H5.H5Tget_class(sec_datatype_id);
			int sec_datasize_id = H5.H5Tget_size(sec_datatype_id);
			input_ids.setIDs(sec_data_id, sec_dataspace_id, sec_dataclass_id, sec_datatype_id, sec_datasize_id);
		}

		AbstractDataset data = null;
		while (iter.hasNext()) {
			long[] start_pos = (long[]) ConvertUtils.convert(iter.getPos(), long[].class);
			long[] start_data = Arrays.copyOf(start_pos, frames.length);

			long[] block_data = Arrays.copyOf(frames, frames.length);
			Arrays.fill(block_data, 0, sliceDim, 1);
			block_data[sliceDim] = (start_pos[sliceDim] + sliceSize > frames[sliceDim]) ? lastSliceSize : sliceSize;

			long[] count_data = new long[frames.length];
			Arrays.fill(count_data, 1);

			input_ids.setSlice(start_data, block_data, count_data, block_data);
			data = sliceInputData(input_ids);

			if (flags.isEnableDetectorResponse() && !flags.isEnableSector()) {
				monitor.setTaskName(monitorFile + " : Correct for detector response");

				DataSliceIdentifiers dr_id = new DataSliceIdentifiers(dr_data_id, start_data, block_data,
						count_data, block_data);

				data = executeDetectorResponse(dim, data, drData, dr_id);
				
				int dr_dataspace_id = H5.H5Dget_space(dr_data_id);
				int dr_datatype_id = H5.H5Dget_type(dr_data_id);
				int dr_dataclass_id = H5.H5Tget_class(dr_datatype_id);
				int dr_datasize_id = H5.H5Tget_size(dr_datatype_id);
				input_ids.setIDs(dr_data_id, dr_dataspace_id, dr_dataclass_id, dr_datatype_id, dr_datasize_id);
			}

			if (flags.isEnableNormalisation()) {
				monitor.setTaskName(monitorFile + " : Normalising data");

				DataSliceIdentifiers norm_id = new DataSliceIdentifiers(norm_data_id, start_data, block_data,
						count_data, block_data);

				long[] cal_start_data = Arrays.copyOf(start_pos, framesCal.length);

				long[] cal_block_data = Arrays.copyOf(framesCal, framesCal.length);
				Arrays.fill(cal_block_data, 0, sliceDim, 1);
				cal_block_data[sliceDim] = (start_pos[sliceDim] + sliceSize > framesCal[sliceDim]) ? lastSliceSize
						: sliceSize;

				long[] cal_count_data = new long[framesCal.length];
				Arrays.fill(cal_count_data, 1);

				calibration_ids.setSlice(cal_start_data, cal_block_data, cal_count_data, cal_block_data);
				AbstractDataset dataCal = sliceInputData(calibration_ids);

				data = executeNormalisation(dim, data, dataCal, norm_id);
				
				int norm_dataspace_id = H5.H5Dget_space(norm_data_id);
				int norm_datatype_id = H5.H5Dget_type(norm_data_id);
				int norm_dataclass_id = H5.H5Tget_class(norm_datatype_id);
				int norm_datasize_id = H5.H5Tget_size(norm_datatype_id);
				input_ids.setIDs(norm_data_id, norm_dataspace_id, norm_dataclass_id, norm_datatype_id, norm_datasize_id);
			}

			if (flags.isEnableInvariant()) {
				monitor.setTaskName(monitorFile + " : Calculating invariant");
				
				long[] inv_start_data = Arrays.copyOf(start_pos, invRank);
				long[] inv_block_data = Arrays.copyOf(frames, invRank);
				Arrays.fill(inv_block_data, 0, sliceDim, 1);
				inv_block_data[sliceDim] = (start_pos[sliceDim] + sliceSize > frames[sliceDim]) ? lastSliceSize
						: sliceSize;
            
				long[] inv_count_data = new long[invRank];
				Arrays.fill(inv_count_data, 1);
            
				DataSliceIdentifiers inv_id = new DataSliceIdentifiers(inv_data_id, inv_start_data, inv_block_data,
						inv_count_data, inv_block_data);
            
				executeInvariant(dim, data, inv_id);
			}
		}

		if(flags.isEnableAverage()) {
			monitor.setTaskName(monitorFile + " : Averaging  datasets");
			
			executeAverage(dim, data, processing_group_id, frameBatch, input_ids);
		}
		
		AbstractDataset qaxis = null;
		
		if (crb != null) {
			if (crb.containsKey(detector)) {
				if (slope == null) slope = crb.getFuction(detector).getParameterValue(0);
				if (intercept == null) intercept = crb.getFuction(detector).getParameterValue(1);
				cameraLength = crb.getMeanCameraLength(detector);
				if (qaxisUnit == null) qaxisUnit = crb.getUnit(detector);
			}
		}
		
		if (slope != null && intercept != null) {
			if (dim == 1) {
				int numPoints = (int) frames[frames.length - 1];
				qaxis = AbstractDataset.zeros(new int[]{numPoints}, AbstractDataset.FLOAT32);
				double pxWaxs = ncdDetectors.getPxWaxs();
				for (int i = 0; i < numPoints; i++)
					qaxis.set(i*pxWaxs *slope + intercept, i);
			} else if (dim > 1 && flags.isEnableSector()) {
				double d2bs = intSector.getRadii()[0]; 
				int numPoints = (int) Math.ceil(intSector.getRadii()[1] - d2bs); 
				qaxis = AbstractDataset.zeros(new int[]{numPoints}, AbstractDataset.FLOAT32);
				double pxSaxs = ncdDetectors.getPxSaxs();
				for (int i = 0; i < numPoints; i++)
					qaxis.set((i+d2bs)*pxSaxs *slope + intercept, i);
			}
		}
        
		String activeDataset = detector;
		
		//if (flags.isEnableNormalisation()) {
		//	monitor.setTaskName(monitorFile + " : Normalising data");
		//	LazyNormalisation lazyNormalisation = new LazyNormalisation(activeDataset, frames, frameBatch, nxsFile);
		//	lazyNormalisation.setDetector(detector);
		//	lazyNormalisation.setFirstFrame(firstFrame, dim);
		//	lazyNormalisation.setLastFrame(lastFrame, dim);
		//	lazyNormalisation.setCalibration(calibration);
		//	lazyNormalisation.setNormChannel(normChannel);
		//	lazyNormalisation.setAbsScaling(absScaling);
		//	lazyNormalisation.setQaxis(qaxis, qaxisUnit);
		//	
		//	lazyNormalisation.execute(tmpNXdata, dim, monitor);
		//	activeDataset = lazyNormalisation.getActiveDataset();
        //
		//	detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(activeDataset, calibration));
		//	tmpNXdata = detectorTree.getNode("entry1/"+detector+"_processing");
		//	frames = updateFrameInfo(tmpNXdata, activeDataset);
		//	if (flags.isEnableInvariant()) detInvariant = activeDataset;
		//}
		//String detInvariant = detector;
		//int[] invFrames = frames.clone();
		//
		//if (frameSelection != null) {
		//	String selNodeName = detector+"_selection";
		//	int selection_group_id = NcdNexusUtils.makegroup(entry_group_id, selNodeName, "NXinstrument");
		//	monitor.setTaskName(monitorFile + " : Selecting input frames");
		//	LazySelection lazySelection = new LazySelection(activeDataset, frames, frameBatch, nxsFile);
		//	lazySelection.setCalibration(calibration);
		//	lazySelection.setFormat(frameSelection);
		//	lazySelection.execute(tmpNXdata, dim, monitor);
		//	activeDataset = lazySelection.getActiveDataset();
        //
		//	detectorTree = NexusTreeBuilder.getNexusTree(filename, NcdDataUtils.getDetectorSelection(activeDataset, calibration));
		//	tmpNXdata = detectorTree.getNode("entry1/"+detector+"_selection");
		//	frames = updateFrameInfo(tmpNXdata, activeDataset);
		//	if (flags.isEnableInvariant()) detInvariant = activeDataset;
		//	nxsFile.closegroup();
		//}
		//
		//if (bgFrameSelection != null) {
		//	String selNodeName = detector+"_bgselection";
		//	nxsFile.makegroup(selNodeName, "NXinstrument");
		//	nxsFile.opengroup(selNodeName, "NXinstrument");
		//	INexusTree bgDetectorTree = NexusTreeBuilder.getNexusTree(bgFile, NcdDataUtils.getDetectorSelection(detector, calibration));
		//	INexusTree bgNXdata = bgDetectorTree.getNode("entry1/instrument");
		//	int[] bgFrames = bgNXdata.getNode(detector).getNode("data").getData().dimensions;
		//	
		//	monitor.setTaskName(monitorFile + " : Selecting background input frames");
		//	LazySelection lazySelection = new LazySelection(detector, bgFrames, frameBatch, nxsFile);
		//	lazySelection.setCalibration(calibration);
		//	lazySelection.setFormat(bgFrameSelection);
		//	lazySelection.execute(bgNXdata, dim, monitor);
		//	nxsFile.closegroup();
		//}
		
		
		
		
	}

	
	private AbstractDataset readDetectorResponseData(String drFile, String detector) throws HDF5Exception {
		
		int nxsfile_handle = H5.H5Fopen(drFile, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		
		int entry_group_id = H5.H5Gopen(nxsfile_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		int instrument_group_id = H5.H5Gopen(entry_group_id, "instrument", HDF5Constants.H5P_DEFAULT);
		int detector_group_id = H5.H5Gopen(instrument_group_id, detector, HDF5Constants.H5P_DEFAULT);
		
		int input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		int input_dataspace_id = H5.H5Dget_space(input_data_id);
		int input_datatype_id = H5.H5Dget_type(input_data_id);
		int input_dataclass_id = H5.H5Tget_class(input_datatype_id);
		int input_datasize_id = H5.H5Tget_size(input_datatype_id);
		
		int rank = H5.H5Sget_simple_extent_ndims(input_dataspace_id);
		int dtype = HDF5Loader.getDtype(input_dataclass_id, input_datasize_id);
		
		long[] frames = new long[rank];
		H5.H5Sget_simple_extent_dims(input_dataspace_id, frames, null);
		int memspace_id = H5.H5Screate_simple(rank, frames, null);
		
		int[] frames_int = (int[]) ConvertUtils.convert(frames, int[].class);
		AbstractDataset data = AbstractDataset.zeros(frames_int, dtype);
		
		if ((input_data_id >= 0) && (input_dataspace_id >= 0) && (memspace_id >= 0))
			H5.H5Dread(input_data_id, input_datatype_id, memspace_id, input_dataspace_id, HDF5Constants.H5P_DEFAULT,
					data.getBuffer());
		
		return data;
	}
	
	private DataSliceIdentifiers readBackgroundSubtractionData(String detector) throws HDF5Exception {
		int bgfile_handle = H5.H5Fopen(bgFile, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		int bgentry_group_id = H5.H5Gopen(bgfile_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		int bginstrument_group_id = H5.H5Gopen(bgentry_group_id, "instrument", HDF5Constants.H5P_DEFAULT);
		int bgdetector_group_id = H5.H5Gopen(bginstrument_group_id, detector, HDF5Constants.H5P_DEFAULT);
		int bginput_data_id = H5.H5Dopen(bgdetector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		int bginput_dataspace_id = H5.H5Dget_space(bginput_data_id);
		int bginput_datatype_id = H5.H5Dget_type(bginput_data_id);
		int bginput_dataclass_id = H5.H5Tget_class(bginput_datatype_id);
		int bginput_datasize_id = H5.H5Tget_size(bginput_datatype_id);
		
		DataSliceIdentifiers ids = new DataSliceIdentifiers();
		ids.setIDs(bginput_data_id, bginput_dataspace_id, bginput_dataclass_id, bginput_datatype_id, bginput_datasize_id);

		
		return ids;
	}
	
	private AbstractDataset sliceInputData(DataSliceIdentifiers ids) throws HDF5Exception {

		H5.H5Sselect_hyperslab(ids.dataspace_id, HDF5Constants.H5S_SELECT_SET, ids.start, ids.stride, ids.count,
				ids.block);
		int rank = H5.H5Sget_simple_extent_ndims(ids.dataspace_id);
		int dtype = HDF5Loader.getDtype(ids.dataclass_id, ids.datasize_id);
		int[] block_data_int = (int[]) ConvertUtils.convert(ids.block, int[].class);
		AbstractDataset data = AbstractDataset.zeros(block_data_int, dtype);
		int memspace_id = H5.H5Screate_simple(rank, ids.block, null);
		// Read the data using the previously defined hyperslab.
		if ((ids.dataset_id >= 0) && (ids.dataspace_id >= 0) && (memspace_id >= 0))
			H5.H5Dread(ids.dataset_id, ids.datatype_id, memspace_id, ids.dataspace_id, HDF5Constants.H5P_DEFAULT,
					data.getBuffer());

		return data;
	}
	
	private AbstractDataset executeNormalisation(int dim, AbstractDataset data, AbstractDataset dataCal, DataSliceIdentifiers norm_id) {
		HDF5Normalisation reductionStep = new HDF5Normalisation("norm", "data");
		reductionStep.setCalibChannel(normChannel);
		if(absScaling != null)
			reductionStep.setNormvalue(absScaling);
		reductionStep.parentngd = data;
		reductionStep.calibngd = dataCal;
		reductionStep.setIDs(norm_id);
		
		return reductionStep.writeout(dim);
	}
	
	
	private AbstractDataset executeDetectorResponse(int dim, AbstractDataset data, AbstractDataset drData, DataSliceIdentifiers det_id) {
		HDF5DetectorResponse reductionStep = new HDF5DetectorResponse("det", "data");
		reductionStep.setResponse(drData);
		reductionStep.parentngd = data;
		reductionStep.setIDs(det_id);
		
		return reductionStep.writeout(dim);
	}

	
	private AbstractDataset executeInvariant(int dim, AbstractDataset data, DataSliceIdentifiers inv_id) {
		HDF5Invariant reductionStep = new HDF5Invariant("inv", "data");
		reductionStep.parentngd = data;
		reductionStep.setIDs(inv_id);
		
		return reductionStep.writeout(dim);
	}
	
	
	private AbstractDataset executeSectorIntegration(int dim, AbstractDataset data, DataSliceIdentifiers sector_id, DataSliceIdentifiers azimuth_id) {
		HDF5SectorIntegration reductionStep = new HDF5SectorIntegration("sector", "data");
		reductionStep.parentdata = data;
		reductionStep.setROI(intSector);
		if (enableMask) 
			reductionStep.setMask(mask);
		reductionStep.setIDs(sector_id);
		reductionStep.setAzimuthalIDs(azimuth_id);
		
		return reductionStep.writeout(dim);
	}
	
	private void executeAverage(int dim, AbstractDataset data, int processing_group_id, int frameBatch, DataSliceIdentifiers input_ids) throws NullPointerException, HDF5Exception {
		
		int[] frames_int = data.getShape();
		long[] frames = (long[]) ConvertUtils.convert(frames_int, long[].class);
		int[] averageIndices = new int[] {};    //Dimension indexes that need to be averaged
		if (gridAverage != null)
			averageIndices = NcdDataUtils.createGridAxesList(gridAverage, frames.length - dim + 1);
		
		// Calculate shape of the averaged dataset based on the dimensions selected for averaging
		long[] framesAve = Arrays.copyOf(frames, frames.length);
		for (int idx : averageIndices)
			framesAve[idx - 1] = 1;
		
		int[] framesAve_int = (int[]) ConvertUtils.convert(framesAve, int[].class);
		
	    int ave_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyAverage.name, "NXdetector");
	    int type = HDF5Constants.H5T_NATIVE_FLOAT;
		int ave_data_id = NcdNexusUtils.makedata(ave_group_id, "data", type, framesAve.length, framesAve, true, "counts");
		
		// Loop over dimensions that aren't averaged
		int[] iter_array = Arrays.copyOf(framesAve_int, framesAve_int.length);
		int[] start = new int[iter_array.length];
		int[] step = Arrays.copyOf(framesAve_int, framesAve_int.length);
		Arrays.fill(start, 0);
		Arrays.fill(step, 0, framesAve_int.length - dim, 1);
		IntegerDataset idx_dataset = new IntegerDataset(iter_array);
		IndexIterator iter = idx_dataset.getSliceIterator(start, iter_array, step);
		
		int sliceDim = 0;
		int sliceSize = frames_int[0];
		int lastSliceSize = 0;

		// We will slice only 2D data. 1D data is loaded into memory completely
		if (averageIndices.length > 0 || dim == 2) {
			// Find dimension that needs to be sliced
			int dimCounter = 1;
			for (int idx = (frames.length - 1 - dim); idx >= 0; idx--) {
				if (ArrayUtils.contains(averageIndices, idx + 1)) {
					dimCounter *= frames[idx];
					if (dimCounter >= frameBatch) {
						sliceDim = idx;
						sliceSize = frameBatch * frames_int[idx] / dimCounter;
						lastSliceSize = frames_int[idx] % sliceSize;
						break;
					}
				}
			}
		}
		
		// This look iterates over the output averaged dataset image by image
		while (iter.hasNext()) {
			
			int[] currentFrame = iter.getPos();
			int[] data_iter_array = Arrays.copyOf(currentFrame, currentFrame.length);
			for (int i = 0; i < currentFrame.length; i++)
				data_iter_array[i]++;
			
			int[] data_start = Arrays.copyOf(currentFrame, currentFrame.length);
			int[] data_step = Arrays.copyOf(step, currentFrame.length);
			Arrays.fill(data_step, 0, currentFrame.length - dim, 1);
			for (int idx : averageIndices) {
				int i = idx - 1;
				data_start[i] = 0;
				data_iter_array[i] = frames_int[i];
				if (i > sliceDim)
					data_step[i] = frames_int[i];
				else if (i == sliceDim)
					data_step[i] = sliceSize;
			}
			
			IntegerDataset data_idx_dataset = new IntegerDataset(data_iter_array);
			IndexIterator data_iter = data_idx_dataset.getSliceIterator(data_start, data_iter_array, data_step);
			
			int[] aveShape = Arrays.copyOfRange(framesAve_int, framesAve_int.length - dim, framesAve_int.length);
			AbstractDataset ave_frame = AbstractDataset.zeros(aveShape, AbstractDataset.FLOAT32);
			
			long[] ave_count_data = new long[frames.length];
			Arrays.fill(ave_count_data, 1);
			long[] ave_block_data = (long[]) ConvertUtils.convert(data_step, long[].class);
			
			// This loop iterates over chunks of data that need to be averaged for the current output image
			int totalFrames = 0;
			while (data_iter.hasNext()) {
				
				long[] ave_start_pos = (long[]) ConvertUtils.convert(data_iter.getPos(), long[].class);
				long[] ave_start_data = Arrays.copyOf(ave_start_pos, frames.length);

				ave_block_data[sliceDim] = (ave_start_pos[sliceDim] + sliceSize > frames[sliceDim]) ? lastSliceSize : sliceSize;

				input_ids.setSlice(ave_start_data, ave_block_data, ave_count_data, ave_block_data);
				AbstractDataset data_slice = sliceInputData(input_ids);
				int data_slice_rank = data_slice.getRank();
				
				int totalFramesBatch = 1;
				for (int idx = (data_slice_rank - dim - 1); idx >= sliceDim; idx--)
					if (ArrayUtils.contains(averageIndices, idx + 1)) {
						totalFramesBatch *= data_slice.getShape()[idx];
						data_slice = data_slice.sum(idx);
					}
				totalFrames += totalFramesBatch;
				ave_frame.iadd(data_slice);
			}
			ave_frame.idivide(totalFrames);
			
			int filespace_id = H5.H5Dget_space(ave_data_id);
			int type_id = H5.H5Dget_type(ave_data_id);
			long[] ave_start = (long[]) ConvertUtils.convert(currentFrame, long[].class);
			long[] ave_step = (long[]) ConvertUtils.convert(step, long[].class);
			int memspace_id = H5.H5Screate_simple(ave_step.length, ave_step, null);
			H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET,
					ave_start, ave_step, ave_count_data, ave_step);
			H5.H5Dwrite(ave_data_id, type_id, memspace_id, filespace_id,
					HDF5Constants.H5P_DEFAULT, ave_frame.getBuffer());
		}
	}

	private DataSliceIdentifiers executeSelection(int dim, int[] frames, DataSliceIdentifiers bgIds, int output_group_id) throws HDF5Exception {
		
		int[] datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
		int[] imageSize = Arrays.copyOfRange(frames, frames.length - dim, frames.length);
		ArrayList<int[]> list = NcdDataUtils.createSliceList(bgFrameSelection, datDimMake);
		for (int i = 0; i < datDimMake.length; i++)
			datDimMake[i] = list.get(i).length;
		long[] bgFramesTotal = (long[]) ConvertUtils.convert(ArrayUtils.addAll(datDimMake, imageSize), long[].class);

		
		long[] block = new long[frames.length];
		block = Arrays.copyOf((long[]) ConvertUtils.convert(frames, long[].class), block.length);
		Arrays.fill(block, 0, block.length - dim, 1);
		int[] block_int = (int[]) ConvertUtils.convert(block, int[].class);
		
		long[] count = new long[frames.length];
		Arrays.fill(count, 1);
		
		int dtype = HDF5Loader.getDtype(bgIds.dataclass_id, bgIds.datasize_id);
		AbstractDataset data = AbstractDataset.zeros(block_int, dtype);
		int output_data_id = NcdNexusUtils.makedata(output_group_id, "data", bgIds.datatype_id, frames.length, bgFramesTotal, true, "counts");
		int output_dataspace_id = H5.H5Dget_space(output_data_id);
		
		MultidimensionalCounter bgFrameCounter = new MultidimensionalCounter(datDimMake);
		Iterator iter = bgFrameCounter.iterator();
		while (iter.hasNext()) {
			iter.next();
			long[] bgFrame = (long[]) ConvertUtils.convert(iter.getCounts(), long[].class);
			long[] gridFrame = new long[datDimMake.length];
			for (int i = 0; i < datDimMake.length; i++)
				gridFrame[i] = list.get(i)[(int) bgFrame[i]];
			
				long[] start = new long[frames.length];
				start = Arrays.copyOf(gridFrame, frames.length);
				long[] writePosition = new long[frames.length];
				writePosition = Arrays.copyOf(bgFrame, frames.length);
				
				int memspace_id = H5.H5Screate_simple(block.length, block, null);
				H5.H5Sselect_hyperslab(bgIds.dataspace_id, HDF5Constants.H5S_SELECT_SET,
						start, block, count, block);
				H5.H5Dread(bgIds.dataset_id, bgIds.datatype_id, memspace_id, bgIds.dataspace_id,
						HDF5Constants.H5P_DEFAULT, data.getBuffer());
				
				H5.H5Sselect_hyperslab(output_dataspace_id, HDF5Constants.H5S_SELECT_SET,
						writePosition, block, count, block);
				H5.H5Dwrite(output_data_id, bgIds.datatype_id, memspace_id, output_dataspace_id,
						HDF5Constants.H5P_DEFAULT, data.getBuffer());
		}
		
		DataSliceIdentifiers outputIds = new DataSliceIdentifiers();
		int output_datatype_id = H5.H5Dget_type(output_data_id);
		int output_dataclass_id = H5.H5Tget_class(output_datatype_id);
		int output_datasize_id = H5.H5Tget_size(output_datatype_id);
		
		outputIds.setIDs(output_data_id, output_dataspace_id, output_dataclass_id, output_datatype_id, output_datasize_id);
		return outputIds;
	}
}
