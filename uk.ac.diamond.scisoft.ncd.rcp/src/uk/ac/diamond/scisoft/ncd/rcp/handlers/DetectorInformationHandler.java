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

package uk.ac.diamond.scisoft.ncd.rcp.handlers;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.tree.INexusTree;
import gda.data.nexus.tree.NexusTreeBuilder;
import gda.data.nexus.tree.NexusTreeNodeSelection;
import gda.device.detector.NXDetectorData;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import uk.ac.diamond.scisoft.ncd.rcp.views.NcdDataReductionParameters;
import uk.ac.gda.server.ncd.detectorsystem.NcdDetectorSystem;

public class DetectorInformationHandler extends AbstractHandler {
	
	private static final Logger logger = LoggerFactory.getLogger(DetectorInformationHandler.class);

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		IStructuredSelection sel = (IStructuredSelection)page.getSelection();
		IWorkbenchPart part = page.getActivePart();
		
		if (sel != null) {
			
			Object[] selObjects = sel.toArray();
			HashMap<String, Integer> detNames = new HashMap<String, Integer>();
			HashMap<String, NXDetectorData> detInfo = new HashMap<String, NXDetectorData>();
			for (int i = 0; i < selObjects.length; i++) {
				
				String tmpfilePath = ((IFile)selObjects[i]).getLocation().toString();
				
				try {
					INexusTree detectorTree = NexusTreeBuilder.getNexusTree(tmpfilePath, getDetectorSelection());
				    Iterator<INexusTree> iterator = detectorTree.getNode("entry1/instrument").iterator();
				    
				    while (iterator.hasNext ()) {
				    	INexusTree tmpTree = iterator.next();
				    	String tmpName = tmpTree.getName();
				    	
				    	if (detNames.containsKey(tmpName))
				    		detNames.put(tmpName, new Integer(detNames.get(tmpName)) + 1);
				    	else {
				    		detNames.put(tmpName, new Integer(1));
				    		
				    		NXDetectorData tmpDetInfo = new NXDetectorData(tmpTree);
				    		detInfo.put(tmpName, tmpDetInfo);
				    	}
				    }
					
				} catch (Exception e) {
					logger.error("SCISOFT NCD: Error reading data reduction parameters", e);
					return Boolean.FALSE;
				}
			}
			
			// Remove detectors that are not found in all the selected files
			Iterator<Entry<String, Integer>> it = detNames.entrySet().iterator();
		    while (it.hasNext()) {
		        Entry<String, Integer> detName = it.next();
		        if (detName.getValue() != selObjects.length) {
		        	detInfo.remove(detName.getKey());
		        }
		    }
    		updateDetectorInformation(detInfo);
		}
		
		page.activate(part);
		return null;
	}

	private void updateDetectorInformation(HashMap<String, NXDetectorData> detectors) {
		
    	HashMap<String, Integer> detDims = new HashMap<String, Integer>();
    	HashMap<String, Double> pixels = new HashMap<String, Double>();
    	HashMap<String, Integer> maxChannel = new HashMap<String, Integer>();
    	ArrayList<String> calList = new ArrayList<String>();
    	ArrayList<String> detListWaxs = new ArrayList<String>();
    	ArrayList<String> detListSaxs = new ArrayList<String>();
    	
		Iterator<Entry<String, NXDetectorData>> it = detectors.entrySet().iterator();
	    while (it.hasNext()) {
	        Entry<String, NXDetectorData> detector = it.next();
	        String detName = detector.getValue().getNexusTree().getName();
	        INexusTree sasTree = detector.getValue().getNexusTree().getChildNode("sas_type", NexusExtractor.SDSClassName);
	        if (sasTree != null) {
				try {
					String type = new String((byte[]) sasTree.getData().getBuffer(), "UTF-8");
					
		        	if (type.equals(NcdDetectorSystem.CALIBRATION_DETECTOR))
		        			calList.add(detName);
		        	
		        	if (type.equals(NcdDetectorSystem.WAXS_DETECTOR)) {
		        		detListWaxs.add(detName);
		        		detDims.put(detName, 1);
		        	}
				    if (type.equals(NcdDetectorSystem.SAXS_DETECTOR)) {
	        			detListSaxs.add(detName);
		        		detDims.put(detName, 2);
				    }
		        	
				} catch (UnsupportedEncodingException e) {
					logger.error("SCISOFT NCD: Error reading sas_type information in " + detName + " detector", e);
				}
	        }
	        INexusTree pixelData = detector.getValue().getNexusTree().getChildNode("x_pixel_size", NexusExtractor.SDSClassName);
	        if (pixelData != null) {
					double[] pxSize = (double[]) pixelData.getData().getBuffer();
					pixels.put(detName, pxSize[0]*1000);
	        }
	        
			INexusTree dataNode = detector.getValue().getNexusTree().getChildNode("data", NexusExtractor.SDSClassName);
	        if (dataNode != null) {
				int[] dims = dataNode.getData().dimensions;
				maxChannel.put(detName, dims[dims.length - 1] - 1);
	        }
	    }
	    
	    NcdDataReductionParameters.setDimData(detDims);
	    NcdDataReductionParameters.setChannelData(maxChannel);
	    NcdDataReductionParameters.setPixelData(pixels);
	    NcdDataReductionParameters.setNormalisationDetectors(calList);
	    NcdDataReductionParameters.setWaxsDetectors(detListWaxs);
	    NcdDataReductionParameters.setSaxsDetectors(detListSaxs);
	}

	private NexusTreeNodeSelection getDetectorSelection() throws Exception {
		String xml = "<?xml version='1.0' encoding='UTF-8'?>" +
		"<nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>NXentry</nxClass><wanted>1</wanted><dataType>2</dataType>" +
		"<nexusTreeNodeSelection><nxClass>NXinstrument</nxClass><wanted>1</wanted><dataType>2</dataType>" +
		"<nexusTreeNodeSelection><nxClass>NXdetector</nxClass><wanted>1</wanted><dataType>2</dataType>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>data</name><wanted>1</wanted><dataType>1</dataType>" +
		"</nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>sas_type</name><wanted>1</wanted><dataType>2</dataType>" +
		"</nexusTreeNodeSelection>" +
		"<nexusTreeNodeSelection><nxClass>SDS</nxClass><name>x_pixel_size</name><wanted>1</wanted><dataType>2</dataType>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>" +
		"</nexusTreeNodeSelection>";
		return NexusTreeNodeSelection.createFromXML(new InputSource(new StringReader(xml)));
	}

}
