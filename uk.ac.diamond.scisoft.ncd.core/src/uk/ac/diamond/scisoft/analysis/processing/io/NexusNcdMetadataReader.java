/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.processing.io;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.impl.ByteDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FloatDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IntegerDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.LongDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.ShortDataset;
import org.eclipse.dawnsci.analysis.dataset.roi.SectorROI;
import org.eclipse.dawnsci.hdf5.HierarchicalDataFactory;
import org.eclipse.dawnsci.hdf5.HierarchicalDataUtils;
import org.eclipse.dawnsci.hdf5.IHierarchicalDataFile;

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

	public void setDetectorName(String detectorName) {
		this.detectorName = detectorName;
	}
	
	public IROI getROIDataFromFile() {
		if (!HierarchicalDataFactory.isHDF5(filePath)) return null;
		IHierarchicalDataFile hiFile = null;
		double[] intAngles = null;
		double[] intRadii = null;
		String intSymm = null;
		double[] beamCentre = null;
		try {
			//need to read the following - "integration angles", "integration radii", "integration symmetry", "beam centre"
			hiFile = HierarchicalDataFactory.getReader(filePath);
			try {
				hiFile = HierarchicalDataFactory.getReader(filePath);
				IDataset intAnglesSet = getSet(hiFile, getDetectorFormattedPath(INT_ANGLES_NEXUS_PATH));
				IDataset intRadiiSet = getSet(hiFile, getDetectorFormattedPath(INT_RADII_NEXUS_PATH));
				IDataset intSymmSet = getSet(hiFile, getDetectorFormattedPath(INT_SYMM_NEXUS_PATH));
				IDataset beamCentreSet = getSet(hiFile, getDetectorFormattedPath(BEAM_CENTRE_NEXUS_PATH));
				
				intAngles = new double[intAnglesSet.getSize()];
				for (int i=0; i< intAnglesSet.getSize(); ++i) {
					intAngles[i] = intAnglesSet.getDouble(i);
				}
				
				intRadii = new double[intRadiiSet.getSize()];
				for (int i=0; i< intRadiiSet.getSize(); ++i) {
					intRadii[i] = intRadiiSet.getDouble(i);
				}
				
				//TODO intsymmset - not sure we can handle strings like this
				
				beamCentre = new double[beamCentreSet.getSize()];
				for (int i=0; i< beamCentreSet.getSize(); ++i) {
					beamCentre[i] = beamCentreSet.getDouble(i);
				}
				
				} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (hiFile!= null)
					try {
						hiFile.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
			}
			
			//then they need to be assembled into a ROI
			SectorROI roi = new SectorROI();
			roi.setAngles(intAngles);
			roi.setRadii(intRadii);
			//go through some bother to set the symmetry value
			roi.setSymmetry(getSymmetryNumber(intSymm));
			roi.setPoint(beamCentre);
			return roi;
			} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (hiFile!= null)
				try {
					hiFile.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
		return null;
	}
	
	public IDataset getMaskFromFile() throws Exception {
		if (!HierarchicalDataFactory.isHDF5(filePath)) return null;
		IHierarchicalDataFile hiFile = null;
		IDataset maskSet = null;
		try {
			hiFile = HierarchicalDataFactory.getReader(filePath);
			maskSet = getSet(hiFile, MASK_NEXUS_PATH);
			
		} finally {
			if (hiFile!= null)
				try {
					hiFile.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
		}
		return maskSet;
	}
	
	private Dataset getSet(IHierarchicalDataFile file, final String path) throws Exception {

		if (!file.isDataset(path)) return null;
		
		ncsa.hdf.object.Dataset       set = (ncsa.hdf.object.Dataset)file.getData(path);
		final Object  val = set.read();
		
		long[] dataShape = HierarchicalDataUtils.getDims(set);
		
		final int[] intShape  = getInt(dataShape);
         
		Dataset ret = null;
        if (val instanceof byte[]) {
        	ret = new ByteDataset((byte[])val, intShape);
        } else if (val instanceof short[]) {
        	ret = new ShortDataset((short[])val, intShape);
        } else if (val instanceof int[]) {
        	ret = new IntegerDataset((int[])val, intShape);
        } else if (val instanceof long[]) {
        	ret = new LongDataset((long[])val, intShape);
        } else if (val instanceof float[]) {
        	ret = new FloatDataset((float[])val, intShape);
        } else if (val instanceof double[]) {
        	ret = new DoubleDataset((double[])val, intShape);
        } else {
        	throw new Exception("Cannot deal with data type "+set.getDatatype().getDatatypeDescription());
        }
        
		if (set.getDatatype().isUnsigned()) {
			switch (ret.getDtype()) {
			case Dataset.INT32:
				ret = new LongDataset(ret);
				DatasetUtils.unwrapUnsigned(ret, 32);
				break;
			case Dataset.INT16:
				ret = new IntegerDataset(ret);
				DatasetUtils.unwrapUnsigned(ret, 16);
				break;
			case Dataset.INT8:
				ret = new ShortDataset(ret);
				DatasetUtils.unwrapUnsigned(ret, 8);
				break;
			}
		}
        return ret;
	}
	
	private int[] getInt(long[] longShape) {
		final int[] intShape  = new int[longShape.length];
		for (int i = 0; i < intShape.length; i++) intShape[i] = (int)longShape[i];
		return intShape;
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
