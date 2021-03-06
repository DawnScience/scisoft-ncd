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

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.hdf.object.HierarchicalDataFactory;
import org.eclipse.dawnsci.hdf.object.IHierarchicalDataFile;
import org.eclipse.january.IMonitor;
import org.eclipse.january.MetadataException;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.metadata.MaskMetadata;
import org.eclipse.january.metadata.MetadataFactory;

import uk.ac.diamond.scisoft.analysis.processing.operations.mask.ImportMaskModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.mask.ImportMaskOperation;
import uk.ac.diamond.scisoft.analysis.processing.operations.utils.ProcessingUtils;

public class ImportNcdMaskOperation extends ImportMaskOperation<ImportMaskModel> {

	private static String NCDMASKLOCATIONSTRING = "/entry1/%s_processing/SectorIntegration/mask";

	private String detectorName;

	private String getDetectorFormattedPath() {
		return String.format(NCDMASKLOCATIONSTRING, detectorName);
	}
	
	private void findDetectorName() {
		if (model.getFilePath() == null || model.getFilePath().isEmpty() || !HierarchicalDataFactory.isHDF5(model.getFilePath())) {
			throw new OperationException(this, new Exception("No HDF5 file specified for the mask"));
		}
		IHierarchicalDataFile hiFile = null;
		try {
			hiFile = HierarchicalDataFactory.getReader(model.getFilePath());
			TreeNode node = hiFile.getNode();
			Enumeration<?> children = node.children();
			searchForDetectorName(children);
			if (detectorName.isEmpty() || detectorName == null) {
				throw new Exception("No detector name found");
			}
		} catch (Exception e) {
			throw new OperationException(this, e);
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
		if (returnData.getData() == null) {
			throw new OperationException(this, "No mask data available");
		}
		return returnData;
	}

	private OperationData getNcdMask(IDataset input) {
		IDataset mask = ProcessingUtils.getDataset(this, model.getFilePath(), getDetectorFormattedPath());
		mask = DatasetUtils.cast(mask, Dataset.BOOL);
		MaskMetadata mm;
		try {
			mm = MetadataFactory.createMetadata(MaskMetadata.class, mask);
		} catch (MetadataException e) {
			throw new OperationException(this, e);
		}
		input.setMetadata(mm);
		return new OperationData(input);
	}
	
	@Override
	public Class<ImportMaskModel> getModelClass() {
		return ImportMaskModel.class;
	}
}
