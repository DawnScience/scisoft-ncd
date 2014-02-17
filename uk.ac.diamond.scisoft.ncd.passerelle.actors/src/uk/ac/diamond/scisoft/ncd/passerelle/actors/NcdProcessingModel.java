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
import javax.measure.unit.Unit;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.dawnsci.plotting.tools.preference.detector.DiffractionDetector;
import org.eclipse.core.runtime.IProgressMonitor;
import org.jscience.physics.amount.Amount;

import ptolemy.data.ObjectToken;
import ptolemy.data.StringToken;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.general.DevNullActor;
import com.isencia.passerelle.actor.v5.ActorContext;
import com.isencia.passerelle.actor.v5.ProcessRequest;
import com.isencia.passerelle.actor.v5.ProcessResponse;
import com.isencia.passerelle.core.PasserelleException;
import com.isencia.passerelle.domain.et.ETDirector;
import com.isencia.passerelle.message.ManagedMessage;
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
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdMessageSink;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdMessageSource;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdAbstractDataForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdAverageForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdBackgroundSubtractionForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdDetectorResponseForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdInvariantForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdNormalisationForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSaxsPlotDataForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSectorIntegrationForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSelectionForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;
import uk.ac.diamond.scisoft.ncd.core.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.core.service.IDataReductionProcess;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

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
	private AbstractDataset drData;

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
	
	private static FlowManager flowMgr;
	
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
	
	public NcdProcessingModel() {
		super();
		
		flowMgr = new FlowManager();
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
		mask = new BooleanDataset();
		drData = new FloatDataset();
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
			preprocessBackgroundData(filename);
		}
		
	}

	@Override
	public void execute(String filename, IProgressMonitor monitor) {

		NcdAbstractDataForkJoinTransformer selection;
		NcdAbstractDataForkJoinTransformer detectorResponse;
		NcdAbstractDataForkJoinTransformer sectorIntegration;
		NcdAbstractDataForkJoinTransformer normalisation;
		NcdAbstractDataForkJoinTransformer backgroundSubtraction;
		NcdAbstractDataForkJoinTransformer invariant;
		NcdAbstractDataForkJoinTransformer average;
		NcdAbstractDataForkJoinTransformer loglogPlot;
		NcdAbstractDataForkJoinTransformer guinierPlot;
		NcdAbstractDataForkJoinTransformer porodPlot;
		NcdAbstractDataForkJoinTransformer kratkyPlot;
		NcdAbstractDataForkJoinTransformer zimmPlot;
		NcdAbstractDataForkJoinTransformer debyebuechePlot;
		
		try {
			configure(filename);

			Flow flow = new Flow("NCD Data Reduction", null);
			
			ETDirector director = new ETDirector(flow, "director");
			flow.setDirector(director);
			
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
			} else {
				sectorIntegration = new NcdMessageForwarder(flow, "SectorIntegration");
			}
			
			if (flags.isEnableNormalisation()) {
				normalisation = new NcdNormalisationForkJoinTransformer(flow, "Normalisation");
				props.put("Normalisation.calibrationParam", calibration);
				props.put("Normalisation.absScalingParam", Double.toString(absScaling));
				props.put("Normalisation.normChannelParam", Integer.toString(normChannel));
			} else {
				normalisation = new NcdMessageForwarder(flow, "Normalisation");
			}
			
			if (flags.isEnableBackground()) {
				backgroundSubtraction = new NcdBackgroundSubtractionForkJoinTransformer(flow, "BackgroundSubtraction");
				props.put("BackgroundSubtraction.bgScalingParam", Double.toString(bgScaling));
				
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
				} else {
					bgAverage = new NcdMessageForwarder(flow, "BackgroundAverage");
				}
				
				flow.connect(bgsource.output, bgAverage.input);
				flow.connect(bgAverage.output, ((NcdBackgroundSubtractionForkJoinTransformer) backgroundSubtraction).bgInput);
			} else {
				backgroundSubtraction = new NcdMessageForwarder(flow, "BackgroundSubtraction");
			}
			
			if (flags.isEnableInvariant()) {
				invariant = new NcdInvariantForkJoinTransformer(flow, "Invariant");
			} else {
				invariant = new NcdMessageForwarder(flow, "Invariant");
			}
			
			if (flags.isEnableAverage()) {
				average = new NcdAverageForkJoinTransformer(flow, "Average");
				props.put("Average.gridAverageParam", gridAverage);
			} else {
				average = new NcdMessageForwarder(flow, "Average");
			}
			
			loglogPlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.LOGLOG_PLOT.getName(), flags.isEnableLogLogPlot());
			guinierPlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.GUINIER_PLOT.getName(), flags.isEnableGuinierPlot());
			porodPlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.POROD_PLOT.getName(), flags.isEnablePorodPlot());
			kratkyPlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.KRATKY_PLOT.getName(), flags.isEnableKratkyPlot());
			zimmPlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.ZIMM_PLOT.getName(), flags.isEnableZimmPlot());
			debyebuechePlot = addSaxsPlotActor(flow, SaxsAnalysisPlotType.DEBYE_BUECHE_PLOT.getName(), flags.isEnableDebyeBuechePlot());
		    
			NcdMessageSink sink = new NcdMessageSink(flow, "MessageSink");
			props.put("MessageSink.detectorParam", detector);

			DevNullActor nullActor = new DevNullActor(flow, "Null");
			
			flow.connect(source.output, selection.input);
			flow.connect(selection.output, detectorResponse.input);
			flow.connect(detectorResponse.output, sectorIntegration.input);
			flow.connect(sectorIntegration.output, normalisation.input);
			flow.connect(normalisation.output, backgroundSubtraction.input);
			flow.connect(backgroundSubtraction.output, invariant.input);
			flow.connect(backgroundSubtraction.output, average.input);
			flow.connect(average.output, loglogPlot.input);
			flow.connect(average.output, guinierPlot.input);
			flow.connect(average.output, porodPlot.input);
			flow.connect(average.output, kratkyPlot.input);
			flow.connect(average.output, zimmPlot.input);
			flow.connect(average.output, debyebuechePlot.input);
			
			flow.connect(average.output, sink.input);
			flow.connect(invariant.output, nullActor.input);
			flow.connect(loglogPlot.output, nullActor.input);
			flow.connect(guinierPlot.output, nullActor.input);
			flow.connect(porodPlot.output, nullActor.input);
			flow.connect(kratkyPlot.output, nullActor.input);
			flow.connect(zimmPlot.output, nullActor.input);
			flow.connect(debyebuechePlot.output, nullActor.input);

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

	private NcdAbstractDataForkJoinTransformer addSaxsPlotActor(CompositeEntity flow, String name, boolean enable)
			throws NameDuplicationException, IllegalActionException {
		NcdAbstractDataForkJoinTransformer saxsPlot;
		if (enable) {
			saxsPlot = new NcdSaxsPlotDataForkJoinTransformer(flow, name);
			((NcdSaxsPlotDataForkJoinTransformer) saxsPlot).plotTypeParam.setToken(new StringToken(name));
		} else {
			saxsPlot = new NcdMessageForwarder(flow, name);
		}
		return saxsPlot;
	}
}
