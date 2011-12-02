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

package uk.ac.diamond.scisoft.ncd.hdf5;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;

import org.nexusformat.NexusFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.ncd.Invariant;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;

/**
 * calculates the total intensity in each frame
 */
public class HDF5Invariant extends HDF5ReductionDetector {

	private static final Logger logger = LoggerFactory.getLogger(HDF5Invariant.class);

	public HDF5Invariant(String name, String key) {
		super(name, key);
	}
	
	@Override
	public void setqAxis(IDataset qAxis) {
		// Ignore qAxis setting for Invariant subdetector
		this.qAxis = null;
	}

	@Override
	public AbstractDataset getqAxis() {
		return null;
	}


	@Override
	public void writeout(int frames, INexusTree nxdata) {
		try {
			NexusGroupData parentngd = NcdDataUtils.getData(nxdata, key, "data", NexusExtractor.SDSClassName);
			if (parentngd == null) return;
			Invariant inv = new Invariant();
			float[] mydata = inv.process(parentngd.getBuffer(), parentngd.dimensions);
			NexusGroupData myngd = new NexusGroupData(new int[] {parentngd.dimensions[0]}, NexusFile.NX_FLOAT32, mydata);
			myngd.isDetectorEntryData = true;
			NcdDataUtils.addData(nxdata, getName(), "data", myngd, "1", 1);
			addMetadata(nxdata);
		} catch (Exception e) {
			logger.error("exception caugth reducing data", e);
		}
	}
	
}