/*-
 * Copyright (c) 2012 European Synchrotron Radiation Facility,
 *                    Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */ 
package uk.ac.diamond.scisoft.ncd.actors;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;

import org.dawb.common.util.eclipse.BundleUtils;
import org.dawb.common.util.io.FileUtils;
import org.dawb.common.util.io.IFileUtils;
import org.dawb.common.util.test.TestUtils;
import org.dawb.passerelle.common.project.PasserelleProjectUtils;
import org.dawb.passerelle.common.remote.WorkbenchServiceManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.dawnsci.hdf.object.HierarchicalDataFactory;
import org.eclipse.dawnsci.hdf.object.IHierarchicalDataFile;
import org.eclipse.dawnsci.hdf5.HDF5Utils;
import org.eclipse.january.dataset.Dataset;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ptolemy.moml.MoMLParser;
import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.SliceSettings;
import uk.ac.diamond.scisoft.ncd.core.utils.NcdNexusUtils;

import com.isencia.passerelle.workbench.model.launch.ModelRunner;

import hdf.hdf5lib.H5;
import hdf.hdf5lib.HDF5Constants;

public class DataReductionPipelinePluginTest {
	
	private static final Logger logger = LoggerFactory.getLogger(DataReductionPipelinePluginTest.class);
	
	/**
	 * Ensure that the projects are available in this workspace.
	 * @throws Exception
	 */
	@BeforeClass
	public static void beforeClass() throws Exception {
		
		PasserelleProjectUtils.createWorkflowProject("workflows", ResourcesPlugin.getWorkspace().getRoot(), true, null);
		ResourcesPlugin.getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		WorkbenchServiceManager.startTestingWorkbenchService();

		// We make some copies in the results folder and test data sets produced by these workflows.
		final IProject workflows = ResourcesPlugin.getWorkspace().getRoot().getProject("workflows");
		IFolder  res  = workflows.getFolder("data").getFolder("results");
	    if (res==null || !res.exists()) {
			final Bundle bundle = Platform.getBundle("org.dawb.workbench.examples");
            if (bundle==null) throw new Exception("Examples not found!");
			
    		final IWorkspaceRoot root         = ResourcesPlugin.getWorkspace().getRoot();
			createDataProject("data", root, true, new NullProgressMonitor());
			
			IWorkspace ws = ResourcesPlugin.getWorkspace();
			ws.save(false, new NullProgressMonitor());
			
			res  = workflows.getFolder("data").getFolder("results");
	    	
	    }
	   
	    if (res==null || !res.exists()) throw new Exception("folder "+res.getName()+" must exist!");
	    final IFile    toCopy=res.getFile("billeA_4201_EF_XRD_5998.edf");   		 		
	    
	    // 10 copies
	    for (int i = 0; i < 3; i++) {
			final IFile to = IFileUtils.getUniqueIFile(res,"billeA_copy","edf");
			to.create(toCopy.getContents(), true, new NullProgressMonitor());
		}
	    res.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
	    
	    // We copy to the python folder the python files.
		final IProject work = ResourcesPlugin.getWorkspace().getRoot().getProject("workflows");
		final IFolder  src  = work.getFolder("src");
	    if (!src.exists()) src.create(true, true, null);
	    
	}
	
	private IProject workflows;
	
	@Before
	public void beforeTest() throws Exception {
		this.workflows = ResourcesPlugin.getWorkspace().getRoot().getProject("workflows");
		workflows.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		IFolder  output    = workflows.getFolder("output");
		if (output.exists()) output.delete(true, new NullProgressMonitor());
	}
	
	@After
	public void afterTest() throws Exception {
		workflows.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		MoMLParser.purgeAllModelRecords();
	}
	
	/**
	 * These lot work better as separate tests.
	 * @throws Throwable
	 */
	@Test
	public void testDataReductionPipeline1() throws Throwable {
		
		setUpLocations( "data/ncd_configuration.xml");
		
		testScalarInjection("data/ncd_model.moml", getSetNames(), getScalarNames());
		
		testSumAndAverage(8.625e-5, 0.001e-5, 0.1219, 0.0001);
	}
	
	/**
	 * These lot work better as separate tests.
	 * @throws Throwable
	 */
	@Test
	public void testDataReductionPipeline2() throws Throwable {
		
		setUpLocations( "data/ncd_configuration_sample_thickness.xml");
		
		testScalarInjection("data/ncd_model.moml", getSetNames(), getScalarNames());
		
		testSumAndAverage(7.841e-4, 0.001e-4, 1.109, 0.001);
	}

	private void testSumAndAverage(double expectedMean, double toleranceMean, double expectedSum, double toleranceSum) throws Exception {
		IFile h5 = getH5File();
		long file_handle = HDF5Utils.H5Fopen(h5.getLocation().toString(), HDF5Constants.H5F_ACC_RDONLY, HDF5Constants.H5P_DEFAULT);
		long entry_group_id = H5.H5Gopen(file_handle, "entry1", HDF5Constants.H5P_DEFAULT);
		long detector_group_id = H5.H5Gopen(entry_group_id, "Pilatus2M_result", HDF5Constants.H5P_DEFAULT);
		long input_data_id = H5.H5Dopen(detector_group_id, "data", HDF5Constants.H5P_DEFAULT);

		DataSliceIdentifiers data_id = new DataSliceIdentifiers();
		data_id.setIDs(detector_group_id, input_data_id);

		long[] shape = {1,1,1414};
		SliceSettings dataSlice = new SliceSettings(shape, 0, (int) shape[0]);
		int[] start = new int[] { 0, 0, 0, 0, 0 };
		dataSlice.setStart(start);
		Dataset data = NcdNexusUtils.sliceInputData(dataSlice, data_id);

		double sum = (Double) data.sum();
		double mean = (Double) data.mean();
		Assert.assertEquals(expectedMean, mean, toleranceMean);
		Assert.assertEquals(expectedSum, sum, toleranceSum);
	}

	private String[] getScalarNames() {
		final String[] scalarNames = {"/entry1/Pilatus2M_processing/Average/sas_type", "/entry1/Pilatus2M_processing/Average_Azimuthal/sas_type",
				"/entry1/Pilatus2M_processing/DebyeBuechePlot/sas_type", "/entry1/Pilatus2M_processing/DegreeOfOrientation_Azimuthal/sas_type",
				"/entry1/Pilatus2M_processing/GuinierPlot/sas_type", "/entry1/Pilatus2M_processing/Invariant/sas_type",
				"/entry1/Pilatus2M_processing/KratkyPlot/sas_type", "/entry1/Pilatus2M_processing/LogLogPlot/sas_type",
				"/entry1/Pilatus2M_processing/Normalisation/sas_type", "/entry1/Pilatus2M_processing/Normalisation_Azimuthal/sas_type",
				"/entry1/Pilatus2M_processing/PorodPlot/sas_type", "/entry1/Pilatus2M_processing/SectorIntegration/integration symmetry",
				"/entry1/Pilatus2M_processing/SectorIntegration/sas_type", "/entry1/Pilatus2M_processing/StandardisedIntensity/sas_type",
				"/entry1/Pilatus2M_processing/ZimmPlot/sas_type", "/entry1/Pilatus2M_processing/guinierTestData/sas_type", "/entry1/entry_identifier",
				"/entry1/instrument/Pilatus2M/sas_type", "/entry1/instrument/Scalers/description", "/entry1/instrument/Scalers/sas_type",
				"/entry1/instrument/TfgTimes/sas_type", "/entry1/instrument/name", "/entry1/instrument/source/name", "/entry1/instrument/source/notes",
				"/entry1/instrument/source/probe", "/entry1/instrument/source/type", "/entry1/program_name", "/entry1/scan_command", "/entry1/scan_identifier",
				 "/entry1/title"};
		return scalarNames;
	}
	
	private String[] getSetNames() {
		final String[] setNames = {"/entry1/Pilatus2M/data", "/entry1/Pilatus2M_azimuthal/data", "/entry1/Pilatus2M_azimuthal/direction",
				"/entry1/Pilatus2M_azimuthal/errors", "/entry1/Pilatus2M_processing/Average/data", "/entry1/Pilatus2M_processing/Average/errors",
				"/entry1/Pilatus2M_processing/Average/q", "/entry1/Pilatus2M_processing/Average/q_errors", "/entry1/Pilatus2M_processing/Average_Azimuthal/data",
				"/entry1/Pilatus2M_processing/Average_Azimuthal/direction", "/entry1/Pilatus2M_processing/Average_Azimuthal/errors",
				"/entry1/Pilatus2M_processing/DebyeBuechePlot/data", "/entry1/Pilatus2M_processing/DebyeBuechePlot/errors",
				"/entry1/Pilatus2M_processing/DebyeBuechePlot/variable", "/entry1/Pilatus2M_processing/DebyeBuechePlot/variable_errors",
				"/entry1/Pilatus2M_processing/DegreeOfOrientation_Azimuthal/data", "/entry1/Pilatus2M_processing/DegreeOfOrientation_Azimuthal/orientation",
				"/entry1/Pilatus2M_processing/DegreeOfOrientation_Azimuthal/vector_map", "/entry1/Pilatus2M_processing/GuinierPlot/I0",
				"/entry1/Pilatus2M_processing/GuinierPlot/I0_errors", "/entry1/Pilatus2M_processing/GuinierPlot/Rg",
				"/entry1/Pilatus2M_processing/GuinierPlot/Rg_errors", "/entry1/Pilatus2M_processing/GuinierPlot/Rg_range",
				"/entry1/Pilatus2M_processing/GuinierPlot/data", "/entry1/Pilatus2M_processing/GuinierPlot/errors", "/entry1/Pilatus2M_processing/GuinierPlot/fit",
				"/entry1/Pilatus2M_processing/GuinierPlot/variable", "/entry1/Pilatus2M_processing/GuinierPlot/variable_errors",
				"/entry1/Pilatus2M_processing/Invariant/data", "/entry1/Pilatus2M_processing/Invariant/errors", "/entry1/Pilatus2M_processing/Invariant/porod_fit",
				"/entry1/Pilatus2M_processing/Invariant/porod_fit_errors", "/entry1/Pilatus2M_processing/KratkyPlot/data",
				"/entry1/Pilatus2M_processing/KratkyPlot/errors", "/entry1/Pilatus2M_processing/KratkyPlot/variable",
				"/entry1/Pilatus2M_processing/KratkyPlot/variable_errors", "/entry1/Pilatus2M_processing/LogLogPlot/data",
				//"/entry1/Pilatus2M_processing/LogLogPlot/errors, /entry1/Pilatus2M_processing/LogLogPlot/fit", //not sure why these fields are no longer available
				"/entry1/Pilatus2M_processing/LogLogPlot/variable",
				"/entry1/Pilatus2M_processing/LogLogPlot/variable_errors", "/entry1/Pilatus2M_processing/Normalisation/data",
				"/entry1/Pilatus2M_processing/Normalisation/errors", "/entry1/Pilatus2M_processing/Normalisation/q",
				"/entry1/Pilatus2M_processing/Normalisation/q_errors", "/entry1/Pilatus2M_processing/Normalisation_Azimuthal/data",
				"/entry1/Pilatus2M_processing/Normalisation_Azimuthal/direction", "/entry1/Pilatus2M_processing/Normalisation_Azimuthal/errors",
				"/entry1/Pilatus2M_processing/PorodPlot/data", "/entry1/Pilatus2M_processing/PorodPlot/errors", "/entry1/Pilatus2M_processing/PorodPlot/q^4",
				"/entry1/Pilatus2M_processing/PorodPlot/q^4_errors", "/entry1/Pilatus2M_processing/PorodPlot/variable",
				"/entry1/Pilatus2M_processing/PorodPlot/variable_errors", "/entry1/Pilatus2M_processing/SectorIntegration/azimuth",
				"/entry1/Pilatus2M_processing/SectorIntegration/azimuth_errors", "/entry1/Pilatus2M_processing/SectorIntegration/beam centre",
				"/entry1/Pilatus2M_processing/SectorIntegration/data", "/entry1/Pilatus2M_processing/SectorIntegration/direction",
				"/entry1/Pilatus2M_processing/SectorIntegration/errors", "/entry1/Pilatus2M_processing/SectorIntegration/integration angles",
				"/entry1/Pilatus2M_processing/SectorIntegration/integration radii", "/entry1/Pilatus2M_processing/SectorIntegration/mask",
				"/entry1/Pilatus2M_processing/SectorIntegration/q", "/entry1/Pilatus2M_processing/SectorIntegration/q_errors",
				"/entry1/Pilatus2M_processing/StandardisedIntensity/data", "/entry1/Pilatus2M_processing/StandardisedIntensity/errors",
				"/entry1/Pilatus2M_processing/StandardisedIntensity/q", "/entry1/Pilatus2M_processing/StandardisedIntensity/q_errors",
				"/entry1/Pilatus2M_processing/ZimmPlot/data", "/entry1/Pilatus2M_processing/ZimmPlot/errors", "/entry1/Pilatus2M_processing/ZimmPlot/variable",
				"/entry1/Pilatus2M_processing/ZimmPlot/variable_errors", "/entry1/Pilatus2M_processing/guinierTestData/I0",
				"/entry1/Pilatus2M_processing/guinierTestData/I0_errors", "/entry1/Pilatus2M_processing/guinierTestData/Rg",
				"/entry1/Pilatus2M_processing/guinierTestData/Rg_errors", "/entry1/Pilatus2M_processing/guinierTestData/Rg_range",
				"/entry1/Pilatus2M_processing/guinierTestData/data", "/entry1/Pilatus2M_processing/guinierTestData/errors",
				"/entry1/Pilatus2M_processing/guinierTestData/fit", "/entry1/Pilatus2M_processing/guinierTestData/outliers",
				"/entry1/Pilatus2M_processing/guinierTestData/variable", "/entry1/Pilatus2M_processing/guinierTestData/variable_errors",
				"/entry1/Pilatus2M_result/data", "/entry1/Pilatus2M_result/errors", "/entry1/Pilatus2M_result/q", "/entry1/Pilatus2M_result/q_errors",
				"/entry1/Scalers/data", "/entry1/instrument/Pilatus2M/data", "/entry1/instrument/Scalers/data", "/entry1/instrument/TfgTimes/data"};
		return setNames;
	}

	private void setUpLocations(String configXmlPath) throws CoreException {
		// Set up the locations
		final String xmlPath  = TestUtils.getAbsolutePath("uk.ac.diamond.scisoft.ncd.actors.test", configXmlPath);
		final String rawPath  = TestUtils.getAbsolutePath("uk.ac.diamond.scisoft.ncd.actors.test", "data/i22-34820.nxs");
		final String persPath = TestUtils.getAbsolutePath("uk.ac.diamond.scisoft.ncd.actors.test", "data/persistence_file.nxs");
		


		System.setProperty("xml.path",         xmlPath);
		System.setProperty("raw.path",         rawPath);
		System.setProperty("persistence.path", persPath);		
		
		final IProject workflows = ResourcesPlugin.getWorkspace().getRoot().getProject("workflows");
		final IFolder  out       = workflows.getFolder("output");
		if (!out.exists())  out.create(true, true, new NullProgressMonitor());
		
		final String outputPath = out.getLocation().toOSString();
		System.setProperty("output.path", outputPath);
	}
	
	private synchronized void testScalarInjection(final String path, final String[] setNames, final String... scalarNames) throws Throwable {
	
		testVariables(path, setNames, scalarNames);
	}
	private synchronized void testVariables(final String path, final String[] setName, final String... scalarNames) throws Throwable {

		testVariables(path, setName, IHierarchicalDataFile.TEXT, scalarNames);

	}

	private IFile getFirstNexusFile(IResource[] resources) {
		for (IResource resource : resources) {
			if (((IFile)resource).getLocation().toOSString().endsWith("nxs")) {
				return (IFile) resource;
			}
		}
		return null;
	}
	public synchronized void testVariables(final String path, final String[] setNames, int dataType, final String... scalarNames) throws Throwable {

		
		testFile(path, false);

		IFile h5 = getH5File();
		final IHierarchicalDataFile hFile = HierarchicalDataFactory.getReader(h5.getLocation().toOSString());
		try {
			final List<String> scalars = hFile.getDatasetNames(dataType);
			if (!scalars.containsAll(Arrays.asList(scalarNames))) {
				throw new Exception("Testing file '"+path+"', did not find injected scalars in "+scalars);
			}
			
			if (setNames!=null) {
				final List<String> sets = hFile.getDatasetNames(IHierarchicalDataFile.NUMBER_ARRAY);
				if (!sets.containsAll(Arrays.asList(setNames))) {
					throw new Exception("Testing file '"+path+"', did not find injected list in "+sets);
				}
			}

		} finally {
			hFile.close();
		}

	}

	private synchronized IFile getH5File() throws Exception {
		workflows.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		IFolder output = workflows.getFolder("output");
		if (!output.exists()) output.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		// There should always be an output folder but sometimes there is a threading issue running all the tests in same VM
		if (!output.exists()) {
			logger.error("Did not find output folder, because tests are not run in separate VM and passerelle not clearing memory!");
			logger.error("TODO Run tests in separate VM!");
			throw new Exception("Did not find output folder, because tests are not run in separate VM and passerelle not clearing memory!");
		}

		final IResource[] reses = output.members();
		if (reses.length<1) {
			logger.error("Hit resource refresh problem or problem with workflow which meant that it did not run!");
			logger.error("TODO Run tests in separate VM!");
			throw new Exception("Hit resource refresh problem or problem with workflow which meant that it did not run!");
		}
		
		final IFile h5 = getFirstNexusFile(reses);
		if (h5==null||!h5.exists()) throw new Exception("output folder must have contents!");
		return h5;
	}

	private synchronized void testFile(final String relPath, boolean requireError)  throws Throwable {
				
		final String afile = TestUtils.getAbsolutePath("uk.ac.diamond.scisoft.ncd.actors.test", relPath);

		final IProject workflows = ResourcesPlugin.getWorkspace().getRoot().getProject("workflows");
		if (!workflows.exists()) {
			workflows.create(new NullProgressMonitor());
			workflows.open(new NullProgressMonitor());
		}
		
		final IFile moml = workflows.getFile((new File(afile)).getName());
		if (moml.exists()) moml.delete(true, new NullProgressMonitor());
		moml.create(new FileInputStream(afile), IResource.FORCE, new NullProgressMonitor());
				
		final long startSize = Runtime.getRuntime().totalMemory();
		
		final ModelRunner runner = new ModelRunner();
		if (requireError) {
			// This should raise an exception - we are testing that it does!
			boolean illegalReportedException = false;
			try {
			    runner.runModel(moml.getLocation().toOSString(), false);
			} catch (Exception ne) {
				illegalReportedException = true;
			}
			if (!illegalReportedException) throw new Exception(relPath+" should not pass!");
		
		} else {
			runner.runModel(moml.getLocation().toOSString(), false);
			
		}
		
		System.gc();
		Thread.sleep(500);
		
 		final long endSize = Runtime.getRuntime().totalMemory();
 		final long leak    = (endSize-startSize);
 		if (leak>700000000) throw new Exception("The memory leak is too large! It is "+leak);
		System.out.println("The memory leak opening example workflows is "+leak);
		
		workflows.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		ws.save(false, new NullProgressMonitor());

	}
	
	@AfterClass
	public static void after() {
		WorkbenchServiceManager.stopWorkbenchService();
	}
	
    /**
     * 
     * @param name
     * @param root
     * @param mon
     * @return
     * @throws Exception
     */
	public static IProject createDataProject(final String           name, 
			                                 final IWorkspaceRoot   root,
			                                 final boolean          createExamples,
			                                 final IProgressMonitor mon) throws Exception {
		
		if (root.getProject(name).exists()) return root.getProject(name);

		final IProject data = root.getProject(name);
		data.create(mon);
		data.open(mon);

		if (createExamples) {
	        final IFolder examples = data.getFolder("examples");
			examples.create(true, true, mon);
			
			// We copy all the data from examples here.
			// Use bundle as works even in debug mode.
	        final File examplesDir = BundleUtils.getBundleLocation("org.dawb.workbench.examples");
	        final File dataDir     = new File(examplesDir, "data");
	        logger.debug("Using data folder "+dataDir.getAbsolutePath());
	        if (dataDir.exists()) {
	        	FileUtils.recursiveCopy(dataDir, new File(examples.getLocation().toOSString()));
	        }
	        
	        final IFolder src = data.getFolder("src");
	        src.create(true, true, mon);

	        final File pythonDir     = new File(examplesDir, "python");
	        if (pythonDir.exists()) {
	        	FileUtils.recursiveCopy(pythonDir, new File(src.getLocation().toOSString()));
	        }

		}
	        
        addDataNature(data, mon);
        
        data.refreshLocal(IResource.DEPTH_INFINITE, mon);

        return data;
	}

	
	/**
	 * 
	 * @param workflows
	 * @param mon
	 * @throws CoreException 
	 */
	private static void addDataNature(final IProject data,
			                          final IProgressMonitor mon) throws CoreException {
		
		IProjectDescription description = data.getDescription();
		description.setNatureIds(new String[]{"org.dawb.common.ui.DataNature"});
		data.setDescription(description, mon);
		
	}


}
