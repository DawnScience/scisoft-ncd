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
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.LinearAlgebra;
import org.eclipse.january.dataset.Maths;

import uk.ac.diamond.scisoft.analysis.io.DiffractionMetadata;

/**
 * Calculates the perpendicular and surface parallel momentum change for
 * scattering from a surface.
 */
public class SurfaceQ {

	private static final int nDim = 3;
	private static final double hBarC = 1.9732697879296464; // keV Å 
	@SuppressWarnings("unused") // surfaceLook is not yet used
	private DoubleDataset surfaceNormal, surfaceLook;

	/**
	 * Direction of the grazing incidence relative to the sample.
	 */
	public enum GrazingDirection {
		XPOSITIVE, XNEGATIVE, YPOSITIVE, YNEGATIVE
	}
	
	/**
	 * Constructs an empty object
	 */
	public SurfaceQ() {
	}

	/**
	 * Defines the normal vector of the scattering surface.
	 * @param n
	 * 			The normal vector of the scattering surface in the Lab frame
	 */
	public void setSurfaceNormal(DoubleDataset n) {
		surfaceNormal = n.clone();
	}
	
	public void setSurfaceLook(DoubleDataset l) {
		surfaceLook = l.clone();
	}
	
	/**
	 * The momentum transfer perpendicular to the surface, based on beam directions.
	 * @param scatteredBeam
	 * 						direction vector of the scattered beam
	 * @param incidentBeam
	 * 						direction vector of the incident beam. Usually (0,0,1)
	 * @param beamEnergy
	 * 					the beam energy in keV
	 * @return
	 * 		the momentum transfer of the scattering perpendicular to the scattering surface.
	 */
	public double qPerpendicular(DoubleDataset scatteredBeam, DoubleDataset incidentBeam, double beamEnergy) {
		return qPerpendicular(scatteredBeam, incidentBeam, surfaceNormal, beamEnergy);
	}
	
	/**
	 * The magnitude of the momentum transfer parallel to the surface, based on beam directions.
	 * @param scatteredBeam
	 * 						direction vector of the scattered beam
	 * @param incidentBeam
	 * 						direction vector of the incident beam. Usually (0,0,1)
	 * @param beamEnergy
	 * 					the beam energy in keV
	 * @return
	 * 		the magnitude of the momentum transfer of the scattering parallel to the scattering surface.
	 */
	public double qParallel(DoubleDataset scatteredBeam, DoubleDataset incidentBeam, double beamEnergy) {
		return qParallel(scatteredBeam, incidentBeam, surfaceNormal, beamEnergy);
	}

	/**
	 * The momentum transfer perpendicular to the surface, based on the diffraction metadata. 
	 * @param dm
	 * 			The diffraction metadata
	 * @return
	 * 		the momentum transfer of the scattering perpendicular to the scattering surface.
	 */
	public DoubleDataset qPerpendicular(DiffractionMetadata dm) {
		return qPerpPara(dm, true);
	}
	
	/**
	 * The magnitude of the momentum transfer parallel to the surface, based on the diffraction metadata. 
	 * @param dm
	 * 			The diffraction metadata
	 * @return
	 * 		the magnitude of the momentum transfer of the scattering parallel to the scattering surface.
	 */
	public DoubleDataset qParallel(DiffractionMetadata dm) {
		return qPerpPara(dm, false);
	}
	
	/**
	 * Calculates the minimum and maximum perpendicular momentum transfer.
	 * <p>
	 * Given a {@link DiffractionMetadata} object, return the minimum (0) and
	 * maximum (1) values of the momentum transfer perpendicular to the
	 * scattering surface.
	 * @param dm
	 * 			The diffraction metadata object
	 * @return an array of {@link Dataset}s containing minimum and maximum of
	 * 			the momentum transfer perpendicular to the scattering surface.
	 */
	public DoubleDataset[] qPerpendicularMinMax(DiffractionMetadata dm) {
		return qPerpParaMinMax(dm, true);
	}

	/**
	 * Calculates the minimum and maximum perpendicular momentum transfer.
	 * <p>
	 * Given a {@link DiffractionMetadata} object, return the minimum (0) and
	 * maximum (1) magnitudes of the momentum transfer parallel to the
	 * scattering surface.
	 * @param dm
	 * 			The diffraction metadata object
	 * @return an array of {@link Dataset}s containing minimum and maximum of
	 * 			the magnitude of the momentum transfer parallel to the
	 * 			scattering surface.
	 */
	public DoubleDataset[] qParallelMinMax(DiffractionMetadata dm) {
		return qPerpParaMinMax(dm, false);
	}

	/**
	 * Calculates the surface normal, given a direction and an angle of the
	 * surface.
	 * @param direction
	 * 					An enum specifying the direction in which the
	 * 					surface scattering is occurring.
	 * @param alpha
	 * 				The angle of the surface to the incident beam.
	 * @return The {@link Dataset} describing the unit surface normal in the
	 * 			lab frame.
	 */
	public static DoubleDataset normalFromDirectionAndAngle(GrazingDirection direction, double alpha) {
		DoubleDataset normal;
		
		switch (direction) {
		case XPOSITIVE:
			normal = DatasetFactory.createFromList(DoubleDataset.class, Arrays.asList(new Double[] {1.0, 0.0, 0.0}));
			break;
		case XNEGATIVE:
			normal = DatasetFactory.createFromList(DoubleDataset.class, Arrays.asList(new Double[] {-1.0, 0.0, 0.0}));
			break;
		case YPOSITIVE:
			normal = DatasetFactory.createFromList(DoubleDataset.class, Arrays.asList(new Double[] {0.0, 1.0, 0.0}));
			break;
		case YNEGATIVE:
		default:
			normal = DatasetFactory.createFromList(DoubleDataset.class, Arrays.asList(new Double[] {0.0, -1.0, 0.0}));
			break;
		}	
		normal.imultiply(Math.cos(alpha));

		// z component is always -sin(alpha)
		normal.setItem(Math.sin(alpha), 2);
		
		return normal;
	}
	
	
	// Extract out all the common boilerplate for looping over the pixels in the detector
	private DoubleDataset qPerpPara(DiffractionMetadata dm, boolean isPerpendicular) {
		// For diffraction metadata, we can assume that the beam is on the +ve z-axis
		DoubleDataset incidentBeam = (DoubleDataset) DatasetFactory.createFromList(Dataset.FLOAT64, Arrays.asList(0.0, 0.0, 1.0));
		DetectorProperties dp = dm.getDetector2DProperties();
		double beamEnergy = 2*Math.PI*hBarC/dm.getDiffractionCrystalEnvironment().getWavelength();
		
		DoubleDataset qPerpPara = DatasetFactory.zeros(dp.getPx(), dp.getPy());
		
		// Get the detector size in each direction
		for (int ix = dp.getPx(); ix >= 0; ix--){
			for (int jy = dp.getPy(); jy >= 0; jy--) {
				DoubleDataset pixelPosition = datasetFromVector3d(dp.pixelPosition(ix+0.5, jy+0.5));
				qPerpPara.set( (isPerpendicular) ? 
						qPerpendicular(pixelPosition, incidentBeam, beamEnergy) :
							qParallel(pixelPosition, incidentBeam, beamEnergy)
							, ix, jy);
			}
		}
		return qPerpPara;
	}
	
	// Extract all the common boilerplate for getting the min and max values of the coordinate out of a pixel
	private DoubleDataset[] qPerpParaMinMax(DiffractionMetadata dm, boolean isPerpendicular) {
		// For diffraction metadata, we can assume that the beam is on the +ve z-axis
		DoubleDataset incidentBeam = (DoubleDataset) DatasetFactory.createFromList(Dataset.FLOAT64, Arrays.asList(0.0, 0.0, 1.0));
		DetectorProperties dp = dm.getDetector2DProperties();
		double beamEnergy = 2*Math.PI*hBarC/dm.getDiffractionCrystalEnvironment().getWavelength();

		DoubleDataset qMin = DatasetFactory.zeros(dp.getPx(), dp.getPy());
		DoubleDataset qMax = DatasetFactory.zeros(dp.getPx(), dp.getPy());

		// Get the detector size in each direction
		for (int ix = dp.getPx(); ix >= 0; ix--){
			for (int jy = dp.getPy(); jy >= 0; jy--) {
				// Initialize the min and max values to the opposite extrema
				double qMinScalar = Double.MAX_VALUE,
						qMaxScalar = -Double.MAX_VALUE;
				// Loop over corner coordinates
				for (int cx = 0; cx <= 1; cx++) {
					for (int cy = 0; cy <=1 ; cy++) {
						DoubleDataset pixelPosition = datasetFromVector3d(dp.pixelPosition(ix+cx, jy+cy));

						double qPerpPara = (isPerpendicular) ? 
								qPerpendicular(pixelPosition, incidentBeam, beamEnergy) :
								qParallel(pixelPosition, incidentBeam, beamEnergy);
						qMinScalar = Math.min(qMinScalar, qPerpPara);
						qMaxScalar = Math.max(qMaxScalar, qPerpPara);
					}
				}
				qMin.set(qMinScalar, ix, jy);
				qMax.set(qMaxScalar, ix, jy);
			}
		}
		
		return new DoubleDataset[] {qMin, qMax};
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
		return LinearAlgebra.dotProduct(dc.deltaK, dc.unitSurfaceNormal).getDouble();
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
		return Math.sqrt(LinearAlgebra.dotProduct(qPar, qPar).getDouble());
	}
	
	private static class DataCacher {
		public DoubleDataset kInHat, kOutHat, deltaK, unitSurfaceNormal;
		
		public DataCacher(DoubleDataset scatteredBeam, DoubleDataset incidentBeam, DoubleDataset surfaceNormal, double beamEnergy) {
			double wavenumber = beamEnergy / hBarC; 
			kInHat = (DoubleDataset) Maths.multiply(wavenumber, normalized(incidentBeam));
			kOutHat = (DoubleDataset) Maths.multiply(wavenumber, normalized(scatteredBeam));
			
			deltaK = (DoubleDataset) Maths.subtract(kOutHat, kInHat);
			unitSurfaceNormal = normalized(surfaceNormal);
		}
	}
	
	
	// Convert between JavaX Vector3d and our own Datasets.
	@SuppressWarnings("unused")
	private static Vector3d vector3dFromDoubleDataset(DoubleDataset d) {
		double[] a = Arrays.copyOf(d.getData(), nDim);
		Vector3d v = new Vector3d(a);
		return v;
	}
	
	private static DoubleDataset datasetFromVector3d(Vector3d v) {
		double[] a = new double[3];
		v.get(a);
		DoubleDataset d = (DoubleDataset) DatasetFactory.createFromList(Arrays.asList(ArrayUtils.toObject(a)));
		return d;
	}
	
	private static DoubleDataset normalized(DoubleDataset v) {
		Dataset vSquared = LinearAlgebra.dotProduct(v, v);
		double vSquaredScalar = vSquared.getDouble();
		double vLength = Math.sqrt(vSquaredScalar);
		return (DoubleDataset) Maths.divide(v, vLength);
	}

}
