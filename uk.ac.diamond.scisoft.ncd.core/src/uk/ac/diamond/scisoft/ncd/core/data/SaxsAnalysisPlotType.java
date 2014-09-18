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

package uk.ac.diamond.scisoft.ncd.core.data;

import java.util.Arrays;
import java.util.Iterator;

import org.apache.commons.math3.util.Pair;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IndexIterator;

import uk.ac.diamond.scisoft.ncd.core.data.plots.DebyeBuechePlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.GuinierPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.KratkyPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.LogNormPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.LogLogPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.PorodPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.SaxsPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.plots.ZimmPlotData;

public enum SaxsAnalysisPlotType {


	LOGNORM_PLOT("Log/Norm Plot",           new Pair<String, String>("q", "log\u2081\u2080(I)"),                  new int[]{0,0,255}),
	LOGLOG_PLOT("Log/Log Plot",             new Pair<String, String>("log\u2081\u2080(q)", "log\u2081\u2080(I)"), new int[]{0,0,255}),
	GUINIER_PLOT("Guinier Plot",            new Pair<String, String>("q\u00b2", "ln(I)"),                         new int[]{255,0,0}),
	POROD_PLOT("Porod Plot",                new Pair<String, String>("q", "Iq\u2074"),                            new int[]{204, 0, 204}),
	KRATKY_PLOT("Kratky Plot",              new Pair<String, String>("q", "Iq\u00b2"),                            new int[]{204, 0, 0}),
	ZIMM_PLOT("Zimm Plot",                  new Pair<String, String>("q\u00b2", "1/I"),                           new int[]{0, 153, 51}),
	DEBYE_BUECHE_PLOT("Debye-Bueche Plot",  new Pair<String, String>("q\u00b2", "1/\u221AI"),                     new int[]{102, 0, 102});
	
	
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

	public String getGroupName() {
		return name.replaceAll("[\\W ]", "");
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
	 * We keep the maths separate from the UI. This is a good idea!
	 * @param xTraceDataSrc
	 * @param yTraceDataSrc
	 */
	public IDataset[] process(Dataset xTraceDataSrc, Dataset yTraceDataSrc) {
		
    	SaxsPlotData plotData = getSaxsPlotDataObject();
    	
    	Dataset yTraceData = yTraceDataSrc.clone();
		Dataset xTraceData = xTraceDataSrc.clone();
    	
		IndexIterator itr = yTraceData.getIterator();		
		while (itr.hasNext()) {
			int idx = itr.index;
			double val = plotData.getDataValue(idx, xTraceData, yTraceData);
			if (yTraceData.hasErrors()) {
				double err = plotData.getDataError(idx, xTraceData, yTraceData);
				((DoubleDataset)(yTraceData.getErrorBuffer())).set(err*err, idx);
			}
			yTraceData.set(val, idx);
		}

		itr = xTraceData.getIterator();
		while (itr.hasNext()) {
			int idx = itr.index;
			double val = plotData.getAxisValue(idx, xTraceData);
			if (xTraceData.hasErrors()) {
				double err = plotData.getAxisError(idx, xTraceData);
				((DoubleDataset)(xTraceData.getErrorBuffer())).set(err*err, idx);
			}
			xTraceData.set(val, idx);
		}
		return new IDataset[]{xTraceData, yTraceData};
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

	public SaxsPlotData getSaxsPlotDataObject() {
    	SaxsPlotData plotData = null;
		if (this.equals(SaxsAnalysisPlotType.LOGNORM_PLOT)) {
			plotData = new LogNormPlotData();
		} else if (this.equals(SaxsAnalysisPlotType.LOGLOG_PLOT)) {  
			plotData = new LogLogPlotData();
		} else if (this.equals(SaxsAnalysisPlotType.GUINIER_PLOT)) {
			plotData = new GuinierPlotData();
		} else if (this.equals(SaxsAnalysisPlotType.POROD_PLOT)) {
			plotData = new PorodPlotData();
		} else if (this.equals(SaxsAnalysisPlotType.KRATKY_PLOT)) {
			plotData = new KratkyPlotData();
		} else if (this.equals(SaxsAnalysisPlotType.ZIMM_PLOT)) {
			plotData = new ZimmPlotData();
		} else if (this.equals(SaxsAnalysisPlotType.DEBYE_BUECHE_PLOT)) {
			plotData = new DebyeBuechePlotData();
		}
		return plotData;
	}
	
}
