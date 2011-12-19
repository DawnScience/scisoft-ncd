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

package uk.ac.diamond.scisoft.ncd.hdf5;

import java.util.Arrays;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;

import org.nexusformat.NexusFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.ncd.Average;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;

public class HDF5Average extends HDF5ReductionDetector {

	private static final Logger logger = LoggerFactory.getLogger(HDF5Average.class);

	public HDF5Average(String name, String key) {
		super(name, key);
	}

	@Override
	public void writeout(int frames, INexusTree nxdata) {
		try {
			NexusGroupData parentngd = NcdDataUtils.getData(nxdata, key, "data", NexusExtractor.SDSClassName);
			
			Average average = new Average();
			
			float[] mydata = average.process(parentngd.getBuffer(), parentngd.dimensions);
			
			int[] imagedim = Arrays.copyOfRange(parentngd.dimensions, 1, parentngd.dimensions.length);
			NexusGroupData myngd = new NexusGroupData(imagedim, NexusFile.NX_FLOAT32, mydata);
			myngd.isDetectorEntryData = true;
			NcdDataUtils.addData(nxdata, getName(), "data", myngd, "1", 1);
			addQAxis(nxdata, parentngd.dimensions.length - 1);

			addMetadata(nxdata);
		} catch (Exception e) {
			logger.error("exception caugth reducing data", e);
		}
	}
	

}
