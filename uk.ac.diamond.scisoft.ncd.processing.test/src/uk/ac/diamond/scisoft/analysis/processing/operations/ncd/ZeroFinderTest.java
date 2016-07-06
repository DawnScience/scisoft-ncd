package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IndexIterator;
import org.eclipse.january.dataset.Maths;

public class ZeroFinderTest {
	
	public static void main(String[] args) {
		Dataset theta = DatasetFactory.createRange(DoubleDataset.class, 0, 32, 0.016);
		Dataset degrees = Maths.toDegrees(theta);
		Dataset sinTheta = Maths.cos(theta);
		
		List<Double> zeros = findDatasetZeros(sinTheta);
//		System.out.println(zeros);
		for (double zeroIndex : zeros)
			System.out.println(Double.toString(degrees.getDouble((int) zeroIndex)));
	}
	
	
	
	
	/**
	 * Returns the zeros of a Dataset
	 * @param x
	 * 			independent variable of the data
	 * @param y
	 * 			dependent variable of the data
	 * @return List of values of the independent variable at the zeros of the data
	 */
	private static List<Double> findDatasetZeros(Dataset y) {
		List<Double> zeros = new ArrayList<>(); 
		IndexIterator searchStartIterator = y.getIterator(), searchingIterator = searchStartIterator;
		if (!searchStartIterator.hasNext())
			return zeros;
		double startValue = y.getElementDoubleAbs(searchStartIterator.index);
		
		while(searchingIterator.hasNext()) {
			double searchValue = y.getElementDoubleAbs(searchingIterator.index);
			if (searchValue == 0) {
				// restart the search from the next point
				if (!searchingIterator.hasNext()) break;
				searchStartIterator = searchingIterator;
				startValue = y.getElementDoubleAbs(searchStartIterator.index);
			}
			if (Math.signum(searchValue) != Math.signum(startValue)) {
				// linear interpolation to get the zero
				double y1 = y.getElementDoubleAbs(searchingIterator.index-1),
						y2 = y.getElementDoubleAbs(searchingIterator.index);
				//zeros.add(x1 - (x2-x1)/(y2-y1)*y1);
				zeros.add(searchingIterator.index - y2/(y2-y1));
				
				// restart the search from the searchValue point
				searchStartIterator = searchingIterator;
				startValue = searchValue;
			}
		}
		return zeros;
	}

}
