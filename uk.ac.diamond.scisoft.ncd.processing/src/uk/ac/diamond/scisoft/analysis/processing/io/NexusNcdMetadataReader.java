/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.processing.io;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.swing.tree.TreeNode;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.hdf5.HierarchicalDataFactory;
import org.eclipse.dawnsci.hdf5.IHierarchicalDataFile;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;

/**
 * Read available information from NCD data reduction files - initially ROI and mask
 */
public class NexusNcdMetadataReader {
	
	public static final String BASE_NEXUS_INSTRUMENT_PATH = "/entry1/%s_processing";
	public static final String BASE_NEXUS_PATH = BASE_NEXUS_INSTRUMENT_PATH + "/SectorIntegration";
	public static final String MASK_NEXUS_PATH = BASE_NEXUS_PATH + "/mask";
	public static final String INT_ANGLES_NEXUS_PATH = BASE_NEXUS_PATH + "/integration angles";
	public static final String INT_RADII_NEXUS_PATH = BASE_NEXUS_PATH + "/integration radii";
	public static final String INT_SYMM_NEXUS_PATH = BASE_NEXUS_PATH + "/integration symmetry";
	public static final String BEAM_CENTRE_NEXUS_PATH = BASE_NEXUS_PATH + "/beam centre";
	
	private String filePath;

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
		findDetectorName();
	}

	private String detectorName;

	private String getDetectorFormattedPath(String pathString) {
		return String.format(pathString, detectorName);
	}

	public NexusNcdMetadataReader() {
	}
	
	public NexusNcdMetadataReader(String filePath2) {
		setFilePath(filePath2);
	}

	private void findDetectorName() {
		if (!HierarchicalDataFactory.isHDF5(filePath)) return;
		IHierarchicalDataFile hiFile = null;
		try {
			hiFile = HierarchicalDataFactory.getReader(filePath);
			TreeNode node = hiFile.getNode();
			Enumeration<?> children = node.children();
			searchForDetectorName(children);
		} catch (Exception e) {
			throw new OperationException(null, e);
		} finally {
			if (hiFile!= null)
				try {
					hiFile.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
	}

	private void searchForDetectorName(Enumeration<?> node) {
		while (node.hasMoreElements()) {
			TreeNode treeNode = (TreeNode)node.nextElement();
			String name = treeNode.toString();
			//for NCD, the entry ending with _processing has the detector name 
			if (Pattern.matches(".*_processing", name)) {
				String fixedName = name.replaceAll("_processing", "");
				setDetectorName(fixedName);
				return;
			}
			searchForDetectorName(treeNode.children());
		}
		return;
	}
	
	public void setDetectorName(String detectorName) {
		this.detectorName = detectorName;
	}
	
	public IROI getROIDataFromFile() throws Exception {
		if (!HierarchicalDataFactory.isHDF5(filePath)) return null;
		IDataHolder loader = LoaderFactory.getData(filePath);

		double[] intAngles = null;
		double[] intRadii = null;
		String intSymm = null;
		double[] beamCentre = null;
		try {
			IDataset intAnglesSet = loader.getDataset(getDetectorFormattedPath(INT_ANGLES_NEXUS_PATH));
			IDataset intRadiiSet = loader.getDataset(getDetectorFormattedPath(INT_RADII_NEXUS_PATH));
			IDataset intSymmSet = loader.getDataset(getDetectorFormattedPath(INT_SYMM_NEXUS_PATH));
			IDataset beamCentreSet = loader.getDataset(getDetectorFormattedPath(BEAM_CENTRE_NEXUS_PATH));

			intAngles = new double[intAnglesSet.getSize()];
			for (int i=0; i< intAnglesSet.getSize(); ++i) {
				intAngles[i] = intAnglesSet.getDouble(i);
			}

			intRadii = new double[intRadiiSet.getSize()];
			for (int i=0; i< intRadiiSet.getSize(); ++i) {
				intRadii[i] = intRadiiSet.getDouble(i);
			}

			intSymm = new String();
			for (int i=0; i< intSymmSet.getSize(); ++i) {
				intSymm += intSymmSet.getString(i);
			}

			beamCentre = new double[beamCentreSet.getSize()];
			for (int i=0; i< beamCentreSet.getSize(); ++i) {
				beamCentre[i] = beamCentreSet.getDouble(i);
			}

			//then they need to be assembled into a ROI
			SectorROI roi = new SectorROI();
			roi.setAngles(intAngles);
			roi.setRadii(intRadii);
			roi.setSymmetry(getSymmetryNumber(intSymm));
			roi.setPoint(beamCentre);
			return roi;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public IDataset getMaskFromFile() throws Exception {
		if (!HierarchicalDataFactory.isHDF5(filePath)) return null;
		IDataset maskSet = null;
		IDataHolder loader = LoaderFactory.getData(filePath);
		maskSet = loader.getDataset(getDetectorFormattedPath(MASK_NEXUS_PATH));
		
		return maskSet;
	}
	
	private int getSymmetryNumber(String symmetryName) {
		Map<Integer, String> symmetryEntries = SectorROI.getSymmetriesPossible();
		for (Iterator<Entry<Integer, String>> it = symmetryEntries.entrySet().iterator(); it.hasNext();) {
			Entry<Integer, String> value = it.next();
			if (value.getValue().equals(symmetryName)) {
				return value.getKey();
			}
		}
		return SectorROI.NONE;
	}
}
