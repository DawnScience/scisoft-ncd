/*
 * Copyright 2013 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.actors;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.dawb.common.services.ServiceManager;
import org.dawb.common.services.conversion.IConversionContext;
import org.dawb.common.services.conversion.IConversionContext.ConversionScheme;
import org.dawb.common.services.conversion.IConversionService;
import org.dawb.passerelle.common.actors.AbstractDataMessageTransformer;
import org.dawb.passerelle.common.message.IVariable;
import org.dawb.passerelle.common.message.IVariable.VARIABLE_TYPE;
import org.dawb.passerelle.common.message.MessageUtils;
import org.dawb.passerelle.common.message.Variable;
import org.dawb.passerelle.common.parameter.ParameterUtils;
import org.dawnsci.conversion.converters.CustomNCDConverter;

import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.message.DataMessageComponent;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.util.ptolemy.ResourceParameter;

public class AsciiOutputTransformer extends AbstractDataMessageTransformer {

	private static final long serialVersionUID = -5124463707322206927L;

	private ResourceParameter datasetname, axisname, filetype;
	private StringParameter resultsFilePath;
	private StringParameter outputFileParam;

	public AsciiOutputTransformer(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);
		
		datasetname = new ResourceParameter(this, "Intensity Dataset");
		datasetname.setExpression("${dataset}");
		registerConfigurableParameter(datasetname);
		
		axisname = new ResourceParameter(this, "Axis Dataset");
		axisname.setExpression("${axis}");
		registerConfigurableParameter(axisname);
		
		filetype = new ResourceParameter(this, "File Type");
		filetype.setExpression("${filetype}");
		registerConfigurableParameter(filetype);

		resultsFilePath = new StringParameter(this, "Results File");
		resultsFilePath.setExpression("${file_path}");
		registerConfigurableParameter(resultsFilePath);

		outputFileParam = new StringParameter(this, "Results Output Name");
		outputFileParam.setExpression("results_path");
		registerConfigurableParameter(outputFileParam);
	}

	@Override
	public List<IVariable> getOutputVariables() {
		final List<IVariable> ret = super.getOutputVariables();
		ret.add(new Variable(outputFileParam.getExpression(), VARIABLE_TYPE.PATH, "<path to exported ascii data>", String.class));
		return ret;
	}

	@Override
	protected DataMessageComponent getTransformedMessage(
			List<DataMessageComponent> cache) throws ProcessingException {

		try {
			final String rawPath = ParameterUtils.getSubstituedValue(resultsFilePath, cache);
			final String thedata = ParameterUtils.getSubstituedValue(datasetname, cache);
			final String theaxis = ParameterUtils.getSubstituedValue(axisname, cache);
			final String thetype = ParameterUtils.getSubstituedValue(filetype, cache);

			final DataMessageComponent ret = MessageUtils.mergeAll(cache);

			IConversionService service = (IConversionService) ServiceManager.getService(IConversionService.class);

			IConversionContext context = service.open(rawPath);
			context.setConversionScheme(ConversionScheme.CUSTOM_NCD);
			context.setOutputPath(new File(rawPath).getParent());
			context.setDatasetNames(Arrays.asList(new String[] { thedata }));
			context.setAxisDatasetName(theaxis);
			context.addSliceDimension(0, "all");

			if ("XML".equalsIgnoreCase(thetype) || "CANSAS".equalsIgnoreCase(thetype))
				context.setUserObject(CustomNCDConverter.SAS_FORMAT.CANSAS);
			else if ("ATSAS".equalsIgnoreCase(thetype))
				context.setUserObject(CustomNCDConverter.SAS_FORMAT.ATSAS);
			else 
				context.setUserObject(CustomNCDConverter.SAS_FORMAT.ASCII);
			
			service.process(context);

			String filename = null;
			
			if (context.getSelectedConversionFile() != null)
				filename = context.getSelectedConversionFile().getAbsolutePath();
			
			ret.putScalar(outputFileParam.getExpression(), filename);

			return ret;
		} catch (Exception ne) {
			throw createDataMessageException(ne.getMessage(), ne);
		}
	}

	@Override
	protected String getOperationName() {
		return "NCD ASCII Output";
	}
}