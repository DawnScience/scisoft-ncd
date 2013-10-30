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

package uk.ac.diamond.scisoft.ncd.rcp.edna.actions;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import uk.ac.diamond.scisoft.ncd.rcp.edna.ModelBuildingParameters;
import uk.ac.diamond.scisoft.ws.rcp.RunPipeline;

public class RunNcdModelBuilderPipeline extends RunPipeline {
	protected static RunNcdModelBuilderPipeline runNcdModelBuilderPipeline = null;
	ModelBuildingParameters parameters;

	public static RunNcdModelBuilderPipeline getInstance() {
		if (runNcdModelBuilderPipeline == null) {
			runNcdModelBuilderPipeline = new RunNcdModelBuilderPipeline();
		}
		return runNcdModelBuilderPipeline;
	}

	public RunNcdModelBuilderPipeline() {
		super("BioSAXS Model Builder", "BioSAXS Model Builder Pipeline");
	}
	
	public RunNcdModelBuilderPipeline(ModelBuildingParameters params) {
		this();
		parameters = params;
	}

	@Override
	public String getParameters() {
		String inputParameters = parameters.toString();
		if (!parameters.isValid()) {
			final Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
			MessageDialog.openError(shell, "Bad parameters", parameters.invalidMessage());
			throw new IllegalArgumentException(parameters.invalidMessage());
		}
		return inputParameters;
	}

	@Override
	public String getWorkingDirectory() {
		return "something";
	}

}
