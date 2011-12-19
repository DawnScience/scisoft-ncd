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
import gda.data.nexus.tree.NexusTreeNode;

import java.util.HashMap;
import java.util.Map;

import org.nexusformat.NexusFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Nexus;
import uk.ac.diamond.scisoft.ncd.utils.NcdDataUtils;

public class HDF5ReductionDetector {

	private static final Logger logger = LoggerFactory.getLogger(HDF5ReductionDetector.class);
	
	private String name;
	protected String key;
	protected AbstractDataset qAxis;
	
	protected String detectorType = "REDUCTION";
	protected double pixelSize = 0.0;
	protected Map<String, Object> attributeMap = new HashMap<String, Object>();
	public static final String descriptionLabel = "description";
	protected String description;
	protected DoubleDataset mask = null;

	public HDF5ReductionDetector(String name, String key) {
		this.key = key;
		this.name = name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}
	
	public String getDetectorType() {
		return detectorType;
	}

	public int[] getDataDimensions() {
		return null;
	}
	
	@SuppressWarnings("unused")
	public void writeout(int frames, INexusTree nxdata) {
		addMetadata(nxdata);
	}
	
	public void setAttribute(String attributeName, Object value) {
		if (descriptionLabel.equals(attributeName)) {
			description = (String) value;
		} else if (value != null) {
			attributeMap.put(attributeName, value);
		} else if (attributeMap.containsKey(attributeName)) {
			attributeMap.remove(attributeName);
		}
	}

	public Object getAttribute(String attributeName) {
		if (descriptionLabel.equals(attributeName)) {
			return description;
		} else if (attributeMap.containsKey(attributeName)) {
			return attributeMap.get(attributeName);
		}
		return null;
	}


	protected void addMetadata(INexusTree nxdata) {
		NexusGroupData ngd;
		INexusTree detTree = NcdDataUtils.getDetTree(nxdata, getName());

		if (getDetectorType() != null) {
			ngd = new NexusGroupData(getDetectorType());
			ngd.isDetectorEntryData = false;

			NexusTreeNode type_node = new NexusTreeNode("sas_type", NexusExtractor.SDSClassName, null, ngd);
			type_node.setIsPointDependent(false);

			detTree.addChildNode(type_node);
		}

		if (description != null) {
			ngd = new NexusGroupData(description);
			ngd.isDetectorEntryData = false;

			NexusTreeNode type_node = new NexusTreeNode(descriptionLabel, NexusExtractor.SDSClassName, null, ngd);
			type_node.setIsPointDependent(false);

			detTree.addChildNode(type_node);
		}

		if (getPixelSize() != 0.0) {
			ngd = new NexusGroupData(new int[] { 1 }, NexusFile.NX_FLOAT64, new double[] { getPixelSize() });
			ngd.isDetectorEntryData = false;

			for (String label : new String[] { "x_pixel_size", "y_pixel_size" }) {
				NexusTreeNode type_node = new NexusTreeNode(label, NexusExtractor.SDSClassName, null, ngd);
				type_node.setIsPointDependent(false);
				type_node.addChildNode(new NexusTreeNode("units", NexusExtractor.AttrClassName, type_node,
						new NexusGroupData("m")));

				detTree.addChildNode(type_node);
			}
		}

		if (mask != null) {
			int[] devicedims = getDataDimensions();
			ngd = new NexusGroupData(new int[] { devicedims[0], devicedims[1] }, NexusFile.NX_FLOAT64, mask.getData());
			NcdDataUtils.addData(nxdata, getName() + "mask", "data", ngd, null, null);
		}
		
		for (String label : new String[] { "distance", "beam_center_x", "beam_center_y" }) {
			if (attributeMap.containsKey(label)) {
				try {
					ngd = new NexusGroupData(new int[] { 1 }, NexusFile.NX_FLOAT64,
							new double[] { (Double) attributeMap.get(label) });
					ngd.isDetectorEntryData = false;

					NexusTreeNode type_node = new NexusTreeNode(label, NexusExtractor.SDSClassName, null, ngd);
					type_node.setIsPointDependent(false);

					type_node.addChildNode(new NexusTreeNode("units", NexusExtractor.AttrClassName, type_node, label
							.equals("distance") ? new NexusGroupData("m") : null));

					detTree.addChildNode(type_node);
				} catch (Exception e) {
					logger.warn("Error writing metadata " + label + ": ", e);
				}
			}
		}
	}

	public double getPixelSize() {
		return pixelSize;
	}

	public void setPixelSize(double pixelSize) {
		this.pixelSize = pixelSize;
	}
	

	public AbstractDataset getMask() {
		return mask;
	}

	public void setMask(DoubleDataset mask) {
		try {
			if (mask == null || mask.getShape() == getDataDimensions()) {
				this.mask = mask;
				return;
			}
		} catch (Exception e) {
			//
		}
		logger.error("cannot set mask due to dimensions problem");
	}
	
	@Override
	public String toString() {
		return "Reduction Detector "+getName()+" of class "+getClass()+" working on "+key;
	}
	
	public void setqAxis(IDataset qAxis) {
		this.qAxis = DatasetUtils.convertToAbstractDataset(qAxis);
	}

	public AbstractDataset getqAxis() {
		return qAxis;
	}
	
	protected void addQAxis(INexusTree nxdata, int axisValue) {
		if (qAxis != null) {
			NcdDataUtils.addAxis(nxdata, getName(), "q", Nexus.createNexusGroupData(qAxis), axisValue, 1, "nm^{-1}", false);
		}
	}
}
