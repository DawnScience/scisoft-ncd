package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.LinearAlgebra;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;

public class GratingFitOperation extends AbstractOperation<GratingFitModel, OperationData> {

	@Override
	public String getId() {
		return "/uk.ac.diamond.scisoft.analysis.processing.operations.ncd.GratingFitOperation.java";
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

		int[] beamCentre = model.getBeamCentre();
		
		int boxHalfWidth = 25;
		int boxHalfLength = Collections.max(Arrays.asList(ArrayUtils.toObject(input.getShape()))); // maximum dimension of the image
		
		RectangularROI integralBox = new RectangularROI();
		
		double theta = 0.0, cosTheta = Math.cos(theta), sinTheta = Math.sin(theta);
		
		double xStart = -(boxHalfLength * cosTheta - boxHalfWidth * sinTheta), yStart = -(boxHalfWidth * cosTheta + boxHalfLength * sinTheta);
		// distance along the line to get to each edge from the Start point 
		double xToLeft = (0.0 - xStart)/cosTheta, xToRight = (input.getShape()[0] - xStart)/cosTheta;
		if (theta > 0.0 && theta < Math.PI)
			xToLeft += 2*boxHalfWidth*sinTheta/cosTheta;
		
		return null;
	}
	
	// Fit a box of shape (length, breadth) centred at xo, at angle theta to
	// the coordinates within the limits 0<=x,y<=xyLimits. Return the centre of
	// the shifted box
	private static Dataset fitInBounds(Dataset xo, double theta, double[] xyLimits, Dataset shape) {
		
		double[] tRange = {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
		double[] signs = new double[] {-1, +1};
		// Rotation matrix
		double cTheta = Math.cos(theta), sTheta = Math.sin(theta);
		Dataset rotation = new DoubleDataset(new double[] {cTheta,  -sTheta, sTheta, cTheta}, new int[] {2,2});
		
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
		tShifted = Math.max(tShifted, tRange[0]);
		tShifted = Math.min(tShifted, tRange[1]);
		
		Dataset xoShifted = Maths.add(xo, Maths.multiply(tShifted, u));
		
		return xoShifted;
	}
	
	public static void main(String[] args) {
		Dataset xo = new DoubleDataset(new double[]{320, 100}, new int[]{2});
		double[] xyLimits = new double[] {640, 480};
		Dataset shape = new DoubleDataset(new double[]{320, 50}, new int[]{2});
		
		for (double theta = 0; theta < 360.0; theta += 10) {
			Dataset xoS = fitInBounds(xo, Math.toRadians(theta), xyLimits, shape);
			System.out.println("theta = " + theta + "("  + xoS.getDouble(0) + "," + xoS.getDouble(1) + ")");
		}
	
		xo = new DoubleDataset(new double[]{30, 100}, new int[]{2});
		for (double theta = 0; theta < 360.0; theta += 10) {
			Dataset xoS = fitInBounds(xo, Math.toRadians(theta), xyLimits, shape);
			System.out.println("theta = " + theta + "("  + xoS.getDouble(0) + "," + xoS.getDouble(1) + ")");
		}

	}
	
}
