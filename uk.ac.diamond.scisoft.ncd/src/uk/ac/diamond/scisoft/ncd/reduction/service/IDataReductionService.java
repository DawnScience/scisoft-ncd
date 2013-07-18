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

package uk.ac.diamond.scisoft.ncd.reduction.service;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

/**
 * This service is designed to run data reduction processing from
 * either the UI or from a headless java executable. The service
 * allows the algorithm in LazyNcdProcessing to be run in headless mode
 * by receiving an XML file file populates source providers. These
 * source providers are already available if the service is run in the 
 * UI because the NCD UI parts contribute them.
 * 
 * The service requires the following information:
 * 1. The XML file (this can be generated from source providers which are linked to the GUI).
 * 2. The mask and region, contributed via the persistence service.
 * 3. The raw nexus file contributed by file path.
 * 
 * This information is provided/configured by the IDataReductionContext
 * 
 * Usage:

 <code>
 
 IDataReductionService service = (IDataReductionService)Activator.getDefault().getService(IDataReductionService.class);
 IDataReductionContext context = service.createContext();
 context.setXXX(); // Send various properties to the context from the UI or the XML file.
 context.setMask(BooleanDataset mask);
 context.setRegion(ISectorROI region);
 
 service.configure(context); // Can throw exception if cannot configure service.
 
 // Loop over files - may be done in a job or thread.
 for(String rawFilePath : files) {
     service.execute(rawFilePath);
 }
 
 </code>
 
 */
public interface IDataReductionService {

	/**
	 * 
	 * @return IDataReductionContext for running the Data Reduction algorithm
	 */
	public IDataReductionContext createContext();
	
	/**
	 * Configure the context, checks data structures.
	 * @param context
	 * @throws Exception
	 */
	public void configure(IDataReductionContext context) throws Exception;
	
	/**
	 * Call this method to run the data reduction algorithm.
	 * 
	 * @param filePath
	 * @param context
	 * @param monitor
	 * @return if file was processed
	 */
	public IStatus execute(String filePath, IDataReductionContext context, IProgressMonitor monitor) throws Exception;
}
