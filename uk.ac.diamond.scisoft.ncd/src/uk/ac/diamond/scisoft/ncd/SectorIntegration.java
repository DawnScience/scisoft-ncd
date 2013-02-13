/*
 * Copyright 2011 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Maths;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;

public class SectorIntegration {
	
	private static final Logger logger = LoggerFactory.getLogger(SectorIntegration.class);

	private AbstractDataset[] areaData;
	private SectorROI roi;
	private boolean calculateRadial = true;
	private boolean calculateAzimuthal = true;
	private boolean fast = true;
	
	public void setROI(SectorROI ds) {
		roi = ds;
	}

	public SectorROI getROI() {
		return roi;
	}

	public void setAreaData(AbstractDataset... area) {
		this.areaData = new AbstractDataset[2];
		this.areaData[0] = area[0];
		this.areaData[1] = area[1];
	}

	
	public void setCalculateRadial(boolean calulcateRadial) {
		this.calculateRadial = calulcateRadial;
	}

	public void setCalculateAzimuthal(boolean calculateAzimuthal) {
		this.calculateAzimuthal = calculateAzimuthal;
	}

	public void setFast(boolean fast) {
		this.fast = fast;
	}

	public AbstractDataset[] process(final AbstractDataset parentdata, int frames, AbstractDataset maskUsed) {
		int[] parentdim = parentdata.getShape();
		int[] start = parentdim.clone();
		for (int i = 1; i < start.length; i++) {
			start[i] = 0;
		}
		int[] stop = parentdim.clone();

		AbstractDataset myraddata = null, myazdata = null;
		for (int i = 0; i < frames; i++) {
			start[0] = i;
			stop[0] = i + 1;
			AbstractDataset slice = parentdata.getSlice(start, stop, null);
			slice.squeeze();
			AbstractDataset[] intresult;
			try {
				intresult = ROIProfile.sector(slice, maskUsed, roi, calculateRadial, calculateAzimuthal, fast);
			} catch (IllegalArgumentException ill) {
				logger.warn("mask and dataset incompatible rank", ill);
				maskUsed = null;
				intresult = ROIProfile.sector(slice, maskUsed, roi, calculateRadial, calculateAzimuthal, fast);
			}
			if (calculateRadial) {
				AbstractDataset radset = intresult[0];
				if (areaData !=null && areaData[0] != null) {
					radset = Maths.dividez(radset, areaData[0]);
				}
				int radrange = radset.getShape()[0];
				radset.resize(new int[] { 1, radrange });
				if (myraddata  == null) {
					myraddata = AbstractDataset.zeros(new int[] { frames, radrange }, radset.getDtype());
				}
				myraddata.setSlice(radset, new int[] { i, 0 }, new int[] { i + 1, radrange }, null);
			}
			if (calculateAzimuthal) {
				AbstractDataset azset = intresult[1];
				if (areaData !=null && areaData[1] != null) {
					azset = Maths.dividez(azset, areaData[1]);
				}
				int azrange = azset.getShape()[0];
				azset.resize(new int[] { 1, azrange });
				if (myazdata == null) {
					myazdata = AbstractDataset.zeros(new int[] { frames, azrange }, azset.getDtype());
				}
				myazdata.setSlice(azset, new int[] { i, 0 }, new int[] { i + 1, azrange }, null);
			}
		}
		
		return new AbstractDataset[] {myazdata, myraddata}; 
	}
}
