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

package uk.ac.diamond.scisoft.ncd.rcp.utils;

import gda.data.nexus.extractor.NexusExtractor;
import gda.data.nexus.extractor.NexusGroupData;
import gda.data.nexus.tree.INexusTree;

import java.util.Arrays;
import java.util.List;

import org.nexusformat.NXlink;
import org.nexusformat.NeXusFileInterface;
import org.nexusformat.NexusException;

public class NcdNexusUtils {

	/**
	 * This function is tailor-made for writing Nexus files with NCD data reduction results.
	 * It will append frame data for the predefined grid position in the output file.
	 * @param file
	 * 			output Nexus file
	 * @param tree
	 * 			Nexus tree object to write into file 
	 * @param makeData
	 * 			flag for adding data into Nexus file
	 * @param attrOnly 
	 * @param links
	 * @param dataDimPrefix
	 * 			array with padding values for grid position 
	 * @param dataStartPosPrefix
	 * 			array with initial grid position 
	 * @param dataDimMake
	 * 			array indicating total grid dimension with total number of frames per grid point
	 * @param dim
	 * 			frame dimension
	 * @throws NexusException
	 */
	public static void writeNcdData(NeXusFileInterface file, INexusTree tree, boolean makeData, boolean attrOnly, List<NXlink> links,
			int[] dataDimPrefix, int[] dataStartPosPrefix, int[] dataDimMake, int dim)	throws NexusException {
		
		if (!tree.isPointDependent() && !makeData) {
			return;
		}
		String name = tree.getName();
		String nxClass = tree.getNxClass();
		Boolean dataOpen = false;
		Boolean loopNodes = true;
		Boolean attrBelowThisOnly = attrOnly;
		Boolean nxClassIsSDS = nxClass.equals(NexusExtractor.SDSClassName);
		Boolean nxClassIsAttr = nxClass.equals(NexusExtractor.AttrClassName);
		if (nxClassIsAttr) {
			if (makeData) {
				NexusGroupData data = tree.getData();
				if (data != null && data.getBuffer() != null) {
					file.putattr(name, data.getBuffer(), data.type);
				}
			}
			return;
		}
		if (attrOnly) {
			return;
		}
		if (!name.isEmpty() && !nxClass.isEmpty()) {
			if (!nxClassIsSDS) {
				if (!(file.groupdir().containsKey(name) && file.groupdir().get(name).equals(nxClass))) {
					file.makegroup(name, nxClass);
				}
				file.opengroup(name, nxClass);
			}
	
			NexusGroupData sds = tree.getData();
			if (sds != null) {
				if( sds.dimensions != null){
					for( int i : sds.dimensions){
						if( i== 0 )
							throw new NexusException("Data for " + name + " is invalid. SDS Dimension = 0");
					}
				}
				if (makeData) {
					int[] dataStartPos;
					if (!(file.groupdir().containsKey(name) && file.groupdir().get(name).equals(nxClass))) {
						// make the data array to store the data...
						if (tree.isPointDependent()) {
							int[] dataDimFull = NcdNexusUtils.generateDataDimFull(dataDimMake, sds.dimensions, dim);
							file.makedata(name, sds.type, dataDimFull.length, dataDimFull);
						}
						else {
							int[] tmpDimMake = NcdNexusUtils.generateDataDim(null, sds.dimensions);
							file.makedata(name, sds.type, tmpDimMake.length, tmpDimMake);
						}
					}
					// FIXME put a break point here and not make it crash
	
					file.opendata(name);
					dataStartPos = NcdNexusUtils.generateDataStartPos((tree.isPointDependent()) ? dataStartPosPrefix : null, sds.dimensions, dim);
					int[] dataDim = NcdNexusUtils.generateDataDim((tree.isPointDependent()) ? dataDimPrefix : null, sds.dimensions);
					file.putslab(sds.getBuffer(), dataStartPos, dataDim);
					if (links != null && sds.isDetectorEntryData) {
						links.add(file.getdataID());
					}
	
					dataOpen = true;
					attrBelowThisOnly = true;
				}
			}
		}
		try {
			if (loopNodes) {
				for (INexusTree branch : tree) {
					writeNcdData(file, branch, makeData, attrBelowThisOnly, links, dataDimPrefix, dataStartPosPrefix, dataDimMake, dim);
				}
			}
		} finally {
			if (dataOpen) {
				file.closedata();
			}
			if (!name.isEmpty() && !nxClass.isEmpty() && !nxClassIsSDS) {
				file.closegroup();
			}
		}
	}

	private static int[] generateDataStartPos(int[] dataStartPosPrefix, int[] dataDimensions, int dim) {
		int[] dataStartPos = null;
		if (dataStartPosPrefix != null) {
			dataStartPos = Arrays.copyOf(dataStartPosPrefix, dataStartPosPrefix.length
					+ (dataDimensions != null ? dim : 0));
		} else if (dataDimensions != null) {
			dataStartPos = new int[dataDimensions.length];
		}
		return dataStartPos;
	}

	private static int[] generateDataDimFull(int[] dataDimMake, int[] dataDimensions, int dim) {
		int[] dataDim = null;
		if (dataDimMake != null) {
			dataDim  = new int[dataDimMake.length + dim];
			for (int i = (dataDimMake.length + dim - 1); i >= 0; i--) {
				if (i > (dataDimMake.length - 1)) {
					int idx = dataDimensions.length - 1 - ((dataDimMake.length + dim - 1) - i); 
					dataDim[i] = dataDimensions[idx];
				} 
				else
					dataDim[i] = dataDimMake[i];
			}
		}
		else
			if (dataDimensions != null)
				dataDim = Arrays.copyOf(dataDimensions, dataDimensions.length);
		
		return dataDim;
	}

	private static int[] generateDataDim(int[] dataDimPrefix, int[] dataDimensions) {
		int[] dataDim = null;
		if (dataDimPrefix != null) {
			//do not attempt to add dataDimensions if not set or indicates single point
			int dataDimensionToAdd = dataDimensions != null ? dataDimensions.length : 0;
	
			dataDim = Arrays.copyOf(dataDimPrefix, dataDimPrefix.length + dataDimensionToAdd);
			if( dataDimensionToAdd > 0 && dataDimensions != null){
				for (int i = dataDimPrefix.length; i < dataDimPrefix.length + dataDimensionToAdd; i++) {
					dataDim[i] = dataDimensions[i - dataDimPrefix.length];
				}
			}
		} else if (dataDimensions != null) {
			dataDim = Arrays.copyOf(dataDimensions, dataDimensions.length);
		}
		return dataDim;
	}

}
