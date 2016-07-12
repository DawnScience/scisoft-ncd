package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.fitting.functions.IPeak;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.FFT;
import org.eclipse.dawnsci.analysis.dataset.impl.LinearAlgebra;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;
import org.eclipse.dawnsci.analysis.dataset.metadata.AxesMetadataImpl;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;

import uk.ac.diamond.scisoft.analysis.fitting.Fitter;
import uk.ac.diamond.scisoft.analysis.fitting.Generic1DFitter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.PseudoVoigt;
import uk.ac.diamond.scisoft.analysis.processing.operations.oned.InterpolateMissingDataOperation;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

public class GratingFitOperation extends AbstractOperation<GratingFitModel, OperationData> {

	/**
	 * Set of keys for the results of the fit.
	 * @author Timothy Spain timothy.spain@diamond.ac.uk
	 *
	 */
	public enum GratingFitKeys {
		FRINGE_SPACING,
		BEAM_CENTRE_X,
		BEAM_CENTRE_Y,
		PATTERN_ANGLE;
	}
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.ncd.GratingFitOperation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.TWO;
	}

	@Override
	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		double[] beamCentre = model.getBeamCentre();
		Map<GratingFitKeys, Double> fitResults = fitGrating(input, beamCentre);
		
		if (fitResults != null) {
			System.out.println("Grating fringe spacing on detector = " + fitResults.get(GratingFitKeys.FRINGE_SPACING) + " px");
			System.out.println("Beam centre: (" + fitResults.get(GratingFitKeys.BEAM_CENTRE_X) + ", " + fitResults.get(GratingFitKeys.BEAM_CENTRE_Y) + ")");
			System.out.println("Diffraction pattern at " + fitResults.get(GratingFitKeys.PATTERN_ANGLE) + "°");
		}
		
		return new OperationData(input);
		
	}

	/**
	 * Fits the beam centre and fringe spacing given an I22 grating calibration dataset.
	 * @param input
	 * 				the dataset containing the grating integration
	 * @return a map of the double result values, encapsulated in a map. The Map is keyed by the GratingFitKeys enum.
	 */
	public static Map<GratingFitKeys, Double> fitGrating(IDataset input) {
		return fitGrating(input, null);
	}
	
	/**
	 * Fits the beam centre and fringe spacing given an I22 grating calibration dataset.
	 * @param input
	 * 				the dataset containing the grating integration
	 * @param beamCentre
	 * 					the manually assigned beam centre, if appropriate
	 * @return a map of the double result values, encapsulated in a map. The Map is keyed by the GratingFitKeys enum.
	 */
	public static Map<GratingFitKeys, Double> fitGrating(IDataset input, double[] beamCentre) {
		Map<GratingFitKeys, Double> results = null;
		if (beamCentre == null) {
			beamCentre = estimateBeamCentre(input);
		}
		if (beamCentre != null) {
		
			int boxHalfWidth = 50;
			int boxHalfLength = Collections.max(Arrays.asList(ArrayUtils.toObject(input.getShape())))/4; // maximum dimension of the image

			// Make the parameters of the integration box
			Dataset boxCentre = new DoubleDataset(new double[] {beamCentre[0], beamCentre[1]}, new int[] {2});
			Dataset boxShape = new DoubleDataset(new double[] {boxHalfLength*2, boxHalfWidth*2}, new int[] {2});
			double[] bounds = new double[] {input.getShape()[0], input.getShape()[1]};

			// box profiles taken across the short edge, running along the long edge
			List<Dataset> longIntegrals = new ArrayList<Dataset>();

			int idTheta = 10;
			for (int iTheta = 0; iTheta < 180; iTheta += idTheta)
				longIntegrals.add(boxIntegrationAtDegreeAngle(input, iTheta, boxShape, boxCentre, bounds));

			// Make longIntegrals into a Dataset, so that I can see it
			int maxLong = 0;
			for (Dataset longProfile : longIntegrals)
				if (longProfile.getSize() > maxLong) maxLong  = longProfile.getSize();

			// allIntegrals makes sure the box profiles all have the same size. It is a roundabout way of padding the data.
			Dataset allIntegrals = new DoubleDataset(longIntegrals.size(), maxLong);
			for (int i = 0; i < longIntegrals.size(); i++) {
				int offset = (maxLong - longIntegrals.get(i).getSize())/2; 
				for (int j = 0; j < longIntegrals.get(i).getSize(); j++) {
					allIntegrals.set(longIntegrals.get(i).getDouble(j), i, j+offset);
				}
			}
			double[] angleSpacing = getFourierAngleSpacing(allIntegrals, idTheta, boxHalfLength);
			double optimumAngle = angleSpacing[0];
			double firstFringeSpacing = angleSpacing[1];
			Dataset alignedIntegral = boxIntegrationAtDegreeAngle(input, optimumAngle, boxShape, boxCentre, bounds);		
			Dataset alignedLog = Maths.log10(alignedIntegral);

			alignedLog = InterpolateMissingDataOperation.interpolateMissingData(alignedLog, null);

			// Fit 10 pseudo-Voigt peaks, and eliminate any with a FWHM greater
			// than 10 pixels, since these will not be diffraction fringes
			List<IPeak> allPeaks = Generic1DFitter.fitPeaks(DoubleDataset.createRange(alignedLog.getSize()), alignedLog, PseudoVoigt.class, 10);

			// List, then remove all peaks with FWHM > 10 pixels
			List<IPeak> fatPeaks = new ArrayList<IPeak>();
			for (IPeak peak : allPeaks)
				if (peak.getFWHM() > 10)
					fatPeaks.add(peak);
			allPeaks.removeAll(fatPeaks);

			// Check there are at least 2 peaks, otherwise set the spacing to that
			// determined from getFourierAngleSpacing()
			double fringeSpacing = firstFringeSpacing;
			int minPeaks = 2;
			if (allPeaks.size() >= minPeaks) {
				// Get all the peak centres
				double[] peakLocations = new double[allPeaks.size()];
				for (int i = 0; i < allPeaks.size(); i++)
					peakLocations[i] = allPeaks.get(i).getPosition();

				Dataset peakLocationData = new DoubleDataset(peakLocations, peakLocations.length);

				double span = ((double) peakLocationData.max() - (double) peakLocationData.min());
				double fourierDerivedMultiple = span/fringeSpacing;
				double roundedMultiple = Math.floor(fourierDerivedMultiple+0.5);
				fringeSpacing = span/roundedMultiple;
			}

			beamCentre = refineBeamCentre(input, beamCentre, boxShape, optimumAngle);
			
			// Fill the map of the results
			results = new HashMap<GratingFitKeys, Double>(4);
			results.put(GratingFitKeys.FRINGE_SPACING, fringeSpacing);
			results.put(GratingFitKeys.BEAM_CENTRE_X, beamCentre[0]);
			results.put(GratingFitKeys.BEAM_CENTRE_Y, beamCentre[1]);
			results.put(GratingFitKeys.PATTERN_ANGLE, optimumAngle);
			
		}
		return results;
	}
	
	// Estimate the beam centre by fitting peaks in each dimension to the entire dataset
	private static double[] estimateBeamCentre(IDataset input) {
		RectangularROI integralBox = new RectangularROI(input.getShape()[0], input.getShape()[1], 0.0);
		Dataset[] profiles = ROIProfile.box(DatasetUtils.convertToDataset(input), DatasetUtils.convertToDataset(getFirstMask(input)), integralBox);
		
		int nDim = 2;
		double[] centre = new double[nDim];
		for (int i = 0; i < nDim; i++) {
			Dataset profile = profiles[i];
			List<IPeak> allPeaks = Generic1DFitter.fitPeaks(DoubleDataset.createRange(profile.getSize()), profile, PseudoVoigt.class, 1);
			centre[i] = allPeaks.get(0).getPosition();
		}
		
		return centre;
	}
	
	// Corrects the position of the beam to account for the beam stop blocking the maximum intensity
	private static double[] refineBeamCentre(IDataset input, double[] beamCentre, Dataset boxShape, double angle) {
		
		double[] bounds = new double[] {input.getShape()[0], input.getShape()[1]};
		// Move the centre by the full box width at 90° to the grating pattern. This is the ±y direction
		Dataset rotationMatrix = rotationMatrix(Math.toRadians(angle));
		// Basis vectors of the box
		Dataset yDash = LinearAlgebra.dotProduct(rotationMatrix, new DoubleDataset(new double[]{0,1}, new int[]{2}));
		Dataset xDash = LinearAlgebra.dotProduct(rotationMatrix, new DoubleDataset(new double[]{1,0}, new int[]{2}));
		
		// vector from the centre of the image to the centre of the old box
		Dataset xCentre = Maths.subtract(new DoubleDataset(beamCentre, new int[]{2}), Maths.divide(new DoubleDataset(bounds, new int[]{2}), 2));
		// Determine the direction to move by taking the dot product of the
		// rotated y coordinate with the displacement vector of the box
		// (centre) from the image centre. The sign of the displacement is the
		// opposite of this sign. This moves the box towards the centre of the
		// image.
		Dataset rotateXCentre = LinearAlgebra.dotProduct(yDash.reshape(1,2), xCentre);
		double shiftSign = -1*Math.signum(rotateXCentre.getDouble(0));
		// direction vector in which to shift the box
		Dataset offAxisShiftVector = Maths.multiply(shiftSign, yDash);
		// displacement vector by which to shift the box
		Dataset offAxisShift = Maths.multiply(boxShape.getDouble(1), offAxisShiftVector);
		// the old (incorrect) beam centre shifted by the box shift
		Dataset shiftedBeamCentre = Maths.add(new DoubleDataset(beamCentre, new int[]{2}), offAxisShift);
		
		// Get the fitted box parameters of the shifted box for our own use
		double theta = Math.toRadians(angle);
		Dataset thisBoxShape = new DoubleDataset(boxShape);
		Dataset newBoxCentre = fitInBounds(shiftedBeamCentre, theta, bounds, thisBoxShape);
		Dataset newBoxOrigin = originFromCentre(newBoxCentre, thisBoxShape, theta);

		Dataset alignedIntegral = boxIntegrationAtDegreeAngle(input, angle, boxShape, shiftedBeamCentre, bounds);		
		// Fit a single peak to the data
		List<IPeak> thePeaks = Generic1DFitter.fitPeaks(DoubleDataset.createRange(alignedIntegral.getSize()), alignedIntegral, PseudoVoigt.class, 1);
		// This is the distance from the new origin along the shifted box edge at which the peak occurs
		double peakLocation = thePeaks.get(0).getPosition();

		// Get the distance from the new origin along the shifted box at which the shifted unrefined beam centre occurs
		double shiftedBeamLocation = LinearAlgebra.dotProduct(xDash.reshape(1,2), Maths.subtract(shiftedBeamCentre, newBoxOrigin)).getDouble(0);
		// the distance by which the beam centre estimate has shifted
		double centreShift = peakLocation - shiftedBeamLocation;
		Dataset beamCentreShift = Maths.multiply(centreShift, xDash);
		
		// Shift the beam
		for (int i=0; i<2; i++)
			beamCentre[i] += beamCentreShift.getDouble(i);
		
		return beamCentre;
	}
	
	// Perform a box integration at the specified angle
	private static Dataset boxIntegrationAtDegreeAngle(IDataset input, double angle, Dataset boxShape, Dataset boxCentre, double[] bounds) {
		double theta = Math.toRadians(angle);
		Dataset thisBoxShape = new DoubleDataset(boxShape);
		// Get the centre and possibly altered shape of the box at this angle.
		Dataset newBoxCentre = fitInBounds(boxCentre, theta, bounds, thisBoxShape);
		Dataset newBoxOrigin = originFromCentre(newBoxCentre, thisBoxShape, theta);
		// Create the ROI covering this box 
		RectangularROI integralBox = new RectangularROI(newBoxOrigin.getDouble(0), newBoxOrigin.getDouble(1), thisBoxShape.getDouble(0), thisBoxShape.getDouble(1), theta);
		//	System.out.println("ROI:" + newBoxOrigin.getDouble(0) + ", " + newBoxOrigin.getDouble(1) + ", " + thisBoxShape.getDouble(0) + ", " + thisBoxShape.getDouble(1) + ", " + (double) iTheta);
		// box profiles from this ROI
		Dataset[] boxes = ROIProfile.box(DatasetUtils.convertToDataset(input), DatasetUtils.convertToDataset(getFirstMask(input)), integralBox);
		// Get only the ROI in the first dimension
		return boxes[0];
	}
	
	// Using Fourier transforms, get the angle of the grating pattern. An
	// estimate of the spacing is also returned, but is not terribly accurate
	private static double[] getFourierAngleSpacing(Dataset allIntegrals, double idTheta, double boxHalfLength) {
		Dataset allFourier = new DoubleDataset(allIntegrals);
		int nangles = allIntegrals.getShape()[0];
		int nData = allIntegrals.getShape()[1];
		for (int i = 0; i < nangles; i++) {
			Dataset fft = Maths.abs(FFT.fft(allIntegrals.getSlice(new int[]{i,  0}, new int[] {i+1, nData}, new int[]{1,1})));
			fft.squeeze();
			for (int j = 0;  j < nData; j++){
				allFourier.set(fft.getDouble(j), i, j);
			}
		}
		
		Dataset firstACPeak = new DoubleDataset(nangles);
		
		// Take the first derivative of the first half of the FT'd data
		for (int i = 0; i < nangles; i++) {
			Dataset x = DoubleDataset.createRange(nData/2+1);
			Dataset y =  allFourier.getSlice(new int[]{i, 0},  new int[]{i+1, nData/2+1}, new int[]{1, 1}).squeeze();
			Dataset ftDerivative = Maths.derivative(x, y, 2);
			// find the maxima and minima of the power spectrum.
			List<Double> zeroes = NcdOperationUtils.findDatasetZeros(ftDerivative);
			// Ignore the first zero if it is very small, corresponding to a zero found in the DC component
			if (zeroes.get(0) < 2.0)
				zeroes.remove(0);
			// The first zero will be the minimum power after the DC. Look, therefore, for the second zero.
			firstACPeak.set(zeroes.get(1), i);
		}
		// Determine if the minimum is too close to the zero
		int mindex = firstACPeak.minPos()[0];
		boolean doShiftData = (Math.abs(mindex - nangles/2) > nangles/4);
		Dataset shiftedData = new DoubleDataset(firstACPeak);
		if (doShiftData) {
			for (int i = 0; i < nangles; i++)
			shiftedData.set(firstACPeak.getDouble(i), (i+nangles/2) % nangles);
		}
		
		mindex = shiftedData.minPos()[0];
		Dataset parabolaX = DoubleDataset.createRange(mindex-1, mindex+2, 1);
		Dataset parabolaY = shiftedData.getSlice(new int[]{mindex-1}, new int[]{mindex+2}, new int[]{1});
		
		AxesMetadataImpl parabolaAxes = new AxesMetadataImpl(1);
		parabolaAxes.addAxis(0, parabolaX);
		parabolaY.addMetadata(parabolaAxes);
		
		double[] params = Fitter.polyFit(new Dataset[]{parabolaX}, parabolaY, 1e-15, 2).getParameterValues();
		double[] derivParams = Arrays.copyOf(params, 3);
		for (int i = 0; i < params.length; i++)
			derivParams[i] *= 2-i;
		double xMin = -derivParams[1]/derivParams[0];

		// The angle of best alignment of the grating
		double alignmentAngle = xMin*idTheta;
		alignmentAngle -= (doShiftData) ? nangles/2*idTheta : 0;
		alignmentAngle %= 180.0;
		
		// The wavenumber of the grating in the image
		double wavenumberGrating = params[2] - params[1]*params[1]/4/params[0];
		wavenumberGrating -= 0.5;
		double pixelSpacing = boxHalfLength/wavenumberGrating;
		
		//System.out.println("Alignment = " + alignmentAngle + "°, fringe spacing = " + pixelSpacing + "px");
		
		return new double[] {alignmentAngle, pixelSpacing};
	}
	
	// Fit a box of shape (length, breadth) centred at xo, at angle theta to
	// the coordinates within the limits 0<=x,y<=xyLimits. Return the centre of
	// the shifted box
	private static Dataset fitInBounds(Dataset xo, double theta, double[] xyLimits, Dataset shape) {
		
		double[] tRange = {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
		double[] signs = new double[] {-1, +1};
		// Rotation matrix
		Dataset rotation = rotationMatrix(theta);
		
		// The vector of the shift
		Dataset u = new DoubleDataset(new double[]{1, 0}, new int[] {2});
		u = LinearAlgebra.dotProduct(rotation, u);
		// Iterate over corners, by the signs of the translation
		for (double cornerX : signs) {
			for (double cornerY : signs) {
				Dataset cornerSigns = new DoubleDataset(new double[] {cornerX, cornerY}, new int[] {2});
				Dataset offset = Maths.multiply(0.5, Maths.multiply(shape, cornerSigns));
				offset = LinearAlgebra.dotProduct(rotation, offset);
				Dataset xc = Maths.add(xo, offset);
				// Now solve the offsets required to keep that corner in the bounds from 0 to [x|y]Limit
				for (int dimension = 0; dimension < 2; dimension++) {
					double[] localTRange = new double[2];
					localTRange[0] = (xc.getDouble(dimension) != 0.0) ? (0 - xc.getDouble(dimension))/u.getDouble(dimension) : 0.0;
					localTRange[1] = (xc.getDouble(dimension) != xyLimits[dimension]) ? (xyLimits[dimension]- xc.getDouble(dimension))/u.getDouble(dimension) : 0.0;
					// Sort the array. The range of the t shift parameter is
					// then the lower and upper values that keep this corner
					// within the bounds of this dimension. 
					Arrays.sort(localTRange);
					// Update the global t range
					tRange[0] = Math.max(tRange[0], localTRange[0]);
					tRange[1] = Math.min(tRange[1], localTRange[1]);
				}
			}
		}
		// Calculate the value it needs to move to get within bounds.
		double tShifted = 0.0;

		double deltaLength = Math.min(tRange[1] - tRange[0], 0.0); // non-positive value
		if (deltaLength < 0.0) {
			tShifted = (tRange[0] + tRange[1])/2;
			// alter the shape of the box in this case
			shape.set(shape.getDouble(0) + deltaLength, 0);
		} else {
			tShifted = Math.max(tShifted, tRange[0]);
			tShifted = Math.min(tShifted, tRange[1]);
		}		
		Dataset xoShifted = Maths.add(xo, Maths.multiply(tShifted, u));
		return xoShifted;
	}
	
	private static Dataset rotationMatrix(double theta) {
		double cTheta = Math.cos(theta), sTheta = Math.sin(theta);
		return new DoubleDataset(new double[] {cTheta,  -sTheta, sTheta, cTheta}, new int[] {2,2});
	}

	private static Dataset originFromCentre(Dataset centre, Dataset shape, double theta) {
		return Maths.subtract(centre, LinearAlgebra.dotProduct(rotationMatrix(theta), Maths.multiply(0.5, shape)));
	}
	
	
	/*
	 * Debugging methods
	 */
	
	public static void main(String[] args) {
		double[] xyLimits = new double[] {640, 480};
		Dataset shape = new DoubleDataset(new double[]{320, 50}, new int[]{2});
		
		rotateBoxInBounds(new DoubleDataset(new double[]{320, 100}, new int[]{2}), shape, xyLimits);
		rotateBoxInBounds(new DoubleDataset(new double[]{30, 100}, new int[]{2}), shape, xyLimits);
	}
	
	private static void rotateBoxInBounds(Dataset boxCentre, Dataset boxShape, double[] bounds) {
		svgHeader();
		svgBB(bounds);
		svgCentre(boxCentre);
		for (double theta = 0; theta < 360.0; theta += 30) {
			Dataset shapeI = new DoubleDataset(boxShape);
			Dataset xoS = fitInBounds(boxCentre, Math.toRadians(theta), bounds, shapeI);
			System.out.println("<!-- theta = " + theta + "("  + xoS.getDouble(0) + "," + xoS.getDouble(1) + ") -->");
			svgRotateyBox(xoS, boxShape, shapeI, theta);
		}
		svgFooter();
	}

	private static void svgHeader() {
		System.out.println("<?xml version=\"1.0\" standalone=\"no\" ?>");
		System.out.println("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\""); 
		System.out.println("  \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">");
		System.out.println("<svg width=\"29.7cm\" height=\"21.0cm\" version=\"1.1\"");
		System.out.println("     xmlns=\"http://www.w3.org/2000/svg\">");
		System.out.println("  <desc>Rotating a box within a bounding box");
		System.out.println("  </desc>");
	}
	
	private static void svgBB(double[] bounds) {
		System.out.println("    <rect x=\"0\" y=\"0\" width=\"" + (int) bounds[0] + "px\" height=\"" + (int) bounds[1] + "px\"");
		System.out.println("       fill=\"none\" stroke=\"black\" stroke-width=\"1px\"/>");
	}
	
	private static void svgCentre(Dataset boxCentre) {
		int x = (int) boxCentre.getDouble(0), y = (int) boxCentre.getDouble(1);
		int armLength = 10;
		System.out.println("    <line x1=\"" + (x - armLength) + "px\" x2=\"" + (x + armLength) + "px\" y1=\"" + y + "px\" y2=\"" + y + "px\" stroke=\"black\" stroke-width=\"1px\"/>");
		System.out.println("    <line x1=\"" + x + "px\" x2=\"" + x + "px\" y1=\"" + (y - armLength) + "px\" y2=\"" + (y + armLength) + "px\" stroke=\"black\" stroke-width=\"1px\"/>");
	}
	
	private static void svgRotateyBox(Dataset newCentre, Dataset oldShape, Dataset newShape, double thetad) {
		double theta = Math.toRadians(thetad);
		Dataset origin = originFromCentre(newCentre, newShape, theta);
		Dataset originFullSize = originFromCentre(newCentre, oldShape, theta);
		System.out.println("    <g transform=\"translate(" + origin.getInt(0) + " )" + origin.getInt(1) + ") rotate(" + (int) thetad + ")\">");
		System.out.println("      <rect x=\"0\" y=\"0\" width=\"" + newShape.getInt(0)+ "px\" height=\"" + newShape.getInt(1) + "px\"");
		System.out.println("        fill=\"none\" stroke=\"black\" stroke-width=\"1px\" />");
		System.out.println("    </g>");

		System.out.println("    <g transform=\"translate(" + originFullSize.getInt(0) + " " + originFullSize.getInt(1) + ") rotate(" + (int) thetad + ")\">");
		System.out.println("      <rect x=\"0\" y=\"0\" width=\"" + oldShape.getInt(0)+ "px\" height=\"" + oldShape.getInt(1) + "px\"");
		System.out.println("        fill=\"none\" stroke=\"red\" stroke-width=\"1px\" />");
		System.out.println("    </g>");
	}
	
	private static void svgFooter() {
		System.out.println("  </svg>");
	}
}
