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

package uk.ac.diamond.scisoft.ncd.actors.test;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;

import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.BeforeClass;
import org.junit.Test;

import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import uk.ac.diamond.scisoft.analysis.TestUtils;
import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.ncd.actors.NcdNormalisationTransformer;
import uk.ac.diamond.scisoft.ncd.actors.NcdProcessingObject;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.data.SliceSettings;
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

public class NcdNormalisationTransformerTest {

	private static final String NXEntryClassName = "NXentry";
	private static final String NXInstrumentClassName = "NXinstrument";
	private static final String NXDataClassName = "NXdata";
	private static String testScratchDirectoryName;
	private static String filename;
	private static String testDatasetName = "testInput";
	private static String testNormName = "testNorm";

	private ILock lock = Job.getJobManager().newLock();

	private static AbstractDataset data;
	private static long[] shape = new long[] { 5, 3, 91, 32, 64 };
	private static long[] normShape = new long[] { shape[0], shape[1], shape[2], 1 };
	private static long[] imageShape = new long[] { shape[3], shape[4] };
	private static float scale = 1.0f;
	private static float absScale = 100.0f;
	private static int normChannel = 0;
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

		testScratchDirectoryName = TestUtils.generateDirectorynameFromClassname(
				NcdNormalisationTransformerTest.class.getCanonicalName());
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
			int normgroup_id = NcdNexusUtils.makegroup(entry_id, testNormName, NXDataClassName);
			int data_id = NcdNexusUtils.makedata(datagroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, shape, true,
					"counts");
			int errors_id = NcdNexusUtils.makedata(datagroup_id, "errors", HDF5Constants.H5T_NATIVE_DOUBLE, shape,
					true, "counts");
			int norm_id = NcdNexusUtils.makedata(normgroup_id, "data", HDF5Constants.H5T_NATIVE_FLOAT, normShape, true,
					"counts");

			for (int m = 0; m < shape[0]; m++)
				for (int n = 0; n < shape[1]; n++) {
					for (int frames = 0; frames < shape[2]; frames++) {
						float[] norm = new float[] { scale * (n + 1) };
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
						{
							long[] start = new long[] { m, n, frames, 0 };
							long[] count = new long[] { 1, 1, 1, 1 };
							long[] block = new long[] { 1, 1, 1, 1 };
							int filespace_id = H5.H5Dget_space(norm_id);
							int type_id = H5.H5Dget_type(norm_id);
							int memspace_id = H5.H5Screate_simple(1, new long[] { 1 }, null);
							H5.H5Sselect_hyperslab(filespace_id, HDF5Constants.H5S_SELECT_SET, start, block, count,
									block);
							H5.H5Dwrite(norm_id, type_id, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, norm);
							H5.H5Sclose(memspace_id);
							H5.H5Sclose(filespace_id);
							H5.H5Tclose(type_id);
						}
					}
				}
			H5.H5Dclose(data_id);
			H5.H5Dclose(errors_id);
			H5.H5Gclose(datagroup_id);
			H5.H5Dclose(norm_id);
			H5.H5Gclose(normgroup_id);
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

	class NcdMessageSource extends Source {

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
				SliceSettings slice = new SliceSettings(shape, 0, (int) shape[0]);
				slice.setStart(new int[] { 0, 0, 0, 0, 0 });

				NcdProcessingObject obj = new NcdProcessingObject(data, slice, lock);

				dataMsg = createMessage(obj, "application/octet-stream");
			} catch (Exception e) {
				messageSent = false;
				throw new ProcessingException(ErrorCode.MSG_CONSTRUCTION_ERROR, "Error creating msg", this, e);
			}
			messageSent = true;
			return dataMsg;
		}

	}

	class NcdMessageSink extends Sink {

		private static final long serialVersionUID = 79096075353029810L;

		public NcdMessageSink(CompositeEntity container, String name) throws NameDuplicationException,
				IllegalActionException {
			super(container, name);
		}

		@Override
		protected void sendMessage(ManagedMessage message) throws ProcessingException {
			if (message != null) {
				NcdProcessingObject content = null;
				try {
					Object obj = message.getBodyContent();
					if (obj instanceof NcdProcessingObject) {
						content = (NcdProcessingObject) obj;
						AbstractDataset outData = content.getData();
						AbstractDataset outErrors = outData.getError();
						for (int h = 0; h < shape[0]; h++) {
							for (int g = 0; g < shape[1]; g++) {
								for (int k = 0; k < shape[2]; k++) {
									for (int i = 0; i < imageShape[0]; i++) {
										for (int j = 0; j < imageShape[1]; j++) {
											float value = outData.getFloat(h, g, k, i, j);
											double error = outErrors.getDouble(h, g, k, i, j);
											float expected = absScale * (g * shape[2] + k + i * imageShape[1] + j)
													/ (scale * (g + 1));
											double expectederror = absScale
													* (Math.sqrt((g * shape[2] + k + i * imageShape[1] + j)) / (scale * (g + 1)));

											assertEquals(
													String.format("Test normalisation frame for (%d, %d, %d, %d, %d)",
															h, g, k, i, j), expected, value, 1e-6 * expected);
											assertEquals(String.format(
													"Test normalisation frame error for (%d, %d, %d, %d, %d)", h, g, k,
													i, j), expectederror, error, 1e-6 * expectederror);
										}
									}
								}
							}
						}
					}
				} catch (MessageException e) {
					throw new ProcessingException(ErrorCode.MSG_DELIVERY_FAILURE, "Error processing msg", this,
							message, e);
				}
			}
		}
	}

	@Test
	public void testNcdNormalisationTransformer() throws FlowAlreadyExecutingException, PasserelleException,
			IllegalActionException, NameDuplicationException, HDF5LibraryException, NullPointerException {

		NcdMessageSource source = new NcdMessageSource(flow, "MessageSource");
		NcdNormalisationTransformer normalisation = new NcdNormalisationTransformer(flow, "Normalisation");
		NcdMessageSink sink = new NcdMessageSink(flow, "MessageSink");

		flow.connect(source.output, normalisation.input);
		flow.connect(normalisation.output, sink.input);

		int nxsFile = H5.H5Fopen(filename, HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT);
		int entry_id = H5.H5Gopen(nxsFile, "entry1", HDF5Constants.H5P_DEFAULT);
		int processing_group_id = H5.H5Gopen(entry_id, "results", HDF5Constants.H5P_DEFAULT);

		Map<String, String> props = new HashMap<String, String>();

		props.put("Normalisation.calibrationParam", testNormName);
		props.put("Normalisation.absScalingParam", Float.toString(absScale));
		props.put("Normalisation.normChannelParam", Integer.toString(normChannel));
		props.put("Normalisation.dimensionParam", Integer.toString(dim));
		props.put("Normalisation.framesParam", Arrays.toString(shape));
		props.put("Normalisation.entryGroupParam", Integer.toString(entry_id));
		props.put("Normalisation.processingGroupParam", Integer.toString(processing_group_id));

		flowMgr.executeBlockingLocally(flow, props);
	}

}
