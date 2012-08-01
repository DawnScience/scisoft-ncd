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

package uk.ac.diamond.scisoft.ncd.actors;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.HDFArray;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.dawb.common.util.io.IFileUtils;
import org.dawb.passerelle.common.actors.AbstractDataMessageTransformer;
import org.dawb.passerelle.common.actors.AbstractPassModeTransformer;
import org.dawb.passerelle.common.message.DataMessageComponent;
import org.dawb.passerelle.common.message.MessageUtils;
import org.dawb.passerelle.common.parameter.ParameterUtils;
import org.eclipse.core.resources.IResource;

import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.FloatDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IntegerDataset;
import uk.ac.diamond.scisoft.analysis.dataset.LongDataset;
import uk.ac.diamond.scisoft.analysis.dataset.ShortDataset;
import uk.ac.diamond.scisoft.analysis.dataset.UnsignedShortDataset;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.TerminationException;
import com.isencia.passerelle.util.ptolemy.ResourceParameter;
import com.isencia.passerelle.workbench.util.ResourceUtils;

public class NcdNexusTreeTransformer extends AbstractDataMessageTransformer {

	private static final long serialVersionUID = 1312885325541751197L;

	private Parameter         detectorName;
	private ResourceParameter filePathParam;
	private String            detector;

	private IResource resource;

	public NcdNexusTreeTransformer(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException {
		super(container, name);
		
		filePathParam = new ResourceParameter(this, "Output");
		filePathParam.setResourceType(IResource.FOLDER);
		filePathParam.setExpression("/${project_name}/output/");
		registerConfigurableParameter(filePathParam);
		
		detectorName = new StringParameter(this,"Detector");
		detectorName.setExpression("Pilatus2M");
		registerConfigurableParameter(detectorName);
	}

	@Override
	protected String getOperationName() {
		return "Creates a ncd nexus tree";
	}

	public void attributeChanged(Attribute attribute) throws IllegalActionException {

		if (attribute == detectorName) {
			detector = detectorName.getExpression();
		}
		super.attributeChanged(attribute);
	}

	@Override
	protected DataMessageComponent getTransformedMessage(List<DataMessageComponent> cache) throws ProcessingException {
		
		String fileFullPath = null;
		try {
			final Map<String,String> scalar = MessageUtils.getScalar(cache);
			final String fileName = scalar!=null ? scalar.get("file_name") : null;
			//final File  output = new File(scalar.get("file_path"));
			
			final String filePath = getDirectoryPath();
			fileFullPath = filePath + File.separator + FilenameUtils.getBaseName(fileName) + ".nxs";
			
			final DataMessageComponent comp = new DataMessageComponent();
			comp.addScalar(scalar);
			comp.putScalar("file_path", scalar.get("file_path"));
			comp.putScalar("file_dir",  scalar.get("file_dir"));
			
			final File targetFile = new File(filePath);
			comp.putScalar("file_name", targetFile.getName());
			comp.putScalar("file_basename", FilenameUtils.getBaseName(targetFile.getName()));

			writeNxsFile(fileFullPath, cache, comp);

			//AbstractPassModeTransformer.refreshResource(output);
			
			return comp;
			
		} catch (Exception ne) {
			try {
				H5.H5Eprint2(HDF5Constants.H5E_DEFAULT, null);
			} catch (HDF5LibraryException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			throw createDataMessageException("Cannot write to "+fileFullPath, ne);
		} 
	}

	private String getDirectoryPath() throws Exception {
		try {
			String dir  = ParameterUtils.getSubstituedValue(filePathParam, cache);
			try {
				final File d = new File(dir);
				if (d.exists()) return d.getAbsolutePath();
			} catch (Throwable ignored) {
				// parse as a resource
			}
			
			this.resource = ResourceUtils.getResource(dir);
			if (resource==null) { // This call 
				resource = IFileUtils.getContainer(dir, getProject().getName(), "output");
			}
			return resource.getLocation().toOSString();
			
		} catch (Exception ne) {
			throw createDataMessageException("Cannot get folder path!", ne);
		}
	}
	
	public void doWrapUp() throws TerminationException {
		super.doWrapUp();
		if (resource!=null) {
			AbstractPassModeTransformer.refreshResource(resource);
			resource = null;
		}
	}
	
	private void writeNxsFile(String filePath, List<DataMessageComponent> cache, DataMessageComponent comp) throws NullPointerException, HDF5Exception {
		
		final List<IDataset>        sets = MessageUtils.getDatasets(cache);
		AbstractDataset data = (AbstractDataset) sets.get(0);
		
		// add frame dimension
		int[] newShape = ArrayUtils.addAll(new int[] {1, 1}, data.getShape());
		data = data.reshape(newShape);
		
		int fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
		H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_STRONG);
		int nxsfile_handle = H5.H5Fcreate(filePath, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, fapl);
		H5.H5Pclose(fapl);
		int[] libversion = new int[3];
		H5.H5get_libversion(libversion);
		putattr(nxsfile_handle, "HDF5_version", StringUtils.join(ArrayUtils.toObject(libversion), "."));
		putattr(nxsfile_handle, "file_name", filePath);
		
		Date date = new Date();
		SimpleDateFormat format =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		String dt =  format.format(date);
		putattr(nxsfile_handle, "file_time", dt);
		
		int entry_group_id = makegroup(nxsfile_handle, "entry1", "NXentry");
		
		int instrument_group_id = makegroup(entry_group_id, "instrument", "NXinstrument");
		int detector_group_id = makegroup(instrument_group_id, detector, "NXdetector");
		
		int datatype = -1;
		if (data instanceof ShortDataset) {
			datatype = HDF5Constants.H5T_NATIVE_SHORT;
		} else if (data instanceof UnsignedShortDataset) {
			datatype = HDF5Constants.H5T_NATIVE_USHORT;
		} else if (data instanceof IntegerDataset) {
			datatype = HDF5Constants.H5T_NATIVE_INT;
		} else if (data instanceof LongDataset) {
			datatype = HDF5Constants.H5T_NATIVE_LONG;
		} else if (data instanceof FloatDataset) {
			datatype = HDF5Constants.H5T_NATIVE_FLOAT;
		} else if (data instanceof DoubleDataset) {
			datatype = HDF5Constants.H5T_NATIVE_DOUBLE;
		}
		
		int input_data_id = makedata(detector_group_id, "data", datatype, data);
		putattr(input_data_id, "signal", 1);
		putattr(input_data_id, "units", "counts");
		make_sas_type(detector_group_id);
		
		int link_group_id = makegroup(entry_group_id, detector, "NXdata");
		H5.H5Lcreate_hard(detector_group_id, "./data", link_group_id, "./data", HDF5Constants.H5P_DEFAULT,  HDF5Constants.H5P_DEFAULT);
		
		H5.H5Dclose(input_data_id);
		H5.H5Gclose(detector_group_id);
		H5.H5Gclose(instrument_group_id);
		H5.H5Gclose(link_group_id);
		H5.H5Gclose(entry_group_id);
		H5.H5Fclose(nxsfile_handle);
	}

	private int makegroup(int handle, String name, String nxclass) throws NullPointerException, HDF5Exception {
		int open_group_id = -1;
		int dataspace_id = -1;
		int datatype_id = -1;
		int attribute_id = -1;
		open_group_id = H5.H5Gcreate(handle, name, HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

		byte[] nxdata = nxclass.getBytes();
		dataspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
		datatype_id = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		H5.H5Tset_size(datatype_id, nxdata.length);
		attribute_id = H5.H5Acreate(open_group_id, "NX_class", datatype_id,
				dataspace_id, HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT);
		H5.H5Awrite(attribute_id, datatype_id, nxdata);

		H5.H5Sclose(dataspace_id);
		H5.H5Tclose(datatype_id);
		H5.H5Aclose(attribute_id);
		
		return open_group_id;
	}
	
	
	private int makedata(int open_group_id, String name, int type, AbstractDataset data) throws NullPointerException, HDF5Exception {
		int rank = data.getRank();
		int[] dim = data.getShape();

		long[] ldim = new long[rank];
		long[] lmaxdim = new long[rank];
		int dataspace_id = -1;
		for (int idx = 0; idx < rank; idx++) {
			if (dim[idx] == HDF5Constants.H5S_UNLIMITED) {
				ldim[idx] = 1;
				lmaxdim[idx] = HDF5Constants.H5S_UNLIMITED;
			} else {
				ldim[idx] = dim[idx];
				lmaxdim[idx] = dim[idx];
			}
		}
		dataspace_id = H5.H5Screate_simple(rank, ldim, lmaxdim);

		int dcpl_id = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE);
		if (dcpl_id >= 0)
			H5.H5Pset_chunk(dcpl_id, rank, ldim);

		int dataset_id = H5.H5Dcreate(open_group_id, name, type, dataspace_id,
				HDF5Constants.H5P_DEFAULT, dcpl_id, HDF5Constants.H5P_DEFAULT);

		H5.H5Dwrite(dataset_id, type, HDF5Constants.H5S_ALL,
				HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT,
				data.getBuffer());
		H5.H5Sclose(dataspace_id);
		H5.H5Pclose(dcpl_id);

		return dataset_id;
	}
	
	private void make_sas_type(int dataset_id) throws NullPointerException, HDF5Exception {
		int sas_type = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
		byte[] saxs = "SAXS".getBytes();
		H5.H5Tset_size(sas_type, saxs.length);
		int dataspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
		int saxs_id = H5.H5Dcreate(dataset_id, "sas_type", sas_type, dataspace_id,
				HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);

		H5.H5Dwrite(saxs_id, sas_type, HDF5Constants.H5S_ALL,
				HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT,
				saxs);
		
	}
	
	private void putattr(int dataset_id, String name, Object value) throws NullPointerException, HDF5Exception {
		int attr_type = -1;
		int dataspace_id = -1;
		byte[] data = null;
		
		if (value instanceof String) {
			data = ((String) value).getBytes();
			attr_type = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
			H5.H5Tset_size(attr_type, data.length);
		}
		if (value instanceof Integer) {
			HDFArray ha = new HDFArray(new int[] {(Integer) value});
			data = ha.byteify();
			attr_type = H5.H5Tcopy(HDF5Constants.H5T_NATIVE_INT32);
		}
		dataspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
		int attribute_id = H5.H5Acreate(dataset_id, name, attr_type,
				dataspace_id, HDF5Constants.H5P_DEFAULT,
				HDF5Constants.H5P_DEFAULT);
		
		H5.H5Awrite(attribute_id, attr_type, data);
		
		H5.H5Tclose(attr_type);
		H5.H5Sclose(dataspace_id);
		H5.H5Aclose(attribute_id);
	}
}
