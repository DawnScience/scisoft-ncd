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

package uk.ac.diamond.scisoft.ncd.reduction;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5BackgroundSubtraction;

public class LazyBackgroundSubtraction extends LazyDataReduction {

	private String bgFile;
	private String bgRoot;
	private Double bgScaling;
	private Double absScaling;

	private Integer bgFirstFrame;
	private Integer bgLastFrame;

	public static String name = "BackgroundSubtraction";

	public void setBgFile(String bgFile) {
		this.bgFile = bgFile;
	}

	public void setBgRoot(String bgRoot) {
		this.bgRoot = bgRoot;
	}

	public void setBgFirstFrame(Integer bgFirstFrame) {
		this.bgFirstFrame = bgFirstFrame;
	}

	public void setBgLastFrame(Integer bgLastFrame) {
		this.bgLastFrame = bgLastFrame;
	}

	public void setBgScale(Double bgScaling) {

		this.bgScaling = bgScaling;
	}

	public void setAbsScaling(Double absScaling) {

		this.absScaling = absScaling;

	}

	public AbstractDataset execute(int dim, AbstractDataset data, AbstractDataset bgData, DataSliceIdentifiers bg_id) {
		HDF5BackgroundSubtraction reductionStep = new HDF5BackgroundSubtraction("background", "data");
		reductionStep.parentngd = data;
		
		reductionStep.setBackground(bgData);
		reductionStep.setIDs(bg_id);
		
		return reductionStep.writeout(dim);
	}
	
	
}
