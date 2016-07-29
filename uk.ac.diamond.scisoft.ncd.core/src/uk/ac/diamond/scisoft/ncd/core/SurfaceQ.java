/*
 * Copyright (c) 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.ncd.core;

import java.util.Arrays;

import javax.vecmath.Vector3d;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.dawnsci.analysis.api.diffraction.DetectorProperties;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.LinearAlgebra;
import org.eclipse.january.dataset.Maths;

/**
 * Calculates the perpendicular and surface parallel momentum change for
 * scattering from a surface.
 */
public class SurfaceQ {

	static final int nDim = 3;
	static final double hBarC = 1.9732697879296464; // keV Å 
	DetectorProperties detector;
	DoubleDataset surfaceNormal, surfaceLook;
	
	public SurfaceQ() {
	}

	public void setSurfaceNormal(DoubleDataset n) {
		surfaceNormal = n.clone();
	}
	
	public void setSurfaceLook(DoubleDataset l) {
		surfaceLook = l.clone();
	}
	
	
	// Static functions
	
	/**
	 * Calculates the perpendicular momentum change.
	 * @param scatteredBeam
	 * 						vector in the direction of the scattered beam, as a 3-element DoubleDataset
	 * @param incidentBeam
	 * 						vector in the direction of the incident beam, as a 3-element DoubleDataset
	 * @param surfaceNormal
	 * 						vector in the direction of the surface normal, as a 3-element DoubleDataset
	 * @param beamEnergy
	 * 					beam photon energy in units of keV.
	 * @return The perpendicular momentum change in units of Å⁻¹.
	 */
	public static double qPerpendicular(DoubleDataset scatteredBeam, DoubleDataset incidentBeam, DoubleDataset surfaceNormal, double beamEnergy) {
		DataCacher dc = new DataCacher(scatteredBeam, incidentBeam, surfaceNormal, beamEnergy);
		
		return qPerpendicular(dc);
	}
	
	private static double qPerpendicular(DataCacher dc) {
		return LinearAlgebra.dotProduct(dc.deltaK, dc.unitSurfaceNormal).getDouble(0);
	}
	
	/**
	 * Calculates the parallel momentum change.
	 * @param scatteredBeam
	 * 						vector in the direction of the scattered beam, as a 3-element DoubleDataset
	 * @param incidentBeam
	 * 						vector in the direction of the incident beam, as a 3-element DoubleDataset
	 * @param surfaceNormal
	 * 						vector in the direction of the surface normal, as a 3-element DoubleDataset
	 * @param beamEnergy
	 * 					beam photon energy in units of keV.
	 * @return The parallel momentum change in units of Å⁻¹.
	 */
	public static double qParallel(DoubleDataset scatteredBeam, DoubleDataset incidentBeam, DoubleDataset surfaceNormal, double beamEnergy) {
		DataCacher dc = new DataCacher(scatteredBeam, incidentBeam, surfaceNormal, beamEnergy);

		double qPerp = qPerpendicular(dc);
		DoubleDataset qPar = (DoubleDataset) Maths.subtract(dc.deltaK, Maths.multiply(qPerp, dc.unitSurfaceNormal));
		return Math.sqrt(LinearAlgebra.dotProduct(qPar, qPar).getDouble(0));
	}
	
	private static class DataCacher {
		public DoubleDataset kInHat, kOutHat, deltaK, unitSurfaceNormal;
		
		public DataCacher(DoubleDataset scatteredBeam, DoubleDataset incidentBeam, DoubleDataset surfaceNormal, double beamEnergy) {
			double wavenumber = beamEnergy / hBarC; 
			kInHat = (DoubleDataset) Maths.multiply(wavenumber, normalized(incidentBeam));
			kOutHat = (DoubleDataset) Maths.multiply(wavenumber, normalized(scatteredBeam));
			
			deltaK = (DoubleDataset) Maths.multiply(wavenumber, Maths.subtract(kOutHat, kInHat));
			unitSurfaceNormal = normalized(surfaceNormal);
		}
	}
	
	
	// Convert between JavaX Vector3d and our own Datasets.
	@SuppressWarnings("unused")
	private static Vector3d Vector3dFromDoubleDataset(DoubleDataset d) {
		double[] a = Arrays.copyOf(d.getData(), nDim);
		Vector3d v = new Vector3d(a);
		return v;
	}
	
	@SuppressWarnings("unused")
	private static DoubleDataset DatasetFromVector3d(Vector3d v) {
		double[] a = new double[3];
		v.get(a);
		DoubleDataset d = (DoubleDataset) DatasetFactory.createFromList(Arrays.asList(ArrayUtils.toObject(a)));
		return d;
	}
	
	private static DoubleDataset normalized(DoubleDataset v) {
		return (DoubleDataset) Maths.divide(v, Math.sqrt(LinearAlgebra.dotProduct(v, v).getDouble(0)));
	}

}
