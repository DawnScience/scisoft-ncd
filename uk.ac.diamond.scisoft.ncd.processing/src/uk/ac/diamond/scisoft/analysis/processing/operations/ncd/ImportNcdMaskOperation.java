/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.Enumeration;
import java.util.regex.Pattern;

import javax.swing.tree.TreeNode;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.metadata.MaskMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.hdf5.HierarchicalDataFactory;
import org.eclipse.dawnsci.hdf5.IHierarchicalDataFile;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.analysis.metadata.MaskMetadataImpl;
import uk.ac.diamond.scisoft.analysis.processing.operations.mask.ImportMaskModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.mask.ImportMaskOperation;

public class ImportNcdMaskOperation extends ImportMaskOperation<ImportMaskModel> {

	private static String NCDMASKLOCATIONSTRING = "/entry1/%s_processing/SectorIntegration/mask";

	private String detectorName;

	private String getDetectorFormattedPath() {
		return String.format(NCDMASKLOCATIONSTRING, detectorName);
	}
	
	private void findDetectorName() {
		if (model.getFilePath() == null || model.getFilePath().isEmpty() || !HierarchicalDataFactory.isHDF5(model.getFilePath())) return;
		IHierarchicalDataFile hiFile = null;
		try {
			hiFile = HierarchicalDataFactory.getReader(model.getFilePath());
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
				detectorName = fixedName;
				return;
			}
			searchForDetectorName(treeNode.children());
		}
		return;
	}
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.ImportNcdMask";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.TWO;
	}
	
	@Override
	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		findDetectorName();
		OperationData returnData = new OperationData();
		try {
			returnData = super.process(input, monitor);
		} catch (OperationException e){
			returnData = getNcdMask(input);
		}
		return returnData;
	}

	private OperationData getNcdMask(IDataset input) {
		IDataset mask;
		try {
			mask = LoaderFactory.getDataSet(model.getFilePath(), getDetectorFormattedPath(), null);
		} catch (Exception e) {
			throw new OperationException(this, e);
		}
		mask = DatasetUtils.cast(mask, Dataset.BOOL);
		MaskMetadata mm = new MaskMetadataImpl(mask);
		input.setMetadata(mm);
		return new OperationData(input);
	}
}
