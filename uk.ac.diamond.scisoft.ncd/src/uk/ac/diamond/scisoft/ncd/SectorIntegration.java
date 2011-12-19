/*
 * Copyright © 2011 Diamond Light Source Ltd.
 * Contact :  ScientificSoftware@diamond.ac.uk
 * 
 * This is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 * 
 * This software is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this software. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;

public class SectorIntegration {
	
	private static final Logger logger = LoggerFactory.getLogger(SectorIntegration.class);

	private SectorROI roi;
	
	public void setROI(SectorROI ds) {
		roi = ds;
	}

	public SectorROI getROI() {
		return roi;
	}

	
	public AbstractDataset[] process(final AbstractDataset parentdata, int frames, AbstractDataset maskUsed, AbstractDataset myazdata, AbstractDataset myraddata) {
		int[] parentdim = parentdata.getShape();
		int[] start = parentdim.clone();
		for (int i = 1; i < start.length; i++) {
			start[i] = 0;
		}
		int[] stop = parentdim.clone();

		for (int i = 0; i < frames; i++) {
			start[0] = i;
			stop[0] = i + 1;
			AbstractDataset slice = parentdata.getSlice(start, stop, null);
			slice.squeeze();
			AbstractDataset[] intresult;
			try {
				intresult = ROIProfile.sector(slice, maskUsed, roi);
			} catch (IllegalArgumentException ill) {
				logger.warn("mask and dataset incompatible rank", ill);
				maskUsed = null;
				intresult = ROIProfile.sector(slice, maskUsed, roi);
			}
			AbstractDataset radset = intresult[0];
			AbstractDataset azset = intresult[1];
			int azrange = azset.getShape()[0];
			int radrange = radset.getShape()[0];
			azset.resize(new int[] { 1, azrange });
			radset.resize(new int[] { 1, radrange });
			if (myazdata == null || myraddata == null) {
				myazdata = AbstractDataset.zeros(new int[] { frames, azrange }, azset.getDtype());
				myraddata = AbstractDataset.zeros(new int[] { frames, radrange }, radset.getDtype());
			}
			myazdata.setSlice(azset, new int[] {i, 0}, new int[] { i + 1, azrange }, null);
			myraddata.setSlice(radset, new int[] {i, 0}, new int[] { i + 1, radrange }, null);
		}
		
		return new AbstractDataset[] {myazdata, myraddata}; 
	}
}
