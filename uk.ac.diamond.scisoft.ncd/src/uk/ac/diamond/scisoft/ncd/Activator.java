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

package uk.ac.diamond.scisoft.ncd;

import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import uk.ac.diamond.scisoft.ncd.reduction.service.DataReductionServiceImpl;
import uk.ac.diamond.scisoft.ncd.reduction.service.IDataReductionService;

public class Activator implements BundleActivator {

	private static BundleContext context;

	@Override
	public void start(BundleContext bundleContext) throws Exception {
		System.out.println("Starting "+bundleContext.getBundle().getSymbolicName());
		Hashtable<String, String> props = new Hashtable<String, String>(1);
		props.put("description", "A service used to convert hdf5 files");
		bundleContext.registerService(IDataReductionService.class, new DataReductionServiceImpl(), props);
		Activator.context = bundleContext;

	}

	@Override
	public void stop(BundleContext context) throws Exception {

	}

}
