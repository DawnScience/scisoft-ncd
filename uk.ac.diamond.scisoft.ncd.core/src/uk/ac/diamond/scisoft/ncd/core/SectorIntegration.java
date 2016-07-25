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

package uk.ac.diamond.scisoft.ncd.core;

import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.Maths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;

public class SectorIntegration {
	
	private static final Logger logger = LoggerFactory.getLogger(SectorIntegration.class);

	private Dataset[] areaData, areaDataSq;
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

	public void setAreaData(Dataset... area) {
		this.areaData = new Dataset[2];
		this.areaDataSq = new Dataset[2];
		this.areaData[0] = area[0].getSlice(null, null, null);
		this.areaData[1] = area[1].getSlice(null, null, null);
		this.areaDataSq[0] = area[0].getSlice(null, null, null).ipower(2);
		this.areaDataSq[1] = area[1].getSlice(null, null, null).ipower(2);
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

	public Dataset[] process(final Dataset parentdata, int frames, Dataset maskUsed) {
		int[] parentdim = parentdata.getShape();
		int[] start = parentdim.clone();
		for (int i = 1; i < start.length; i++) {
			start[i] = 0;
		}
		int[] stop = parentdim.clone();
		
		boolean doErrors = parentdata.hasErrors(); 

		Dataset myraddata = null, myazdata = null;
		DoubleDataset myraderrors = null, myazerrors = null;
		for (int i = 0; i < frames; i++) {
			start[0] = i;
			stop[0] = i + 1;
			
			Dataset slice = parentdata.getSlice(start, stop, null);
			slice.squeeze();
			
			Dataset[] intresult;
			try {
				intresult = ROIProfile.sector(slice, maskUsed, roi, calculateRadial, calculateAzimuthal, fast, null, null, doErrors);
			} catch (IllegalArgumentException ill) {
				logger.warn("mask and dataset incompatible rank", ill);
				maskUsed = null;
				intresult = ROIProfile.sector(slice, maskUsed, roi, calculateRadial, calculateAzimuthal, fast, null, null, doErrors);
			}
			
			if (calculateRadial) {
				Dataset radset = intresult[0];
				if (areaData != null && areaData[0] != null) {
					radset = Maths.dividez(radset, areaData[0]);
				}
				int radrange = radset.getShape()[0];
				radset.resize(new int[] { 1, radrange });
				if (myraddata  == null) {
					myraddata  = DatasetFactory.zeros(new int[] { frames, radrange }, radset.getDType());
				}
				myraddata.setSlice(radset, new int[] { i, 0 }, new int[] { i + 1, radrange }, null);
				
				if (doErrors) {
					Dataset raderr = intresult[0].getErrorBuffer();
					if (areaData != null && areaData[0] != null) {
						raderr = Maths.dividez(raderr, areaDataSq[0]);
					}
					radset.resize(new int[] { 1, radrange });
					if (myraderrors == null) {
						myraderrors = (DoubleDataset) DatasetFactory.zeros(myraddata, Dataset.FLOAT64);
					}
					myraderrors.setSlice(raderr, new int[] { i, 0 }, new int[] { i + 1, radrange }, null);
				}
			}
			
			if (calculateAzimuthal) {
				Dataset azset = intresult[1];
				if (areaData !=null && areaData[1] != null) {
					azset = Maths.dividez(azset, areaData[1]);
				}
				int azrange = azset.getShape()[0];
				azset.resize(new int[] { 1, azrange });
				if (myazdata == null) {
					myazdata   = DatasetFactory.zeros(new int[] { frames, azrange }, azset.getDType());
				}
				myazdata.setSlice(azset, new int[] { i, 0 }, new int[] { i + 1, azrange }, null);
				if (doErrors) {
					Dataset azerr = intresult[1].getErrorBuffer();
					if (areaData !=null && areaData[1] != null) {
						azerr = Maths.dividez(azerr, areaDataSq[1]);
					}
					if (myazerrors == null) {
						myazerrors = (DoubleDataset) DatasetFactory.zeros(myazdata, Dataset.FLOAT64);
					}
					myazerrors.setSlice(azerr, new int[] { i, 0 }, new int[] { i + 1, azrange }, null);
				}
			}
		}
		if (myraddata != null && myraderrors != null) {
			myraddata.setErrorBuffer(myraderrors);
		}
		if (myazdata != null && myazerrors != null) {
			myazdata.setErrorBuffer(myazerrors);
		}
		return new Dataset[] {myazdata, myraddata}; 
	}
}
