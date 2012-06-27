/*
 * Copyright 2012 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.preferences;

import org.eclipse.osgi.util.NLS;

public class NcdMessages extends NLS {
	
	private static final String BUNDLE_NAME = "uk.ac.diamond.scisoft.ncd.preferences.NcdMessages"; //$NON-NLS-1$
	
	public static String NO_SAXS_DETECTOR;
	public static String NO_BG_DATA, NO_MASK_DATA, NO_QAXIS_DATA;

	 static {
		 NLS.initializeMessages(BUNDLE_NAME, NcdMessages.class);
	 }
}
