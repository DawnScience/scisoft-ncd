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
import org.apache.commons.collections.iterators.ArrayIterator;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.derby.iapi.services.io.NewByteArrayInputStream;
import org.apache.derby.impl.store.raw.data.SetReservedSpaceOperation;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.nexusformat.NexusException;
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.IntegerDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationResultsBean;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
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
		if(flags.isEnableInvariant()) {
		    inv_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyInvariant.name, "NXdetector");
			int type = HDF5Constants.H5T_NATIVE_FLOAT;
			int invRank = rank - dim;
			long[] invFrames = Arrays.copyOf(frames, invRank);
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
		if(flags.isEnableBackground())
		    bkg_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyBackgroundSubtraction.name, "NXdetector");
	    
	    int dr_group_id;
	    int dr_data_id = -1;
	    AbstractDataset drData = null;
		if(flags.isEnableDetectorResponse()) {
		    dr_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyDetectorResponse.name, "NXdetector");
		    int type = HDF5Constants.H5T_NATIVE_FLOAT;
			dr_data_id = NcdNexusUtils.makedata(dr_group_id, "data", type, rank, frames, true, "counts");
			
			drData = readDetectorResponseData(drFile, detector);
		}
	    
		int frameBatch = 150;	//TODO: calculate based on the image size
		
		int sliceDim = 0;
		int sliceSize = (int) frames[0];
		int lastSliceSize = 0;

		// We will slice only 2D data. 1D data is loaded into memory completely
		if (dim == 2) {
			// Find dimension that needs to be sliced
			int dimCounter = 1;
			for (int idx = (frames.length - 1 - dim); idx >= 0; idx--) {
				dimCounter *= frames[idx];
				if (dimCounter >= frameBatch) {
					sliceDim = idx;
					sliceSize = (int) (frameBatch * frames[idx] / dimCounter);
					lastSliceSize = (int) (frames[idx] % sliceSize);
					break;
				}
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

				if (flags.isEnableInvariant()) {
					monitor.setTaskName(monitorFile + " : Calculating invariant");
					
					int invRank = rank - dim;
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

		AbstractDataset data;
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
			}

			if (flags.isEnableInvariant() && !flags.isEnableSector()) {
				monitor.setTaskName(monitorFile + " : Calculating invariant");
				
				int invRank = rank - dim;
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
		    int ave_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyAverage.name, "NXdetector");
		    
			monitor.setTaskName(monitorFile + " : Averaging  datasets");
			
			int[] averageIndices = new int[] {};    //Dimension indexes that need to be averaged
			if (gridAverage != null)
				averageIndices = NcdDataUtils.createGridAxesList(gridAverage, frames.length - dim + 1);
			
			// Calculate shape of the averaged dataset based on the dimensions selected for averaging
			long[] framesAve = new long[data.getRank() - averageIndices.length];
			{
				int tmpIdx = 0;
				for (int i = 0; i < data.getRank(); i++) {
					if (ArrayUtils.contains(averageIndices, i + 1))
						continue;
					framesAve[tmpIdx] = data.getShape()[i];
					tmpIdx++;
				}
			}
			
			int[] framesAve_int = (int[]) ConvertUtils.convert(framesAve, int[].class);
		    int type = HDF5Constants.H5T_NATIVE_FLOAT;
			int ave_data_id = NcdNexusUtils.makedata(ave_group_id, "data", type, framesAve.length, framesAve, true, "counts");
			
			// Loop over dimensions that aren't averaged
			iter_array = Arrays.copyOfRange(framesAve_int, 0, framesAve_int.length - dim);
			start = new int[iter_array.length];
			step =  new int[iter_array.length];
			Arrays.fill(start, 0);
			Arrays.fill(step, 1);
			idx_dataset = new IntegerDataset(iter_array);
			iter = idx_dataset.getSliceIterator(start, iter_array, step);
			
			while (iter.hasNext()) {
				
				sliceDim = 0;
				sliceSize = data.getShape()[0];
				lastSliceSize = 0;

				// We will slice only 2D data. 1D data is loaded into memory completely
				if (averageIndices.length > 0 || dim == 2) {
					// Find dimension that needs to be sliced
					int dimCounter = 1;
					for (int idx = (data.getRank() - 1 - dim); idx >= 0; idx--) {
						if (ArrayUtils.contains(averageIndices, idx + 1)) {
							dimCounter *= data.getShape()[idx];
							if (dimCounter >= frameBatch) {
								sliceDim = idx;
								sliceSize = frameBatch * data.getShape()[idx] / dimCounter;
								lastSliceSize = data.getShape()[idx] % sliceSize;
								break;
							}
						}
					}
				}
				
				int[] currentFrame = iter.getPos();
				int[] data_iter_array = Arrays.copyOfRange(data.getShape(), 0, data.getRank() - dim);
				int [] data_start = Arrays.copyOfRange(data.getShape(), 0, data.getRank() - dim);
				int[] data_step =  new int[iter_array.length];
				int tmpIdx = 0;
				for (int idx = 0; idx < (data.getRank() - dim); idx++) {
					if (ArrayUtils.contains(averageIndices, idx + 1)) {
						data_start[idx] = 0;
						if (idx < sliceDim)
							data_step[idx] = 1;
						if (idx > sliceDim)
							data_step[idx] = data.getShape()[idx];
						if (idx == sliceDim)
							data_step[idx] = sliceSize;
					} else {
						data_start[idx] = currentFrame[tmpIdx];
						data_step[idx] = 1;
						tmpIdx++;
					}
						
				}
				IntegerDataset data_idx_dataset = new IntegerDataset(data_iter_array);
				IndexIterator data_iter = data_idx_dataset.getSliceIterator(data_start, data_iter_array, data_step);
				
				while (iter.hasNext()) {
					
					lazyAverage.setFirstFrame(firstFrame, aveDim);
					lazyAverage.setLastFrame(lastFrame, aveDim);
					lazyAverage.setQaxis(qaxis, qaxisUnit);
					
					lazyAverage.execute(tmpNXdata, aveDim, monitor);
				}
			}
			
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
}
