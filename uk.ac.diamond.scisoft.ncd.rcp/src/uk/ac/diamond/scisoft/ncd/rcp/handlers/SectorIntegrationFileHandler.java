/*
 * Copyright © 2011 Diamond Light Source Ltd.
 * Contact :  ScientificSoftware@diamond.ac.uk
 * 
 * This is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 * 
 * This software is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this software. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.rcp.editors.HDF5TreeEditor;
import uk.ac.diamond.scisoft.analysis.rcp.inspector.DatasetSelection.InspectorType;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;
import uk.ac.gda.common.rcp.util.EclipseUtils;

public class SectorIntegrationFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(SectorIntegrationFileHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final ISelection selection = HandlerUtil.getCurrentSelection(event);

		if (selection instanceof IStructuredSelection) {
			if (((IStructuredSelection)selection).toList().size() == 1 && (((IStructuredSelection)selection).getFirstElement() instanceof IFile)) {

				final Object sel = ((IStructuredSelection)selection).getFirstElement();
				try {

					int idxSaxs = NcdDataReductionParameters.getDetListSaxs().getSelectionIndex();
					if (idxSaxs >= 0) {
						String detectorSaxs = NcdDataReductionParameters.getDetListSaxs().getItem(idxSaxs);
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
