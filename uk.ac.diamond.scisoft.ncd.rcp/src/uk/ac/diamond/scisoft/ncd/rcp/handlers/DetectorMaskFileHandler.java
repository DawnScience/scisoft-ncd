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

import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeBuilder;
import gda.data.nexus.tree.NexusTreeNodeSelection;

import java.io.StringReader;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.analysis.plotserver.GuiParameters;
import uk.ac.diamond.scisoft.analysis.rcp.views.PlotView;
import uk.ac.diamond.scisoft.analysis.roi.MaskingBean;
import uk.ac.diamond.scisoft.ncd.rcp.NcdPerspective;
import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;

public class DetectorMaskFileHandler extends AbstractHandler {

	private static final Logger logger = LoggerFactory.getLogger(DetectorMaskFileHandler.class);
	private AbstractDataset mask;

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {

		final ISelection selection = HandlerUtil.getCurrentSelection(event);

		if (selection instanceof IStructuredSelection) {
			if (((IStructuredSelection)selection).toList().size() == 1 && (((IStructuredSelection)selection).getFirstElement() instanceof IFile)) {

				final Object sel = ((IStructuredSelection)selection).getFirstElement();
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				Shell shell = window.getShell();
				try {

					int idxSaxs = NcdDataReductionParameters.getDetListSaxs().getSelectionIndex();
					if (idxSaxs >= 0) {
						String detectorSaxs = NcdDataReductionParameters.getDetListSaxs().getItem(idxSaxs);
						INexusTree detectorTree = NexusTreeBuilder.getNexusTree(((IFile)sel).getLocation().toString(), getDetectorSelection(detectorSaxs));
						INexusTree node = detectorTree.getNode("entry1/"+detectorSaxs+"_processing/SectorIntegration/mask");
						if (node == null) {
							String msg = "No mask data found in "+((IFile)sel).getLocation().toString();
							return DetectorMaskErrorDialog(shell, msg, null);
						}
						
						mask = Nexus.createDataset(node.getData(), false);
						try {
							IWorkbenchPage page = window.getActivePage();
							IViewPart activePlot = page.findView(PlotView.ID + "DP");
							if (activePlot instanceof PlotView)
								if (mask != null) {
									MaskingBean mb = new MaskingBean((BooleanDataset) DatasetUtils.cast(mask, AbstractDataset.BOOL), 0, 1);
									((PlotView)page.showView(PlotView.ID + "DP")).putGUIInfo(GuiParameters.MASKING, mb);
								}
						} catch (PartInitException e) {
							String msg = "SCISOFT NCD: cannot set masking GUI bean information";
							return DetectorMaskErrorDialog(shell, msg, e);
						}
						return Status.OK_STATUS;
					} 
					String msg = "SCISOFT NCD: No SAXS detector is selected";
					return DetectorMaskErrorDialog(shell, msg, null);

				} catch (Exception e) {
					String msg = "SCISOFT NCD: Failed to read detector mask from "+((IFile)sel).getLocation().toString();
					return DetectorMaskErrorDialog(shell, msg, e);
				}
			}

		}

		return null;
	}

	private NexusTreeNodeSelection getDetectorSelection(String detName) throws Exception {
		String xml = "<?xml version='1.0' encoding='UTF-8'?>" +
		"<nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>NXentry</nxClass><wanted>1</wanted><dataType>1</dataType>" +
		"<nexusTreeNodeSelection><nxClass>NXinstrument</nxClass><name>"+ detName + "_processing</name><wanted>1</wanted><dataType>1</dataType>" +
		"<nexusTreeNodeSelection><nxClass>NXdetector</nxClass><name>SectorIntegration</name><wanted>1</wanted><dataType>1</dataType>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>mask</name><wanted>1</wanted><dataType>2</dataType>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>";
		return NexusTreeNodeSelection.createFromXML(new InputSource(new StringReader(xml)));
	}
	
	private IStatus DetectorMaskErrorDialog(Shell shell, String msg, Exception e) {
		logger.error(msg, e);
		Status status = new Status(IStatus.ERROR, NcdPerspective.PLUGIN_ID, msg, e);
		ErrorDialog.openError(shell, "Mask loading error", "Error loading detector mask", status);
		return Status.CANCEL_STATUS;
	}
}
