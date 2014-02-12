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

package uk.ac.diamond.scisoft.ncd.core.service;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

/**
 * This service is designed to run data reduction processing from
 * either the UI or from a headless java executable. The service
 * allows the algorithm in LazyNcdProcessing to be run in headless mode
 * by receiving an XML file which normally populates source providers. These
 * source providers are already available if the service is run in the 
 * UI because the NCD UI parts contribute them.
 * 
 * The service requires the following information:
 * 1. The XML file (this can be generated from source providers which are linked to the GUI).
 * 2. The mask and region, contributed via the persistence service or from the UI.
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
     service.execute(rawFilePath, context, new NullProgressMonitor());
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
	 * @param monitor - may not be null, use NullProgressMonitor if not calling with thread.
	 * @return if file was processed
	 */
	public IStatus process(String filePath, IDataReductionContext context, IProgressMonitor monitor) throws Exception;
}
