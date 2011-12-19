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

import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;

import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public class WaxsDataReductionHandler extends AbstractHandler implements
		IElementUpdater {

	public static String COMMAND_ID = NcdProcessingSourceProvider.WAXS_STATE;
	public static String STATE_ID = "uk.ac.diamond.scisoft.ncd.rcp.waxsdatareduction.state";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		ICommandService service = (ICommandService) PlatformUI.getWorkbench().getService(ICommandService.class);
		Command command = service.getCommand(COMMAND_ID);
	    Boolean oldValue = (Boolean)command.getState(STATE_ID).getValue();
	    command.getState(STATE_ID).setValue(!oldValue);
	    service.refreshElements(event.getCommand().getId(), null);
	    return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void updateElement(UIElement element, Map parameters) {
		
		ICommandService service = (ICommandService) PlatformUI.getWorkbench().getService(ICommandService.class);
		Command command = service.getCommand(COMMAND_ID);
	    Boolean isSelected = (Boolean)command.getState(STATE_ID).getValue();
		element.setChecked(isSelected);
	}
}
