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
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.dawb.passerelle.common.actors.AbstractDataMessageSource;
import org.dawb.passerelle.common.message.MessageUtils;
import org.dawnsci.plotting.tools.preference.detector.DiffractionDetector;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.dawnsci.analysis.api.diffraction.DetectorProperties;
import org.eclipse.dawnsci.analysis.api.diffraction.DiffractionCrystalEnvironment;
import org.eclipse.dawnsci.analysis.api.message.DataMessageComponent;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.hdf5.HDF5Utils;
import org.eclipse.january.dataset.BooleanDataset;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.FloatDataset;
import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.jscience.physics.amount.Amount;
import org.jscience.physics.amount.Constants;

import com.isencia.passerelle.actor.InitializationException;
import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.general.DevNullActor;
import com.isencia.passerelle.actor.v5.ActorContext;
import com.isencia.passerelle.actor.v5.ProcessRequest;
import com.isencia.passerelle.actor.v5.ProcessResponse;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.domain.et.ETDirector;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.model.Flow;
import com.isencia.passerelle.model.FlowManager;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;
import hdf.hdf5lib.exceptions.HDF5Exception;
import hdf.hdf5lib.exceptions.HDF5LibraryException;
import hdf.hdf5lib.structs.H5L_info_t;
import ptolemy.data.ObjectToken;
import ptolemy.data.StringToken;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.io.NexusDiffractionMetaReader;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStatsParameters;
import uk.ac.diamond.scisoft.ncd.core.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.core.service.IDataReductionProcess;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdMessageSink;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdMessageSource;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdProcessingObjectTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdAbstractDataForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdAverageForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdBackgroundSubtractionForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdDetectorResponseForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdInvariantForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdNormalisationForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdOrientationForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSaxsDataStatsForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSaxsPlotDataForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSectorIntegrationForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSelectionForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdStandardiseForkJoinTransformer;

public class NcdProcessingModel implements IDataReductionProcess {

	private boolean enableMask;
	private int normChannel;
	private Double bgScaling;
	private String  bgFile, drFile;
	private String  calibration, bgDetector;
	private SectorROI intSector;
	private Amount<ScatteringVectorOverDistance> slope;
	private Amount<ScatteringVector> intercept;
	private Amount<Length> cameraLength;
	private Amount<Energy> energy;
	private Amount<Length> pxSize;
	private Unit<ScatteringVector> qaxisUnit;
	private BooleanDataset mask;
	private Dataset drData;

	private CalibrationResultsBean crb;
	
	private NcdReductionFlags flags;
	private String detector;
	private int dimension;
	
	private Double absScaling;
	private Integer firstFrame;
	private Integer lastFrame;
	private String frameSelection;
	
	private String gridAverage, bgGridAverage;
	private boolean enableBgAverage;
	
	private SaxsAnalysisStatsParameters saxsAnalysisStatsParameters;
	
	private static ReentrantLock lock;

	private final String PROCESSING = "processing";
	
	/**
	 * Dummy message forwarder actor.
	 * Intended as a stub for disabled data reduction stages
	 *
	 */
	private class NcdMessageForwarder extends NcdAbstractDataForkJoinTransformer {

		private static final long serialVersionUID = -8957295090985150203L;

		public NcdMessageForwarder(CompositeEntity container, String name) throws IllegalActionException,
				NameDuplicationException {
			super(container, name);
		}
		
		@Override
		protected void process(ActorContext ctxt, ProcessRequest request, ProcessResponse response)
				throws ProcessingException {

			ManagedMessage receivedMsg = request.getMessage(input);
			response.addOutputMessage(output, receivedMsg);
		}
			
	}
	
	/**
	 * Dummy message forwarder actor.
	 * Intended as a stub for disabled data reduction stages
	 *
	 */
	private class NcdDummySelectionSource extends AbstractDataMessageSource {
		
		private static final long serialVersionUID = 1L;

		private boolean messageSent;

		public NcdDummySelectionSource(CompositeEntity container, String name) throws NameDuplicationException,
				IllegalActionException {
			super(container, name);
		}

		@Override
		protected void doInitialize() throws InitializationException {
			super.doInitialize();
			messageSent = false;
		}

		@Override
		protected ManagedMessage getDataMessage() throws ProcessingException {
			if (messageSent) {
				return null;
			}
			
			DataMessageComponent despatch = new DataMessageComponent();
			despatch.addList("selection", DatasetFactory.zeros(BooleanDataset.class, null));
        
			try {
				ManagedMessage msg = MessageUtils.getDataMessage(despatch, null);
				return msg;
			} catch (Exception e) {
				throw new ProcessingException(ErrorCode.ACTOR_EXECUTION_ERROR, "Cannnot send dummy selection dataset", this, null);
			} finally {
				messageSent = true;
			}
		}

		@Override
		protected boolean mustWaitForTrigger() {
			// TODO Auto-generated method stub
			return false;
		}
	}
	
	public NcdProcessingModel() {
		super();
		
		lock = new ReentrantLock();
		
		normChannel = -1;
		bgScaling = 1.0;
		bgFile = "";
		drFile = "";
		calibration = "";
		bgDetector = "";
		intSector = new SectorROI();
		slope = null;
		intercept = null;
		cameraLength = null;
		energy = null;
		mask = DatasetFactory.zeros(BooleanDataset.class, null);
		drData = DatasetFactory.zeros(FloatDataset.class, null);
		firstFrame = null;
		lastFrame = null;
		frameSelection = null;
		
		crb = new CalibrationResultsBean();
		
		flags = new NcdReductionFlags();
		absScaling = 1.0;
		
		gridAverage = null;
		bgGridAverage = null;
		enableBgAverage = false;
		
	}

	@Override
	public void setBgScaling(Double bgScaling) {
		this.bgScaling = bgScaling;
	}

	@Override
	public void setBgDetector(String bgDetector) {
		this.bgDetector = bgDetector;
	}

	@Override
	public void setBgFile(String bgFile) {
		this.bgFile = bgFile;
	}

	@Override
	public void setDrFile(String drFile) {
		this.drFile = drFile;
	}

	@Override
	public void setAbsScaling(Double absScaling) {
		this.absScaling = absScaling;
	}
	
	@Override
	public void setNormChannel(int normChannel) {
		this.normChannel = normChannel;
	}

	@Override
	public void setCalibration(String calibration) {
		this.calibration = calibration;
	}

	@Override
	public void setIntSector(SectorROI intSector) {
		this.intSector = intSector;
	}

	@Override
	public void setEnableMask(boolean enableMask) {
		this.enableMask = enableMask;
	}

	@Override
	public void setMask(BooleanDataset mask) {
		this.mask = mask;
	}

	@Override
	public void setCrb(CalibrationResultsBean crb) {
		this.crb = crb;
	}

	@Override
	public void setFlags(NcdReductionFlags flags) {
		this.flags = new NcdReductionFlags(flags);
	}

	@Override
	public void setNcdDetector(DiffractionDetector ncdDetector) {
		this.detector = ncdDetector.getDetectorName();
		if (ncdDetector.getxPixelSize() != null && ncdDetector.getPixelSize() != null) {
			dimension = 2;
		} else {
			dimension = 1;
		}
		this.pxSize = ncdDetector.getPixelSize();
	}

	@Override
	public void setFirstFrame(Integer firstFrame) {
		this.firstFrame = firstFrame;
	}

	@Override
	public void setLastFrame(Integer lastFrame) {
		this.lastFrame = lastFrame;
	}

	@Override
	public void setFrameSelection(String frameSelection) {
		this.frameSelection = frameSelection;
	}

	@Override
	public void setGridAverageSelection(String gridAverage) {
		this.gridAverage = gridAverage;
	}

	@Override
	public void setEnergy(Amount<Energy> energy) {
		this.energy = energy;
	}
	
	@Override
	public void setSaxsAnalysisStatsParameters(SaxsAnalysisStatsParameters saxsAnalysisStatsParameters) {
		this.saxsAnalysisStatsParameters = saxsAnalysisStatsParameters;
	}
	
	private long[] readDataShape(String detector, String filename) throws HDF5Exception {
		long fapl = -1;
		long fileID = -1;
		long entryGroupID = -1;
		long detectorGroupID = -1;
		long dataID = -1;
		long dataspaceID = -1;
		try {
			fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
			H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_WEAK);
			
			fileID = HDF5Utils.H5Fopen(filename, HDF5Constants.H5F_ACC_RDONLY, fapl);

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
			List<Long> identifiers = new ArrayList<Long>(Arrays.asList(
					dataspaceID,
					dataID,
					detectorGroupID,
					entryGroupID,
					fileID));

			NcdNexusUtils.closeH5idList(identifiers);
		}
				
	}
	
	private void preprocessBackgroundData(String filename) throws HDF5Exception {

		long[] bgFrames = readDataShape(detector, bgFile);
		long[] frames = readDataShape(detector, filename);

		if (frames != null && bgFrames != null) {
			if (!Arrays.equals(bgFrames, frames)) {
				ArrayList<Integer> bgAverageIndices = new ArrayList<Integer>();
				int bgRank = bgFrames.length;
				for (int i = (bgRank - 1); i >= 0; i--) {
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

	private void configure(String filename) throws HDF5LibraryException, HDF5Exception {
		IDiffractionMetadata dm = null;
		{
			long detectorGroupID = -1;
			long entryGroupID = -1;
			long fileID = -1;
			try {
				long fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
				H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_WEAK);
				fileID = HDF5Utils.H5Fopen(filename, HDF5Constants.H5F_ACC_RDONLY, fapl);
				H5.H5Pclose(fapl);

				entryGroupID = H5.H5Gopen(fileID, "entry1", HDF5Constants.H5P_DEFAULT);
				detectorGroupID = H5.H5Gopen(entryGroupID, detector, HDF5Constants.H5P_DEFAULT);

				H5L_info_t link_info = H5.H5Lget_info(detectorGroupID, "data", HDF5Constants.H5P_DEFAULT);
				long[] frames = readDataShape(detector, filename);
				int[] shape = new int[] { (int) frames[frames.length - 2], (int) frames[frames.length - 1] };
				if (link_info.type == HDF5Constants.H5L_TYPE_EXTERNAL) {
					String[] buff = new String[(int) link_info.address_val_size];
					H5.H5Lget_value(detectorGroupID, "data", buff, HDF5Constants.H5P_DEFAULT);
					if (buff[1] != null) {
						NexusDiffractionMetaReader nexusDiffReader = new NexusDiffractionMetaReader(buff[1]);
						dm = nexusDiffReader.getDiffractionMetadataFromNexus(shape);
						if (!nexusDiffReader
								.isMetadataEntryRead(NexusDiffractionMetaReader.DiffractionMetaValue.BEAM_CENTRE)
								|| !nexusDiffReader
										.isMetadataEntryRead(NexusDiffractionMetaReader.DiffractionMetaValue.ENERGY)
								|| !nexusDiffReader
										.isMetadataEntryRead(NexusDiffractionMetaReader.DiffractionMetaValue.DISTANCE)
								|| !nexusDiffReader
										.isMetadataEntryRead(NexusDiffractionMetaReader.DiffractionMetaValue.PIXEL_SIZE)) {
							dm = null;
						}
					}
				}
			} finally {
				List<Long> identifiers = new ArrayList<Long>(Arrays.asList(
						detectorGroupID,
						entryGroupID,
						fileID));

				NcdNexusUtils.closeH5idList(identifiers);
			}
		}

		if (crb != null && crb.containsKey(detector)) {
			if (slope == null) {
				slope = crb.getGradient(detector);
			}
			if (intercept == null) {
				intercept = crb.getIntercept(detector);
			}
			cameraLength = crb.getMeanCameraLength(detector);
			Unit<Length> unit = crb.getUnit(detector);
			if (unit != null) {
				// q-axis units need to be inverse of the linear dimension units
				this.qaxisUnit = unit.inverse().asType(ScatteringVector.class);
			}
		}
		
		// If calibration bean is empty, we will estimate gradient and intercept values from diffraction metadata
		if ((slope == null || intercept == null) && dm != null) {
			DetectorProperties detectorProperties = dm.getDetector2DProperties();
			DiffractionCrystalEnvironment crystalEnvironment = dm.getDiffractionCrystalEnvironment();
			qaxisUnit = NonSI.ANGSTROM.inverse().asType(ScatteringVector.class);
			cameraLength = Amount.valueOf(detectorProperties.getBeamCentreDistance(), SI.MILLIMETRE);
			Amount<Length> wv = Amount.valueOf(crystalEnvironment.getWavelength(), NonSI.ANGSTROM);
			energy = Constants.ℎ.times(Constants.c).divide(wv).to(SI.KILO(NonSI.ELECTRON_VOLT));
			slope = wv.inverse().times(2.0*Math.PI).divide(cameraLength).to(qaxisUnit.divide(pxSize.getUnit()).asType(ScatteringVectorOverDistance.class));
			intercept = Amount.valueOf(0.0, qaxisUnit);
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
			long drEntryGroupID = -1;
			long drInstrumentGroupID = -1;
			long drDetectorGroupID = -1;
			long drDataID = -1;
			long drDataspaceID = -1;
			long drDatatypeID = -1;
			long memspaceID = -1;
			long drFileID = -1;
			try {
				long fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
				H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_WEAK);
				drFileID = HDF5Utils.H5Fopen(drFile, HDF5Constants.H5F_ACC_RDONLY, fapl);
				H5.H5Pclose(fapl);

				drEntryGroupID = H5.H5Gopen(drFileID, "entry1", HDF5Constants.H5P_DEFAULT);
				drInstrumentGroupID = H5.H5Gopen(drEntryGroupID, "instrument", HDF5Constants.H5P_DEFAULT);
				drDetectorGroupID = H5.H5Gopen(drInstrumentGroupID, detector, HDF5Constants.H5P_DEFAULT);

				drDataID = H5.H5Dopen(drDetectorGroupID, "data", HDF5Constants.H5P_DEFAULT);
				drDataspaceID = H5.H5Dget_space(drDataID);
				drDatatypeID = H5.H5Dget_type(drDataID);
				int drDataclassID = H5.H5Tget_class(drDatatypeID);
				int drDatasizeID = (int) H5.H5Tget_size(drDatatypeID);

				int rank = H5.H5Sget_simple_extent_ndims(drDataspaceID);
				int dtype = HDF5Utils.getDType(drDataclassID, drDatasizeID);

				long[] drFrames = new long[rank];
				H5.H5Sget_simple_extent_dims(drDataspaceID, drFrames, null);
				memspaceID = H5.H5Screate_simple(rank, drFrames, null);

				int[] drFramesInt = (int[]) ConvertUtils.convert(drFrames, int[].class);
				drData = DatasetFactory.zeros(drFramesInt, dtype);

				int readID = -1;
				if ((drDataID >= 0) && (drDataspaceID >= 0) && (memspaceID >= 0)) {
					readID = H5.H5Dread(drDataID, drDatatypeID, memspaceID, drDataspaceID,
							HDF5Constants.H5P_DEFAULT, drData.getBuffer());
				}
				if (readID < 0) {
					throw new HDF5Exception("Failed to read detector response dataset");
				}
			} finally {
				List<Long> identifiers = new ArrayList<Long>(Arrays.asList(
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
			preprocessBackgroundData(filename);
		}
		
	}

	@Override
	public void execute(String filename, final IProgressMonitor monitor) throws Exception {

		NcdAbstractDataForkJoinTransformer selection;
		NcdAbstractDataForkJoinTransformer detectorResponse;
		NcdAbstractDataForkJoinTransformer sectorIntegration;
		NcdAbstractDataForkJoinTransformer normalisation;
		NcdAbstractDataForkJoinTransformer standardise;
		NcdAbstractDataForkJoinTransformer backgroundSubtraction;
		NcdAbstractDataForkJoinTransformer invariant;
		NcdAbstractDataForkJoinTransformer average;
		NcdAbstractDataForkJoinTransformer loglogPlot;
		NcdAbstractDataForkJoinTransformer guinierPlot;
		NcdAbstractDataForkJoinTransformer porodPlot;
		NcdAbstractDataForkJoinTransformer kratkyPlot;
		NcdAbstractDataForkJoinTransformer zimmPlot;
		NcdAbstractDataForkJoinTransformer debyebuechePlot;
		
		NcdDummySelectionSource dummySelection;
		NcdProcessingObjectTransformer exportFilter = null;
		
		FlowManager flowMgr = new FlowManager();
		Flow flow = new Flow("NCD Data Reduction", null);
		
		ETDirector director = new ETDirector(flow, "director");
		flow.setDirector(director);
		
		configure(filename);

		Map<String, String> props = new HashMap<String, String>();

		NcdMessageSource source = new NcdMessageSource(flow, "MessageSource");
		source.lockParam.setToken(new ObjectToken(lock));
		if (monitor != null) {
			source.monitorParam.setToken(new ObjectToken(monitor));
		}
		props.put("MessageSource.filenameParam", filename);
		props.put("MessageSource.detectorParam", detector);
		props.put("MessageSource.dimensionParam", Integer.toString(dimension));
		String processingName = StringUtils.join(new String[] {detector, PROCESSING},  "_");
		props.put("MessageSource.processingParam", processingName);
		props.put("MessageSource.readOnlyParam", Boolean.toString(false));
		
		dummySelection = new NcdDummySelectionSource(flow, "SelectionString");
		
		if (frameSelection != null) {
			selection = new NcdSelectionForkJoinTransformer(flow, "Selection");
			props.put("Selection.formatParam", frameSelection != null ? frameSelection : "");
		} else {
			selection = new NcdMessageForwarder(flow, "Selection");
		}
		
		if (flags.isEnableDetectorResponse()) {
			detectorResponse = new NcdDetectorResponseForkJoinTransformer(flow, "DetectorResponse");
			((NcdDetectorResponseForkJoinTransformer) detectorResponse).detectorResponseParam.setToken(new ObjectToken(drData));
		} else {
			detectorResponse = new NcdMessageForwarder(flow, "DetectorResponse");
		}
		
		if (flags.isEnableSector()) {
			sectorIntegration = new NcdSectorIntegrationForkJoinTransformer(flow, "SectorIntegration");
			props.put("SectorIntegration.doRadialParam", Boolean.toString(flags.isEnableRadial()));
			props.put("SectorIntegration.doAzimuthalParam", Boolean.toString(flags.isEnableAzimuthal()));
			props.put("SectorIntegration.doFastParam", Boolean.toString(flags.isEnableFastintegration()));
			if (intSector != null) {
				((NcdSectorIntegrationForkJoinTransformer) sectorIntegration).sectorROIParam.setRoi(intSector);
			}
			if (slope != null) {
				((NcdSectorIntegrationForkJoinTransformer) sectorIntegration).gradientParam.setToken(new ObjectToken(slope));
			}
			if (intercept != null) {
				((NcdSectorIntegrationForkJoinTransformer) sectorIntegration).interceptParam.setToken(new ObjectToken(intercept));
			}
			if (cameraLength != null) {
				((NcdSectorIntegrationForkJoinTransformer) sectorIntegration).cameraLengthParam.setToken(new ObjectToken(cameraLength));
			}
			if (energy != null) {
				((NcdSectorIntegrationForkJoinTransformer) sectorIntegration).energyParam.setToken(new ObjectToken(energy));
			}
			if (pxSize != null) {
				((NcdSectorIntegrationForkJoinTransformer) sectorIntegration).pxSizeParam.setToken(new ObjectToken(pxSize));
			}
			if (qaxisUnit != null) {
				((NcdSectorIntegrationForkJoinTransformer) sectorIntegration).axisUnitParam.setToken(new ObjectToken(qaxisUnit));
			}
			if (enableMask && mask != null) {
				((NcdSectorIntegrationForkJoinTransformer) sectorIntegration).maskParam.setToken(new ObjectToken(mask));
			}
			standardise = new NcdStandardiseForkJoinTransformer(flow, "StandardisedIntensity");
		} else {
			sectorIntegration = new NcdMessageForwarder(flow, "SectorIntegration");
			standardise = new NcdMessageForwarder(flow, "StandardisedIntensity");
		}
		
		if (flags.isEnableNormalisation() && flags.isEnableRadial()) {
			normalisation = new NcdNormalisationForkJoinTransformer(flow, "Normalisation");
			props.put("Normalisation.calibrationParam", calibration);
			props.put("Normalisation.absScalingParam", Double.toString(absScaling));
			props.put("Normalisation.normChannelParam", Integer.toString(normChannel));
		} else {
			normalisation = new NcdMessageForwarder(flow, "Normalisation");
		}
		
		if (flags.isEnableBackground() && flags.isEnableRadial()) {
			backgroundSubtraction = new NcdBackgroundSubtractionForkJoinTransformer(flow, "BackgroundSubtraction");
			if (bgScaling != null) {
				props.put("BackgroundSubtraction.bgScalingParam", Double.toString(bgScaling));
			} else {
				props.put("BackgroundSubtraction.bgScalingParam", Double.toString(Double.NaN));
			}
			
			NcdMessageSource bgsource = new NcdMessageSource(flow, "BackgroundMessageSource");
			bgsource.lockParam.setToken(new ObjectToken(lock));
			props.put("BackgroundMessageSource.filenameParam", bgFile);
			props.put("BackgroundMessageSource.detectorParam", bgDetector);
			int bgDimension = flags.isEnableSector() ? 1 : dimension;
			props.put("BackgroundMessageSource.dimensionParam", Integer.toString(bgDimension));
			props.put("BackgroundMessageSource.readOnlyParam", Boolean.toString(!enableBgAverage));
			String[] bgProcessingName = StringUtils.split(filename, "_");
			if (bgProcessingName.length > 1 && enableBgAverage) {
				String bgProcessing = StringUtils.join(new String[] {bgDetector, bgProcessingName[1]}, "_");
				props.put("BackgroundMessageSource.processingParam", bgProcessing);
			}
		
			NcdAbstractDataForkJoinTransformer bgAverage;
			if (enableBgAverage) {
				bgAverage = new NcdAverageForkJoinTransformer(flow, "BackgroundAverage");
				props.put("BackgroundAverage.gridAverageParam", bgGridAverage);
				
				flow.connect(dummySelection.output,((NcdAverageForkJoinTransformer) bgAverage).selectionInput);
			} else {
				bgAverage = new NcdMessageForwarder(flow, "BackgroundAverage");
			}
			
			flow.connect(bgsource.output, bgAverage.input);
			flow.connect(bgAverage.output, ((NcdBackgroundSubtractionForkJoinTransformer) backgroundSubtraction).bgInput);
		} else {
			backgroundSubtraction = new NcdMessageForwarder(flow, "BackgroundSubtraction");
		}
		
		if (flags.isEnableInvariant() && flags.isEnableRadial()) {
			invariant = new NcdInvariantForkJoinTransformer(flow, "Invariant");
		} else {
			invariant = new NcdMessageForwarder(flow, "Invariant");
		}
		
		loglogPlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.LOGLOG_PLOT, flags.isEnableLogLogPlot());
		guinierPlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.GUINIER_PLOT, flags.isEnableGuinierPlot());
		porodPlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.POROD_PLOT, flags.isEnablePorodPlot());
		kratkyPlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.KRATKY_PLOT, flags.isEnableKratkyPlot());
		zimmPlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.ZIMM_PLOT, flags.isEnableZimmPlot());
		debyebuechePlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.DEBYE_BUECHE_PLOT, flags.isEnableDebyeBuechePlot());
		
		DevNullActor nullActor = new DevNullActor(flow, "Null");
		
		if (flags.isEnableAverage()) {
			average = new NcdAverageForkJoinTransformer(flow, "Average");
			props.put("Average.gridAverageParam", gridAverage);
			if (guinierPlot instanceof NcdSaxsPlotDataForkJoinTransformer) {
				NcdSaxsPlotDataForkJoinTransformer testGuinierPlot = new NcdSaxsPlotDataForkJoinTransformer(flow, "guinierTestData");
				testGuinierPlot.plotTypeParam.setToken(new StringToken(SaxsAnalysisPlotType.GUINIER_PLOT.getName()));
				NcdSaxsDataStatsForkJoinTransformer filter = new NcdSaxsDataStatsForkJoinTransformer(flow, "DataFilter");
				filter.statTypeParam.setToken(new ObjectToken(saxsAnalysisStatsParameters));
				
				exportFilter = new NcdProcessingObjectTransformer(flow, "ExportFilter");
				props.put("ExportFilter.datasetNameParam", "selection");
				
				flow.connect(backgroundSubtraction.output, testGuinierPlot.input);
				flow.connect(testGuinierPlot.output, nullActor.input);
				flow.connect(testGuinierPlot.portRg, filter.input);
				flow.connect(filter.output,	exportFilter.input);
				flow.connect(exportFilter.output, ((NcdAverageForkJoinTransformer) average).selectionInput);
			} else {
				flow.connect(dummySelection.output,((NcdAverageForkJoinTransformer) average).selectionInput);
			}
		} else {
			average = new NcdMessageForwarder(flow, "Average");
		}
		
		NcdMessageSink sink = new NcdMessageSink(flow, "result");
		props.put("result.detectorParam", detector);

		flow.connect(source.output, selection.input);
		flow.connect(selection.output, detectorResponse.input);
		flow.connect(detectorResponse.output, sectorIntegration.input);
		flow.connect(sectorIntegration.output, normalisation.input);
		flow.connect(sectorIntegration.output, standardise.input);
		flow.connect(normalisation.output, backgroundSubtraction.input);
		flow.connect(backgroundSubtraction.output, average.input);
		flow.connect(backgroundSubtraction.output, invariant.input);
		flow.connect(average.output, loglogPlot.input);
		flow.connect(average.output, guinierPlot.input);
		flow.connect(average.output, porodPlot.input);
		flow.connect(average.output, kratkyPlot.input);
		flow.connect(average.output, zimmPlot.input);
		flow.connect(average.output, debyebuechePlot.input);
		
		flow.connect(average.output, sink.input);
		flow.connect(standardise.output, nullActor.input);
		flow.connect(loglogPlot.output, nullActor.input);
		flow.connect(guinierPlot.output, nullActor.input);
		flow.connect(porodPlot.output, nullActor.input);
		flow.connect(kratkyPlot.output, nullActor.input);
		flow.connect(zimmPlot.output, nullActor.input);
		flow.connect(debyebuechePlot.output, nullActor.input);
		
		if (flags.isEnableSector() && flags.isEnableAzimuthal()) {
			
			NcdAbstractDataForkJoinTransformer normalisationAzimuthal;
			NcdAbstractDataForkJoinTransformer backgroundSubtractionAzimuthal;
			NcdAbstractDataForkJoinTransformer averageAzimuthal;
			NcdAbstractDataForkJoinTransformer degreeOrientationAzimuthal;
			
			NcdMessageSink azimuthalSink = new NcdMessageSink(flow, "azimuthal");
			props.put("azimuthal.detectorParam", detector);

			if (flags.isEnableNormalisation()) {
				normalisationAzimuthal = new NcdNormalisationForkJoinTransformer(flow, "Normalisation_Azimuthal");
				props.put("Normalisation_Azimuthal.calibrationParam", calibration);
				props.put("Normalisation_Azimuthal.absScalingParam", Double.toString(absScaling));
				props.put("Normalisation_Azimuthal.normChannelParam", Integer.toString(normChannel));
			} else {
				normalisationAzimuthal = new NcdMessageForwarder(flow, "Normalisation_Azimuthal");
			}
			
			if (flags.isEnableBackground()) {
				backgroundSubtractionAzimuthal = new NcdBackgroundSubtractionForkJoinTransformer(flow, "BackgroundSubtraction_Azimuthal");
				if (bgScaling != null) {
					props.put("BackgroundSubtraction_Azimuthal.bgScalingParam", Double.toString(bgScaling));
				} else {
					props.put("BackgroundSubtraction_Azimuthal.bgScalingParam", Double.toString(Double.NaN));
				}
				String bgAzDetector = StringUtils.join(new String[] {detector, azimuthalSink.getName()}, "_");
				NcdMessageSource bgsource = new NcdMessageSource(flow, "BackgroundMessageSource_Azimuthal");
				bgsource.lockParam.setToken(new ObjectToken(lock));
				props.put("BackgroundMessageSource_Azimuthal.filenameParam", bgFile);
				props.put("BackgroundMessageSource_Azimuthal.detectorParam", bgAzDetector);
				props.put("BackgroundMessageSource_Azimuthal.dimensionParam", Integer.toString(1));
				props.put("BackgroundMessageSource_Azimuthal.readOnlyParam", Boolean.toString(!enableBgAverage));
				String[] bgProcessingName = StringUtils.split(filename, "_");
				if (bgProcessingName.length > 1 && enableBgAverage) {
					String bgProcessing = StringUtils.join(new String[] {bgAzDetector, bgProcessingName[1]}, "_");
					props.put("BackgroundMessageSource_Azimuthal.processingParam", bgProcessing);
				}
			
				NcdAbstractDataForkJoinTransformer bgAverage;
				if (enableBgAverage) {
					bgAverage = new NcdAverageForkJoinTransformer(flow, "BackgroundAverage_Azimuthal");
					props.put("BackgroundAverage_Azimuthal.gridAverageParam", bgGridAverage);
					
					flow.connect(dummySelection.output,((NcdAverageForkJoinTransformer) bgAverage).selectionInput);
				} else {
					bgAverage = new NcdMessageForwarder(flow, "BackgroundAverage_Azimuthal");
				}
				
				flow.connect(bgsource.output, bgAverage.input);
				flow.connect(bgAverage.output, ((NcdBackgroundSubtractionForkJoinTransformer) backgroundSubtractionAzimuthal).bgInput);
			} else {
				backgroundSubtractionAzimuthal = new NcdMessageForwarder(flow, "BackgroundSubtraction_Azimuthal");
			}
			
			if (flags.isEnableAverage()) {
				averageAzimuthal = new NcdAverageForkJoinTransformer(flow, "Average_Azimuthal");
				props.put("Average_Azimuthal.gridAverageParam", gridAverage);
				if (guinierPlot instanceof NcdSaxsPlotDataForkJoinTransformer && exportFilter != null) {
					flow.connect(exportFilter.output, ((NcdAverageForkJoinTransformer) averageAzimuthal).selectionInput);
				} else {
					flow.connect(dummySelection.output,((NcdAverageForkJoinTransformer) averageAzimuthal).selectionInput);
				}
			} else {
				averageAzimuthal = new NcdMessageForwarder(flow, "Average_Azimuthal");
			}
			
			if (intSector.checkSymmetry(SectorROI.FULL)) {
				degreeOrientationAzimuthal = new NcdOrientationForkJoinTransformer(flow, "DegreeOfOrientation_Azimuthal");
			} else {
				degreeOrientationAzimuthal = new NcdMessageForwarder(flow, "DegreeOfOrientation_Azimuthal");
			}
			
			flow.connect(((NcdSectorIntegrationForkJoinTransformer)sectorIntegration).portAzimuthal, normalisationAzimuthal.input);
			flow.connect(normalisationAzimuthal.output, backgroundSubtractionAzimuthal.input);
			flow.connect(backgroundSubtractionAzimuthal.output, averageAzimuthal.input);
			flow.connect(averageAzimuthal.output, azimuthalSink.input);
			flow.connect(averageAzimuthal.output, degreeOrientationAzimuthal.input);
			flow.connect(degreeOrientationAzimuthal.output, nullActor.input);
		}
					
		flowMgr.executeBlockingErrorLocally(flow, props);		
	}

	private NcdAbstractDataForkJoinTransformer addSaxsPlotActor(CompositeEntity flow, SaxsAnalysisPlotType plotType, boolean enable)
			throws NameDuplicationException, IllegalActionException {
		NcdAbstractDataForkJoinTransformer saxsPlot;
		if (enable) {
			saxsPlot = new NcdSaxsPlotDataForkJoinTransformer(flow, plotType.getGroupName());
			((NcdSaxsPlotDataForkJoinTransformer) saxsPlot).plotTypeParam.setToken(new StringToken(plotType.getName()));
		} else {
			saxsPlot = new NcdMessageForwarder(flow, plotType.getGroupName());
		}
		return saxsPlot;
	}
}
