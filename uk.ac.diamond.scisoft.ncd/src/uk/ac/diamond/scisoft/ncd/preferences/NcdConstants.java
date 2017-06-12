/*
 * Copyright 2012, 2017 Diamond Light Source Ltd.
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

import javax.measure.quantity.Length;

import tec.units.ri.unit.Units;
import tec.units.ri.unit.MetricPrefix;

import javax.measure.Unit;

public class NcdConstants {
	
	public static String[] dimChoices = new String[] { "1D", "2D" };
	public static Unit<Length> DEFAULT_UNIT = MetricPrefix.NANO(Units.METRE);
}
