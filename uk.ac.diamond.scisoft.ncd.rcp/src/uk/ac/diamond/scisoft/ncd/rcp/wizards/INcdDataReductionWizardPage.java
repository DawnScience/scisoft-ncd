/*-
 * Copyright © 2013 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.rcp.wizards;

import org.eclipse.jface.wizard.IWizardPage;

import uk.ac.diamond.scisoft.ncd.rcp.NcdProcessingSourceProvider;

public interface INcdDataReductionWizardPage extends IWizardPage{
	/**
	 * 
	 * @return the provider, including any modifications (which includes path changes and 
	 * the setting of user objects)
	 */
	public abstract NcdProcessingSourceProvider getProvider();
	
	/**
	 * 
	 * @param provider
	 */
	public abstract void setProvider(NcdProcessingSourceProvider provider);

	/**
	 * Returns whether a page is active or not
	 * @return boolean
	 */
	public boolean isActive();

	/**
	 * Set whether a page is active or not
	 * @param isActive
	 */
	public void setActive(boolean isActive);

	/**
	 * Returns whether this is the current wizard page
	 * @return boolean
	 */
	public boolean isCurrentNcdWizardpage();
}
