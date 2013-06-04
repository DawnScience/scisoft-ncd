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
	
	public static String NO_SAXS_DETECTOR, NO_WAXS_DETECTOR;
	public static String NO_SAXS_PIXEL, NO_WAXS_PIXEL;
	public static String NO_SAXS_DIM, NO_WAXS_DIM;
	public static String NO_BG_FILE, NO_BG_READ, NO_BG_DATA;
	public static String NO_DR_FILE, NO_DR_READ, NO_DR_DATA;
	public static String NO_WORKING_DIR, NO_WORKINGDIR_WRITE, NO_WORKINGDIR_DATA;
	public static String NO_IMAGE_DATA, NO_IMAGE_PLOT, NO_MASK_IMAGE, NO_MASK_DATA, NO_QAXIS_DATA;
	public static String NO_SEC_DATA, NO_SEC_INT, NO_SEC_SYM, NO_SEC_SUPPORT;
	public static String NO_ENERGY_DATA;

	 static {
		 NLS.initializeMessages(BUNDLE_NAME, NcdMessages.class);
	 }
}
