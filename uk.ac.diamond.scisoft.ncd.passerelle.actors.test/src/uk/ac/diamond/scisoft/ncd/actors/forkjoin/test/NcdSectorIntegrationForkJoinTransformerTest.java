/*
 * Copyright 2014 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.actors.forkjoin.test;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.dawb.common.services.IPersistenceService;
import org.dawb.common.services.ServiceManager;
import org.dawnsci.persistence.PersistenceServiceCreator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.TestUtils;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdProcessingObject;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdSectorIntegrationForkJoinTransformer;
import uk.ac.diamond.scisoft.ncd.utils.NcdNexusUtils;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.Sink;
import com.isencia.passerelle.actor.Source;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.core.PasserelleException;
import com.isencia.passerelle.domain.et.ETDirector;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;
import com.isencia.passerelle.model.Flow;
import com.isencia.passerelle.model.FlowAlreadyExecutingException;
import com.isencia.passerelle.model.FlowManager;

public class NcdSectorIntegrationForkJoinTransformerTest {

	private static final String NXEntryClassName = "NXentry";
	private static final String NXInstrumentClassName = "NXinstrument";
	private static final String NXDataClassName = "NXdata";
	private static String testScratchDirectoryName;
	private static String filename;
	private static String testDatasetName = "testInput";

	private ReentrantLock lock = new ReentrantLock();
	private SectorROI intSector;

	private static AbstractDataset data;
	private static long[] shape = new long[] { 5, 3, 91, 32, 64 };
	private static long[] radialShape = new long[] { 5, 3, 91, 64 };
	private static long[] azimuthalShape = new long[] { 5, 3, 91, 131 };
	private static long[] imageShape = new long[] { shape[3], shape[4] };
	private static int dim = 2;
	private static int points = 1;

	private static Flow flow;
	private static FlowManager flowMgr;

	@BeforeClass
	public static void setUp() throws Exception {
		
		// This is required for ROIParameter class to work		
		ServiceManager.setService(IPersistenceService.class, PersistenceServiceCreator.createPersistenceService());		
		
		flow = new Flow("unit test", null);
		flowMgr = new FlowManager();
		ETDirector director = new ETDirector(flow, "director");
		flow.setDirector(director);

		testScratchDirectoryName = TestUtils.generateDirectorynameFromClassname(
				NcdSectorIntegrationForkJoinTransformerTest.class.getCanonicalName());
		TestUtils.makeScratchDirectory(testScratchDirectoryName);

		for (long n : imageShape)
			points *= n;

		{
			filename = testScratchDirectoryName + "ncd_sda_test.nxs";

			int nxsFile = H5.H5Fcreate(filename, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			int entry_id = NcdNexusUtils.makegroup(nxsFile, "entry1", NXEntryClassName);
			NcdNexusUtils.makegroup(entry_id, "results", NXInstrumentClassName);
			int datagroup_id = NcdNexusUtils.makegroup(entry_id, testDatasetName, NXDataClassName);
			int data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, shape, true,
					"counts");
			int errors_id = NcdNexusUtils.makedata(datagroup_id, "errors", HDF5Constants.H5T_NATIVE_DOUBLE, shape,
					true, "counts");

			for (int m = 0; m < shape[0]; m++)
				for (int n = 0; n < shape[1]; n++) {
					for (int frames = 0; frames < shape[2]; frames++) {
						float[] data = new float[points];
						double[] errors = new double[points];
						for (int i = 0; i < imageShape[0]; i++) {
							for (int j = 0; j < imageShape[1]; j++) {
								int idx = (int) (i * imageShape[1] + j);
								float val = n * shape[2] + frames + i * imageShape[1] + j;
								data[idx] = val;
								errors[idx] = Math.sqrt(val);
							}
						}
						{
							long[] start = new long[] { m, n, frames, 0, 0 };
							long[] count = new long[] { 1, 1, 1, 1, 1 };
							long[] block = new long[] { 1, 1, 1, imageShape[0], imageShape[1] };
							int filespace_id = H5.H5Dget_space(data_id);
							int type_id = H5.H5Dget_type(data_id);
							int memspace_id = H5.H5Screate_simple(dim, imageShape, null);
							H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count,
									block);
							H5.H5Dwrite(data_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, data);
							H5.H5Sclose(memspace_id);
							H5.H5Sclose(filespace_id);
							H5.H5Tclose(type_id);

							filespace_id = H5.H5Dget_space(errors_id);
							type_id = H5.H5Dget_type(errors_id);
							memspace_id = H5.H5Screate_simple(dim, imageShape, null);
							H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count,
									block);
							H5.H5Dwrite(errors_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT,
									errors);
							H5.H5Sclose(memspace_id);
							H5.H5Sclose(filespace_id);
							H5.H5Tclose(type_id);
						}
					}
				}
			H5.H5Dclose(data_id);
			H5.H5Dclose(errors_id);
			H5.H5Gclose(datagroup_id);
			H5.H5Gclose(entry_id);
			H5.H5Fclose(nxsFile);
		}

		int file_handle = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		int entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		int detector_group_id = H5.H5Gopen(entry_group_id, testDatasetName, HDF5Constants.H5P_DEFAULT);
		int input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		int input_errors_id = H5.H5Dopen(detector_group_id, "errors", HDF5Constants.H5P_DEFAULT);

		DataSliceIdentifiers data_id = new DataSliceIdentifiers();
		data_id.setIDs(detector_group_id, input_data_id);
		DataSliceIdentifiers errors_id = null;
		errors_id = new DataSliceIdentifiers();
		errors_id.setIDs(detector_group_id, input_errors_id);

		SliceSettings dataSlice = new SliceSettings(shape, 0, (int) shape[0]);
		int[] start = new int[] { 0, 0, 0, 0, 0 };
		dataSlice.setStart(start);
		data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
		AbstractDataset error = NcdNexusUtils.sliceInputData(dataSlice, errors_id);
		data.setError(error);
	}

	private class NcdMessageSource extends Source {

		private static final long serialVersionUID = -6462158669206987591L;

		private boolean messageSent;

		public NcdMessageSource(CompositeEntity container, String name) throws NameDuplicationException,
				IllegalActionException {
			super(container, name);
			messageSent = false;
		}

		@Override
		protected ManagedMessage getMessage() throws ProcessingException {
			ManagedMessage dataMsg = null;
			if (messageSent) {
				return null;
			}
			try {
				int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
				int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
				int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);
				int detector_group_id = H5.H5Gopen(entry_id, testDatasetName, HDF5Constants.H5P_DEFAULT);
				int input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
				int input_errors_id = H5.H5Dopen(detector_group_id, "errors", HDF5Constants.H5P_DEFAULT);

				NcdProcessingObject obj = new NcdProcessingObject(entry_id, processing_group_id, detector_group_id, input_data_id, input_errors_id, lock);

				dataMsg = createMessage(obj, "application/octet-stream");
			} catch (Exception e) {
				messageSent = false;
				throw new ProcessingException(ErrorCode.MSG_CONSTRUCTION_ERROR, "Error creating msg", this, e);
			}
			messageSent = true;
			return dataMsg;
		}

	}

	private class NcdMessageSink extends Sink {

		private static final long serialVersionUID = 79096075353029810L;

		public NcdMessageSink(CompositeEntity container, String name) throws NameDuplicationException,
				IllegalActionException {
			super(container, name);
		}

		@Override
		protected void sendMessage(ManagedMessage message) throws ProcessingException {
			if (message != null) {
				try {
					
					SliceSettings slice = new SliceSettings(radialShape, 0, (int) radialShape[0]);
					slice.setStart(new int[] { 0, 0, 0, 0 });
					SliceSettings azSlice = new SliceSettings(azimuthalShape, 0, (int) azimuthalShape[0]);
					azSlice.setStart(new int[] { 0, 0, 0, 0 });

					Object obj = message.getBodyContent();
					if (obj instanceof NcdProcessingObject) {
						NcdProcessingObject content = (NcdProcessingObject) obj;
						int result_group_id = content.getInputGroupID();
						int result_data_id = content.getInputDataID();
						int result_errors_id = content.getInputErrorsID();
						
						DataSliceIdentifiers result_ids = new DataSliceIdentifiers();
						result_ids.setIDs(result_group_id, result_data_id);
						DataSliceIdentifiers result_error_ids = new DataSliceIdentifiers();
						result_error_ids.setIDs(result_group_id, result_errors_id);
						
						AbstractDataset outData = NcdNexusUtils.sliceInputData(slice, result_ids);
						AbstractDataset outErrors = NcdNexusUtils.sliceInputData(slice, result_error_ids);
						
						int azimuthal_data_id = H5.H5Dopen(result_group_id, "azimuth", HDF5Constants.H5P_DEFAULT);
						int azimuthal_errors_id = H5.H5Dopen(result_group_id, "azimuth_errors", HDF5Constants.H5P_DEFAULT);
						
						DataSliceIdentifiers azimuthal_ids = new DataSliceIdentifiers();
						azimuthal_ids.setIDs(result_group_id, azimuthal_data_id);
						DataSliceIdentifiers azimuthal_error_ids = new DataSliceIdentifiers();
						azimuthal_error_ids.setIDs(result_group_id, azimuthal_errors_id);
						
						AbstractDataset azData = NcdNexusUtils.sliceInputData(azSlice, azimuthal_ids);
						AbstractDataset azErrors = NcdNexusUtils.sliceInputData(azSlice, azimuthal_error_ids);
						
						for (int h = 0; h < shape[0]; h++)
						  for (int g = 0; g < shape[1]; g++)
							for (int k = 0; k < shape[2]; k++) {
								int[] startImage = new int[] {h, g, k, 0, 0};
								int[] stopImage = new int[] {h + 1, g + 1, k + 1, (int) imageShape[0], (int) imageShape[1]};
								AbstractDataset image = data.getSlice(startImage, stopImage, null).squeeze();
								AbstractDataset errimage = ((DoubleDataset) data.getErrorBuffer()).getSlice(startImage, stopImage, null).squeeze();
								image.setErrorBuffer(errimage);
								intSector.setAverageArea(true);
								AbstractDataset[] intResult = ROIProfile.sector(image, null, intSector, true, true, false, null, null, true);
								for (int i = 0; i < outData.getShape()[3]; i++) {
										float value = outData.getFloat(h, g, k, i);
										double error = outErrors.getDouble(h, g, k, i);
										float expected = intResult[0].getFloat(i);
										double expectederror = Math.sqrt(((AbstractDataset) intResult[0].getErrorBuffer()).getDouble(i));
										assertEquals(String.format("Test radial sector integration profile for frame (%d, %d, %d, %d)", h, g, k, i), expected, value, 1e-6*expected);
										assertEquals(String.format("Test radial sector integration profile error for frame (%d, %d, %d, %d)", h, g, k, i), expectederror, error, 1e-6*expectederror);
								}
								for (int i = 0; i < azData.getShape()[3]; i++) {
									float value = azData.getFloat(h, g, k, i);
									double error = azErrors.getDouble(h, g, k, i);
									float expected = intResult[1].getFloat(i);
									double expectederror = Math.sqrt(((AbstractDataset) intResult[1].getErrorBuffer()).getDouble(i));
									assertEquals(String.format("Test azimuthal sector integration profile for frame (%d, %d, %d, %d)", h, g, k, i), expected, value, 1e-6*expected);
									assertEquals(String.format("Test azimuthal sector integration profile error for frame (%d, %d, %d, %d)", h, g, k, i), expectederror, error, 1e-6*expectederror);
								}
							}
					}
				} catch (MessageException e) {
					throw new ProcessingException(ErrorCode.MSG_DELIVERY_FAILURE, "Error processing msg", this,
							message, e);
				} catch (HDF5LibraryException e) {
					throw new ProcessingException(ErrorCode.MSG_DELIVERY_FAILURE, "Error processing msg", this,
							message, e);
				} catch (HDF5Exception e) {
					throw new ProcessingException(ErrorCode.MSG_DELIVERY_FAILURE, "Error processing msg", this,
							message, e);
				}
			}
		}
	}

	@Test
	public void testNcdSectorIntegrationForkJoinTransformer() throws FlowAlreadyExecutingException, PasserelleException,
			IllegalActionException, NameDuplicationException {

		NcdMessageSource source = new NcdMessageSource(flow, "MessageSource");
		NcdSectorIntegrationForkJoinTransformer sectorIntegration = new NcdSectorIntegrationForkJoinTransformer(flow, "SectorIntegration");
		NcdMessageSink sink = new NcdMessageSink(flow, "MessageSink");

		flow.connect(source.output, sectorIntegration.input);
		flow.connect(sectorIntegration.output, sink.input);
		
		intSector = new SectorROI(0, 0, 0, imageShape[1], 0, 90);
		intSector.setClippingCompensation(true);
		intSector.setAverageArea(false);

		sectorIntegration.sectorROIParam.setRoi(intSector);
		
		Map<String, String> props = new HashMap<String, String>();
		props.put("SectorIntegration.enable", Boolean.toString(true));
		props.put("SectorIntegration.dimensionParam", Integer.toString(dim));
		props.put("SectorIntegration.doRadialParam", Boolean.toString(true));
		props.put("SectorIntegration.doAzimuthalParam", Boolean.toString(true));
		props.put("SectorIntegration.doFastParam", Boolean.toString(false));

		flowMgr.executeBlockingLocally(flow, props);
	}
	
	@AfterClass
	public static void removeTmpFiles() throws Exception {
		//Clear scratch directory 
		TestUtils.makeScratchDirectory(testScratchDirectoryName);
	}

}
