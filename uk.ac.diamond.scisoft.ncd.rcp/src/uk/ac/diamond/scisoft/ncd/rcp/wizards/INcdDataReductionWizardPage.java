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
