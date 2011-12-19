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

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;

import org.nexusformat.NexusFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.SectorIntegration;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;

public class HDF5SectorIntegration extends HDF5ReductionDetector {

	private static final Logger logger = LoggerFactory.getLogger(HDF5SectorIntegration.class);

	private SectorROI roi;
	private Double gradient, intercept, cameraLength;

	public double getCameraLength() {
		return cameraLength.doubleValue();
	}

	public void setCameraLength(double cameraLength) {
		this.cameraLength = new Double(cameraLength);
	}

	public double[] getCalibrationData() {
		if (gradient != null && intercept != null)
			return new double[] {gradient.doubleValue(), intercept.doubleValue()};
		return null;
	}

	public void setCalibrationData(double gradient, double intercept) {
		this.gradient = new Double(gradient);
		this.intercept =  new Double(intercept);
	}

	@SuppressWarnings("hiding")
	private AbstractDataset mask;
	
	public HDF5SectorIntegration(String name, String key) {
		super(name, key);
	}

	public void setMask(IDataset mask) {
		this.mask = DatasetUtils.convertToAbstractDataset(mask);
	}

	@Override
	public AbstractDataset getMask() {
		return mask;
	}

	public void setROI(SectorROI ds) {
		roi = ds;
	}

	public SectorROI getROI() {
		return roi;
	}

	@Override
	public void writeout(int frames, INexusTree nxdata) {
		if (roi == null) {
			return;
		}

		AbstractDataset maskUsed = mask;

		roi.setClippingCompensation(true);

		try {
			AbstractDataset myazdata = null, myraddata = null;

			NexusGroupData parentngd = NcdDataUtils.getData(nxdata, key, "data", NexusExtractor.SDSClassName);
			AbstractDataset parentdata = Nexus.createDataset(parentngd, false);

			SectorIntegration sec = new SectorIntegration();
			sec.setROI(roi);
			AbstractDataset[] mydata = sec.process(parentdata, frames, maskUsed, myazdata, myraddata);
			myazdata = mydata[0];
			myraddata = mydata[1];

			NexusGroupData myazngd = Nexus.createNexusGroupData(myazdata);
			myazngd.isDetectorEntryData = true;
			NcdDataUtils.addData(nxdata, getName(), "azimuth", myazngd, "1",  null);

			NexusGroupData myradngd = Nexus.createNexusGroupData(myraddata);
			myradngd.isDetectorEntryData = true;
			NcdDataUtils.addData(nxdata, getName(), "data", myradngd, "1", 1);

			NexusGroupData roiData = new NexusGroupData(new int[] { 2 }, NexusFile.NX_FLOAT64, roi.getPoint());
			roiData.isDetectorEntryData = false;
			NcdDataUtils.addData(nxdata, getName(), "beam centre", roiData, "pixels", 0);
			roiData = new NexusGroupData(new int[] { 2 }, NexusFile.NX_FLOAT64, roi.getAnglesDegrees());
			roiData.isDetectorEntryData = false;
			NcdDataUtils.addData(nxdata, getName(), "integration angles", roiData, "Deg", 0);
			roiData = new NexusGroupData(new int[] { 2 }, NexusFile.NX_FLOAT64, roi.getRadii());
			roiData.isDetectorEntryData = false;
			NcdDataUtils.addData(nxdata, getName(), "integration radii", roiData, "pixels", 0);
			NcdDataUtils.addData(nxdata, getName(), "integration symmetry", new NexusGroupData(roi.getSymmetryText()), null, 0);
			if (gradient != null && intercept != null) {
				double[] calibrationValues =  new double[] {gradient.doubleValue(), intercept.doubleValue()};
				NexusGroupData calibrationData = new NexusGroupData(new int[] { 2 }, NexusFile.NX_FLOAT64, calibrationValues);
				calibrationData.isDetectorEntryData = false;
				NcdDataUtils.addData(nxdata, getName(), "qaxis calibration", calibrationData, null, 0);
			}
			if (cameraLength != null) {
				NexusGroupData cameraData = new NexusGroupData(new int[] { 1 }, NexusFile.NX_FLOAT64, new double[] {cameraLength.doubleValue()});
				cameraData.isDetectorEntryData = false;
				NcdDataUtils.addData(nxdata, getName(), "camera length", cameraData, "mm", 0);
			}
			if (maskUsed != null) {
				NcdDataUtils.addData(nxdata, getName(), "mask", Nexus.createNexusGroupData(maskUsed), "pixel", 0);
			}
			addQAxis(nxdata, parentngd.dimensions.length - 1);

			addMetadata(nxdata);
		} catch (Exception e) {
			logger.error("exception caught reducing data", e);
		}
	}

}
