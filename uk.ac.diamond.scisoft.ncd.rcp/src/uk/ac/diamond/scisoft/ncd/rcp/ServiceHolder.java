/*
 * Copyright 2016 Diamond Light Source Ltd.
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
package uk.ac.diamond.scisoft.ncd.rcp;

import uk.ac.diamond.scisoft.ncd.core.service.IDataReductionService;

public class ServiceHolder {

	private static IDataReductionService rservice;

	public ServiceHolder() {
		// do nothing, OSGi might load service more than once
	}

	public static IDataReductionService getDataReductionService() {
		return rservice;
	}

	public static void setDataReductionService(IDataReductionService rservice) {
		ServiceHolder.rservice = rservice;
	}
}