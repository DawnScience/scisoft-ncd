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

import uk.ac.diamond.scisoft.ncd.Normalisation;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;

public class HDF5Normalisation extends HDF5ReductionDetector {


	private static final Logger logger = LoggerFactory.getLogger(HDF5Normalisation.class);

	private double normvalue = 1;
	private String calibName;
	private int calibChannel = 1;

	public HDF5Normalisation(String name, String key) {
		super(name, key);
	}

	public double getNormvalue() {
		return normvalue;
	}

	public void setNormvalue(double normvalue) {
		this.normvalue = normvalue;
	}

	public String getCalibName() {
		return calibName;
	}

	public void setCalibName(String calibName) {
		this.calibName = calibName;
	}

	public int getCalibChannel() {
		return calibChannel;
	}

	public void setCalibChannel(int calibChannel) {
		this.calibChannel = calibChannel;
	}

	@Override
	public void writeout(int frames, INexusTree nxdata) {
		if (calibName == null) {
			logger.error(getName()+": no calibration source set up");
			return;
		}

		try {
			NexusGroupData parentngd = NcdDataUtils.getData(nxdata, key, "data", NexusExtractor.SDSClassName);
			NexusGroupData calibngd = NcdDataUtils.getData(nxdata, calibName, "data", NexusExtractor.SDSClassName);
			//LazyDataset parentngd = (LazyDataset) nxdata.getDataset("/data").getDataset();
			//LazyDataset calibngd = (LazyDataset) nxdata.getDataset(calibName + "/data").getDataset();

			if (calibngd.dimensions.length != 2) {
				throw new IllegalArgumentException("calibration of wrong dimensionality");
			}

			Normalisation nm = new Normalisation();
			nm.setCalibChannel(calibChannel);
			nm.setNormvalue(normvalue);
			float[] mydata = nm.process(parentngd.getBuffer(), calibngd.getBuffer(), frames, parentngd.dimensions, calibngd.dimensions);
			NexusGroupData myngd = new NexusGroupData(parentngd.dimensions, NexusFile.NX_FLOAT32, mydata);
			myngd.isDetectorEntryData = true;
			NcdDataUtils.addData(nxdata, getName(), "data", myngd, "1", 1);
			addQAxis(nxdata, parentngd.dimensions.length);

			addMetadata(nxdata);
		} catch (Exception e) {
			logger.error("exception caught reducing data", e);
		}
	}
}
