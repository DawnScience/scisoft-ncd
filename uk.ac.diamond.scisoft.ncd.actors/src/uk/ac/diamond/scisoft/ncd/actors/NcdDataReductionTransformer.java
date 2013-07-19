package uk.ac.diamond.scisoft.ncd.actors;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.measure.quantity.Energy;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.dawb.common.services.IPersistenceService;
import org.dawb.common.services.IPersistentFile;
import org.dawb.passerelle.common.actors.AbstractDataMessageTransformer;
import org.dawb.passerelle.common.message.DataMessageComponent;
import org.dawb.passerelle.common.message.DataMessageException;
import org.dawb.passerelle.common.message.IVariable;
import org.dawb.passerelle.common.message.MessageUtils;
import org.dawb.passerelle.common.message.Variable;
import org.dawb.passerelle.common.message.IVariable.VARIABLE_TYPE;
import org.dawb.passerelle.common.parameter.ParameterUtils;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.swt.SWT;
import org.jscience.physics.amount.Amount;

import ptolemy.data.IntToken;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Settable;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.monitor.IMonitor;
import uk.ac.diamond.scisoft.analysis.roi.IROI;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdSourceProviderAdapter;
import uk.ac.diamond.scisoft.ncd.reduction.service.IDataReductionContext;
import uk.ac.diamond.scisoft.ncd.reduction.service.IDataReductionService;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.util.ptolemy.IAvailableChoices;
import com.isencia.passerelle.util.ptolemy.ResourceParameter;
import com.isencia.passerelle.util.ptolemy.StringChoiceParameter;
import com.isencia.passerelle.workbench.util.ResourceUtils;

public class NcdDataReductionTransformer extends AbstractDataMessageTransformer {

	/**
	 * 
	 */
	private static final long serialVersionUID = -5124463707332206927L;
	
	private ResourceParameter     xmlPathParam, persistenceParam;
	private StringChoiceParameter maskName, sectorName;
	private StringParameter       rawFilePath;
	private StringParameter       resultsFileParam;

	/**
	 * Attributes:
	 * ===========
	 * 
	 *  1. XML file, used to setup the actor.
	 *  2. Mask and region, based into the actor in pipeline (normally the same)
	 *  3. Raw file path, passed in during pipeline.
	 *            
	 * @param container
	 * @param name
	 * @throws NameDuplicationException
	 * @throws IllegalActionException
	 */
	public NcdDataReductionTransformer(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException {
		super(container, name);
		
		xmlPathParam = new ResourceParameter(this, "XML Configuration");
		xmlPathParam.setResourceType(IResource.FILE);
		registerConfigurableParameter(xmlPathParam);
		
		persistenceParam = new ResourceParameter(this, "Persistence File");
		persistenceParam.setResourceType(IResource.FILE);
		registerConfigurableParameter(persistenceParam);
		
		maskName = new StringChoiceParameter(this, "Mask Name", new IAvailableChoices.Stub() {		
			@Override
			public String[] getChoices() {
				return getMaskNames();
			}
		}, SWT.SINGLE);
		registerConfigurableParameter(maskName);
		
		sectorName = new StringChoiceParameter(this, "Sector Name", new IAvailableChoices.Stub() {		
			@Override
			public String[] getChoices() {
				return getRoiNames();
			}
		}, SWT.SINGLE);
		registerConfigurableParameter(sectorName);
		
		rawFilePath = new StringParameter(this, "Raw file");
		rawFilePath.setExpression("${file_path}");
		registerConfigurableParameter(rawFilePath);
		
		resultsFileParam  = new StringParameter(this, "Results Output Name");
		resultsFileParam.setExpression("results_path");
		registerConfigurableParameter(resultsFileParam);
		
		memoryManagementParam.setVisibility(Settable.NONE);
		dataSetNaming.setVisibility(Settable.NONE);
		
		// This forces only one data reduction file to run at a time.
		receiverQueueCapacityParam.setToken(new IntToken(1)); // They can change this in expert mode if required.
	}
	
	@Override
	public List<IVariable> getOutputVariables() {
		
        final List<IVariable> ret = super.getOutputVariables();
		ret.add(new Variable(resultsFileParam.getExpression(),  VARIABLE_TYPE.PATH,   "<path to data reduction results>", String.class));
		ret.add(new Variable("results_file_name",  VARIABLE_TYPE.PATH,   "<name of results file written>", String.class));
    
        return ret;
	}


	private IDataReductionService service;
	private IDataReductionContext context;

	@Override
	protected DataMessageComponent getTransformedMessage(List<DataMessageComponent> cache) throws ProcessingException {
		
		createServiceConnectionIfRequired(cache);
		
		try {
			final String rawPath = ParameterUtils.getSubstituedValue(rawFilePath, cache);
			service.process(rawPath, context, new NullProgressMonitor());
			
			final DataMessageComponent ret = MessageUtils.mergeAll(cache);
			final String results = context.getResultsFile();
			ret.putScalar(resultsFileParam.getExpression(), results);
			ret.putScalar("results_file_name", (new File(results)).getName());
			
			return ret;
			
		} catch (Exception ne) {
			throw createDataMessageException(ne.getMessage(), ne);
		}
	}

	private void createServiceConnectionIfRequired(List<DataMessageComponent> cache) throws ProcessingException {
		
        if (service!=null) return; // We have already created and setup the service.
        
        service = (IDataReductionService)Activator.getService(IDataReductionService.class);
        if (service == null) throw createDataMessageException("Cannot find IDataReductionService using activator!", new Exception());
        
        try {
	        context = service.createContext();
	        final String xmlPath = getPath(xmlPathParam, cache);    
	        createData(context, xmlPath);
	        
			createMaskAndRegion(context, cache);
			service.configure(context);
	        
        } catch (Exception ne) {
        	throw createDataMessageException("Cannot parse xml file!", ne);
        }
	}

	private void createMaskAndRegion(IDataReductionContext context, List<DataMessageComponent> cache)  throws Exception{
		
		final String path = getPath(persistenceParam, cache);
		
		final IPersistenceService pservice = (IPersistenceService)Activator.getService(IPersistenceService.class);
		final IPersistentFile     pfile    = pservice.createPersistentFile(path);
		try {
		
			final IDataset mask = pfile.getMask(maskName.getExpression(), new IMonitor.Stub());
			context.setMask((BooleanDataset)mask);
			
			final IROI sector = pfile.getROI(sectorName.getExpression());
			context.setSector((SectorROI)sector);
		} finally {
			pfile.close();
		}
	}
	
	
	protected String[] getMaskNames() {
		try {
			final String path = getPath(persistenceParam, null);
			if (path==null) return new String[]{"Please define a persistence file first."};
			final IPersistenceService pservice = (IPersistenceService)Activator.getService(IPersistenceService.class);
			final IPersistentFile     pfile    = pservice.createPersistentFile(path);
			try {
				
				Collection<String> maskNames = pfile.getMaskNames(new IMonitor.Stub());
				return maskNames.toArray(new String[maskNames.size()]);
			} catch (Exception ne) {
				return new String[]{ne.getMessage()}; // Bit naughty, do not copy, but might be handy.
			} finally {
				pfile.close();
			}
		} catch (Exception ne) {
			return new String[]{ne.getMessage()}; // Bit naughty, do not copy, but might be handy.
		}
	}
	
	protected String[] getRoiNames() {
		
		try {
			final String path = getPath(persistenceParam, null);
			if (path==null) return new String[]{"Please define a persistence file first."};
			final IPersistenceService pservice = (IPersistenceService)Activator.getService(IPersistenceService.class);
			final IPersistentFile     pfile    = pservice.createPersistentFile(path);
			try {
				
				Collection<String> roiNames = pfile.getROINames(new IMonitor.Stub());
				return roiNames.toArray(new String[roiNames.size()]);
			} catch (Exception ne) {
				return new String[]{ne.getMessage()}; // Bit naughty, do not copy, but might be handy.
			} finally {
				pfile.close();
			}
		} catch (Exception ne) {
			return new String[]{ne.getMessage()}; // Bit naughty, do not copy, but might be handy.
		}
	}


	private void createData(IDataReductionContext context, String xmlPath) throws Exception {
	    
		NcdSourceProviderAdapter adapter = getSourceProviderAdapter(xmlPath);
		
		NcdProcessingSourceProvider  processing  = adapter.getNcdProcessingSourceProvider();
		
		context.setEnableNormalisation(processing.isEnableNormalisation());		
		context.setEnableBackground(processing.isEnableBackground());
		context.setEnableDetectorResponse(processing.isEnableDetectorResponse());
		context.setEnableSector(processing.isEnableSector());
		context.setEnableInvariant(processing.isEnableInvariant());
		context.setEnableAverage(processing.isEnableAverage());
		context.setCalibrationName(processing.getScaler());
		context.setEnableWaxs(processing.isEnableWaxs());
		context.setWaxsDetectorName(processing.getWaxsDetector());

		context.setEnableSaxs(processing.isEnableSaxs());
		context.setSaxsDetectorName(processing.getSaxsDetector());
		context.setDataSliceInput(processing.getDataSlice());
		context.setBgSliceInput(processing.getBkgSlice());
		context.setGridAverageSlice(processing.getGridAverage());
		context.setBgPath(processing.getBgFile());
		context.setDrFile(processing.getDrFile());
		context.setWorkingDir(processing.getWorkingDir());
        context.setEnableMask(processing.isEnableMask());
		context.setEnableRadial(processing.isEnableRadial());
		context.setEnableAzimuthal(processing.isEnableAzimuthal());
		context.setEnableFastIntegration(processing.isEnableFastIntegration());
		context.setAbsScaling(processing.getAbsScaling());
		context.setSampleThickness(processing.getSampleThickness());
		context.setBgScaling(processing.getBgScaling());
		
		NcdCalibrationSourceProvider calibration = adapter.getNcdCalibrationSourceProvider();
		context.setDetWaxsInfo(calibration.getNcdDetectors().get(context.getWaxsDetectorName()));
		context.setDetSaxsInfo(calibration.getNcdDetectors().get(context.getSaxsDetectorName()));
		context.setScalerData(calibration.getNcdDetectors().get(context.getCalibrationName()));
		
		CalibrationResultsBean crb = (CalibrationResultsBean) calibration.getCurrentState().get(NcdCalibrationSourceProvider.CALIBRATION_STATE);
		context.setCalibrationResults(crb);
		
		Amount<Energy> energy = processing.getEnergy();
		context.setEnergy(energy.doubleValue(SI.KILO(NonSI.ELECTRON_VOLT)));

	}
	
	private NcdSourceProviderAdapter getSourceProviderAdapter(final String xmlPath) throws Exception {
		
		final File file = new File(xmlPath);
		FileReader reader=null;
		try {
			reader = new FileReader(file);
			
			JAXBContext jc = JAXBContext.newInstance (NcdSourceProviderAdapter.class);
			Unmarshaller u = jc.createUnmarshaller ();
			
			return (NcdSourceProviderAdapter) u.unmarshal(reader);
			
		} catch (Exception ne) {
			throw new Exception("Cannot export ncd parameters", ne);
		} finally {
			if (reader!=null) {
				try {
					reader.close();
				} catch (IOException e) {
					throw new Exception("Cannot export ncd parameters", e);
				}
		    }
		}
	}
	

	@Override
	protected String getOperationName() {
		return "NCD Data Reduction";
	}
	
	private String getPath(ResourceParameter param, List<DataMessageComponent> cache) throws DataMessageException {
		
		if (param.getExpression()==null||"".equals(param.getExpression())) return null;
		try {
			String path  = ParameterUtils.getSubstituedValue(param, cache);
			try {
				final File d = new File(path);
				if (d.exists()) return d.getAbsolutePath();
			} catch (Throwable ignored) {
				// parse as a resource
			}
			
			IResource file = ResourceUtils.getResource(path);
			return file.getLocation().toOSString();
			
		} catch (Exception ne) {
			throw createDataMessageException("Cannot get folder path!", ne);
		}
	}

}