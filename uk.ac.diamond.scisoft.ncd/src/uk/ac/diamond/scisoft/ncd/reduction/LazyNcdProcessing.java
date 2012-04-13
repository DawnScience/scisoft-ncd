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

import java.util.ArrayList;
import java.util.Arrays;

import javax.measure.unit.Unit;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.util.MultidimensionalCounter;

import org.eclipse.core.runtime.IProgressMonitor;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.IntegerDataset;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class LazyNcdProcessing {

	private boolean enableMask;
	private int normChannel;
	private String bgDetector;
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
	private String gridAverage;
	
	private int frameBatch;
	
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
		
		frameBatch = 5;
		
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

	public void setBgDetector(String bgDetector) {
		this.bgDetector = bgDetector;
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
		this.flags = new NcdReductionFlags(flags);
	}

	public void setNcdDetectors(NcdDetectors ncdDetectors) {
		this.ncdDetectors = new NcdDetectors(ncdDetectors);
	}

	public void setFirstFrame(Integer firstFrame) {
		this.firstFrame = firstFrame;
	}

	public void setLastFrame(Integer lastFrame) {
		this.lastFrame = lastFrame;
	}

	public void setFrameSelection(String frameSelection) {
		this.frameSelection = frameSelection;
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
		// q-axis units need to be inverse of the linear dimension units
		this.qaxisUnit = (unit != null ? Unit.valueOf(unit).inverse().toString() : null);
	}

	
	public void execute(String detector, int dim, String filename, IProgressMonitor monitor) throws NullPointerException, HDF5Exception {
		
		String[] tmpName = FilenameUtils.getName(filename).split("_");
		String monitorFile = tmpName[tmpName.length - 1];
		
		int nxsfile_handle = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_group_id = H5.H5Gopen(nxsfile_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		
		DataSliceIdentifiers input_ids = NcdNexusUtils.readDataId(filename, detector);
		
		int rank = H5.H5Sget_simple_extent_ndims(input_ids.dataspace_id);
		long[] frames = new long[rank];
		H5.H5Sget_simple_extent_dims(input_ids.dataspace_id, frames, null);
		int[] frames_int = (int[]) ConvertUtils.convert(frames, int[].class);
		
		int processing_group_id = NcdNexusUtils.makegroup(entry_group_id, detector + "_processing", "NXinstrument");
		
		if (firstFrame != null || lastFrame != null) {
			frameSelection = StringUtils.leftPad("", rank - dim - 1, ";");
			if (firstFrame != null)
				frameSelection += Integer.toString(firstFrame);
			frameSelection += "-";
			if (lastFrame != null)
				frameSelection += Integer.toString(lastFrame);
			frameSelection += ";";
		}
		
		if (frameSelection != null) {
		    int sel_group_id = NcdNexusUtils.makegroup(processing_group_id, LazySelection.name, "NXdetector");
		    
		    LazySelection selection = new LazySelection(frames_int);
		    selection.setFormat(frameSelection);
		    input_ids = selection.execute(dim, input_ids, sel_group_id);
			H5.H5Sget_simple_extent_dims(input_ids.dataspace_id, frames, null);
			frames_int = (int[]) ConvertUtils.convert(frames, int[].class);
		}
		
	    int sec_group_id = -1;
	    int sec_data_id = -1;
	    int az_data_id = -1;
		int secRank = rank - dim + 1;
		long[] secFrames = Arrays.copyOf(frames, secRank);
		AbstractDataset qaxis = null;
		LazySectorIntegration lazySectorIntegration = new LazySectorIntegration();
		if(flags.isEnableSector() && dim == 2) {
		    sec_group_id = NcdNexusUtils.makegroup(processing_group_id, LazySectorIntegration.name, "NXdetector");
			int type = HDF5Constants.H5T_NATIVE_FLOAT;
			int[] intRadii = intSector.getIntRadii();
			double[] radii = intSector.getRadii();
			double dpp = intSector.getDpp();
			secFrames[secRank - 1] = intRadii[1] - intRadii[0] + 1;
			sec_data_id = NcdNexusUtils.makedata(sec_group_id, "data", type, secRank, secFrames, true, "counts");
			
			double[] angles = intSector.getAngles();
			long[] azFrames = Arrays.copyOf(frames, secRank);
			if (intSector.getSymmetry() == SectorROI.FULL)
				angles[1] = angles[0] + 2 * Math.PI;
			if (intSector.getSymmetry() != SectorROI.NONE && intSector.getSymmetry() != SectorROI.FULL) {
				 throw new IllegalArgumentException("Using symmetry related sectors in SectorIntegration stage is currently is not supported.");
			}
			azFrames[secRank - 1] = (int) Math.ceil((angles[1] - angles[0]) * radii[1] * dpp);
			az_data_id = NcdNexusUtils.makedata(sec_group_id, "azimuth", type, secRank, azFrames, false, "counts");
			
			lazySectorIntegration.setIntSector(intSector);
			if(enableMask)
				lazySectorIntegration.setMask(mask);
			lazySectorIntegration.setCameraLength(cameraLength);
			
			qaxis = calculateQaxisDataset(detector, dim, secFrames);
			if (qaxis != null) {
				lazySectorIntegration.setQaxis(qaxis, qaxisUnit);
				lazySectorIntegration.setCalibrationData(slope, intercept);
				lazySectorIntegration.writeQaxisData(sec_group_id);
			}
			
			lazySectorIntegration.writeNcdMetadata(sec_group_id);
		}
		
	    int inv_group_id = -1;
	    int inv_data_id = -1;
		int invRank = flags.isEnableSector() ? secRank - 1: rank - dim;
		LazyInvariant lazyInvariant = new LazyInvariant();
		if(flags.isEnableInvariant()) {
		    inv_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyInvariant.name, "NXdetector");
			int type = HDF5Constants.H5T_NATIVE_FLOAT;
			long[] invFrames = Arrays.copyOf(flags.isEnableSector() ? secFrames : frames, invRank);
			inv_data_id = NcdNexusUtils.makedata(inv_group_id, "data", type, invRank, invFrames, true, "counts");
			
			lazyInvariant.writeNcdMetadata(inv_group_id);
		}
		
		
		DataSliceIdentifiers calibration_ids = null;
		
		int rankCal;
		long[] framesCal = null;
		
	    int dr_group_id = -1;
	    int dr_data_id = -1;
		LazyDetectorResponse lazyDetectorResponse = new LazyDetectorResponse(drFile, detector);
		if(flags.isEnableDetectorResponse()) {
		    dr_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyDetectorResponse.name, "NXdetector");
		    int type = HDF5Constants.H5T_NATIVE_FLOAT;
			dr_data_id = NcdNexusUtils.makedata(dr_group_id, "data", type, rank, frames, true, "counts");
			
			lazyDetectorResponse.createDetectorResponseInput();
			lazyDetectorResponse.writeNcdMetadata(dr_group_id);
		}
	    
	    int norm_group_id = -1;
	    int norm_data_id = -1;
		LazyNormalisation lazyNormalisation = new LazyNormalisation();
		if(flags.isEnableNormalisation()) {
		    norm_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyNormalisation.name, "NXdetector");
		    int type = HDF5Constants.H5T_NATIVE_FLOAT;
			norm_data_id = NcdNexusUtils.makedata(norm_group_id, "data", type, flags.isEnableSector() ? secRank : rank,
					flags.isEnableSector() ? secFrames : frames, true, "counts");
			
			calibration_ids = NcdNexusUtils.readDataId(filename, calibration);
			
			rankCal = H5.H5Sget_simple_extent_ndims(calibration_ids.dataspace_id);
			framesCal = new long[rankCal];
			H5.H5Sget_simple_extent_dims(calibration_ids.dataspace_id, framesCal, null);
			
			lazyNormalisation.setAbsScaling(absScaling);
			lazyNormalisation.setNormChannel(normChannel);
			
			if (qaxis != null) {
				lazyNormalisation.setQaxis(qaxis, qaxisUnit);
				lazyNormalisation.writeQaxisData(norm_group_id);
			}
			lazyNormalisation.writeNcdMetadata(norm_group_id);
		}
		
	    int bg_group_id = -1; 
	    int bg_data_id = -1; 
	    int bgRank;
	    DataSliceIdentifiers bgIds = null;
		long[] bgFrames = null;
		int[] bgFrames_int = null;
		LazyBackgroundSubtraction lazyBackgroundSubtraction = new LazyBackgroundSubtraction();
		if(flags.isEnableBackground()) {
		    bg_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyBackgroundSubtraction.name, "NXdetector");
		    int type = HDF5Constants.H5T_NATIVE_FLOAT;
			bg_data_id = NcdNexusUtils.makedata(bg_group_id, "data", type, flags.isEnableSector() ? secRank : rank,
					flags.isEnableSector() ? secFrames : frames, true, "counts");
			
		    bgIds = NcdNexusUtils.readDataId(bgFile, bgDetector);
		    bgRank = H5.H5Sget_simple_extent_ndims(bgIds.dataspace_id);
			bgFrames = new long[bgRank];
			H5.H5Sget_simple_extent_dims(bgIds.dataspace_id, bgFrames, null);
			bgFrames_int = (int[]) ConvertUtils.convert(bgFrames, int[].class);
			lazyBackgroundSubtraction.setBgScale(bgScaling);
			
			if (qaxis != null) {
				lazyBackgroundSubtraction.setQaxis(qaxis, qaxisUnit);
				lazyBackgroundSubtraction.writeQaxisData(bg_group_id);
			}
			lazyBackgroundSubtraction.writeNcdMetadata(bg_group_id);
		}
	    
		int sliceDim = 0;
		int sliceSize = (int) frames[0];
		estimateFrameBatchSize(dim, frames);

		// We will slice only 2D data. 1D data is loaded into memory completely
		if (dim == 2) {
			// Find dimension that needs to be sliced
			MultidimensionalCounter dimCounter = new MultidimensionalCounter(Arrays.copyOfRange(frames_int, 0, rank - dim));
			if (dimCounter.getSize() > frameBatch) {
				int[] sliceIdx = dimCounter.getCounts(frameBatch);
				for (int i = 0; i < sliceIdx.length; i++) {
					int idx = sliceIdx.length - 1 - i;
					if (sliceIdx[i] != frames_int[idx]) {
						sliceDim = i;
						break;
					}
				}
				sliceSize = sliceIdx[sliceDim];
			}
		}
		
		SliceSettings sliceParams = new SliceSettings(frames, sliceDim, sliceSize);
		
		int[] iter_array = Arrays.copyOfRange(frames_int, 0, sliceDim + 1);
		int [] start = new int[iter_array.length];
		int[] step =  new int[iter_array.length];
		Arrays.fill(start, 0);
		Arrays.fill(step, 1);
		step[sliceDim] = sliceSize;
		IntegerDataset idx_dataset = new IntegerDataset(iter_array);
		IndexIterator iter = idx_dataset.getSliceIterator(start, iter_array, step);
		
		if (flags.isEnableSector() && dim == 2) {
			while (iter.hasNext()) {
				sliceParams.setStart(iter.getPos());
				AbstractDataset data = NcdNexusUtils.sliceInputData(sliceParams, input_ids);

				if (flags.isEnableDetectorResponse()) {
					monitor.setTaskName(monitorFile + " : Correct for detector response");

					input_ids.setIDs(dr_group_id, dr_data_id);
					data = lazyDetectorResponse.execute(dim, data, input_ids);
				}

				monitor.setTaskName(monitorFile + " : Performing sector integration");
				DataSliceIdentifiers sector_id = new DataSliceIdentifiers(input_ids);
				sector_id.setIDs(sec_group_id, sec_data_id);
				DataSliceIdentifiers azimuth_id = new DataSliceIdentifiers(input_ids);
				azimuth_id.setIDs(sec_group_id, az_data_id);
				
				data = lazySectorIntegration.execute(dim, data, sector_id, azimuth_id)[1];
			}

			dim = 1;
			rank = secRank;
			sliceDim = 0;
			sliceSize = (int) secFrames[0];
			
			frames = secFrames;
			frames_int = (int[]) ConvertUtils.convert(secFrames, int[].class);

			sliceParams = new SliceSettings(frames, sliceDim, sliceSize);
			idx_dataset = new IntegerDataset(new int[] {sliceSize}, new int[] {1});
			iter = idx_dataset.getSliceIterator(new int[] {0}, new int[] {sliceSize}, new int[] {sliceSize});
			
			input_ids.setIDs(sec_group_id, sec_data_id);
		}

		AbstractDataset data = null;
		if (flags.isEnableBackground())
			if (!Arrays.equals(bgFrames_int, frames_int)) {
				ArrayList<Integer> bgAverageIndices = new ArrayList<Integer>();
				for (int i = 0; i < (rank - dim); i++)
					if (bgFrames[i] != frames[i] && bgFrames[i] != 1) {
						bgAverageIndices.add(i + 1);
						bgFrames[i] = 1;
					}
				if (bgAverageIndices.size() > 0) {
					int bgfile_handle = H5.H5Fopen(bgFile, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
					int bgentry_group_id = H5.H5Gopen(bgfile_handle, "entry1", HDF5Constants.H5P_DEFAULT);
					int bgdata_group_id = NcdNexusUtils.makegroup(bgentry_group_id,
							detector + "_result_" + monitorFile, "NXdata");

					LazyAverage lazyAverage = new LazyAverage();
					lazyAverage.setAverageIndices(ArrayUtils.toPrimitive(bgAverageIndices.toArray(new Integer[] {})));
					lazyAverage.execute(dim, bgFrames_int,bgdata_group_id, frameBatch, bgIds);

					bgFrames_int = (int[]) ConvertUtils.convert(bgFrames, int[].class);
				}
			}
		
		while (iter.hasNext()) {
			sliceParams.setStart(iter.getPos());
			data = NcdNexusUtils.sliceInputData(sliceParams, input_ids);

			if (flags.isEnableDetectorResponse() && !flags.isEnableSector()) {
				monitor.setTaskName(monitorFile + " : Correct for detector response");

				input_ids.setIDs(dr_group_id, dr_data_id);
				data = lazyDetectorResponse.execute(dim, data, input_ids);
			}

			if (flags.isEnableNormalisation()) {
				monitor.setTaskName(monitorFile + " : Normalising data");

				SliceSettings calibrationSliceParams = new SliceSettings(sliceParams);
				calibrationSliceParams.setFrames(framesCal);
				AbstractDataset dataCal = NcdNexusUtils.sliceInputData(calibrationSliceParams, calibration_ids);

				input_ids.setIDs(norm_group_id, norm_data_id);
				data = lazyNormalisation.execute(dim, data, dataCal, input_ids);
			}

			if (flags.isEnableBackground()) {
				monitor.setTaskName(monitorFile + " : Correct for detector response");

				int bgSliceSize = Math.min(sliceSize, bgFrames_int[sliceDim]);
				int[] bgStart = new int[sliceDim + 1]; 
				for (int i = 0; i <= sliceDim; i++)
					bgStart[i] = Math.min(sliceParams.getStart()[i], bgFrames_int[i] - 1);
				SliceSettings bgSliceParams = new SliceSettings(bgFrames, sliceDim, bgSliceSize);
				bgSliceParams.setStart(bgStart);
				AbstractDataset bgData = NcdNexusUtils.sliceInputData(bgSliceParams, bgIds);
				
				input_ids.setIDs(bg_group_id, bg_data_id);
				AbstractDataset[] remapData = NcdDataUtils.matchDataDimensions(data, bgData);
				remapData[0] = lazyBackgroundSubtraction.execute(dim, remapData[0], remapData[1], input_ids);

				// restore original axis order in output dataset
				data = DatasetUtils.transpose(remapData[0], (int[]) remapData[3].getBuffer());
			}

			if (flags.isEnableInvariant()) {
				monitor.setTaskName(monitorFile + " : Calculating invariant");
				
				DataSliceIdentifiers inv_id = new DataSliceIdentifiers(input_ids);
				inv_id.setIDs(inv_group_id, inv_data_id);
				inv_id.start = Arrays.copyOf(input_ids.start, invRank);
				inv_id.stride = Arrays.copyOf(input_ids.stride, invRank);
				inv_id.count = Arrays.copyOf(input_ids.count, invRank);
				inv_id.block = Arrays.copyOf(input_ids.block, invRank);
            
				lazyInvariant.execute(dim, data, inv_id);
			}
		}

		if(flags.isEnableAverage()) {
			monitor.setTaskName(monitorFile + " : Averaging  datasets");
			int[] averageIndices = new int[] {frames.length - dim};
			if (gridAverage != null)
				averageIndices = NcdDataUtils.createGridAxesList(gridAverage, frames.length - dim + 1);
			
			LazyAverage lazyAverage = new LazyAverage();
			lazyAverage.setAverageIndices(averageIndices);
			lazyAverage.execute(dim, frames_int, processing_group_id, frameBatch, input_ids);
			
			if (qaxis != null) {
				lazyAverage.setQaxis(qaxis, qaxisUnit);
				lazyAverage.writeQaxisData(input_ids.datagroup_id);
			}
			lazyAverage.writeNcdMetadata(input_ids.datagroup_id);
		}
		
	    int result_group_id = NcdNexusUtils.makegroup(entry_group_id, detector+"_result", "NXdata");
	    H5.H5Lcreate_hard(input_ids.datagroup_id, "./data", result_group_id, "./data", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
	    if (qaxis != null) {
		    H5.H5Lcreate_hard(input_ids.datagroup_id, "./q", result_group_id, "./q", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
	    }
	    H5.H5Gclose(result_group_id);
	    
	    if (flags.isEnableDetectorResponse()) {
	    	H5.H5Dclose(dr_data_id);
	    	H5.H5Gclose(dr_group_id);
	    }
	    
	    if (flags.isEnableSector() && dim == 2) {
	    	H5.H5Dclose(sec_data_id);
	    	H5.H5Dclose(az_data_id);
	    	H5.H5Gclose(sec_group_id);
	    }
	    
	    if (flags.isEnableBackground()) {
	    	H5.H5Dclose(bg_data_id);
	    	H5.H5Gclose(bg_group_id);
	    }
	    
	    if (flags.isEnableNormalisation()) {
	    	H5.H5Dclose(norm_data_id);
	    	H5.H5Gclose(norm_group_id);
	    }
	    
	    
	    if (flags.isEnableInvariant()) {
	    	H5.H5Dclose(inv_data_id);
	    	H5.H5Gclose(inv_group_id);
	    }
	    
	    if (flags.isEnableAverage()) {
	    	H5.H5Dclose(input_ids.dataset_id);
	    	H5.H5Gclose(input_ids.datagroup_id);
	    }
	    
	    H5.H5Gclose(processing_group_id);
	    H5.H5Gclose(entry_group_id);
	    H5.H5Fclose(nxsfile_handle);
	}
	
	private void estimateFrameBatchSize(int dim, long[] frames) {
	    Runtime runtime = Runtime.getRuntime();
	    long maxMemory = runtime.maxMemory();

		int batchSize = 4; // use 4 byte data size
		for (int i = frames.length - dim; i < frames.length; i++)
			batchSize *= frames[i];
		
		frameBatch = (int) (maxMemory / (20 * batchSize));
	}

	private AbstractDataset calculateQaxisDataset(String detector, int dim, long[] frames) {
		
		AbstractDataset qaxis = null;
		
		if (crb != null) {
			if (crb.containsKey(detector)) {
				if (slope == null) slope = crb.getFuction(detector).getParameterValue(0);
				if (intercept == null) intercept = crb.getFuction(detector).getParameterValue(1);
				cameraLength = crb.getMeanCameraLength(detector);
				setUnit(crb.getUnit(detector));
			}
		}
		
		int numPoints = (int) frames[frames.length - 1];
		if (slope != null && intercept != null) {
			if (dim == 1) {
				qaxis = AbstractDataset.zeros(new int[]{numPoints}, AbstractDataset.FLOAT32);
				double pxWaxs = ncdDetectors.getPxWaxs();
				for (int i = 0; i < numPoints; i++)
					qaxis.set(i*pxWaxs *slope + intercept, i);
			} else if (dim > 1 && flags.isEnableSector()) {
				double d2bs = intSector.getRadii()[0]; 
				qaxis = AbstractDataset.zeros(new int[]{numPoints}, AbstractDataset.FLOAT32);
				double pxSaxs = ncdDetectors.getPxSaxs();
				for (int i = 0; i < numPoints; i++)
					qaxis.set((i+d2bs)*pxSaxs *slope + intercept, i);
			}
		}
		return qaxis;
	}
}
