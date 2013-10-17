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

import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.math3.util.Pair;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IndexIterator;

public enum SaxsAnalysisPlotType {


	LOGLOG_PLOT("Log/Log Plot",  new Pair<String, String>("log\u2081\u2080(q)", "log\u2081\u2080(I)"), new int[]{0,0,255}),
	GUINIER_PLOT("Guinier Plot", new Pair<String, String>("q\u00b2", "ln(I)"), new int[]{255,0,0}),
	POROD_PLOT("Porod Plot",     new Pair<String, String>("q", "Iq\u2074"),    new int[]{204, 0, 204}),
	KRATKY_PLOT("Kratky Plot",   new Pair<String, String>("q", "Iq\u00b2"),    new int[]{204, 0, 0}),
	ZIMM_PLOT("Zimm Plot",       new Pair<String, String>("q\u00b2", "1/I"),   new int[]{0, 153, 51}),
	DEBYE_BUECHE_PLOT("Debye-Bueche Plot",  new Pair<String, String>("q\u00b2", "1/\u221AI"), new int[]{102, 0, 102});
	
	
	private final String name;
	private final Pair<String, String> axisNames;
	private final int[]  rgb;

	SaxsAnalysisPlotType(String name, Pair<String, String> axisNames, int[] rgb) {
		this.name      = name;
		this.axisNames = axisNames;
		this.rgb       = rgb;
	}

	public String getName() {
		return name;
	}

	public Pair<String, String> getAxisNames() {
		return axisNames;
	}

	public static SaxsAnalysisPlotType forName(String pt) {
		for (SaxsAnalysisPlotType plotType : values()) {
			if (plotType.getName().equals(pt)) return plotType;
		}
		return null;
	}
	
	/**
	 * Process the maths in place for the passed in arguments.
	 * @param xTraceData
	 * @param yTraceData
	 */
	public void process(AbstractDataset xTraceData, AbstractDataset yTraceData) {
		process(xTraceData, yTraceData, this);
	}

	/**
	 * We keep the maths separate from the UI. This is a good idea!
	 * @param xTraceData
	 * @param yTraceData
	 * @param plotType
	 */
	private static void process(AbstractDataset xTraceData, AbstractDataset yTraceData, SaxsAnalysisPlotType plotType) {
		
		if (plotType.equals(SaxsAnalysisPlotType.LOGLOG_PLOT)) {
			IndexIterator itr = yTraceData.getIterator();
			while (itr.hasNext()) {
				int idx = itr.index;
				yTraceData.set(Math.log10(yTraceData.getDouble(idx)), idx);
			}
			itr = xTraceData.getIterator();
			while (itr.hasNext()) {
				int idx = itr.index;
				xTraceData.set(Math.log10(xTraceData.getDouble(idx)), idx);
			}
		} else if (plotType.equals(SaxsAnalysisPlotType.GUINIER_PLOT)) {
			IndexIterator itr = yTraceData.getIterator();
			while (itr.hasNext()) {
				int idx = itr.index;
				yTraceData.set(Math.log(yTraceData.getDouble(idx)), idx);
			}
			xTraceData.ipower(2);
			
		} else if (plotType.equals(SaxsAnalysisPlotType.POROD_PLOT)) {
			IndexIterator itr = yTraceData.getIterator();
			while (itr.hasNext()) {
				int idx = itr.index;
				yTraceData.set(Math.pow(xTraceData.getDouble(idx), 4) * yTraceData.getDouble(idx), idx);
			}
			
		} else if (plotType.equals(SaxsAnalysisPlotType.KRATKY_PLOT)) {
			IndexIterator itr = yTraceData.getIterator();
			while (itr.hasNext()) {
				int idx = itr.index;
				yTraceData.set(Math.pow(xTraceData.getDouble(idx), 2) * yTraceData.getDouble(idx), idx);
			}
			
		} else if (plotType.equals(SaxsAnalysisPlotType.ZIMM_PLOT)) {
			yTraceData.ipower(-1);
			xTraceData.ipower(2);
			
		} else if (plotType.equals(SaxsAnalysisPlotType.DEBYE_BUECHE_PLOT)) {
			yTraceData.ipower(-0.5);
			xTraceData.ipower(2);
			
		}
		xTraceData.setErrorBuffer(null);
		yTraceData.setErrorBuffer(null);

	}

	/**
	 * Gets a regex which matches any of the names
	 * @return regex string
	 */
	public static String getRegex() {
		final StringBuilder buf = new StringBuilder();
		buf.append("(");
		for (Iterator<SaxsAnalysisPlotType> it = Arrays.asList(values()).iterator(); it.hasNext();) {
			SaxsAnalysisPlotType type = it.next();

			// Escape everything
			buf.append("\\Q");
			buf.append(type.getName());
			buf.append("\\E");
			// Add Or
			if (it.hasNext()) buf.append("|");
		}
		buf.append(")");
		return buf.toString();
	}

	public int[] getRgb() {
		return rgb;
	}
	
}
