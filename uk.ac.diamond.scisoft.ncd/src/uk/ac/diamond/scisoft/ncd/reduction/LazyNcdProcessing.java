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

import javax.measure.quantity.Length;
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
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.IntegerDataset;
import uk.ac.diamond.scisoft.analysis.dataset.SliceIterator;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
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
	private Amount<Length> cameraLength;
	private Unit<Length> qaxisUnit;
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
	
	private static ILock lock;
	
	private int cores;
    private long maxMemory;
    
    private abstract class DataReductionJob extends Job {

		protected int dim;
    	protected DataSliceIdentifiers tmp_ids, tmp_calibration_ids, tmp_bgIds;
		protected SliceSettings currentSliceParams;
		
		public DataReductionJob(String name) {
			super(name);
			
			tmp_ids = null;
			setCurrentSliceParams(null);
		}
		
		protected void setDim(int dim) {
			this.dim = dim;
		}

		protected DataSliceIdentifiers getResultIds() {
			return tmp_ids;
		}

		protected void setResultIds(DataSliceIdentifiers tmp_ids) {
			this.tmp_ids = new DataSliceIdentifiers(tmp_ids);
		}

		protected void setCalibrationIds(DataSliceIdentifiers calibration_ids) {
			this.tmp_calibration_ids = new DataSliceIdentifiers(calibration_ids);
		}

		protected void setBgIds(DataSliceIdentifiers bg_ids) {
			this.tmp_bgIds = new DataSliceIdentifiers(bg_ids);
		}

		protected SliceSettings getCurrentSliceParams() {
			return currentSliceParams;
		}

		protected void setCurrentSliceParams(SliceSettings currentSliceParams) {
			this.currentSliceParams = new SliceSettings(currentSliceParams);
		}
    	
    }
	
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
		
		frameBatch = 1;
		
		cores = Runtime.getRuntime().availableProcessors();
		maxMemory = Runtime.getRuntime().maxMemory();
		
		lock = Job.getJobManager().newLock();
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

	public void setCameraLength(Amount<Length> cameraLength) {
		this.cameraLength = cameraLength;
	}

	public void setUnit(Unit<Length> unit) {
		if (unit == null) {
			this.qaxisUnit = null;
			return;
		}
		// q-axis units need to be inverse of the linear dimension units
		this.qaxisUnit = unit.inverse().asType(Length.class);
	}

	
	public void execute(String detector, int dim, String filename, final IProgressMonitor monitor) throws NullPointerException, HDF5Exception {
		
		String[] tmpName = FilenameUtils.getName(filename).split("_");
		final String monitorFile = tmpName[tmpName.length - 1];
		
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
		
	    final int sec_group_id;
	    final int sec_data_id;
	    final int az_data_id;
	    final AbstractDataset[] areaData;
		int secRank = rank - dim + 1;
		long[] secFrames = Arrays.copyOf(frames, secRank);
		AbstractDataset qaxis = null;
		final LazySectorIntegration lazySectorIntegration = new LazySectorIntegration();
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
			
			intSector.setAverageArea(false);
			lazySectorIntegration.setIntSector(intSector);
			if(enableMask)
				lazySectorIntegration.setMask(mask);
			
			qaxis = calculateQaxisDataset(detector, dim, secFrames);
			if (qaxis != null) {
				lazySectorIntegration.setQaxis(qaxis, qaxisUnit);
				lazySectorIntegration.setCalibrationData(slope, intercept);
				lazySectorIntegration.setCameraLength(cameraLength);
				lazySectorIntegration.writeQaxisData(sec_group_id);
			}
			
			lazySectorIntegration.writeNcdMetadata(sec_group_id);
			areaData = ROIProfile.area(Arrays.copyOfRange(frames_int, rank - dim, rank), mask,
					intSector, flags.isEnableRadial(), flags.isEnableAzimuthal(), flags.isEnableFastintegration());
		} else {
			sec_group_id = -1;
			sec_data_id = -1;
			az_data_id = -1;
			areaData = null;
		}
		
	    final int inv_group_id;
	    final int inv_data_id;
		final int invRank = flags.isEnableSector() ? secRank - 1: rank - dim;
		final LazyInvariant lazyInvariant = new LazyInvariant();
		if(flags.isEnableInvariant()) {
		    inv_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyInvariant.name, "NXdetector");
			int type = HDF5Constants.H5T_NATIVE_FLOAT;
			long[] invFrames = Arrays.copyOf(flags.isEnableSector() ? secFrames : frames, invRank);
			inv_data_id = NcdNexusUtils.makedata(inv_group_id, "data", type, invRank, invFrames, true, "counts");
			
			lazyInvariant.writeNcdMetadata(inv_group_id);
		} else {
		    inv_group_id = -1;
		    inv_data_id = -1;
		}
		
		
		DataSliceIdentifiers calibration_ids = null;
		
		int rankCal;
		final long[] framesCal;
		
	    final int dr_group_id;
	    final int dr_data_id;
		final LazyDetectorResponse lazyDetectorResponse = new LazyDetectorResponse(drFile, detector);
		if(flags.isEnableDetectorResponse()) {
		    dr_group_id = NcdNexusUtils.makegroup(processing_group_id, LazyDetectorResponse.name, "NXdetector");
		    int type = HDF5Constants.H5T_NATIVE_FLOAT;
			dr_data_id = NcdNexusUtils.makedata(dr_group_id, "data", type, rank, frames, true, "counts");
			
			lazyDetectorResponse.createDetectorResponseInput();
			lazyDetectorResponse.writeNcdMetadata(dr_group_id);
		} else {
		    dr_group_id = -1;
		    dr_data_id = -1;
		}
	    
	    final int norm_group_id;
	    final int norm_data_id;
		final LazyNormalisation lazyNormalisation = new LazyNormalisation();
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
		} else {
		    norm_group_id = -1;
		    norm_data_id = -1;
		    framesCal = null;
		}
		
	    final int bg_group_id; 
	    final int bg_data_id; 
	    int bgRank;
	    DataSliceIdentifiers bgIds = null;
		final long[] bgFrames;
		int[] bgFrames_int;
		final LazyBackgroundSubtraction lazyBackgroundSubtraction = new LazyBackgroundSubtraction();
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
		} else {
		    bg_group_id = -1; 
		    bg_data_id = -1;
		    bgFrames = null;
		    bgFrames_int = null;
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
				for (int i = sliceIdx.length - 1; i >= 0; i--) {
					if (sliceIdx[i] != frames_int[i]) {
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
		int[] newShape = AbstractDataset.checkSlice(iter_array, start, iter_array, start, iter_array, step);
		IndexIterator iter = new SliceIterator(iter_array, AbstractDataset.calcSize(iter_array), start, step, newShape);
		
		if (flags.isEnableSector() && dim == 2) {
			ArrayList<Job> sectorJobList = new ArrayList<Job>();
			ArrayList<Job> runningJobList = new ArrayList<Job>();
			
			while (iter.hasNext()) {
				
				sliceParams.setStart(iter.getPos());

				DataReductionJob sectorJob = new DataReductionJob("Sector Integration") {

					@Override
					protected IStatus run(IProgressMonitor jobmonitor) {
						try {
							AbstractDataset data;
							try {
								lock.acquire();
								data = NcdNexusUtils.sliceInputData(currentSliceParams, tmp_ids);
							} catch (Exception e) {
								throw e;
							} finally {
								lock.release();
							}

							if (flags.isEnableDetectorResponse()) {
								monitor.setTaskName(monitorFile + " : Correct for detector response");

								tmp_ids.setIDs(dr_group_id, dr_data_id);
								data = lazyDetectorResponse.execute(dim, data, tmp_ids, lock);
							}

							DataSliceIdentifiers sector_id = new DataSliceIdentifiers(tmp_ids);
							DataSliceIdentifiers azimuth_id = new DataSliceIdentifiers(tmp_ids);
							monitor.setTaskName(monitorFile + " : Performing sector integration");
							sector_id.setIDs(sec_group_id, sec_data_id);
							azimuth_id.setIDs(sec_group_id, az_data_id);
							
							LazySectorIntegration tmpLazySectorIntegration = new LazySectorIntegration();
							tmpLazySectorIntegration.setIntSector(intSector);
							tmpLazySectorIntegration.setCalculateRadial(flags.isEnableRadial());
							tmpLazySectorIntegration.setCalculateAzimuthal(flags.isEnableAzimuthal());
							tmpLazySectorIntegration.setFast(flags.isEnableFastintegration());
							tmpLazySectorIntegration.setMask(mask);
							tmpLazySectorIntegration.setAreaData(areaData);
							tmpLazySectorIntegration.execute(dim, data, sector_id, azimuth_id, lock);
						} catch (Exception e) {
							e.printStackTrace();
							return Status.CANCEL_STATUS;
						}

						return Status.OK_STATUS;
					}
				};
				
				sectorJob.setDim(dim);
				sectorJob.setResultIds(input_ids);
				sectorJob.setCurrentSliceParams(sliceParams);
				sectorJobList.add(sectorJob);
				
			}
			
			for (Job job : sectorJobList) {
				while (runningJobList.size() >= cores) {
					try {
						runningJobList.get(0).join();
						runningJobList.remove(0);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				job.schedule();
				runningJobList.add(job);
			}
			
			for (Job job : sectorJobList) {
				try {
					job.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			dim = 1;
			rank = secRank;
			sliceDim = 0;
			sliceSize = (int) secFrames[0];
			
			frames = secFrames;
			frames_int = (int[]) ConvertUtils.convert(secFrames, int[].class);

			sliceParams = new SliceSettings(frames, sliceDim, sliceSize);
			IntegerDataset idx_dataset = new IntegerDataset(new int[] {sliceSize}, new int[] {1});
			iter = idx_dataset.getSliceIterator(new int[] {0}, new int[] {sliceSize}, new int[] {sliceSize});
			
			input_ids.setIDs(sec_group_id, sec_data_id);
		}

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
		
		final int[] final_bgFrames_int = flags.isEnableBackground() ? Arrays.copyOf(bgFrames_int, bgFrames_int.length) : null;
		ArrayList<DataReductionJob> processingJobList = new ArrayList<DataReductionJob>();
		ArrayList<DataReductionJob> runningJobList = new ArrayList<DataReductionJob>();
		
		while (iter.hasNext()) {
			sliceParams.setStart(iter.getPos());

			DataReductionJob processingJob = new DataReductionJob("Data Reduction") {

				@Override
				protected IStatus run(IProgressMonitor jobmonitor) {
					try {
						AbstractDataset data;
						int finalSliceDim = currentSliceParams.getSliceDim();
						int finalSliceSize = currentSliceParams.getSliceSize();
						
						try {
							lock.acquire();
							data = NcdNexusUtils.sliceInputData(currentSliceParams, tmp_ids);
						} catch (Exception e) {
							throw e;
						} finally {
							lock.release();
						}

						if (flags.isEnableDetectorResponse() && !flags.isEnableSector()) {
							monitor.setTaskName(monitorFile + " : Correct for detector response");

							tmp_ids.setIDs(dr_group_id, dr_data_id);
							data = lazyDetectorResponse.execute(dim, data, tmp_ids, lock);
						}

						if (flags.isEnableNormalisation()) {
							monitor.setTaskName(monitorFile + " : Normalising data");

							SliceSettings calibrationSliceParams = new SliceSettings(currentSliceParams);
							calibrationSliceParams.setFrames(framesCal);
							AbstractDataset dataCal = NcdNexusUtils.sliceInputData(calibrationSliceParams,
									tmp_calibration_ids);

							tmp_ids.setIDs(norm_group_id, norm_data_id);
							data = lazyNormalisation.execute(dim, data, dataCal, tmp_ids, lock);
						}

						if (flags.isEnableBackground()) {
							monitor.setTaskName(monitorFile + " : Correct for detector response");

							int bgSliceSize = Math.min(finalSliceSize, final_bgFrames_int[finalSliceDim]);
							int[] bgStart = new int[finalSliceDim + 1];
							for (int i = 0; i <= finalSliceDim; i++)
								bgStart[i] = Math.min(currentSliceParams.getStart()[i], final_bgFrames_int[i] - 1);
							SliceSettings bgSliceParams = new SliceSettings(bgFrames, finalSliceDim, bgSliceSize);
							bgSliceParams.setStart(bgStart);
							AbstractDataset bgData = NcdNexusUtils.sliceInputData(bgSliceParams, tmp_bgIds);

							tmp_ids.setIDs(bg_group_id, bg_data_id);
							AbstractDataset[] remapData = NcdDataUtils.matchDataDimensions(data, bgData);
							remapData[0] = lazyBackgroundSubtraction.execute(dim, remapData[0], remapData[1], tmp_ids, lock);

							// restore original axis order in output dataset
							data = DatasetUtils.transpose(remapData[0], (int[]) remapData[3].getBuffer());
						}

						if (flags.isEnableInvariant()) {
							monitor.setTaskName(monitorFile + " : Calculating invariant");

							DataSliceIdentifiers inv_id = new DataSliceIdentifiers(tmp_ids);
							inv_id.setIDs(inv_group_id, inv_data_id);
							inv_id.start = Arrays.copyOf(tmp_ids.start, invRank);
							inv_id.stride = Arrays.copyOf(tmp_ids.stride, invRank);
							inv_id.count = Arrays.copyOf(tmp_ids.count, invRank);
							inv_id.block = Arrays.copyOf(tmp_ids.block, invRank);

							lazyInvariant.execute(dim, data, inv_id, lock);
						}
					} catch (Exception e) {
						e.printStackTrace();
						return Status.CANCEL_STATUS;
					}

					return Status.OK_STATUS;
				}
			};
			
			processingJob.setDim(dim);
			processingJob.setResultIds(input_ids);
			if (flags.isEnableNormalisation())
				processingJob.setCalibrationIds(calibration_ids);
			if(flags.isEnableBackground())
				processingJob.setBgIds(bgIds);
			processingJob.setCurrentSliceParams(sliceParams);
			processingJobList.add(processingJob);
		}

		for (DataReductionJob job : processingJobList) {
			while (runningJobList.size() >= cores) {
				try {
					runningJobList.get(0).join();
					runningJobList.remove(0);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			job.schedule();
			runningJobList.add(job);
		}
		
		for (DataReductionJob job : processingJobList) {
			try {
				job.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		if (!processingJobList.isEmpty()) {
			DataReductionJob tmpJob = processingJobList.get(0);
			input_ids = new DataSliceIdentifiers(tmpJob.getResultIds());
			sliceParams = new SliceSettings(tmpJob.getCurrentSliceParams());
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
		
		Runtime.getRuntime().gc();
		
		int batchSize = 4; // use 4 byte data size
		for (int i = frames.length - dim; i < frames.length; i++)
			batchSize *= frames[i];
		
		frameBatch = Math.max(1, (int) (maxMemory / (10 * batchSize * cores)));
	}

	private AbstractDataset calculateQaxisDataset(String detector, int dim, long[] frames) {
		
		AbstractDataset qaxis = null;
		
		if (crb != null) {
			if (crb.containsKey(detector)) {
				if (slope == null) slope = crb.getFunction(detector).getParameterValue(0);
				if (intercept == null) intercept = crb.getFunction(detector).getParameterValue(1);
				if (qaxisUnit == null) setUnit(crb.getUnit(detector));
				cameraLength = crb.getMeanCameraLength(detector);
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
