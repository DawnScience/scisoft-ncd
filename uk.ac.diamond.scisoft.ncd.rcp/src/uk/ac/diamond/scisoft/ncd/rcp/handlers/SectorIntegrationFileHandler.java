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

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import org.dawb.common.ui.util.EclipseUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.services.ISourceProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.rcp.editors.HDF5TreeEditor;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection.InspectorType;
import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class SectorIntegrationFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(SectorIntegrationFileHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);

		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection) {
			if (((IStructuredSelection)selection).toList().size() == 1 && (((IStructuredSelection)selection).getFirstElement() instanceof IFile)) {

				final Object sel = ((IStructuredSelection)selection).getFirstElement();
				try {
					String detectorSaxs = ncdSaxsDetectorSourceProvider.getSaxsDetector();
					if (detectorSaxs != null) {
						HDF5TreeEditor editor = (HDF5TreeEditor)EclipseUtils.openExternalEditor(((IFile)sel).getLocation().toString());
						HDF5NodeLink node = editor.getHDF5Tree().findNodeLink("/entry1/"+detectorSaxs+"/data");

						editor.getHDF5TreeExplorer().selectHDF5Node(node, InspectorType.IMAGE);

						return Status.OK_STATUS;
					} 
					return Status.CANCEL_STATUS;

				} catch (Exception e) {
					logger.error("File "+((IFile)sel).getLocation().toString()+" does not open with " + HDF5TreeEditor.ID, e);
					return Status.CANCEL_STATUS;
				}
			}

		}
		return null;
	}
}
