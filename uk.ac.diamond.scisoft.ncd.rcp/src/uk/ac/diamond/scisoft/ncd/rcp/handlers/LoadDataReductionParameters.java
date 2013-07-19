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

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.dawb.common.ui.util.EclipseUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;

import uk.ac.diamond.scisoft.ncd.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;
import uk.ac.diamond.scisoft.ncd.rcp.NcdSourceProviderAdapter;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;

/**
 * This class saves out the DataReductionParameters to an xml file. This
 * can be loaded back into the view manually. This means that as well as the view
 * remembering how it was left using a normal memento, you can have different 
 * configurations of the parameters for different files. In addition
 * this file can be used as an input for the workflows.
 */
public class LoadDataReductionParameters extends AbstractHandler {

	private static String filterPath;
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		final IViewPart part = EclipseUtils.getPage().findView(NcdDataReductionParameters.ID);
		if (part!=null) {
			
			final FileDialog dialog = new FileDialog(Display.getDefault().getActiveShell(), SWT.OPEN);
			dialog.setFilterExtensions(new String[]{"*.xml"});
			dialog.setFilterPath(filterPath);
			
			final String path = dialog.open();
			if (path==null) return null;
			
			final File file = new File(path);
			filterPath = file.getParent();
			
			if (!file.exists()) {
				MessageDialog.openError(Display.getDefault().getActiveShell(), "Non-existent file", "The file '"+file.getName()+"' does not exist.");
				return null;
			}
			
			FileReader reader = null;
			try {
				reader = new FileReader(file);
				
				JAXBContext jc = JAXBContext.newInstance (NcdSourceProviderAdapter.class);
				Unmarshaller u = jc.createUnmarshaller ();
				NcdSourceProviderAdapter data = (NcdSourceProviderAdapter) u.unmarshal(reader);
				
				ISourceProviderService service = (ISourceProviderService) PlatformUI.getWorkbench().getService(ISourceProviderService.class);
				
				NcdProcessingSourceProvider   ncdProcessingSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXS_STATE);
				NcdCalibrationSourceProvider ncdCalibrationSourceProvider = (NcdCalibrationSourceProvider)service.getSourceProvider(NcdCalibrationSourceProvider.CALIBRATION_STATE);
				
				ncdCalibrationSourceProvider.setAll(data.getNcdCalibrationSourceProvider());
				ncdProcessingSourceProvider.setAll(data.getNcdProcessingSourceProvider());
				
			} catch (Exception ne) {
				throw new ExecutionException("Cannot export ncd parameters", ne);
			} finally {
				if (reader!=null) {
					try {
						reader.close();
					} catch (IOException e) {
						throw new ExecutionException("Cannot export ncd parameters", e);
					}
			    }
			}
		}
		return Boolean.TRUE;
	}
	
}
