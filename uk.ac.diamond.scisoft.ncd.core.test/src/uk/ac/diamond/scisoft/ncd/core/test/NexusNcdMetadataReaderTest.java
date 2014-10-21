/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.ncd.core.test;

import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.processing.io.NexusNcdMetadataReader;

public class NexusNcdMetadataReaderTest {
	@Test
	public void testReader() throws Exception {
		NexusNcdMetadataReader reader = new NexusNcdMetadataReader();
		reader.setDetectorName("detector");
		reader.setFilePath("/scratch/rbv51579/dawn_trunk_git/scisoft-ncd.git/uk.ac.diamond.scisoft.ncd.core.test/data/b21-15008.reduced.nxs");
		reader.getMaskFromFile();
		reader.getROIDataFromFile();
	}
}