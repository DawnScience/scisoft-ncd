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
import java.util.List;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import ncsa.hdf.hdf5lib.structs.H5L_info_t;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.util.MultidimensionalCounter;

import org.dawb.hdf5.Nexus;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;
import org.jscience.physics.amount.Amount;
import org.jscience.physics.amount.Constants;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;
import uk.ac.diamond.scisoft.analysis.dataset.IntegerDataset;
import uk.ac.diamond.scisoft.analysis.dataset.SliceIterator;
import uk.ac.diamond.scisoft.analysis.diffraction.DetectorProperties;
import uk.ac.diamond.scisoft.analysis.diffraction.DiffractionCrystalEnvironment;
import uk.ac.diamond.scisoft.analysis.io.IDiffractionMetadata;
import uk.ac.diamond.scisoft.analysis.io.NexusDiffractionMetaReader;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.data.plots.DebyeBuechePlotData;
import uk.ac.diamond.scisoft.ncd.data.plots.GuinierPlotData;
import uk.ac.diamond.scisoft.ncd.data.plots.KratkyPlotData;
import uk.ac.diamond.scisoft.ncd.data.plots.LogLogPlotData;
import uk.ac.diamond.scisoft.ncd.data.plots.PorodPlotData;
import uk.ac.diamond.scisoft.ncd.data.plots.SaxsPlotData;
import uk.ac.diamond.scisoft.ncd.data.plots.ZimmPlotData;
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
	private Amount<ScatteringVectorOverDistance> slope;
	private Amount<ScatteringVector> intercept;
	private Amount<Length> cameraLength;
	private Amount<Energy> energy;
	private Unit<ScatteringVector> qaxisUnit;
	private BooleanDataset mask;

	private String detector;
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
    
	private int nxsfile_handle, entry_group_id, processing_group_id, detector_group_id, input_data_id, input_errors_id;
	private int result_group_id;
	
	private DataSliceIdentifiers input_ids, input_errors_ids;
	
	private String monitorFile;
	
	// Input data frames
	private int dim;
	private	int rank;
	private long[] frames;
	private  int[] frames_int;
	
	// Sector integrate data shapes
	private int secRank;
	private long[] secFrames;
	
	private AbstractDataset qaxis;
	
	private LazyDetectorResponse lazyDetectorResponse;
	private LazySectorIntegration lazySectorIntegration;
	private LazyNormalisation lazyNormalisation;
	private LazyBackgroundSubtraction lazyBackgroundSubtraction;
	private LazyInvariant lazyInvariant;
	private LazyAverage lazyAverage;
	
    private abstract class DataReductionJob extends Job {

    	protected DataSliceIdentifiers tmp_ids, tmp_errors_ids;
    	protected DataSliceIdentifiers tmp_bgIds,  tmp_errors_bgIds;
		protected SliceSettings currentSliceParams;
		
		public DataReductionJob(String name) {
			super(name);
			
			tmp_ids = null;
			tmp_errors_ids = null;
			tmp_bgIds = null;
			tmp_errors_bgIds = null;
			currentSliceParams = null;
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
		energy = null;
		qaxisUnit = null;
		mask = null;
		crb = null;
		firstFrame = null;
		lastFrame = null;
		
		flags = new NcdReductionFlags();
		ncdDetectors = new NcdDetectors();
				
		cores = Runtime.getRuntime().availableProcessors();
		maxMemory = Runtime.getRuntime().maxMemory();
		
		nxsfile_handle = -1;
		entry_group_id = -1;
		processing_group_id = -1;
		detector_group_id = -1;
		input_data_id = -1;
		input_errors_id = -1;
		
		input_ids = null;
		input_errors_ids = null;
		
		result_group_id =  -1;
		
		lock = Job.getJobManager().newLock();
		
		qaxis = null;
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

	public void setSlope(Amount<ScatteringVectorOverDistance> slope) {
		this.slope = slope;
	}

	public void setIntercept(Amount<ScatteringVector> intercept) {
		this.intercept = intercept;
	}

	public void setCameraLength(Amount<Length> cameraLength) {
		this.cameraLength = cameraLength;
	}

	public void setEnergy(Amount<Energy> energy) {
		this.energy = energy;
	}

	public void setUnit(Unit<Length> unit) {
		if (unit == null) {
			this.qaxisUnit = null;
			return;
		}
		// q-axis units need to be inverse of the linear dimension units
		this.qaxisUnit = unit.inverse().asType(ScatteringVector.class);
	}
	
	/**
	 * 
	 * @param detectorName - name of detector e.g. Pilatus2M
	 * @param dimension      - dimension of detector
	 * @param filename - file path to io file (processing done in this folder) 
	 * @param monitor
	 * @throws HDF5Exception 
	 */
	public void configure(String detectorName, int dimension, String filename, final IProgressMonitor monitor) throws HDF5Exception {
		String[] tmpName = FilenameUtils.getName(filename).split("_");
		monitorFile = tmpName[1];
		detector = detectorName;
		
		int fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
		H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_WEAK);
		nxsfile_handle = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, fapl);
		H5.H5Pclose(fapl);
		entry_group_id = H5.H5Gopen(nxsfile_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		detector_group_id = H5.H5Gopen(entry_group_id, detector, HDF5Constants.H5P_DEFAULT);
		input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		boolean exists = H5.H5Lexists(detector_group_id, "errors", HDF5Constants.H5P_DEFAULT);
		if (exists) {
			input_errors_id = H5.H5Dopen(detector_group_id, "errors", HDF5Constants.H5P_DEFAULT);
		}
		
		input_ids = new DataSliceIdentifiers();
		input_ids.setIDs(detector_group_id, input_data_id);
		input_errors_ids = new DataSliceIdentifiers();
		input_errors_ids.setIDs(detector_group_id, input_errors_id);
		
		dim = dimension;
		rank = H5.H5Sget_simple_extent_ndims(input_ids.dataspace_id);
		frames = new long[rank];
		H5.H5Sget_simple_extent_dims(input_ids.dataspace_id, frames, null);
		frames_int = (int[]) ConvertUtils.convert(frames, int[].class);
		
		processing_group_id = NcdNexusUtils.makegroup(entry_group_id, detector + "_processing", Nexus.INST);
	    result_group_id = NcdNexusUtils.makegroup(entry_group_id, detector+"_result", Nexus.DATA);
		
		if (firstFrame != null || lastFrame != null) {
			frameSelection = StringUtils.leftPad("", rank - dim - 1, ";");
			if (firstFrame != null) {
				frameSelection += Integer.toString(firstFrame);
			}
			frameSelection += "-";
			if (lastFrame != null) {
				frameSelection += Integer.toString(lastFrame);
			}
			frameSelection += ";";
		}
		
		if (frameSelection != null) {
		    int sel_group_id = NcdNexusUtils.makegroup(processing_group_id, LazySelection.name, Nexus.DETECT);
		    
			monitor.beginTask(monitorFile + " : Slicing Input Data", IProgressMonitor.UNKNOWN);
		    LazySelection selection = new LazySelection(frames_int);
		    selection.setFormat(frameSelection);
		    selection.setMonitor(monitor);
		    DataSliceIdentifiers[] obj_ids = selection.execute(dim, input_ids, input_errors_ids, sel_group_id);
		    
		    if (monitor.isCanceled()) {
		    	return;
		    }
		    
		    monitor.done();
		    
		    input_ids = obj_ids[0];
		    input_errors_ids = obj_ids[1];
			H5.H5Sget_simple_extent_dims(input_ids.dataspace_id, frames, null);
			frames_int = (int[]) ConvertUtils.convert(frames, int[].class);
		}
		
		lazyDetectorResponse = new LazyDetectorResponse(drFile, detector);
		if(flags.isEnableDetectorResponse()) {
			lazyDetectorResponse.setDrFile(drFile);
			lazyDetectorResponse.configure(dimension, frames, entry_group_id, processing_group_id);
		}
	    
		secRank = rank - dim + 1;
		secFrames = Arrays.copyOf(frames, secRank);
		lazySectorIntegration = new LazySectorIntegration();
		if(flags.isEnableSector() && dim == 2) {
			intSector.setAverageArea(false);
			lazySectorIntegration.setIntSector(intSector);
			if(enableMask) {
				lazySectorIntegration.setMask(mask);
			}
			qaxis = calculateQaxisDataset(detector, dim, secFrames, frames);
			if (qaxis != null) {
				lazySectorIntegration.setQaxis(qaxis, qaxisUnit);
				lazySectorIntegration.setCalibrationData(slope, intercept);
				lazySectorIntegration.setCameraLength(cameraLength);
				lazySectorIntegration.setEnergy(energy);
			}
			lazySectorIntegration.configure(dimension, frames, processing_group_id);
		}
		
		lazyNormalisation = new LazyNormalisation();
		if(flags.isEnableNormalisation()) {
			lazyNormalisation.setCalibration(calibration);
			lazyNormalisation.setAbsScaling(absScaling);
			lazyNormalisation.setNormChannel(normChannel);
			lazyNormalisation.configure(dimension, flags.isEnableSector() ? secFrames : frames, entry_group_id, processing_group_id);
		}
		
		lazyBackgroundSubtraction = new LazyBackgroundSubtraction();
		if(flags.isEnableBackground()) {
			if (qaxis != null) {
				lazyBackgroundSubtraction.setQaxis(qaxis, qaxisUnit);
			}
			lazyBackgroundSubtraction.setBgFile(bgFile);
			lazyBackgroundSubtraction.setBgDetector(bgDetector);
			lazyBackgroundSubtraction.setBgScale(bgScaling);
			lazyBackgroundSubtraction.configure(dimension, flags.isEnableSector() ? secFrames : frames, processing_group_id);
			
			lazyBackgroundSubtraction.preprocess(dimension, frames, frameBatch);
		}
		
		lazyInvariant = new LazyInvariant();
		if(flags.isEnableInvariant()) {
			lazyInvariant.configure(dimension, flags.isEnableSector() ? secFrames : frames, entry_group_id, processing_group_id);
		}
	}
	
	
	/**
	 * 
	 * @param monitor
	 * @throws HDF5Exception 
	 */
	public void execute(final IProgressMonitor monitor) throws HDF5Exception {
		
		int sliceDim = 0;
		int sliceSize = (int) frames[0];
			 		
		// We will slice only 2D data. 1D data is loaded into memory completely
		if (dim == 2) {
			
			estimateFrameBatchSize(dim, frames);
			
			// Find dimension that needs to be sliced
			MultidimensionalCounter dimCounter = new MultidimensionalCounter(Arrays.copyOfRange(frames_int, 0, rank - dim));
			if (dimCounter.getSize() > frameBatch) {
				int[] sliceIdx = dimCounter.getCounts(frameBatch);
				for (int i = 0; i < sliceIdx.length; i++) {
					if (sliceIdx[i] != 0) {
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
								if (tmp_errors_ids != null) { 
									if(tmp_errors_ids.dataset_id >= 0) {
										AbstractDataset errors = NcdNexusUtils.sliceInputData(currentSliceParams, tmp_errors_ids);
										data.setError(errors);
									} else {
										tmp_errors_ids.setSlice(currentSliceParams);
									}
								}
							} catch (Exception e) {
								throw e;
							} finally {
								lock.release();
							}

							if (flags.isEnableDetectorResponse()) {
								jobmonitor.setTaskName(monitorFile + " : Correct for detector response");
								data = lazyDetectorResponse.execute(dim, data, currentSliceParams, lock);
							}

							jobmonitor.setTaskName(monitorFile + " : Performing sector integration");
							
							LazySectorIntegration tmpLazySectorIntegration = new LazySectorIntegration();
							tmpLazySectorIntegration.setIntSector(intSector);
							tmpLazySectorIntegration.setCalculateRadial(flags.isEnableRadial());
							tmpLazySectorIntegration.setCalculateAzimuthal(flags.isEnableAzimuthal());
							tmpLazySectorIntegration.setFast(flags.isEnableFastintegration());
							tmpLazySectorIntegration.setMask(mask);
							data = tmpLazySectorIntegration.execute(dim, data, currentSliceParams, lock)[1];
						} catch (Exception e) {
							e.printStackTrace();
							return Status.CANCEL_STATUS;
						}

						return Status.OK_STATUS;
					}
				};
				
				sectorJob.tmp_ids = new DataSliceIdentifiers(input_ids);
				sectorJob.tmp_errors_ids = new DataSliceIdentifiers(input_errors_ids);
				sectorJob.currentSliceParams = new SliceSettings(sliceParams);
				sectorJobList.add(sectorJob);
				
			}
			
			monitor.beginTask(monitorFile + " : Running Sector Integration Stage", sectorJobList.size());
			for (Job job : sectorJobList) {
				if (monitor.isCanceled()) {
					sectorJobList.clear();
					for (Job runningJob : runningJobList) {
						runningJob.cancel();
					}
					break;
				}
				while (runningJobList.size() >= cores) {
					try {
						runningJobList.get(0).join();
						runningJobList.remove(0);
						monitor.worked(1);
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
			
			if (monitor.isCanceled()) {
				return;
			}
			
			monitor.done();
			
			dim = 1;
			rank = secRank;
			sliceDim = 0;
			sliceSize = (int) secFrames[0];
			
			frames = secFrames;
			frames_int = (int[]) ConvertUtils.convert(secFrames, int[].class);

			sliceParams = new SliceSettings(frames, sliceDim, sliceSize);
			IntegerDataset idx_dataset = new IntegerDataset(new int[] {sliceSize}, new int[] {1});
			iter = idx_dataset.getSliceIterator(new int[] {0}, new int[] {1}, new int[] {1});
			
			input_ids.setIDs(sec_group_id, sec_data_id);
			input_errors_ids.setIDs(sec_group_id, sec_errors_id);
		}

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
							if (tmp_errors_ids != null) { 
								if(tmp_errors_ids.dataset_id >= 0) {
									AbstractDataset errors = NcdNexusUtils.sliceInputData(currentSliceParams, tmp_errors_ids);
									data.setError(errors);
								} else {
									tmp_errors_ids.setSlice(currentSliceParams);
								}
							}
						} catch (Exception e) {
							throw e;
						} finally {
							lock.release();
						}

						if (flags.isEnableDetectorResponse() && !flags.isEnableSector()) {
							jobmonitor.setTaskName(monitorFile + " : Correct for detector response");
							data = lazyDetectorResponse.execute(dim, data, currentSliceParams, lock);
						}

						if (flags.isEnableNormalisation()) {
							jobmonitor.setTaskName(monitorFile + " : Normalising data");
							data = lazyNormalisation.execute(dim, data, currentSliceParams, lock);
						}

						if (flags.isEnableBackground() && final_bgFrames_int != null) {
							jobmonitor.setTaskName(monitorFile + " : Subtracting background");

							int bgSliceSize = Math.min(finalSliceSize, final_bgFrames_int[finalSliceDim]);
							int[] bgStart = new int[finalSliceDim + 1];
							for (int i = 0; i <= finalSliceDim; i++) {
								bgStart[i] = Math.min(currentSliceParams.getStart()[i], final_bgFrames_int[i] - 1);
							}
							SliceSettings bgSliceParams = new SliceSettings(bgFrames, finalSliceDim, bgSliceSize);
							bgSliceParams.setStart(bgStart);
							AbstractDataset bgData = NcdNexusUtils.sliceInputData(bgSliceParams, tmp_bgIds);
							if(tmp_errors_bgIds != null) {
								if (tmp_errors_bgIds.dataset_id >= 0) {
									AbstractDataset bgErrors = NcdNexusUtils.sliceInputData(bgSliceParams, tmp_errors_bgIds);
									bgData.setError(bgErrors);
								} else {
									tmp_errors_bgIds.setSlice(bgSliceParams);
								}
							}
							AbstractDataset[] remapData = NcdDataUtils.matchDataDimensions(data, bgData);
							AbstractDataset[] remapErrors = NcdDataUtils.matchDataDimensions(data.getError(), bgData.getError());
							remapData[0].setError(remapErrors[0]);
							remapData[1].setError(remapErrors[1]);
							AbstractDataset res = lazyBackgroundSubtraction.execute(dim, remapData[0], remapData[1], currentSliceParams, lock);
							remapData[0] = res;
							remapErrors[0] = res.getError();

							// restore original axis order in output dataset
							data = DatasetUtils.transpose(remapData[0], (int[]) remapData[2].getBuffer());
							data.setError(DatasetUtils.transpose(remapErrors[0], (int[]) remapErrors[2].getBuffer()));
						}

						if (flags.isEnableInvariant()) {
							jobmonitor.setTaskName(monitorFile + " : Calculating invariant");
							lazyInvariant.execute(dim, data, currentSliceParams, lock);
						}
					} catch (Exception e) {
						e.printStackTrace();
						return Status.CANCEL_STATUS;
					}

					return Status.OK_STATUS;
				}
			};
			
			processingJob.tmp_ids = new DataSliceIdentifiers(input_ids);
			processingJob.tmp_errors_ids = new DataSliceIdentifiers(input_errors_ids);
			processingJob.currentSliceParams = new SliceSettings(sliceParams);
			processingJobList.add(processingJob);
		}

		monitor.beginTask(monitorFile + " : Running NCD Data Reduction stages", processingJobList.size());
		for (DataReductionJob job : processingJobList) {
			if (monitor.isCanceled()) {
				processingJobList.clear();
				for (Job runningJob : runningJobList) {
					runningJob.cancel();
				}
				break;
			}
			while (runningJobList.size() >= cores) {
				try {
					runningJobList.get(0).join();
					runningJobList.remove(0);
					monitor.worked(1);
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
		
		if (monitor.isCanceled()) {
			return;
		}
		
		monitor.done();
		
		if (!processingJobList.isEmpty()) {
			DataReductionJob tmpJob = processingJobList.get(0);
			input_ids = new DataSliceIdentifiers(tmpJob.tmp_ids);
			input_errors_ids = new DataSliceIdentifiers(tmpJob.tmp_errors_ids);
			sliceParams = new SliceSettings(tmpJob.currentSliceParams);
		}
		
		if(flags.isEnableAverage()) {
			monitor.beginTask(monitorFile + " : Averaging  datasets", IProgressMonitor.UNKNOWN);
			int[] averageIndices = new int[] {frames.length - dim};
			if (gridAverage != null) {
				averageIndices = NcdDataUtils.createGridAxesList(gridAverage, frames.length - dim + 1);
			}
			lazyAverage = new LazyAverage();
			lazyAverage.setAverageIndices(averageIndices);
			lazyAverage.setMonitor(monitor);
			lazyAverage.configure(dim, frames_int, processing_group_id, frameBatch);
			lazyAverage.execute(input_ids, input_errors_ids);
			
			if (monitor.isCanceled()) {
				return;
			}
			
			if (qaxis != null) {
				lazyAverage.setQaxis(qaxis, qaxisUnit);
				lazyAverage.writeQaxisData(frames_int.length, input_ids.datagroup_id);
			}
			lazyAverage.writeNcdMetadata(input_ids.datagroup_id);
			
			monitor.done();
		}
		
	    H5.H5Lcopy(input_ids.datagroup_id, "./data", result_group_id, "./data", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
	    if (input_errors_ids.dataset_id != -1) {
	    	H5.H5Lcopy(input_errors_ids.datagroup_id, "./errors", result_group_id, "./errors", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
	    }
	    if (qaxis != null) {
		    H5.H5Lcopy(input_ids.datagroup_id, "./q", result_group_id, "./q", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		    if (input_errors_ids.dataset_id != -1 && qaxis.hasErrors()) {
		    	H5.H5Lcopy(input_ids.datagroup_id, "./q_errors", result_group_id, "./q_errors", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		    }
	    }
	    
	    if (flags.isEnableLogLogPlot()) {
	    	SaxsPlotData plotData = new LogLogPlotData();
	    	addPlotData(plotData, detector, qaxis);
	    }
	    
	    if (flags.isEnableGuinierPlot()) {
	    	SaxsPlotData plotData = new GuinierPlotData();
	    	addPlotData(plotData, detector, qaxis);
	    }
	    
	    if (flags.isEnablePorodPlot()) {
	    	SaxsPlotData plotData = new PorodPlotData();
	    	addPlotData(plotData, detector, qaxis);
	    }
	    
	    if (flags.isEnableKratkyPlot()) {
	    	SaxsPlotData plotData = new KratkyPlotData();
	    	addPlotData(plotData, detector, qaxis);
	    }
	    
	    if (flags.isEnableZimmPlot()) {
	    	SaxsPlotData plotData = new ZimmPlotData();
	    	addPlotData(plotData, detector, qaxis);
	    }
	    
	    if (flags.isEnableDebyeBuechePlot()) {
	    	SaxsPlotData plotData = new DebyeBuechePlotData();
	    	addPlotData(plotData, detector, qaxis);
	    }
	}
	
	private void addPlotData(SaxsPlotData plotData, String detector, AbstractDataset qaxis) throws HDF5Exception {
    	plotData.setDetector(detector);
    	plotData.setQaxis(qaxis, qaxisUnit);
    	plotData.execute(entry_group_id, input_ids, input_errors_ids);
	}
	
	public void complete() throws HDF5LibraryException {
		try {
			if (lazyAverage != null) {
				lazyAverage.complete();
			}
		} catch (HDF5LibraryException e) {
			throw e;
		} finally {
			List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(input_data_id,
					input_errors_id,
					detector_group_id,
					processing_group_id,
					result_group_id,
					entry_group_id,
					nxsfile_handle));

			NcdNexusUtils.closeH5idList(identifiers);
		}
	}
	
	private void estimateFrameBatchSize(int dim, long[] frames) {
		int batchSize = 12; // use 12 byte for float data and double errors
		for (int i = frames.length - dim; i < frames.length; i++) {
			batchSize *= frames[i];
		}
		int totalBatch = Math.max(1, (int) (maxMemory / (10 * batchSize)));
		if (totalBatch <= cores) {
			cores = totalBatch;
			frameBatch = 1;
		} else {
			frameBatch = totalBatch / cores;
		}
		if (!flags.isEnableSector() && dim == 2) {
			cores = Math.min(cores, 2);
			frameBatch = 1;
		}
	}

	private AbstractDataset calculateQaxisDataset(String detector, int dim, long[] secFrames, long[] frames) throws HDF5LibraryException {
		
		AbstractDataset qaxis = null;
		AbstractDataset qaxisErr = null;
		IDiffractionMetadata dm = null;
		boolean hasErrors = true;
		
		H5L_info_t link_info = H5.H5Lget_info(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		int[] shape = new int[] {(int) frames[frames.length - 2], (int) frames[frames.length - 1]};
		if (link_info.type == HDF5Constants.H5L_TYPE_EXTERNAL) {
			String[] buff = new String[(int) link_info.address_val_size];
			H5.H5Lget_val(detector_group_id, "data", buff, HDF5Constants.H5P_DEFAULT);
			if (buff[1] != null) {
				NexusDiffractionMetaReader nexusDiffReader = new NexusDiffractionMetaReader(buff[1]);
				dm = nexusDiffReader.getDiffractionMetadataFromNexus(shape);
				if (!nexusDiffReader.isMetadataEntryRead(NexusDiffractionMetaReader.DiffractionMetaValue.BEAM_CENTRE) ||
					!nexusDiffReader.isMetadataEntryRead(NexusDiffractionMetaReader.DiffractionMetaValue.ENERGY)      || 
					!nexusDiffReader.isMetadataEntryRead(NexusDiffractionMetaReader.DiffractionMetaValue.DISTANCE)    ||
					!nexusDiffReader.isMetadataEntryRead(NexusDiffractionMetaReader.DiffractionMetaValue.PIXEL_SIZE)) { 
						dm = null;
				}
			}
		}
		
		if (crb != null && crb.containsKey(detector)) {
			if (slope == null) {
				slope = crb.getGradient(detector);
			}
			if (intercept == null) {
				intercept = crb.getIntercept(detector);
			}
			if (qaxisUnit == null) {
				setUnit(crb.getUnit(detector));
			}
			cameraLength = crb.getMeanCameraLength(detector);
		}
		
		// If calibration bean is empty, we will estimate gradient and intercept values from diffraction metadata
		if ((slope == null || intercept == null) && dm != null) {
			DetectorProperties detectorProperties = dm.getDetector2DProperties();
			DiffractionCrystalEnvironment crystalEnvironment = dm.getDiffractionCrystalEnvironment();
			qaxisUnit = NonSI.ANGSTROM.inverse().asType(ScatteringVector.class);
			Unit<Length> pxUnit = ncdDetectors.getPxSaxs().getUnit();
			cameraLength = Amount.valueOf(detectorProperties.getBeamCentreDistance(), SI.MILLIMETRE);
			Amount<Length> wv = Amount.valueOf(crystalEnvironment.getWavelength(), NonSI.ANGSTROM);
			energy = Constants.ℎ.times(Constants.c).divide(wv).to(SI.KILO(NonSI.ELECTRON_VOLT));
			slope = wv.inverse().times(2.0*Math.PI).divide(cameraLength).to(qaxisUnit.divide(pxUnit).asType(ScatteringVectorOverDistance.class));
			intercept = Amount.valueOf(0.0, qaxisUnit);
			
			// Diffraction metadata currently doesn't include error estimates
			hasErrors = false;
		}
		
		if (slope != null && intercept != null) {
			int numPoints = (int) secFrames[secFrames.length - 1];
			qaxis = AbstractDataset.zeros(new int[] { numPoints }, AbstractDataset.FLOAT32);
			qaxisErr = AbstractDataset.zeros(new int[] { numPoints }, AbstractDataset.FLOAT32);
			if (dim == 1) {
				Amount<Length> pxWaxs = ncdDetectors.getPxWaxs();
				for (int i = 0; i < numPoints; i++) {
					Amount<ScatteringVector> amountQaxis = slope.times(i).times(pxWaxs).plus(intercept).to(qaxisUnit); 
					qaxis.set(amountQaxis.getEstimatedValue(), i);
					if (hasErrors) {
						qaxisErr.set(amountQaxis.getAbsoluteError(), i);
					}
				}
			} else {
				if (dim > 1 && flags.isEnableSector()) {
					double d2bs = intSector.getRadii()[0];
					Amount<Length> pxSaxs = ncdDetectors.getPxSaxs();
					for (int i = 0; i < numPoints; i++) {
						Amount<ScatteringVector> amountQaxis = slope.times(i + d2bs).times(pxSaxs).plus(intercept).to(qaxisUnit);
						qaxis.set(amountQaxis.getEstimatedValue(), i);
						if (hasErrors) {
							qaxisErr.set(amountQaxis.getAbsoluteError(), i);
						}
					}
				}
			}
			if (hasErrors) {
				qaxis.setError(qaxisErr);
			}
		}
		
		return qaxis;
	}
}
