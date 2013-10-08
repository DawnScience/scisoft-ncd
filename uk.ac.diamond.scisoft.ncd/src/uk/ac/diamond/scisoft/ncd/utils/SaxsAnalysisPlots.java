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

package uk.ac.diamond.scisoft.ncd.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.util.Pair;

public class SaxsAnalysisPlots {

	public static final String LOGLOG_PLOT = "Log/Log Plot";
	public static final String GUINIER_PLOT = "Guinier Plot";
	public static final String POROD_PLOT = "Porod Plot";
	public static final String KRATKY_PLOT = "Kratky Plot";
	public static final String ZIMM_PLOT = "Zimm Plot";
	public static final String DEBYE_BUECHE_PLOT = "Debye-Bueche Plot";
	
	private static Map<String,Pair<String, String>> plotAxesNames  = new HashMap<String, Pair<String,String>>();
	
	static {
		plotAxesNames.put(LOGLOG_PLOT, new Pair<String, String>("log\u2081\u2080(q)", "log\u2081\u2080(I)"));
		plotAxesNames.put(GUINIER_PLOT, new Pair<String, String>("q\u00b2", "ln(I)"));
		plotAxesNames.put(POROD_PLOT, new Pair<String, String>("q", "Iq\u2074"));
		plotAxesNames.put(KRATKY_PLOT, new Pair<String, String>("q", "Iq\u00b2"));
		plotAxesNames.put(ZIMM_PLOT, new Pair<String, String>("q\u00b2", "1/I"));
		plotAxesNames.put(DEBYE_BUECHE_PLOT, new Pair<String, String>("q\u00b2", "1/\u221AI"));
	}
	
	private SaxsAnalysisPlots() {
	}
	
	public static Pair<String, String> getSaxsPlotAxes(String plotName) {
		return plotAxesNames.get(plotName);
	}
	
}
