/*
 * Copyright (c) 2012 European Synchrotron Radiation Facility,
 *                    Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */ 
package uk.ac.diamond.scisoft.ncd.actors.test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;

import org.dawb.common.util.eclipse.BundleUtils;
import org.dawb.common.util.io.FileUtils;
import org.dawb.common.util.io.IFileUtils;
import org.dawb.common.util.test.TestUtils;
import org.dawb.hdf5.HierarchicalDataFactory;
import org.dawb.hdf5.IHierarchicalDataFile;
import org.dawb.passerelle.common.WorkbenchServiceManager;
import org.dawb.passerelle.common.utils.ModelUtils;
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
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ptolemy.moml.MoMLParser;
import uk.ac.diamond.scisoft.ncd.actors.Activator;

import com.isencia.passerelle.workbench.model.launch.ModelRunner;

public class DataReductionPipelineTest {
	
	private static final Logger logger = LoggerFactory.getLogger(DataReductionPipelineTest.class);
	
	/**
	 * Ensure that the projects are available in this workspace.
	 * @throws Exception
	 */
	@BeforeClass
	public static void beforeClass() throws Exception {
		
		ModelUtils.createWorkflowProject("workflows", ResourcesPlugin.getWorkspace().getRoot(), true, null);
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
		
		// Set up the locations
		final String xmlPath  = TestUtils.getAbsolutePath(Activator.getDefault().getBundle(), "test/uk/ac/diamond/scisoft/ncd/actors/test/ncd_configuration.xml");
		final String rawPath  = TestUtils.getAbsolutePath(Activator.getDefault().getBundle(), "test/uk/ac/diamond/scisoft/ncd/actors/test/i22-34820.nxs");
		final String persPath = TestUtils.getAbsolutePath(Activator.getDefault().getBundle(), "test/uk/ac/diamond/scisoft/ncd/actors/test/persistence_file.nxs");
		
		System.setProperty("xml.path",         xmlPath);
		System.setProperty("raw.path",         rawPath);
		System.setProperty("persistence.path", persPath);		
		
		final IProject workflows = ResourcesPlugin.getWorkspace().getRoot().getProject("workflows");
		final IFolder  out       = workflows.getFolder("output");
		if (!out.exists())  out.create(true, true, new NullProgressMonitor());
		
		final String outputPath = out.getLocation().toOSString();
		System.setProperty("output.path", outputPath);

		
		testScalarInjection("test/uk/ac/diamond/scisoft/ncd/actors/test/ncd_model.moml",  "/entry/dictionary/results_path");
	}
	
	private synchronized void testScalarInjection(final String path, final String... scalarNames) throws Throwable {
	
		testVariables(path, null, scalarNames);
	}
	private synchronized void testVariables(final String path, final String listName, final String... scalarNames) throws Throwable {
	
	    testVariables(path, listName, IHierarchicalDataFile.TEXT, scalarNames);
	}
	public synchronized void testVariables(final String path, final String listName, int dataType, final String... scalarNames) throws Throwable {

		
		testFile(path, false);

		workflows.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		IFolder output = workflows.getFolder("output");
		if (!output.exists()) output.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		// There should always be an output folder but sometimes there is a threading issue running all the tests in same VM
		if (!output.exists()) {
			logger.error("Did not find output folder, because tests are not run in separate VM and passerelle not clearing memory!");
			logger.error("TODO Run tests in separate VM!");
			return; // HACK Disguises a problem
		}

		final IResource[] reses = output.members();
		if (reses.length<1) {
			logger.error("Hit resource refresh problem or problem with workflow which meant that it did not run!");
			logger.error("TODO Run tests in separate VM!");
			return; // HACK Disguises a problem
		}
		
		final IFile h5 = (IFile)reses[0];
		if (h5==null||!h5.exists()) throw new Exception("output folder must have contents!");

		final IHierarchicalDataFile hFile = HierarchicalDataFactory.getReader(h5.getLocation().toOSString());
		try {
			final List<String> scalars = hFile.getDatasetNames(dataType);
			if (!scalars.containsAll(Arrays.asList(scalarNames))) {
				throw new Exception("Testing file '"+path+"', did not find injected scalars in "+scalars);
			}
			
			if (listName!=null) {
				final List<String> sets = hFile.getDatasetNames(IHierarchicalDataFile.NUMBER_ARRAY);
				if (!sets.contains(listName)) {
					throw new Exception("Testing file '"+path+"', did not find injected list in "+sets);
				}
			}

		} finally {
			hFile.close();
		}

	}

	private synchronized void testFile(final String relPath, boolean requireError)  throws Throwable {
				
		final String afile = TestUtils.getAbsolutePath(Activator.getDefault().getBundle(), relPath);

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
