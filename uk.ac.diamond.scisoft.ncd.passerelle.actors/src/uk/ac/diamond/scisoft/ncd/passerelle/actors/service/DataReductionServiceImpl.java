/*
 * Copyright 2013 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.passerelle.actors.service;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.measure.quantity.Energy;
import javax.measure.quantity.Length;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import ncsa.hdf.hdf5lib.H5;
import ncsa.hdf.hdf5lib.HDF5Constants;
import ncsa.hdf.hdf5lib.HDFArray;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.dawb.hdf5.HierarchicalDataFactory;
import org.dawb.hdf5.IHierarchicalDataFile;
import org.dawnsci.plotting.tools.preference.detector.DiffractionDetector;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.jscience.physics.amount.Amount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Node;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.roi.SectorROI;
import uk.ac.diamond.scisoft.ncd.core.data.CalibrationResultsBean;
import uk.ac.diamond.scisoft.ncd.core.data.NcdDetectorSettings;
import uk.ac.diamond.scisoft.ncd.core.data.SliceInput;
import uk.ac.diamond.scisoft.ncd.core.preferences.NcdDetectors;
import uk.ac.diamond.scisoft.ncd.core.preferences.NcdReductionFlags;
import uk.ac.diamond.scisoft.ncd.core.service.IDataReductionContext;
import uk.ac.diamond.scisoft.ncd.core.service.IDataReductionProcess;
import uk.ac.diamond.scisoft.ncd.core.service.IDataReductionService;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;
import uk.ac.diamond.scisoft.ncd.passerelle.actors.NcdProcessingModel;
import uk.ac.diamond.scisoft.ncd.preferences.NcdMessages;

/**
 * Class to be available internally only. The IDataReductionService can be 
 * retrieved because it is an OSGI service.
 * @internal
 */
public class DataReductionServiceImpl implements IDataReductionService {

	private static final Logger logger = LoggerFactory.getLogger(DataReductionServiceImpl.class);
	
	private static final String NXEntryClassName = "NXentry";
	private static final String NXDataClassName  = "NXdata";	
	
	@Override
	public IDataReductionContext createContext() {
		DataReductionContext context    = new DataReductionContext();
		IDataReductionProcess processing   = new NcdProcessingModel();
		IDataReductionProcess bgProcessing = new NcdProcessingModel();
		NcdReductionFlags  flags        = new NcdReductionFlags();
		NcdDetectors       ncdDetectors = new NcdDetectors();
		context.setProcessing(processing);
		context.setBgProcessing(bgProcessing);
		context.setFlags(flags);
		context.setNcdDetectors(ncdDetectors);
		return context;
	}

	@Override
	public void configure(IDataReductionContext context) {
		
		IDataReductionProcess processing = context.getProcessing();
		IDataReductionProcess bgProcessing = context.getBgProcessing();
		NcdReductionFlags flags        = context.getFlags();
		NcdDetectors      ncdDetectors = context.getNcdDetectors();
		 
		checkStages(flags);
		readDetectorInformation(context,  flags, ncdDetectors);
		readDataReductionOptions(context, flags, processing);

		processing.setFlags(flags);

		if (flags.isEnableBackground()) {
			
			NcdReductionFlags bgFlags = new NcdReductionFlags(flags);
			NcdDetectors  bgDetectors = new NcdDetectors();

			checkStages(bgFlags);
			readDetectorInformation(context, bgFlags,  bgDetectors);
			readDataReductionOptions(context, bgFlags, bgProcessing);

			bgFlags.setEnableBackground(false);
			bgFlags.setEnableInvariant(false);
			bgFlags.setEnableLogLogPlot(false);
			bgFlags.setEnableGuinierPlot(false);
			bgFlags.setEnablePorodPlot(false);
			bgFlags.setEnableKratkyPlot(false);
			bgFlags.setEnableZimmPlot(false);
			bgFlags.setEnableDebyeBuechePlot(false);
			bgProcessing.setFlags(bgFlags);

			SliceInput bgSliceInput = context.getBgSliceInput();
			Integer bgFirstFrame    = bgSliceInput.getStartFrame();
			Integer bgLastFrame     = bgSliceInput.getStopFrame();
			String bgFrameSelection = bgSliceInput.getAdvancedSlice();

			bgProcessing.setFirstFrame(bgFirstFrame);
			bgProcessing.setLastFrame(bgLastFrame);
			bgProcessing.setFrameSelection(bgFrameSelection);
		}
				
		int idxMonitor = 0;
		if (context.isEnableWaxs()) idxMonitor += 6;
		if (context.isEnableSaxs()) idxMonitor += 6;
		context.setWorkAmount(idxMonitor);		
	}
	
	/**
	 * Execute the data processing we have configured. 
	 * @param rawFilePath
	 * @param context
	 */
	@Override
	public IStatus process(String rawFilePath, IDataReductionContext context, IProgressMonitor monitor) throws Exception {
		
	
		IDataReductionProcess processing = context.getProcessing();
		IDataReductionProcess bgProcessing = context.getBgProcessing();
		NcdReductionFlags flags        = context.getFlags();
		NcdDetectors      ncdDetectors = context.getNcdDetectors();

		if (processing==null || bgProcessing==null || flags==null || ncdDetectors==null) {
			throw new Exception("Please call configure before using execute!");
		}
		
		boolean enableWaxs = flags.isEnableWaxs();
		boolean enableSaxs = flags.isEnableSaxs();
		
		DiffractionDetector detectorWaxs = null;
		DiffractionDetector detectorSaxs = null;
		if (enableWaxs) {
			detectorWaxs = new DiffractionDetector();
			String detectorWaxsName = context.getWaxsDetectorName();
			Amount<Length> pxSize = ncdDetectors.getPxWaxs();
			detectorWaxs.setDetectorName(detectorWaxsName);
			detectorWaxs.setxPixelSize(null);
			detectorWaxs.setyPixelSize(pxSize);
		}
		if (enableSaxs) {
			detectorSaxs = new DiffractionDetector();
			String detectorSaxsName = context.getSaxsDetectorName();
			Amount<Length> pxSize = ncdDetectors.getPxSaxs();
			detectorSaxs.setDetectorName(detectorSaxsName);
			detectorSaxs.setxPixelSize(pxSize);
			detectorSaxs.setyPixelSize(pxSize);
		}
		
		final String bgPath = context.getBgPath();
		final String bgName = FilenameUtils.getName(bgPath);
		
		final String inputfileName, inputfileExtension, inputfilePath;

		inputfilePath      = rawFilePath;
		inputfileExtension = FilenameUtils.getExtension(inputfilePath);
		inputfileName      = FilenameUtils.getName(inputfilePath);

		// Process background
		IHierarchicalDataFile dawbReader = null;
		IHierarchicalDataFile dawbWriter = null;
		String bgFilename = null;
		
		if (context.isEnableBackground()) {  // Processes the background once after the configure and sets
			                                 // background processing disabled for future calls.
			try {
				dawbReader = HierarchicalDataFactory.getReader(context.getBgPath());
				bgFilename = createResultsFile(context, bgName, bgPath, "background");
				dawbWriter = HierarchicalDataFactory.getWriter(bgFilename);
				if (enableWaxs && bgFilename != null) {
						bgProcessing.setNcdDetector(detectorWaxs);
						bgProcessing.execute(bgFilename, monitor);
				}
				if (enableSaxs && bgFilename != null) {
						bgProcessing.setNcdDetector(detectorSaxs);
						bgProcessing.execute(bgFilename, monitor);
				}
				processing.setBgFile(bgFilename);

				context.setBgName(bgFilename);
				context.setEnableBackground(false); // We have done it now.
			} catch (Exception e) {
				logger.error("SCISOFT NCD: Error processing background file", e);
				throw e;
			} finally {
				try {
					if (dawbReader != null) {
						dawbReader.close();
					}
				} catch (Exception ex) {
					logger.error("SCISOFT NCD: Failed to close input background file", ex);
				} finally {
					try {
						if (dawbWriter != null) {
							dawbWriter.close();
						}
					} catch (Exception ex) {
						logger.error("SCISOFT NCD: Failed to close processing background file", ex);
					}
				}
			}
		}
	
		
		if (ignoreInputFile(context, inputfilePath, inputfileExtension)) {
			return new Status(IStatus.WARNING, "uk.ac.diamond.scisoft.ncd", "Input file '"+inputfilePath+" is invalid but other files may still be processed.");
		}
		logger.info("Processing: " + inputfileName);
		
		IHierarchicalDataFile dawbOutputReader = null;
		IHierarchicalDataFile dawbOutputWriter = null;
		IHierarchicalDataFile dawbBgWriter     = null;
		try {
			dawbOutputReader = HierarchicalDataFactory.getReader(inputfilePath);
			final String filename = createResultsFile(context, inputfileName, inputfilePath, "results");
			context.setResultsFile(filename);
			dawbOutputWriter = HierarchicalDataFactory.getWriter(filename);
			if (context.getBgName() != null) {
				dawbBgWriter = HierarchicalDataFactory.getWriter(context.getBgName());
			}

			if (enableWaxs && detectorWaxs != null) {
				processing.setBgDetector(detectorWaxs.getDetectorName()+"_result");
				processing.setNcdDetector(detectorWaxs);
				processing.execute(filename, monitor);
			}

			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}

			if (enableSaxs && detectorSaxs != null) {
				processing.setBgDetector(detectorSaxs.getDetectorName() + "_result");
				processing.setNcdDetector(detectorSaxs);
				processing.execute(filename, monitor);
			}

			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
		} catch (final Exception e) {
			logger.error("SCISOFT NCD: Error processing input file", e);
			throw e;
		} finally {
			try {
				if (dawbOutputReader != null) {
					dawbOutputReader.close();
				}
			} catch (Exception ex) {
				logger.error("SCISOFT NCD: Error closing input data file", ex);
			} finally {
				try {
					if (dawbOutputWriter != null) {
						dawbOutputWriter.close();
					}
				} catch (Exception ex) {
					logger.error("SCISOFT NCD: Error closing NCD data reduction result file", ex);
				} finally {
					try {
						if (dawbBgWriter != null) {
							dawbBgWriter.close();
						}
					} catch (Exception ex) {
						logger.error("SCISOFT NCD: Error closing background processing results file", ex);
					}
				}
			}
		}

		return Status.OK_STATUS; // Will then process next file, if required.
	}
	
	private String createResultsFile(IDataReductionContext context, String inputfileName, String inputfilePath, String prefix) {
		
		String datetime = generateDateTimeStamp();
		String detNames = "_" + ((context.isEnableWaxs()) ? context.getWaxsDetectorName() : "") + ((context.isEnableSaxs()) ? context.getSaxsDetectorName() : "") + "_";
		final String filename = context.getWorkingDir() + File.separator + prefix + "_" + FilenameUtils.getBaseName(inputfileName) + detNames + datetime + ".nxs";
		
		int fid = -1;
		int entry_id = -1;
		try {
			int fapl = H5.H5Pcreate(HDF5Constants.H5P_FILE_ACCESS);
			H5.H5Pset_fclose_degree(fapl, HDF5Constants.H5F_CLOSE_WEAK);
			fid = H5.H5Fcreate(filename, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, fapl);
			H5.H5Pclose(fapl);

			int[] libversion = new int[3];
			H5.H5get_libversion(libversion);
			putattr(fid, "HDF5_version", StringUtils.join(ArrayUtils.toObject(libversion), "."));
			putattr(fid, "file_name", filename);

			Date date = new Date();
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
			String dt = format.format(date);
			putattr(fid, "file_time", dt);

			entry_id = NcdNexusUtils.makegroup(fid, "entry1", NXEntryClassName);

			final String calibration = context.getCalibrationName();
			if (calibration != null) {
				int calib_id = NcdNexusUtils.makegroup(entry_id, calibration, NXDataClassName);
				H5.H5Lcreate_external(inputfilePath, "/entry1/" + calibration + "/data", calib_id, "data",
						HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
				H5.H5Gclose(calib_id);
			}

			writeNCDMetadata(entry_id, inputfilePath);

			if (context.isEnableWaxs()) {
				createDetectorNode(context.getWaxsDetectorName(), entry_id, inputfilePath);
			}

			if (context.isEnableSaxs()) {
				createDetectorNode(context.getSaxsDetectorName(), entry_id, inputfilePath);
			}

		} catch (HDF5Exception e) {
			throw new RuntimeException("Failed to create results NeXus file", e);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Invalind NeXus link path in the input data file", e);
		} finally {
			try {
				NcdNexusUtils.closeH5id(entry_id);
			} catch (Exception e2) {
				logger.error("Failed to close entry group in the NeXus results file", e2);
			} finally {
				try {
					NcdNexusUtils.closeH5id(fid);
				} catch (Exception e3) {
					logger.error("Failed to close reference id of the NeXus results file", e3);
				}
			}
		}
		return filename;
	}


	private void writeNCDMetadata(int entry_id, String inputfilePath) {
		try {
			HDF5File inputFileTree = new HDF5Loader(inputfilePath).loadTree();

			writeStringMetadata("/entry1/entry_identifier", "entry_identifier", entry_id, inputFileTree);
			writeStringMetadata("/entry1/scan_command", "scan_command", entry_id, inputFileTree);
			writeStringMetadata("/entry1/scan_identifier", "scan_identifier", entry_id, inputFileTree);
			writeStringMetadata("/entry1/title", "title", entry_id, inputFileTree);
		} catch (Exception e) {
			logger.warn("Couldn't open scan data file. Scan metadata won't be written into NCD processing results file",e);
		}
	}
	
	private void writeStringMetadata(String nodeName, String textName, int entry_id, HDF5File inputFileTree) throws HDF5Exception {
		
		HDF5Node node;
		HDF5NodeLink nodeLink;
		nodeLink = inputFileTree.findNodeLink(nodeName);
		if (nodeLink != null) {
			node = nodeLink.getDestination();
			if (node instanceof HDF5Dataset) {
				String text = ((AbstractDataset) ((HDF5Dataset) node).getDataset()).getString(0);
				
				int text_type = H5.H5Tcopy(HDF5Constants.H5T_C_S1);
				H5.H5Tset_size(text_type, text.length());
				int text_id = NcdNexusUtils.makedata(entry_id, textName, text_type, new long[] {1});
				int filespace_id = H5.H5Dget_space(text_id);
				int memspace_id = H5.H5Screate_simple(1, new long[] {1}, null);
				H5.H5Sselect_all(filespace_id);
				H5.H5Dwrite(text_id, text_type, memspace_id, filespace_id, HDF5Constants.H5P_DEFAULT, text.getBytes());
					
				H5.H5Sclose(filespace_id);
				H5.H5Sclose(memspace_id);
				H5.H5Tclose(text_type);
				H5.H5Dclose(text_id);
			}
		}
	}

	private void createDetectorNode(String detector, int entry_id, String inputfilePath) throws HDF5Exception, URISyntaxException {
		
		int detector_id = NcdNexusUtils.makegroup(entry_id, detector, NXDataClassName);
		
		int file_handle = H5.H5Fopen(inputfilePath, HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		int entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		int detector_group_id = H5.H5Gopen(entry_group_id, detector, HDF5Constants.H5P_DEFAULT);
		int input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);
		
		boolean isNAPImount = H5.H5Aexists(input_data_id, "napimount");
		if (isNAPImount) {
			int attr_id = H5.H5Aopen(input_data_id, "napimount", HDF5Constants.H5P_DEFAULT);
			int type_id = H5.H5Aget_type(attr_id);
			int size = H5.H5Tget_size(type_id);
			byte[] link = new byte[size];
			H5.H5Aread(attr_id, type_id, link);
			
			String str = new String(link);
			final URI ulink = new URI(str);
			if (ulink.getScheme().equals("nxfile")) {
				String lpath = ulink.getPath();
				String ltarget = ulink.getFragment();
				File f = new File(lpath);
				if (!f.exists()) {
					logger.debug("SCISOFT NCD: Linked file, {}, does not exist!", lpath);

					// see if linked file in same directory
					File file = new File(inputfilePath);
					f = new File(file.getParent(), f.getName());
					if (!f.exists()) {
						H5.H5Tclose(type_id);
						H5.H5Aclose(attr_id);
						
						H5.H5Gclose(detector_id);
						
						H5.H5Dclose(input_data_id);
						H5.H5Gclose(detector_group_id);
						H5.H5Gclose(entry_group_id);
						H5.H5Fclose(file_handle);
						
						throw new HDF5Exception("File, " + lpath + ", does not exist");
					}
				}
				lpath = f.getAbsolutePath();
				H5.H5Lcreate_external(lpath, ltarget, detector_id, "data", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
			} else {
				logger.error("SCISOFT NCD: Linked file has incompatible type: " + ulink.getScheme());
			}
			H5.H5Tclose(type_id);
			H5.H5Aclose(attr_id);
		} else {
		    H5.H5Lcreate_external(inputfilePath, "/entry1/" + detector + "/data", detector_id, "data", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		}
		
		boolean exists = H5.H5Lexists(detector_group_id, "errors", HDF5Constants.H5P_DEFAULT);
		if (exists) {
		    H5.H5Lcreate_external(inputfilePath, "/entry1/" + detector + "/errors", detector_id, "errors", HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT);
		}
		
		H5.H5Gclose(detector_id);
		
		H5.H5Dclose(input_data_id);
		H5.H5Gclose(detector_group_id);
		H5.H5Gclose(entry_group_id);
		H5.H5Fclose(file_handle);
	}
	
	private void putattr(int dataset_id, String name, Object value) throws HDF5Exception {
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
	
	private boolean ignoreInputFile(IDataReductionContext context, String inputfilePath, String inputfileExtension) {
		if (!inputfileExtension.equals("nxs")) {
			return true;
		}
		HDF5File dataTree;
		try {
			dataTree = new HDF5Loader(inputfilePath).loadTree();
		} catch (Exception e) {
			logger.info("Error loading Nexus tree from {}", inputfilePath);
			return true;
		}
		if (context.isEnableWaxs()) {
			HDF5NodeLink node = dataTree.findNodeLink("/entry1/"+context.getWaxsDetectorName()+"/data");
			if (node == null) {
				return true;
			}
		}
		
		if (context.isEnableSaxs()) {
			HDF5NodeLink node = dataTree.findNodeLink("/entry1/"+context.getSaxsDetectorName()+"/data");
			if (node == null) {
				return true;
			}
		}
		
		return false;
	}

	
	private void readDataReductionOptions(IDataReductionContext context, NcdReductionFlags flags, IDataReductionProcess processing) {

		String workingDir = context.getWorkingDir();
		if (workingDir == null || workingDir.isEmpty()) {
			throw new IllegalArgumentException(NcdMessages.NO_WORKING_DIR);
		}
		File testDir = new File(workingDir);
		if (!(testDir.isDirectory())) {
			throw new IllegalArgumentException(NLS.bind(NcdMessages.NO_WORKINGDIR_DATA, testDir.getAbsolutePath()));
		}
		if (!(testDir.canWrite())) {
			throw new IllegalArgumentException(NcdMessages.NO_WORKINGDIR_WRITE);
		}
		
		SliceInput dataSliceInput = context.getDataSliceInput();
		Integer firstFrame = null;
		Integer lastFrame = null;
		String frameSelection = null;
		if (dataSliceInput != null) {
			firstFrame = dataSliceInput.getStartFrame();
			lastFrame = dataSliceInput.getStopFrame();
			frameSelection = dataSliceInput.getAdvancedSlice();
		}
		
		SliceInput gridAverageSlice = context.getGridAverageSlice();
		String gridAverage = null;
		if (gridAverageSlice != null) {
			gridAverage = gridAverageSlice.getAdvancedSlice();
		}
		
		String bgFile = null;
		Double bgScaling = null;
		if (flags.isEnableBackground()) {
			bgFile = context.getBgPath();
			bgScaling = context.getBgScaling();
			
			if (bgFile == null) {
				throw new IllegalArgumentException(NcdMessages.NO_BG_FILE);
			}
			File testFile = new File(bgFile);
			if (!(testFile.isFile())) {
				throw new IllegalArgumentException(NLS.bind(NcdMessages.NO_BG_DATA, testFile.getAbsolutePath()));
			}
			if (!(testFile.canRead())) {
				throw new IllegalArgumentException(NLS.bind(NcdMessages.NO_BG_READ, testFile.getAbsolutePath()));
			}
		}

		String drFile = null;
		if (flags.isEnableDetectorResponse()) {
			drFile = context.getDrFile();
			
			if (drFile == null) {
				throw new IllegalArgumentException(NcdMessages.NO_DR_FILE);
			}
			File testFile = new File(drFile);
			if (!(testFile.isFile())) {
				throw new IllegalArgumentException(NLS.bind(NcdMessages.NO_DR_DATA, testFile.getAbsolutePath()));
			}
			if (!(testFile.canRead())) {
				throw new IllegalArgumentException(NLS.bind(NcdMessages.NO_DR_READ, testFile.getAbsolutePath()));
			}
		}
		
		int normChannel = -1;
		Double absScaling = null;
		Double thickness = null;
		if (flags.isEnableNormalisation()) {
	   	    absScaling = context.getAbsScaling();
			thickness = context.getSampleThickness();
			NcdDetectorSettings scalerData = context.getScalerData();
			if (scalerData != null) {
				normChannel = scalerData.getNormChannel();
			}
		}
		
		if (context.isEnableMask()) {
			BooleanDataset mask = context.getMask();
			if (mask != null) {
				processing.setMask(new BooleanDataset(mask));
				processing.setEnableMask(true);
			} else {
				throw new IllegalArgumentException(NcdMessages.NO_MASK_IMAGE);
			}
		}
		
		// Must have a sector.
		if (flags.isEnableSector()) {
			final SectorROI sector = context.getSector();
			if (sector == null) throw new IllegalArgumentException(NcdMessages.NO_SEC_DATA);
			int sym = sector.getSymmetry(); 
			SectorROI tmpSector = sector.copy();
			if ((sym != SectorROI.NONE) && (sym != SectorROI.FULL)) {
				tmpSector.setCombineSymmetry(true);
			}
			processing.setIntSector(tmpSector);
		}

		CalibrationResultsBean crb = context.getCalibrationResults();
		processing.setCrb(crb);
		Double valEnergy = context.getEnergy();
		if (valEnergy != null) {
			Amount<Energy> energy = Amount.valueOf(context.getEnergy(), SI.KILO(NonSI.ELECTRON_VOLT));
			processing.setEnergy(energy);
		}
		
		
		processing.setBgFile(bgFile);
		if (absScaling != null) {
			if (thickness != null) {
				processing.setAbsScaling(absScaling / thickness);
			} else {
				processing.setAbsScaling(absScaling);
			}
		}
		processing.setBgScaling(bgScaling);
		processing.setDrFile(drFile);
		processing.setFirstFrame(firstFrame);
		processing.setLastFrame(lastFrame);
		processing.setFrameSelection(frameSelection);
		processing.setGridAverageSelection(gridAverage);
		processing.setCalibration(context.getCalibrationName());
		processing.setNormChannel(normChannel);
		
		processing.setSaxsAnalysisStatsParameters(context.getSaxsAnalysisStatParameters());
	}

	
	private void readDetectorInformation(IDataReductionContext context, final NcdReductionFlags flags, NcdDetectors ncdDetectors) {
		
		String detectorWaxs = null;
		NcdDetectorSettings detWaxsInfo = null;
		Amount<Length> pxWaxs = null;
		Integer dimWaxs = null ;
		
		String detectorSaxs = null;
		NcdDetectorSettings detSaxsInfo = null;
		Amount<Length> pxSaxs = null;		
		Integer dimSaxs = null ;
		
		
		if (flags.isEnableWaxs()) {
			detectorWaxs = context.getWaxsDetectorName();
			detWaxsInfo = context.getDetWaxsInfo();
			if (detWaxsInfo != null) {
				pxWaxs = detWaxsInfo.getPxSize();
				dimWaxs = detWaxsInfo.getDimension();
				if ((detectorWaxs != null) && (pxWaxs != null)) {
					ncdDetectors.setDetectorWaxs(detectorWaxs);
					ncdDetectors.setPxWaxs(pxWaxs);
					ncdDetectors.setDimWaxs(dimWaxs);
				}
			}
		} 
		
		if (flags.isEnableSaxs()) {
			detectorSaxs = context.getSaxsDetectorName();
			detSaxsInfo = context.getDetSaxsInfo();
			if (detSaxsInfo != null) {
				pxSaxs = detSaxsInfo.getPxSize();
				dimSaxs = detSaxsInfo.getDimension();
				if ((detectorSaxs != null) && (pxSaxs != null)) {
					ncdDetectors.setDetectorSaxs(detectorSaxs);
					ncdDetectors.setPxSaxs(pxSaxs);
					ncdDetectors.setDimSaxs(dimSaxs);
				}
			}
		}
		
		if (flags.isEnableWaxs()) {
			if (detectorWaxs == null || detWaxsInfo == null) {
				throw new IllegalArgumentException(NcdMessages.NO_WAXS_DETECTOR);
			}
			if (pxWaxs == null) {
				throw new IllegalArgumentException(NcdMessages.NO_WAXS_PIXEL);
			}
			if (dimWaxs == null) {
				throw new IllegalArgumentException(NcdMessages.NO_WAXS_DIM);
			}
		}
		
		if (flags.isEnableSaxs()) {
			if (detectorSaxs == null || detSaxsInfo == null) {
				throw new IllegalArgumentException(NcdMessages.NO_SAXS_DETECTOR);
			}
			if (pxSaxs == null) {
				throw new IllegalArgumentException(NcdMessages.NO_SAXS_PIXEL);
			}
			if (dimSaxs == null) {
				throw new IllegalArgumentException(NcdMessages.NO_SAXS_DIM);
			}
		}
		
	}


	private void checkStages(NcdReductionFlags flags) {
		
		if (flags.isEnableSector()) {
			if (!flags.isEnableRadial() && !flags.isEnableAzimuthal()) {
				throw new IllegalArgumentException(NcdMessages.NO_SEC_INT);
			}
		}
	}

	
	private String generateDateTimeStamp() {

		Date date = new Date();

		SimpleDateFormat format =
			new SimpleDateFormat("ddMMyy_HHmmss");

		return format.format(date);
	}

}
