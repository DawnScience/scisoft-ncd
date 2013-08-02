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

public class InvariantHandler extends AbstractHandler implements
		IElementUpdater {
	
	public static final String COMMAND_ID = NcdProcessingSourceProvider.INVARIANT_STATE;
	public static final String STATE_ID = "uk.ac.diamond.scisoft.ncd.rcp.invariant.state";

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
