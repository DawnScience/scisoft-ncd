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

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.measure.quantity.Length;
import javax.measure.unit.Unit;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.dawnsci.hdf5.Nexus;
import org.eclipse.dawnsci.hdf5.nexus.NexusUtils;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.ISourceProviderService;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Group;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Node;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.ncd.core.data.DetectorTypes;
import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdCalibrationSourceProvider;
import uk.ac.diamond.scisoft.ncd.core.rcp.NcdProcessingSourceProvider;

public class DetectorInformationHandler extends AbstractHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(DetectorInformationHandler.class);
	
	// Attribute value indicating detector data type 
	private static final Map<String, Integer> INTERPRETATION = new HashMap<String, Integer>();
	static {
		INTERPRETATION.put("spectrum", 1);
		INTERPRETATION.put("image", 2);
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		IWorkbenchPage page = window.getActivePage();
		IStructuredSelection sel = (IStructuredSelection)page.getSelection();
		IWorkbenchPart part = page.getActivePart();
		
		// Counter for a number of NeXus files in the selection
		int idxFiles = 0;	
		
		if (sel != null) {
			Object[] selObjects = sel.toArray();
			HashMap<String, Integer> detNames = new HashMap<String, Integer>();
			HashMap<String, HDF5Group> detInfo = new HashMap<String, HDF5Group>();
			for (int i = 0; i < selObjects.length; i++) {
				String tmpfilePath, tmpfileExtension;
				if (selObjects[i] instanceof IFile) {
					tmpfilePath = ((IFile)selObjects[i]).getLocation().toString();
					tmpfileExtension = ((IFile)selObjects[i]).getFileExtension();
				} else {
					tmpfilePath = ((File)selObjects[i]).getAbsolutePath();
					tmpfileExtension = FilenameUtils.getExtension(tmpfilePath);
				}
				
				if (!tmpfileExtension.equals("nxs")) {
					continue;
				}
				
				try {
					HDF5File tmpfile = new HDF5Loader(tmpfilePath).loadTree();
					String[] locations = new String[] {"/entry1", "/entry1/instrument"};
					for (String loc : locations) {
						HDF5NodeLink nodeLink = tmpfile.findNodeLink(loc);
						if (nodeLink != null) {
							HDF5Group node = (HDF5Group) nodeLink.getDestination();
							Iterator<String> iterator = node.getNodeNameIterator();

							while (iterator.hasNext()) {
								String tmpName = iterator.next();
								HDF5Node tmpTree = node.findNodeLink(tmpName).getDestination();
								if (tmpTree instanceof HDF5Group) {
									String nxClass = tmpTree.getAttribute(NexusUtils.NXCLASS).getFirstElement();
									if (nxClass.equals(Nexus.DETECT) || nxClass.equals(Nexus.MONITOR)) {
										if (detNames.containsKey(tmpName)) {
											detNames.put(tmpName, detNames.get(tmpName) + 1);
										} else {
											detNames.put(tmpName, 1);
											detInfo.put(tmpName, (HDF5Group) tmpTree);
										}
									}
								}
							}
						}
					}
					idxFiles++;
				} catch (Exception e) {
					logger.error("SCISOFT NCD: Error reading data reduction parameters", e);
					return null;
				}
			}
			
			// Remove detectors that are not found in all the selected files
			Iterator<Entry<String, Integer>> it = detNames.entrySet().iterator();
		    while (it.hasNext()) {
		        Entry<String, Integer> detName = it.next();
		        if (detName.getValue() != idxFiles) {
		        	detInfo.remove(detName.getKey());
		        }
		    }
    		updateDetectorInformation(detInfo, window);
		}
		
		page.activate(part);
		return null;
	}

	private void updateDetectorInformation(Map<String, HDF5Group> detectors, IWorkbenchWindow window) {
		
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		NcdCalibrationSourceProvider ncdDetectorSourceProvider = (NcdCalibrationSourceProvider) service.getSourceProvider(NcdCalibrationSourceProvider.NCDDETECTORS_STATE);
		NcdProcessingSourceProvider ncdScalerSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SCALER_STATE);
		NcdProcessingSourceProvider ncdWaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.WAXSDETECTOR_STATE);
		NcdProcessingSourceProvider ncdSaxsDetectorSourceProvider = (NcdProcessingSourceProvider) service.getSourceProvider(NcdProcessingSourceProvider.SAXSDETECTOR_STATE);
	    
	    ncdDetectorSourceProvider.clearNcdDetectors();
	
		Iterator<Entry<String, HDF5Group>> it = detectors.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String, HDF5Group> detector = it.next();
	        String detName = detector.getKey();
	        HDF5Group detNode = detector.getValue();
			String nxClass = detNode.getAttribute(NexusUtils.NXCLASS).getFirstElement();
	        if (nxClass.equals(Nexus.MONITOR)) {
        		NcdDetectorSettings tmpDet = new NcdDetectorSettings(detName, DetectorTypes.CALIBRATION_DETECTOR, 1);
				tmpDet.setMaxChannel(0);
    	        ncdDetectorSourceProvider.addNcdDetector(tmpDet);
    	        continue;
	        }
	        HDF5NodeLink sasNode = detector.getValue().getNodeLink("sas_type");
	        HDF5NodeLink dataNode = detector.getValue().getNodeLink("data");
	        if (sasNode != null && dataNode != null) {
				try {
					String type = ((AbstractDataset)((HDF5Dataset)sasNode.getDestination()).getDataset()).getString(0);
					
		        	if (type.equals(DetectorTypes.CALIBRATION_DETECTOR)) {
		        		NcdDetectorSettings tmpDet = new NcdDetectorSettings(detName, type, 1);
	    				int[] dims = ((HDF5Dataset)dataNode.getDestination()).getDataset().getShape();
	    				tmpDet.setMaxChannel(dims[dims.length - 1] - 1);
		    	        ncdDetectorSourceProvider.addNcdDetector(tmpDet);
		        	}
		        	
				    if (type.equals(DetectorTypes.WAXS_DETECTOR) || type.equals(DetectorTypes.SAXS_DETECTOR)) {
		        		NcdDetectorSettings tmpDet = new NcdDetectorSettings(detName, type, 1);
		        		if (type.equals(DetectorTypes.SAXS_DETECTOR)) {
		        			tmpDet.setDimension(2);
		        		}
						if (dataNode.getDestination().containsAttribute("interpretation")) {
							String interpretation = dataNode.getDestination().getAttribute("interpretation").getFirstElement();
							if (INTERPRETATION.containsKey(interpretation)) {
								tmpDet.setDimension(INTERPRETATION.get(interpretation));
							}
						}
		    	        HDF5NodeLink pixelData = detector.getValue().getNodeLink("x_pixel_size");
		    	        if (pixelData != null) {
		    	        	HDF5Dataset pxSizeDataset = (HDF5Dataset) pixelData.getDestination();
		    				double pxSize = pxSizeDataset.getDataset().getSlice().getDouble(0);
		    				if (pxSizeDataset.containsAttribute("units")) {
								Unit<Length> pxSizeUnit = Unit.valueOf(pxSizeDataset.getAttribute("units").getFirstElement()).asType(Length.class);
			    				tmpDet.setPxSize(Amount.valueOf(pxSize, pxSizeUnit));
		    				}
		    	        }
		    	        ncdDetectorSourceProvider.addNcdDetector(tmpDet);
				    }
				} catch (Exception e) {
					logger.error("SCISOFT NCD: Error reading sas_type information in " + detName + " detector", e);
				}
	        }
	    }
	    
		ncdDetectorSourceProvider.updateNcdDetectors();
		
		if (ncdDetectorSourceProvider.getNcdDetectors().isEmpty()) {
			ncdScalerSourceProvider.setScaler(null);
		    ncdWaxsDetectorSourceProvider.setWaxsDetector(null);
		    ncdSaxsDetectorSourceProvider.setSaxsDetector(null);
		}
	}
}
