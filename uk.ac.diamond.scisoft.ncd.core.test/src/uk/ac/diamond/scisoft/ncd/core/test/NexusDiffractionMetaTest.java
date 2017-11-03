/*
 * Copyright (c) 2012, 2017 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.ncd.core.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.junit.Test;

import uk.ac.diamond.scisoft.ncd.core.NexusDiffractionMetaReader;

public class NexusDiffractionMetaTest {
	
	static String testScratchDirectoryName = null;
	final static String testFileFolder = "testfiles/gda/analysis/io/NexusDiffractionTest/";
	
	@Test
	public void testNCDReductionResults1Detector() {
		
		NexusDiffractionMetaReader nr = new NexusDiffractionMetaReader(testFileFolder + "results_i22-102527_Pilatus2M_280313_112434.nxs");
		
		IDiffractionMetadata md = nr.getDiffractionMetadataFromNexus(new int[]{1679,1475});
		
		assertTrue(nr.anyValuesRead());
		
		double[] beamCentre = md.getDetector2DProperties().getBeamCentreCoords();
		assertEquals(702.2209567198178, beamCentre[0],0.01);
		assertEquals(94.02399999999989, beamCentre[1],0.01);
		assertEquals(9660.957924056238, md.getDetector2DProperties().getBeamCentreDistance(),0.01);
		assertEquals(1, md.getDiffractionCrystalEnvironment().getWavelength(),0.01);
		
	}
	
	@Test
	public void testNCDReductionResults2Detectors() {
		
		NexusDiffractionMetaReader nr = new NexusDiffractionMetaReader(testFileFolder + "background_i22-119185_HotwaxsPilatus2M_090513_115327.nxs");
		
		IDiffractionMetadata md = nr.getDiffractionMetadataFromNexus(new int[]{1679,1475});
		
		assertTrue(nr.anyValuesRead());
		
		double[] beamCentre = md.getDetector2DProperties().getBeamCentreCoords();
		assertEquals(718.46, beamCentre[0],0.01);
		assertEquals(92.3614931237721, beamCentre[1],0.01);
		assertEquals(1.5498, md.getDiffractionCrystalEnvironment().getWavelength(),0.01);
	}
	
	@Test
	public void testI22Nexus() {
		NexusDiffractionMetaReader nr = new NexusDiffractionMetaReader(testFileFolder + "i22-119583.nxs");
		IDiffractionMetadata md = nr.getDiffractionMetadataFromNexus(new int[]{1679,1475});
		assertTrue(nr.anyValuesRead());
		double[] beamCentre = md.getDetector2DProperties().getBeamCentreCoords();
		assertEquals(100.0, beamCentre[0],0.01);
		assertEquals(100.00, beamCentre[1],0.01);
		assertEquals(5486.400000000001, md.getDetector2DProperties().getBeamCentreDistance(),0.01);
		assertEquals(1, md.getDiffractionCrystalEnvironment().getWavelength(),0.01);
	}
	
	@Test
	public void testPersisted() {
		NexusDiffractionMetaReader nr = new NexusDiffractionMetaReader(testFileFolder + "persisted.nxs");

		IDiffractionMetadata md = nr.getDiffractionMetadataFromNexus(new int[]{2527,2463});
		assertTrue(nr.anyValuesRead());
		assertTrue(nr.isPartialRead());
		assertTrue(nr.isCompleteRead());
		
		double[] beamCentre = md.getDetector2DProperties().getBeamCentreCoords();
		assertEquals(1225.28, beamCentre[0],0.01);
		assertEquals(1223.32, beamCentre[1],0.01);
		assertEquals(199.9999999999999, md.getDetector2DProperties().getBeamCentreDistance(),0.01);
		assertEquals(0.9762999999999995, md.getDiffractionCrystalEnvironment().getWavelength(),0.01);
	}
	

}
