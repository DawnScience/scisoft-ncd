/*-
 * Copyright © 2011 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.dawb.common.ui.util.EclipseUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.XMLMemento;

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
				XMLMemento memento = XMLMemento.createReadRoot(reader);
				((NcdDataReductionParameters)part).restoreState(memento);
				
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
