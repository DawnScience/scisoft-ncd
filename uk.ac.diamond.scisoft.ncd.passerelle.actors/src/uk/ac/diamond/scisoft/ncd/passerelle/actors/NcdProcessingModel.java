/*
 * Copyright 2014 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.passerelle.actors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.jscience.physics.amount.Amount;

import ptolemy.data.ObjectToken;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

import com.isencia.passerelle.core.PasserelleException;
import com.isencia.passerelle.domain.et.ETDirector;
import com.isencia.passerelle.model.Flow;
import com.isencia.passerelle.model.FlowAlreadyExecutingException;
import com.isencia.passerelle.model.FlowManager;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdMessageSink;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdMessageSource;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdAverageForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdBackgroundSubtractionForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdDetectorResponseForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdInvariantForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdNormalisationForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSectorIntegrationForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSelectionForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

public class NcdProcessingModel {

	private int normChannel;
	private Double bgScaling;
	private String  bgFile, drFile;
	private String  calibration, bgDetector;
	private SectorROI intSector;
	private Amount<ScatteringVectorOverDistance> slope;
	private Amount<ScatteringVector> intercept;
	private Amount<Length> cameraLength;
	private Amount<Energy> energy;
	private BooleanDataset mask;
	private AbstractDataset drData;

	private CalibrationResultsBean crb;
	
	private NcdReductionFlags flags;
	private Double absScaling;
	private Integer firstFrame;
	private Integer lastFrame;
	private String frameSelection;
	
	private String gridAverage, bgGridAverage;
	private boolean enableBgAverage;
	
	private Flow flow;
	private FlowManager flowMgr;
	
	private static ReentrantLock lock;

	
	public NcdProcessingModel() {
		super();
		
		try {
			flow = new Flow("NCD Data Reduction", null);
			flowMgr = new FlowManager();
			ETDirector director = new ETDirector(flow, "director");
			flow.setDirector(director);
		} catch (IllegalActionException e) {
			throw new RuntimeException(e);
		} catch (NameDuplicationException e) {
			throw new RuntimeException(e);
		}

		lock = new ReentrantLock();
		
		normChannel = -1;
		bgScaling = 1.0;
		bgFile = "";
		drFile = "";
		calibration = "";
		bgDetector = "";
		intSector = new SectorROI();
		slope = Amount.valueOf(0, ScatteringVectorOverDistance.UNIT);
		intercept = Amount.valueOf(0, ScatteringVector.UNIT);
		cameraLength = Amount.valueOf(0, Length.UNIT);
		energy = Amount.valueOf(0, Energy.UNIT);
		mask = new BooleanDataset();
		drData = new FloatDataset();
		firstFrame = null;
		lastFrame = null;
		frameSelection = null;
		
		crb = new CalibrationResultsBean();
		
		flags = new NcdReductionFlags();
		absScaling = 1.0;
		
		gridAverage = "";
		bgGridAverage = "";
		enableBgAverage = false;
		
	}

	public void setBgScaling(Double bgScaling) {
		this.bgScaling = bgScaling;
	}

	public void setBgDetector(String bgDetector) {
		this.bgDetector = bgDetector;
	}

	public void setBgFile(String bgFile) {
		this.bgFile = bgFile;
	}

	public void setDrFile(String drFile) {
		this.drFile = drFile;
	}

	public void setAbsScaling(Double absScaling) {
		this.absScaling = absScaling;
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
	
	private long[] readDataShape(String detector, String filename) throws HDF5Exception {
		int fapl = -1;
		int fileID = -1;
		int entryGroupID = -1;
		int detectorGroupID = -1;
		int dataID = -1;
		int dataspaceID = -1;
		try {
			fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
			H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_WEAK);
			
			fileID = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDONLY, fapl);

			entryGroupID = H5.H5Gopen(fileID, "entry1", HDF5Constants.H5P_DEFAULT);
			detectorGroupID = H5.H5Gopen(entryGroupID, detector, HDF5Constants.H5P_DEFAULT);

			dataID = H5.H5Dopen(detectorGroupID, "data", HDF5Constants.H5P_DEFAULT);
			dataspaceID = H5.H5Dget_space(dataID);

			int rank = H5.H5Sget_simple_extent_ndims(dataspaceID);
			long[] frames = new long[rank];
			H5.H5Sget_simple_extent_dims(dataspaceID, frames, null);
			
			return frames;

		} finally {
			if (fapl > 0) {
				H5.H5Pclose(fapl);
			}
			List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(
					dataspaceID,
					dataID,
					detectorGroupID,
					entryGroupID,
					fileID));

			NcdNexusUtils.closeH5idList(identifiers);
		}
				
	}
	
	private void preprocessBackgroundData(String detector, int dimension, String filename) throws HDF5Exception {

		long[] bgFrames = readDataShape(bgDetector, bgFile);
		long[] frames = readDataShape(detector, filename);

		if (frames != null && bgFrames != null) {
			if (!Arrays.equals(bgFrames, frames)) {
				ArrayList<Integer> bgAverageIndices = new ArrayList<Integer>();
				int bgRank = bgFrames.length;
				for (int i = (bgRank - dimension - 1); i >= 0; i--) {
					int fi = i - bgRank + frames.length;
					if ((bgFrames[i] != 1) && (fi < 0 || (bgFrames[i] != frames[fi]))) {
						bgAverageIndices.add(i + 1);
						bgFrames[i] = 1;
					}
				}
				enableBgAverage = true;
				bgGridAverage = StringUtils.join(bgAverageIndices, ",");
			}
		}
	}

	public void configure(String detector, int dimension, String filename) throws HDF5LibraryException, HDF5Exception {
		if (crb != null && crb.containsKey(detector)) {
			if (slope == null) {
				slope = crb.getGradient(detector);
			}
			if (intercept == null) {
				intercept = crb.getIntercept(detector);
			}
			cameraLength = crb.getMeanCameraLength(detector);
		}
		
		if (firstFrame != null || lastFrame != null) {
			long[] frames = readDataShape(detector, filename);
			frameSelection = StringUtils.leftPad("", frames.length - dimension - 1, ";");
			if (firstFrame != null) {
				frameSelection += Integer.toString(firstFrame);
			}
			frameSelection += "-";
			if (lastFrame != null) {
				frameSelection += Integer.toString(lastFrame);
			}
			frameSelection += ";";
		}
		
		if (flags.isEnableDetectorResponse()) {
			int drEntryGroupID = -1;
			int drInstrumentGroupID = -1;
			int drDetectorGroupID = -1;
			int drDataID = -1;
			int drDataspaceID = -1;
			int drDatatypeID = -1;
			int memspaceID = -1;
			int drFileID = -1;
			try {
				int fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
				H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_WEAK);
				drFileID = H5.H5Fopen(drFile, HDF5Constants.H5F_ACC_RDONLY, fapl);
				H5.H5Pclose(fapl);

				drEntryGroupID = H5.H5Gopen(drFileID, "entry1", HDF5Constants.H5P_DEFAULT);
				drInstrumentGroupID = H5.H5Gopen(drEntryGroupID, "instrument", HDF5Constants.H5P_DEFAULT);
				drDetectorGroupID = H5.H5Gopen(drInstrumentGroupID, detector, HDF5Constants.H5P_DEFAULT);

				drDataID = H5.H5Dopen(drDetectorGroupID, "data", HDF5Constants.H5P_DEFAULT);
				drDataspaceID = H5.H5Dget_space(drDataID);
				drDatatypeID = H5.H5Dget_type(drDataID);
				int drDataclassID = H5.H5Tget_class(drDatatypeID);
				int drDatasizeID = H5.H5Tget_size(drDatatypeID);

				int rank = H5.H5Sget_simple_extent_ndims(drDataspaceID);
				int dtype = HDF5Loader.getDtype(drDataclassID, drDatasizeID);

				long[] drFrames = new long[rank];
				H5.H5Sget_simple_extent_dims(drDataspaceID, drFrames, null);
				memspaceID = H5.H5Screate_simple(rank, drFrames, null);

				int[] drFramesInt = (int[]) ConvertUtils.convert(drFrames, int[].class);
				drData = AbstractDataset.zeros(drFramesInt, dtype);

				int readID = -1;
				if ((drDataID >= 0) && (drDataspaceID >= 0) && (memspaceID >= 0)) {
					readID = H5.H5Dread(drDataID, drDatatypeID, memspaceID, drDataspaceID,
							HDF5Constants.H5P_DEFAULT, drData.getBuffer());
				}
				if (readID < 0) {
					throw new HDF5Exception("Failed to read detector response dataset");
				}
			} finally {
				List<Integer> identifiers = new ArrayList<Integer>(Arrays.asList(
						memspaceID,
						drDataspaceID,
						drDatatypeID,
						drDataID,
						drDetectorGroupID,
						drInstrumentGroupID,
						drEntryGroupID,
						drFileID));

				NcdNexusUtils.closeH5idList(identifiers);

			}
		}
		
		if (flags.isEnableBackground()) {
			preprocessBackgroundData(detector, dimension, filename);
		}
		
	}

	public void execute(String detectorName, int dimension, String filename) {

		try {

			configure(detectorName, dimension, filename);

			NcdMessageSource source = new NcdMessageSource(flow, "MessageSource");
			NcdSelectionForkJoinTransformer selection = new NcdSelectionForkJoinTransformer(flow,
					"Selection");
			NcdDetectorResponseForkJoinTransformer detectorResponse = new NcdDetectorResponseForkJoinTransformer(flow,
					"DetectorResponse");
			NcdSectorIntegrationForkJoinTransformer sectorIntegration = new NcdSectorIntegrationForkJoinTransformer(
					flow, "SectorIntegration");
			NcdNormalisationForkJoinTransformer normalisation = new NcdNormalisationForkJoinTransformer(flow,
					"Normalisation");
			NcdBackgroundSubtractionForkJoinTransformer backgroundSubtraction = new NcdBackgroundSubtractionForkJoinTransformer(
					flow, "BackgroundSubtraction");
			NcdInvariantForkJoinTransformer invariant = new NcdInvariantForkJoinTransformer(flow, "Invariant");
			NcdAverageForkJoinTransformer average = new NcdAverageForkJoinTransformer(flow, "Average");
			NcdMessageSink sink = new NcdMessageSink(flow, "MessageSink");

			flow.connect(source.output, selection.input);
			flow.connect(selection.output, detectorResponse.input);
			flow.connect(detectorResponse.output, sectorIntegration.input);
			flow.connect(sectorIntegration.output, normalisation.input);
			flow.connect(normalisation.output, backgroundSubtraction.input);
			flow.connect(backgroundSubtraction.output, invariant.input);
			//flow.connect(invariant.output, sink.input);
			flow.connect(normalisation.output, average.input);
			flow.connect(average.output, sink.input);
			
			source.lockParam.setToken(new ObjectToken(lock));
			
			detectorResponse.detectorResponseParam.setToken(new ObjectToken(drData));

			sectorIntegration.sectorROIParam.setToken(new ObjectToken(intSector));
			sectorIntegration.gradientParam.setToken(new ObjectToken(slope));
			sectorIntegration.interceptParam.setToken(new ObjectToken(intercept));
			sectorIntegration.cameraLengthParam.setToken(new ObjectToken(cameraLength));
			sectorIntegration.energyParam.setToken(new ObjectToken(energy));
			sectorIntegration.maskParam.setToken(new ObjectToken(mask));

			Map<String, String> props = new HashMap<String, String>();

			props.put("MessageSource.filenameParam", filename);
			props.put("MessageSource.detectorParam", detectorName);
			
			props.put("Selection.enable", Boolean.toString(frameSelection != null));
			props.put("Selection.dimensionParam", Integer.toString(dimension));
			props.put("Selection.formatParam", frameSelection != null ? frameSelection : "");
			
			props.put("DetectorResponse.enable", Boolean.toString(flags.isEnableDetectorResponse()));
			props.put("DetectorResponse.dimensionParam", Integer.toString(dimension));
			
			props.put("DetectorResponse.enable", Boolean.toString(flags.isEnableDetectorResponse()));
			props.put("DetectorResponse.dimensionParam", Integer.toString(dimension));
			
			props.put("SectorIntegration.enable", Boolean.toString(flags.isEnableSector()));
			props.put("SectorIntegration.dimensionParam", Integer.toString(dimension));
			props.put("SectorIntegration.doRadialParam", Boolean.toString(flags.isEnableRadial()));
			props.put("SectorIntegration.doAzimuthalParam", Boolean.toString(flags.isEnableAzimuthal()));
			props.put("SectorIntegration.doFastParam", Boolean.toString(flags.isEnableFastintegration()));

			if (flags.isEnableSector()) {
				dimension = 1;
			}

			props.put("Normalisation.enable", Boolean.toString(flags.isEnableNormalisation()));
			props.put("Normalisation.dimensionParam", Integer.toString(dimension));
			props.put("Normalisation.calibrationParam", calibration);
			props.put("Normalisation.absScalingParam", Double.toString(absScaling));
			props.put("Normalisation.normChannelParam", Integer.toString(normChannel));
			
			props.put("BackgroundSubtraction.enable", Boolean.toString(flags.isEnableBackground()));
			props.put("BackgroundSubtraction.dimensionParam", Integer.toString(dimension));
			props.put("BackgroundSubtraction.bgScalingParam", Double.toString(bgScaling));
			
			props.put("Invariant.enable", Boolean.toString(flags.isEnableInvariant()));
			props.put("Invariant.dimensionParam", Integer.toString(dimension));
			
			props.put("Average.enable", Boolean.toString(flags.isEnableAverage()));
			props.put("Average.dimensionParam", Integer.toString(dimension));
			props.put("Average.gridAverageParam", gridAverage);

			props.put("MessageSink.detectorParam", detectorName);

			if (flags.isEnableBackground()) {
				NcdMessageSource bgsource = new NcdMessageSource(flow, "BackgroundMessageSource");
				NcdAverageForkJoinTransformer bgAverage = new NcdAverageForkJoinTransformer(flow, "BackgroundAverage");
				
				flow.connect(bgsource.output, bgAverage.input);
				flow.connect(bgAverage.output, backgroundSubtraction.bgInput);
				
				bgsource.lockParam.setToken(new ObjectToken(lock));
				
				props.put("BackgroundMessageSource.filenameParam", bgFile);
				props.put("BackgroundMessageSource.detectorParam", bgDetector);
				
				props.put("BackgroundAverage.enable", Boolean.toString(enableBgAverage));
				props.put("BackgroundAverage.dimensionParam", Integer.toString(dimension));
				props.put("BackgroundAverage.gridAverageParam", bgGridAverage);
			}

			flowMgr.executeBlockingLocally(flow, props);

		} catch (FlowAlreadyExecutingException e) {
			throw new RuntimeException(e);
		} catch (PasserelleException e) {
			throw new RuntimeException(e);
		} catch (IllegalActionException e) {
			throw new RuntimeException(e);
		} catch (NameDuplicationException e) {
			throw new RuntimeException(e);
		} catch (HDF5LibraryException e) {
			throw new RuntimeException(e);
		} catch (HDF5Exception e) {
			throw new RuntimeException(e);
		}
	}
}
