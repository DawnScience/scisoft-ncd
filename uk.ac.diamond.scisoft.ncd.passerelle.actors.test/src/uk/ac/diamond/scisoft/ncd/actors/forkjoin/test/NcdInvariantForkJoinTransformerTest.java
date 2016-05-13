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

import org.apache.commons.lang.StringUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.hdf5.HDF5Utils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import ptolemy.data.ObjectToken;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.IOTestUtils;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdMessageSource;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.core.NcdProcessingObject;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.forkjoin.NcdInvariantForkJoinTransformer;

import com.isencia.passerelle.actor.ProcessingException;
import com.isencia.passerelle.actor.Sink;
import com.isencia.passerelle.core.ErrorCode;
import com.isencia.passerelle.core.PasserelleException;
import com.isencia.passerelle.domain.et.ETDirector;
import com.isencia.passerelle.message.ManagedMessage;
import com.isencia.passerelle.message.MessageException;
import com.isencia.passerelle.model.Flow;
import com.isencia.passerelle.model.FlowAlreadyExecutingException;
import com.isencia.passerelle.model.FlowManager;

public class NcdInvariantForkJoinTransformerTest {

	private static final String NXEntryClassName = "NXentry";
	private static final String NXInstrumentClassName = "NXinstrument";
	private static final String NXDataClassName = "NXdata";
	private static String testScratchDirectoryName;
	private static String filename;
	private static String testDatasetName = "testInput";

	private ReentrantLock lock = new ReentrantLock();

	private static Dataset data;
	private static long[] shape = new long[] { 5, 3, 91, 32, 64 };
	private static long[] invShape = new long[] {shape[0], shape[1], shape[2]};
	private static long[] imageShape = new long[] { shape[3], shape[4] };
	private static int dim = 2;
	private static int points = 1;

	private static Flow flow;
	private static FlowManager flowMgr;

	@BeforeClass
	public static void setUp() throws Exception {
		flow = new Flow("unit test", null);
		flowMgr = new FlowManager();
		ETDirector director = new ETDirector(flow, "director");
		flow.setDirector(director);

		testScratchDirectoryName = IOTestUtils.generateDirectorynameFromClassname(
				NcdInvariantForkJoinTransformerTest.class.getCanonicalName());
		IOTestUtils.makeScratchDirectory(testScratchDirectoryName);

		for (long n : imageShape)
			points *= n;

		{
			filename = testScratchDirectoryName + "ncd_sda_test.nxs";

			long nxsFile = H5.H5Fcreate(filename, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT,
					HDF5Constants.H5P_DEFAULT);
			long entry_id = NcdNexusUtils.makegroup(nxsFile, "entry1", NXEntryClassName);
			NcdNexusUtils.makegroup(entry_id, "results", NXInstrumentClassName);
			long datagroup_id = NcdNexusUtils.makegroup(entry_id, testDatasetName, NXDataClassName);
			long data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, shape, true,
					"counts");
			long errors_id = NcdNexusUtils.makedata(datagroup_id, "errors", HDF5Constants.H5T_NATIVE_DOUBLE, shape,
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
							long filespace_id = H5.H5Dget_space(data_id);
							long type_id = H5.H5Dget_type(data_id);
							long memspace_id = H5.H5Screate_simple(dim, imageShape, null);
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

		long file_handle = HDF5Utils.H5Fopen(filename, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		long entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		long detector_group_id = H5.H5Gopen(entry_group_id, testDatasetName, HDF5Constants.H5P_DEFAULT);
		long input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		long input_errors_id = H5.H5Dopen(detector_group_id, "errors", HDF5Constants.H5P_DEFAULT);

		DataSliceIdentifiers data_id = new DataSliceIdentifiers();
		data_id.setIDs(detector_group_id, input_data_id);
		DataSliceIdentifiers errors_id = null;
		errors_id = new DataSliceIdentifiers();
		errors_id.setIDs(detector_group_id, input_errors_id);

		SliceSettings dataSlice = new SliceSettings(shape, 0, (int) shape[0]);
		int[] start = new int[] { 0, 0, 0, 0, 0 };
		dataSlice.setStart(start);
		data = NcdNexusUtils.sliceInputData(dataSlice, data_id);
		Dataset error = NcdNexusUtils.sliceInputData(dataSlice, errors_id);
		data.setError(error);
	}

	private class NcdMessageSink extends Sink {

		public NcdMessageSink(CompositeEntity container, String name) throws NameDuplicationException,
				IllegalActionException {
			super(container, name);
		}

		@Override
		protected void sendMessage(ManagedMessage message) throws ProcessingException {
			if (message != null) {
				try {
					SliceSettings slice = new SliceSettings(invShape, 0, (int) invShape[0]);
					slice.setStart(new int[] {0, 0, 0});

					Object obj = message.getBodyContent();
					if (obj instanceof NcdProcessingObject) {
						NcdProcessingObject content = (NcdProcessingObject) obj;
						long result_group_id = content.getInputGroupID();
						long result_data_id = content.getInputDataID();
						long result_errors_id = content.getInputErrorsID();
						
						DataSliceIdentifiers result_ids = new DataSliceIdentifiers();
						result_ids.setIDs(result_group_id, result_data_id);
						DataSliceIdentifiers result_error_ids = new DataSliceIdentifiers();
						result_error_ids.setIDs(result_group_id, result_errors_id);
						Dataset outData = NcdNexusUtils.sliceInputData(slice, result_ids);
						Dataset outErrors = NcdNexusUtils.sliceInputData(slice, result_error_ids);
						for (int h = 0; h < invShape[0]; h++) {
						  for (int g = 0; g < invShape[1]; g++) {
							for (int k = 0; k < invShape[2]; k++) {
								float value = outData.getFloat(h, g, k);
								double error = outErrors.getDouble(h, g, k);
								float expected = 0.0f;
								double expectederror = 0.0;
								for (int i = 0; i < imageShape[0]; i++) {
									for (int j = 0; j < imageShape[1]; j++) { 
										expected += g*shape[2] + k + i*imageShape[1] + j;
										expectederror += g*shape[2] + k + i*imageShape[1] + j;
									}
								}
								expectederror = Math.sqrt(expectederror);
								assertEquals(String.format("Test invariant result for (%d, %d, %d)", h, g, k), expected, value, 1e-6*expected);
								assertEquals(String.format("Test invariant error result for (%d, %d, %d)", h, g, k), expectederror, error, 1e-6*expectederror);
							}
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
	public void testNcdInvariantForkJoinTransformer() throws FlowAlreadyExecutingException, PasserelleException,
			IllegalActionException, NameDuplicationException {

		NcdMessageSource source = new NcdMessageSource(flow, "MessageSource");
		NcdInvariantForkJoinTransformer invariant = new NcdInvariantForkJoinTransformer(flow, "Invariant");
		NcdMessageSink sink = new NcdMessageSink(flow, "MessageSink");

		flow.connect(source.output, invariant.input);
		flow.connect(invariant.output, sink.input);

		source.lockParam.setToken(new ObjectToken(lock));
		
		Map<String, String> props = new HashMap<String, String>();

		props.put("MessageSource.filenameParam", filename);
		props.put("MessageSource.detectorParam", testDatasetName);
		props.put("MessageSource.dimensionParam", Integer.toString(dim));
		String processingName = StringUtils.join(new String[] {testDatasetName, "processing"},  "_");
		props.put("MessageSource.processingParam", processingName);

		flowMgr.executeBlockingLocally(flow, props);
	}
	
	@AfterClass
	public static void removeTmpFiles() throws Exception {
		//Clear scratch directory 
		IOTestUtils.makeScratchDirectory(testScratchDirectoryName);
	}

}
