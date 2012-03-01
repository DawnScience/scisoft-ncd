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

package uk.ac.diamond.scisoft.ncd.hdf5;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;

import org.nexusformat.NexusFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;
import uk.ac.diamond.scisoft.ncd.BackgroundSubtraction;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;

public class HDF5BackgroundSubtraction extends HDF5ReductionDetector {

	private static final Logger logger = LoggerFactory.getLogger(HDF5BackgroundSubtraction.class);

	public AbstractDataset parentngd;
	private AbstractDataset background;

	public HDF5BackgroundSubtraction(String name, String key) {
		super(name, key);
	}

	public void setBackground(AbstractDataset ds) {
		background = ds;
	}

	public AbstractDataset getBackground() {
		return background;
	}

	@Override
	public void writeout(int frames, INexusTree nxdata) {
		if (background == null) {
			return;
		}

		try {
			NexusGroupData parentngd = NcdDataUtils.getData(nxdata, key, "data", NexusExtractor.SDSClassName);

			if (parentngd == null) {
				logger.error(getName() + ": no detector " + key + " found");
				return;
			}

			BackgroundSubtraction bs = new BackgroundSubtraction();
			bs.setBackground(background);
			float[] mydata = bs.process(parentngd.getBuffer(), parentngd.dimensions);
			NexusGroupData myngd = new NexusGroupData(parentngd.dimensions, NexusFile.NX_FLOAT32, mydata);
			myngd.isDetectorEntryData = true;
			NcdDataUtils.addData(nxdata, getName(), "data", myngd, "1", 1);
			addQAxis(nxdata, parentngd.dimensions.length);

			addMetadata(nxdata);
		} catch (Exception e) {
			logger.error("exception caugth reducing data", e);
		}
	}

	
	public AbstractDataset writeout(int dim) {
		if (background == null) {
			return null;
		}

		if (parentngd == null) {
			logger.error(getName() + ": no detector " + key + " found");
			return null;
		}

		try {
			int[] dataShape = parentngd.getShape();

			parentngd = flattenGridData(parentngd, dim);
			background = background.squeeze();

			BackgroundSubtraction bs = new BackgroundSubtraction();
			bs.setBackground(background);

			int[] flatShape = parentngd.getShape();
			float[] mydata = bs.process(parentngd.getBuffer(), flatShape);

			int filespace_id = H5.H5Dget_space(ids.dataset_id);
			int type_id = H5.H5Dget_type(ids.dataset_id);
			int memspace_id = H5.H5Screate_simple(ids.block.length, ids.block, null);
			H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, ids.start, ids.stride, ids.count,
					ids.block);
			H5.H5Dwrite(ids.dataset_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, mydata);

			return new FloatDataset(mydata, dataShape);
			
		} catch (Exception e) {
			logger.error("exception caugth reducing data", e);
		}

		return null;
	}
}
