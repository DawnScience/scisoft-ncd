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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;
import javax.swing.tree.TreeNode;

import org.dawb.common.services.ServiceManager;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.persistence.IPersistenceService;
import org.eclipse.dawnsci.analysis.api.persistence.IPersistentFile;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.hdf5.HierarchicalDataFactory;
import org.eclipse.dawnsci.hdf5.IHierarchicalDataFile;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;

/**
 * Read available information from NCD data reduction files - initially ROI and mask
 */
@SuppressWarnings("deprecation")
public class NexusNcdMetadataReader {
	
	public static final String BASE_NEXUS_INSTRUMENT_PATH = "/entry1/%s_processing";
	public static final String BASE_NEXUS_PATH = BASE_NEXUS_INSTRUMENT_PATH + "/SectorIntegration";
	public static final String MASK_NEXUS_PATH = BASE_NEXUS_PATH + "/mask";
	public static final String INT_ANGLES_NEXUS_PATH = BASE_NEXUS_PATH + "/integration angles";
	public static final String INT_RADII_NEXUS_PATH = BASE_NEXUS_PATH + "/integration radii";
	public static final String INT_SYMM_NEXUS_PATH = BASE_NEXUS_PATH + "/integration symmetry";
	public static final String BEAM_CENTRE_NEXUS_PATH = BASE_NEXUS_PATH + "/beam centre";
	
	//q axis calibration
	public static final String QAXIS_BASE_PATH = BASE_NEXUS_PATH + "/qaxis calibration";
	public static final String QAXIS_GRADIENT_NEXUS_PATH = QAXIS_BASE_PATH + "/gradient";
	public static final String QAXIS_GRADIENT_ERRORS_NEXUS_PATH = QAXIS_BASE_PATH + "/gradient_errors";
	public static final String QAXIS_INTERCEPT_NEXUS_PATH = QAXIS_BASE_PATH + "/intercept";
	public static final String QAXIS_INTERCEPT_ERRORS_NEXUS_PATH = QAXIS_BASE_PATH + "/intercept_errors";

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

	private String getFormattedUnitPath(String pathString) {
		return getDetectorFormattedPath(pathString) + "@units";
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
			IDataset intAnglesSet;
			IDataset intRadiiSet;
			IDataset intSymmSet;
			IDataset beamCentreSet;
			
			if (loader.getLazyDataset(getDetectorFormattedPath(INT_ANGLES_NEXUS_PATH)) != null) { //NCD data reduction pipeline result file
				intAnglesSet = loader.getLazyDataset(getDetectorFormattedPath(INT_ANGLES_NEXUS_PATH)).getSlice();
				intRadiiSet = loader.getLazyDataset(getDetectorFormattedPath(INT_RADII_NEXUS_PATH)).getSlice();
				intSymmSet = loader.getLazyDataset(getDetectorFormattedPath(INT_SYMM_NEXUS_PATH)).getSlice();
				beamCentreSet = loader.getLazyDataset(getDetectorFormattedPath(BEAM_CENTRE_NEXUS_PATH)).getSlice();
			}
			else { //mask/ROI file
				IPersistenceService service = (IPersistenceService)ServiceManager.getService(IPersistenceService.class);
				IPersistentFile pf = service.getPersistentFile(filePath);
				SectorROI sector = (SectorROI) pf.getROI("Profile 1");
				return sector;
			}

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
			roi.setAnglesDegrees(intAngles);
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
		maskSet = loader.getLazyDataset(getDetectorFormattedPath(MASK_NEXUS_PATH)).getSlice();
		
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
	
	@SuppressWarnings("unchecked")
	public QAxisCalibration getQAxisCalibrationFromFile() throws Exception {
		IDataHolder loader = LoaderFactory.getData(filePath);
		IHierarchicalDataFile hiFile = HierarchicalDataFactory.getReader(filePath);

		if (loader.getLazyDataset(getDetectorFormattedPath(QAXIS_GRADIENT_NEXUS_PATH)) == null) {
			return null; //this file is a Dawn mask/ROI file, we will calculate the calibration parameters later
		}
		
		//this is an NCD data reduction pipeline-reduced file
		IDataset gradient = loader.getLazyDataset(getDetectorFormattedPath(QAXIS_GRADIENT_NEXUS_PATH)).getSlice();
		String gradientUnitsString = hiFile.getAttributeValue(getFormattedUnitPath(QAXIS_GRADIENT_NEXUS_PATH));
		IDataset gradientErrors= loader.getLazyDataset(getDetectorFormattedPath(QAXIS_GRADIENT_ERRORS_NEXUS_PATH)).getSlice();
		String gradientErrorsUnitsString = hiFile.getAttributeValue(getFormattedUnitPath(QAXIS_GRADIENT_ERRORS_NEXUS_PATH));
		IDataset intercept = loader.getLazyDataset(getDetectorFormattedPath(QAXIS_INTERCEPT_NEXUS_PATH)).getSlice();
		String interceptUnitsString = hiFile.getAttributeValue(getFormattedUnitPath(QAXIS_INTERCEPT_NEXUS_PATH));
		IDataset interceptErrors = loader.getLazyDataset(getDetectorFormattedPath(QAXIS_INTERCEPT_ERRORS_NEXUS_PATH)).getSlice();
		String interceptErrorsUnitsString = hiFile.getAttributeValue(getFormattedUnitPath(QAXIS_INTERCEPT_ERRORS_NEXUS_PATH));

		QAxisCalibration cal = new QAxisCalibration();
		Unit<ScatteringVectorOverDistance> gradientUnits = (Unit<ScatteringVectorOverDistance>) Unit.ONE.divide(NonSI.ANGSTROM.times(SI.MILLIMETER));
		Unit<ScatteringVector> interceptUnits = (Unit<ScatteringVector>) Unit.ONE.divide(NonSI.ANGSTROM);
		Map<String, String> unitsMap = new HashMap<String, String>();
		unitsMap.put(gradientUnits.toString(), "[Angstrom^-1*mm^-1]");
		unitsMap.put(interceptUnits.toString(), "[Angstrom^-1]");
		if (!unitsMap.get(gradientUnits.toString()).equals(gradientUnitsString) || !unitsMap.get(interceptUnits.toString()).equals(interceptUnitsString) ||
				!unitsMap.get(gradientUnits.toString()).equals(gradientErrorsUnitsString) || !unitsMap.get(interceptUnits.toString()).equals(interceptErrorsUnitsString)) {
			throw new Exception("Units are not the expected ones");
		}
		cal.setGradient((Amount<ScatteringVectorOverDistance>) Amount.valueOf(gradient.getSlice().getDouble(), gradientUnits));
		cal.setGradientErrors((Amount<ScatteringVectorOverDistance>) Amount.valueOf(gradientErrors.getDouble(), gradientUnits));
		cal.setIntercept((Amount<ScatteringVector>) Amount.valueOf(intercept.getDouble(), interceptUnits));
		cal.setInterceptErrors((Amount<ScatteringVector>) Amount.valueOf(interceptErrors.getDouble(), interceptUnits));
		return cal;
	}
}
