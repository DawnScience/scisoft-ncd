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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;

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
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdMessageSink;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdMessageSource;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdAverageForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdNormalisationForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSectorIntegrationForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.preferences.NcdReductionFlags;

public class NcdProcessingModel {

	private int normChannel;
	private String calibration;
	private SectorROI intSector;
	private Amount<ScatteringVectorOverDistance> slope;
	private Amount<ScatteringVector> intercept;
	private Amount<Length> cameraLength;
	private Amount<Energy> energy;
	private BooleanDataset mask;

	private CalibrationResultsBean crb;
	
	private NcdReductionFlags flags;
	private Double absScaling;
	private String gridAverage;
	
	private static Flow flow;
	private static FlowManager flowMgr;

	
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
	
	public void configure(String detector) {
		if (crb != null && crb.containsKey(detector)) {
			if (slope == null) {
				slope = crb.getGradient(detector);
			}
			if (intercept == null) {
				intercept = crb.getIntercept(detector);
			}
			cameraLength = crb.getMeanCameraLength(detector);
		}
	}

	public void execute(String detectorName, int dimension, String filename) {
		
		configure(detectorName);
		
		try {
		NcdMessageSource source = new NcdMessageSource(flow, "MessageSource");
		NcdSectorIntegrationForkJoinTransformer sectorIntegration = new NcdSectorIntegrationForkJoinTransformer(flow, "SectorIntegration");
		NcdNormalisationForkJoinTransformer normalisation = new NcdNormalisationForkJoinTransformer(flow, "Normalisation");
		NcdAverageForkJoinTransformer average = new NcdAverageForkJoinTransformer(flow, "Average");
		NcdMessageSink sink = new NcdMessageSink(flow, "MessageSink");

		flow.connect(source.output, sectorIntegration.input);
		flow.connect(sectorIntegration.output, normalisation.input);
		flow.connect(normalisation.output, average.input);
		flow.connect(average.output, sink.input);
		
		source.lockParam.setToken(new ObjectToken(new ReentrantLock()));
		
		sectorIntegration.sectorROIParam.setToken(new ObjectToken(intSector));
		sectorIntegration.gradientParam.setToken(new ObjectToken(slope));
		sectorIntegration.interceptParam.setToken(new ObjectToken(intercept));
		sectorIntegration.cameraLengthParam.setToken(new ObjectToken(cameraLength));
		sectorIntegration.energyParam.setToken(new ObjectToken(energy));
		sectorIntegration.maskParam.setToken(new ObjectToken(mask));
		
		
		Map<String, String> props = new HashMap<String, String>();
		
		props.put("MessageSource.filenameParam", filename);
		props.put("MessageSource.detectorParam", detectorName);
		
		props.put("SectorIntegration.enable", Boolean.toString(flags.isEnableSector()));
		props.put("SectorIntegration.dimensionParam", Integer.toString(dimension));
		props.put("SectorIntegration.doRadialParam", Boolean.toString(flags.isEnableRadial()));
		props.put("SectorIntegration.doAzimuthalParam", Boolean.toString(flags.isEnableAzimuthal()));
		props.put("SectorIntegration.doFastParam", Boolean.toString(flags.isEnableFastintegration()));
		
		if (flags.isEnableSector()) {
			dimension = 1;
		}

		props.put("Normalisation.enable", Boolean.toString(flags.isEnableNormalisation()));
		props.put("Normalisation.calibrationParam", calibration);
		props.put("Normalisation.absScalingParam", Double.toString(absScaling));
		props.put("Normalisation.normChannelParam", Integer.toString(normChannel));
		props.put("Normalisation.dimensionParam", Integer.toString(dimension));
		
		props.put("Average.enable", Boolean.toString(flags.isEnableAverage()));
		props.put("Average.dimensionParam", Integer.toString(dimension));
		props.put("Average.gridAverageParam", gridAverage);

		flowMgr.executeBlockingLocally(flow, props);
		
		} catch (FlowAlreadyExecutingException e) {
			throw new RuntimeException(e);
		} catch (PasserelleException e) {
			throw new RuntimeException(e);
		} catch (IllegalActionException e) {
			throw new RuntimeException(e);
		} catch (NameDuplicationException e) {
			throw new RuntimeException(e);
		}
	}
}
