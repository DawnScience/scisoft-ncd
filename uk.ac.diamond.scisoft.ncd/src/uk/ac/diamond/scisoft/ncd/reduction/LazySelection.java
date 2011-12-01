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

package uk.ac.diamond.scisoft.ncd.reduction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CancellationException;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.tree.INexusTree;

public class LazySelection extends LazyDataReduction {

	public static String name = "Selection";
	private String format;
	
	public LazySelection(String activeDataset, int[] frames, int frameBatch, NexusFile nxsFile) {
		super(activeDataset, frames, frameBatch, nxsFile);
	}

	@Override
	public void execute(INexusTree tmpNXdata, int dim, IProgressMonitor monitor) throws Exception {
		
		int[] datDimMake = Arrays.copyOfRange(frames, 0, frames.length-dim);
		ArrayList<int[]> list = NcdDataUtils.createSliceList(format, datDimMake);
		ArrayList<int[]> comb = NcdDataUtils.generateCombinations(list);
		for (int i = 0; i < datDimMake.length; i++)
			datDimMake[i] = list.get(i).length;
		
		for (int n = 0; n < comb.size(); n++) {
			if (monitor.isCanceled()) {
				throw new CancellationException("Data reduction cancelled");
			}			
			int[] gridFrame = comb.get(n);
			
				int[] start = new int[gridFrame.length + dim];
				int[] stop = new int[gridFrame.length + dim];
				start = Arrays.copyOf(gridFrame, start.length);
				stop = Arrays.copyOf(gridFrame, stop.length);
				for (int k = 0; k < gridFrame.length; k++)
					stop[k]++;
				
				for (int k = (frames.length-1); k >= gridFrame.length; k--) {
					start[k] = 0;
					stop[k] = frames[k];
				}
				
				INexusTree tmpData = NcdDataUtils.selectNAxisFrames(activeDataset, calibration, tmpNXdata, dim + 1, start, stop);
				
				int[] datDimStartPrefix = new int[gridFrame.length];
				for (int k = 0; k < datDimStartPrefix.length; k++)
					datDimStartPrefix[k] = ArrayUtils.indexOf(list.get(k), gridFrame[k]);
				
				int[] datDimPrefix = new int[gridFrame.length - 1];
				Arrays.fill(datDimPrefix, 1);
				
				if (n==0) {
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(tmpData, activeDataset), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, dim);
					if (calibration != null)
						NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(tmpData, calibration), true, false, null, datDimPrefix, datDimStartPrefix, datDimMake, 1);
				}
				else {
					nxsFile.opengroup(activeDataset, NexusExtractor.NXDetectorClassName);
					NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(tmpData, activeDataset).getNode("data"),true, false, null, datDimPrefix,  datDimStartPrefix, datDimMake, dim);
					nxsFile.closegroup();
					if (calibration != null) {
						nxsFile.opengroup(calibration, NexusExtractor.NXDetectorClassName);
						NcdNexusUtils.writeNcdData(nxsFile, NcdDataUtils.getDetTree(tmpData, calibration).getNode("data"),true, false, null, datDimPrefix,  datDimStartPrefix, datDimMake, 1);
						nxsFile.closegroup();
					}
				}
				nxsFile.flush();
		}
	}

	public void setFormat(String format) {
		this.format = format;
	}

}
