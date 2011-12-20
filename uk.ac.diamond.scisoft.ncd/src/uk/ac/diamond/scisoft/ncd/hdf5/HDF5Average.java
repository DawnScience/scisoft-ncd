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
