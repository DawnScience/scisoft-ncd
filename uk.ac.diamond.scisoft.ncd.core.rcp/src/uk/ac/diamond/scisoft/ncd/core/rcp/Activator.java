/*
 * Copyright 2014 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.core.rcp;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

public class Activator extends AbstractUIPlugin {

	private static Activator plugin;
	private static BundleContext context;

	public static final String PLUGIN_ID = FrameworkUtil.getBundle(Activator.class).getSymbolicName();

	@Override
	public void start(BundleContext c) throws Exception {
		super.start(c);
		plugin = this;
		context = c;
	}
	
	@Override 
	public void stop(BundleContext c) throws Exception {
		plugin = null;
		super.stop(c);
	}

	public static Activator getDefault() {
		return plugin;
	}

	public static Object getService(final Class<?> serviceClass) {
		if (context == null) return null;
		ServiceReference<?> ref = context.getServiceReference(serviceClass);
		if (ref==null) return null;
		return context.getService(ref);
	}
	
}
